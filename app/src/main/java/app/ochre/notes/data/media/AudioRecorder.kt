package app.ochre.notes.data.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException

class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var target: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    fun start(): File {
        check(recorder == null) { "Already recording" }
        val file = File.createTempFile("voice-", ".m4a", context.cacheDir)
        val media = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            media.setAudioSource(MediaRecorder.AudioSource.MIC)
            media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            media.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            media.setAudioEncodingBitRate(96_000)
            media.setAudioSamplingRate(44_100)
            media.setOutputFile(file.absolutePath)
            media.prepare()
            media.start()
        } catch (error: Throwable) {
            runCatching { media.release() }
            file.delete()
            throw IOException("Could not start recording", error)
        }
        recorder = media
        target = file
        startedAt = System.currentTimeMillis()
        return file
    }

    fun maxAmplitude(): Int = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    fun elapsedMillis(): Long = if (recorder == null) 0L else System.currentTimeMillis() - startedAt

    fun stop(): Recording? {
        val media = recorder ?: return null
        val file = target
        val duration = elapsedMillis()
        recorder = null
        target = null
        startedAt = 0L
        val ok = runCatching {
            media.stop()
            media.release()
        }.isSuccess
        if (!ok || file == null || !file.exists() || file.length() < 512L) {
            file?.delete()
            return null
        }
        return Recording(file = file, durationMs = duration.coerceAtLeast(500L))
    }

    fun cancel() {
        val media = recorder
        recorder = null
        target?.delete()
        target = null
        startedAt = 0L
        if (media != null) {
            runCatching { media.stop() }
            runCatching { media.release() }
        }
    }

    data class Recording(val file: File, val durationMs: Long)
}