package app.chumo.dtmf

import java.io.File

/** 一条号码配置：id 递增，number 可为空（与 Android 客户端同构） */
data class DtmfConfig(
    val id: Int,
    val number: String,
    val time: Int,
    val gap: Int
) {
    /** 配置列表显示格式：号码前五位/时长ms/间隔ms */
    fun label(): String = "${number.take(5)}/${time}ms/${gap}ms"

    fun toJson(): String =
        "{\"id\":$id,\"number\":\"${escape(number)}\",\"time\":$time,\"gap\":$gap}"

    companion object {
        private fun escape(s: String): String =
            s.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}

/** config.json 读写：存放在 ~/DTMF/config.json（与 WAV 保存目录一致），最新配置在最顶端 */
object ConfigStore {
    private const val PATTERN =
        "\\{\"id\":(\\d+),\"number\":\"((?:[^\"\\\\]|\\\\.)*)\",\"time\":(\\d+),\"gap\":(\\d+)\\}"

    private val file: File
        get() = File(File(System.getProperty("user.home"), "DTMF"), "config.json")

    fun load(): List<DtmfConfig> {
        val f = file
        if (!f.exists()) return emptyList()
        return runCatching {
            val text = f.readText()
            Regex(PATTERN).findAll(text).map { m ->
                DtmfConfig(
                    id = m.groupValues[1].toInt(),
                    number = unescape(m.groupValues[2]),
                    time = m.groupValues[3].toInt(),
                    gap = m.groupValues[4].toInt()
                )
            }.toList()
        }.getOrDefault(emptyList())
    }

    fun save(configs: List<DtmfConfig>) {
        val f = file
        f.parentFile?.mkdirs()
        f.writeText(configs.joinToString(",", "[", "]") { it.toJson() })
    }

    private fun unescape(s: String): String =
        s.replace("\\\"", "\"").replace("\\\\", "\\")
}
