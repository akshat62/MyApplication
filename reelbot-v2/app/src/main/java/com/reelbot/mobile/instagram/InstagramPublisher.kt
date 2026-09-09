package com.reelbot.mobile.instagram

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class InstagramPublisher {
    data class PublishResult(val mediaId: String)

    suspend fun publish(file: File, caption: String, igUserId: String, token: String): PublishResult = withContext(Dispatchers.IO) {
        require(igUserId.isNotBlank() && token.isNotBlank()) { "Instagram API credentials are not configured" }
        val create = postForm("https://graph.facebook.com/${enc(igUserId)}/media", mapOf(
            "media_type" to "REELS", "upload_type" to "resumable", "caption" to caption, "access_token" to token
        ))
        val container = create.getString("id"); val uploadUri = create.getString("uri")
        uploadBinary(uploadUri, file, token)
        var ready = false; var lastStatus = ""
        for (attempt in 0 until 40) {
            val status = getJson("https://graph.facebook.com/${enc(container)}?fields=status_code,status&access_token=${enc(token)}")
            lastStatus = status.optString("status_code", status.optString("status", ""))
            if (lastStatus == "FINISHED") { ready = true; break }
            if (lastStatus == "ERROR" || lastStatus == "EXPIRED") error("Instagram processing failed: $lastStatus")
            delay(3_000)
        }
        check(ready) { "Instagram processing did not finish (last status: $lastStatus)" }
        val published = postForm("https://graph.facebook.com/${enc(igUserId)}/media_publish", mapOf("creation_id" to container, "access_token" to token))
        PublishResult(published.getString("id"))
    }

    private fun uploadBinary(uri: String, file: File, token: String) {
        val conn = (URL(uri).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 30_000; readTimeout = 120_000
            setRequestProperty("Authorization", "OAuth $token"); setRequestProperty("offset", "0")
            setRequestProperty("file_size", file.length().toString()); setRequestProperty("Content-Type", "application/octet-stream")
            setFixedLengthStreamingMode(file.length())
        }
        conn.outputStream.use { out -> file.inputStream().use { input -> input.copyTo(out, 1024 * 1024) } }
        val body = readBody(conn); if (conn.responseCode !in 200..299) error("Instagram upload HTTP ${conn.responseCode}: $body")
        conn.disconnect()
    }

    private fun postForm(url: String, fields: Map<String,String>): JSONObject {
        val body = fields.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }.toByteArray()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 30_000; readTimeout = 60_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); setFixedLengthStreamingMode(body.size)
        }
        conn.outputStream.use { it.write(body) }; val response = readBody(conn)
        if (conn.responseCode !in 200..299) error("Instagram API HTTP ${conn.responseCode}: $response")
        conn.disconnect(); return JSONObject(response)
    }

    private fun getJson(url: String): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 20_000; readTimeout = 30_000 }
        val body = readBody(conn); if (conn.responseCode !in 200..299) error("Instagram API HTTP ${conn.responseCode}: $body")
        conn.disconnect(); return JSONObject(body)
    }
    private fun readBody(conn: HttpURLConnection): String = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")
}
