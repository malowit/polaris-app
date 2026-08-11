package com.malowit.polaris

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class GuideActivity : AppCompatActivity() {
    private var fromSplash = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guide)

        val whiteOverlay = findViewById<View>(R.id.guide_white_overlay)
        whiteOverlay.animate()
            .alpha(0f)
            .setDuration(900)
            .withEndAction { whiteOverlay.visibility = View.GONE }
            .start()

        fromSplash = intent.getBooleanExtra("fromSplash", false)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.guide_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + v.paddingTop, v.paddingRight, systemBars.bottom + v.paddingBottom)
            insets
        }

        findViewById<TextView>(R.id.guide_skip_button).setOnClickListener {
            exitGuide()
        }

        findViewById<TextView>(R.id.guide_finish_button).setOnClickListener {
            exitGuide()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                exitGuide()
            }
        })
    }

    private fun exitGuide() {
        if (fromSplash) {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}