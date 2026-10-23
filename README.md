# DTMF Generator for Linux amd64

Compose Multiplatform，与Android版同源。

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
# bin/{dtmf-generator, lib/, runtime/}
tar czf DTMF-Generator-0.3-Release-linux-amd64.tar.gz DTMF-Generator-0.3-Release-linux-amd64
```

## 说明

- 经XWayland运行（Compose Desktop 的窗口层基于 AWT/Skiko）
- **WTF**PL v2

> -1. just fuck wtf u want to fuck yay
