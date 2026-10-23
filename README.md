<h1 align="center">DTMF Generator for Linux amd64</h1>

<p align="center">
  <!-- Kotlin -->
  <a href="https://kotlinlang.org/" target="_blank"><img src="https://img.shields.io/badge/Kotlin-7f52ff.svg?logo=kotlin&logoColor=white&style=for-the-badge" alt="Kotlin"/></a>
  <!-- Android -->
  <a href="https://kernel.org/" target="_blank"><img alt="Static Badge" src="https://img.shields.io/badge/Linux-66ccff?style=for-the-badge&logo=linux&logoColor=white&labelColor=grey&color=66ccff">
</a>
  <!-- License -->
  <a href="https://opensource.org/licenses/MIT" target="_blank"><img src="https://img.shields.io/badge/License-WTFPL-green.svg?style=for-the-badge" alt="WTFPL License"></a>
</p>

Compose Multiplatform，与 Android 版同源、**同版本号**（当前 `1.0-Alpha (100)`）。

图标：与 Android 版同图 —— 圆形白底，内容缩放 0.75。窗口/任务栏图标已内嵌；
想出现在应用菜单里，打包后运行包内的 `install-desktop-entry.sh`。

~~我操这b玩意儿我他妈一行代码没写别问问就是不会写ELF我操感谢DeepSeek感谢深度求索感谢梁文谷我他妈好开心啊我操AI什么时候这么牛逼了顺便在此悼念我那亲爱的可悲的死去的燃尽的钱包😭😭😭梁希批你还我午饭😭😭😭😭😭😭（以上均为玩笑因为我写这些东西的时候有点躁狂发作而且我他妈今天真的没吃上午饭还请各位哦鲸鲸爱好者不要殴打我我也喜欢蓝色肥鱼女仆娘☝️😋~~

## 环境

- JDK 17
- Gradle 8.x

## 构建

```bash
gradle installDist
# build/install/DTMF-Generator-Desktop/{bin,lib}
```

运行（开发）：

```bash
build/install/DTMF-Generator-Desktop/bin/DTMF-Generator-Desktop
```

## 打包

```bash
./package-linux.sh
# 产物：dist/DTMF-Generator-1.0-Alpha-linux-amd64.tar.gz
# 结构：bin/{dtmf-generator, lib/, runtime/} + share/{applications,icons} + VERSION
```

## 说明

- 经 XWayland 运行（Compose Desktop 的窗口层基于 AWT/Skiko）
- 图标：圆形白底 PNG（缩放 0.75），源图 1024×1024
- **WTF**PL v2

> -1. just fuck wtf u want to fuck yay
