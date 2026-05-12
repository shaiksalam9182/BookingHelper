package com.mine.bookinghelper.service

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.text.TextUtils
import android.util.TypedValue
import android.view.*
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.mine.bookinghelper.R
import com.mine.bookinghelper.data.AppDatabase
import kotlinx.coroutines.*

class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var params: WindowManager.LayoutParams
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val db by lazy { AppDatabase.getDatabase(applicationContext).userDetailDao() }

    private var expanded = false

    companion object {
        const val ACTION_TYPE_DATA   = "com.mine.bookinghelper.TYPE_DATA"
        const val ACTION_BURST_GEN   = "com.mine.bookinghelper.BURST_GEN"
        const val ACTION_FILL_PERSON = "com.mine.bookinghelper.FILL_PERSON"
        const val ACTION_DEBUG_SCAN  = "com.mine.bookinghelper.DEBUG_SCAN"
        const val ACTION_FILL_ALL    = "com.mine.bookinghelper.FILL_ALL"
        const val ACTION_MEASURE     = "com.mine.bookinghelper.MEASURE"
        const val EXTRA_VALUE        = "extra_value"
        const val EXTRA_P_INDEX      = "extra_p_index"
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        try {
            val ctx = android.view.ContextThemeWrapper(this, R.style.Theme_BookingHelper)
            overlayView = LayoutInflater.from(ctx).inflate(R.layout.overlay_button, null)

            params = WindowManager.LayoutParams(
                dp(88),   // collapsed width — small pill
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 300
            }

            val header    = overlayView!!.findViewById<LinearLayout>(R.id.header_row)
            val container = overlayView!!.findViewById<LinearLayout>(R.id.container_suggestions)
            val closeBtn  = overlayView!!.findViewById<ImageButton>(R.id.btn_close_bar)

            closeBtn.setOnClickListener { stopSelf() }
            attachDragAndTap(header) { toggle(container, closeBtn) }

            // Buttons built async (DB load for person names/aadhaar)
            buildButtons(container)

            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            Toast.makeText(this, "Overlay error: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        overlayView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Expand / Collapse ─────────────────────────────────────────────────────

    private fun toggle(container: LinearLayout, closeBtn: ImageButton) {
        expanded = !expanded
        val label = overlayView?.findViewById<TextView>(R.id.tv_bar_label)

        if (expanded) {
            params.width = dp(152)
            container.visibility = View.VISIBLE
            closeBtn.visibility  = View.VISIBLE
            label?.text    = "TTD Fill"
            label?.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        } else {
            params.width = dp(88)
            container.visibility = View.GONE
            closeBtn.visibility  = View.GONE
            label?.text    = "⚡ TTD"
            label?.gravity = Gravity.CENTER
        }

        overlayView?.post {
            try { windowManager.updateViewLayout(overlayView, params) } catch (_: Exception) {}
        }
    }

    /** Fire an action broadcast then auto-collapse after a short delay. */
    private fun fireAndCollapse(action: () -> Unit) {
        action()
        serviceScope.launch {
            delay(200)
            val container = overlayView?.findViewById<LinearLayout>(R.id.container_suggestions) ?: return@launch
            val closeBtn  = overlayView?.findViewById<ImageButton>(R.id.btn_close_bar) ?: return@launch
            if (expanded) toggle(container, closeBtn)
        }
    }

    // ── Build buttons ─────────────────────────────────────────────────────────

    private fun buildButtons(container: LinearLayout) {
        // ⚡ FILL ALL — always present
        addBtn(container, "⚡  FILL ALL", 0xFF1565C0.toInt()) {
            fireAndCollapse { sendBroadcast(Intent(ACTION_FILL_ALL).setPackage(packageName)) }
        }

        // Person cards — only for persons that are saved in the DB
        serviceScope.launch {
            val persons = withContext(Dispatchers.IO) {
                db.getAllPersonsOnce()
                    .filter { it.personIndex in 1..4 }
                    .sortedBy { it.personIndex }
            }

            if (persons.isEmpty()) return@launch   // nothing to show

            val bgColors = listOf(
                0xFF0D47A1.toInt(),
                0xFF4A148C.toInt(),
                0xFF1B5E20.toInt(),
                0xFF880E4F.toInt()
            )

            for (p in persons) {
                val name   = p.name.trim().take(10)
                val idTail = if (p.idNumber.length >= 4) "••${p.idNumber.takeLast(4)}" else "——"
                addPersonCard(container, "P${p.personIndex}", name, idTail,
                    bgColors[p.personIndex - 1]
                ) {
                    fireAndCollapse {
                        sendBroadcast(
                            Intent(ACTION_FILL_PERSON).setPackage(packageName)
                                .putExtra(EXTRA_P_INDEX, p.personIndex)
                        )
                    }
                }
            }
        }
    }

    // ── View factories ────────────────────────────────────────────────────────

    private fun addBtn(container: LinearLayout, label: String, bg: Int, onClick: () -> Unit) {
        container.addView(TextView(applicationContext).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(dp(10), 0, dp(10), 0)
            background = roundedBg(bg)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(38)
            ).apply { topMargin = dp(5) }
            setOnClickListener { onClick() }
        })
    }

    private fun addPersonCard(
        container: LinearLayout,
        badge: String, name: String, idTail: String,
        bg: Int, onClick: () -> Unit
    ) {
        val row = LinearLayout(applicationContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = roundedBg(bg)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(5) }
            setPadding(dp(8), dp(7), dp(8), dp(7))
            setOnClickListener { onClick() }
        }

        // "P1" badge
        row.addView(TextView(applicationContext).apply {
            text = badge
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(22), LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { marginEnd = dp(5) }
        })

        // Name (truncated)
        row.addView(TextView(applicationContext).apply {
            text = name
            setTextColor(Color.WHITE)
            textSize = 11f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        // Aadhaar last 4
        row.addView(TextView(applicationContext).apply {
            text = idTail
            setTextColor(0xBBFFFFFF.toInt())
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(4) }
        })

        container.addView(row)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun roundedBg(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(8).toFloat()
        setColor(color)
    }

    private fun dp(value: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    // ── Drag + tap ────────────────────────────────────────────────────────────
    //
    // If finger moved > 6dp it's a drag; otherwise treat release as a tap.

    private fun attachDragAndTap(view: View, onTap: () -> Unit) {
        var wx = 0; var wy = 0       // window position at touch-down
        var rx = 0f; var ry = 0f     // raw screen position at touch-down
        var dragged = false

        view.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    wx = params.x; wy = params.y
                    rx = ev.rawX;  ry = ev.rawY
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - rx).toInt()
                    val dy = (ev.rawY - ry).toInt()
                    if (!dragged && (kotlin.math.abs(dx) > dp(6) || kotlin.math.abs(dy) > dp(6)))
                        dragged = true
                    if (dragged) {
                        params.x = wx + dx
                        params.y = wy + dy
                        try { windowManager.updateViewLayout(overlayView, params) }
                        catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) onTap()
                    true
                }
                else -> false
            }
        }
    }
}
