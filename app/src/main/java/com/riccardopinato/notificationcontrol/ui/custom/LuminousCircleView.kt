package com.riccardopinato.notificationcontrol.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class LuminousCircleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var circleColor = Color.WHITE
    private var strokeWidth = 24f
    private var glowRadius = 30f

    private val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val innerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    init {
        updatePaints()
    }

    fun setAesthetics(color: Int, thickness: Float, glow: Float) {
        circleColor = color
        strokeWidth = thickness
        glowRadius = glow
        updatePaints()
        invalidate()
    }

    private fun updatePaints() {
        corePaint.color = circleColor
        corePaint.strokeWidth = strokeWidth
        corePaint.alpha = 255

        innerGlowPaint.color = circleColor
        innerGlowPaint.strokeWidth = strokeWidth + glowRadius * 0.65f
        innerGlowPaint.alpha = 72

        outerGlowPaint.color = circleColor
        outerGlowPaint.strokeWidth = strokeWidth + glowRadius * 1.45f
        outerGlowPaint.alpha = 28
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = minOf(width, height) / 4.5f
        val centerX = width / 2f
        val centerY = height / 2f
        canvas.drawCircle(centerX, centerY, radius, outerGlowPaint)
        canvas.drawCircle(centerX, centerY, radius, innerGlowPaint)
        canvas.drawCircle(centerX, centerY, radius, corePaint)
    }
}
