package com.poscoenc.bim.sitemeasure

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.util.Base64
import android.webkit.JavascriptInterface
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.ar.core.ArCoreApk
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 사진 물량 화면(JS)에서 부르는 기기 기능. 메서드는 WebView의 별도 스레드에서 불린다. */
class AppBridge(private val act: MainActivity) {

    private fun ui(block: () -> Unit) = act.runOnUiThread(block)
    private fun toast(t: String) = ui { Toast.makeText(act, t, Toast.LENGTH_LONG).show() }

    /** CSV·근거 사진 저장: Android 10 이상은 다운로드/현장포토물량, 그 아래는 앱 전용 다운로드 폴더 */
    @JavascriptInterface
    fun saveFile(name: String, b64: String, mime: String): String = try {
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date())
        val dot = name.lastIndexOf('.')
        val fname = if (dot > 0) name.substring(0, dot) + "_" + stamp + name.substring(dot) else name + "_" + stamp
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fname)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/현장포토물량")
            }
            val uri = act.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: error("insert")
            act.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
            toast("다운로드/현장포토물량/$fname 에 저장했습니다.")
        } else {
            val dir = File(act.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "현장포토물량").apply { mkdirs() }
            File(dir, fname).writeBytes(bytes)
            toast("앱 저장소 Download/현장포토물량/$fname 에 저장했습니다.")
        }
        "ok"
    } catch (e: Exception) {
        "failed"
    }

    @JavascriptInterface
    fun copyText(t: String): Boolean {
        ui {
            val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("물량산출", t))
        }
        return true
    }

    /** 사진 물량 산출 결과를 AR 실측과 같은 산출 내역(CSV)에 쌓는다. 반환: 저장한 건수 */
    @JavascriptInterface
    fun saveRecords(json: String, jpegB64: String): Int = try {
        val arr = JSONArray(json)
        val photo = if (jpegB64.isNotEmpty()) {
            val name = "photo_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date()) + ".jpg"
            File(RecordStore.photoDir(act), name).writeBytes(Base64.decode(jpegB64, Base64.DEFAULT))
            name
        } else ""
        val now = System.currentTimeMillis()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            RecordStore.add(
                act, Record(
                    time = now, type = o.optString("type"), label = o.optString("label"),
                    value = o.optDouble("value", 0.0), unit = o.optString("unit"),
                    detail = o.optString("detail"), photo = photo
                )
            )
        }
        arr.length()
    } catch (e: Exception) {
        -1
    }

    @JavascriptInterface
    fun arStatus(): String {
        val a = ArCoreApk.getInstance().checkAvailability(act)
        val o = JSONObject()
        when {
            a.isTransient -> o.put("ok", false).put("wait", true).put("text", "AR 지원 여부를 확인하는 중…")
            a.isSupported -> o.put("ok", true).put("text", "이 기기는 ARCore를 지원합니다")
            else -> o.put("ok", false).put("text", "이 기기는 ARCore를 지원하지 않아 AR 실측을 쓸 수 없습니다")
        }
        return o.toString()
    }

    @JavascriptInterface
    fun openAR() = ui {
        val a = ArCoreApk.getInstance().checkAvailability(act)
        if (!a.isTransient && !a.isSupported) {
            Toast.makeText(act, "이 기기는 ARCore를 지원하지 않습니다. 사진 물량의 거리 기능을 쓰세요.", Toast.LENGTH_LONG).show()
        } else act.startActivity(Intent(act, MeasureActivity::class.java))
    }

    @JavascriptInterface
    fun openRecords() = ui { act.startActivity(Intent(act, RecordsActivity::class.java)) }

    // ---------- AI 자동 세기 (선택) ----------
    @JavascriptInterface
    fun aiReady(): Boolean = AiClient.key(act).isNotEmpty()

    @JavascriptInterface
    fun aiJson(id: Int, prompt: String, imageB64: String, mime: String) {
        Thread {
            val (ok, text) = AiClient.json(act, prompt, imageB64, mime)
            ui { act.web.evaluateJavascript("window.__appAI&&__appAI.done($id,$ok," + JSONObject.quote(text) + ")", null) }
        }.start()
    }

    /** AI 설정: 키가 있으면 개수 '범위 자동'과 'AI 부재 판별'에 Claude 비전을 쓴다. 없으면 기기 분석으로 센다. */
    @JavascriptInterface
    fun openSettings(hasWork: Boolean) = ui {
        val pad = (20 * act.resources.displayMetrics.density).toInt()
        val keyIn = EditText(act).apply {
            hint = "Anthropic API 키 (sk-ant-…)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(AiClient.key(act))
        }
        val modelIn = EditText(act).apply {
            hint = "모델"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(AiClient.model(act))
        }
        val box = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(keyIn); addView(modelIn)
        }
        val warn = if (hasWork) "\n\n적용하려면 화면을 다시 엽니다. 지금 작업 중인 측정은 지워집니다." else ""
        AlertDialog.Builder(act)
            .setTitle("AI 자동 세기 설정")
            .setMessage(
                "키를 넣으면 개수 ‘범위 자동’과 ‘AI 부재 판별’이 Claude 비전으로 동작합니다. " +
                    "범위로 고른 사진 부분이 Anthropic 서버로 전송되니 회사 보안 기준을 확인하세요. " +
                    "키가 없으면 기기 안에서 직접 셉니다(인터넷 불필요)." + warn
            )
            .setView(box)
            .setPositiveButton("저장") { _, _ ->
                AiClient.save(act, keyIn.text.toString().trim(), modelIn.text.toString().trim())
                act.reloadPage()
            }
            .setNeutralButton("키 지우기") { _, _ ->
                AiClient.save(act, "", "")
                act.reloadPage()
            }
            .setNegativeButton("취소", null)
            .show()
    }
}
