# 开源实践调研记录：1.1.4 连续机制包

本轮只参考许可证与本项目 MIT 发布目标兼容的项目和官方样例的**机制思路**，不复制其源代码。

| 来源 | 许可证/归属 | 可借鉴机制 | 本轮适配原则 |
|---|---|---|---|
| [Audiofy](https://github.com/googol-apps/Audiofy) | Apache-2.0 | 当前播放项突出、按“正在播放 / 接下来”组织队列、首次打开自动定位正在播放项、队列清空与随机入口。 | 保持现有 Material 3 视觉，优先补齐当前曲目定位与队列区段，不复制 UI 或实现。 |
| [UAMP](https://github.com/android/uamp) | Apache-2.0，Android 官方样例 | MediaSession 服务承载后台播放、会话活动入口与跨设备媒体应用基础架构。 | 继续使用现有 Media3 `MediaSessionService`，补强与当前 UI 的状态衔接，不引入过时依赖。 |
| [MusicPlayer-JetpackCompose](https://github.com/DawinderGill/MusicPlayer-JetpackCompose) | Apache-2.0 | ViewModel 驱动的 Compose 状态、仓库职责分离、可扩展的状态管理。 | 延续现有 `PlayerViewModel` 与 repository 边界，只加入低风险的派生状态和交互状态。 |

> 本轮优先落地两个低风险高价值机制：播放队列“正在播放 / 接下来”分段与打开队列时自动定位当前歌曲；同时不改动已有后台播放、授权、二次打包 assets 或共存安装策略。
