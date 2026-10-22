# DTMF Generator — 需求与约束记录

> 本文件记录 2026-10-22 的 Android App 生成任务：原始 prompt、过程中她下达的所有限制与提醒、最终实现状态与构建环境。

## 一、原始需求（她的 prompt，要点逐条）

- 名称：**DTMF Generator**
- 语言：**Kotlin**；UI 用 **Material Design 3**，配色走 **Material You** 动态取色
- **不需要拨号盘**
- 只有一个页面：
  - 一个**输入框**，仅允许输入数字和 `#`、`*`
  - 输入框下方两个按钮：
    1. 「**播放DTMF**」——播放刚输入内容的 DTMF 音频
    2. 「**保存**」——将 **44.1kHz、16bit PCM** 的 DTMF 音频保存到 `/storage/emulated/0/DTMF`
- 页面顶部 **TopAppBar**，标题与应用名相同
- 应用应**申请存储权限**
- DTMF 音长 **100ms**、间隔 **20ms**，**固定值，不显示任何控制组件**
- 包名：**`app.chumo.dtmf`**
- 签名：**创建新的密钥**；需要输入必要内容时**询问她**，由她**以明文方式输入**

## 二、她下达的限制与提醒（时间序，保留原话要点）

1. 「**目标API为37，最低API为31**」
2. 「对了，**版本号设为0.1-Alpha (10)**」→ versionCode = 10，versionName = "0.1-Alpha"
3. 密钥信息（她明文提供）：
   - storePassword 与 keyPassword **相同**（明文值见 `keystore.properties`，文件权限 600，本记录不重复落盘）
   - keyAlias = **`dtmfpwd`**
   - DName = `CN=zzChumo, OU=zzChumo, O=zzChumo, L=Nanjing, ST=Jiangsu, C=CN`
4. 「**有疑问的地方可以直接问我，不必擅自决定**」
5. 「**在必要时允许通过现有子代理辅助工作加快进程**」
6. 「**帮我直接在本地环境内编译apk文件**」

## 三、她拍板的两个技术决策

1. SDK 仓库里没有 `platforms;android-37`（只有 37.0 / 37.1 / 37.2）：
   - 第一次她选：「**用 android-37.0 并复制成 android-37**」
   - AGP 仍按缓存索引查找、不认该目录（`Failed to find target with hash string 'android-37'`）
   - 第二次她选：「**降到 compileSdk/targetSdk 36**」← 最终采用
2. 由此最终 SDK 配置为：**minSdk 31，compileSdk 36，targetSdk 36**

## 四、最终实现状态

- 包名 `app.chumo.dtmf`，版本 **0.1-Pre_Release (11)**（曾为 0.1-Alpha (10)）
- 工具链：AGP 8.13.2 / Gradle 8.13 / Kotlin 2.0.21 + compose 插件 / Compose BOM 2024.09.03
- 存储：`MANAGE_EXTERNAL_STORAGE`，保存到 `/storage/emulated/0/DTMF/DTMF_时间戳_序列.wav`（标准 WAV：RIFF 头 + 44.1kHz/16bit 单声道 PCM）
- 播放：`AudioTrack` MODE_STATIC（44.1kHz / MONO / 16bit）
- 产物（**全部留档，不再删除旧版**）：
  - `DTMF-Generator-0.1-Alpha.apk` (10) sha256 `be8ae25f…`
  - `DTMF-Generator-0.1-Pre_Release.apk` (11) sha256 `55bd2b20…`（回滚代码后重建的忠实版：页脚+WAV+关于弹窗，无时长 Select）
  - `DTMF-Generator-0.1-Release.apk` (12) sha256 `db8fba7a…`
  - `DTMF-Generator-0.1-Release_Fix.apk` (13) sha256 `bb6e472d…`
- **她立的新规矩**：「以后不准清我旧版本了，我要留档」——所有旧版本一律保留
  - sha256 `be8ae25f94925f19cc164d594d5d8918d9300d8db18818f1e97888a0520458fa`
  - 签名证书 DN：`CN=zzChumo, OU=zzChumo, O=zzChumo, L=Nanjing, ST=Jiangsu, C=CN`
- APK 位置：
  - 容器内 `/home/dsh/test/dtmf/DTMF-Generator-0.1-Alpha.apk`
  - 手机可直达 `/storage/emulated/0/dsh-shared/dtmf/DTMF-Generator-0.1-Alpha.apk`

## 五、本机构建环境的坑（复现必读）

- 主机是 **aarch64**：SDK 的 aapt2 是 x86_64 ELF，直接跑不了
  - 解法：Debian trixie 的 **qemu 10**（`/opt/qemu10/usr/bin/qemu-x86_64`，`-cpu max`）
  - 加 amd64 多架构库：`libc6:amd64 libstdc++6:amd64 zlib1g:amd64`
  - aapt2 包装器 `/opt/aapt2wrap/aapt2`（文件名必须是 aapt2，AGP 校验），内容：
    ```sh
    #!/bin/sh
    exec /opt/qemu10/usr/bin/qemu-x86_64 -cpu max /opt/android-sdk/build-tools/36.0.0/aapt2 "$@"
    ```
  - `gradle.properties` 里加 `android.aapt2FromMavenOverride=/opt/aapt2wrap/aapt2`
- apt 源：arm64 走 `mirrors.ustc.edu.cn/ubuntu-ports`；amd64 多架构包必须走**普通 ubuntu 镜像**（ubuntu-ports 没有 amd64）：
  `deb [arch=amd64] http://mirrors.tuna.tsinghua.edu.cn/ubuntu jammy main universe`（+updates/+security）
- Gradle 下载走 `mirrors.cloud.tencent.com/gradle/`（services.gradle.org 会跳 GitHub，GitHub 连不上）

## 六、复现构建命令

```bash
export ANDROID_HOME=/opt/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
cd /home/dsh/test/dtmf
/opt/gradle-8.13/bin/gradle assembleRelease
```

密钥文件：`dtmf-release.keystore` + `keystore.properties`（均 600 权限，位于项目根目录）。
