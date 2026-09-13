package com.reelbot.mobile.ai

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.reelbot.mobile.data.model.FailureReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Thrown for every real, specific processing failure — never swallowed into fake output. */
class PipelineException(val reason: FailureReason, detail: String? = null) :
    Exception(detail ?: reason.userMessage)

private const val WHISPER_SAMPLE_RATE = 16000

class AudioExtractor {

    /**
     * Decodes [videoFile]'s audio track to 16kHz mono 16-bit PCM and writes it as a WAV
     * file at [outputWav]. Real MediaCodec decode + linear resample + stereo downmix —
     * not a stub.
     */
    suspend fun extract(videoFile: File, outputWav: File): File = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(videoFile.absolutePath)
        } catch (e: Exception) {
            extractor.release()
            throw PipelineException(FailureReason.UNSUPPORTED_CODEC, "Could not open video container: ${e.message}")
        }

        val (trackIndex, format) = findAudioTrack(extractor)
            ?: run { extractor.release(); throw PipelineException(FailureReason.NO_AUDIO_TRACK) }

        extractor.selectTrack(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        var srcSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var srcChannelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = try {
            MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }
        } catch (e: Exception) {
            extractor.release()
            throw PipelineException(FailureReason.UNSUPPORTED_CODEC, "No decoder available for $mime: ${e.message}")
        }

        val pcmOut = File(outputWav.parentFile, outputWav.name + ".pcm")
        try {
        pcmOut.outputStream().use { rawOut ->
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEof = false
            var sawOutputEof = false

            var lastOutput = System.currentTimeMillis()
            while (!sawOutputEof) {
                currentCoroutineContext().ensureActive()
                check(System.currentTimeMillis() - lastOutput < 60000) { "Audio decoder stalled for 60 seconds." }
                if (!sawInputEof) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEof = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val decoded = codec.outputFormat
                    srcSampleRate = decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    srcChannelCount = decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    require(!decoded.containsKey(MediaFormat.KEY_PCM_ENCODING) || decoded.getInteger(MediaFormat.KEY_PCM_ENCODING) == android.media.AudioFormat.ENCODING_PCM_16BIT) { "Decoder returned unsupported PCM encoding." }
                }
                if (outputIndex >= 0) {
                    lastOutput = System.currentTimeMillis()
                    if (bufferInfo.size > 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val chunk = ByteArray(bufferInfo.size)
                        outputBuffer.get(chunk)
                        val mono16k = toMono16kPcm(chunk, srcSampleRate, srcChannelCount)
                        rawOut.write(mono16k)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEof = true
                    }
                }
            }
        }

        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }

        writeWavHeader(pcmOut, outputWav)
        pcmOut.delete()

        if (!outputWav.exists() || outputWav.length() <= 44L) {
            throw PipelineException(FailureReason.NO_AUDIO_TRACK, "Decoded audio was empty.")
        }

        outputWav
    }

    private fun findAudioTrack(extractor: MediaExtractor): Pair<Int, MediaFormat>? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i to format
        }
        return null
    }

    /** Downmixes to mono (if needed) and linearly resamples to 16kHz — whisper.cpp's
     *  required input format. Good enough quality for speech recognition; not intended
     *  for hi-fi audio processing. */
    private fun toMono16kPcm(pcm16leBytes: ByteArray, srcRate: Int, srcChannels: Int): ByteArray {
        val buffer = ByteBuffer.wrap(pcm16leBytes).order(ByteOrder.LITTLE_ENDIAN)
        val sampleCount = pcm16leBytes.size / 2 / srcChannels
        val mono = ShortArray(sampleCount)
        for (i in 0 until sampleCount) {
            var sum = 0
            for (c in 0 until srcChannels) sum += buffer.short.toInt()
            mono[i] = (sum / srcChannels).toShort()
        }

        if (srcRate == WHISPER_SAMPLE_RATE) {
            val out = ByteBuffer.allocate(mono.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            mono.forEach { out.putShort(it) }
            return out.array()
        }

        val ratio = WHISPER_SAMPLE_RATE.toDouble() / srcRate.toDouble()
        val outLength = (mono.size * ratio).toInt()
        val resampled = ShortArray(outLength)
        for (i in 0 until outLength) {
            val srcPos = i / ratio
            val idx0 = srcPos.toInt().coerceIn(0, mono.size - 1)
            val idx1 = (idx0 + 1).coerceAtMost(mono.size - 1)
            val frac = srcPos - idx0
            resampled[i] = (mono[idx0] * (1 - frac) + mono[idx1] * frac).toInt().toShort()
        }
        val out = ByteBuffer.allocate(resampled.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        resampled.forEach { out.putShort(it) }
        return out.array()
    }

    private fun writeWavHeader(pcmFile: File, wavFile: File) {
        val pcmDataSize = pcmFile.length()
        RandomAccessFile(wavFile, "rw").use { raf ->
            raf.setLength(0)
            val byteRate = WHISPER_SAMPLE_RATE * 1 * 16 / 8
            val blockAlign = 1 * 16 / 8

            raf.writeBytes("RIFF")
            raf.write(intLe((36 + pcmDataSize).toInt()))
            raf.writeBytes("WAVE")
            raf.writeBytes("fmt ")
            raf.write(intLe(16))
            raf.write(shortLe(1)) // PCM
            raf.write(shortLe(1)) // mono
            raf.write(intLe(WHISPER_SAMPLE_RATE))
            raf.write(intLe(byteRate))
            raf.write(shortLe(blockAlign))
            raf.write(shortLe(16)) // bits per sample
            raf.writeBytes("data")
            raf.write(intLe(pcmDataSize.toInt()))

            pcmFile.inputStream().use { input ->
                val bytes = ByteArray(65536)
                while (true) { val n = input.read(bytes); if (n < 0) break; raf.write(bytes, 0, n) }
            }
        }
    }

    private fun intLe(v: Int) = byteArrayOf(
        (v and 0xff).toByte(), ((v shr 8) and 0xff).toByte(),
        ((v shr 16) and 0xff).toByte(), ((v shr 24) and 0xff).toByte()
    )

    private fun shortLe(v: Int) = byteArrayOf((v and 0xff).toByte(), ((v shr 8) and 0xff).toByte())
}
