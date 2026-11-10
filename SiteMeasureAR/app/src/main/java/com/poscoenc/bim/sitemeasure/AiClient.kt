package com.poscoenc.bim.sitemeasure

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 선택 기능: 사용자가 넣은 Anthropic API 키로 Claude 비전에 사진 일부를 보내 JSON 답을 받는다.
 * 키는 이 앱 전용 저장소에만 두고 화면(JS)에는 넘기지 않는다.
 */
object AiClient {
    private const val PREF = "ai"
    const val DEFAULT_MODEL = "claude-sonnet-5-5"

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    fun key(c: Context): String = prefs(c).getString("key", "") ?: ""
    fun model(c: Context): String = (prefs(c).getString("model", "") ?: "").ifEmpty { DEFAULT_MODEL }
    fun save(c: Context, key: String, model: String) {
        prefs(c).edit().putString("key", key).putString("model", model).apply()
    }

    /** 반환: (성공 여부, 성공이면 JSON 문자열 / 실패면 오류 코드) */
    fun json(c: Context, prompt: String, imageB64: String, mime: String): Pair<Boolean, String> {
        val k = key(c)
        if (k.isEmpty()) return false to "not_granted"
        return try {
            val content = JSONArray()
            if (imageB64.isNotEmpty()) {
                content.put(
                    JSONObject().put("type", "image").put(
                        "source", JSONObject().put("type", "base64").put("media_type", mime.ifEmpty { "image/jpeg" }).put("data", imageB64)
                    )
                )
            }
            content.put(JSONObject().put("type", "text").put("text", prompt))
            val body = JSONObject()
                .put("model", model(c))
                .put("max_tokens", 8000)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))

            val conn = (URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 90000
                doOutput = true
                setRequestProperty("content-type", "application/json")
                setRequestProperty("x-api-key", k)
                setRequestProperty("anthropic-version", "2023-06-01")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code != 200) {
                conn.errorStream?.close()
                return false to when (code) {
                    401, 403 -> "not_granted"
                    429, 529 -> "rate_limited"
                    else -> "failed"
                }
            }
            val resp = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            val blocks = resp.optJSONArray("content") ?: JSONArray()
            val sb = StringBuilder()
            for (i in 0 until blocks.length()) {
                val b = blocks.getJSONObject(i)
                if (b.optString("type") == "text") sb.append(b.optString("text"))
            }
            val out = extractJson(sb.toString()) ?: return false to "bad_json"
            true to out
        } catch (e: Exception) {
            false to "failed"
        }
    }

    /** 답에서 첫 JSON 값(객체나 배열)만 잘라낸다 */
    private fun extractJson(t: String): String? {
        val start = t.indexOfFirst { it == '{' || it == '[' }
        if (start < 0) return null
        val end = maxOf(t.lastIndexOf('}'), t.lastIndexOf(']'))
        if (end <= start) return null
        val s = t.substring(start, end + 1)
        return try {
            if (s.trimStart().startsWith("[")) JSONArray(s).toString() else JSONObject(s).toString()
        } catch (e: Exception) { null }
    }
}
