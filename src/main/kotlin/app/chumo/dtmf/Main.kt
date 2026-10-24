package app.chumo.dtmf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

// ================= DTMF 数据 =================
private const val SAMPLE_RATE = 44100

private val FREQ: Map<Char, Pair<Int, Int>> = mapOf(
    '1' to (697 to 1209), '2' to (697 to 1336), '3' to (697 to 1477),
    '4' to (770 to 1209), '5' to (770 to 1336), '6' to (770 to 1477),
    '7' to (852 to 1209), '8' to (852 to 1336), '9' to (852 to 1477),
    '*' to (941 to 1209), '0' to (941 to 1336), '#' to (941 to 1477)
)

private fun toneSamples(key: Char, count: Int, start: Int = 0): ShortArray {
    val f = FREQ[key] ?: return ShortArray(count)
    val out = ShortArray(count)
    for (i in 0 until count) {
        val t = (start + i).toDouble() / SAMPLE_RATE
        val v = 0.5 * (sin(2.0 * PI * f.first * t) + sin(2.0 * PI * f.second * t))
        out[i] = (v * 32000).toInt().toShort()
    }
    return out
}

private fun generateSequence(seq: String, toneMs: Int, gapMs: Int): ShortArray {
    if (seq.isEmpty()) return ShortArray(0)
    val tone = SAMPLE_RATE * (if (toneMs > 0) toneMs else 1) / 1000
    val gap = SAMPLE_RATE * (if (gapMs > 0) gapMs else 0) / 1000
    val out = ShortArray(seq.length * (tone + gap))
    var pos = 0
    for (c in seq) {
        val f = FREQ[c] ?: continue
        for (i in 0 until tone) {
            val t = i.toDouble() / SAMPLE_RATE
            val v = 0.5 * (sin(2.0 * PI * f.first * t) + sin(2.0 * PI * f.second * t))
            out[pos + i] = (v * 32000).toInt().toShort()
        }
        pos += tone + gap
    }
    return out
}

private fun toWav(pcm: ShortArray): ByteArray {
    val dataSize = pcm.size * 2
    val bb = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
    bb.put("RIFF".toByteArray()); bb.putInt(36 + dataSize); bb.put("WAVE".toByteArray())
    bb.put("fmt ".toByteArray()); bb.putInt(16); bb.putShort(1); bb.putShort(1)
    bb.putInt(SAMPLE_RATE); bb.putInt(SAMPLE_RATE * 2); bb.putShort(2); bb.putShort(16)
    bb.put("data".toByteArray()); bb.putInt(dataSize)
    for (s in pcm) bb.putShort(s)
    return bb.array()
}

// ================= 音频播放 =================
private val fmt = AudioFormat(SAMPLE_RATE.toFloat(), 16, 1, true, false)

private var currentLine: SourceDataLine? = null

private fun playPcm(pcm: ShortArray) {
    if (pcm.isEmpty()) return
    stopAudio()
    val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
    for (s in pcm) bytes.putShort(s)
    val data = bytes.array()
    thread(isDaemon = true) {
        try {
            val line = AudioSystem.getSourceDataLine(fmt)
            currentLine = line
            line.open(fmt)
            line.start()
            line.write(data, 0, data.size)
            line.drain()
            line.stop()
            line.close()
            if (currentLine === line) currentLine = null
        } catch (_: Exception) {
        }
    }
}

private fun stopAudio() {
    currentLine?.let {
        try { it.stop(); it.flush(); it.close() } catch (_: Exception) {}
    }
    currentLine = null
}

/** 按住持续发声 */
private fun startHoldTone(key: Char) {
    stopAudio()
    thread(isDaemon = true) {
        try {
            val line = AudioSystem.getSourceDataLine(fmt)
            currentLine = line
            line.open(fmt)
            line.start()
            var pos = 0
            val block = 1024
            while (currentLine === line) {
                val buf = toneSamples(key, block, pos)
                pos += block
                val bb = ByteBuffer.allocate(block * 2).order(ByteOrder.LITTLE_ENDIAN)
                for (s in buf) bb.putShort(s)
                line.write(bb.array(), 0, block * 2)
            }
        } catch (_: Exception) {
        }
    }
}

