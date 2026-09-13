package com.reelbot.mobile.ai

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmChunkReaderTest {
    @Test fun readsHourLongAudioWithBoundedMemoryAndAbsoluteOffsets() {
        val file = File.createTempFile("long-recording", ".wav")
        try {
            val seconds = 3661L
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray()).putInt((seconds * 32000 + 36).toInt())
            header.put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            header.putInt(16000).putInt(32000).putShort(2).putShort(16)
            header.put("data".toByteArray()).putInt((seconds * 32000).toInt())
            RandomAccessFile(file, "rw").use { it.write(header.array()); it.setLength(seconds * 32000 + 44) }
            PcmChunkReader(file).use { reader ->
                assertEquals(3661000L, reader.durationMs)
                var end = 0L
                var count = 0
                while (true) {
                    val chunk = reader.next() ?: break
                    assertEquals(end, chunk.startMs)
                    assertTrue(chunk.samples.size <= 960000)
                    end = chunk.endMs
                    count++
                }
                assertEquals(3661000L, end)
                assertEquals(62, count)
            }
        } finally { file.delete() }
    }
}
