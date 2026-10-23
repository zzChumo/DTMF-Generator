package app.chumo.dtmf

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * 离屏渲染截图（无需 X 服务器）：
 *   java -Djava.awt.headless=true -cp ... app.chumo.dtmf.ScreenshotKt <输出目录>
 */
fun main(args: Array<String>) {
    System.setProperty("java.awt.headless", "true")
    val outDir = File(if (args.isNotEmpty()) args[0] else ".")
    outDir.mkdirs()

    val density = 2f
    val width = (480 * density).toInt()
    val height = (820 * density).toInt()

    fun render(name: String, page: Int, dark: Boolean) {
        val scene = ImageComposeScene(width, height, Density(density)) {
            MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme) {
                DtmfApp(initialPage = page)
            }
        }
        try {
            scene.render()
            val image = scene.render()
            val data = image.encodeToData(EncodedImageFormat.PNG) ?: error("PNG 编码失败")
            val out = File(outDir, name)
            out.writeBytes(data.bytes)
            println("saved: " + out.absolutePath + " (" + data.size + " B)")
        } finally {
            scene.close()
        }
    }

    render("desktop_light_home.png", 0, false)
    render("desktop_light_dialpad.png", 1, false)
    render("desktop_dark_home.png", 0, true)
}
