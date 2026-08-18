package dev.ujhhgtg.wekit.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ujhhgtg.wekit.service.ReadReceiptService
import java.io.File

class ReadReceiptActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ReadReceiptService.ACTION_STATUS) return
            isRunning = intent.getBooleanExtra(ReadReceiptService.EXTRA_RUNNING, false)
            intent.getStringExtra(ReadReceiptService.EXTRA_TUNNEL_URL)?.let {
                tunnelUrl = it
            }
        }
    }

    private var isRunning by mutableStateOf(false)
    private var tunnelUrl by mutableStateOf<String?>(null)
    private var tunnelMode by mutableStateOf(ReadReceiptService.MODE_TUNNEL)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tunnelMode = getSharedPreferences(ReadReceiptService.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(ReadReceiptService.PREF_MODE, ReadReceiptService.MODE_TUNNEL)
            ?: ReadReceiptService.MODE_TUNNEL
        setContent {
            MaterialTheme {
                ReadReceiptScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val filter = IntentFilter(ReadReceiptService.ACTION_STATUS)
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(receiver, filter)
            }
            handler.postDelayed({ queryStatus() }, 500)
        } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(receiver) }
    }

    private fun queryStatus() {
        val intent = Intent(this, ReadReceiptService::class.java).apply {
            action = ReadReceiptService.ACTION_STATUS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    private fun setMode(mode: String) {
        tunnelMode = mode
        getSharedPreferences(ReadReceiptService.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(ReadReceiptService.PREF_MODE, mode).apply()
        val intent = Intent(this, ReadReceiptService::class.java).apply {
            action = ReadReceiptService.ACTION_START
            putExtra(ReadReceiptService.EXTRA_MODE, mode)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    private fun toggleService() {
        val intent = Intent(this, ReadReceiptService::class.java)
        if (isRunning) {
            intent.action = ReadReceiptService.ACTION_STOP
            startService(intent)
        } else {
            intent.action = ReadReceiptService.ACTION_START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
        }
    }

    @Composable
    private fun ReadReceiptScreen() {
        var logText by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            while (true) {
                runCatching {
                    val f = File(filesDir, "cloudflared.log")
                    if (f.exists()) logText = f.readText().takeLast(6000)
                }
                kotlinx.coroutines.delay(1500)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "已读追踪",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            // 模式选择
            Text("服务模式", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = tunnelMode == ReadReceiptService.MODE_TUNNEL,
                    onClick = { setMode(ReadReceiptService.MODE_TUNNEL) },
                    label = { Text("内置隧道") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = tunnelMode == ReadReceiptService.MODE_LOCAL,
                    onClick = { setMode(ReadReceiptService.MODE_LOCAL) },
                    label = { Text("本地") },
                    modifier = Modifier.weight(1f)
                )
            }

            // 状态卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (tunnelUrl != null) Color(0xFF1a3a2a)
                    else if (isRunning) Color(0xFF2a2a1a) else Color(0xFF3a1a1a)
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = when {
                            tunnelUrl != null && tunnelMode == ReadReceiptService.MODE_LOCAL ->
                                "● 本地服务已就绪"
                            tunnelUrl != null -> "● 隧道已就绪"
                            isRunning && tunnelMode == ReadReceiptService.MODE_LOCAL ->
                                "● 本地服务启动中..."
                            isRunning -> "● 服务启动中，正在建立隧道..."
                            else -> "● 服务未启动"
                        },
                        color = when {
                            tunnelUrl != null -> Color(0xFF3fb950)
                            isRunning -> Color(0xFFf0b429)
                            else -> Color(0xFFf85149)
                        },
                        fontWeight = FontWeight.Bold
                    )
                    tunnelUrl?.let { url ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = url,
                            color = Color(0xFF58a6ff),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                val cm = getSystemService(Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                                cm.setPrimaryClip(
                                    android.content.ClipData.newPlainText("tunnel", url)
                                )
                            }) { Text("复制地址") }
                            TextButton(onClick = {
                                startActivity(
                                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                )
                            }) { Text("打开控制台") }
                        }
                    }
                }
            }

            Button(
                onClick = { toggleService() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Color(0xFFf85149) else Color(0xFF3fb950)
                )
            ) {
                Text(if (isRunning) "停止服务" else "启动服务")
            }

            if (tunnelMode == ReadReceiptService.MODE_TUNNEL) {
                HorizontalDivider()

                Text("运行日志", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp),
                    color = Color(0xFF0d1117),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = logText.ifEmpty { "（暂无日志）" },
                        fontSize = 10.sp,
                        color = Color(0xFF8b949e),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Button(
                onClick = {
                    val url = tunnelUrl ?: "http://127.0.0.1:${ReadReceiptService.PORT}/"
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("打开 Web 控制台") }
        }
    }
}
