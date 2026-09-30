package com.riccardopinato.notificationcontrol.ui.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.FrameLayout
import android.widget.TextView
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.ui.custom.LuminousCircleView
import kotlin.random.Random

class LuminousCircleOverlay(private val context: Context) {
    private val windowManager = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val settings = AppSettings(context); private val handler = Handler(Looper.getMainLooper())
    private var container: FrameLayout? = null; private var circle: LuminousCircleView? = null
    private val timeout = Runnable { hide() }
    private val pixelShift = object : Runnable { override fun run() { circle?.let { it.translationX = Random.nextInt(-10,11).toFloat(); it.translationY = Random.nextInt(-10,11).toFloat() }; handler.postDelayed(this,60_000L) } }
    fun show(label: String) {
        if (container != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) return
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT).apply { screenBrightness = 0.03f; buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF }
        val host = FrameLayout(context).apply { setBackgroundColor(Color.BLACK); setOnClickListener { hide() } }
        val color = runCatching { Color.parseColor(settings.circleColorHex) }.getOrDefault(Color.WHITE)
        val view = LuminousCircleView(context).apply {
            setAesthetics(color, settings.circleThickness, settings.circleGlow)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER)
            startAnimation(AlphaAnimation(0.08f,0.72f).apply { duration = settings.pulseSpeedMs; repeatMode = Animation.REVERSE; repeatCount = Animation.INFINITE })
        }
        val text = TextView(context).apply { this.text = label; setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER; layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply { topMargin = (240 * resources.displayMetrics.density).toInt() } }
        host.addView(view); host.addView(text)
        runCatching { windowManager?.addView(host, params) }.onSuccess { container = host; circle = view; handler.postDelayed(timeout, 120_000L); handler.post(pixelShift) }
    }
    fun hide() { handler.removeCallbacks(timeout); handler.removeCallbacks(pixelShift); container?.let { runCatching { windowManager?.removeView(it) } }; container = null; circle = null }
}
