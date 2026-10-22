package app.chumo.dtmf

import kotlin.math.PI
import kotlin.math.sin

/**
 * DTMF 双音多频生成器。
 * 采样率 44.1kHz、16bit PCM 单声道；音长可选 100ms/200ms，间隔可选 20ms/50ms/100ms。
 */
object Dtmf {
    const val SAMPLE_RATE = 44100
    const val TONE_MS = 100
    const val GAP_MS = 20

    /** 标准 DTMF 频率表（低频 x 高频） */
    private val FREQ: Map<Char, Pair<Int, Int>> = mapOf(
        '1' to (697 to 1209), '2' to (697 to 1336), '3' to (697 to 1477),
        '4' to (770 to 1209), '5' to (770 to 1336), '6' to (770 to 1477),
        '7' to (852 to 1209), '8' to (852 to 1336), '9' to (852 to 1477),
        '*' to (941 to 1209), '0' to (941 to 1336), '#' to (941 to 1477)
    )

    /** 查询某个按键的双频（低频, 高频），不支持则返回 null */
    fun frequencyOf(key: Char): Pair<Int, Int>? = FREQ[key]

    /** 生成整个序列的 PCM 采样（小端 16bit）。toneMs 音长（默认 100ms），gapMs 间隔（默认 20ms） */
    fun generate(seq: String, toneMs: Int = TONE_MS, gapMs: Int = GAP_MS): ShortArray {
        val toneSamples = SAMPLE_RATE * toneMs / 1000
        val gapSamples = SAMPLE_RATE * gapMs / 1000
        val total = seq.length * (toneSamples + gapSamples)
        val pcm = ShortArray(total)
        var pos = 0
        for (ch in seq) {
            val f = FREQ[ch] ?: continue
            for (i in 0 until toneSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val v = 0.5 * (sin(2.0 * PI * f.first * t) + sin(2.0 * PI * f.second * t))
                pcm[pos + i] = (v * Short.MAX_VALUE).toInt().toShort()
            }
            pos += toneSamples + gapSamples
        }
        return pcm
    }

    /** ShortArray -> 小端 byte 数组 */
    fun toLittleEndianBytes(pcm: ShortArray): ByteArray {
        val out = ByteArray(pcm.size * 2)
        for (i in pcm.indices) {
            val s = pcm[i].toInt()
            out[i * 2] = (s and 0xFF).toByte()
            out[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return out
    }

    /** 封装成标准 WAV（RIFF + PCM16 单声道 44.1kHz） */
    fun toWavBytes(pcm: ShortArray): ByteArray {
        val dataSize = pcm.size * 2
        val byteRate = SAMPLE_RATE * 2
        val out = ByteArray(44 + dataSize)
        out[0] = 'R'.code.toByte(); out[1] = 'I'.code.toByte(); out[2] = 'F'.code.toByte(); out[3] = 'F'.code.toByte()
        putIntLE(out, 4, 36 + dataSize)
        out[8] = 'W'.code.toByte(); out[9] = 'A'.code.toByte(); out[10] = 'V'.code.toByte(); out[11] = 'E'.code.toByte()
        out[12] = 'f'.code.toByte(); out[13] = 'm'.code.toByte(); out[14] = 't'.code.toByte(); out[15] = ' '.code.toByte()
        putIntLE(out, 16, 16)
        putShortLE(out, 20, 1)
        putShortLE(out, 22, 1)
        putIntLE(out, 24, SAMPLE_RATE)
        putIntLE(out, 28, byteRate)
        putShortLE(out, 32, 2)
        putShortLE(out, 34, 16)
        out[36] = 'd'.code.toByte(); out[37] = 'a'.code.toByte(); out[38] = 't'.code.toByte(); out[39] = 'a'.code.toByte()
        putIntLE(out, 40, dataSize)
        System.arraycopy(toLittleEndianBytes(pcm), 0, out, 44, dataSize)
        return out
    }

    private fun putIntLE(b: ByteArray, o: Int, v: Int) {
        b[o] = (v and 0xFF).toByte()
        b[o + 1] = ((v shr 8) and 0xFF).toByte()
        b[o + 2] = ((v shr 16) and 0xFF).toByte()
        b[o + 3] = ((v shr 24) and 0xFF).toByte()
    }

    private fun putShortLE(b: ByteArray, o: Int, v: Int) {
        b[o] = (v and 0xFF).toByte()
        b[o + 1] = ((v shr 8) and 0xFF).toByte()
    }
}
