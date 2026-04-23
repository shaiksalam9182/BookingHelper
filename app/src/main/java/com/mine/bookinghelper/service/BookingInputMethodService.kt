package com.mine.bookinghelper.service

import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.mine.bookinghelper.R
import com.mine.bookinghelper.data.AppDatabase
import com.mine.bookinghelper.model.GeneralDetails
import com.mine.bookinghelper.model.PersonDetail
import kotlinx.coroutines.*

class BookingInputMethodService : InputMethodService() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    companion object {
        private const val TAG = "BookingHelper"
    }

    override fun onCreateInputView(): View {
        val contextThemeWrapper = ContextThemeWrapper(this, R.style.Theme_BookingHelper)
        val themedInflater = LayoutInflater.from(contextThemeWrapper)
        
        val keyboardView = themedInflater.inflate(R.layout.keyboard_view, null)
        val grid = keyboardView.findViewById<GridLayout>(R.id.keyboard_grid_container)
        val btnSwitch = keyboardView.findViewById<ImageButton>(R.id.btn_switch_keyboard)
        
        btnSwitch.setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showInputMethodPicker()
        }
        
        loadGridItems(grid, contextThemeWrapper)
        return keyboardView
    }

    private fun loadGridItems(grid: GridLayout, context: Context) {
        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext).userDetailDao()
            val general = db.getGeneralDetailsOnce()
            val persons = db.getAllPersonsOnce()

            grid.removeAllViews()

            // 1. General Details Section
            general?.let {
                addHeader(grid, "COMMON DATA", context)
                addKey(grid, "Email", it.email, 0xFF546E7A.toInt(), context)
                addKey(grid, "City", it.city, 0xFF546E7A.toInt(), context)
                addKey(grid, "State", it.state, 0xFF546E7A.toInt(), context)
                addKey(grid, "Country", it.country, 0xFF546E7A.toInt(), context)
                addKey(grid, "PIN", it.pincode, 0xFF546E7A.toInt(), context)
            }

            // 2. Individual Pilgrim Sections
            persons.forEach { p ->
                addHeader(grid, "PILGRIM ${p.personIndex}", context)
                addKey(grid, "Name", p.name, 0xFF3F51B5.toInt(), context)
                addKey(grid, "Age", p.age, 0xFF3F51B5.toInt(), context)
                addKey(grid, "Gender", p.gender, 0xFF2E7D32.toInt(), context)
                addKey(grid, "ID Type", p.idType, 0xFF2E7D32.toInt(), context)
                addKey(grid, "ID #", p.idNumber, 0xFF3F51B5.toInt(), context)
            }
        }
    }

    private fun addHeader(grid: GridLayout, title: String, context: Context) {
        val tv = TextView(context).apply {
            text = title
            textSize = 11f
            setTextColor(0xFF1A237E.toInt())
            setPadding(16, 16, 0, 4)
            val params = GridLayout.LayoutParams()
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 3)
            layoutParams = params
        }
        grid.addView(tv)
    }

    private fun addKey(grid: GridLayout, label: String, value: String, color: Int, context: Context) {
        if (value.isEmpty()) return
        
        val btn = Button(context).apply {
            val params = GridLayout.LayoutParams()
            params.width = 0
            params.height = 130 // Fixed height for neatness
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            params.setMargins(6, 6, 6, 6)
            layoutParams = params
            
            // Show label and value snippet
            text = "$label\n${if (value.length > 10) value.take(8) + ".." else value}"
            setBackgroundColor(color)
            setTextColor(android.graphics.Color.WHITE)
            textSize = 10f
            isAllCaps = false
            setPadding(4, 4, 4, 4)
            
            setOnClickListener {
                currentInputConnection?.commitText(value, 1)
            }
        }
        grid.addView(btn)
    }

    override fun onEvaluateInputViewShown(): Boolean = true

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
