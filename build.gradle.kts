plugins {
    kotlin("jvm") version "1.9.24"
    id("org.jetbrains.compose") version "1.6.11"
    application
}

group = "app.chumo"
version = "0.3-Release"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    // 关键：显式使用 Linux x86_64 桌面构件（含 skiko-linux-x64），在 aarch64 上也能交叉打包
    implementation("org.jetbrains.compose.desktop:desktop-jvm-linux-x64:1.6.11")
    implementation(compose.material3)
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("app.chumo.dtmf.MainKt")
}
