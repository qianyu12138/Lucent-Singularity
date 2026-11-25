# 诱光奇点 · Lucent Singularity

基于手机摄像头的 Android 测光工具，提供普通相机测光、RAW 自定义区域测光，以及可同时调整快门与光圈的曝光模拟预览。

当前版本：**1.0.1**（versionCode 2） · 包名：`com.qianyu.lux` · 最低 Android 8.0（API 26）。

## 功能与操作

| 功能 | 使用方式 |
| --- | --- |
| 测光读数 | 摄像头画面下方显示 Lux、EV100，并记录最高照度；测光运行时保持屏幕常亮。 |
| 曝光计算 | 设置 ISO、ND；选择光圈优先或快门优先，另一项自动匹配当前组中已启用的档位。超出范围时显示红色边界警告。 |
| 参数拨轮 | 左右拖动调整，切换档位有触感反馈；短按切换优先模式；手动项的数值可点击直接选择。 |
| 器材分组 | 长按数值 5 秒选择组；设置中可新建、重命名组，并启用或取消档位。光圈组默认命名为“镜头 1”，快门组为“相机 1”。 |
| 曝光预览 | 点击主页右上角切换；进入时保留当前显示的参数，快门、光圈同时手动调整，画面随目标曝光变化。 |
| 预览校准 | 曝光预览模式下，原暂停按钮改为校准选择，范围 −3 至 +3 EV、每 1/3 档，默认 0。仅调整模拟画面。 |
| 点击画面 | 普通测光按设备能力请求对焦与曝光测光区域；曝光预览只请求对焦。RAW 点测光则移动软件取样位置。 |
| 显示设置 | 七套主题色；摄像头画面可选彩色或黑白，默认彩色。选择自动保存，黑白显示不改变测光数据或界面主题。 |
| 测量设置 | 默认每 1 秒计算一次，可选 0.5、1、2、5 秒；支持显示或隐藏主页焦距／变焦拨轮。 |
| 信息说明 | “设置 → 信息”包含作者声明、计算公式、测光方案来源和测量边界。 |

普通测光模式保留暂停／继续测光功能。焦距／变焦范围取决于设备，当前实现会在不支持曝光测光区域时禁用该拨轮。

## 测光原理

### 普通相机测光

手机摄像头自动曝光后，App 从拍摄结果读取实际光圈 `Nc`、曝光时间 `tc`（秒）和 ISO `Sc`：

```text
EV100 = log₂(Nc² / tc) − log₂(Sc / 100)
Lux = 2.5 × 2^EV100
```

该方案不使用手机前面的环境光传感器，也不直接统计预览像素亮度。曝光参数无效或场景较暗时，会尝试读取 JPEG 的 EXIF 曝光参数，使用相同公式计算。

普通模式点击画面时，支持 AF 的设备会设置对焦区域，支持 AE 区域的设备会同时设置曝光测光区域。只有 AF 时仍允许对焦，但不能保证曝光针对点击位置。自动曝光调整的是手机摄像头自身的采集参数，不是改变场景光照；调整期间可能短暂显示过渡读数。

### RAW 测光

RAW 方案沿用普通方案的曝光基准，再根据同一帧 RAW 中目标区域与参考区域的线性亮度比例修正：

```text
EVraw = EV相机 + log₂(G选区 / G参考)
```

App 按时间戳匹配 RAW 图像与曝光参数，扣除黑电平、按饱和值归一化，并在设备提供时使用镜头阴影校正信息。彩色传感器使用绿色通道估计亮度。参考区域取自该帧返回的 AE 区域及权重；无有效区域时使用当前传感器裁剪范围的平均亮度。

RAW 像素同样受手机摄像头实际曝光时间、ISO 和光圈影响，并非独立的绝对光照读数。当前 RAW 点测光点击只移动软件取样区，不发送新的 AF／AE 选点请求。

| RAW 模式 | 取样方式 | 画面标记 |
| --- | --- | --- |
| 点测光 | 点击位置附近的圆形区域，约占可见预览面积 1.5%；靠近边缘时将选区保留在画面内。 | 小十字与中心微点 |
| 中央重点（默认） | 固定中央圆约占可见预览面积 13%，中央权重 75%，外围权重 25%。 | 中央虚线圆 |
| 平均测光 | 可见预览范围内有效采样点的等权平均。 | 画面内侧细边框 |

