package com.mine.bookinghelper

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.mine.bookinghelper.data.AppDatabase
import com.mine.bookinghelper.model.UserDetail
import com.mine.bookinghelper.service.BookingAccessibilityService
import com.mine.bookinghelper.service.FloatingOverlayService
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var etName: TextInputEditText
    private lateinit var etAge: TextInputEditText
    private lateinit var etGender: TextInputEditText
    private lateinit var etIdType: TextInputEditText
    private lateinit var etIdNumber: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etCity: TextInputEditText
    private lateinit var etState: TextInputEditText
    private lateinit var etCountry: TextInputEditText
    private lateinit var etPincode: TextInputEditText
    private lateinit var etGothram: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        loadSavedDetails()

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            saveDetails()
        }

        findViewById<Button>(R.id.btn_accessibility_permission).setOnClickListener {
            requestAccessibilityPermission()
        }

        findViewById<Button>(R.id.btn_overlay_permission).setOnClickListener {
            requestOverlayPermission()
        }

        findViewById<Button>(R.id.btn_toggle_overlay).setOnClickListener {
            toggleOverlay()
        }
    }

    private fun initViews() {
        etName = findViewById(R.id.et_name)
        etAge = findViewById(R.id.et_age)
        etGender = findViewById(R.id.et_gender)
        etIdType = findViewById(R.id.et_id_type)
        etIdNumber = findViewById(R.id.et_id_number)
        etEmail = findViewById(R.id.et_email)
        etCity = findViewById(R.id.et_city)
        etState = findViewById(R.id.et_state)
        etCountry = findViewById(R.id.et_country)
        etPincode = findViewById(R.id.et_pincode)
        etGothram = findViewById(R.id.et_gothram)
    }

    private fun loadSavedDetails() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@MainActivity)
            val userDetail = db.userDetailDao().getUserDetail().firstOrNull()
            userDetail?.let {
                etName.setText(it.name)
                etAge.setText(it.age)
                etGender.setText(it.gender)
                etIdType.setText(it.identityType)
                etIdNumber.setText(it.identityNumber)
                etEmail.setText(it.email)
                etCity.setText(it.city)
                etState.setText(it.state)
                etCountry.setText(it.country)
                etPincode.setText(it.pincode)
                etGothram.setText(it.gothram)
            }
        }
    }

    private fun saveDetails() {
        val userDetail = UserDetail(
            name = etName.text.toString(),
            age = etAge.text.toString(),
            gender = etGender.text.toString(),
            identityType = etIdType.text.toString(),
            identityNumber = etIdNumber.text.toString(),
            email = etEmail.text.toString(),
            city = etCity.text.toString(),
            state = etState.text.toString(),
            country = etCountry.text.toString(),
            pincode = etPincode.text.toString(),
            gothram = etGothram.text.toString()
        )

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@MainActivity)
            db.userDetailDao().saveUserDetail(userDetail)
            Toast.makeText(this@MainActivity, "Details saved locally", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestAccessibilityPermission() {
        if (!isAccessibilityServiceEnabled()) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Enable BookingHelper in Accessibility settings", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Accessibility permission already granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.packageName == packageName) {
                return true
            }
        }
        return false
    }

    private fun toggleOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }
        
        if (!isAccessibilityServiceEnabled()) {
            requestAccessibilityPermission()
            return
        }

        val intent = Intent(this, FloatingOverlayService::class.java)
        startService(intent)
        Toast.makeText(this, "Overlay started", Toast.LENGTH_SHORT).show()
    }
}
