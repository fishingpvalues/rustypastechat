package com.rustypastechat.ui.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.rustypastechat.data.model.VoiceQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0L

    val isRecording: Boolean get() = recorder != null

    fun start(quality: VoiceQuality = VoiceQuality.STANDARD): Result<File> = runCatching {
        if (recorder != null) cancel() // guard against orphaning a still-live recorder
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(quality.bitRate)
        r.setAudioSamplingRate(quality.sampleRate)
        r.setOutputFile(file.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        outputFile = file
        startTimeMs = System.currentTimeMillis()
        file
    }.onFailure {
        runCatching { recorder?.release() }
        recorder = null
        outputFile = null
    }

    /** Instantaneous input level (0..32767), for a live recording waveform. 0 if not recording. */
    fun currentAmplitude(): Int = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    /** Stops recording. Returns the recorded file + duration in ms, or null if too short/failed
     *  (in which case the partial file is deleted). */
    fun stop(): Pair<File, Long>? {
        val r = recorder ?: return null
        val file = outputFile
        val durationMs = System.currentTimeMillis() - startTimeMs
        recorder = null
        outputFile = null
        val stopped = runCatching {
            r.stop()
            r.release()
        }.isSuccess
        return if (stopped && file != null && durationMs > 300) {
            file to durationMs
        } else {
            file?.delete()
            null
        }
    }

    /** Aborts recording without producing a message (e.g. slide-to-cancel). */
    fun cancel() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}
