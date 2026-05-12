package com.mine.bookinghelper.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.mine.bookinghelper.data.AppDatabase
import kotlinx.coroutines.*

class BookingAccessibilityService : AccessibilityService() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val db by lazy { AppDatabase.getDatabase(applicationContext).userDetailDao() }

    companion object {
        const val TAG = "BookingHelper"
        var isRunning = false
    }

    // ── Broadcast receiver ────────────────────────────────────────────────────

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Broadcast: ${intent?.action}")
            serviceScope.launch {
                when (intent?.action) {
                    FloatingOverlayService.ACTION_FILL_PERSON -> {
                        val idx = intent.getIntExtra(FloatingOverlayService.EXTRA_P_INDEX, 1)
                        fillPersonForm(idx)
                    }
                    FloatingOverlayService.ACTION_BURST_GEN  -> fillGeneralForm()
                    FloatingOverlayService.ACTION_DEBUG_SCAN -> dumpTree()
                    FloatingOverlayService.ACTION_FILL_ALL   -> fillAll()
                    FloatingOverlayService.ACTION_MEASURE    -> measureLayout()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        val filter = IntentFilter().apply {
            addAction(FloatingOverlayService.ACTION_FILL_PERSON)
            addAction(FloatingOverlayService.ACTION_BURST_GEN)
            addAction(FloatingOverlayService.ACTION_DEBUG_SCAN)
            addAction(FloatingOverlayService.ACTION_FILL_ALL)
            addAction(FloatingOverlayService.ACTION_MEASURE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
        toast("BookingHelper ready")
    }

    // ── Fill All ──────────────────────────────────────────────────────────────
    //
    // TTD WebView EditText field order:
    //   [0] Email  [1] City  [2] State  [3] Country  [4] Pincode
    //   [5+(P-1)*5 + 0] Name
    //   [5+(P-1)*5 + 1] Age
    //   [5+(P-1)*5 + 2] Gender    ← readonly, user selects manually
    //   [5+(P-1)*5 + 3] ID Type   ← readonly, user selects manually
    //   [5+(P-1)*5 + 4] ID Number ← enabled only after ID Type selected
    //
    // ACTION_SET_TEXT works on off-screen nodes — no scrolling needed.

    private suspend fun fillAll() {
        val gen = withContext(Dispatchers.IO) { db.getGeneralDetailsOnce() }
        val persons = withContext(Dispatchers.IO) {
            db.getAllPersonsOnce()
                .filter { it.personIndex in 1..4 }
                .sortedBy { it.personIndex }
        }

        if (gen == null && persons.isEmpty()) {
            toast("❌ No data saved — open app and add details first")
            return
        }

        val inputs = getInputNodes() ?: return
        toast("Filling…")

        // General details
        if (gen != null && inputs.size >= 5) {
            setText(inputs[0], gen.email)
            setText(inputs[1], gen.city)
            setText(inputs[2], gen.state)
            setText(inputs[3], gen.country)
            setText(inputs[4], gen.pincode)
        }

        // Person name + age (ID number skipped — requires ID type dropdown first)
        for (person in persons) {
            val base = 5 + (person.personIndex - 1) * 5
            if (inputs.size < base + 2) continue
            setText(inputs[base],     person.name)
            setText(inputs[base + 1], person.age)
        }

        toast("✅ General + name/age filled. Do dropdowns, then tap P1–P4 for Aadhaar.")
    }

    // ── Fill one person's text fields ─────────────────────────────────────────
    //
    // Called when user taps a person card on the overlay AFTER selecting
    // Gender and ID Type manually. Fills name, age, and Aadhaar number.

    private suspend fun fillPersonForm(pIndex: Int) {
        val person = withContext(Dispatchers.IO) {
            db.getAllPersonsOnce().find { it.personIndex == pIndex }
        } ?: run {
            toast("❌ No data for P$pIndex — save in app first")
            return
        }

        val inputs = getInputNodes() ?: return
        val base = 5 + (pIndex - 1) * 5

        if (inputs.size < base + 5) {
            toast("❌ Not enough fields (${inputs.size}). Are you on the booking form?")
            return
        }

        setText(inputs[base],     person.name)
        setText(inputs[base + 1], person.age)

        val idNode = inputs[base + 4]
        if (idNode.isEnabled) {
            val ok = setText(idNode, person.idNumber)
            toast(if (ok) "✅ P$pIndex filled!" else "⚠️ P$pIndex: name+age set, Aadhaar failed")
        } else {
            toast("⚠️ P$pIndex: name+age set. Select ID Type first, then tap P$pIndex again.")
        }
    }

    // ── General details only ──────────────────────────────────────────────────

    private suspend fun fillGeneralForm() {
        val gen = withContext(Dispatchers.IO) { db.getGeneralDetailsOnce() } ?: run {
            toast("❌ No general details saved"); return
        }
        val inputs = getInputNodes() ?: return
        if (inputs.size < 5) {
            toast("❌ Only ${inputs.size} fields — on the booking form?"); return
        }
        toast("Filling general…")
        setText(inputs[0], gen.email)
        setText(inputs[1], gen.city)
        setText(inputs[2], gen.state)
        setText(inputs[3], gen.country)
        setText(inputs[4], gen.pincode)
        toast("✅ General done!")
    }

    // ── Node collection ───────────────────────────────────────────────────────
    //
    // Searches ALL windows (not just rootInActiveWindow) because after tapping
    // the floating overlay, the overlay window may hold accessibility focus,
    // hiding the TTD WebView from rootInActiveWindow.

    private fun getInputNodes(): List<AccessibilityNodeInfo>? {
        val nodes = collectEditTextNodes()
        Log.d(TAG, "EditText nodes found: ${nodes.size}")
        if (nodes.isEmpty()) {
            toast("❌ No fields found — navigate to the TTD booking form first")
            return null
        }
        return nodes
    }

    private fun collectEditTextNodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val roots = windows?.mapNotNull { it.root } ?: listOfNotNull(rootInActiveWindow)
        fun walk(node: AccessibilityNodeInfo) {
            if (node.className?.toString()?.endsWith("EditText") == true) result.add(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it) }
        }
        roots.forEach { walk(it) }
        return result
    }

    // ── Text helpers ──────────────────────────────────────────────────────────

    private fun setText(node: AccessibilityNodeInfo, value: String): Boolean {
        if (value.isBlank()) return false
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        Log.d(TAG, "setText '${value.take(20)}' → $ok")
        return ok
    }

    // ── Layout measurement (diagnostic) ──────────────────────────────────────

    private fun measureLayout() {
        val nodes = collectEditTextNodes()
        if (nodes.isEmpty()) { toast("No fields found"); return }
        val rects = nodes.map { Rect().also { r -> it.getBoundsInScreen(r) } }
        val anchorY = rects[0].centerY()
        Log.d(TAG, "===== LAYOUT (${nodes.size} fields) =====")
        rects.forEachIndexed { i, r ->
            Log.d(TAG, "[$i] cy=${r.centerY()} h=${r.height()} offset=${r.centerY() - anchorY}")
        }
        toast("MEASURE: ${nodes.size} fields — check logcat")
    }

    // ── Debug tree scan ───────────────────────────────────────────────────────

    private fun dumpTree() {
        Log.d(TAG, "========== SCAN START ==========")
        var total = 0
        val roots = windows?.mapIndexedNotNull { wi, win ->
            Log.d(TAG, "--- Window $wi: type=${win.type} active=${win.isActive} title='${win.title}' ---")
            win.root
        } ?: listOfNotNull(rootInActiveWindow)
        roots.forEach { total += walkNode(it, 0) }
        Log.d(TAG, "========== SCAN END — $total nodes ==========")
        toast("SCAN: $total nodes — check logcat")
    }

    private fun walkNode(node: AccessibilityNodeInfo, depth: Int): Int {
        val interesting = node.isEditable || node.isClickable ||
                !node.hintText.isNullOrEmpty() ||
                !node.contentDescription.isNullOrEmpty() ||
                !node.viewIdResourceName.isNullOrEmpty() ||
                !node.text.isNullOrEmpty()
        var count = 0
        if (interesting) {
            count++
            Log.d(TAG, "d$depth | ${node.className?.toString()?.substringAfterLast('.')} " +
                "edit=${node.isEditable} click=${node.isClickable} en=${node.isEnabled} " +
                "hint='${node.hintText}' desc='${node.contentDescription}' " +
                "text='${node.text}' id='${node.viewIdResourceName}'")
        }
        for (i in 0 until node.childCount) node.getChild(i)?.let { count += walkNode(it, depth + 1) }
        return count
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    private fun toast(msg: String) =
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        serviceJob.cancel()
    }
}
