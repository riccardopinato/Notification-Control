package com.riccardopinato.notificationcontrol.ui.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.FrameLayout
import android.widget.TextView
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.luminous.LuminousAlertStyle
import com.riccardopinato.notificationcontrol.ui.custom.LuminousCircleView
import kotlin.random.Random

class LuminousCircleOverlay(context: Context) {
    private val appContext = context.applicationContext
    private val windowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val powerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val settings = AppSettings(appContext)
    private val handler = Handler(Looper.getMainLooper())

    private var container: FrameLayout? = null
    private var circle: LuminousCircleView? = null

    private val timeout = Runnable { hide() }
    private val pixelShift = object : Runnable {
        override fun run() {
            circle?.let {
                it.translationX = Random.nextInt(-10, 11).toFloat()
                it.translationY = Random.nextInt(-10, 11).toFloat()
            }
            handler.postDelayed(this, 60_000L)
        }
    }

    fun show(
        label: String,
        style: LuminousAlertStyle = defaultStyle(),
        onShown: (() -> Unit)? = null
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { show(label, style, onShown) }
            return
        }
        if (!Settings.canDrawOverlays(appContext)) return
        val manager = windowManager ?: return

        hide()

        val interactive = powerManager?.isInteractive == true
        val flags =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

        @Suppress("DEPRECATION")
        val overlayType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            screenBrightness = if (interactive) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            } else {
                0.03f
            }
            buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
        }

        val host = FrameLayout(appContext).apply {
            setBackgroundColor(if (interactive) Color.TRANSPARENT else Color.BLACK)
        }

        val color = runCatching {
            Color.parseColor(style.colorHex)
        }.getOrDefault(Color.WHITE)

        val view = LuminousCircleView(appContext).apply {
            setAesthetics(
                color,
                style.thickness.coerceIn(8f, 60f),
                style.glow.coerceIn(0f, 80f)
            )
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
            startAnimation(
                AlphaAnimation(0.08f, 0.72f).apply {
                    duration = style.pulseSpeedMs.coerceIn(250L, 3_000L)
                    repeatMode = Animation.REVERSE
                    repeatCount = Animation.INFINITE
                }
            )
        }

        val text = TextView(appContext).apply {
            this.text = label
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply {
                topMargin = (240 * resources.displayMetrics.density).toInt()
            }
        }

        host.addView(view)
        host.addView(text)

        runCatching {
            manager.addView(host, params)
        }.onSuccess {
            container = host
            circle = view
            runCatching { onShown?.invoke() }
            handler.postDelayed(
                timeout,
                style.displayDurationMs.coerceIn(3_000L, 120_000L)
            )
            handler.post(pixelShift)
        }
    }

    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(::hide)
            return
        }
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(pixelShift)
        container?.let { runCatching { windowManager?.removeView(it) } }
        container = null
        circle = null
    }

    private fun defaultStyle() = LuminousAlertStyle(
        colorHex = settings.circleColorHex,
        thickness = settings.circleThickness,
        glow = settings.circleGlow,
        pulseSpeedMs = settings.pulseSpeedMs,
        displayDurationMs = 15_000L
    )
}
