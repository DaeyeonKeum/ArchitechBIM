package com.poscoenc.bim.sitemeasure

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/** 산출 내역: 저장한 측정을 최신순으로 보고, 근거 사진을 열고, CSV로 내보낸다 */
class RecordsActivity : AppCompatActivity() {

    private var records: List<Record> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_records)
        findViewById<Button>(R.id.btnExport).setOnClickListener {
            val i = RecordStore.shareCsvIntent(this)
            if (i == null) Toast.makeText(this, "아직 저장한 내역이 없습니다.", Toast.LENGTH_SHORT).show()
            else startActivity(Intent.createChooser(i, "CSV 내보내기"))
        }
        findViewById<ListView>(R.id.list).setOnItemClickListener { _, _, pos, _ ->
            val r = records.getOrNull(pos) ?: return@setOnItemClickListener
            if (r.photo.isBlank()) return@setOnItemClickListener
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(RecordStore.photoUri(this@RecordsActivity, r.photo), "image/jpeg")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try { startActivity(view) } catch (e: Exception) { Toast.makeText(this, "사진을 열 앱이 없습니다.", Toast.LENGTH_SHORT).show() }
        }
    }

    override fun onResume() {
        super.onResume()
        records = RecordStore.all(this)
        val rows = records.map {
            val v = when (it.unit) {
                "개" -> it.value.toInt().toString() + " 개"
                "m" -> String.format(Locale.US, "%.3f m", it.value)
                else -> String.format(Locale.US, "%.2f ", it.value) + it.unit
            }
            RecordStore.fmtTime(it.time) + "\n" + it.type + " · " + it.label + " · " + v + "\n" + it.detail
        }
        findViewById<ListView>(R.id.list).adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
        val n = records.size
        val dist = records.count { it.type == "거리" }
        val cnt = records.filter { it.unit == "개" }.sumOf { it.value.toInt() }
        findViewById<TextView>(R.id.summary).text =
            if (n == 0) "저장한 내역이 없습니다. AR 실측의 ‘저장’이나 사진 물량 산출 내역의 ‘산출 내역에 저장’을 누르세요."
            else "모두 " + n + "건 · AR 거리 " + dist + "건 · 개수 합계 " + cnt + "개 · 항목을 누르면 근거 사진이 열립니다."
    }
}
