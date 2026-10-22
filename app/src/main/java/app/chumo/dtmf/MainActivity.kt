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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    onSave = ::save
                )
            }
        }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DtmfApp(
    onPlay: (String, Int, Int) -> Unit,
    onSave: (String, Int, Int, (String) -> Unit) -> Unit
) {
    var seq by remember { mutableStateOf("") }
    var toneMs by remember { mutableStateOf(100) }
    var toneExpanded by remember { mutableStateOf(false) }
    var gapMs by remember { mutableStateOf(20) }
    var gapExpanded by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DTMF Generator") },
                actions = {
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "关于")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = seq,
                onValueChange = { input ->
                    seq = input.filter { c -> c.isDigit() || c == '#' || c == '*' }
                },
                label = { Text("号码（数字、#、*）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            ExposedDropdownMenuBox(
                expanded = toneExpanded,
                onExpandedChange = { toneExpanded = it }
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
                    onDismissRequest = { toneExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("100ms") },
                        onClick = { toneMs = 100; toneExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("200ms") },
                        onClick = { toneMs = 200; toneExpanded = false }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            ExposedDropdownMenuBox(
                expanded = gapExpanded,
                onExpandedChange = { gapExpanded = it }
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
                    onDismissRequest = { gapExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("20ms") },
                        onClick = { gapMs = 20; gapExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("50ms") },
                        onClick = { gapMs = 50; gapExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("100ms") },
                        onClick = { gapMs = 100; gapExpanded = false }
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onPlay(seq, toneMs, gapMs) },
                enabled = seq.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("播放DTMF")
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    onSave(seq, toneMs, gapMs) { msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                },
                enabled = seq.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存")
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "由DeepSeek辅助完成",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
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
            text = { Text(aboutText) },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text("关闭")
                }
            }
        )
    }
}