// ================= 主题 =================
internal val LightScheme = lightColorScheme(
    primary = Color(0xFF4F6D8C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E0F0),
    onPrimaryContainer = Color(0xFF0B1F33),
    secondaryContainer = Color(0xFFDCE4EE),
    onSecondaryContainer = Color(0xFF1B2733),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2329),
    surfaceVariant = Color(0xFFE9EDF2),
    onSurfaceVariant = Color(0xFF8A9099),
    background = Color(0xFFF2F4F7),
    onBackground = Color(0xFF1F2329),
)

internal val DarkScheme = darkColorScheme(
    primary = Color(0xFF9DBBD8),
    onPrimary = Color(0xFF0B1F33),
    primaryContainer = Color(0xFF33475B),
    onPrimaryContainer = Color(0xFFD3E0F0),
    secondaryContainer = Color(0xFF3A434E),
    onSecondaryContainer = Color(0xFFDCE4EE),
    surface = Color(0xFF1B1F24),
    onSurface = Color(0xFFE3E6EA),
    surfaceVariant = Color(0xFF262C33),
    onSurfaceVariant = Color(0xFF9AA3AD),
    background = Color(0xFF14171B),
    onBackground = Color(0xFFE3E6EA),
)

// ================= 图标（自绘，避免额外依赖） =================
@Composable
private fun IconHome(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier.size(24.dp)) {
        val w = size.width; val h = size.height
        val cx = w / 2
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx, h * 0.12f)
            lineTo(w * 0.9f, h * 0.45f)
            lineTo(w * 0.78f, h * 0.45f)
            lineTo(w * 0.78f, h * 0.88f)
            lineTo(w * 0.22f, h * 0.88f)
            lineTo(w * 0.22f, h * 0.45f)
            lineTo(w * 0.1f, h * 0.45f)
            close()
        }
        drawPath(path, tint)
    }
}

@Composable
private fun IconDialpad(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier.size(24.dp)) {
        val r = size.minDimension * 0.075f
        val xs = listOf(0.25f, 0.5f, 0.75f)
        val ys = listOf(0.2f, 0.5f, 0.8f)
        for (y in ys) for (x in xs) drawCircle(tint, r, Offset(size.width * x, size.height * y))
        drawCircle(tint, r, Offset(size.width * 0.5f, size.height * 1.0f))
    }
}

