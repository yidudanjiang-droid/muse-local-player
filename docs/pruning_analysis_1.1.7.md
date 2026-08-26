# 1.1.7 剪枝分析记录

当前 `1.1.6` universal APK 为约 7.6 MiB，单 ABI `arm64-v8a` 包为约 7.5 MiB。APK 的主要非代码体积来自两张 `drawable-nodpi` PNG：`splash_6g_cloud_bg.png` 为 1440×2560、约 3.6 MiB；`featured_audio_cover.png` 为 1536×1024、约 1.8 MiB。它们在 APK 内分别约占 3.18 MiB 与 1.91 MiB 的未压缩资源空间，是最有价值且可安全处理的剪枝目标。

开屏背景用于短时全屏展示，保留竖屏构图即可；在 1080×1920 级别仍可覆盖常见手机显示，降至 1080×1920 并使用高质量 WebP 有损压缩可显著减少下载与解码压力。专题封面最终显示在约 238dp 高的首页卡片中，保留 1152×768 的 3:2 比例并使用高质量 WebP 足以维持感知质量。

发布配置已启用 R8、资源收缩与 ABI splits。依赖中 `androidx.navigation:navigation-compose` 未出现在当前代码导入中，因此是候选安全移除项；其余 Media3、Compose、DataStore、Coil 和 Material 图标依赖均为当前功能实际使用，不在本轮删除范围。

> 剪枝原则：不压缩 APK 内专题音频，不改变 `noCompress` 音频策略，不删除后台 MediaSession、通知、DataStore、Coil 封面缓存、四 ABI 或 universal 输出；只降低未使用显示分辨率和删除确认无引用依赖。

视觉检查结果：优化后的 1080×1920 开屏 WebP 保留了完整的云端与 6G 科技构图，并维持中部文字安全区域；1152×768 专题封面 WebP 保留了首页卡片所需的深蓝层次、明亮线条与橙色视觉焦点。两张资源均未出现明显压缩块、裁切或内容缺失，可安全替换原 PNG。
