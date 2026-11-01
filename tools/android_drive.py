"""安卓端测试驱动：用 uiautomator dump 拿到真实控件边界，按文本/描述精确点击。

    python tools/android_drive.py list                  # 列出当前屏所有可点/有字的节点
    python tools/android_drive.py tap 双页               # 按文本精确点击（找不到就退化成包含匹配）
    python tools/android_drive.py tap desc:关闭          # 按 content-desc 点击
    python tools/android_drive.py shot out.png          # 截图（本地路径）
    python tools/android_drive.py swipe x1 y1 x2 y2 ms
    python tools/android_drive.py size 1080 2400 420    # 改分辨率/密度；reset 恢复
    python tools/android_drive.py wait 400              # 毫秒

单独 import 也能用：from android_drive import Ui
"""
from __future__ import annotations

import os
import re
import subprocess
import sys
import time

ADB = os.environ.get(
    "ADB",
    "adb",  # 走 PATH；可用环境变量 ADB 覆盖
)

_NODE_RE = re.compile(r"<node[^>]*>")


def _run(args: list[str]) -> str:
    p = subprocess.run([ADB, *args], capture_output=True, text=True, errors="replace")
    return p.stdout


def _attr(node: str, name: str) -> str:
    m = re.search(rf'{name}="([^"]*)"', node)
    return m.group(1) if m else ""


def _center(bounds: str) -> tuple[int, int] | None:
    m = re.match(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", bounds)
    if not m:
        return None
    x1, y1, x2, y2 = (int(v) for v in m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


class Ui:
    """一次 dump = 一份快照。每次都重新 dump，避免拿旧坐标点新界面。"""

    def __init__(self) -> None:
        self.nodes: list[dict] = []
        self.reload()

    def reload(self, retries: int = 8) -> None:
        # 改完 wm size/density 之后无障碍树会短暂为空（uiautomator 直接 dump 出 0 个节点），
        # 空就退避重试，否则所有按文本的点击都会 MISS。
        xml = ""
        for attempt in range(retries):
            _run(["shell", "uiautomator", "dump", "/sdcard/ui.xml"])
            xml = _run(["shell", "cat", "/sdcard/ui.xml"])
            if "<node" in xml:
                break
            time.sleep(1.2)
        self.nodes = []
        for raw in _NODE_RE.findall(xml):
            text = _attr(raw, "text")
            desc = _attr(raw, "content-desc")
            bounds = _attr(raw, "bounds")
            center = _center(bounds)
            if center is None:
                continue
            self.nodes.append(
                {
                    "text": text,
                    "desc": desc,
                    "clickable": _attr(raw, "clickable") == "true",
                    "bounds": bounds,
                    "center": center,
                }
            )

    # ---------- 查找 ----------

    def find(self, key: str) -> dict | None:
        """key 可以是「文本」或 desc:xxx。先精确，再包含。"""
        want_desc = key.startswith("desc:")
        needle = key[5:] if want_desc else key
        field = "desc" if want_desc else "text"

        cands = [n for n in self.nodes if n[field] == needle]
        if not cands:
            cands = [n for n in self.nodes if needle and needle in n[field]]
        # 有可点的优先（文本节点本身不可点，点它也能落到父容器上，但取父级更稳）
        clickable = [n for n in cands if n["clickable"]]
        return (clickable or cands or [None])[0]

    def dump_list(self) -> str:
        lines = []
        for n in self.nodes:
            if not (n["text"] or n["desc"] or n["clickable"]):
                continue
            tag = "●" if n["clickable"] else " "
            label = n["text"] or f"[{n['desc']}]"
            lines.append(f"{tag} {label:<24} {n['bounds']}")
        return "\n".join(lines)

    # ---------- 动作 ----------

    def tap(self, key: str) -> bool:
        node = self.find(key)
        if not node:
            return False
        x, y = node["center"]
        _run(["shell", "input", "tap", str(x), str(y)])
        return True

    def tap_xy(self, x: int, y: int) -> None:
        _run(["shell", "input", "tap", str(x), str(y)])

    def swipe(self, x1: int, y1: int, x2: int, y2: int, ms: int = 300) -> None:
        _run(["shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), str(ms)])

    def shot(self, path: str) -> None:
        os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
        with open(path, "wb") as f:
            subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=f)

    def shell(self, *args: str) -> str:
        """直通 adb shell（按键、输入文本这类没有节点可点的动作）。"""
        return _run(["shell", *args])

    def text(self, value: str) -> None:
        _run(["shell", "input", "text", value])

    def key(self, code: str) -> None:
        _run(["shell", "input", "keyevent", code])

    def size(self, w: int | None = None, h: int | None = None, dpi: int | None = None) -> None:
        if w is None:
            _run(["shell", "wm", "size", "reset"])
            _run(["shell", "wm", "density", "reset"])
            return
        _run(["shell", "wm", "size", f"{w}x{h}"])
        if dpi:
            _run(["shell", "wm", "density", str(dpi)])


def _main(argv: list[str]) -> int:
    if not argv:
        print(__doc__)
        return 2
    cmd, *rest = argv
    ui = Ui()
    if cmd == "list":
        print(ui.dump_list())
    elif cmd == "tap":
        ok = ui.tap(rest[0])
        print("ok" if ok else f"NOT FOUND: {rest[0]}")
        return 0 if ok else 1
    elif cmd == "shot":
        ui.shot(rest[0])
        print("saved", rest[0])
    elif cmd == "swipe":
        ui.swipe(*(int(v) for v in rest))
        print("ok")
    elif cmd == "size":
        if rest and rest[0] == "reset":
            ui.size()
        else:
            ui.size(int(rest[0]), int(rest[1]), int(rest[2]) if len(rest) > 2 else None)
        print("ok")
    elif cmd == "wait":
        time.sleep(int(rest[0]) / 1000)
        print("ok")
    else:
        print(__doc__)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(_main(sys.argv[1:]))
