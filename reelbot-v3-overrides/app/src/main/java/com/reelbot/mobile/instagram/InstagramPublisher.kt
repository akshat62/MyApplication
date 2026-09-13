package com.reelbot.mobile.instagram

import com.reelbot.mobile.ai.PipelineException
import com.reelbot.mobile.data.model.FailureReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class PublishResult(val mediaId: String)

/**
 * Publishes a rendered Reel via Instagram's Content Publishing API using the resumable
 * upload variant (`upload_type=resumable`), which uploads video bytes directly from this
 * device to Meta's servers — this is why ReelBot needs no self-hosted backend or public
 * URL for the video, unlike the simpler `video_url`-based container creation.
 *
 * Real 3-step flow, matching Meta's actual API:
 *  1. POST /{ig-user-id}/media (upload_type=resumable) -> {id, uri}
 *  2. POST <uri> with the raw video bytes (Authorization: OAuth <token>)
 *  3. Poll GET /{container-id}?fields=status_code until FINISHED
 *  4. POST /{ig-user-id}/media_publish (creation_id=<container-id>) -> {id: media-id}
 *
 * Never reports POSTED until step 4 actually returns a media id from Meta.
 */
class InstagramPublisher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build()

    suspend fun publish(
        videoFile: File,
        caption: String,
        session: MetaSession
    ): PublishResult = withContext(Dispatchers.IO) {
        val (containerId, uploadUrl) = createResumableContainer(session, caption, videoFile.length())
        uploadVideoBytes(uploadUrl, videoFile, session.accessToken)
        awaitProcessingFinished(containerId, session.accessToken)
        publishContainer(containerId, session)
    }

    private fun createResumableContainer(session: MetaSession, caption: String, fileSizeBytes: Long): Pair<String, String> {
        val url = "https://graph.facebook.com/$GRAPH_VERSION/${session.igUserId}/media"
        val form = mapOf(
            "media_type" to "REELS",
            "upload_type" to "resumable",
            "caption" to caption,
            "access_token" to session.accessToken
        )
        val json = postForm(url, form)
        val containerId = json.optString("id").ifBlank {
            throw PipelineException(FailureReason.UPLOAD_FAILED, "No container id returned: $json")
        }

        // Meta's resumable containers expose an upload endpoint at a fixed path keyed by
        // container id and API version (not returned in this response for all API
        // versions — construct it explicitly per Meta's documented pattern).
        val uploadUrl = "https://rupload.facebook.com/ig-api-upload/$GRAPH_VERSION/$containerId"
        return containerId to uploadUrl
    }

    private fun uploadVideoBytes(uploadUrl: String, videoFile: File, accessToken: String) {
        val request = Request.Builder()
            .url(uploadUrl)
            .post(videoFile.asRequestBody("application/octet-stream".toMediaType()))
            .addHeader("Authorization", "OAuth $accessToken")
            .addHeader("offset", "0")
            .addHeader("file_size", videoFile.length().toString())
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                throw PipelineException(FailureReason.UPLOAD_FAILED, "Instagram upload HTTP ${response.code}: $body")
            }
        }
    }

    private suspend fun awaitProcessingFinished(containerId: String, accessToken: String) {
        val url = "https://graph.facebook.com/$GRAPH_VERSION/$containerId?fields=status_code&access_token=$accessToken"
        repeat(MAX_POLL_ATTEMPTS) { attempt ->
            val json = getJson(url)
            when (json.optString("status_code")) {
                "FINISHED" -> return
                "ERROR" -> throw PipelineException(
                    FailureReason.META_PROCESSING_FAILED,
                    "Instagram reported an error processing the upload: $json"
                )
                "EXPIRED" -> throw PipelineException(
                    FailureReason.META_PROCESSING_FAILED,
                    "The upload session expired before processing finished."
                )
                else -> { /* IN_PROGRESS — keep polling */ }
            }
            delay(POLL_INTERVAL_MS)
        }
        throw PipelineException(
            FailureReason.META_PROCESSING_FAILED,
            "Instagram didn't finish processing within the expected time."
        )
    }

    private fun publishContainer(containerId: String, session: MetaSession): PublishResult {
        val url = "https://graph.facebook.com/$GRAPH_VERSION/${session.igUserId}/media_publish"
        val json = postForm(url, mapOf("creation_id" to containerId, "access_token" to session.accessToken))
        val mediaId = json.optString("id").ifBlank {
            throw PipelineException(FailureReason.PUBLICATION_REJECTED, "Instagram rejected the publish request: $json")
        }
        return PublishResult(mediaId)
    }

    private fun postForm(url: String, fields: Map<String, String>): JSONObject {
        val body = fields.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val request = Request.Builder().url(url).post(body).build()
        return executeAndParse(request)
    }

    private fun getJson(url: String): JSONObject = executeAndParse(Request.Builder().url(url).build())

    private fun executeAndParse(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            val json = try {
                JSONObject(bodyStr)
            } catch (e: Exception) {
                throw PipelineException(FailureReason.UPLOAD_FAILED, "Non-JSON response from Meta: $bodyStr")
            }
            if (!response.isSuccessful) {
                val error = json.optJSONObject("error")
                val message = error?.optString("message") ?: bodyStr
                val reason = if (error?.optInt("code") == 190) FailureReason.TOKEN_EXPIRED else FailureReason.UPLOAD_FAILED
                throw PipelineException(reason, message)
            }
            return json
        }
    }

    private fun enc(v: String) = java.net.URLEncoder.encode(v, "UTF-8")

    companion object {
        private const val GRAPH_VERSION = "v21.0"
        private const val MAX_POLL_ATTEMPTS = 30
        private const val POLL_INTERVAL_MS = 3000L
    }
}
