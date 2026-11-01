package com.example.tvlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

class EffectsView(ctx: Context, private val sprites: List<Bitmap>) : View(ctx) {

    private class Particle(val dx: Float, val dy: Float, val r: Float, val delay: Long)
    private class Puff(val x: Float, val y: Float, val t0: Long, val parts: List<Particle>)
    private class Clone(val x: Float, val y: Float, val pose: Int, val phase: Float, val born: Long)
    private class Leaf(val x0: Float, val y0: Float, val speed: Float, val sway: Float, val phase: Float, val s: Float)

    private val d = resources.displayMetrics.density
    private val size = 72f * d
    private val puffs = ArrayList<Puff>()
    private var main: Clone? = null
    private val idle = ArrayList<Clone>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val poses: Int get() = if (sprites.isEmpty()) 3 else sprites.size
    private val leaves = ArrayList<Leaf>()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        leaves.clear()
        for (i in 0 until 7) {
            leaves.add(
                Leaf(
                    Random.nextFloat() * w,
                    Random.nextFloat() * h,
                    (0.02f + Random.nextFloat() * 0.03f) * d,
                    (20f + Random.nextFloat() * 30f) * d,
                    Random.nextFloat() * 6f,
                    (6f + Random.nextFloat() * 6f) * d
                )
            )
        }
    }

    private fun drawLeaves(canvas: Canvas, now: Long) {
        if (height == 0) return
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(200, 46, 94, 30)
        for (l in leaves) {
            val y = (l.y0 + now * l.speed) % (height + 60f * d) - 30f * d
            val x = l.x0 + sin(now / 1100f + l.phase) * l.sway
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(now / 14f + l.phase * 40f)
            canvas.drawOval(RectF(-l.s, -l.s * 0.45f, l.s, l.s * 0.45f), paint)
            canvas.restore()
        }
    }

    private fun addPuff(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val parts = List(12) {
            Particle(
                (Random.nextFloat() * 2f - 1f) * size * 0.55f,
                (Random.nextFloat() * 2f - 1f) * size * 0.45f,
                size * (0.22f + Random.nextFloat() * 0.2f),
                Random.nextLong(0L, 120L)
            )
        }
        puffs.add(Puff(x, y, now, parts))
    }

    fun moveMain(cx: Float, cy: Float) {
        val now = SystemClock.uptimeMillis()
        main?.let { addPuff(it.x, it.y - size * 0.45f) }
        main = Clone(cx, cy, Random.nextInt(poses), 0f, now)
        addPuff(cx, cy - size * 0.45f)
        invalidate()
    }

    fun startIdle(cx: Float, cy: Float, spacing: Float) {
        if (idle.isNotEmpty()) return
        val now = SystemClock.uptimeMillis()
        var i = 0
        for (k in intArrayOf(-2, -1, 1, 2)) {
            val x = cx + k * spacing
            if (x < size / 2 || x > width - size / 2) continue
            val idx = i
            i++
            idle.add(Clone(x, cy, idx, idx * 1.3f, now + idx * 90L))
            addPuff(x, cy - size * 0.45f)
        }
        invalidate()
    }

    fun stopIdle() {
        if (idle.isEmpty()) return
        for (c in idle) addPuff(c.x, c.y - size * 0.45f)
        idle.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        drawLeaves(canvas, now)
        main?.let { drawClone(canvas, it, now, false) }
        for (c in idle) drawClone(canvas, c, now, true)

        val iter = puffs.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            if (now - p.t0 > 1000L) {
                iter.remove()
                continue
            }
            drawPuff(canvas, p, now)
        }
        postInvalidateOnAnimation()
    }

    private fun drawPuff(canvas: Canvas, p: Puff, now: Long) {
        paint.style = Paint.Style.FILL
        for (q in p.parts) {
            val t = now - p.t0 - q.delay
            if (t < 0L) continue
            val f = (t / 650f).coerceIn(0f, 1f)
            paint.color = Color.argb(((1f - f) * 200f).toInt(), 245, 250, 255)
            canvas.drawCircle(
                p.x + q.dx * f,
                p.y + q.dy * f - size * 0.1f * f,
                q.r * (0.5f + 1.1f * f),
                paint
            )
        }
    }

    private fun drawClone(canvas: Canvas, c: Clone, now: Long, isIdle: Boolean) {
        if (now < c.born) return
        val t = ((now - c.born) / 240f).coerceIn(0f, 1f)
        val s = 1f - (1f - t) * (1f - t)
        val speed = if (isIdle) 380f else 700f
        val amp = if (isIdle) 4f else 2f
        val bob = sin(now / speed + c.phase) * amp * d
        val pose = if (isIdle) (c.pose + (now / 1300L).toInt()) % poses else c.pose

        canvas.save()
        canvas.translate(c.x, c.y + bob)
        canvas.scale(s, s)
        if (sprites.isEmpty()) {
            drawPlaceholder(canvas, pose)
        } else {
            drawSprite(canvas, sprites[pose % sprites.size])
        }
        canvas.restore()
    }

    private fun drawSprite(canvas: Canvas, bmp: Bitmap) {
        val h = size * 1.2f
        val w = h * bmp.width / bmp.height
        paint.isFilterBitmap = true
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawBitmap(bmp, null, RectF(-w / 2f, -h, w / 2f, 0f), paint)
    }

    private fun arm(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        canvas.drawLine(x1 * size, y1 * size, x2 * size, y2 * size, paint)
    }

    private fun drawPlaceholder(canvas: Canvas, pose: Int) {
        val h = size
        paint.style = Paint.Style.FILL
        paint.color = 0xFF37474F.toInt()
        canvas.drawRoundRect(RectF(-0.17f * h, -0.30f * h, 0.17f * h, 0f), 8f, 8f, paint)
        paint.color = 0xFFF57C00.toInt()
        canvas.drawRoundRect(RectF(-0.22f * h, -0.62f * h, 0.22f * h, -0.28f * h), 12f, 12f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.09f * h
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = 0xFFF57C00.toInt()
        when (pose % 3) {
            0 -> {
                arm(canvas, -0.22f, -0.58f, -0.34f, -0.36f)
                arm(canvas, 0.22f, -0.58f, 0.34f, -0.36f)
            }
            1 -> {
                arm(canvas, -0.22f, -0.58f, -0.40f, -0.82f)
                arm(canvas, 0.22f, -0.58f, 0.40f, -0.82f)
            }
            else -> {
                arm(canvas, -0.22f, -0.56f, -0.05f, -0.50f)
                arm(canvas, 0.22f, -0.56f, 0.05f, -0.50f)
            }
        }
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFCCA0.toInt()
        canvas.drawCircle(0f, -0.76f * h, 0.17f * h, paint)
        paint.color = 0xFF1565C0.toInt()
        canvas.drawRect(-0.18f * h, -0.81f * h, 0.18f * h, -0.75f * h, paint)
        paint.color = 0xFF212121.toInt()
        canvas.drawCircle(-0.06f * h, -0.71f * h, 0.018f * h, paint)
        canvas.drawCircle(0.06f * h, -0.71f * h, 0.018f * h, paint)
    }
}
