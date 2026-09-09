package com.zerosix.app

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

class HudView(context: android.content.Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pulse = 0f
    init { setWillNotDraw(false) }
    fun tick(v: Float) { pulse = v; invalidate() }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawColor(0xFF020308.toInt())
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f
        p.color = 0x223DFFFF
        val step = 48f
        var x = 0f
        while (x < w) { c.drawLine(x, 0f, x, h, p); x += step }
        var y = 0f
        while (y < h) { c.drawLine(0f, y, w, y, p); y += step }
        p.color = 0x557DF9FF
        p.strokeWidth = 2f
        val sweepY = (h * ((pulse % 1f) + 0.1f)).coerceAtMost(h)
        c.drawLine(0f, sweepY, w, sweepY, p)
        p.color = 0x117DF9FF
        p.style = Paint.Style.FILL
        c.drawCircle(w * .82f, h * .15f, 80f + 25f * pulse, p)
    }
}
