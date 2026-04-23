package com.mine.bookinghelper.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.mine.bookinghelper.R
import com.mine.bookinghelper.data.AppDatabase
import kotlinx.coroutines.*

class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var params: WindowManager.LayoutParams
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    companion object {
        const val ACTION_TYPE_DATA = "com.mine.bookinghelper.TYPE_DATA"
        const val ACTION_BURST_GEN = "com.mine.bookinghelper.BURST_GEN"
        const val ACTION_BURST_PERSON = "com.mine.bookinghelper.BURST_PERSON"
        const val EXTRA_VALUE = "extra_value"
        const val EXTRA_P_INDEX = "extra_p_index"
        private const val TAG = "BookingHelper"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Overlay Service onCreate")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        try {
            val contextThemeWrapper = ContextThemeWrapper(this, R.style.Theme_BookingHelper)
            overlayView = LayoutInflater.from(contextThemeWrapper).inflate(R.layout.overlay_button, null)

            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 500 // Start in the middle of the screen
            }

            val container = overlayView!!.findViewById<LinearLayout>(R.id.container_suggestions)
            val btnClose = overlayView!!.findViewById<ImageButton>(R.id.btn_close_bar)
            btnClose.setOnClickListener { stopSelf() }

            // Enhanced drag logic for both X and Y
            overlayView!!.setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                
                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            return false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            params.x = initialX + (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager.updateViewLayout(overlayView, params)
                            return true
                        }
                    }
                    return false
                }
            })

            loadRibbonData(container)
            windowManager.addView(overlayView, params)
            Log.d(TAG, "Overlay View added successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding overlay view", e)
            Toast.makeText(this, "Failed to show overlay: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun loadRibbonData(container: LinearLayout) {
        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext).userDetailDao()
            val persons = db.getAllPersonsOnce()

            container.removeAllViews()

            // 1. ⚡ TURBO GENERAL FILL
            addChip(container, "⚡ GEN", 0xFFE91E63.toInt()) {
                sendBroadcast(Intent(ACTION_BURST_GEN).setPackage(packageName))
            }

            // 2. 👤 PERSON MACROS
            persons.forEach { p ->
                val color = if (p.personIndex % 2 == 0) 0xFF004488.toInt() else 0xFF006622.toInt()
                
                // Person Start Macro
                addChip(container, "👤 P${p.personIndex} START", color) {
                    val intent = Intent(ACTION_BURST_PERSON).setPackage(packageName)
                    intent.putExtra(EXTRA_P_INDEX, p.personIndex)
                    sendBroadcast(intent)
                }
                
                // ID Number Only
                addChip(container, "🆔 P${p.personIndex} NUM", color) {
                    val intent = Intent(ACTION_TYPE_DATA).setPackage(packageName)
                    intent.putExtra(EXTRA_VALUE, p.idNumber)
                    sendBroadcast(intent)
                }
            }
        }
    }

    private fun addChip(container: LinearLayout, label: String, color: Int, onClick: () -> Unit) {
        val contextThemeWrapper = ContextThemeWrapper(this, R.style.Theme_BookingHelper)
        val btn = MaterialButton(contextThemeWrapper).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                40.dpToPx()
            ).apply { setMargins(8, 0, 8, 0) }
            text = label
            setPadding(16, 0, 16, 0)
            setOnClickListener { onClick() }
            cornerRadius = 20.dpToPx()
            setBackgroundColor(color)
            setTextColor(android.graphics.Color.WHITE)
            textSize = 10f
            isAllCaps = false
        }
        container.addView(btn)
    }

    private fun Int.dpToPx() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), resources.displayMetrics
    ).toInt()

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        overlayView?.let { 
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
