package com.malowit.polaris

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var localAdapter: CurrencyAutoCompleteAdapter

    private lateinit var homeAdapter: CurrencyAutoCompleteAdapter
    private lateinit var localInput: AutoCompleteTextView
    private lateinit var homeInput: AutoCompleteTextView
    private lateinit var rateDisplayText: TextView
    private lateinit var rateUpdateHintText: TextView
    private lateinit var calcInput: EditText
    private lateinit var calcResultText: TextView
    private lateinit var statusHintText: TextView

    private var selectedLocalCode: String? = null
    private var selectedHomeCode: String? = null

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

        localInput = findViewById(R.id.local_currency_spinner)
        homeInput = findViewById(R.id.home_currency_spinner)
        rateDisplayText = findViewById(R.id.rate_display_text)
        rateUpdateHintText = findViewById(R.id.rate_update_hint_text)
        calcInput = findViewById(R.id.calc_input)
        calcResultText = findViewById(R.id.calc_result_text)
        statusHintText = findViewById(R.id.status_hint_text)

        setupCurrencyFields()
        setupCalculator()
        findViewById<ImageView>(R.id.settings_star).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun setupCurrencyFields() {
        localAdapter = CurrencyAutoCompleteAdapter(this, POPULAR_LOCAL_CURRENCIES)
        homeAdapter = CurrencyAutoCompleteAdapter(this, POPULAR_HOME_CURRENCIES)

        localInput.setAdapter(localAdapter)
        homeInput.setAdapter(homeAdapter)

        localInput.threshold = 0
        homeInput.threshold = 0

        localInput.setOnItemClickListener { parent, _, position, _ ->
            val label = parent.getItemAtPosition(position) as String
            val currency = currencyFromLabel(label)
            if (currency != null) {
                selectedLocalCode = currency.code
                localInput.setText(formatCurrencyShort(currency), false)
                localInput.clearFocus()
                updateStatus(forceRefresh = false)
            }
        }

        homeInput.setOnItemClickListener { parent, _, position, _ ->
            val label = parent.getItemAtPosition(position) as String
            val currency = currencyFromLabel(label)
            if (currency != null) {
                selectedHomeCode = currency.code
                homeInput.setText(formatCurrencyShort(currency), false)
                homeInput.clearFocus()
                updateStatus(forceRefresh = false)
            }
        }

        val savedPair = getCurrencyPair(this)
        if (savedPair != null) {
            val local = SUPPORTED_CURRENCIES.find { it.code == savedPair.first }
            val home = SUPPORTED_CURRENCIES.find { it.code == savedPair.second }
            if (local != null) {
                selectedLocalCode = local.code
                localInput.setText(formatCurrencyShort(local), false)
            }
            if (home != null) {
                selectedHomeCode = home.code
                homeInput.setText(formatCurrencyShort(home), false)
            }
            updateStatus(forceRefresh = false)
        }
    }

    private fun getSelectedCurrencies(): Pair<String, String>? {
        val local = selectedLocalCode
        val home = selectedHomeCode
        return if (local != null && home != null) Pair(local, home) else null
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

    private fun flashView(view: android.view.View) {
        val flashColor = android.graphics.Color.WHITE
        val originalColor = "#1A1F4E".toColorInt()

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