package com.poscoenc.bim.sitemeasure

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** 카메라 영상 위에 조준점, 측정점, 측정선, 거리 라벨을 그린다 (2D) */
class OverlayView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    data class State(
        val points: List<PointF?> = emptyList(),   // 화면 밖이거나 추적 끊김이면 null
        val preview: PointF? = null,               // 조준점이 가리키는 표면 위치
        val label: String? = null,                 // "1.234 m"
        val snapLabel: String? = null,             // "깊이" | "평면" | "특징점"
        val reticleOk: Boolean = false,            // 조준점 아래 표면을 찾았는지
        val inRange: Boolean = true                // 권장 거리(0.5~5 m) 안인지
    )

    @Volatile var state = State()
        set(v) { field = v; postInvalidateOnAnimation() }

    private val dp = resources.displayMetrics.density
    private val accent = Color.parseColor("#2F62F5")
    private val warn = Color.parseColor("#FF9B63")
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 15 * dp; isFakeBoldText = true; textAlign = Paint.Align.CENTER }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawOnto(canvas, width, height)
    }

    /** 저장 사진에도 같은 표시를 그리기 위해 분리 */
    fun drawOnto(c: Canvas, w: Int, h: Int) {
        val s = state
        val pts = s.points
        // 측정선
        if (pts.size >= 2 && pts[0] != null && pts[1] != null) {
            line(c, pts[0]!!, pts[1]!!, accent, 4f)
        } else if (pts.size == 1 && pts[0] != null && s.preview != null) {
            dashed(c, pts[0]!!, s.preview, accent)
        }
        pts.forEachIndexed { i, p -> if (p != null) dot(c, p, accent, (i + 1).toString()) }
        // 거리 라벨
        if (s.label != null) {
            val a = pts.getOrNull(0); val b = pts.getOrNull(1) ?: s.preview
            if (a != null && b != null) pill(c, (a.x + b.x) / 2, (a.y + b.y) / 2 - 28 * dp, s.label, accent)
        }
        // 조준점
        val cx = w / 2f; val cy = h / 2f
        val col = if (!s.reticleOk) Color.WHITE else if (s.inRange) accent else warn
        stroke.color = Color.argb(140, 0, 0, 0); stroke.strokeWidth = 5 * dp
        c.drawCircle(cx, cy, 18 * dp, stroke)
        stroke.color = col; stroke.strokeWidth = 2.5f * dp
        c.drawCircle(cx, cy, 18 * dp, stroke)
        c.drawLine(cx - 28 * dp, cy, cx - 8 * dp, cy, stroke); c.drawLine(cx + 8 * dp, cy, cx + 28 * dp, cy, stroke)
        c.drawLine(cx, cy - 28 * dp, cx, cy - 8 * dp, stroke); c.drawLine(cx, cy + 8 * dp, cx, cy + 28 * dp, stroke)
        if (s.snapLabel != null && s.reticleOk) pill(c, cx, cy + 46 * dp, s.snapLabel, col)
    }

    private fun line(c: Canvas, a: PointF, b: PointF, col: Int, w: Float) {
        stroke.pathEffect = null
        stroke.color = Color.WHITE; stroke.strokeWidth = (w + 3) * dp; c.drawLine(a.x, a.y, b.x, b.y, stroke)
        stroke.color = col; stroke.strokeWidth = w * dp; c.drawLine(a.x, a.y, b.x, b.y, stroke)
    }

    private fun dashed(c: Canvas, a: PointF, b: PointF, col: Int) {
        stroke.color = col; stroke.strokeWidth = 2.5f * dp
        stroke.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10 * dp, 8 * dp), 0f)
        c.drawLine(a.x, a.y, b.x, b.y, stroke)
        stroke.pathEffect = null
    }

    private fun dot(c: Canvas, p: PointF, col: Int, n: String) {
        fill.color = Color.WHITE; c.drawCircle(p.x, p.y, 13 * dp, fill)
        fill.color = col; c.drawCircle(p.x, p.y, 11 * dp, fill)
        text.textSize = 12 * dp; c.drawText(n, p.x, p.y + 4 * dp, text)
    }

    private fun pill(c: Canvas, x: Float, y: Float, t: String, col: Int) {
        text.textSize = 15 * dp
        val w = text.measureText(t) + 24 * dp; val h = 30 * dp
        fill.color = col
        c.drawRoundRect(RectF(x - w / 2, y - h / 2, x + w / 2, y + h / 2), h / 2, h / 2, fill)
        c.drawText(t, x, y + 5 * dp, text)
    }
}