// ================= 界面 =================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DtmfApp(initialPage: Int = 0) {
    var page by remember { mutableStateOf(initialPage) }
    var number by remember { mutableStateOf("") }
    var toneMs by remember { mutableStateOf(100) }
    var gapMs by remember { mutableStateOf(20) }
    var showAbout by remember { mutableStateOf(false) }
    var customToneOpen by remember { mutableStateOf(false) }
    var customGapOpen by remember { mutableStateOf(false) }
    var customToneText by remember { mutableStateOf("") }
    var customGapText by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }

    // —— 配置（~/DTMF/config.json，与 Android 客户端同构）——
    var configs by remember { mutableStateOf(emptyList<DtmfConfig>()) }
    var selectedConfigId by remember { mutableStateOf(0) }
    var showDeleteConfigDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { configs = ConfigStore.load() }

    fun saveConfig() {
        val newId = (configs.maxOfOrNull { it.id } ?: 0) + 1
        val updated = listOf(DtmfConfig(newId, number, toneMs, gapMs)) + configs
        configs = updated
        ConfigStore.save(updated)
        selectedConfigId = newId
    }

    fun selectConfig(id: Int) {
        selectedConfigId = id
        if (id == 0) {
            number = ""
            toneMs = 100
            gapMs = 20
        } else {
            configs.firstOrNull { it.id == id }?.let {
                number = it.number
                toneMs = it.time
                gapMs = it.gap
            }
        }
    }

    fun deleteSelectedConfig() {
        val id = selectedConfigId
        if (id == 0) return
        val updated = configs.filterNot { it.id == id }
        configs = updated
        ConfigStore.save(updated)
        selectedConfigId = 0
        scope.launch { snackbar.showSnackbar("配置已删除") }
    }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (e.isCtrlPressed) return@onPreviewKeyEvent false
                val ch = e.utf16CodePoint.toChar()
                when {
                    e.key == Key.Backspace -> {
                        if (page == 0 && number.isNotEmpty()) { number = number.dropLast(1); true } else false
                    }
                    ch.isDigit() || ch == '#' || ch == '*' -> {
                        if (page == 0) { number = (number + ch).take(63); true } else false
                    }
                    else -> false
                }
            }
    ) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DTMF Generator", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { showAbout = true }) {
                        IconPerson()
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = page == 0,
                    onClick = { page = 0 },
                    icon = { IconHome(tint = LocalContentColor.current) },
                    label = { Text("主页") }
                )
                NavigationBarItem(
                    selected = page == 1,
                    onClick = { page = 1 },
                    icon = { IconDialpad(tint = LocalContentColor.current) },
                    label = { Text("拨号键盘") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (page == 0) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                ConfigSelectField(
                    label = "配置",
                    value = if (selectedConfigId == 0) "默认" else (configs.firstOrNull { it.id == selectedConfigId }?.label() ?: "默认"),
                    items = listOf("默认") + configs.map { it.label() },
                    onPick = { idx -> selectConfig(if (idx == 0) 0 else configs[idx - 1].id) },
                    onDelete = if (selectedConfigId != 0) ({ showDeleteConfigDialog = true }) else null
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = number,
                    onValueChange = { it.filter { c -> c.isDigit() || c == '#' || c == '*' }.take(63) },
                    label = { Text("号码") },
                    placeholder = { Text("数字、*、#") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                Spacer(Modifier.height(12.dp))
                SelectField(
                    label = "音频时长",
                    value = "$toneMs ms",
                    items = listOf("100 ms", "200 ms", "自定义"),
                    onPick = { idx ->
                        when (idx) {
                            0 -> toneMs = 100
                            1 -> toneMs = 200
                            2 -> { customToneText = toneMs.toString(); customToneOpen = true }
                        }
                    }
                )
                Spacer(Modifier.height(12.dp))
                SelectField(
                    label = "音频间隔",
                    value = "$gapMs ms",
                    items = listOf("20 ms", "50 ms", "100 ms", "0 ms（实验性）", "自定义"),
                    onPick = { idx ->
                        when (idx) {
                            0 -> gapMs = 20
                            1 -> gapMs = 50
                            2 -> gapMs = 100
                            3 -> gapMs = 0
                            4 -> { customGapText = gapMs.toString(); customGapOpen = true }
                        }
                    }
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        val pcm = generateSequence(number, toneMs, gapMs)
                        if (pcm.isEmpty()) {
                            scope.launch { snackbar.showSnackbar("错误：号码为空") }
                        } else {
                            playPcm(pcm)
                            scope.launch { snackbar.showSnackbar("播放中") }
                        }
                    },
                    enabled = number.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("播放 DTMF") }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    FilledTonalButton(
                        onClick = {
                            val pcm = generateSequence(number, toneMs, gapMs)
                            if (pcm.isEmpty()) {
                                scope.launch { snackbar.showSnackbar("错误：号码为空") }
                            } else {
                                val dir = File(System.getProperty("user.home"), "DTMF")
                                dir.mkdirs()
                                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                val safe = number.replace('#', 'H').replace('*', 'S')
                                val f = File(dir, "DTMF_${stamp}_$safe.wav")
                                f.writeBytes(toWav(pcm))
                                scope.launch { snackbar.showSnackbar("已保存：${f.absolutePath}") }
                            }
                        },
                        enabled = number.isNotEmpty(),
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) { Text("保存音频") }
                    FilledTonalButton(
                        onClick = { saveConfig() },
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) { Text("保存配置") }
                }
                Spacer(Modifier.height(24.dp))
                }   // 滚动内容结束

                // 底部小字：常驻底部
                Text(
                    "由DeepSeek辅助完成",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                Spacer(Modifier.height(8.dp))
            }
        } else {
            DialpadPage(
                padding = padding,
                onToneStart = { startHoldTone(it) },
                onToneStop = { stopAudio() }
            )
        }
    }

    }

    if (showDeleteConfigDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfigDialog = false },
            title = { Text("删除配置", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("确定要删除该配置吗？") },
            dismissButton = {
                TextButton(onClick = { showDeleteConfigDialog = false }) { Text("取消") }
            },
            confirmButton = {
                FilledTonalButton(onClick = {
                    deleteSelectedConfig()
                    showDeleteConfigDialog = false
                }) { Text("删除") }
            }
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("关于", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column {
                    Text("本应用由 ")
                    Text(
                        "zzChumo",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://github.com/zzChumo")
                        }
                    )
                    Text("制作，DeepSeek 辅助完成。")
                }
            },
            confirmButton = {
                FilledTonalButton(onClick = { showAbout = false }) { Text("关闭") }
            }
        )
    }

    if (customToneOpen) {
        CustomValueDialog(
            title = "自定义时长",
            initial = customToneText,
            min = 1,
            onChange = { customToneText = it },
            onApply = { v -> toneMs = v; customToneOpen = false },
            onDismiss = { customToneOpen = false }
        )
    }
    if (customGapOpen) {
        CustomValueDialog(
            title = "自定义间隔",
            initial = customGapText,
            min = 0,
            onChange = { customGapText = it },
            onApply = { v -> gapMs = v; customGapOpen = false },
            onDismiss = { customGapOpen = false }
        )
    }
}

