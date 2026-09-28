package com.malowit.polaris

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SettingsActivity : AppCompatActivity() {
    private lateinit var englishStar: TextView
    private lateinit var russianStar: TextView
    private lateinit var chineseStar: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        findViewById<ImageView>(R.id.settings_star).setOnClickListener {
            finish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        findViewById<TextView>(R.id.feedback_telegram_button).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, "https://t.me/malowit".toUri()))
        }

        findViewById<TextView>(R.id.feedback_email_button).setOnClickListener {
            startActivity(Intent(Intent.ACTION_SENDTO, "mailto:malowit@gmail.com".toUri()))
        }

        englishStar = findViewById(R.id.lang_english_star)
        russianStar = findViewById(R.id.lang_russian_star)
        chineseStar = findViewById(R.id.lang_chinese_star)

        findViewById<TextView>(R.id.lang_english_button).setOnClickListener {
            setAppLanguageWithFlash(it as TextView, "en")
        }
        findViewById<TextView>(R.id.lang_russian_button).setOnClickListener {
            setAppLanguageWithFlash(it as TextView, "ru")
        }
        findViewById<TextView>(R.id.lang_chinese_button).setOnClickListener {
            setAppLanguageWithFlash(it as TextView, "zh")
        }

        updateLanguageStars()

        findViewById<TextView>(R.id.settings_guide_button).setOnClickListener {
            val intent = Intent(this, GuideActivity::class.java)
            intent.putExtra("fromSplash", false)
            startActivity(intent)
        }

        val bubbleToggle = findViewById<Switch>(R.id.bubble_toggle)
        bubbleToggle.isChecked = BubbleService.isRunning

        bubbleToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!Settings.canDrawOverlays(this)) {
                    bubbleToggle.isChecked = false
                    val overlayIntent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(overlayIntent)
                } else {
                    setBubbleEnabled(this, true)
                    val intent = Intent(this, ScreenCaptureActivity::class.java)
                    intent.putExtra("startBubbleAfter", true)
                    startActivity(intent)
                }
            } else {
                setBubbleEnabled(this, false)
                stopService(Intent(this, BubbleService::class.java))
            }
        }
    }

    private fun setAppLanguage(languageCode: String) {
        val localeList = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    private fun updateLanguageStars() {
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val currentLanguage = if (currentLocales.isEmpty) "en" else currentLocales[0]?.language ?: "en"

        englishStar.visibility = if (currentLanguage == "en") android.view.View.VISIBLE else android.view.View.GONE
        russianStar.visibility = if (currentLanguage == "ru") android.view.View.VISIBLE else android.view.View.GONE
        chineseStar.visibility = if (currentLanguage == "zh") android.view.View.VISIBLE else android.view.View.GONE
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
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

    private fun setAppLanguageWithFlash(button: TextView, languageCode: String) {
        flashView(button)
        button.postDelayed({
            setAppLanguage(languageCode)
        }, 150)
    }
}