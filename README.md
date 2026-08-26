# Muse 本地音乐

> **以 APK 内置专题音频为核心、设备本地音乐为辅助的 Android 音乐播放器。**

Muse 本地音乐使用 **Kotlin、Jetpack Compose、Material Design 3 与 AndroidX Media3** 构建，面向“一个 APK 即一张专题声音作品”的发布场景。发布者可以将拥有使用与分发权利的音频放入 APK `assets/`，也可以让用户浏览其设备中的本地音乐；应用不会上传、下载或分发用户的媒体文件。

| 项目 | 状态 |
|---|---|
| 平台 | Android 8.0（API 26）及以上 |
| 包名 | `com.muse.localplayer` |
| 构建体系 | Kotlin 2.x、AGP 8.7、Jetpack Compose、Material 3 |
| 播放内核 | AndroidX Media3 / ExoPlayer |
| 许可证 | [MIT License](LICENSE) |
| 隐私原则 | 无账户、无网络请求、无分析 SDK、无媒体上传 |

## 核心体验

| 模块 | 当前能力 |
|---|---|
| 专题音频包 | 自动递归发现 APK `assets/` 内的 MP3、M4A、AAC、OGG、WAV、FLAC；首页优先展示专题内容，点击专题主卡片或“全部播放”会按稳定扫描顺序建立完整专题连续队列。 |
| 二次打包兼容 | 已构建 APK 经二次打包后新增的 assets 音频同样可被发现；若二次打包工具压缩音频，应用会在私有缓存中准备可播放副本。 |
| 本地资料库 | 读取设备 MediaStore，过滤临时、回收站和短片段媒体，并监听设备音频变更后防抖刷新。 |
| 专辑体验 | 可进入专辑详情、查看曲目、播放整张专辑，或从指定曲目开始连续播放。 |
| 播放控制 | 后台播放、通知栏与锁屏控制、进度拖动、上一首/下一首、播放速度、收藏、持久化队列、恢复上次位置。 |
| 播放方式 | 统一为顺序循环、随机播放、单曲循环、不循环四种互斥策略。 |
| 真实时间轴 | 只以 Media3 已就绪后的解码时间轴计算进度；未知时长不会伪造百分比，解析后会回写曲目列表与播放页。 |
| 视觉与适配 | Material 3 亮暗主题、Android 12+ 动态色、手机底部导航、宽屏导航轨道、品牌开屏动画，以及适配圆形和圆角方形启动器遮罩的无白角自适应图标。 |

## 快速开始

使用 Android Studio 打开项目根目录，选择 API 26 或更高版本的设备并运行 `app`。命令行构建示例如下：

```bash
export ANDROID_HOME=/path/to/Android/Sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
./gradlew assembleDebug
```

调试 APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 制作专题 APK

### 推荐目录与文案配置

建议将专题音频置于 `app/src/main/assets/featured_audio/` 或其子目录。`pack.json` 控制首页的专题标题与降级元数据；音频标签优先级高于配置中的默认艺人和专辑。

```text
app/src/main/assets/
└── featured_audio/
    ├── pack.json
    ├── 01-序章.mp3
    ├── 02-主题曲.m4a
    └── bonus/
        └── 03-现场版.flac
```

| `pack.json` 字段 | 说明 |
|---|---|
| `title` | 专题首页主标题 |
| `eyebrow` | 专题小标识 |
| `description` | 专题导语 |
| `defaultArtist` | 音频标签缺失时的艺人名称 |
| `defaultAlbum` | 音频标签缺失时的专辑名称 |
| `playLabel` | 首页播放按钮文字 |

支持的格式为 `.mp3`、`.m4a`、`.aac`、`.ogg`、`.wav`、`.flac`。文件名可使用 `01-`、`02-` 等前缀控制无标签文件的默认排序。更多约定见 [`app/src/main/assets/featured_audio/README.md`](app/src/main/assets/featured_audio/README.md)。

### 已构建 APK 的二次打包

Muse 不会把专题音频发现逻辑固定在构建时。重新签名并安装/覆盖更新二次打包后的 APK 后，应用会递归扫描 APK `assets/` 中所有支持的音频，因此音频可继续放入 `featured_audio/`，也可以放入新的 assets 子目录。

