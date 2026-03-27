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
            if (intent?.action == ACTION_FILL_FORM) {
                fillForm()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(ACTION_FILL_FORM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't necessarily need to react to events automatically, 
        // as we trigger fill from the overlay.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
        serviceJob.cancel()
    }

    private fun fillForm() {
        serviceScope.launch {
            val userDetail = AppDatabase.getDatabase(applicationContext).userDetailDao().getUserDetailOnce()
            if (userDetail == null) {
                Log.d(TAG, "No user details found to fill.")
                return@launch
            }

            val rootNode = rootInActiveWindow ?: return@launch
            findAndFillNodes(rootNode, userDetail)
            rootNode.recycle()
        }
    }

    private fun findAndFillNodes(node: AccessibilityNodeInfo, userDetail: UserDetail) {
        if (node.className == "android.widget.EditText" || node.isEditable) {
            val textToFill = getMatchingValue(node, userDetail)
            if (textToFill != null) {
                fillNode(node, textToFill)
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                findAndFillNodes(child, userDetail)
                child.recycle()
            }
        }
    }

    private fun getMatchingValue(node: AccessibilityNodeInfo, userDetail: UserDetail): String? {
        val hint = node.hintText?.toString()?.lowercase() ?: ""
        val contentDescription = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        
        val combinedInfo = "$hint $contentDescription $viewId"

        return when {
            containsAny(combinedInfo, "name", "full name", "first name") -> userDetail.name
            containsAny(combinedInfo, "age") -> userDetail.age
            containsAny(combinedInfo, "gender", "sex") -> userDetail.gender
            containsAny(combinedInfo, "identity type", "id type", "document") -> userDetail.identityType
            containsAny(combinedInfo, "identity number", "id number", "card number") -> userDetail.identityNumber
            containsAny(combinedInfo, "email", "e-mail") -> userDetail.email
            containsAny(combinedInfo, "city", "town") -> userDetail.city
            containsAny(combinedInfo, "state", "province") -> userDetail.state
            containsAny(combinedInfo, "country") -> userDetail.country
            containsAny(combinedInfo, "pincode", "zip", "postal") -> userDetail.pincode
            containsAny(combinedInfo, "gothram", "gotra") -> userDetail.gothram
            else -> null
        }
    }

    private fun containsAny(info: String, vararg keywords: String): Boolean {
        return keywords.any { info.contains(it) }
    }

    private fun fillNode(node: AccessibilityNodeInfo, text: String) {
        val arguments = Bundle()
        arguments.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            text
        )
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }
}
