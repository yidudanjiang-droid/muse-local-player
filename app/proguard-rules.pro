# Muse release build
# Android Gradle Plugin、Compose、Media3、DataStore 与 Coil 均自带 consumer rules。
# 此文件刻意保持最小化：只启用默认优化和资源收缩，避免为未使用功能保留无效代码。

# 应用中的 MediaSessionService 由 AndroidManifest.xml 声明，R8 会自动保留该组件。
# 不要将真实签名口令或私钥路径写入此文件。
