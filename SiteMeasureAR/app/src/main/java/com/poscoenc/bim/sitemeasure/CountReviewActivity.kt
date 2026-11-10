package com.poscoenc.bim.sitemeasure

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/** 강재 개수 검토: 사진을 열고 번호를 붙이며 누락·오탐을 고친 뒤 근거 사진과 함께 저장 */
class CountReviewActivity : AppCompatActivity() {

    private lateinit var view: CountView
    private lateinit var count: TextView
    private lateinit var label: EditText

    private val pick = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) load(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_count)
        view = findViewById(R.id.countView)
        count = findViewById(R.id.count)
        label = findViewById(R.id.label)
        view.onChange = { count.text = view.marks.size.toString() + " 개" }

        findViewById<Button>(R.id.btnPick).setOnClickListener {
            pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        findViewById<Button>(R.id.btnUndoCount).setOnClickListener { view.undo() }
        findViewById<Button>(R.id.btnAuto).setOnClickListener {
            val det = Detectors.current
            val bmp = view.bitmap
            when {
                bmp == null -> toast("먼저 사진을 여세요.")
                det == null -> toast("자동 검출은 현장 사진으로 학습한 모델을 넣은 뒤 켜집니다. 지금은 눌러서 세어 주세요.")
                else -> { val pts = det.detect(bmp); view.setDetected(pts); toast(det.name + ": " + pts.size + "개 검출. 누락·오탐을 눌러 고치세요.") }
            }
        }
        findViewById<Button>(R.id.btnSaveCount).setOnClickListener { save() }
    }

    private fun load(uri: Uri) {
        try {
            val src: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { d, _, _ ->
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
            // 메모리를 아끼려고 긴 변 2048 화소로 줄인다
            val k = 2048f / maxOf(src.width, src.height)
            view.bitmap = if (k < 1f) Bitmap.createScaledBitmap(src, (src.width * k).toInt(), (src.height * k).toInt(), true) else src
        } catch (e: Exception) {
            toast("사진을 열 수 없습니다.")
        }
    }

    private fun save() {
        val n = view.marks.size
        val out = view.renderForSave()
        if (out == null || n == 0) { toast("사진을 열고 개수를 표시한 뒤 저장하세요."); return }
        val name = RecordStore.savePhoto(this, out, "개수")
        val item = label.text.toString().ifBlank { "강재" }
        val detail = "수동 검토 " + n + "개" + (Detectors.current?.let { " · 모델 " + it.name } ?: "") +
            " · 사진 " + out.width + "×" + out.height
        RecordStore.add(this, Record(System.currentTimeMillis(), "개수", item, n.toDouble(), "개", detail, name))
        toast("저장했습니다: " + item + " " + n + "개")
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