| 二次打包情形 | 应用行为 |
|---|---|
| 新增音频保持未压缩 | Media3 直接从 APK assets 播放。 |
| 新增音频被工具压缩 | 应用仅在需要时复制到私有缓存，并以本地 URI 播放；缓存随 APK 更新时间隔离，旧版本缓存会被清理。 |
| 没有 `featured_audio/pack.json` | 使用稳定的默认专题标题和默认元数据。 |
| 正在运行旧 APK | 不会动态读取电脑端后来添加的文件；必须重新签名并安装/更新 APK。 |

> 仅应内置你拥有使用、修改、发行或分发权利的音频、封面与文案。Muse 本身不提供音乐下载、在线流媒体或版权授权服务。

## 设备音乐与权限

内置专题 assets 不需要读取设备存储权限。只有用户进入设备本地音乐资料库时，应用才会请求 Android 的音频读取权限；通知权限为可选，拒绝后不会阻碍播放或扫描，但系统媒体通知不会显示。

| Android 权限 | 用途 |
|---|---|
| `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` | 读取用户选择授权的设备本地音频。 |
| `POST_NOTIFICATIONS` | 在 Android 13+ 显示可选的系统媒体通知。 |
| 前台媒体播放权限 | 维持 MediaSessionService 的后台播放与系统控制。 |

应用不请求网络、定位、通讯录、相机或麦克风权限。

## 播放行为与稳定性

播放页的“播放方式”是唯一的策略入口，避免随机与循环开关组合造成理解负担。

| 播放方式 | 行为 |
|---|---|
| 顺序循环 | 按当前队列顺序播放，到末尾后从第一首继续。 |
| 随机播放 | 随机选择当前队列中的歌曲，队列结束后停止。 |
| 单曲循环 | 重复播放当前歌曲。 |
| 不循环 | 按当前队列顺序播放，到末尾后停止。 |

为了应对错误或缺失的文件标签，播放进度不再直接采用封装标签的时长。切换曲目会立即清空上一首的时间轴；Media3 解码器进入就绪状态并给出有效时长后，播放页、进度条、曲目列表与队列会同步为真实时长。若尚未解析成功，界面显示 `—:—` 和“正在读取时长”，并暂时禁用进度拖动，不会让进度条错误接近结尾。

后台播放由 `MediaSessionService` 管理。系统通知与锁屏卡片使用标准 Media3 媒体样式，显示专辑封面、歌曲名、艺人和专辑；点按通知会返回现有 Muse 界面。音频焦点与耳机拔出暂停由 ExoPlayer 处理。[1]

如果已扫描的设备文件后来被删除、移动，或当前设备无法解码该格式，Muse 会提示原因并自动尝试继续下一首可用曲目；单个异常文件不会直接中断整段队列播放。

## 架构

```text
app/src/main/java/com/muse/localplayer/
├── MainActivity.kt                         # 权限、Compose 入口与开屏覆盖层
├── data/
│   ├── FeaturedAudioRepository.kt          # APK assets 发现、压缩资源回退与元数据读取
│   ├── MusicRepository.kt                  # MediaStore 设备音乐扫描
│   ├── MediaStoreObserver.kt                # 设备音乐变更监听
│   └── UserPreferencesRepository.kt        # DataStore 队列、收藏和播放恢复
├── playback/
│   ├── MusicPlaybackService.kt              # 后台 MediaSessionService
│   ├── MuseMediaNotificationProvider.kt     # 通知文案、图标与频道
│   └── PlayerViewModel.kt                   # 队列、真实时间轴与播放器状态
└── ui/
    ├── MuseMusicApp.kt                      # Material 3 导航、资料库与播放器 UI
    └── MuseLaunchSplash.kt                  # 品牌开屏动画
```

## 正式发布构建

发布变体启用 R8 代码压缩与 Android 资源收缩；这会移除未使用的 Compose 图标、代码与资源。请使用自己的签名密钥发布，**不要**将私钥或口令提交到仓库。

```bash
cp keystore.properties.example keystore.properties
# 在 keystore.properties 中填写自己的签名信息
./gradlew clean assembleRelease
```

签名配置示例：

```properties
storeFile=release/muse-release.jks
storePassword=CHANGE_ME
keyAlias=muse
keyPassword=CHANGE_ME
```

正式 APK 位于：

```text
app/build/outputs/apk/release/app-release.apk
```

`keystore.properties`、`release/`、`*.jks`、APK/AAB 与构建产物均已列入 `.gitignore`。详细发布检查清单见 [`docs/release.md`](docs/release.md)。

## 开源、贡献与安全

