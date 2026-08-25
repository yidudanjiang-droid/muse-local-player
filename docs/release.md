# 正式发布指南

本文用于生成可分发的 Muse APK。正式发布必须使用 `release` 变体；调试 APK 未启用 R8 与资源收缩，体积和可逆向性均不适合作为公开发行物。

## 发布前检查

| 项目 | 要求 |
|---|---|
| 专题内容 | 只包含拥有使用与分发权利的音频、封面和文案。 |
| `pack.json` | JSON 合法，标题、导语、默认艺人和默认专辑符合本期主题。 |
| 文件格式 | 仅使用 MP3、M4A、AAC、OGG、WAV、FLAC；优先填充标题、艺人、专辑、曲目号和年份标签。 |
| 版本号 | 每次向同一安装渠道更新时递增 `versionCode`。 |
| 签名 | 使用受控的长期发布密钥；不要使用调试密钥，也不要提交私钥或口令。 |
| 验证 | 发布前执行 release 构建、Lint、单元测试与 APK 签名核验。 |

## 配置签名

复制模板并填写私有信息：

```bash
cp keystore.properties.example keystore.properties
```

`keystore.properties` 仅应存在于本机或安全的 CI 密钥存储中。示例：

```properties
storeFile=release/muse-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=muse
keyPassword=YOUR_KEY_PASSWORD
```

## 构建与验证

```bash
./gradlew --no-daemon clean assembleRelease lintRelease testReleaseUnitTest
```

输出文件：

```text
app/build/outputs/apk/release/app-release.apk
```

可使用 Android SDK Build Tools 核验：

```bash
$ANDROID_HOME/build-tools/<version>/apksigner verify --verbose --print-certs \
  app/build/outputs/apk/release/app-release.apk
$ANDROID_HOME/build-tools/<version>/aapt dump badging \
  app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
```

## 二次打包专题音频

如果在构建完成后向 APK `assets/` 加入音频，必须在修改后重新对 APK 签名，并在设备上安装或覆盖更新该 APK。Muse 会在启动时递归发现 `assets/` 内的受支持格式；`featured_audio/pack.json` 仍用于专题首页文案。二次打包工具若压缩音频，应用会自动准备私有缓存副本以保证播放。

重新签名会改变 APK 签名证书。对于已有用户的正式更新，必须继续使用与已发布版本相同的签名密钥，否则 Android 会拒绝覆盖安装。[1]

## GitHub 发布建议

公开仓库只提交源代码、许可证、notice、模板和文档。不要提交 `.jks`、`keystore.properties`、APK、AAB、mapping 文件、`local.properties`、`.gradle` 或 `app/build`。可将经过核验的 APK 上传至 GitHub Release；发布说明至少包含版本号、SHA-256、签名证书指纹、主要变化、已知限制和版权提醒。

## 参考资料

[1] [Android：应用签名与更新](https://developer.android.com/studio/publish/app-signing)
