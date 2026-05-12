package com.mine.bookinghelper

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.mine.bookinghelper.data.AppDatabase
import com.mine.bookinghelper.model.GeneralDetails
import com.mine.bookinghelper.model.PersonDetail
import com.mine.bookinghelper.service.FloatingOverlayService
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var actPersonCount: AutoCompleteTextView
    private val personCards = arrayOfNulls<View>(4)

    private val etNames = arrayOfNulls<TextInputEditText>(4)
    private val etAges = arrayOfNulls<TextInputEditText>(4)
    private val actGenders = arrayOfNulls<AutoCompleteTextView>(4)
    private val actIdTypes = arrayOfNulls<AutoCompleteTextView>(4)
    private val etIdNumbers = arrayOfNulls<TextInputEditText>(4)

    private lateinit var etEmail: TextInputEditText
    private lateinit var etCity: TextInputEditText
    private lateinit var etState: TextInputEditText
    private lateinit var etCountry: TextInputEditText
    private lateinit var etPincode: TextInputEditText

    private lateinit var tvAccStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var btnStartOverlay: MaterialButton

    private var overlayRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupDropdowns()
        loadAllDetails()
        wireButtons()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
    }

    private fun initViews() {
        actPersonCount = findViewById(R.id.act_person_count)

        personCards[0] = findViewById(R.id.person_card_1)
        personCards[1] = findViewById(R.id.person_card_2)
        personCards[2] = findViewById(R.id.person_card_3)
        personCards[3] = findViewById(R.id.person_card_4)

        for (i in 0..3) {
            val card = personCards[i]!!
            etNames[i] = card.findViewById(R.id.et_name)
            etAges[i] = card.findViewById(R.id.et_age)
            actGenders[i] = card.findViewById(R.id.act_gender)
            actIdTypes[i] = card.findViewById(R.id.act_id_type)
            etIdNumbers[i] = card.findViewById(R.id.et_id_number)
            card.findViewById<TextView>(R.id.tv_person_title).text =
                if (i == 0) "PRIMARY PILGRIM" else "PILGRIM ${i + 1}"
        }

        etEmail = findViewById(R.id.et_email)
        etCity = findViewById(R.id.et_city)
        etState = findViewById(R.id.et_state)
        etCountry = findViewById(R.id.et_country)
        etPincode = findViewById(R.id.et_pincode)

        tvAccStatus = findViewById(R.id.tv_acc_status)
        tvOverlayStatus = findViewById(R.id.tv_overlay_status)
        btnStartOverlay = findViewById(R.id.btn_start_overlay)
    }

    private fun wireButtons() {
        findViewById<MaterialButton>(R.id.btn_save).setOnClickListener { saveAllDetails() }
        findViewById<MaterialButton>(R.id.btn_wipe).setOnClickListener { wipeAllDetails() }

        findViewById<MaterialButton>(R.id.btn_enable_keyboard).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        // Opens Android Accessibility settings so user can enable our service
        findViewById<MaterialButton>(R.id.btn_enable_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // Opens Draw Over Apps permission screen for this app
        findViewById<MaterialButton>(R.id.btn_grant_overlay).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        // Start / stop the floating overlay service
        btnStartOverlay.setOnClickListener {
            if (overlayRunning) {
                stopService(Intent(this, FloatingOverlayService::class.java))
                overlayRunning = false
                updateOverlayButton()
            } else {
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Grant 'Draw Over Apps' permission first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!isAccessibilityEnabled()) {
                    Toast.makeText(this, "Enable Accessibility Service first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                startService(Intent(this, FloatingOverlayService::class.java))
                overlayRunning = true
                updateOverlayButton()
                Toast.makeText(this, "Floating bar launched — switch to TTD app", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshPermissionStatus() {
        val accOk = isAccessibilityEnabled()
        val overlayOk = Settings.canDrawOverlays(this)

        tvAccStatus.text = if (accOk) "● Active" else "● Not enabled"
        tvAccStatus.setTextColor(getColor(if (accOk) R.color.status_ok else R.color.status_warn))

        tvOverlayStatus.text = if (overlayOk) "● Granted" else "● Not granted"
        tvOverlayStatus.setTextColor(getColor(if (overlayOk) R.color.status_ok else R.color.status_warn))

        updateOverlayButton()
    }

    private fun updateOverlayButton() {
        btnStartOverlay.text = if (overlayRunning) "STOP FLOATING BAR" else "SHOW FLOATING BAR"
        btnStartOverlay.alpha = if (overlayRunning) 0.7f else 1.0f
    }

    private fun setupDropdowns() {
        val counts = arrayOf("1", "2", "3", "4")
        actPersonCount.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, counts))
        actPersonCount.setOnItemClickListener { _, _, position, _ -> updateVisibility(position + 1) }

        val genders = arrayOf("Male", "Female", "Transgender")
        val ids = arrayOf("Aadhaar Card", "Passport", "Voter ID", "Driving License")

        for (i in 0..3) {
            actGenders[i]?.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genders))
            actIdTypes[i]?.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, ids))
        }
    }

    private fun updateVisibility(count: Int) {
        for (i in 0..3) {
            personCards[i]?.visibility = if (i < count) View.VISIBLE else View.GONE
        }
    }

    private fun loadAllDetails() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@MainActivity).userDetailDao()
            val general = db.getGeneralDetails().firstOrNull()
            general?.let {
                etEmail.setText(it.email)
                etCity.setText(it.city)
                etState.setText(it.state)
                etCountry.setText(it.country)
                etPincode.setText(it.pincode)
            }

            val persons = db.getAllPersonsOnce()
            if (persons.isNotEmpty()) {
                val count = persons.size.coerceAtMost(4)
                actPersonCount.setText(count.toString(), false)
                updateVisibility(count)
                persons.forEachIndexed { i, p ->
                    if (i < 4) {
                        etNames[i]?.setText(p.name)
                        etAges[i]?.setText(p.age)
                        actGenders[i]?.setText(p.gender, false)
                        actIdTypes[i]?.setText(p.idType, false)
                        etIdNumbers[i]?.setText(p.idNumber)
                    }
                }
            } else {
                actPersonCount.setText("1", false)
                updateVisibility(1)
            }
        }
    }

    private fun saveAllDetails() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@MainActivity).userDetailDao()
            val count = actPersonCount.text.toString().toIntOrNull() ?: 1

            db.saveGeneralDetails(
                GeneralDetails(
                    email = etEmail.text.toString(),
                    city = etCity.text.toString(),
                    state = etState.text.toString(),
                    country = etCountry.text.toString(),
                    pincode = etPincode.text.toString()
                )
            )

            db.deleteAllPersons()
            for (i in 0 until count) {
                db.savePerson(
                    PersonDetail(
                        personIndex = i + 1,
                        name = etNames[i]?.text.toString() ?: "",
                        age = etAges[i]?.text.toString() ?: "",
                        gender = actGenders[i]?.text.toString() ?: "",
                        idType = actIdTypes[i]?.text.toString() ?: "",
                        idNumber = etIdNumbers[i]?.text.toString() ?: ""
                    )
                )
            }
            Toast.makeText(this@MainActivity, "Saved successfully", Toast.LENGTH_SHORT).show()
        }
    }

    private fun wipeAllDetails() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@MainActivity).userDetailDao()
            db.deleteAllPersons()
            db.saveGeneralDetails(GeneralDetails())
            loadAllDetails()
            Toast.makeText(this@MainActivity, "All data cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
    }
}
