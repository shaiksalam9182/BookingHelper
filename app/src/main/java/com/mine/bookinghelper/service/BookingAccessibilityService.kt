package com.mine.bookinghelper.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.mine.bookinghelper.data.AppDatabase
import kotlinx.coroutines.*

class BookingAccessibilityService : AccessibilityService() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    companion object {
        const val TAG = "BookingHelper"
        var isRunning = false
    }

    private val ribbonReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            serviceScope.launch {
                when (intent?.action) {
                    FloatingOverlayService.ACTION_TYPE_DATA -> {
                        val value = intent.getStringExtra(FloatingOverlayService.EXTRA_VALUE)
                        if (value != null) typeText(value)
                    }
                    FloatingOverlayService.ACTION_BURST_GEN -> runGeneralBurst()
                    FloatingOverlayService.ACTION_BURST_PERSON -> {
                        val pIndex = intent.getIntExtra(FloatingOverlayService.EXTRA_P_INDEX, 1)
                        runPersonBurst(pIndex)
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.d(TAG, "✅ Accessibility Service Connected")
        
        val filter = IntentFilter().apply {
            addAction(FloatingOverlayService.ACTION_TYPE_DATA)
            addAction(FloatingOverlayService.ACTION_BURST_GEN)
            addAction(FloatingOverlayService.ACTION_BURST_PERSON)
        }
        
        // FIX: Mandatory flag for Android 14+ (S25 Ultra)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(ribbonReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(ribbonReceiver, filter)
        }
    }

    private suspend fun runGeneralBurst() {
        val general = AppDatabase.getDatabase(applicationContext).userDetailDao().getGeneralDetailsOnce() ?: return
        val data = listOf(general.email, general.city, general.state, general.country, general.pincode)
        for (value in data) {
            if (value.isNotEmpty()) {
                typeText(value)
                delay(400); sendTab(); delay(400)
            }
        }
    }

    private suspend fun runPersonBurst(pIndex: Int) {
        val persons = AppDatabase.getDatabase(applicationContext).userDetailDao().getAllPersonsOnce()
        val p = persons.find { it.personIndex == pIndex } ?: return
        typeText(p.name)
        delay(400); sendTab(); delay(400)
        typeText(p.age)
        delay(400); sendTab()
    }

    private fun typeText(text: String) {
        val root = rootInActiveWindow ?: return
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null) {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            focused.recycle()
        }
        root.recycle()
    }

    private fun sendTab() {
        val root = rootInActiveWindow ?: return
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        focused?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        focused?.recycle()
        root.recycle()
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent) {
        // Log activity to confirm the service is alive
        if (event.eventType == android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED) {
            Log.v(TAG, "Interaction detected: ${event.packageName}")
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try { unregisterReceiver(ribbonReceiver) } catch (e: Exception) {}
        serviceJob.cancel()
    }
}
