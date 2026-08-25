# 贡献指南

感谢你关注 Muse 本地音乐。项目欢迎修复、无障碍改进、稳定性优化、文档完善与经过讨论的功能贡献。提交前请确保改动保持“专题音频包优先、设备媒体库辅助”的产品定位，并避免引入不必要的网络依赖、账号体系或侵入式权限。

## 本地开发

请使用 Android Studio 或 JDK 17、Android SDK 和 Gradle Wrapper。提交前至少执行：

```bash
./gradlew --no-daemon assembleDebug lintDebug testDebugUnitTest
```

如果改动触及发布配置、R8、资源收缩或 Media3 通知，请额外执行：

```bash
./gradlew --no-daemon assembleRelease lintRelease testReleaseUnitTest
```

## 代码与体验要求

Kotlin 代码应保持清晰的状态所有权：Media3 播放状态集中在 `PlayerViewModel` 与 `MusicPlaybackService`，UI 层只消费状态并发出用户意图。请避免在 Compose 根节点订阅高频进度流，也不要在主线程进行 MediaStore 扫描、音频元数据解析或大文件复制。

涉及播放、队列、时长或媒体权限的改动，应同时考虑切歌、暂停、后台恢复、权限拒绝、媒体文件删除和音频标签缺失等场景。涉及 UI 的改动应遵循 Material Design 3 的层级、触控面积与内容描述要求。[1]

## 音频、图片与版权

仓库不应提交未经授权的音乐、商业专辑封面、私钥或真实用户数据。测试音频必须是自制、公共领域或拥有明确再分发许可的文件；如没有合适的测试资产，请在测试中使用模拟或空专题包。生成的视觉资源应注明来源与用途，不得包含 API 密钥或提示中的敏感信息。

## 许可证与第三方代码

新贡献默认以项目 MIT License 提交。若引入第三方代码、素材或实现，请在 pull request 中提供原始链接、许可证、采用范围和所需 notice。未经明确兼容性审查，不得复制 GPL/AGPL 代码进入本项目。

## 提交与 Pull Request

提交信息应说明用户可感知的变化，例如 `fix: 使用 Media3 时间轴修正未知时长`。Pull request 应包括改动目的、验证命令与结果；涉及界面时请附上截图或录屏说明。大范围架构变更请先在 issue 中说明问题、边界与回滚风险。

## 行为准则

请保持尊重、具体和建设性的沟通。歧视、骚扰、侵权内容或主动收集用户隐私数据的贡献将被拒绝。

## 参考资料

[1] [Material 3 for Jetpack Compose](https://m3.material.io/develop/android/jetpack-compose)
