package com.poscoenc.bim.sitemeasure

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sqrt

/**
 * AR 실측: 기준 치수 없이 2점 거리 측정.
 * 화면 가운데 조준점이 가리키는 표면(깊이 → 평면 → 특징점 순)에 앵커를 만들고, 두 앵커 사이 3차원 거리를 잰다.
 */
class MeasureActivity : AppCompatActivity(), GLSurfaceView.Renderer {

    private lateinit var surface: GLSurfaceView
    private lateinit var overlay: OverlayView
    private lateinit var status: TextView
    private lateinit var result: TextView
    private lateinit var resultSub: TextView

    private var session: Session? = null
    private var installRequested = false
    private var depthOn = false
    private val bg = BackgroundRenderer()

    // GL 스레드에서만 만지는 상태
    private val anchors = mutableListOf<Anchor>()
    private var viewW = 0
    private var viewH = 0
    private var lastRotation = -1
    private var lastHit: HitResult? = null
    private var lastHitKind: String? = null

    // UI → GL 스레드 요청
    @Volatile private var reqAdd = false
    @Volatile private var reqUndo = false
    @Volatile private var reqReset = false
    @Volatile private var reqSave = false

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) { Toast.makeText(this, R.string.camera_needed, Toast.LENGTH_LONG).show(); finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_measure)
        surface = findViewById(R.id.surface)
        overlay = findViewById(R.id.overlay)
        status = findViewById(R.id.status)
        result = findViewById(R.id.result)
        resultSub = findViewById(R.id.resultSub)

        surface.preserveEGLContextOnPause = true
        surface.setEGLContextClientVersion(2)
        surface.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        surface.setRenderer(this)
        surface.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        surface.setWillNotDraw(false)

        findViewById<Button>(R.id.btnAdd).setOnClickListener { reqAdd = true }
        findViewById<Button>(R.id.btnUndo).setOnClickListener { reqUndo = true }
        findViewById<Button>(R.id.btnReset).setOnClickListener { reqReset = true }
        findViewById<Button>(R.id.btnSave).setOnClickListener { reqSave = true }
        status.setText(R.string.hint_scan)
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            askCamera.launch(Manifest.permission.CAMERA)
            return
        }
        if (session == null) {
            try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> { installRequested = true; return }
                    ArCoreApk.InstallStatus.INSTALLED -> Unit
                    else -> Unit
                }
                val s = Session(this)
                val cfg = Config(s)
                depthOn = s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
                cfg.depthMode = if (depthOn) Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
                cfg.focusMode = Config.FocusMode.AUTO
                cfg.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                cfg.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                s.configure(cfg)
                session = s
            } catch (e: Exception) {
                val msg = when (e) {
                    is UnavailableArcoreNotInstalledException, is UnavailableUserDeclinedInstallationException -> "Google Play AR 서비스를 설치해야 합니다."
                    is UnavailableApkTooOldException -> "Google Play AR 서비스를 업데이트하세요."
                    is UnavailableSdkTooOldException -> "앱을 최신 버전으로 업데이트하세요."
                    is UnavailableDeviceNotCompatibleException -> "이 기기는 ARCore를 지원하지 않습니다."
                    else -> "AR을 시작하지 못했습니다: " + e.javaClass.simpleName
                }
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                finish(); return
            }
        }
        try { session?.resume() } catch (e: CameraNotAvailableException) {
            Toast.makeText(this, "카메라를 쓸 수 없습니다. 다른 앱을 닫고 다시 여세요.", Toast.LENGTH_LONG).show()
            session = null; finish(); return
        }
        surface.onResume()
        resultSub.text = if (depthOn) "깊이(Depth) 사용 · 대상까지 0.5~5 m에서 가장 정확" else "이 기기는 깊이 미지원: 평면·특징점으로 측정 (정확도 낮음)"
    }

    override fun onPause() {
        super.onPause()
        surface.onPause()
        session?.pause()
    }

    override fun onDestroy() {
        session?.close(); session = null
        super.onDestroy()
    }

    // ---------------- GL ----------------
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        bg.createOnGlThread()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewW = width; viewH = height
        lastRotation = -1  // 다음 프레임에서 화면 방향 다시 알림
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) (display?.rotation ?: Surface.ROTATION_0)
        else windowManager.defaultDisplay.rotation

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val s = session ?: return
        val rot = displayRotation()
        if (rot != lastRotation && viewW > 0) { s.setDisplayGeometry(rot, viewW, viewH); lastRotation = rot }
        s.setCameraTextureName(bg.textureId)

        val frame: Frame = try { s.update() } catch (e: CameraNotAvailableException) { return }
        bg.draw(frame)
        val camera = frame.camera

        if (reqReset) { reqReset = false; anchors.forEach { it.detach() }; anchors.clear() }
        if (reqUndo) { reqUndo = false; if (anchors.isNotEmpty()) anchors.removeAt(anchors.size - 1).detach() }

        if (camera.trackingState != TrackingState.TRACKING) {
            ui(trackingMessage(camera.trackingFailureReason), null, null)
            overlay.state = OverlayView.State(points = emptyList(), reticleOk = false)
            reqAdd = false
            return
        }

        // 조준점 아래 표면 찾기
        pickHit(frame)
        val camPose = camera.pose
        val hitDist = lastHit?.let { dist(camPose, it.hitPose) }
        val inRange = hitDist == null || (hitDist in 0.5f..5f)

        if (reqAdd) {
            reqAdd = false
            val h = lastHit
            if (h == null) ui("조준점 아래 표면을 아직 찾지 못했습니다. 조금 움직여 보세요.", null, null)
            else {
                if (anchors.size >= 2) { anchors.forEach { it.detach() }; anchors.clear() }
                anchors.add(h.createAnchor())
            }
        }

        // 화면 좌표로 투영
        camera.getProjectionMatrix(proj, 0, 0.05f, 100f)
        camera.getViewMatrix(view, 0)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        val pts = anchors.map { a -> if (a.trackingState == TrackingState.TRACKING) project(a.pose) else null }
        val preview = lastHit?.let { project(it.hitPose) }

        var label: String? = null
        var main = "– m"
        when (anchors.size) {
            0 -> ui(getString(R.string.hint_first) + rangeNote(hitDist, inRange), main, null)
            1 -> {
                val d = lastHit?.let { dist(anchors[0].pose, it.hitPose) }
                if (d != null) { label = fmt(d); main = fmt(d) }
                ui(getString(R.string.hint_second) + rangeNote(hitDist, inRange), main, null)
            }
            else -> {
                val d = dist(anchors[0].pose, anchors[1].pose)
                label = fmt(d); main = fmt(d)
                ui("측정 완료. ‘저장’을 누르거나 ‘점 찍기’로 새로 재세요.", main, null)
            }
        }
        val st = OverlayView.State(points = pts, preview = if (anchors.size == 1) preview else null, label = label,
            snapLabel = lastHitKind, reticleOk = lastHit != null, inRange = inRange)
        overlay.state = st

        if (reqSave) { reqSave = false; saveSnapshot(st, camPose) }
    }

    /** 깊이 점 → 평면 → 방향 있는 특징점 순으로 가장 믿을 만한 표면을 고른다 */
    private fun pickHit(frame: Frame) {
        lastHit = null; lastHitKind = null
        if (viewW == 0) return
        val hits = frame.hitTest(viewW / 2f, viewH / 2f)
        for (h in hits) {
            val t = h.trackable
            if (t is DepthPoint) { lastHit = h; lastHitKind = "깊이"; return }
        }
        for (h in hits) {
            val t = h.trackable
            if (t is Plane && t.isPoseInPolygon(h.hitPose) && t.trackingState == TrackingState.TRACKING) { lastHit = h; lastHitKind = "평면"; return }
        }
        for (h in hits) {
            val t = h.trackable
            if (t is Point && t.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL) { lastHit = h; lastHitKind = "특징점"; return }
        }
    }

    private fun project(p: Pose): PointF? {
        val v = floatArrayOf(p.tx(), p.ty(), p.tz(), 1f)
        val o = FloatArray(4)
        Matrix.multiplyMV(o, 0, vp, 0, v, 0)
        if (o[3] <= 0f) return null
        val x = (o[0] / o[3] + 1f) / 2f * viewW
        val y = (1f - o[1] / o[3]) / 2f * viewH
        return PointF(x, y)
    }

    private fun dist(a: Pose, b: Pose): Float {
        val dx = a.tx() - b.tx(); val dy = a.ty() - b.ty(); val dz = a.tz() - b.tz()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun fmt(m: Float) = String.format(Locale.US, "%.3f m", m)

    private fun rangeNote(d: Float?, ok: Boolean): String =
        if (d == null) "" else if (ok) "  (대상까지 " + String.format(Locale.US, "%.1f", d) + " m)" else "  ⚠ 대상까지 " + String.format(Locale.US, "%.1f", d) + " m: 0.5~5 m 안에서 재면 더 정확합니다."

    private fun trackingMessage(r: TrackingFailureReason): String = when (r) {
        TrackingFailureReason.INSUFFICIENT_LIGHT -> "너무 어둡습니다. 밝은 곳에서 다시 시도하세요."
        TrackingFailureReason.EXCESSIVE_MOTION -> "휴대폰을 너무 빨리 움직였습니다. 천천히 움직이세요."
        TrackingFailureReason.INSUFFICIENT_FEATURES -> "무늬가 없는 면입니다. 모서리나 무늬가 보이게 비추세요."
        TrackingFailureReason.CAMERA_UNAVAILABLE -> "카메라를 쓸 수 없습니다."
        else -> getString(R.string.hint_scan)
    }

    private fun ui(statusText: String, main: String?, sub: String?) = runOnUiThread {
        status.text = statusText
        if (main != null) result.text = main
        if (sub != null) resultSub.text = sub
    }

    /** 카메라 영상 + 측정 표시를 한 장의 근거 사진으로 저장하고 산출 내역에 기록 */
    private fun saveSnapshot(st: OverlayView.State, camPose: Pose) {
        if (anchors.size < 2) { runOnUiThread { Toast.makeText(this, "두 점을 찍은 뒤 저장하세요.", Toast.LENGTH_SHORT).show() }; return }
        val w = viewW; val h = viewH
        val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        val p1 = anchors[0].pose; val p2 = anchors[1].pose
        val d = dist(p1, p2)
        val detail = String.format(Locale.US,
            "P1(%.3f,%.3f,%.3f) P2(%.3f,%.3f,%.3f) 카메라거리 %.2f/%.2f m · %s",
            p1.tx(), p1.ty(), p1.tz(), p2.tx(), p2.ty(), p2.tz(), dist(camPose, p1), dist(camPose, p2),
            if (depthOn) "Depth 사용" else "Depth 미지원")
        runOnUiThread {
            val raw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            buf.rewind(); raw.copyPixelsFromBuffer(buf)
            val m = android.graphics.Matrix().apply { preScale(1f, -1f) }
            val bmp = Bitmap.createBitmap(raw, 0, 0, w, h, m, false).copy(Bitmap.Config.ARGB_8888, true)
            overlay.drawOnto(Canvas(bmp), w, h)
            val name = RecordStore.savePhoto(this, bmp, "AR거리")
            RecordStore.add(this, Record(System.currentTimeMillis(), "거리", "AR 2점", d.toDouble(), "m", detail, name))
            Toast.makeText(this, "저장했습니다: " + fmt(d), Toast.LENGTH_SHORT).show()
        }
    }
}
