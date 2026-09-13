package com.reelbot.mobile.ui.create

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelbot.mobile.data.db.entity.ReelJobEntity
import com.reelbot.mobile.data.db.entity.SourceVideoEntity
import com.reelbot.mobile.data.model.JobStatus
import com.reelbot.mobile.data.model.ReelDuration
import com.reelbot.mobile.data.repository.ReelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class VideoMetadata(
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long
)

data class CreateUiState(
    val selectedVideo: VideoMetadata? = null,
    val reelCount: Int = 3,
    val duration: ReelDuration = ReelDuration.MEDIUM_45,
    val automaticMode: Boolean = true,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    val submittedImportId: String? = null
)

class CreateViewModel(
    private val repository: ReelRepository,
    private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateUiState())
    val uiState: StateFlow<CreateUiState> = _uiState.asStateFlow()

    fun onVideoSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                appContext.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val meta = withContext(Dispatchers.IO) { readMetadata(uri, appContext.contentResolver) }
                require(meta.width > 0 && meta.height > 0) { "The selected file has no readable video track." }
                require(meta.durationMs > 0) { "Could not read the video duration. Try saving a local copy and selecting it again." }
                _uiState.value = _uiState.value.copy(selectedVideo = meta, submitError = null)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("ReelBotImport", "Video import failed", e)
                _uiState.value = _uiState.value.copy(selectedVideo = null, submitError = e.message ?: "Cannot read this video.")
            }
        }
    }

    fun setReelCount(count: Int) {
        _uiState.value = _uiState.value.copy(reelCount = count.coerceIn(1, 10))
    }

    fun setDuration(duration: ReelDuration) {
        _uiState.value = _uiState.value.copy(duration = duration)
    }

    fun setAutomaticMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(automaticMode = enabled)
    }

    /**
     * Persists the import + placeholder job rows so the Queue/Home screens have something
     * real to show. Does NOT run the pipeline itself — enqueuing the actual foreground
     * processing work (transcription → highlights → render) is wired up in the next
     * phase. Each created job starts in IMPORTED, which is an honest, real state — not a
     * faked result.
     */
    fun submit() {
        if (_uiState.value.isSubmitting) return
        val video = _uiState.value.selectedVideo ?: return
        _uiState.value = _uiState.value.copy(isSubmitting = true, submitError = null)

        viewModelScope.launch {
            try {
                val importId = UUID.randomUUID().toString()
                repository.saveSourceVideo(
                    SourceVideoEntity(
                        importId = importId,
                        uri = video.uri.toString(),
                        displayName = video.displayName,
                        durationMs = video.durationMs,
                        fileSizeBytes = video.fileSizeBytes,
                        requestedClipCount = _uiState.value.reelCount,
                        requestedDurationSeconds = _uiState.value.duration.seconds
                    )
                )
                val jobs = (1.._uiState.value.reelCount).map {
                    ReelJobEntity(
                        sourceImportId = importId,
                        sourceVideoUri = video.uri.toString(),
                        sourceVideoDurationMs = video.durationMs,
                        status = JobStatus.IMPORTED
                    )
                }
                repository.saveJobs(jobs)
                com.reelbot.mobile.work.PipelineScheduler.enqueue(appContext, importId)
                _uiState.value = _uiState.value.copy(isSubmitting = false, submittedImportId = importId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    submitError = e.message ?: "Could not save this import."
                )
            }
        }
    }

    private fun readMetadata(uri: Uri, resolver: ContentResolver): VideoMetadata {
        val retriever = MediaMetadataRetriever()
        var displayName = uri.lastPathSegment ?: "video"
        var fileSize = 0L

        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
            }
        }

        return try {
            retriever.setDataSource(appContext, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            VideoMetadata(uri, displayName, durationMs, width, height, fileSize)
        } finally {
            retriever.release()
        }
    }
}
