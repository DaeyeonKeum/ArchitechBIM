package com.poscoenc.bim.sitemeasure

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import java.io.File

/**
 * 첫 화면 = 사진 물량 (웹 시제품 ver 0.9와 같은 화면·계산)
 * - 개수(범위 자동·비슷한 것·추가/제외), 면적·부피(자동 면·다각형), 토공량, 거리, 기준 치수, 되돌리기·수정
 * - 'AR 실측' 탭에서 기기 AR 측정(MeasureActivity)과 저장된 산출 내역(RecordsActivity)을 연다
 * 화면은 앱 안의 assets/web/index.html 을 https://appassets.androidplatform.net 주소로 연다 (인터넷 없이 동작).
 */
class MainActivity : AppCompatActivity() {

    lateinit var web: WebView
        private set
    private var fileCb: ValueCallback<Array<Uri>>? = null
    private var shotUri: Uri? = null

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { deliver(it) }
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok -> deliver(if (ok) shotUri else null) }
    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else {
            Toast.makeText(this, "카메라 권한이 없어 촬영할 수 없습니다. 앨범에서 고르세요.", Toast.LENGTH_LONG).show()
            deliver(null)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(true)
        web = WebView(this)
        setContentView(web)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            textZoom = 100
            setSupportZoom(false)
        }
        web.addJavascriptInterface(AppBridge(this), "AndroidApp")

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.host == HOST) return false
                try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (_: Exception) { }
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams
            ): Boolean {
                fileCb?.onReceiveValue(null)
                fileCb = cb
                if (params.isCaptureEnabled) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                    else askCamera.launch(Manifest.permission.CAMERA)
                } else {
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                return true
            }

            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                Log.d("PhotoQTO", m.message() + " @" + m.lineNumber())
                return true
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("(window.__appBack&&window.__appBack())?1:0") { r ->
                    if (r != "1") confirmExit()
                }
            }
        })

        web.loadUrl(START)
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    fun reloadPage() = web.loadUrl(START)

    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setMessage("앱을 닫을까요?\n저장하지 않은 측정 내용은 사라집니다.")
            .setPositiveButton("닫기") { _, _ -> finish() }
            .setNegativeButton("계속 작업", null)
            .show()
    }

    private fun launchCamera() {
        try {
            val dir = File(cacheDir, "shots").apply { mkdirs() }
            dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
            val f = File(dir, "shot_" + System.currentTimeMillis() + ".jpg")
            val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
            shotUri = uri
            takePhoto.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(this, "카메라를 열 수 없습니다. 앨범에서 고르세요.", Toast.LENGTH_LONG).show()
            deliver(null)
        }
    }

    private fun deliver(uri: Uri?) {
        fileCb?.onReceiveValue(uri?.let { arrayOf(it) })
        fileCb = null
    }

    companion object {
        const val HOST = "appassets.androidplatform.net"
        const val START = "https://$HOST/assets/web/index.html"
    }
}
