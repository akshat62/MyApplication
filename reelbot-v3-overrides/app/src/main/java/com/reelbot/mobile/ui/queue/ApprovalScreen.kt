package com.reelbot.mobile.ui.queue

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ApprovalScreen(vm: QueueViewModel, jobId: String) {
    val jobs by vm.jobs.collectAsState()
    val job = jobs.firstOrNull { it.id == jobId }
    if (job == null) { Text("Reel is unavailable."); return }
    var caption by remember(job.id, job.caption) { mutableStateOf(job.caption ?: "") }
    var hashtags by remember(job.id, job.hashtags) { mutableStateOf(job.hashtags ?: "") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Review Reel", style = MaterialTheme.typography.headlineMedium)
        Text(job.status.name.replace('_', ' '))
        job.outputFilePath?.let { path ->
            if (java.io.File(path).isFile) com.reelbot.mobile.ui.VideoPreview(android.net.Uri.fromFile(java.io.File(path)))
        }
        job.failureDetail?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        job.highlightScore?.let { Text("Highlight score: ${it.toInt()}/100") }
        OutlinedTextField(value = caption, onValueChange = { caption = it }, label = { Text("Caption") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = hashtags, onValueChange = { hashtags = it }, label = { Text("Hashtags") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.saveText(job.id, caption, hashtags) }) { Text("Save changes") }
        val reviewable = job.status in listOf(com.reelbot.mobile.data.model.JobStatus.READY_FOR_REVIEW, com.reelbot.mobile.data.model.JobStatus.REJECTED)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.approve(job.id) }, enabled = reviewable) { Text("Approve") }
            OutlinedButton(onClick = { vm.reject(job.id) }, enabled = reviewable) { Text("Reject") }
        }
        if (job.status == com.reelbot.mobile.data.model.JobStatus.APPROVED) Button(onClick = { vm.postNow(job.id) }) { Text("Post Now") }
        if (!job.status.isProcessing && job.status != com.reelbot.mobile.data.model.JobStatus.IMPORTED) {
            OutlinedButton(onClick = { vm.regenerate(job.id) }) { Text("Regenerate") }
            TextButton(onClick = { vm.delete(job.id) }) { Text("Delete") }
        }
    }
}
