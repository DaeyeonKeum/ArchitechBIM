package com.poscoenc.bim.sitemeasure

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 측정 한 건: 사진, 측정점, 산출 근거를 함께 남긴다 */
data class Record(
    val time: Long,
    val type: String,      // "거리" | "개수"
    val label: String,
    val value: Double,
    val unit: String,      // "m" | "개"
    val detail: String,    // 측정점 좌표·추적 상태·카메라 거리 등 산출 근거
    val photo: String      // 앱 전용 폴더 안의 파일 이름
)

/** 기기 내부(앱 전용 외부 저장소)에 CSV 한 줄씩 쌓는다. 인터넷 연결이 필요 없다. */
object RecordStore {
    private const val HEADER = "시각,구분,품목,값,단위,산출근거,사진"

    private fun root(ctx: Context): File =
        (ctx.getExternalFilesDir(null) ?: ctx.filesDir).also { it.mkdirs() }

    fun photoDir(ctx: Context): File =
        File(ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: ctx.filesDir, "records").also { it.mkdirs() }

    fun csv(ctx: Context): File = File(root(ctx), "산출내역.csv")

    fun savePhoto(ctx: Context, bmp: Bitmap, prefix: String): String {
        val name = prefix + "_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date()) + ".jpg"
        FileOutputStream(File(photoDir(ctx), name)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return name
    }

    fun add(ctx: Context, r: Record) {
        val f = csv(ctx)
        val isNew = !f.exists()
        f.appendText(
            (if (isNew) "﻿" + HEADER + "\n" else "") +
                listOf(fmtTime(r.time), r.type, r.label, "%.3f".format(Locale.US, r.value), r.unit, r.detail, r.photo)
                    .joinToString(",") { esc(it) } + "\n",
            Charsets.UTF_8
        )
    }

    fun all(ctx: Context): List<Record> {
        val f = csv(ctx)
        if (!f.exists()) return emptyList()
        return f.readLines(Charsets.UTF_8).drop(1).mapNotNull { line ->
            val c = parse(line)
            if (c.size < 7) null else Record(
                time = parseTime(c[0]), type = c[1], label = c[2],
                value = c[3].toDoubleOrNull() ?: 0.0, unit = c[4], detail = c[5], photo = c[6]
            )
        }.reversed()
    }

    fun photoUri(ctx: Context, name: String): Uri =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(photoDir(ctx), name))

    fun shareCsvIntent(ctx: Context): Intent? {
        val f = csv(ctx)
        if (!f.exists()) return null
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "AI 사진분석 물량 산출 내역")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun fmtTime(t: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date(t))
    private fun parseTime(s: String): Long =
        try { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).parse(s.trimStart('﻿'))?.time ?: 0L } catch (e: Exception) { 0L }

    private fun esc(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    private fun parse(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var q = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                q && ch == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                ch == '"' -> q = !q
                ch == ',' && !q -> { out.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(ch)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }
}
