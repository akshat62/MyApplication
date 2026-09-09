package com.reelbot.mobile.ai

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.RandomAccessFile

object AudioExtractor {
    fun extractWav(video: File, output: File): File {
        val extractor = MediaExtractor()
        extractor.setDataSource(video.absolutePath)
        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                trackIndex = i; format = f; break
            }
        }
        require(trackIndex >= 0 && format != null) { "No audio track found" }
        extractor.selectTrack(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        try { format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT) } catch (_: Exception) {}
        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()

        val raw = File(output.parentFile, output.nameWithoutExtension + ".pcm")
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        raw.outputStream().use { pcm ->
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val outIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFormat = decoder.outputFormat
                        if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (outFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    else -> if (outIndex >= 0) {
                        val buffer = decoder.getOutputBuffer(outIndex)!!
                        if (info.size > 0) {
                            buffer.position(info.offset); buffer.limit(info.offset + info.size)
                            val bytes = ByteArray(info.size); buffer.get(bytes); pcm.write(bytes)
                        }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(outIndex, false)
                    }
                }
            }
        }
        decoder.stop(); decoder.release(); extractor.release()
        writeWav(raw, output, sampleRate, channels)
        raw.delete()
        return output
    }

    private fun writeWav(raw: File, wav: File, sampleRate: Int, channels: Int) {
        val dataSize = raw.length()
        RandomAccessFile(wav, "rw").use { out ->
            out.setLength(0)
            fun leShort(v: Int) { out.write(byteArrayOf((v and 0xff).toByte(), ((v shr 8) and 0xff).toByte())) }
            fun leInt(v: Long) { out.write(byteArrayOf((v and 0xff).toByte(), ((v shr 8) and 0xff).toByte(), ((v shr 16) and 0xff).toByte(), ((v shr 24) and 0xff).toByte())) }
            out.writeBytes("RIFF"); leInt(36 + dataSize); out.writeBytes("WAVEfmt "); leInt(16)
            leShort(1); leShort(channels); leInt(sampleRate.toLong()); leInt((sampleRate * channels * 2).toLong())
            leShort(channels * 2); leShort(16); out.writeBytes("data"); leInt(dataSize)
            raw.inputStream().use { it.copyTo(out) }
        }
    }
}
