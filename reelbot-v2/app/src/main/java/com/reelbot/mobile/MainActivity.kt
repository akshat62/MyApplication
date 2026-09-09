package com.reelbot.mobile

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import com.reelbot.mobile.ai.*
import com.reelbot.mobile.export.ReelExporter
import com.reelbot.mobile.face.FaceAnalyzer
import com.reelbot.mobile.instagram.InstagramPublisher
import com.reelbot.mobile.instagram.TokenVault
import com.reelbot.mobile.model.*
import com.reelbot.mobile.store.ReelStore
import com.reelbot.mobile.util.ShareUtils
import com.reelbot.mobile.util.VideoIngest
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

@UnstableApi
class MainActivity : AppCompatActivity() {
    companion object { const val PICK_VIDEO = 7001 }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var status: TextView
    private lateinit var queueBox: LinearLayout
    private lateinit var language: EditText
    private lateinit var autoPost: CheckBox
    private lateinit var igId: EditText
    private lateinit var igToken: EditText
    private lateinit var generate: Button
    private lateinit var modelButton: Button
    private lateinit var store: ReelStore
    private lateinit var vault: TokenVault
    private val drafts = mutableListOf<ReelDraft>()
    private var sourceUri: Uri? = null
    private var sourceFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ReelStore(this); drafts += store.load(); vault = TokenVault(this)
        setContentView(buildUi()); refreshQueue(); refreshModelState()
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(16,19,26)) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(20), dp(18), dp(30)) }
        scroll.addView(root)
        root.addView(text("ReelBot Mobile", 28, true))
        root.addView(text("100% phone processing • AI highlights • subtitles • smart face crop • approval • Instagram API", 14, false).apply { setTextColor(Color.LTGRAY) })
        root.addView(space(12))
        root.addView(button("1. Choose source video") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="video/*"; addCategory(Intent.CATEGORY_OPENABLE); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) }, PICK_VIDEO) })
        modelButton = button("2. Download on-device AI model (~142 MB)") { downloadModel() }; root.addView(modelButton)
        language = input("Transcription language: en / hi / auto", "en"); root.addView(language)
        autoPost = CheckBox(this).apply { text="Full Auto: publish generated Reels without approval"; setTextColor(Color.WHITE) }; root.addView(autoPost)
        generate = button("3. Generate 3 AI Reels") { runPipeline() }.apply { isEnabled=false }; root.addView(generate)
        status = text("Select a source video and install the AI model.", 14, false).apply { setTextColor(0xFF88C0FF.toInt()); setPadding(0,dp(10),0,dp(10)) }; root.addView(status)
        root.addView(section("Instagram direct publishing"))
        igId = input("Instagram professional account ID", vault.accountId()); root.addView(igId)
        igToken = input("Content Publishing access token", "").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }; root.addView(igToken)
        root.addView(button("Save Instagram credentials securely") {
            if (igId.text.isNotBlank()) vault.saveAccountId(igId.text.toString())
            if (igToken.text.isNotBlank()) { vault.saveToken(igToken.text.toString()); igToken.setText("") }
            toast("Instagram settings saved in Android Keystore")
        })
        root.addView(text("Direct posting requires a valid Meta/Instagram Content Publishing token and professional IG account ID. Without them, generated Reels stay in the approval queue and can still be shared to Instagram.", 12, false).apply { setTextColor(Color.GRAY) })
        root.addView(section("Approval queue"))
        queueBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; root.addView(queueBox)
        return scroll
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_VIDEO && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            sourceUri = uri; sourceFile = null; status.text = "Video selected. Tap Generate 3 AI Reels."; updateGenerateEnabled()
        }
    }

    private fun downloadModel() {
        modelButton.isEnabled=false
        scope.launch {
            try {
                status.text="Downloading multilingual Whisper base model…"
                ModelDownloader(this@MainActivity).download { p -> runOnUiThread { status.text="Downloading AI model… $p%" } }
                status.text="AI model installed. Transcription runs locally on this phone."; refreshModelState(); updateGenerateEnabled()
            } catch (e: Exception) { status.text="Model download failed: ${e.message}"; modelButton.isEnabled=true }
        }
    }

    private fun runPipeline() {
        val uri = sourceUri ?: return
        generate.isEnabled=false
        scope.launch {
            try {
                status.text="Copying source video into private app storage…"
                val local = withContext(Dispatchers.IO) { sourceFile ?: VideoIngest.copyToPrivateStorage(this@MainActivity, uri).also { sourceFile=it } }
                val audio = File(cacheDir, "audio_${System.currentTimeMillis()}.wav")
                status.text="Extracting audio on phone…"
                withContext(Dispatchers.IO) { AudioExtractor.extractWav(local, audio) }
                status.text="AI transcription running locally…"
                val segments = WhisperTranscriber(this@MainActivity).transcribe(audio, ModelDownloader(this@MainActivity).modelFile, language.text.toString())
                audio.delete()
                status.text="AI is scoring the strongest moments…"
                val clips = withContext(Dispatchers.Default) { SmartClipEngine.select(segments, 3, 45_000) }
                val exporter = ReelExporter(this@MainActivity)
                clips.forEachIndexed { index, clip ->
                    status.text="Reel ${index+1}/${clips.size}: detecting speaker face…"
                    val center = withContext(Dispatchers.IO) { FaceAnalyzer.averageFaceCenterX(local, clip.startMs, clip.endMs) }
                    status.text="Reel ${index+1}/${clips.size}: rendering 9:16 + subtitles…"
                    val file = exporter.export(local, clip, center, index+1)
                    val draft = ReelDraft(UUID.randomUUID().toString(), file.absolutePath, clip.startMs, clip.endMs, clip.title, clip.caption)
                    drafts.add(0, draft); store.save(drafts); refreshQueue()
                    if (autoPost.isChecked && vault.accountId().isNotBlank() && vault.token().isNotBlank()) {
                        draft.state = DraftState.APPROVED; store.save(drafts); refreshQueue(); publishDraft(draft, automatic = true)
                    }
                }
                status.text="Done: ${clips.size} Reels created on this phone."
            } catch (e: Exception) {
                status.text="Pipeline failed: ${e.message ?: e.javaClass.simpleName}"; toast(status.text.toString())
            } finally { updateGenerateEnabled() }
        }
    }

    private suspend fun publishDraft(draft: ReelDraft, automatic: Boolean) {
        try {
            status.text="Uploading ${draft.title.take(28)} to Instagram…"
            val result = InstagramPublisher().publish(File(draft.filePath), draft.caption, vault.accountId(), vault.token())
            draft.state=DraftState.POSTED; draft.message="Instagram media ${result.mediaId}"; store.save(drafts); refreshQueue()
            status.text="Published to Instagram successfully."
        } catch (e: Exception) {
            draft.state=DraftState.FAILED; draft.message=e.message ?: "Publishing failed"; store.save(drafts); refreshQueue()
            if (automatic) status.text="Auto-post failed; Reel kept in queue: ${draft.message}" else throw e
        }
    }

    private fun refreshQueue() {
        if (!::queueBox.isInitialized) return
        queueBox.removeAllViews()
        if (drafts.isEmpty()) { queueBox.addView(text("No generated Reels yet.",14,false).apply { setTextColor(Color.GRAY) }); return }
        drafts.take(12).forEach { d ->
            val card = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)); setBackgroundResource(R.drawable.card_bg) }
            card.addView(text(d.title,16,true)); card.addView(text("${format(d.startMs)}–${format(d.endMs)} • ${d.state}",12,false).apply { setTextColor(Color.LTGRAY) })
            if (d.message.isNotBlank()) card.addView(text(d.message,11,false).apply { setTextColor(Color.GRAY) })
            val row = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            row.addView(smallButton("Preview") { ShareUtils.preview(this, File(d.filePath)) })
            if (d.state == DraftState.PENDING || d.state == DraftState.FAILED) row.addView(smallButton("Approve") { d.state=DraftState.APPROVED; d.message=""; store.save(drafts); refreshQueue() })
            if (d.state == DraftState.APPROVED || d.state == DraftState.FAILED) row.addView(smallButton("Post") { scope.launch { try { publishDraft(d,false) } catch(e:Exception) { toast(e.message ?: "Post failed") } } })
            row.addView(smallButton("Share") { ShareUtils.instagram(this, File(d.filePath), d.caption) })
            card.addView(row); card.addView(text(d.caption,12,false).apply { setTextColor(Color.LTGRAY) })
            queueBox.addView(card); queueBox.addView(space(10))
        }
    }

    private fun refreshModelState() { if (ModelDownloader(this).modelFile.exists()) { modelButton.text="✓ On-device AI model installed"; modelButton.isEnabled=false } else modelButton.isEnabled=true; updateGenerateEnabled() }
    private fun updateGenerateEnabled() { if (::generate.isInitialized) generate.isEnabled = sourceUri != null && ModelDownloader(this).modelFile.exists() }
    private fun text(s:String,size:Int,bold:Boolean)=TextView(this).apply { text=s; textSize=size.toFloat(); setTextColor(Color.WHITE); if(bold) setTypeface(typeface,android.graphics.Typeface.BOLD) }
    private fun section(s:String)=text(s,20,true).apply { setPadding(0,dp(20),0,dp(8)) }
    private fun input(hint:String,value:String)=EditText(this).apply { this.hint=hint; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); setText(value); setPadding(dp(10),dp(10),dp(10),dp(10)) }
    private fun button(label:String,onClick:()->Unit)=Button(this).apply { text=label; setOnClickListener { onClick() } }
    private fun smallButton(label:String,onClick:()->Unit)=Button(this).apply { text=label; textSize=11f; setOnClickListener { onClick() }; layoutParams=LinearLayout.LayoutParams(0,dp(44),1f) }
    private fun space(h:Int)=Space(this).apply { layoutParams=LinearLayout.LayoutParams(1,dp(h)) }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun format(ms:Long)=String.format("%d:%02d",ms/60000,(ms/1000)%60)
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
