package com.riccardopinato.notificationcontrol.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class LuminousCircleView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {
    private var circleColor = Color.WHITE; private var strokeWidth = 24f; private var glowRadius = 30f
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    init { setLayerType(LAYER_TYPE_SOFTWARE, null); updatePaints() }
    fun setAesthetics(color: Int, thickness: Float, glow: Float) { circleColor = color; strokeWidth = thickness; glowRadius = glow; updatePaints(); invalidate() }
    private fun updatePaints() { corePaint.color = circleColor; corePaint.strokeWidth = strokeWidth; glowPaint.color = circleColor; glowPaint.strokeWidth = strokeWidth; glowPaint.setShadowLayer(glowRadius, 0f, 0f, circleColor) }
    override fun onDraw(canvas: Canvas) { super.onDraw(canvas); val r = minOf(width, height) / 4.5f; canvas.drawCircle(width/2f, height/2f, r, glowPaint); canvas.drawCircle(width/2f, height/2f, r, corePaint) }
}
