package com.mine.bookinghelper.service

import android.accessibilityservice.AccessibilityService
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
import com.mine.bookinghelper.model.UserDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class BookingAccessibilityService : AccessibilityService() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    companion object {
        const val ACTION_FILL_FORM = "com.mine.bookinghelper.ACTION_FILL_FORM"
        private const val TAG = "BookingAccessibility"
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "📡 Broadcast received: ${intent?.action}")
            if (intent?.action == ACTION_FILL_FORM) {
                fillForm()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "🚀 Service Connected and Ready")
        
        val filter = IntentFilter(ACTION_FILL_FORM)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(receiver, filter)
            }
            Log.d(TAG, "✅ Receiver registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to register receiver", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        serviceJob.cancel()
    }

    private fun fillForm() {
        serviceScope.launch {
            Log.d(TAG, "🎬 Starting Form Fill Process...")
            val userDetail = AppDatabase.getDatabase(applicationContext).userDetailDao().getUserDetailOnce()
            if (userDetail == null) {
                Log.w(TAG, "⚠️ No user details found in database.")
                Toast.makeText(applicationContext, "Save details in the app first!", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                Log.e(TAG, "❌ rootInActiveWindow is NULL. Is the app protecting its content?")
                Toast.makeText(applicationContext, "Cannot access screen. Check permissions.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            Log.d(TAG, "📂 Scanning UI Tree...")
            val filledCount = findAndFillNodes(rootNode, userDetail)
            
            if (filledCount > 0) {
                Log.i(TAG, "🎉 Successfully filled $filledCount fields.")
                Toast.makeText(applicationContext, "Filled $filledCount fields!", Toast.LENGTH_SHORT).show()
            } else {
                Log.w(TAG, "🔍 Finished scanning. No matching fields were found.")
                Toast.makeText(applicationContext, "No matching fields found.", Toast.LENGTH_SHORT).show()
            }
            rootNode.recycle()
        }
    }

    private fun findAndFillNodes(node: AccessibilityNodeInfo, userDetail: UserDetail): Int {
        var count = 0
        
        val className = node.className?.toString() ?: "unknown"
        val viewId = node.viewIdResourceName ?: "no-id"
        val hint = node.hintText?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val text = node.text?.toString() ?: ""

        // Log everything for EditTexts or items that look like inputs
        if (node.className == "android.widget.EditText" || node.isEditable) {
            Log.d(TAG, "👁️ Checking Input -> ID: $viewId | Hint: '$hint' | Desc: '$contentDesc' | Text: '$text'")
            
            val textToFill = getMatchingValue(node, userDetail)
            if (textToFill != null && textToFill.isNotEmpty()) {
                Log.i(TAG, "✨ MATCH! Field $viewId matches. Filling with: $textToFill")
                if (fillNode(node, textToFill)) {
                    count++
                } else {
                    Log.e(TAG, "❌ Action SET_TEXT failed for $viewId")
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                count += findAndFillNodes(child, userDetail)
                child.recycle()
            }
        }
        return count
    }

    private fun getMatchingValue(node: AccessibilityNodeInfo, userDetail: UserDetail): String? {
        val hint = node.hintText?.toString()?.lowercase() ?: ""
        val contentDescription = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        
        val info = "$hint $contentDescription $viewId $text"

        return when {
            containsAny(info, "full name", "first name", "name", "username") -> userDetail.name
            containsAny(info, "age", "years") -> userDetail.age
            containsAny(info, "gender", "sex") -> userDetail.gender
            containsAny(info, "identity type", "id type", "document type", "id-type") -> userDetail.identityType
            containsAny(info, "identity number", "id number", "card number", "document number", "id-number") -> userDetail.identityNumber
            containsAny(info, "email", "e-mail", "mail address") -> userDetail.email
            containsAny(info, "city", "town", "district") -> userDetail.city
            containsAny(info, "state", "province", "region") -> userDetail.state
            containsAny(info, "country", "nation") -> userDetail.country
            containsAny(info, "pincode", "zip", "postal", "pin code") -> userDetail.pincode
            containsAny(info, "gothram", "gotra", "gothra") -> userDetail.gothram
            else -> null
        }
    }

    private fun containsAny(info: String, vararg keywords: String): Boolean {
        return keywords.any { info.contains(it) }
    }

    private fun fillNode(node: AccessibilityNodeInfo, text: String): Boolean {
        val arguments = Bundle()
        arguments.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            text
        )
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }
}
