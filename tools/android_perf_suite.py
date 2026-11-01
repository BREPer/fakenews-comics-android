#!/usr/bin/env python3
"""Android 流畅度固定测量序列（A/B 用）。

两版 APK 跑同一序列，输出 JSON 便于逐项对比：
  冷启动 TotalTime ×N、五个 tab 真切换、首页→详情、详情→阅读器、列表滚动。

注意：
  * tab 必须**轮流**切 —— `launchSingleTop` 下重复点同一个 tab 不导航，只会产生 1 帧，
    拿它当"切换耗时"会把结果读成假的「流畅」。
  * 每步之间 sleep 是为了让导航动画走完；太短会把上一段动画的帧算进下一步，
    太长 framestats 的 120 帧环形缓冲会把最重的那几帧挤掉。

用法: python tools/android_perf_suite.py --tag after --out perf-after.json
"""
import argparse
import json
import subprocess
import sys
import time

from android_perf_probe import ACT, PKG, adb, parse_frames, run_action, stats

TAB_X = {"home": 108, "shelf": 324, "search": 540, "library": 756, "settings": 972}
TAB_Y = 2255
CARD_XY = (170, 1300)      # 首页「全部漫画」第一张卡
CONTINUE_XY = (282, 1232)  # 详情页「继续阅读」按钮


def frames_now():
    raw = adb("shell", "dumpsys", "gfxinfo", PKG, "framestats")
    return parse_frames(raw)


def measure(steps):
    adb("shell", "dumpsys", "gfxinfo", PKG, "reset")
    for s in steps:
        run_action(s)
    fr = frames_now()
    app = [f["app"] for f in fr]
    ui = [f["ui"] for f in fr]
    out = dict(stats(app))
    out["ui_max"] = round(max(ui), 1) if ui else None
    out["ui_p90"] = stats(ui).get("p90")
    return out


def cold_starts(n):
    times = []
    for _ in range(n):
        adb("shell", "am", "force-stop", PKG)
        time.sleep(1.5)
        out = adb("shell", "am", "start", "-W", "-S", "-n", ACT)
        for line in out.splitlines():
            if line.startswith("TotalTime:"):
                times.append(int(line.split()[1]))
        time.sleep(4)
    return times


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--cold", type=int, default=4)
    args = ap.parse_args()

    result = {"tag": args.tag}

    result["coldStart"] = cold_starts(args.cold)

    # 回到首页并等数据落位，作为后续所有场景的统一起点
    adb("shell", "am", "start", "-n", ACT)
    time.sleep(2)
    adb("shell", "input", "tap", str(TAB_X["home"]), str(TAB_Y))
    time.sleep(3)

    # 五个 tab 轮流切（每次都是真切换，前一步停在上一个 tab）
    order = ["shelf", "search", "library", "settings", "home"]
    tab_res = {}
    for name in order:
        tab_res[name] = measure([f"tap {TAB_X[name]} {TAB_Y}", "sleep 1.5"])
    result["tabSwitch"] = tab_res

    # 首页 → 详情 → 返回，三次
    detail = []
    for _ in range(3):
        detail.append(measure([f"tap {CARD_XY[0]} {CARD_XY[1]}", "sleep 1.5"]))
        run_action("back")
        time.sleep(1.5)
    result["openDetail"] = detail

    # 详情 → 阅读器 → 返回 → 返回，三次
    reader = []
    for _ in range(3):
        run_action(f"tap {CARD_XY[0]} {CARD_XY[1]}")
        time.sleep(1.5)
        reader.append(measure([f"tap {CONTINUE_XY[0]} {CONTINUE_XY[1]}", "sleep 2"]))
        run_action("back")
        time.sleep(1.2)
        run_action("back")
        time.sleep(1.5)
    result["openReader"] = reader

    # 首页列表滚动：8 次上滑
    scroll = measure([f"swipe 540 1700 540 500 110"] * 8 + ["sleep 1"])
    result["scroll"] = scroll

    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