@Composable
private fun IconPerson() {
    val tint = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        drawCircle(tint, size.minDimension * 0.20f, Offset(size.width / 2, size.height * 0.32f))
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.22f, size.height * 0.92f)
            quadraticBezierTo(size.width * 0.5f, size.height * 0.55f, size.width * 0.78f, size.height * 0.92f)
            close()
        }
        drawPath(path, tint)
    }
}

@Composable
private fun ConfigSelectField(
    label: String,
    value: String,
    items: List<String>,
    onPick: (Int) -> Unit,
    onDelete: (() -> Unit)?
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Text("▾", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            modifier = Modifier.fillMaxWidth()
        )
        // 点击整个输入框打开下拉
        Box(Modifier.matchParentSize().clickable { expanded = true })
        // 删除图标：仅非默认配置时出现（覆盖在点击层之上）
        if (onDelete != null) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 40.dp)
                    .size(36.dp)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEachIndexed { idx, item ->
                DropdownMenuItem(text = { Text(item) }, onClick = {
                    expanded = false
                    onPick(idx)
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectField(label: String, value: String, items: List<String>, onPick: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(value) }
    LaunchedEffect(value) { selected = value }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Text("▾", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            Modifier.matchParentSize().clickable { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEachIndexed { idx, item ->
                DropdownMenuItem(text = { Text(item) }, onClick = {
                    expanded = false
                    selected = item
                    onPick(idx)
                })
            }
        }
    }
}

@Composable
private fun CustomValueDialog(
    title: String,
    initial: String,
    min: Int,
    onChange: (String) -> Unit,
    onApply: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial.ifEmpty { "0" }) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in min..10000
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it.filter { c -> c.isDigit() }.take(5)
                    onChange(text)
                },
                suffix = { Text("ms") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            FilledTonalButton(onClick = { parsed?.let(onApply) }, enabled = valid) { Text("应用") }
        }
    )
}

@Composable
private fun DialpadPage(
    padding: PaddingValues,
    onToneStart: (Char) -> Unit,
    onToneStop: () -> Unit
) {
    val rows = listOf(
        listOf('1' to "", '2' to "ABC", '3' to "DEF"),
        listOf('4' to "GHI", '5' to "JKL", '6' to "MNO"),
        listOf('7' to "PQRS", '8' to "TUV", '9' to "WXYZ"),
        listOf('*' to "", '0' to "+", '#' to "")
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEachIndexed { rowIdx, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { (key, sub) ->
                    DialKey(key = key, sub = sub, modifier = Modifier.weight(1f), onToneStart, onToneStop)
                }
            }
            if (rowIdx != rows.lastIndex) Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun DialKey(
    key: Char,
    sub: String,
    modifier: Modifier = Modifier,
    onToneStart: (Char) -> Unit,
    onToneStop: () -> Unit
) {
    Box(
        modifier = modifier
            .height(78.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(key) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onToneStart(key)
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        onToneStop()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                key.toString(),
                fontSize = 28.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                if (sub.isEmpty()) " " else sub,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun main(args: Array<String>) {
    if (args.contains("--version")) {
        println("DTMF Generator 1.0-Alpha-2 (102) - Linux amd64 (Compose Multiplatform + Material3)")
        return
    }
    if (args.contains("--help")) {
        println("用法: dtmf-generator [--version]")
        println("  需要图形桌面（Wayland 桌面下经 XWayland 运行）；依赖已随包附带（JRE 17）。")
        return
    }
    runApp()
}

private fun runApp() = application {
    val dark = isSystemInDarkTheme()
    val appIcon = painterResource("icon.png")
    Window(
        onCloseRequest = ::exitApplication,
        title = "DTMF Generator",
        icon = appIcon,
        state = rememberWindowState(width = 480.dp, height = 820.dp)
    ) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme) {
            DtmfApp()
        }
    }
}
