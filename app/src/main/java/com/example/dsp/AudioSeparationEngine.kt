package com.example.dsp

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioSeparationEngine(private val context: Context) {
    companion object {
        private const val TAG = "AudioSeparationEngine"
        private const val TIMEOUT_US = 5000L
    }

    interface ProgressListener {
        fun onProgress(progress: Float)
        fun onError(error: String)
    }

    /**
     * Splits source audio into vocals and instrumentals.
     * Writes WAV files to the app's cache directory.
     * @return Triple of (vocalWavFile, instrumentalWavFile, durationMs)
     */
    fun separateAudio(
        inputPath: String,
        vocalStrength: Float = 1.0f,
        vocalCutoffLow: Double = 130.0,
        vocalCutoffHigh: Double = 3400.0,
        listener: ProgressListener
    ): Triple<File, File, Long>? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        
        val vocPcmFile = File(context.cacheDir, "voc_temp.pcm")
        val instPcmFile = File(context.cacheDir, "inst_temp.pcm")
        
        var vocPcmOut: BufferedOutputStream? = null
        var instPcmOut: BufferedOutputStream? = null

        try {
            extractor.setDataSource(inputPath)
            var trackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    extractor.selectTrack(i)
                    break
                }
            }

            if (trackIndex == -1) {
                listener.onError("No audio track found in selected file.")
                return null
            }

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                1L
            }
            val durationMs = durationUs / 1000

            // Create decoder
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            // Initialize temp streams
            vocPcmOut = BufferedOutputStream(FileOutputStream(vocPcmFile))
            instPcmOut = BufferedOutputStream(FileOutputStream(instPcmFile))

            val bufferInfo = MediaCodec.BufferInfo()
            var isInputEOS = false
            var isOutputEOS = false
            
            var sampleRate = 44100
            var channels = 2

            // Default filters (will be re-initialized when output format changes)
            var vocalFilter = BiquadFilter(BiquadFilter.Type.BANDPASS, vocalCutoffLow, sampleRate.toDouble())
            var notchFilter = BiquadFilter(BiquadFilter.Type.NOTCH, 1000.0, sampleRate.toDouble(), 0.5)

            var totalBytesWritten = 0L

            while (!isOutputEOS) {
                // 1. Supply raw buffers to decoder
                if (!isInputEOS) {
                    val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                isInputEOS = true
                            } else {
                                val sampleTime = extractor.sampleTime
                                codec.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    sampleSize,
                                    sampleTime,
                                    0
                                )
                                extractor.advance()

                                // Update progress based on extractor sample time shadow
                                if (durationUs > 0) {
                                    val progress = sampleTime.toFloat() / durationUs.toFloat()
                                    listener.onProgress(progress.coerceIn(0f, 0.95f))
                                }
                            }
                        }
                    }
                }

                // 2. Retrieve uncompressed buffers from decoder
                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when (outputBufferIndex) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFormat = codec.outputFormat
                        sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        Log.d(TAG, "Decoder output format changed: Sample Rate = $sampleRate, Channels = $channels")
                        vocalFilter.configure(vocalCutoffLow, sampleRate.toDouble())
                        notchFilter.configure(1000.0, sampleRate.toDouble(), 0.5)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // Decoder is digesting/caching buffers
                    }
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                        // Deprecated but okay
                    }
                    else -> {
                        if (outputBufferIndex >= 0) {
                            val outBuffer = codec.getOutputBuffer(outputBufferIndex)
                            if (outBuffer != null && bufferInfo.size > 0) {
                                outBuffer.position(bufferInfo.offset)
                                outBuffer.limit(bufferInfo.offset + bufferInfo.size)

                                val pcmBytes = ByteArray(bufferInfo.size)
                                outBuffer.get(pcmBytes)
                                outBuffer.clear()

                                // Convert bytes to Short samples (16-bit)
                                val shortCount = pcmBytes.size / 2
                                val samples = ShortArray(shortCount)
                                ByteBuffer.wrap(pcmBytes)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                    .asShortBuffer()
                                    .get(samples)

                                // Process with high-performance inline DSP
                                val processed = processShorts(
                                    inputShorts = samples,
                                    isStereo = channels >= 2,
                                    vocalStrength = vocalStrength,
                                    vocalFilter = vocalFilter,
                                    notchFilter = notchFilter
                                )

                                // Convert vocal and instrumental shorts back to byte arrays
                                val rawVocBytes = ByteArray(processed.first.size * 2)
                                ByteBuffer.wrap(rawVocBytes)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                    .asShortBuffer()
                                    .put(processed.first)

                                val rawInstBytes = ByteArray(processed.second.size * 2)
                                ByteBuffer.wrap(rawInstBytes)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                    .asShortBuffer()
                                    .put(processed.second)

                                // Write chunk to temporary PCM files
                                vocPcmOut.write(rawVocBytes)
                                instPcmOut.write(rawInstBytes)
                                totalBytesWritten += rawVocBytes.size
                            }

                            codec.releaseOutputBuffer(outputBufferIndex, false)

                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                isOutputEOS = true
                            }
                        }
                    }
                }
            }

            // Close temp outputs to flush buffer caches
            vocPcmOut.close()
            instPcmOut.close()
            vocPcmOut = null
            instPcmOut = null

            // 3. Prepend standard WAV header
            val outputVocalFile = File(context.filesDir, "Separated_Vocals_${System.currentTimeMillis()}.wav")
            val outputInstFile = File(context.filesDir, "Separated_Instrumental_${System.currentTimeMillis()}.wav")

            listener.onProgress(0.97f)
            convertPcmToWav(vocPcmFile, outputVocalFile, sampleRate, channels)
            
            listener.onProgress(0.99f)
            convertPcmToWav(instPcmFile, outputInstFile, sampleRate, channels)

            listener.onProgress(1.0f)

            // Delete temporary scratch PCM files
            vocPcmFile.delete()
            instPcmFile.delete()

            return Triple(outputVocalFile, outputInstFile, durationMs)

        } catch (e: Exception) {
            Log.e(TAG, "Audio separation failed", e)
            listener.onError(e.message ?: "An unknown audio parsing error occurred.")
            return null
        } finally {
            try {
                vocPcmOut?.close()
                instPcmOut?.close()
            } catch (ignored: Exception) {}
            try {
                codec?.stop()
                codec?.release()
            } catch (ignored: Exception) {}
            try {
                extractor.release()
            } catch (ignored: Exception) {}
            // Cleanup temp files if exception occurred
            if (vocPcmFile.exists()) vocPcmFile.delete()
            if (instPcmFile.exists()) instPcmFile.delete()
        }
    }

    private fun processShorts(
        inputShorts: ShortArray,
        isStereo: Boolean,
        vocalStrength: Float,
        vocalFilter: BiquadFilter,
        notchFilter: BiquadFilter
    ): Pair<ShortArray, ShortArray> {
        val size = inputShorts.size
        val vocalShorts = ShortArray(size)
        val instShorts = ShortArray(size)

        if (isStereo) {
            var i = 0
            while (i < size - 1) {
                val L = inputShorts[i] / 32768f
                val R = inputShorts[i + 1] / 32768f

                // Isolate vocals: (L + R) / 2 to extract the center lane, then bandpass filter
                val center = (L + R) / 2f
                val filteredVocal = vocalFilter.process(center)

                val vocalShortVal = (filteredVocal * 32768f).coerceIn(-32768f, 32767f).toInt().toShort()
                vocalShorts[i] = vocalShortVal         // left
                vocalShorts[i + 1] = vocalShortVal     // right

                // Remove vocals (Instrumentals) via side expansion: L_inst = (L - alpha * R) / (1 + alpha)
                val L_inst = (L - vocalStrength * R) / (1f + vocalStrength)
                val R_inst = (R - vocalStrength * L) / (1f + vocalStrength)

                instShorts[i] = (L_inst * 32768f).coerceIn(-32768f, 32767f).toInt().toShort()
                instShorts[i + 1] = (R_inst * 32768f).coerceIn(-32768f, 32767f).toInt().toShort()

                i += 2
            }
        } else {
            // Fallback for Mono files
            for (i in 0 until size) {
                val s = inputShorts[i] / 32768f

                val v = vocalFilter.process(s)
                vocalShorts[i] = (v * 32768f).coerceIn(-32768f, 32767f).toInt().toShort()

                val inst = notchFilter.process(s)
                instShorts[i] = (inst * 32768f).coerceIn(-32768f, 32767f).toInt().toShort()
            }
        }

        return Pair(vocalShorts, instShorts)
    }

    private fun convertPcmToWav(pcmFile: File, wavFile: File, sampleRate: Int, channels: Int) {
        val pcmSize = pcmFile.length()
        val totalAudioCheckData = pcmSize
        val totalHeaderAndData = pcmSize + 36

        val byteRate = (sampleRate * channels * 16 / 8).toLong()

        FileInputStream(pcmFile).use { input ->
            FileOutputStream(wavFile).use { output ->
                writeWavHeader(output, totalAudioCheckData, totalHeaderAndData, sampleRate.toLong(), channels, byteRate)
                val buffer = ByteArray(16384)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
        }
    }

    private fun writeWavHeader(
        out: FileOutputStream,
        totalAudioLen: Long,
        totalDataLen: Long,
        longSampleRate: Long,
        channels: Int,
        byteRate: Long
    ) {
        val header = ByteArray(44)
        header[0] = 'R'.toByte() // RIFF
        header[1] = 'I'.toByte()
        header[2] = 'F'.toByte()
        header[3] = 'F'.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.toByte() // WAVE
        header[9] = 'A'.toByte()
        header[10] = 'V'.toByte()
        header[11] = 'E'.toByte()
        header[12] = 'f'.toByte() // 'fmt '
        header[13] = 'm'.toByte()
        header[14] = 't'.toByte()
        header[15] = ' '.toByte()
        header[16] = 16 // Subchunk1Size
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // AudioFormat: 1 = PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = (longSampleRate shr 8 and 0xff).toByte()
        header[26] = (longSampleRate shr 16 and 0xff).toByte()
        header[27] = (longSampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * 16 / 8).toByte() // BlockAlign
        header[33] = 0
        header[34] = 16 // BitsPerSample
        header[35] = 0
        header[36] = 'd'.toByte() // data
        header[37] = 'a'.toByte()
        header[38] = 't'.toByte()
        header[39] = 'a'.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()
        out.write(header, 0, 44)
    }
}
