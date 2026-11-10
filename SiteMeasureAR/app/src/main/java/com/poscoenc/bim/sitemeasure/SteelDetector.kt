package com.poscoenc.bim.sitemeasure

import android.graphics.Bitmap
import android.graphics.PointF

/**
 * 강재 자동 검출 자리.
 * 기본 YOLO 모델은 겹쳐 쌓인 H형강·각관·철근 단면을 구분하도록 학습되지 않았으므로 쓰지 않는다.
 * 현장 사진으로 별도 학습·검증한 모델(TFLite)을 넣으면 detect()가 부재 중심점 목록을 돌려준다.
 */
interface SteelDetector {
    val name: String
    fun detect(photo: Bitmap): List<PointF>
}

object Detectors {
    /** 현장 학습 모델이 준비되면 여기에 연결한다. 그 전에는 null(수동 표시로 검토). */
    var current: SteelDetector? = null
}
