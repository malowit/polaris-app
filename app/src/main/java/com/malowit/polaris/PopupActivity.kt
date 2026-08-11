package com.malowit.polaris

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class PopupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_popup)

        val card = findViewById<LinearLayout>(R.id.card)
        val rootOverlay = findViewById<View>(android.R.id.content)
        val details = findViewById<LinearLayout>(R.id.details)

        animateCardIn(card)

        val savedPair = getCurrencyPair(this)

        if (savedPair == null) {
            val resultText = findViewById<TextView>(R.id.result_text)
            resultText.text = getString(R.string.please_setup_currencies_first)
            resultText.setOnClickListener {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        } else {

            handlePriceIntent(savedPair.first, savedPair.second)

            card.setOnClickListener {
                details.visibility = if (details.isVisible) View.GONE else View.VISIBLE
            }

            findViewById<TextView>(R.id.go_to_app_button).setOnClickListener {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }

        rootOverlay.setOnClickListener {
            finish()
        }
    }

    private fun animateCardIn(card: LinearLayout) {
        ObjectAnimator.ofFloat(card, "alpha", 0f, 1f).apply {
            duration = 300
            start()
        }
    }

    private fun handlePriceIntent(localCurrency: String, homeCurrency: String) {
        if (intent?.action == Intent.ACTION_PROCESS_TEXT) {
            val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: ""


            val resultText = findViewById<TextView>(R.id.result_text)
            val rateText = findViewById<TextView>(R.id.rate_text)

            if (looksAmbiguous(selectedText)) {
                resultText.text = getString(R.string.result_ambiguous)
            } else {
                val price = extractPrice(selectedText)
                if (price != null) {
                    performConversion(price, localCurrency, homeCurrency, resultText, rateText)
                } else {
                    resultText.text = getString(R.string.result_not_found)
                }
            }
        }
    }

    private fun performConversion(
        price: Double,
        localCurrency: String,
        homeCurrency: String,
        resultText: TextView,
        rateText: TextView
    ) {
        resultText.text = getString(R.string.result_converting)
        lifecycleScope.launch {
            val rate = getRate(this@PopupActivity, localCurrency, homeCurrency)
            if (rate != null) {
                val converted = price * rate
                val localSymbol = SUPPORTED_CURRENCIES.find { it.code == localCurrency }?.symbol ?: localCurrency
                val homeSymbol = SUPPORTED_CURRENCIES.find { it.code == homeCurrency }?.symbol ?: homeCurrency

                resultText.text = getString(
                    R.string.result_converted_format,
                    localSymbol, "%.2f".format(price), homeSymbol, "%.2f".format(converted)
                )

                val display = formatRateForDisplay(localCurrency, homeCurrency, rate)
                val fromSymbol = SUPPORTED_CURRENCIES.find { it.code == display.fromCode }?.symbol ?: display.fromCode
                val toSymbol = SUPPORTED_CURRENCIES.find { it.code == display.toCode }?.symbol ?: display.toCode
                rateText.text = getString(
                    R.string.rate_display_format,
                    fromSymbol, display.fromCode, toSymbol, "%.2f".format(display.rate), display.toCode
                )
            } else {
                resultText.text = getString(R.string.result_rate_unavailable)
            }
        }
    }
}