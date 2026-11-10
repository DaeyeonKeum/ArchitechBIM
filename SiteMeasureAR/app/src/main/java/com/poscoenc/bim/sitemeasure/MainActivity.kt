package com.poscoenc.bim.sitemeasure

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.ArCoreApk

/** 첫 화면: AR 실측 · 강재 개수 검토 · 산출 내역 세 가지 모드 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<android.view.View>(R.id.cardAr).setOnClickListener {
            startActivity(Intent(this, MeasureActivity::class.java))
        }
        findViewById<android.view.View>(R.id.cardCount).setOnClickListener {
            startActivity(Intent(this, CountReviewActivity::class.java))
        }
        findViewById<android.view.View>(R.id.cardRecords).setOnClickListener {
            startActivity(Intent(this, RecordsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        showArAvailability()
    }

    /** 이 기기가 ARCore를 지원하는지 첫 화면에 표시 */
    private fun showArAvailability() {
        val tv = findViewById<TextView>(R.id.arStatus)
        val a = ArCoreApk.getInstance().checkAvailability(this)
        tv.text = when {
            a.isTransient -> {
                tv.postDelayed({ showArAvailability() }, 300)
                "AR 지원 여부를 확인하는 중…"
            }
            a.isSupported -> "이 기기는 ARCore를 지원합니다. 대상까지 0.5~5 m 거리에서 가장 정확합니다."
            else -> "이 기기는 ARCore를 지원하지 않아 AR 실측을 쓸 수 없습니다. 강재 개수 검토와 산출 내역은 쓸 수 있습니다."
        }
    }
}
