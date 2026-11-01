#!/usr/bin/env python3
"""Android 主线程帧耗时探针。

模拟器的 GPU 是宿主机软件/半硬件渲染，单帧 GPU 就要 ~18ms，帧率绝对值被模拟器
自身瓶颈淹没，量不出应用改动的差别。真正随代码变化的是「主线程一帧干了多少活」，
所以本探针解析 `dumpsys gfxinfo <pkg> framestats`，取每帧
   app  = SyncQueued      - PerformTraversalsStart   # measure/layout/录制绘制命令
   ui   = SyncQueued      - HandleInputStart         # 再加上输入分发
单位换算成 ms，输出中位/P90/P99/最大值与超阈值帧数。

用法:
  python tools/android_perf_probe.py --label tab-shelf "tap 324 2255" "sleep 1.5"
  python tools/android_perf_probe.py --label scroll "swipe 540 1800 540 600 120" x8
  python tools/android_perf_probe.py --label detail --cold "tap 180 1250" "sleep 2"

命令: tap X Y | swipe X1 Y1 X2 Y2 [MS] | sleep S | back | home | start | stop
     `x8` 形式的重复后缀可套在任意命令上。--cold 会先 force-stop 再 start。
"""
import argparse
import os
import statistics
import subprocess
import sys
import time

ADB = os.environ.get(
    "ADB",
    "adb",  # 走 PATH；可用环境变量 ADB 覆盖
)
PKG = "com.fakenews.comics"
ACT = f"{PKG}/.MainActivity"

# framestats 列序随版本变动（API 36 起插了 FrameTimelineVsyncId 等），一律按表头定位
COLS = ("HandleInputStart", "PerformTraversalsStart", "SyncQueued", "FrameCompleted")


def adb(*args, timeout=90):
    return subprocess.run(
        [ADB, *args], capture_output=True, text=True, encoding="utf-8",
        errors="replace", timeout=timeout,
    ).stdout.replace("\r", "")


def run_action(cmd):
    parts = cmd.split()
    if not parts:
        return
    op, rest = parts[0], parts[1:]
    if op == "tap":
        adb("shell", "input", "tap", rest[0], rest[1])
    elif op == "swipe":
        ms = rest[4] if len(rest) > 4 else "120"
        adb("shell", "input", "swipe", rest[0], rest[1], rest[2], rest[3], ms)
    elif op == "sleep":
        time.sleep(float(rest[0]))
    elif op == "back":
        adb("shell", "input", "keyevent", "4")
    elif op == "home":
        adb("shell", "input", "keyevent", "3")
    elif op == "start":
        adb("shell", "am", "start", "-n", ACT)
    elif op == "stop":
        adb("shell", "am", "force-stop", PKG)
    else:
        raise SystemExit(f"未知命令: {cmd}")


def expand(cmds, times):
    out = []
    for c in cmds:
        token = c.split()[-1]
        n = times
        if token.startswith("x") and token[1:].isdigit():
            n = int(token[1:])
            c = " ".join(c.split()[:-1])
        out.extend([c] * n)
    return out


def parse_frames(raw):
    """从 framestats 输出里抠出每帧的 app/ui 主线程耗时（ms）。"""
    frames = []
    recording = False
    idx = None
    for line in raw.splitlines():
        line = line.strip()
        if line == "---PROFILEDATA---":
            recording = not recording
            idx = None
            continue
        if not recording or not line:
            continue
        cells = line.split(",")
        if line.startswith("Flags"):
            # 按表头定位列，别写死下标（不同 API 列序不同）
            names = [c.strip() for c in cells]
            try:
                idx = [names.index(n) for n in COLS]
            except ValueError:
                return []
            continue
        if idx is None or len(cells) <= max(idx):
            continue
        try:
            handle, perf, sync, done = (int(cells[i]) for i in idx)
        except ValueError:
            continue
        if sync <= 0 or perf <= 0:
            continue
        app = (sync - perf) / 1e6
        ui = (sync - handle) / 1e6 if 0 < handle <= sync else app
        if app < 0 or app > 2000:      # 明显的坏数据/暂停帧
            continue
        frames.append({"app": app, "ui": ui, "done": done})
    return frames


def stats(vals):
    if not vals:
        return {}
    s = sorted(vals)

    def pct(p):
        return round(s[min(len(s) - 1, int(len(s) * p))], 1)

    return {
        "n": len(s),
        "median": round(statistics.median(s), 1),
        "p90": pct(0.90),
        "p99": pct(0.99),
        "max": round(s[-1], 1),
        "gt16": sum(1 for v in s if v > 16.7),
        "gt33": sum(1 for v in s if v > 33.4),
        "gt50": sum(1 for v in s if v > 50),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--label", required=True)
    ap.add_argument("--repeat", type=int, default=1, help="整组动作重复轮数")
    ap.add_argument("--cold", action="store_true", help="先 force-stop 再 start")
    ap.add_argument("--json", action="store_true")
    ap.add_argument("cmds", nargs="+", help="tap/swipe/sleep/back/home/start/stop")
    args = ap.parse_args()

    adb("shell", "dumpsys", "gfxinfo", PKG, "reset")
    if args.cold:
        adb("shell", "am", "force-stop", PKG)
        adb("shell", "am", "start", "-W", "-n", ACT)
        time.sleep(3)

    for _ in range(args.repeat):
        for c in expand(args.cmds, 1):
            run_action(c)

    raw = adb("shell", "dumpsys", "gfxinfo", PKG, "framestats")
    frames = parse_frames(raw)
    app = stats([f["app"] for f in frames])
    ui = stats([f["ui"] for f in frames])

    if args.json:
        import json
        print(json.dumps({"label": args.label, "app": app, "ui": ui}))
        return 0

    print(f"[{args.label}] frames={app.get('n', 0)}")
    if app:
        print(f"  app(主线程活) 中位 {app['median']}ms  p90 {app['p90']}  "
              f"p99 {app['p99']}  max {app['max']}")
        print(f"  ui(含输入)    中位 {ui['median']}ms  p90 {ui['p90']}  "
              f"p99 {ui['p99']}  max {ui['max']}")
        print(f"  超 16.7ms: {app['gt16']}  超 33ms: {app['gt33']}  超 50ms: {app['gt50']}")
    worst = sorted(frames, key=lambda f: -f["app"])[:3]
    if worst:
        print("  最重三帧 app: " + " / ".join(f"{f['app']:.1f}ms" for f in worst))
    return 0


if __name__ == "__main__":
    sys.exit(main())
