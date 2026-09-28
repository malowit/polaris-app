package com.malowit.polaris

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

class ScreenCaptureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = projectionManager.createScreenCaptureIntent()
        startActivityForResult(captureIntent, REQUEST_CODE_SCREEN_CAPTURE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                val serviceIntent = Intent(this, BubbleService::class.java)
                serviceIntent.putExtra("resultCode", resultCode)
                serviceIntent.putExtra("resultData", data)
                startService(serviceIntent)
            } else {
                setBubbleEnabled(this, false)
            }
            finish()
        }
    }

    companion object {
        private const val REQUEST_CODE_SCREEN_CAPTURE = 1001
    }
}

object ScreenCaptureHolder {
    var capturedBitmap: android.graphics.Bitmap? = null
}