"""安卓端巡检：功能走查 + 多屏幕适配，一次跑完出截图。

    python tools/android_tour.py func     # 功能走查（默认尺寸）
    python tools/android_tour.py adapt    # 适配扫描（4 档尺寸 × 各页面）

截图落到 _shots/tour/（已在 .gitignore），配合 tools/android_sheet.py 拼成对照图。
驱动见 android_drive.py（uiautomator dump + 按文本点击）。
"""
from __future__ import annotations

import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from android_drive import Ui  # noqa: E402

OUT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "_shots", "tour"))

# 四档屏幕：(宽, 高, dpi, 标签)。dp 尺寸 = 像素 / dpi * 160
SIZES = [
    (720, 1280, 320, "small-360x640dp"),
    (1080, 2400, 420, "default-411x914dp"),
    (1600, 2560, 320, "tablet-800x1280dp"),
    (2400, 1080, 420, "landscape-914x411dp"),
]


def log(msg: str) -> None:
    print(msg, flush=True)


class Tour:
    def __init__(self) -> None:
        self.ui = Ui()
        os.makedirs(OUT, exist_ok=True)

    # ---------- 基础动作 ----------

    def shot(self, name: str) -> None:
        self.ui.shot(os.path.join(OUT, f"{name}.png"))
        log(f"  shot {name}")

    def tap(self, key: str, wait: float = 1.5) -> bool:
        ok = self.ui.tap(key)
        log(f"  tap {'ok ' if ok else 'MISS'} {key}")
        time.sleep(wait)
        self.ui.reload()
        return ok

    def tap_xy(self, x: int, y: int, wait: float = 1.2) -> None:
        self.ui.tap_xy(x, y)
        time.sleep(wait)
        self.ui.reload()

    def back(self, wait: float = 1.5) -> None:
        self.ui.key("KEYCODE_BACK")
        time.sleep(wait)
        self.ui.reload()

    def has(self, key: str) -> bool:
        return self.ui.find(key) is not None

    # ---------- 步骤 ----------

    def on_home(self) -> bool:
        return self.has("全部漫画")

    def ensure_app(self) -> None:
        """保证 App 在前台。返回键按多了会退到桌面，后续所有按文本的点击都会 MISS。"""
        self.ui.shell("am", "start", "-n", "com.fakenews.comics/.MainActivity")
        time.sleep(2.5)
        self.ui.reload()

    def goto_home(self) -> None:
        """从任意位置回首页：一路返回，直到看见底部导航。"""
        # 底部导航的 首页/书架 标签在每个 tab 页都存在，不能拿它判断「已经到首页」；
        # 用首页独有的「全部漫画 / 继续阅读」判定。
        for _ in range(5):
            self.ui.reload()
            # 只认「全部漫画」：详情页也有「继续阅读」按钮，用它判断会把详情页当首页
            if self.has("全部漫画"):
                return
            self.back()
        self.tap("首页", 2.0)

    def open_reader_settings(self) -> None:
        """阅读器 chrome 3 秒自动隐藏，所以先点中间唤出，再点设置。"""
        self.ui.tap_xy(540, 1200)
        time.sleep(0.6)
        self.ui.reload()
        self.tap("desc:阅读设置", 1.2)

    # ---------- 功能走查 ----------

    def func(self) -> None:
        log("[func] 首页")
        self.goto_home()
        self.shot("func-01-home")
        if self.tap("未读", 2.5):
            self.shot("func-02-home-filter-unread")
        self.tap("未读", 2.0)  # 再点一次取消筛选

        log("[func] 书架")
        self.tap("书架", 2.5)
        self.shot("func-03-shelf")
        if self.tap("收藏", 2.0):
            self.shot("func-04-shelf-fav")
            self.tap("历史", 2.0)

        log("[func] 搜索")
        self.tap("搜索", 2.5)
        self.shot("func-05-search-empty")
        # 搜索框：点一下再输入
        if self.tap("搜索漫画 / 作者", 1.0):
            self.ui.text("feng")
            time.sleep(2.5)
            self.ui.reload()
            self.shot("func-06-search-results")

        log("[func] 漫画库")
        self.tap("漫画库", 2.5)
        self.shot("func-07-libraries")
        if self.tap("新建", 2.0):
            self.shot("func-08-library-new-sheet")
            # 表单里滚一下，看窄屏是否被键盘/高度切断
            self.ui.swipe(540, 1800, 540, 900, 300)
            time.sleep(1.0)
            self.shot("func-09-library-new-sheet-scrolled")
            self.back(1.5)

        log("[func] 设置")
        self.tap("设置", 2.5)
        self.shot("func-10-settings")
        if self.tap("浅色", 2.0):
            self.shot("func-11-settings-light")
            self.tap("深色", 2.0)

        log("[func] done")

    def scroll_page(self, frac: float = 0.46) -> None:
        """按屏高比例滚动一屏（写死像素只对 1080x2400 有效）。"""
        x = self.w // 2
        self.ui.swipe(x, int(self.h * 0.80), x, int(self.h * 0.80 - self.h * frac), 250)

    def first_card(self) -> tuple[int, int] | None:
        """网格里的第一张封面：可点、够高、且位于「全部漫画」标题之下。"""
        cands = []
        for n in self.ui.nodes:
            m = n["bounds"]
            if not n["clickable"] or m.count(",") < 3:
                continue
            x1, y1, x2, y2 = (int(v) for v in m.strip("[]").replace("][", ",").split(","))
            # 阈值按屏高取比例：写死 1000 只有 1080x2400 上成立，小屏上永远找不到
            if y2 - y1 > self.h * 0.12 and y1 > self.h * 0.42:
                cands.append((y1, (x1 + x2) // 2, (y1 + y2) // 2))
        if not cands:
            return None
        cands.sort()
        _, cx, cy = cands[0]
        return cx, cy

    # ---------- 适配扫描 ----------

    def pages(self, tag: str) -> None:
        """在某一档尺寸下把关键页面各截一张。尺寸切换在外部做。"""
        self.goto_home()
        self.shot(f"{tag}-01-home-top")
        self.goto_home()
        self.scroll_page()
        time.sleep(0.8)
        self.scroll_page()
        time.sleep(1.5)
        self.shot(f"{tag}-02-home-grid")

        self.tap("书架", 2.5)
        self.shot(f"{tag}-03-shelf")
        self.tap("搜索", 2.5)
        self.shot(f"{tag}-04-search")
        self.tap("漫画库", 2.5)
        self.shot(f"{tag}-05-libraries")
        self.tap("设置", 2.5)
        self.shot(f"{tag}-06-settings")

        # 详情 + 阅读器（单页）从首页第一本进
        self.goto_home()
        card = self.first_card()
        if card:
            x, y = card
            self.ui.tap_xy(x, y)
        else:
            self.ui.tap_xy(int(self.w * 0.17), int(self.h * 0.62))
        time.sleep(2.5)
        self.ui.reload()
        self.shot(f"{tag}-07-detail")
        if self.tap("继续阅读", 3.0):
            self.shot(f"{tag}-08-reader-single")
        # 不要连按两次返回：如果详情页没打开（继续阅读 MISS），第二次返回会直接退出 App，
        # 下一档尺寸的点击就全落在桌面上了。统一走 goto_home。
        self.ensure_app()
        self.goto_home()

    def detail_only(self, only: list[str] | None = None) -> None:
        """每档尺寸只走「详情页 + 阅读器」这两屏（适配里最容易露馅的两页）。"""
        for w, h, dpi, tag in SIZES:
            if only and not any(tag.startswith(o) for o in only):
                continue
            log(f"[detail] {tag}")
            self.ui.size(w, h, dpi)
            time.sleep(2.0)
            self.w, self.h = w, h
            self.ensure_app()
            self.goto_home()
            # 首页可能停在上一轮滚过的位置，先回到顶部再找封面
            for _ in range(3):
                self.ui.swipe(self.w // 2, int(self.h * 0.35), self.w // 2, int(self.h * 0.85), 200)
                time.sleep(0.5)
            # 首屏骨架（shimmer 无限动画）会让 uiautomator 拿不到 idle，dump 出 0 个节点；
            # 等页面稳定再取节点
            time.sleep(2.5)
            self.ui.reload()
            card = self.first_card()
            log(f"  card={card}")
            if card:
                self.ui.tap_xy(*card)
            else:
                # 兜底：按屏占比点「全部漫画」网格的第一行（各尺寸实测都落在这附近）
                self.ui.tap_xy(int(self.w * 0.17), int(self.h * 0.62))
            time.sleep(2.5)
            self.ui.reload()
            self.shot(f"{tag}-07-detail")
            if self.tap("继续阅读", 3.0):
                self.shot(f"{tag}-08-reader")
            self.ensure_app()
        self.ui.size()
        time.sleep(1.5)

    def adapt(self, only: list[str] | None = None) -> None:
        for w, h, dpi, tag in SIZES:
            if only and not any(tag.startswith(o) for o in only):
                continue
            log(f"[adapt] {tag} ({w}x{h} @{dpi})")
            self.ui.size(w, h, dpi)
            time.sleep(2.0)
            self.w, self.h = w, h
            self.ensure_app()
            self.pages(tag)
        self.ui.size()
        time.sleep(1.5)
        log("[adapt] reset to physical size")


if __name__ == "__main__":
    t = Tour()
    what = sys.argv[1] if len(sys.argv) > 1 else "func"
    t.w, t.h = 1080, 2400
    if what == "func":
        t.func()
    elif what == "detail":
        t.detail_only(sys.argv[2:] or None)
    elif what == "adapt":
        t.adapt(sys.argv[2:] or None)
    else:
        print(__doc__)
