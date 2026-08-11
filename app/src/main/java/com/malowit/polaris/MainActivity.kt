package com.malowit.polaris

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var localSpinner: Spinner
    private lateinit var homeSpinner: Spinner
    private lateinit var rateDisplayText: TextView
    private lateinit var rateUpdateHintText: TextView
    private lateinit var calcInput: EditText
    private lateinit var calcResultText: TextView
    private lateinit var statusHintText: TextView

    private var currentRate: Double? = null
    private var isFirstCalcFocus = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        localSpinner = findViewById(R.id.local_currency_spinner)
        homeSpinner = findViewById(R.id.home_currency_spinner)
        rateDisplayText = findViewById(R.id.rate_display_text)
        rateUpdateHintText = findViewById(R.id.rate_update_hint_text)
        calcInput = findViewById(R.id.calc_input)
        calcResultText = findViewById(R.id.calc_result_text)
        statusHintText = findViewById(R.id.status_hint_text)

        setupCurrencySpinners()
        setupCalculator()
        findViewById<ImageView>(R.id.settings_star).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun setupCurrencySpinners() {
        val placeholder = getString(R.string.select_currency_placeholder)
        val currencyLabels = listOf(placeholder) + SUPPORTED_CURRENCIES.map { "${it.symbol} ${it.code}" }
        val adapter = CurrencySpinnerAdapter(this, currencyLabels)

        localSpinner.adapter = adapter
        homeSpinner.adapter = adapter

        val savedPair = getCurrencyPair(this)
        if (savedPair != null) {
            val localIndex = SUPPORTED_CURRENCIES.indexOfFirst { it.code == savedPair.first }
            val homeIndex = SUPPORTED_CURRENCIES.indexOfFirst { it.code == savedPair.second }
            if (localIndex >= 0) localSpinner.setSelection(localIndex + 1)
            if (homeIndex >= 0) homeSpinner.setSelection(homeIndex + 1)
        }

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateStatus(forceRefresh = false)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        localSpinner.onItemSelectedListener = listener
        homeSpinner.onItemSelectedListener = listener
    }

    private fun getSelectedCurrencies(): Pair<String, String>? {
        val localPosition = localSpinner.selectedItemPosition
        val homePosition = homeSpinner.selectedItemPosition
        if (localPosition == 0 || homePosition == 0) return null
        return Pair(
            SUPPORTED_CURRENCIES[localPosition - 1].code,
            SUPPORTED_CURRENCIES[homePosition - 1].code
        )
    }

    private fun updateStatus(forceRefresh: Boolean) {
        val pair = getSelectedCurrencies()

        if (pair == null) {
            statusHintText.text = getString(R.string.hint_choose_currencies)
            rateDisplayText.text = ""
            rateUpdateHintText.text = ""
            rateUpdateHintText.setOnClickListener(null)

            calcInput.isEnabled = false
            calcInput.hint = null
            calcInput.setText("")

            calcResultText.text = ""
            currentRate = null
            return
        }

        val (local, home) = pair
        saveCurrencyPair(this, local, home)
        statusHintText.text = getString(R.string.hint_ready_to_explore)

        calcInput.isEnabled = true
        calcInput.hint = getString(R.string.calc_input_hint)

        lifecycleScope.launch {
            val rate = getRate(this@MainActivity, local, home, forceRefresh)
            if (rate != null) {
                currentRate = rate

                if (forceRefresh) {
                    flashView(rateUpdateHintText)
                }

                val display = formatRateForDisplay(local, home, rate)
                val fromSymbol = SUPPORTED_CURRENCIES.find { it.code == display.fromCode }?.symbol ?: display.fromCode
                val toSymbol = SUPPORTED_CURRENCIES.find { it.code == display.toCode }?.symbol ?: display.toCode
                rateDisplayText.text = getString(
                    R.string.rate_display_format,
                    fromSymbol, display.fromCode, toSymbol, "%.2f".format(display.rate), display.toCode
                )

                updateRateFreshnessText(local, home)
                rateUpdateHintText.setOnClickListener {
                    updateStatus(forceRefresh = true)
                }

                recalculateIfNeeded()
            } else {
                rateDisplayText.text = getString(R.string.result_rate_unavailable)
            }
        }
    }

    private fun flashView(view: View) {
        val flashColor = android.graphics.Color.WHITE
        val originalColor = android.graphics.Color.parseColor("#1A1F4E")

        val colorAnimator = android.animation.ValueAnimator.ofObject(
            android.animation.ArgbEvaluator(), flashColor, originalColor
        )
        colorAnimator.duration = 400
        colorAnimator.addUpdateListener { animator ->
            view.setBackgroundColor(animator.animatedValue as Int)
        }
        colorAnimator.start()
    }

    private fun updateRateFreshnessText(local: String, home: String) {
        val timestamp = getRateTimestamp(this, local, home)
        if (timestamp == null) {
            rateUpdateHintText.text = getString(R.string.rate_updated_just_now)
            return
        }

        val now = Calendar.getInstance()
        val rateTime = Calendar.getInstance().apply { timeInMillis = timestamp }

        val isSameDay = now.get(Calendar.YEAR) == rateTime.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == rateTime.get(Calendar.DAY_OF_YEAR)

        val isJustNow = now.timeInMillis - timestamp < 60_000

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        rateUpdateHintText.text = when {
            isJustNow -> getString(R.string.rate_updated_just_now)
            isSameDay -> getString(R.string.rate_updated_today, timeFormat.format(rateTime.time))
            else -> getString(R.string.rate_updated_yesterday, timeFormat.format(rateTime.time))
        }
    }

    private fun setupCalculator() {
        calcInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                calcInput.hint = null
                if (!isFirstCalcFocus) {
                    calcInput.setText("")
                }
                isFirstCalcFocus = false
            } else if (calcInput.text.isEmpty()) {
                calcInput.hint = getString(R.string.calc_input_hint)
            }
        }

        calcInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                recalculate()
            }
        })
    }

    private fun recalculate() {
        val pair = getSelectedCurrencies() ?: return
        val rate = currentRate ?: return
        val amount = calcInput.text.toString().toDoubleOrNull()

        if (amount == null) {
            calcResultText.text = ""
            return
        }

        val (_, home) = pair
        val converted = amount * rate
        val homeSymbol = SUPPORTED_CURRENCIES.find { it.code == home }?.symbol ?: home
        calcResultText.text = getString(R.string.calc_result_format, homeSymbol, "%.2f".format(converted))
    }

    private fun recalculateIfNeeded() {
        if (calcInput.text.isNotEmpty()) {
            recalculate()
        }
    }
}