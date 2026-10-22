# DTMF Generator

Kotlin + Jetpack Compose (Material 3 / Material You) 写的 DTMF 音频生成器。

- 包名：`app.chumo.dtmf`
- minSdk 31 / targetSdk 37
- 输入：仅数字、`#`、`*`
- DTMF：每音 100ms，间隔 20ms，44.1kHz / 16bit PCM 单声道
- 播放：`播放DTMF`（AudioTrack）
- 保存：`保存` → `/storage/emulated/0/DTMF/DTMF_时间戳_序列.pcm`（原始 PCM，无 WAV 头）
- 权限：`MANAGE_EXTERNAL_STORAGE`（首次保存会跳「所有文件访问权限」设置页）

## 构建

需要 JDK 17+。用 Android Studio 打开本目录，或命令行：

```bash
gradle wrapper          # 若本机已装 gradle；否则下载 gradle-8.9 手动生成 wrapper
./gradlew assembleRelease
```

APK 输出：`app/build/outputs/apk/release/app-release.apk`

## 签名（新密钥）

1. 生成 keystore（把 `<store密码>`、`<别名>` 换成实际值）：

```bash
keytool -genkeypair -v \
  -keystore dtmf-release.keystore \
  -alias dtmf \
  -keyalg RSA -keysize 2048 -validity 10950 \
  -storepass <store密码> \
  -keypass <key密码> \
  -dname "CN=chumo, OU=DTMF, O=chumo, L=Nanjing, ST=Jiangsu, C=CN"
```

2. 复制 `keystore.properties.example` 为 `keystore.properties`，填四项：

```properties
storeFile=dtmf-release.keystore
storePassword=<store密码>
keyAlias=dtmf
keyPassword=<key密码>
```

3. 重新 `./gradlew assembleRelease`，即用新密钥签名。
