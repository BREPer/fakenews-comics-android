# 肥牛漫画 Android 客户端

[![CI](https://github.com/BREPer/fakenews-comics-android/actions/workflows/ci.yml/badge.svg)](https://github.com/BREPer/fakenews-comics-android/actions/workflows/ci.yml)

原生 Kotlin + Jetpack Compose，连接 fnOS 上跑的肥牛漫画服务端（默认 `13800`），
底栏与 Web 逐项同名同序：**首页 / 书架 / 搜索 / 漫画库 / 设置**（外加详情、阅读器）。
漫画库的新建/改路径/删除、上传、目录点选都在客户端内完成；离线整章下载未做（阅读只依赖图片磁盘缓存）。

- 参数：`minSdk 30`、`compileSdk/targetSdk 36`、单 Activity、单模块（`app`）+ 采集模块（`baselineprofile`）
- 自托管签名分发，不上应用商店；出包与升级流程见
  [`docs/出包与升级.md`](docs/出包与升级.md)

## 构建

前置两样：

- **JDK 17**
- **Android SDK**，位置二选一：
  - 环境变量 `ANDROID_HOME`（或 `ANDROID_SDK_ROOT`）
  - 或在仓库根建 `local.properties` 写 `sdk.dir=/path/to/sdk`（已在 `.gitignore`，别提交）

`compileSdk 36` 需要 platform 36 与对应 build-tools，AGP 会自动下载缺失的组件（首次慢几分钟）。

```bash
export JAVA_HOME=/path/to/jdk-17      # 需要 JDK 17
./gradlew :app:assembleDebug          # wrapper 自带 Gradle 8.9，首次运行会自动下载
```

Gradle 不需要预装，也不要用系统上的其他版本（wrapper 固定 8.9）；离线环境可先
`./gradlew --offline` 或把发行包放到 `~/.gradle/wrapper/dists/`。

- debug 包：`app/build/outputs/apk/debug/app-debug.apk`
- release 包：`app/build/outputs/apk/release/app-release.apk`（需要签名属性，见下）
- JVM 单测：`./gradlew :app:testDebugUnitTest`

签名属性从 `~/.gradle/gradle.properties` 读四项（模板见 `key.properties.example`）：

```
fakenewsStoreFile / fakenewsStorePassword / fakenewsKeyAlias / fakenewsKeyPassword
```

**四项缺任意一项时，release 变体照常构建但不签名**（不会报错），所以出包前务必
`apksigner verify` 确认签名。

## 模拟器联调

```bash
emulator -avd Pixel_8_API36 -no-snapshot-load -no-boot-anim
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

模拟器里用 `http://10.0.2.2:13800` 访问宿主机上的联调实例。截图与验收脚本产物放到 `_shots/`（不入库）。

## Baseline Profile

规则文件：`app/src/release/baselineProfiles/baseline-prof.txt`（AGP 只认这个目录，放在
`src/release/` 根下不会生效）。由 `baselineprofile` 模块在**非混淆**的 `benchmark` 变体上采集：

```bash
./gradlew :baselineprofile:connectedBenchmarkAndroidTest
# 产物：baselineprofile/build/outputs/connected_android_test_additional_output/benchmark/connected/<设备>/
#       BaselineProfileGenerator_generate-baseline-prof.txt
# 复制到 app/src/release/baselineProfiles/baseline-prof.txt 后重建 release
```

采集用例会自己填服务器地址，首次安装不需要预置数据；模拟器上跑前先唤醒解锁，
否则目标进程不出帧（详见出包文档的踩坑一节）。

## 许可证

本项目采用 [GNU General Public License v3.0](LICENSE)（GPL-3.0）发布。
