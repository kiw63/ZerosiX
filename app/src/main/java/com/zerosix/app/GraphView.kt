package com.zerosix.app

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.max

class GraphView : View {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG)
    private val values = ArrayDeque<Float>()
    private var label = "CPU"
    private var suffix = "%"

    constructor(context: android.content.Context) : super(context) {
        paint.typeface = android.graphics.Typeface.MONOSPACE
        grid.color = 0x223DFFFF
        setWillNotDraw(false)
    }

    fun push(value: Float, label: String = this.label, suffix: String = this.suffix) {
        this.label = label; this.suffix = suffix
        if (values.size >= 60) values.removeFirst()
        values.addLast(value)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat(); val h = height.toFloat()
        c.drawColor(0x1400FFFF)
        for (i in 1..4) c.drawLine(0f, h * i / 5f, w, h * i / 5f, grid)
        val p = Path()
        val maxV = max(1f, if (suffix == "%") 100f else (values.maxOrNull() ?: 1f) * 1.15f)
        values.forEachIndexed { i, v ->
            val x = if (values.size <= 1) 0f else i * (w / (values.size - 1).toFloat())
            val y = h - (v / maxV) * (h - 8f)
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = 0xFF7DF9FF.toInt()
        c.drawPath(p, paint)
        paint.style = Paint.Style.FILL
        paint.textSize = 11f
        paint.color = 0xFFA8FFFF.toInt()
        c.drawText(label.uppercase(), 8f, 15f, paint)
    }
}
