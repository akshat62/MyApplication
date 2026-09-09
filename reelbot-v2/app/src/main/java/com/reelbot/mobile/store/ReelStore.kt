package com.reelbot.mobile.store

import android.content.Context
import com.reelbot.mobile.model.DraftState
import com.reelbot.mobile.model.ReelDraft
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ReelStore(context: Context) {
    private val file = File(context.filesDir, "reel_queue.json")

    fun load(): MutableList<ReelDraft> = try {
        if (!file.exists()) return mutableListOf()
        val arr = JSONArray(file.readText())
        MutableList(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            ReelDraft(
                id = o.getString("id"), filePath = o.getString("filePath"),
                startMs = o.getLong("startMs"), endMs = o.getLong("endMs"),
                title = o.getString("title"), caption = o.getString("caption"),
                state = DraftState.valueOf(o.optString("state", "PENDING")),
                message = o.optString("message", "")
            )
        }
    } catch (_: Exception) { mutableListOf() }

    fun save(items: List<ReelDraft>) {
        val arr = JSONArray()
        items.forEach { d ->
            arr.put(JSONObject().apply {
                put("id", d.id); put("filePath", d.filePath); put("startMs", d.startMs)
                put("endMs", d.endMs); put("title", d.title); put("caption", d.caption)
                put("state", d.state.name); put("message", d.message)
            })
        }
        file.writeText(arr.toString())
    }
}
