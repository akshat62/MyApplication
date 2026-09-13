package com.reelbot.mobile.ui

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun VideoPreview(uri: Uri) {
    var error by remember(uri) { mutableStateOf<String?>(null) }
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    AndroidView(modifier = Modifier.fillMaxWidth().height(320.dp), factory = { context ->
        VideoView(context).apply {
            videoView = this
            setMediaController(MediaController(context).also { it.setAnchorView(this) })
            setOnErrorListener { _, what, extra -> error = "Playback failed ($what / $extra)."; true }
            setVideoURI(uri)
            setOnPreparedListener { seekTo(1) }
        }
    })
    LaunchedEffect(uri) { videoView?.setVideoURI(uri) }
    DisposableEffect(Unit) { onDispose { videoView?.stopPlayback() } }
    error?.let { Text(it) }
}
