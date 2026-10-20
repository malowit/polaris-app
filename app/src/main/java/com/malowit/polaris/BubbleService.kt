package com.malowit.polaris

import android.animation.ObjectAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs

class BubbleService : Service() {

    companion object {
        var isRunning = false
    }

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: ImageView
    private lateinit var params: WindowManager.LayoutParams

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val mainHandler = Handler(Looper.getMainLooper())

    private var mediaProjection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null

    private var virtualDisplay: VirtualDisplay? = null
    private var captureReader: ImageReader? = null
    private var captureTimeout: Runnable? = null
    private var isCapturing = false

    private var drawOverlayView: View? = null
    private var resultPopupView: View? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        bubbleView = ImageView(this).apply {
            setImageResource(R.drawable.polaris_star)
            setPadding(24, 24, 24, 24)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor("#66000000".toColorInt())
            }
        }

        params = WindowManager.LayoutParams(
            120,
            120,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 300

        windowManager.addView(bubbleView, params)
        setupDragging()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", Int.MIN_VALUE) ?: Int.MIN_VALUE
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("resultData", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("resultData")
        }

        if (resultCode != Int.MIN_VALUE && resultData != null) {
            startForegroundWithNotification()
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(resultCode, resultData)
            if (projection == null) {
                stopSelf()
                return START_NOT_STICKY
            }
            mediaProjection = projection

            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    releaseCapture()
                    mediaProjection = null
                    stopSelf()
                }
            }
            projectionCallback = callback
            projection.registerCallback(callback, mainHandler)

            createCaptureDisplay(projection)
        }

        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val channelId = "polaris_bubble_service"

        val channel = NotificationChannel(
            channelId,
            "Polaris bubble",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Polaris")
            .setContentText("Bubble is active")
            .setSmallIcon(R.drawable.polaris_star)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(1, notification)
        }
    }

    @Suppress("ClickableViewAccessibility")
    private fun setupDragging() {
        bubbleView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(v, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = abs(event.rawX - initialTouchX)
                    val deltaY = abs(event.rawY - initialTouchY)
                    if (deltaX < 10 && deltaY < 10) {
                        v.performClick()
                        showBubbleMenu(v)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun showBubbleMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, getString(R.string.bubble_menu_select_element))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    captureScreen()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun currentScreenSize(): Triple<Int, Int, Int> {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return Triple(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
    }

    // Виртуальный дисплей создаётся один раз и без поверхности: пока поверхности нет, он ничего не рисует.
    private fun createCaptureDisplay(projection: MediaProjection) {
        releaseCapture()
        val (width, height, dpi) = currentScreenSize()
        virtualDisplay = projection.createVirtualDisplay(
            "PolarisScreenCapture",
            width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            null, null, null
        )
    }

    private fun releaseCapture() {
        captureTimeout?.let { mainHandler.removeCallbacks(it) }
        captureTimeout = null
        isCapturing = false
        captureReader?.setOnImageAvailableListener(null, null)
        captureReader?.close()
        captureReader = null
        virtualDisplay?.release()
        virtualDisplay = null
    }

    private fun captureScreen() {
        if (virtualDisplay == null || isCapturing) return
        isCapturing = true

        // Пауза, чтобы меню успело закрыться и не попало в снимок
        mainHandler.postDelayed({
            val display = virtualDisplay
            if (display == null) {
                isCapturing = false
                return@postDelayed
            }

            val (width, height, dpi) = currentScreenSize()
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            captureReader = reader
            reader.setOnImageAvailableListener({ r ->
                val image = r.acquireLatestImage()
                if (image != null) {
                    finishCapture(image)
                }
            }, mainHandler)

            display.resize(width, height, dpi)
            display.setSurface(reader.surface)

            val timeout = Runnable { finishCapture(captureReader?.acquireLatestImage()) }
            captureTimeout = timeout
            mainHandler.postDelayed(timeout, 2000)
        }, 300)
    }

    private fun finishCapture(image: Image?) {
        if (!isCapturing) {
            image?.close()
            return
        }
        isCapturing = false

        captureTimeout?.let { mainHandler.removeCallbacks(it) }
        captureTimeout = null

        // Сразу отключаем поверхность: дисплей снова ничего не рисует
        virtualDisplay?.setSurface(null)

        val reader = captureReader
        captureReader = null
        reader?.setOnImageAvailableListener(null, null)

        var bitmap: Bitmap? = null
        if (image != null) {
            bitmap = imageToBitmap(image)
            image.close()
        }
        reader?.close()

        if (bitmap != null) {
            showDrawOverlay(bitmap)
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val width = image.width
        val height = image.height
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowPadding = plane.rowStride - pixelStride * width

        val padded = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        padded.copyPixelsFromBuffer(buffer)

        return if (rowPadding == 0) {
            padded
        } else {
            Bitmap.createBitmap(padded, 0, 0, width, height)
        }
    }

    private fun showDrawOverlay(bitmap: Bitmap) {
        val root = FrameLayout(this)

        val imageView = ImageView(this).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_XY
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(imageView)

        val hintText = TextView(this).apply {
            text = getString(R.string.draw_overlay_hint)
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setBackgroundColor("#AA0F1235".toColorInt())
            setPadding(24, 24, 24, 24)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 100
            }
        }
        root.addView(hintText)

        lateinit var selectionViewRef: SelectionOverlayView
        val selectionView = SelectionOverlayView(this) { rect ->
            handleDrawSelection(bitmap, rect, selectionViewRef.width, selectionViewRef.height)
        }
        selectionView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        root.addView(selectionView)
        selectionViewRef = selectionView

        val overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(root, overlayParams)
        drawOverlayView = root
    }

    private fun removeDrawOverlay() {
        drawOverlayView?.let {
            windowManager.removeView(it)
            drawOverlayView = null
        }
    }

    private fun handleDrawSelection(bitmap: Bitmap, rect: Rect, viewWidth: Int, viewHeight: Int) {
        val scaleX = bitmap.width.toFloat() / viewWidth
        val scaleY = bitmap.height.toFloat() / viewHeight

        val safeRect = Rect(
            (rect.left * scaleX).toInt().coerceIn(0, bitmap.width),
            (rect.top * scaleY).toInt().coerceIn(0, bitmap.height),
            (rect.right * scaleX).toInt().coerceIn(0, bitmap.width),
            (rect.bottom * scaleY).toInt().coerceIn(0, bitmap.height)
        )

        removeDrawOverlay()

        if (safeRect.width() < 10 || safeRect.height() < 10) {
            return
        }

        val croppedBitmap = Bitmap.createBitmap(
            bitmap,
            safeRect.left,
            safeRect.top,
            safeRect.width(),
            safeRect.height()
        )

        runOcr(croppedBitmap)
    }

    private fun runOcr(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                showResultPopup(visionText.text)
            }
            .addOnFailureListener {
                showResultPopup("")
            }
    }

    private fun showResultPopup(selectedText: String) {
        val savedPair = getCurrencyPair(this)

        val inflater = LayoutInflater.from(this)
        val container = FrameLayout(this)
        inflater.inflate(R.layout.activity_popup, container, true)

        val card = container.findViewById<LinearLayout>(R.id.card)
        val details = container.findViewById<LinearLayout>(R.id.details)
        val resultText = container.findViewById<TextView>(R.id.result_text)
        val rateText = container.findViewById<TextView>(R.id.rate_text)
        val goToAppButton = container.findViewById<TextView>(R.id.go_to_app_button)

        val popupParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(container, popupParams)
        resultPopupView = container

        ObjectAnimator.ofFloat(card, "alpha", 0f, 1f).apply {
            duration = 300
            start()
        }

        container.setOnClickListener {
            removeResultPopup()
        }

        if (savedPair == null) {
            resultText.text = getString(R.string.please_setup_currencies_first)
            resultText.setOnClickListener {
                removeResultPopup()
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
            return
        }

        val (localCurrency, homeCurrency) = savedPair

        card.setOnClickListener {
            details.visibility = if (details.isVisible) View.GONE else View.VISIBLE
        }

        goToAppButton.setOnClickListener {
            removeResultPopup()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }

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

    private fun performConversion(
        price: Double,
        localCurrency: String,
        homeCurrency: String,
        resultText: TextView,
        rateText: TextView
    ) {
        resultText.text = getString(R.string.result_converting)
        serviceScope.launch {
            val rate = getRate(this@BubbleService, localCurrency, homeCurrency)
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

    private fun removeResultPopup() {
        resultPopupView?.let {
            windowManager.removeView(it)
            resultPopupView = null
        }
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
        removeDrawOverlay()
        removeResultPopup()
        if (::bubbleView.isInitialized) {
            windowManager.removeView(bubbleView)
        }
        releaseCapture()
        projectionCallback?.let { mediaProjection?.unregisterCallback(it) }
        mediaProjection?.stop()
        mediaProjection = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Suppress("ClickableViewAccessibility")
class SelectionOverlayView(
    context: android.content.Context,
    private val onSelectionComplete: (Rect) -> Unit
) : View(context) {

    private val pathPaint = Paint().apply {
        color = "#F0B429".toColorInt()
        style = Paint.Style.STROKE
        strokeWidth = 60f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        alpha = 140
    }

    private val dimPaint = Paint().apply {
        color = "#66000000".toColorInt()
    }

    private val path = Path()
    private var isDrawing = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                path.reset()
                path.moveTo(event.x, event.y)
                isDrawing = true
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawing) {
                    path.lineTo(event.x, event.y)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                isDrawing = false
                path.close()

                val bounds = RectF()
                path.computeBounds(bounds, true)

                val rect = Rect(
                    bounds.left.toInt(),
                    bounds.top.toInt(),
                    bounds.right.toInt(),
                    bounds.bottom.toInt()
                )

                onSelectionComplete(rect)
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawPath(path, pathPaint)
    }
}