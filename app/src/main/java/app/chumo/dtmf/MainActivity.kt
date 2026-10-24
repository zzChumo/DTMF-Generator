package app.chumo.dtmf

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.ripple
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    // 拨号键盘的低延迟音频引擎：预创建 AudioTrack + 专用线程持续喂数据
    @Volatile private var activeKey: Char? = null
    @Volatile private var engineRunning = false
    private var toneTrack: AudioTrack? = null
    private var feederThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val scheme = when {
                Build.VERSION.SDK_INT >= 31 && isSystemInDarkTheme() -> dynamicDarkColorScheme(this)
                Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(this)
                isSystemInDarkTheme() -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) {
                DtmfApp(
                    onPlay = ::play,
                    onSave = ::save,
                    onToneStart = ::startTone,
                    onToneStop = ::stopTone
                )
            }
        }
        // 启动时就建好音频引擎，按下按键时只剩一个标志位切换
        ensureAudioEngine()
    }

    override fun onDestroy() {
        releaseAudioEngine()
        super.onDestroy()
    }

    /** 播放一段 DTMF 音频 */
    private fun play(seq: String, toneMs: Int, gapMs: Int) {
        lifecycleScope.launch(Dispatchers.Default) {
            val pcm = Dtmf.generate(seq, toneMs, gapMs)
            val minBuf = AudioTrack.getMinBufferSize(
                Dtmf.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufSize = maxOf(minBuf, pcm.size * 2)
            val track = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(Dtmf.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
                bufSize,
                AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            track.write(pcm, 0, pcm.size)
            track.setNotificationMarkerPosition(pcm.size)
            track.setPlaybackPositionUpdateListener(object :
                AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) {
                    t.release()
                }

                override fun onPeriodicNotification(t: AudioTrack) {}
            })
            track.play()
        }
    }

    /** 保存为 44.1kHz/16bit WAV 到 /storage/emulated/0/DTMF */
    private fun save(seq: String, toneMs: Int, gapMs: Int, onResult: (String) -> Unit) {
        if (!Environment.isExternalStorageManager()) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
            onResult("请授予「所有文件访问权限」，再点一次保存")
            return
        }
        lifecycleScope.launch(Dispatchers.Default) {
            val pcm = Dtmf.generate(seq, toneMs, gapMs)
            val dir = File(Environment.getExternalStorageDirectory(), "DTMF").apply { mkdirs() }
            val name = "DTMF_" +
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) +
                "_" + seq + ".wav"
            val f = File(dir, name)
            f.writeBytes(Dtmf.toWavBytes(pcm))
            onResult("已保存：" + f.absolutePath)
        }
    }

    // ================= 低延迟音频引擎 =================

    /**
     * 预创建一条低延迟 AudioTrack（PERFORMANCE_MODE_LOW_LATENCY），
     * 由高优先级线程持续喂入 256 帧（≈5.8ms）的小块数据：
     * 空闲时写静音、按下时立刻改写成对应双音 —— 按下到出声只有约一个 block 的延迟。
     */
    private fun ensureAudioEngine() {
        if (toneTrack != null) return
        val minBuf = AudioTrack.getMinBufferSize(
            Dtmf.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val blockFrames = 256
        val bufferBytes = maxOf(minBuf, blockFrames * 2 * 2)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(Dtmf.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        toneTrack = track
        track.play()
        engineRunning = true
        feederThread = Thread {
            val toneBlock = ShortArray(blockFrames)
            val silence = ShortArray(blockFrames)
            var currentKey: Char? = null
            var freq: Pair<Int, Int>? = null
            var phase = 0L
            while (engineRunning) {
                val key = activeKey
                if (key == null) {
                    currentKey = null
                    freq = null
                    phase = 0L
                    if (!writeBlock(track, silence)) break
                } else {
                    if (key != currentKey) {
                        currentKey = key
                        freq = Dtmf.frequencyOf(key)
                        phase = 0L
                    }
                    val f = freq
                    if (f == null) {
                        if (!writeBlock(track, silence)) break
                    } else {
                        for (i in toneBlock.indices) {
                            val t = (phase + i).toDouble() / Dtmf.SAMPLE_RATE
                            val v = 0.5 * (sin(2.0 * PI * f.first * t) + sin(2.0 * PI * f.second * t))
                            toneBlock[i] = (v * Short.MAX_VALUE).toInt().toShort()
                        }
                        phase += toneBlock.size
                        if (!writeBlock(track, toneBlock)) break
                    }
                }
            }
        }.apply {
            name = "dtmf-tone-feeder"
            priority = Thread.MAX_PRIORITY
            isDaemon = true
            start()
        }
    }

    private fun writeBlock(track: AudioTrack, block: ShortArray): Boolean {
        return try {
            track.write(block, 0, block.size) > 0
        } catch (_: Exception) {
            false
        }
    }

    /** 按下：只切一个 volatile 标志位，下一块数据（≈5.8ms 内）就变成双音 */
    private fun startTone(key: Char) {
        ensureAudioEngine()
        activeKey = key
    }

    /** 松手：切回静音 */
    private fun stopTone() {
        activeKey = null
    }

    private fun releaseAudioEngine() {
        engineRunning = false
        activeKey = null
        feederThread = null
        toneTrack?.let { t ->
            try {
                t.pause()
                t.flush()
                t.stop()
            } catch (_: Exception) {
            }
            t.release()
        }
        toneTrack = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DtmfApp(
    onPlay: (String, Int, Int) -> Unit,
    onSave: (String, Int, Int, (String) -> Unit) -> Unit,
    onToneStart: (Char) -> Unit,
    onToneStop: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var selectedTab by remember { mutableStateOf(0) }
    var seq by remember { mutableStateOf("") }
    var toneMs by remember { mutableStateOf(100) }
    var toneExpanded by remember { mutableStateOf(false) }
    var gapMs by remember { mutableStateOf(20) }
    var gapExpanded by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // —— 配置（config.json）——
    var configs by remember { mutableStateOf(emptyList<DtmfConfig>()) }
    var selectedConfigId by remember { mutableStateOf(0) }
    var configExpanded by remember { mutableStateOf(false) }
    var showDeleteConfigDialog by remember { mutableStateOf(false) }
    var showImportConfigDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { configs = ConfigStore.load(context) }

    fun saveConfig() {
        val newId = (configs.maxOfOrNull { it.id } ?: 0) + 1
        val newConfig = DtmfConfig(id = newId, number = seq, time = toneMs, gap = gapMs)
        val updated = listOf(newConfig) + configs
        configs = updated
        ConfigStore.save(context, updated)
        selectedConfigId = newId
        configExpanded = false
    }

    fun selectConfig(id: Int) {
        selectedConfigId = id
        configExpanded = false
        if (id == 0) {
            seq = ""
            toneMs = 100
            gapMs = 20
        } else {
            configs.firstOrNull { it.id == id }?.let {
                seq = it.number
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
        ConfigStore.save(context, updated)
        selectedConfigId = 0
        scope.launch { snackbarHostState.showSnackbar("配置已删除") }
    }

    fun importConfigFromUri(uri: android.net.Uri) {
        runCatching {
            // 取显示名（用于校验扩展名）
            var name = ""
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) name = c.getString(idx) ?: ""
            }
            require(name.lowercase(Locale.US).endsWith(".json")) { "扩展名必须是 .json" }
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: error("无法读取文件")
            val parsed = ConfigStore.parse(text) ?: error("配置文件格式不合法")
            ConfigStore.save(context, parsed)
            configs = ConfigStore.load(context)
            selectedConfigId = 0
        }.onSuccess {
            scope.launch { snackbarHostState.showSnackbar("配置已导入，共 ${configs.size} 条") }
        }.onFailure {
            scope.launch { snackbarHostState.showSnackbar("导入失败：" + (it.message ?: "未知错误")) }
        }
    }

    val importConfigLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importConfigFromUri(uri)
    }

    fun exportConfig() {
        // 导出到与音频相同的目录：/storage/emulated/0/DTMF/
        val dir = File(Environment.getExternalStorageDirectory(), "DTMF").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dst = File(dir, "config_${stamp}.json")
        val src = File(context.filesDir, "config.json")
        runCatching {
            if (src.exists()) src.copyTo(dst, overwrite = true) else dst.writeText("[]")
        }.onSuccess {
            scope.launch { snackbarHostState.showSnackbar("配置文件已导出至${dst.absolutePath}") }
        }.onFailure {
            scope.launch { snackbarHostState.showSnackbar("导出失败：${it.message}") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DTMF Generator") },
                actions = {
                    IconButton(onClick = { showImportConfigDialog = true }) {
                        Icon(Icons.Filled.FileDownload, contentDescription = "导入配置")
                    }
                    IconButton(onClick = { exportConfig() }) {
                        Icon(Icons.Filled.FileUpload, contentDescription = "导出配置")
                    }
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "关于")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { focusManager.clearFocus(); selectedTab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "主页") },
                    label = { Text("主页") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { focusManager.clearFocus(); selectedTab = 1 },
                    icon = { DialpadIcon() },
                    label = { Text("拨号键盘") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (selectedTab == 0) {
            HomePage(
                padding = padding,
                seq = seq,
                onSeqChange = { input ->
                    seq = input.filter { c -> c.isDigit() || c == '#' || c == '*' }
                },
                toneMs = toneMs,
                toneExpanded = toneExpanded,
                onToneExpandedChange = { toneExpanded = it },
                onToneSelected = { toneMs = it; toneExpanded = false },
                gapMs = gapMs,
                gapExpanded = gapExpanded,
                onGapExpandedChange = { gapExpanded = it },
                onGapSelected = { gapMs = it; gapExpanded = false },
                onPlay = { focusManager.clearFocus(); onPlay(seq, toneMs, gapMs) },
                onSave = {
                    focusManager.clearFocus()
                    onSave(seq, toneMs, gapMs) { msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                },
                configs = configs,
                selectedConfigId = selectedConfigId,
                configExpanded = configExpanded,
                onConfigExpandedChange = { configExpanded = it },
                onConfigSelected = { id -> selectConfig(id); focusManager.clearFocus() },
                onSaveConfig = { saveConfig(); focusManager.clearFocus() },
                onRequestDeleteConfig = { showDeleteConfigDialog = true },
                focusManager = focusManager
            )
        } else {
            DialpadPage(
                padding = padding,
                onToneStart = onToneStart,
                onToneStop = onToneStop
            )
        }
    }

    if (showImportConfigDialog) {
        AlertDialog(
            onDismissRequest = { showImportConfigDialog = false },
            title = {
                Text(
                    text = "导入配置",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column {
                    Text("请选择文件名为config开头、拓展名为.json的配置文件。")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("导入配置会覆盖所有已保存的配置，请先确保您已经导出当前配置后再谨慎考虑导入。")
                            }
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfigDialog = false }) {
                    Text("取消")
                }
            },
            confirmButton = {
                FilledTonalButton(onClick = {
                    showImportConfigDialog = false
                    importConfigLauncher.launch(arrayOf("*/*"))
                }) {
                    Text("选择")
                }
            }
        )
    }

    if (showDeleteConfigDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfigDialog = false },
            title = {
                Text(
                    text = "删除配置",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = { Text("确定要删除该配置吗？") },
            dismissButton = {
                TextButton(onClick = { showDeleteConfigDialog = false }) {
                    Text("取消")
                }
            },
            confirmButton = {
                FilledTonalButton(onClick = {
                    deleteSelectedConfig()
                    showDeleteConfigDialog = false
                }) {
                    Text("删除")
                }
            }
        )
    }

    if (showAbout) {
        val context = LocalContext.current
        val linkColor = if (isSystemInDarkTheme()) {
            Color(context.resources.getColor(android.R.color.system_accent1_200, context.theme))
        } else {
            Color(context.resources.getColor(android.R.color.system_accent1_500, context.theme))
        }
        val aboutText = buildAnnotatedString {
            append("本应用由")
            withLink(
                LinkAnnotation.Url(
                    "https://github.com/zzChumo",
                    TextLinkStyles(style = SpanStyle(color = linkColor))
                )
            ) {
                append("zzChumo")
            }
            append("制作，DeepSeek辅助完成。")
        }
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = {
                Text(
                    text = "关于",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = { Text(aboutText) },
            confirmButton = {
                FilledTonalButton(onClick = { showAbout = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

/** 主页：输入框 + 两个 Select + 两个按钮 + 底部小字 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomePage(
    padding: PaddingValues,
    seq: String,
    onSeqChange: (String) -> Unit,
    toneMs: Int,
    toneExpanded: Boolean,
    onToneExpandedChange: (Boolean) -> Unit,
    onToneSelected: (Int) -> Unit,
    gapMs: Int,
    gapExpanded: Boolean,
    onGapExpandedChange: (Boolean) -> Unit,
    onGapSelected: (Int) -> Unit,
    onPlay: () -> Unit,
    onSave: () -> Unit,
    configs: List<DtmfConfig>,
    selectedConfigId: Int,
    configExpanded: Boolean,
    onConfigExpandedChange: (Boolean) -> Unit,
    onConfigSelected: (Int) -> Unit,
    onSaveConfig: () -> Unit,
    onRequestDeleteConfig: () -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    var showCustomToneDialog by remember { mutableStateOf(false) }
    var showCustomGapDialog by remember { mutableStateOf(false) }
    var customToneText by remember { mutableStateOf("") }
    var customGapText by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { focusManager.clearFocus() },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // —— 配置 Select ——
        val selectedConfig = configs.firstOrNull { it.id == selectedConfigId }
        ExposedDropdownMenuBox(
            expanded = configExpanded,
            onExpandedChange = onConfigExpandedChange
        ) {
            OutlinedTextField(
                value = if (selectedConfigId == 0) "默认" else (selectedConfig?.label() ?: "默认"),
                onValueChange = {},
                readOnly = true,
                label = { Text("配置") },
                trailingIcon = {
                    if (selectedConfigId != 0) {
                        IconButton(onClick = onRequestDeleteConfig) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除配置")
                        }
                    } else {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = configExpanded)
                    }
                },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = configExpanded,
                onDismissRequest = { onConfigExpandedChange(false) }
            ) {
                DropdownMenuItem(
                    text = { Text("默认") },
                    onClick = { onConfigSelected(0); focusManager.clearFocus() }
                )
                configs.forEach { cfg ->
                    DropdownMenuItem(
                        text = { Text(cfg.label()) },
                        onClick = { onConfigSelected(cfg.id); focusManager.clearFocus() }
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = seq,
            onValueChange = onSeqChange,
            label = { Text("号码") },
            placeholder = { Text("数字、*、#") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        ExposedDropdownMenuBox(
            expanded = toneExpanded,
            onExpandedChange = onToneExpandedChange
        ) {
            OutlinedTextField(
                value = "${toneMs}ms",
                onValueChange = {},
                readOnly = true,
                label = { Text("音频时长") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = toneExpanded)
                },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = toneExpanded,
                onDismissRequest = { onToneExpandedChange(false) }
            ) {
                DropdownMenuItem(
                    text = { Text("100ms") },
                    onClick = { onToneSelected(100); focusManager.clearFocus() }
                )
                DropdownMenuItem(
                    text = { Text("200ms") },
                    onClick = { onToneSelected(200); focusManager.clearFocus() }
                )
                DropdownMenuItem(
                    text = { Text("自定义") },
                    onClick = {
                        customToneText = toneMs.toString()
                        onToneExpandedChange(false)
                        focusManager.clearFocus()
                        showCustomToneDialog = true
                    }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        ExposedDropdownMenuBox(
            expanded = gapExpanded,
            onExpandedChange = onGapExpandedChange
        ) {
            OutlinedTextField(
                value = "${gapMs}ms",
                onValueChange = {},
                readOnly = true,
                label = { Text("音频间隔") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = gapExpanded)
                },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = gapExpanded,
                onDismissRequest = { onGapExpandedChange(false) }
            ) {
                DropdownMenuItem(
                    text = { Text("20ms") },
                    onClick = { onGapSelected(20); focusManager.clearFocus() }
                )
                DropdownMenuItem(
                    text = { Text("50ms") },
                    onClick = { onGapSelected(50); focusManager.clearFocus() }
                )
                DropdownMenuItem(
                    text = { Text("100ms") },
                    onClick = { onGapSelected(100); focusManager.clearFocus() }
                )
                DropdownMenuItem(
                    text = { Text("0ms（实验性）") },
                    onClick = { onGapSelected(0); focusManager.clearFocus() }
                )
                DropdownMenuItem(
                    text = { Text("自定义") },
                    onClick = {
                        customGapText = gapMs.toString()
                        onGapExpandedChange(false)
                        focusManager.clearFocus()
                        showCustomGapDialog = true
                    }
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onPlay,
            enabled = seq.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("播放DTMF")
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
            FilledTonalButton(
                onClick = onSave,
                enabled = seq.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text("保存音频")
            }
            FilledTonalButton(
                onClick = onSaveConfig,
                modifier = Modifier.weight(1f)
            ) {
                Text("保存配置")
            }
        }
        Spacer(Modifier.height(24.dp))
        }   // 滚动内容结束

        // 底部小字：常驻在列底部，不随内容滚动
        Text(
            text = "由DeepSeek辅助完成",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
        Spacer(Modifier.height(8.dp))
    }

    // —— 自定义时长 Dialog ——
    if (showCustomToneDialog) {
        val customTone = customToneText.toIntOrNull()
        AlertDialog(
            onDismissRequest = { showCustomToneDialog = false },
            title = {
                Text(
                    text = "自定义时长",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                OutlinedTextField(
                    value = customToneText,
                    onValueChange = { input ->
                        customToneText = input.filter { c -> c.isDigit() }.take(5)
                    },
                    suffix = { Text("ms") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        customTone?.let { onToneSelected(it) }
                        showCustomToneDialog = false
                    },
                    enabled = customTone != null && customTone in 1..10000
                ) {
                    Text("应用")
                }
            }
        )
    }

    // —— 自定义间隔 Dialog ——
    if (showCustomGapDialog) {
        val customGap = customGapText.toIntOrNull()
        AlertDialog(
            onDismissRequest = { showCustomGapDialog = false },
            title = {
                Text(
                    text = "自定义间隔",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                OutlinedTextField(
                    value = customGapText,
                    onValueChange = { input ->
                        customGapText = input.filter { c -> c.isDigit() }.take(5)
                    },
                    suffix = { Text("ms") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        customGap?.let { onGapSelected(it) }
                        showCustomGapDialog = false
                    },
                    enabled = customGap != null && customGap in 0..10000
                ) {
                    Text("应用")
                }
            }
        )
    }
}

/** 拨号键盘页面（胶囊按键 + 字母副标签） */
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
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { (key, sub) ->
                    DialKey(
                        key = key,
                        sub = sub,
                        modifier = Modifier.weight(1f),
                        onToneStart = onToneStart,
                        onToneStop = onToneStop
                    )
                }
            }
            if (index != rows.lastIndex) Spacer(Modifier.height(14.dp))
        }
    }
}

/** 单个拨号键：胶囊形；按住持续发声，松手停止 */
@Composable
private fun DialKey(
    key: Char,
    sub: String,
    modifier: Modifier = Modifier,
    onToneStart: (Char) -> Unit,
    onToneStop: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(78.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .indication(
                interactionSource = interactionSource,
                indication = ripple(color = Color.Gray.copy(alpha = 0.4f))
            )
            .pointerInput(key) {
                detectTapGestures(
                    onPress = { pressOffset ->
                        // 用真实手指落点做涟漪原点，而不是 (0,0)
                        val press = PressInteraction.Press(pressOffset)
                        interactionSource.emit(press)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToneStart(key)
                        val released = tryAwaitRelease()
                        if (released) {
                            interactionSource.emit(PressInteraction.Release(press))
                        } else {
                            interactionSource.emit(PressInteraction.Cancel(press))
                        }
                        onToneStop()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = key.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            // 固定高度的副标签槽位：没有字母的键也占位，保证所有数字处于同一高度
            Box(
                modifier = Modifier.height(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (sub.isNotEmpty()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 拨号盘图标：3x3 圆点 + 底部中间一个圆点 */
@Composable
private fun DialpadIcon() {
    val color = LocalContentColor.current
    Canvas(modifier = Modifier.size(24.dp)) {
        val radius = size.minDimension * 0.075f
        val xStep = size.width / 4f
        val yStep = size.height / 5f
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                drawCircle(
                    color = color,
                    radius = radius,
                    center = Offset(xStep * (col + 1), yStep * (row + 1))
                )
            }
        }
        drawCircle(
            color = color,
            radius = radius,
            center = Offset(xStep * 2, yStep * 4)
        )
    }
}