范围与权重参考 [Nikon Z7 II／Z6 II 测光规格](https://onlinemanual.nikonimglib.com/z7II_z6II/en/15_technical_notes_08.html)，平均测光参考 [Sony α1 Entire Screen Avg.](https://helpguide.sony.net/ilc/2420/v1/en/contents/0406_metering_mode.html)。中央圆 13% 是将全画幅上 12 mm 圆的面积比例适配到手机预览的选择，不是统一行业标准。

不支持 RAW 的摄像头会将开关置灰并说明原因。等待曝光稳定、选区／参考区过暗或过亮、帧参数未匹配或采集失败时，可能显示 `—`；它表示没有有效读数，不是照度为零。具体原因见主页右上角状态。当前一帧无效会清空上一次读数。

### 目标拍摄参数与曝光预览

主页的光圈 `N`、快门 `t`、ISO `S`、ND 档数 `d` 是供拍摄器材使用的目标参数，与手机摄像头自身的 `Nc`、`tc`、`Sc` 分开。无 ND 时 `d = 0`。

```text
曝光时间：t = 250 × N² × 2^d / (Lux × S)
光圈：    N = √[Lux × S × t / (250 × 2^d)]

EV目标 = log₂(N² / t) − log₂(S / 100) + d
曝光偏差 = EV测量 − EV目标
预览增益 g = 2^(EV手机 − EV目标 + 校准EV)
```

正曝光偏差表示相对测光建议增加曝光，负值表示减少曝光。GPU 仅处理预览画面：按 sRGB 近似转换到线性值，乘以增益后转回显示值。图像与手机实际曝光参数按时间戳匹配；显示校准不计入测光读数或曝光偏差。

当前 CameraX 1.5.1 不允许 RAW 拍摄与 CameraEffect 同时绑定，因此曝光预览期间使用普通相机基准，保留 RAW 开关选择，退出预览后恢复对应测光方式。

Lux 是相机测量场景反射光后换算的估算值，未进行独立照度标定，不能等同于照度计直接测得的入射照度。RAW 自定义范围不代表天然更准确。曝光预览同样是辅助模拟，无法恢复已丢失的高光／暗部细节，也不能准确复现其他相机的色彩、动态范围、噪点、景深或运动模糊。

## 开发与构建

项目使用 Java 原生界面、CameraX 1.5.1、Android Gradle Plugin 8.13.1 和 Gradle Wrapper 8.14；编译与目标 SDK 均为 36，Java 源码级别为 17。

1. 安装 Android Studio、兼容的 JDK 17 或更高版本，以及 Android SDK 36、Build Tools 36.0.0、Platform Tools。
2. 用 Android Studio 打开项目根目录，配置 SDK 路径。命令行构建也可在未纳入 Git 的 `local.properties` 中设置：

   ```properties
   sdk.dir=/你的/Android/sdk/路径
   ```

3. 准备本地签名配置。当前 Gradle 脚本在配置阶段直接读取 `.signing/release.properties`，因此 Debug 构建也要求该文件存在：

   ```properties
   storePassword=你的密钥库密码
   keyPassword=你的密钥密码
   ```

   Release 使用 `.signing/lucent-singularity.jks`，别名为 `lucent-singularity`。发布更新需使用原发布密钥；配置、密钥和密码不纳入 Git。

在项目根目录执行，必要时先将 `JAVA_HOME` 指向所选 JDK：

```sh
./gradlew assembleDebug
./gradlew assembleRelease lintRelease
```

依赖已缓存时可附加 `--offline`。标准产物路径：

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

构建命令不会自动生成带产品名的 APK。如需命名副本：

```sh
cp app/build/outputs/apk/release/app-release.apk \
   app/build/outputs/apk/release/Lucent-Singularity-1.0.1-release.apk
```

连接设备或启动模拟器后安装调试包：

```sh
adb devices
adb install --no-incremental -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.qianyu.lux/.MainActivity
```

多设备时通过 `adb -s <设备序列号>` 指定目标。Debug 与 Release 签名不同，不能直接互相覆盖安装。相机权限需在设备上授权；模拟器的 RAW、AF／AE 区域和变焦能力由其摄像头实现决定，不能代表所有真机。

## 代码与设计稿

| 路径 | 职责 |
| --- | --- |
| `app/src/main/java/com/qianyu/lux/MainActivity.java` | 主界面、设置、相机生命周期、交互与测光结果展示。 |
| `app/src/main/java/com/qianyu/lux/ExposureCalculator.java` | EV、光圈、快门和曝光预览增益计算。 |
| `app/src/main/java/com/qianyu/lux/RawMeter.java` | RAW 帧匹配、亮度取样、区域权重及相对修正。 |
| `app/src/main/java/com/qianyu/lux/ExposurePreview.java` | CameraEffect／OpenGL 实时曝光模拟与资源释放。 |
| `app/src/main/java/com/qianyu/lux/DialView.java` | 拨轮、档位、渐隐刻度、触感与手势。 |
| `app/src/main/java/com/qianyu/lux/ThemeColors.java` | 界面主题色。 |
| `design/index.html` | 主界面、设置与配色的 HTML 设计稿。 |
| `design/metering.html` | 四种测光标记的交互设计稿。 |

设计稿可在浏览器中打开，使用模拟场景与读数，不连接真实摄像头；部分文案与交互可能落后于 App，应以当前代码和实际 App 为准。

作者：**千羽**。
