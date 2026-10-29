package com.example.tvmp3

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * Hanya dipakai untuk satu hal: menidurkan layar TV (setara tombol power)
 * tanpa menghentikan PlaybackService. Tidak membaca isi layar.
 */
class ScreenOffService : AccessibilityService() {

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }

    fun turnOffScreen() {
        if (Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    companion object { var instance: ScreenOffService? = null }
}
