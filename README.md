# 诱光奇点 · Lucent Singularity

基于手机摄像头的 Android 测光工具，支持 RAW 区域测光与曝光模拟预览。

**版本 1.0.1** · Android 8.0+ · `com.qianyu.lux`

## 功能

- 显示 Lux、EV100，支持光圈优先、快门优先及 ISO、ND 调整。
- RAW 支持点测光、中央重点测光（默认）和平均测光。
- 点击右上角切换曝光预览，同时调整快门与光圈；点击画面只对焦。
- 预览亮度支持 ±3 EV 校准，每 1/3 档；仅影响显示。
- 自定义器材档位组、拨轮触感、测量间隔与变焦显示。
- 多套主题色，摄像头画面可切换彩色／黑白。

Lux 为相机测光换算的估算值，曝光预览为辅助模拟。预览期间暂用普通相机基准，退出后恢复 RAW 设置。详细原理、公式与操作说明见 App「设置 → 信息」。

## 构建与安装

使用 Android Studio、JDK 17 及 Android SDK 36，配置本机 SDK 路径。

当前构建脚本要求 `.signing/release.properties` 存在（含 Debug 构建）：

```properties
storePassword=你的密钥库密码
keyPassword=你的密钥密码
```

Release 还需 `.signing/lucent-singularity.jks`，别名 `lucent-singularity`。密钥与配置不纳入 Git。

```sh
./gradlew assembleDebug
./gradlew assembleRelease lintRelease

adb install --no-incremental -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.qianyu.lux/.MainActivity
```

产物位于 `app/build/outputs/apk/` 下的 `debug/`、`release/` 目录。Debug 与 Release 签名不同，不能直接互相覆盖安装。

## 设计稿

[主界面与配色](design/index.html) · [测光标记](design/metering.html)，均使用模拟场景与读数。

## 开源协议

Copyright (C) 2026 **千羽**。采用 [GPL-3.0-only](LICENSE) 协议。
