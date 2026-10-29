package com.example.tvmp3

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Service foreground: musik tetap jalan walau layar mati / TV standby / Activity ditutup.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true // audio focus
            )
            .setHandleAudioBecomingNoisy(true)   // pause jika Bluetooth terputus
            .setWakeMode(C.WAKE_MODE_LOCAL)      // CPU tidak tidur saat memutar
            .build()
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    // Jangan berhenti walau aplikasi di-swipe dari recent apps
    override fun onTaskRemoved(rootIntent: Intent?) {}

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
