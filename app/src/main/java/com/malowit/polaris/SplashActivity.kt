package com.malowit.polaris

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

private const val SPLASH_PREFS = "polaris_splash"

class SplashActivity : AppCompatActivity() {
    private var pulseAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(SPLASH_PREFS, MODE_PRIVATE)
        val alreadySeen = prefs.getBoolean("splash_seen", false)

        if (alreadySeen) {
            goToMainDirectly()
            return
        }

        setContentView(R.layout.activity_splash)
        prefs.edit().putBoolean("splash_seen", true).apply()

        playSplashAnimation()
    }

    private fun playSplashAnimation() {
        val logo = findViewById<ImageView>(R.id.splash_logo)
        val guideText = findViewById<TextView>(R.id.splash_guide_text)

        val logoFadeIn = ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f).apply {
            duration = 900
        }

        val textFadeIn = ObjectAnimator.ofFloat(guideText, "alpha", 0f, 1f).apply {
            duration = 600
        }

        val sequence = AnimatorSet().apply {
            playSequentially(logoFadeIn, textFadeIn)
        }

        sequence.start()

        sequence.doOnEnd {
            startPulsing(guideText)
            guideText.setOnClickListener {
                pulseAnimator?.cancel()
                guideText.alpha = 1f
                goToGuide()
            }
        }
    }

    private fun startPulsing(view: TextView) {
        pulseAnimator = ObjectAnimator.ofFloat(view, "alpha", 1f, 0.4f).apply {
            duration = 900
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun goToGuide() {
        val intent = Intent(this, GuideActivity::class.java)
        intent.putExtra("fromSplash", true)
        startActivity(intent)
        finish()
    }

    private fun goToMainDirectly() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

private fun AnimatorSet.doOnEnd(action: () -> Unit) {
    addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) {
            action()
        }
    })
}