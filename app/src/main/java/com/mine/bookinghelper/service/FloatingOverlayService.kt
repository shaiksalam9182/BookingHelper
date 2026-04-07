package com.mine.bookinghelper.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mine.bookinghelper.R
import kotlin.math.abs

class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var params: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val layoutInflater = LayoutInflater.from(this)
        overlayView = layoutInflater.inflate(R.layout.overlay_button, null)

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
            x = 100
            y = 100
        }

        val btnFill = overlayView!!.findViewById<FloatingActionButton>(R.id.btn_fill)
        val btnClose = overlayView!!.findViewById<FloatingActionButton>(R.id.btn_close)

        btnFill.setOnClickListener {
            android.util.Log.d("FloatingOverlay", "Fill button clicked")
            sendFillBroadcast()
        }

        btnClose.setOnClickListener {
            stopSelf()
        }
        
        // Touch listener for dragging on the main layout container
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
                        return true
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

        windowManager.addView(overlayView, params)
    }

    private fun sendFillBroadcast() {
        val intent = Intent(BookingAccessibilityService.ACTION_FILL_FORM)
        // Ensure the package name is specified for safety and visibility
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { windowManager.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
