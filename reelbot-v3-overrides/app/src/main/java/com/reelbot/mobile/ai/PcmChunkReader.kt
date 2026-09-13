package com.reelbot.mobile.ai

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reads our extracted PCM16 WAV in bounded chunks; never allocates the whole recording. */
internal class PcmChunkReader(file: File, chunkSeconds: Int = 60) : Closeable {
    private val input = RandomAccessFile(file, "r")
    private val chunkBytes: Int
    private var consumed = 0L
    private val dataBytes: Long
    val durationMs: Long get() = dataBytes * 1000 / 32000

    init {
        try {
            require(chunkSeconds in 1..60) { "Invalid transcription chunk size." }
            chunkBytes = chunkSeconds * 32000
            val header = ByteArray(44)
            input.readFully(header)
            val h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            require(String(header, 0, 4) == "RIFF" && String(header, 8, 4) == "WAVE" &&
                String(header, 12, 4) == "fmt " && h.getInt(16) == 16 &&
                h.getShort(20).toInt() == 1 && h.getShort(22).toInt() == 1 &&
                h.getInt(24) == 16000 && h.getShort(34).toInt() == 16 &&
                String(header, 36, 4) == "data") { "Expected extracted 16 kHz mono PCM16 WAV." }
            dataBytes = input.length() - 44
            require(dataBytes > 0 && dataBytes % 2 == 0L) { "Audio is empty or truncated." }
        } catch (error: Throwable) {
            input.close()
            throw error
        }
    }

    fun next(): PcmChunk? {
        if (consumed >= dataBytes) return null
        val count = minOf(chunkBytes.toLong(), dataBytes - consumed).toInt()
        val bytes = ByteArray(count)
        input.readFully(bytes)
        val pcm = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val samples = FloatArray(count / 2) { pcm.short / 32768f }
        val start = consumed * 1000 / 32000
        consumed += count
        return PcmChunk(start, consumed * 1000 / 32000, samples)
    }

    override fun close() = input.close()
}

internal data class PcmChunk(val startMs: Long, val endMs: Long, val samples: FloatArray)