项目以 [MIT License](LICENSE) 发布。第三方归属与适配范围见 [NOTICE](NOTICE)、[`docs/open_source_review.md`](docs/open_source_review.md) 及 [`THIRD_PARTY_LICENSES`](THIRD_PARTY_LICENSES)。本项目不会复制 GPL-3.0 代码；GPL 项目仅用于功能体验研究。

欢迎通过 issue 或 pull request 提交可复现问题和改进建议。请先阅读 [`CONTRIBUTING.md`](CONTRIBUTING.md)；安全问题请遵循 [`SECURITY.md`](SECURITY.md) 的私下报告流程。

## 参考资料

[1] [AndroidX Media3：媒体会话、通知与后台播放](https://developer.android.com/media/media3/session/background-playback)

[2] [Material 3 for Jetpack Compose](https://m3.material.io/develop/android/jetpack-compose)

[3] [Android 应用签名与发布](https://developer.android.com/studio/publish/app-signing)

## 一键共存安装

Muse 可作为独立 APK 共存安装。使用 APK 共存工具修改并重新签名为不同包名后，Android 会将其视为新的应用；本地权限、DataStore 队列/收藏/播放记录、应用私有缓存、后台 `MediaSessionService` 和通知渠道均按该 APK 的运行时包名隔离。Muse 的媒体通知频道会动态使用 `<当前包名>.playback`，因此不同专题 APK 不会复用同一通知状态。

> 共存工具修改 APK 后必须重新签名；改包名后的应用不能覆盖更新原包名应用，后续更新该共存专题时也必须保持同一个改后包名和签名证书。

## ABI 下载包

正式 Release 同时提供四个架构专用 APK 和一个通用 APK。普通安卓手机优先下载 `arm64-v8a`；不确定设备架构时下载通用包即可。

| 发布资产 | 适用设备 |
|---|---|
| `arm64-v8a` | 绝大多数近年 Android 手机和平板。 |
| `armeabi-v7a` | 较旧的 32 位 ARM Android 设备。 |
| `x86_64` | 64 位 x86 Android 模拟器或少数 x86_64 设备。 |
| `x86` | 32 位 x86 Android 模拟器或少数旧 x86 设备。 |
| `universal` | 同时包含四种 ABI；无法确认架构时使用，文件略大。 |

## 日常使用功能包

**睡眠定时**位于首页，可选择 15、30 或 60 分钟；时间结束时播放器会按当前淡化设置平滑暂停。定时结束时间会保存在本应用私有偏好中，应用返回前台后会恢复倒计时状态。

**最近播放**会保留最近 50 首实际切入播放的曲目，并自动去重；点击首页的“清空”可随时删除记录。**最近加入**基于设备媒体库的加入时间显示最新 8 首设备音乐，设备文件变化后会在媒体库观察器的刷新中更新。两类记录均不上传网络，并随应用包名隔离。

收藏页现提供 **播放全部收藏**，会将当前全部收藏按稳定顺序建立独立播放队列；无需再逐首点按加入队列。专辑详情的“播放整张专辑”、歌曲菜单的“下一首播放”和播放队列内的移动/移除可与该入口配合使用。

## 队列效率机制

专辑详情和收藏页均提供 **加入队列**，会跳过已存在的曲目并仅追加新增歌曲；完成后会展示实际新增数量。清空队列后可在短暂提示内选择 **撤销**，恢复刚才清空前的队列及选中曲目。单曲从队列移除后同样支持即时撤销，并回到原先位置。迷你播放器右侧新增队列快捷入口，不需要先进入播放页即可管理播放顺序。

长队列会按 **队列前序、正在播放、接下来** 分段；打开队列时会自动定位到当前曲目区域，标题同时显示总曲数和当前播放序号。当前曲目前有已播歌曲时，队列顶部会出现 **移除已播**：它只清理队列前序，不会改变当前曲目或接下来播放顺序，并可即时撤销。该机制参考商业播放器中成熟的队列整理模式，但已按 Muse 的离线本地播放架构原创实现。

为避免大媒体库用户在回到前台时产生不必要 I/O，Muse 只会在设备音频权限状态真正发生变化时重新扫描资料库。播放恢复会避开刚播放结束时的曲目末尾位置；队列已经恢复到内存、但控制器尚未装载媒体项目时，点按队列曲目也会正确建立队列并开始播放。对于已经被删除、移动或无法解码的本地文件，连续播放会尝试自动跳过异常项并继续下一首。
