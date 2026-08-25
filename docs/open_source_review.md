# 开源本地播放器筛选记录

审阅日期：2026-08-25

| 项目 | 许可证 | 可借鉴能力 | 处理结论 |
|---|---|---|---|
| [Auxio](https://github.com/OxygenCobalt/Auxio) | GPL-3.0 | 本地媒体标签、可靠播放列表、恢复状态、Media3 播放 | 不复制其代码或资源；GPL-3.0 不适合保持本项目的 MIT 发布目标。仅作为功能基准参考。 |
| [jslowinski/MusicPlayer](https://github.com/jslowinski/MusicPlayer) | Apache-2.0 | Compose、ExoPlayer/Media3、前台播放服务、媒体通知、随机/循环控制 | 可在保留 Apache-2.0 许可和归属的前提下借鉴独立实现；优先采用其公开的架构思路，而非整段复制。 |
| [Vaibhav2002/MusicX](https://github.com/Vaibhav2002/MusicX) | MIT | Kotlin、Compose、Material 3、本地数据源、播放列表和移除操作 | 可合规复用；优先审阅其本地数据源与播放列表处理。 |
| [android/uamp](https://github.com/android/uamp) | Apache-2.0 | Android 官方媒体会话、后台播放和媒体通知样例 | 可作为媒体会话与服务边界的官方架构参考；仓库已归档，不将其旧 UI 当作现代实现。 |

本项目保持 MIT 许可证。凡直接纳入 Apache-2.0 或 MIT 源文件的情况，均须在 `NOTICE` 中列明对应项目、文件范围和许可证；不直接使用 GPL-3.0 代码。

| [Audiofy](https://github.com/googol-apps/Audiofy) | Apache-2.0 | Media3/Compose、高性能媒体库、队列、均衡器/外部音频处理、更多编解码器支持 | 可作为音频功能与大屏队列交互参考；直接采用 Apache-2.0 源文件时需补充 NOTICE。当前不引入其 FFmpeg 扩展，以避免显著增大安装包与构建复杂度。 |
