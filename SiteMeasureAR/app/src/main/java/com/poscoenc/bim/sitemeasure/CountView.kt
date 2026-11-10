package com.poscoenc.bim.sitemeasure

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/**
 * 사진 위 개수 표시. 누르면 번호가 붙고, 번호를 다시 누르면 빠진다.
 * 좌표는 사진 화소 기준으로 저장하므로 화면 크기와 무관하다.
 */
class CountView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var bitmap: Bitmap? = null
        set(v) { field = v; marks.clear(); history.clear(); invalidate(); onChange?.invoke() }
    val marks = mutableListOf<PointF>()
    private val history = mutableListOf<List<PointF>>()
    var onChange: (() -> Unit)? = null

    private val dp = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
    private val markColor = Color.parseColor("#FF2D87")
    private val dst = RectF()

    private fun layoutRect(): Float {
        val b = bitmap ?: return 1f
        val s = min(width / b.width.toFloat(), height / b.height.toFloat())
        val w = b.width * s; val h = b.height * s
        dst.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
        return s
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val b = bitmap
        if (b == null) {
            text.textSize = 16 * dp; text.color = Color.parseColor("#9AA5B4")
            canvas.drawText("‘사진 선택’으로 강재가 보이는 사진을 여세요", width / 2f, height / 2f, text)
            text.color = Color.WHITE
            return
        }
        val s = layoutRect()
        canvas.drawBitmap(b, null, dst, null)
        drawMarks(canvas, s, dst.left, dst.top)
    }

    /** 저장용: 원본 해상도 사진에 같은 표시를 그린다 */
    fun renderForSave(): Bitmap? {
        val b = bitmap ?: return null
        val out = b.copy(Bitmap.Config.ARGB_8888, true)
        val k = b.width / 1080f
        drawMarks(Canvas(out), 1f, 0f, 0f, k.coerceAtLeast(1f))
        return out
    }

    private fun drawMarks(c: Canvas, s: Float, ox: Float, oy: Float, unit: Float = dp) {
        val r = 11 * unit
        marks.forEachIndexed { i, p ->
            val x = ox + p.x * s; val y = oy + p.y * s
            fill.color = Color.WHITE; c.drawCircle(x, y, r + 2 * unit, fill)
            fill.color = markColor; c.drawCircle(x, y, r, fill)
            text.textSize = 11 * unit; c.drawText((i + 1).toString(), x, y + 4 * unit, text)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val b = bitmap ?: return false
        if (e.action != MotionEvent.ACTION_UP) return true
        val s = layoutRect()
        val px = (e.x - dst.left) / s; val py = (e.y - dst.top) / s
        if (px < 0 || py < 0 || px > b.width || py > b.height) return true
        history.add(marks.map { PointF(it.x, it.y) })
        val tol = 18 * dp / s
        val hit = marks.minByOrNull { hypot(it.x - px, it.y - py) }
        if (hit != null && hypot(hit.x - px, hit.y - py) < tol) marks.remove(hit) else marks.add(PointF(px, py))
        invalidate(); onChange?.invoke()
        performClick()
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    fun undo() { if (history.isNotEmpty()) { marks.clear(); marks.addAll(history.removeAt(history.size - 1)); invalidate(); onChange?.invoke() } }

    fun setDetected(points: List<PointF>) { history.add(marks.map { PointF(it.x, it.y) }); marks.clear(); marks.addAll(points); invalidate(); onChange?.invoke() }
}
