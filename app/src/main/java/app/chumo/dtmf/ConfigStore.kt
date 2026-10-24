package app.chumo.dtmf

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 一条号码配置：id 递增，number 可为空 */
data class DtmfConfig(
    val id: Int,
    val number: String,
    val time: Int,
    val gap: Int
) {
    /** Select 菜单里的显示格式：号码前五位/时长ms/间隔ms */
    fun label(): String = "${number.take(5)}/${time}ms/${gap}ms"
}

/** config.json 的读写：最新配置放在数组最顶端 */
object ConfigStore {
    private const val FILE_NAME = "config.json"

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun load(context: Context): List<DtmfConfig> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        DtmfConfig(
                            id = o.optInt("id"),
                            number = o.optString("number", ""),
                            time = o.optInt("time", 100),
                            gap = o.optInt("gap", 20)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, configs: List<DtmfConfig>) {
        val arr = JSONArray()
        configs.forEach { c ->
            arr.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("number", c.number)
                    put("time", c.time)
                    put("gap", c.gap)
                }
            )
        }
        file(context).writeText(arr.toString())
    }
}
