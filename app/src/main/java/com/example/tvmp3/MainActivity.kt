package com.example.tvmp3

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private var controller: MediaController? = null
    private var songs by mutableStateOf<List<MediaItem>>(emptyList())
    private var current by mutableStateOf<MediaItem?>(null)
    private var playing by mutableStateOf(false)
    private var shuffle by mutableStateOf(false)
    private var repeatAll by mutableStateOf(false)

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            songs = MusicRepository.load(this)
        }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) { current = item }
        override fun onShuffleModeEnabledChanged(enabled: Boolean) { shuffle = enabled }
        override fun onRepeatModeChanged(mode: Int) { repeatAll = mode == Player.REPEAT_MODE_ALL }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Screen() } }

        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            perms += Manifest.permission.READ_MEDIA_AUDIO
            perms += Manifest.permission.POST_NOTIFICATIONS
        } else perms += Manifest.permission.READ_EXTERNAL_STORAGE
        permLauncher.launch(perms.toTypedArray())

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            controller = future.get().also { c ->
                c.addListener(listener)
                playing = c.isPlaying
                current = c.currentMediaItem
                shuffle = c.shuffleModeEnabled
                repeatAll = c.repeatMode == Player.REPEAT_MODE_ALL
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        controller?.removeListener(listener)
        controller?.release()
        super.onDestroy()
    }

    // ---- Layar mati sungguhan (panel TV sleep), musik tetap jalan ----
    private fun screenOffNow() {
        val svc = ScreenOffService.instance
        if (svc != null) {
            svc.turnOffScreen()
        } else {
            Toast.makeText(this,
                "Aktifkan dulu: Pengaturan > Aksesibilitas > TV MP3 Player Screen Off",
                Toast.LENGTH_LONG).show()
            try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (e: Exception) {}
        }
    }

    private fun playAt(index: Int) {
        val c = controller ?: return
        c.setMediaItems(songs, index, 0L)
        c.prepare(); c.play()
    }

    private fun openBluetooth() {
        try { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            catch (e2: Exception) {
                Toast.makeText(this, "Buka Pengaturan TV > Remote & Aksesori", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun Modifier.tvFocus() = composed {
        var f by remember { mutableStateOf(false) }
        this.onFocusChanged { f = it.hasFocus }
            .border(if (f) 3.dp else 0.dp, Color.White, RoundedCornerShape(10.dp))
    }

    private fun fmt(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(s / 60, s % 60)
    }

    @Composable
    private fun Screen() {
        var pos by remember { mutableStateOf(0L) }
        var dur by remember { mutableStateOf(0L) }
        LaunchedEffect(Unit) {
            while (true) {
                pos = controller?.currentPosition ?: 0L
                dur = controller?.duration?.takeIf { it > 0 } ?: 0L
                delay(500)
            }
        }

        Box(Modifier.fillMaxSize().background(Color(0xFF10131A))) {
            Row(Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                // Daftar lagu
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Text("Daftar Lagu (${songs.size})", fontSize = 22.sp, color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    if (songs.isEmpty()) {
                        Text("Tidak ada lagu ditemukan. Salin file MP3 ke penyimpanan TV atau colokkan flashdisk.",
                            color = Color.LightGray)
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(songs) { i, s ->
                            val sel = current?.mediaId == s.mediaId
                            Column(
                                Modifier.fillMaxWidth().tvFocus()
                                    .background(if (sel) Color(0xFF2B3A5E) else Color(0xFF181C26), RoundedCornerShape(10.dp))
                                    .clickable { playAt(i) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(s.mediaMetadata.title?.toString() ?: "", color = Color.White,
                                    fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val a = s.mediaMetadata.artist?.toString().orEmpty()
                                if (a.isNotEmpty()) Text(a, color = Color.Gray, fontSize = 14.sp, maxLines = 1)
                            }
                        }
                    }
                }

                // Kontrol
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    Text("Sedang diputar", color = Color.Gray, fontSize = 16.sp)
                    Text(current?.mediaMetadata?.title?.toString() ?: "-", color = Color.White,
                        fontSize = 28.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(current?.mediaMetadata?.artist?.toString().orEmpty(), color = Color.LightGray, fontSize = 18.sp)
                    Spacer(Modifier.height(20.dp))
                    LinearProgressIndicator(
                        progress = { if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("${fmt(pos)} / ${fmt(dur)}", color = Color.Gray, fontSize = 14.sp)
                    Spacer(Modifier.height(20.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button({ controller?.seekToPreviousMediaItem() }, Modifier.tvFocus()) { Text("⏮") }
                        Button({ controller?.seekBack() }, Modifier.tvFocus()) { Text("-10d") }
                        Button({ controller?.let { if (it.isPlaying) it.pause() else it.play() } },
                            Modifier.tvFocus()) { Text(if (playing) "⏸ Jeda" else "▶ Putar") }
                        Button({ controller?.seekForward() }, Modifier.tvFocus()) { Text("+10d") }
                        Button({ controller?.seekToNextMediaItem() }, Modifier.tvFocus()) { Text("⏭") }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button({ controller?.shuffleModeEnabled = !shuffle }, Modifier.tvFocus()) {
                            Text(if (shuffle) "Acak: ON" else "Acak: OFF")
                        }
                        Button({ controller?.repeatMode =
                            if (repeatAll) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL }, Modifier.tvFocus()) {
                            Text(if (repeatAll) "Ulang: ON" else "Ulang: OFF")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button({ openBluetooth() }, Modifier.tvFocus()) { Text("Bluetooth") }
                        Button({ screenOffNow() }, Modifier.tvFocus()) { Text("Matikan Layar") }
                    }
                }
            }

        }
    }
}
