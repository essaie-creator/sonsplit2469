package com.example.dsp

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaCodecInfo
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

    data class SeparationResult(
        val vocalPath: String,
        val instrumentalPath: String,
        val bassPath: String?,
        val melodyPath: String?,
        val durationMs: Long
    )

    class ProcessedChunk(
        val vocals: ShortArray,
        val instrumental: ShortArray,
        val bass: ShortArray?,
        val melody: ShortArray?
    )

    /**
     * Splits source audio into vocals, instrumentals, and optionally bass and melody tracks.
     * Writes WAV or M4A files to the app's files directory.
     * @return SeparationResult containing output paths and duration
     */
    fun separateAudio(
        inputPath: String,
        vocalStrength: Float = 1.0f,
        vocalCutoffLow: Double = 130.0,
        vocalCutoffHigh: Double = 3400.0,
        outputFormat: String = "WAV",
        splitBass: Boolean = true,
        splitMelody: Boolean = true,
        listener: ProgressListener
    ): SeparationResult? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        
        val vocPcmFile = File(context.cacheDir, "voc_temp.pcm")
        val instPcmFile = File(context.cacheDir, "inst_temp.pcm")
        val bassPcmFile = File(context.cacheDir, "bass_temp.pcm")
        val melodyPcmFile = File(context.cacheDir, "melody_temp.pcm")
        
        var vocPcmOut: BufferedOutputStream? = null
        var instPcmOut: BufferedOutputStream? = null
        var bassPcmOut: BufferedOutputStream? = null
        var melodyPcmOut: BufferedOutputStream? = null

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
            if (splitBass) {
                bassPcmOut = BufferedOutputStream(FileOutputStream(bassPcmFile))
            }
            if (splitMelody) {
                melodyPcmOut = BufferedOutputStream(FileOutputStream(melodyPcmFile))
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var isInputEOS = false
            var isOutputEOS = false
            
            var sampleRate = 44100
            var channels = 2

            // Initialize DSP filters
            var vocFilterL_hp = BiquadFilter(BiquadFilter.Type.HIGHPASS, 120.0, sampleRate.toDouble())
            var vocFilterL_lp = BiquadFilter(BiquadFilter.Type.LOWPASS, 3800.0, sampleRate.toDouble())
            var vocFilterR_hp = BiquadFilter(BiquadFilter.Type.HIGHPASS, 120.0, sampleRate.toDouble())
            var vocFilterR_lp = BiquadFilter(BiquadFilter.Type.LOWPASS, 3800.0, sampleRate.toDouble())
            
            var bassFilterL = BiquadFilter(BiquadFilter.Type.LOWPASS, 160.0, sampleRate.toDouble())
            var bassFilterR = BiquadFilter(BiquadFilter.Type.LOWPASS, 160.0, sampleRate.toDouble())
            var melodyFilterL = BiquadFilter(BiquadFilter.Type.HIGHPASS, 4500.0, sampleRate.toDouble())
            var melodyFilterR = BiquadFilter(BiquadFilter.Type.HIGHPASS, 4500.0, sampleRate.toDouble())

            var totalBytesWritten = 0L

            while (!isOutputEOS) {
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

                                if (durationUs > 0) {
                                    val progress = sampleTime.toFloat() / durationUs.toFloat()
                                    listener.onProgress(progress.coerceIn(0f, 0.95f))
                                }
                            }
                        }
                    }
                }

                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when (outputBufferIndex) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFormat = codec.outputFormat
                        sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        Log.d(TAG, "Decoder output format changed: Sample Rate = $sampleRate, Channels = $channels")
                        vocFilterL_hp.configure(120.0, sampleRate.toDouble())
                        vocFilterL_lp.configure(3800.0, sampleRate.toDouble())
                        vocFilterR_hp.configure(120.0, sampleRate.toDouble())
                        vocFilterR_lp.configure(3800.0, sampleRate.toDouble())
                        bassFilterL.configure(160.0, sampleRate.toDouble())
                        bassFilterR.configure(160.0, sampleRate.toDouble())
                        melodyFilterL.configure(4500.0, sampleRate.toDouble())
                        melodyFilterR.configure(4500.0, sampleRate.toDouble())
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // Decoder is digesting/caching buffers
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

                                val shortCount = pcmBytes.size / 2
                                val samples = ShortArray(shortCount)
                                ByteBuffer.wrap(pcmBytes)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                    .asShortBuffer()
                                    .get(samples)

                                val processed = processShorts(
                                    inputShorts = samples,
                                    isStereo = channels >= 2,
                                    vocalStrength = vocalStrength,
                                    vocFilterL_hp = vocFilterL_hp,
                                    vocFilterL_lp = vocFilterL_lp,
                                    vocFilterR_hp = vocFilterR_hp,
                                    vocFilterR_lp = vocFilterR_lp,
                                    splitBass = splitBass,
                                    bassFilterL = bassFilterL,
                                    bassFilterR = bassFilterR,
                                    splitMelody = splitMelody,
                                    melodyFilterL = melodyFilterL,
                                    melodyFilterR = melodyFilterR
                                )

                                // Convert and write vocals
                                val rawVocBytes = ByteArray(processed.vocals.size * 2)
                                ByteBuffer.wrap(rawVocBytes)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                    .asShortBuffer()
                                    .put(processed.vocals)
                                vocPcmOut.write(rawVocBytes)

                                // Convert and write instrumentals
                                val rawInstBytes = ByteArray(processed.instrumental.size * 2)
                                ByteBuffer.wrap(rawInstBytes)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                    .asShortBuffer()
                                    .put(processed.instrumental)
                                instPcmOut.write(rawInstBytes)

                                // Convert and write bass
                                if (splitBass && processed.bass != null && bassPcmOut != null) {
                                    val rawBassBytes = ByteArray(processed.bass.size * 2)
                                    ByteBuffer.wrap(rawBassBytes)
                                        .order(ByteOrder.LITTLE_ENDIAN)
                                        .asShortBuffer()
                                        .put(processed.bass)
                                    bassPcmOut.write(rawBassBytes)
                                }

                                // Convert and write melody
                                if (splitMelody && processed.melody != null && melodyPcmOut != null) {
                                    val rawMelBytes = ByteArray(processed.melody.size * 2)
                                    ByteBuffer.wrap(rawMelBytes)
                                        .order(ByteOrder.LITTLE_ENDIAN)
                                        .asShortBuffer()
                                        .put(processed.melody)
                                    melodyPcmOut.write(rawMelBytes)
                                }

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
            bassPcmOut?.close()
            melodyPcmOut?.close()
            
            vocPcmOut = null
            instPcmOut = null
            bassPcmOut = null
            melodyPcmOut = null

            // Determine output files format & extensions
            val formattingM4A = outputFormat.uppercase() == "M4A"
            val ext = if (formattingM4A) "m4a" else "wav"
            
            val outputVocalFile = File(context.filesDir, "Separated_Vocals_${System.currentTimeMillis()}.$ext")
            val outputInstFile = File(context.filesDir, "Separated_Instrumental_${System.currentTimeMillis()}.$ext")
            val outputBassFile = if (splitBass) File(context.filesDir, "Separated_Bass_${System.currentTimeMillis()}.$ext") else null
            val outputMelodyFile = if (splitMelody) File(context.filesDir, "Separated_Melody_${System.currentTimeMillis()}.$ext") else null

            listener.onProgress(0.96f)
            if (formattingM4A) {
                convertPcmToM4a(vocPcmFile, outputVocalFile, sampleRate, channels)
                listener.onProgress(0.97f)
                convertPcmToM4a(instPcmFile, outputInstFile, sampleRate, channels)
                
                if (splitBass && outputBassFile != null && bassPcmFile.exists()) {
                    listener.onProgress(0.98f)
                    convertPcmToM4a(bassPcmFile, outputBassFile, sampleRate, channels)
                }
                if (splitMelody && outputMelodyFile != null && melodyPcmFile.exists()) {
                    listener.onProgress(0.99f)
                    convertPcmToM4a(melodyPcmFile, outputMelodyFile, sampleRate, channels)
                }
            } else {
                convertPcmToWav(vocPcmFile, outputVocalFile, sampleRate, channels)
                listener.onProgress(0.97f)
                convertPcmToWav(instPcmFile, outputInstFile, sampleRate, channels)
                
                if (splitBass && outputBassFile != null && bassPcmFile.exists()) {
                    listener.onProgress(0.98f)
                    convertPcmToWav(bassPcmFile, outputBassFile, sampleRate, channels)
                }
                if (splitMelody && outputMelodyFile != null && melodyPcmFile.exists()) {
                    listener.onProgress(0.99f)
                    convertPcmToWav(melodyPcmFile, outputMelodyFile, sampleRate, channels)
                }
            }

            listener.onProgress(1.0f)

            // Delete temporary scratch PCM files
            if (vocPcmFile.exists()) vocPcmFile.delete()
            if (instPcmFile.exists()) instPcmFile.delete()
            if (bassPcmFile.exists()) bassPcmFile.delete()
            if (melodyPcmFile.exists()) melodyPcmFile.delete()

            return SeparationResult(
                vocalPath = outputVocalFile.absolutePath,
                instrumentalPath = outputInstFile.absolutePath,
                bassPath = outputBassFile?.absolutePath,
                melodyPath = outputMelodyFile?.absolutePath,
                durationMs = durationMs
            )

        } catch (e: Exception) {
            Log.e(TAG, "Audio separation failed", e)
            listener.onError(e.message ?: "An unknown audio parsing error occurred.")
            return null
        } finally {
            try {
                vocPcmOut?.close()
                instPcmOut?.close()
                bassPcmOut?.close()
                melodyPcmOut?.close()
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
            if (bassPcmFile.exists()) bassPcmFile.delete()
            if (melodyPcmFile.exists()) melodyPcmFile.delete()
        }
    }

    private fun processShorts(
        inputShorts: ShortArray,
        isStereo: Boolean,
        vocalStrength: Float,
        vocFilterL_hp: BiquadFilter,
        vocFilterL_lp: BiquadFilter,
        vocFilterR_hp: BiquadFilter,
        vocFilterR_lp: BiquadFilter,
        splitBass: Boolean,
        bassFilterL: BiquadFilter,
        bassFilterR: BiquadFilter,
        splitMelody: Boolean,
        melodyFilterL: BiquadFilter,
        melodyFilterR: BiquadFilter
    ): ProcessedChunk {
        val size = inputShorts.size
        val vocalShorts = ShortArray(size)
        val instShorts = ShortArray(size)
        val bassShorts = if (splitBass) ShortArray(size) else null
        val melodyShorts = if (splitMelody) ShortArray(size) else null

        if (isStereo) {
            var i = 0
            while (i < size - 1) {
                val L = inputShorts[i] / 32768f
                val R = inputShorts[i + 1] / 32768f

                // Isolate mid frequencies (speech/vocal range) for each channel
                val L_mid = vocFilterL_lp.process(vocFilterL_hp.process(L))
                val R_mid = vocFilterR_lp.process(vocFilterR_hp.process(R))

                // Vocal is centered, so extract the center (in-phase) part of the vocal band
                val vocal_center = (L_mid + R_mid) / 2f

                // Isolate output vocals
                val vocalOut = vocal_center * vocalStrength
                val vocalShortVal = (vocalOut * 32768f).coerceIn(-32768f, 32767f).toInt().toShort()
                vocalShorts[i] = vocalShortVal
                vocalShorts[i + 1] = vocalShortVal

                // The instrumental track is the original signal minus the extracted vocal center
                val L_inst = L - vocalStrength * vocal_center
                val R_inst = R - vocalStrength * vocal_center

                instShorts[i] = (L_inst * 32768f).coerceIn(-32768f, 32767f).toInt().toShort()
                instShorts[i + 1] = (R_inst * 32768f).coerceIn(-32768f, 32767f).toInt().toShort()

                // Process Bass (lowpass filtered instrumental)
                if (splitBass && bassShorts != null) {
                    val L_bass = bassFilterL.process(L_inst)
                    val R_bass = bassFilterR.process(R_inst)
                    bassShorts[i] = (L_bass * 32768f).coerceIn(-32768f, 32767f).toInt().toShort()
                    bassShorts[i + 1] = (R_bass * 32768f).coerceIn(-32768f, 32767f).toInt().toShort()
                }

                // Process Melody (highpass filtered instrumental)
                if (splitMelody && melodyShorts != null) {
                    val L_mel = melodyFilterL.process(L_inst)
                    val R_mel = melodyFilterR.process(R_inst)
                    melodyShorts[i] = (L_mel * 32768f).coerceIn(-32768f, 32767f).toInt().toShort()
                    melodyShorts[i + 1] = (R_mel * 32768f).coerceIn(-32768f, 32767f).toInt().toShort()
                }

                i += 2
            }
        } else {
            // Fallback for Mono files
            for (i in 0 until size) {
                val s = inputShorts[i] / 32768f

                val v = vocFilterL_lp.process(vocFilterL_hp.process(s))
                val vocalOut = v * vocalStrength
                vocalShorts[i] = (vocalOut * 32768f).coerceIn(-32768f, 32767f).toInt().toShort()

                val inst = s - vocalStrength * v
                instShorts[i] = (inst * 32768f).coerceIn(-32768f, 32767f).toInt().toShort()

                if (splitBass && bassShorts != null) {
                    val b = bassFilterL.process(inst)
                    bassShorts[i] = (b * 32768f).coerceIn(-32768f, 32767f).toInt().toShort()
                }

                if (splitMelody && melodyShorts != null) {
                    val m = melodyFilterL.process(inst)
                    melodyShorts[i] = (m * 32768f).coerceIn(-32768f, 32767f).toInt().toShort()
                }
            }
        }

        return ProcessedChunk(vocalShorts, instShorts, bassShorts, melodyShorts)
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

    private fun convertPcmToM4a(
        pcmFile: File,
        m4aFile: File,
        sampleRate: Int,
        channels: Int,
        bitrate: Int = 128000
    ) {
        if (pcmFile.length() == 0L) {
            Log.w(TAG, "PCM file is empty, skipping M4A conversion: ${pcmFile.absolutePath}")
            return
        }

        val safeSampleRate = if (sampleRate > 0) sampleRate else 44100
        val safeChannels = if (channels in 1..8) channels else 2
        val mime = "audio/mp4a-latm"

        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var fis: FileInputStream? = null
        var isMuxerStarted = false
        var trackIndex = -1
        var samplesWritten = 0

        try {
            val format = MediaFormat.createAudioFormat(mime, safeSampleRate, safeChannels)
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)

            encoder = MediaCodec.createEncoderByType(mime)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(m4aFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            fis = FileInputStream(pcmFile)

            val bufferInfo = MediaCodec.BufferInfo()
            val inputBuffer = ByteArray(16384)
            var isInputEOS = false
            var isOutputEOS = false
            
            val bytesPerSample = 2 // 16-bit PCM
            val bytesPerSecond = safeSampleRate * safeChannels * bytesPerSample
            var totalBytesProcessed = 0L

            while (!isOutputEOS) {
                if (!isInputEOS) {
                    val inputBufferIndex = encoder.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val encoderInputBuffer = encoder.getInputBuffer(inputBufferIndex)
                        if (encoderInputBuffer != null) {
                            encoderInputBuffer.clear()
                            val bytesRead = fis.read(inputBuffer)
                            if (bytesRead < 0) {
                                val presentationTimeUs = if (bytesPerSecond > 0) {
                                    (totalBytesProcessed * 1000000L) / bytesPerSecond
                                } else {
                                    0L
                                }
                                encoder.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    0,
                                    presentationTimeUs,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                isInputEOS = true
                            } else {
                                encoderInputBuffer.put(inputBuffer, 0, bytesRead)
                                val presentationTimeUs = if (bytesPerSecond > 0) {
                                    (totalBytesProcessed * 1000000L) / bytesPerSecond
                                } else {
                                    0L
                                }
                                encoder.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    bytesRead,
                                    presentationTimeUs,
                                    0
                                )
                                totalBytesProcessed += bytesRead
                            }
                        }
                    }
                }

                val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!isMuxerStarted) {
                        val newFormat = encoder.outputFormat
                        trackIndex = muxer.addTrack(newFormat)
                        muxer.start()
                        isMuxerStarted = true
                    }
                } else if (outputBufferIndex >= 0) {
                    val encodedData = encoder.getOutputBuffer(outputBufferIndex)
                    if (encodedData != null) {
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }

                        if (bufferInfo.size > 0 && isMuxerStarted && trackIndex >= 0) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                            samplesWritten++
                        }
                    }

                    encoder.releaseOutputBuffer(outputBufferIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEOS = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in convertPcmToM4a for file: ${m4aFile.name}", e)
            throw e
        } finally {
            try {
                fis?.close()
            } catch (ignored: Exception) {}
            try {
                encoder?.stop()
            } catch (ignored: Exception) {}
            try {
                encoder?.release()
            } catch (ignored: Exception) {}
            try {
                if (isMuxerStarted && samplesWritten > 0) {
                    muxer?.stop()
                }
            } catch (ignored: Exception) {}
            try {
                muxer?.release()
            } catch (ignored: Exception) {}
        }
    }
}
