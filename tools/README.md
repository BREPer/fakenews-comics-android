# tools —— 模拟器验收脚本

真机/模拟器上的走查、出图与性能测量。都需要 `adb`（默认从 `PATH` 找，可用环境变量 `ADB`
指定绝对路径）与一台已安装 debug 包的设备；模拟器用 `http://10.0.2.2:13800` 访问宿主机后端。

| 脚本 | 作用 |
|---|---|
| `android_drive.py` | uiautomator 驱动：dump 出真实控件边界，按文本/描述精确点击。`list` 看当前屏、`tap <文本>` 点一下 |
| `android_tour.py` | 巡检：`func` 功能走查 / `adapt` 四档尺寸 × 各页面适配扫描，产物落 `_shots/tour/` |
| `android_sheet.py` | 把一批截图缩到同宽拼成对照图，一轮巡检一眼看完 |
| `android_perf_probe.py` | 单次帧耗时探针（framestats 按表头取列，换 API 版本不炸） |
| `android_perf_suite.py` | 固定测量序列，两版 APK 跑同一套，输出 JSON 逐项对比 |

## 注意

- 连发 `adb shell input tap` 会丢事件，脚本内部已按小步长 + 等待处理，别自己再加速
- Compose 的文本节点不在 `uiautomator dump` 里，坐标要靠 `android_drive.py` 实测或截图取样
- 切分辨率（`wm size`）后 dump 可能短暂返回 0 个节点，脚本有退避重试
