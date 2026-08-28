# Muse 本地音乐技术文档

## 1. 项目边界

Muse 是一个以 APK `assets` 内专题音频为核心、以 MediaStore 设备音乐为辅助的本地 Android 播放器。项目使用 Kotlin、Jetpack Compose、Material 3、AndroidX Media3、DataStore Preferences 和 Coil Compose。应用不提供网络媒体服务；专题内容的发现、解析、播放和摘要均在设备端完成。

项目入口位于 `app/src/main/java/com/muse/localplayer/`。数据模型位于 `data/`，播放器与状态机位于 `playback/`，Compose 页面位于 `ui/`。专题资产契约的长期说明位于 `app/src/main/assets/featured_audio/README.md`，架构说明位于 `docs/featured_audio_architecture.md`。

## 2. 专题加载链路

`FeaturedAudioRepository` 首先检查 legacy 的 `featured_audio/pack.json`，随后扫描 `featured_audio/topics/<目录>/pack.json`。每个有效专题会解析为一个独立的专题包，包含稳定 `id`、显示元数据、封面、曲目、章节、笔记、LRC 关联路径和本地制作摘要。没有 `pack.json` 的目录不会被当作专题目录；无法解析的单期内容不会阻塞设备音乐资料库。

多专题曲目在创建时写入 `featuredTopicId`。该字段是队列边界、搜索结果归属、进度分桶、断点恢复和专题全览切换的共同索引。legacy 单专题使用 `default` 作为稳定 ID，以便承接旧版本完成状态与断点。

## 3. 播放队列与状态隔离

`PlayerViewModel` 维护全量专题目录、当前专题、专题曲目索引、节目单索引和逐专题进度缓存。专题曲目播放入口不直接复用当前 Media3 队列，而是先按 `featuredTopicId` 取得该期完整曲目，再根据目标曲目计算起始位置。章节、歌词、节目笔记、书签和跨专题继续入口使用同一规则。

DataStore 使用稳定专题 ID 组成独立键空间，保存完成曲目集合、专题断点和当前专题选择。旧版默认专题键仍能读取，首次进入目录化专题时会迁移到 `default` 桶。切换专题只切换上下文和队列，不会清除其他专题进度。设备音乐继续使用独立的全局恢复与历史路径。

## 4. 内容搜索与 LRC 缓存

`searchContent` 对设备曲目和全部专题曲目建立统一结果流。专题搜索可命中曲目元数据、专题名称、章节标题、节目笔记和音频同名 LRC 的歌词行；每个结果携带曲目和所属专题上下文。用户点按结果时，播放器先切换专题并建立完整队列，再执行播放或定位。

`LyricsRepository` 只读取 APK assets 内的发布者文件，不联网、不推断歌词。LRC 文件按音频路径推导，例如 `topics/topic-a/03.mp3` 对应 `topics/topic-a/03.lrc`。解析结果按 assets 路径保存在进程内 `ConcurrentHashMap`，减少连续搜索时的重复 IO；应用重启后从原始 assets 重新建立缓存。

## 5. 制作摘要

专题摘要是扫描结果的只读视图，包括音频数、已确认真实时长数、章节数、节目笔记数、`pack.json` 状态、稳定 ID 状态和封面状态。初始扫描可能无法立即获得所有媒体时长；Media3 对曲目完成真实解码确认后，播放器会同步回写对应专题的摘要数据。摘要不会执行远程诊断，也不会上传文件信息。

## 6. 二次打包兼容

Android assets 在构建时可能被压缩或重新排列。专题加载器使用 APK 当前 assets 树重新扫描，不依赖构建时固定的媒体清单。播放时若 Media3 无法直接使用压缩资产，仓库会把对应文件复制到版本隔离的应用私有缓存，再通过缓存 URI 提供给播放器。缓存不会暴露给其他应用，且不会修改原始 assets。

二次打包后新增专题应保持 `pack.json`、封面、曲目和侧车 LRC 的相对路径一致。稳定专题 ID 必须长期保持不变；修改 ID 会创建新的进度桶，这是为了避免不同专题意外共享收听状态。

## 7. 构建、测试与发布

本地构建使用 JDK 17：

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --no-daemon clean assembleRelease lintRelease testReleaseUnitTest
```

Release 通过 ABI splits 输出 `arm64-v8a`、`armeabi-v7a`、`x86_64`、`x86` 四个专包和一个 universal 包。发布前应使用 Android SDK 的 `aapt` 检查包名、版本与 `native-code`，并使用 `apksigner verify --verbose` 确认 APK Signature Scheme v2。签名配置、keystore 和 `keystore.properties` 只能留在本地，禁止放入源码包或提交仓库。

## 8. 维护规则

新增 UI 时应优先复用现有播放器状态流和专题模型，不在 Compose 页面复制播放逻辑。新增持久化字段时必须考虑 legacy default 专题迁移和多 APK 共存；新增队列入口时必须显式处理 `featuredTopicId`，确保专题不会串队。所有外部媒体、封面、歌词和文案必须由发布者确认拥有合法使用与分发权利。
