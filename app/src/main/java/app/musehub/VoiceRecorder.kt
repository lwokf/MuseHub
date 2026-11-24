package app.musehub

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Voice recording, V2. Records an audio file and sends it through the Hub
 * file API. There is no speech-to-text in the Hub, so no transcription text
 * is ever shown. Tap to start / tap to stop; hold-to-talk is not used.
 */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    var isRecording: Boolean = false
        private set

    fun amplitude(): Int = try { recorder?.maxAmplitude ?: 0 } catch (e: Exception) { 0 }

    fun start(): Boolean {
        return try {
            val f = File(context.cacheDir, voiceFilename())
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = f
            isRecording = true
            true
        } catch (e: Exception) {
            stopQuietly()
            false
        }
    }

    /** Stop and keep the file. Returns the file, or null on failure. */
    fun stop(): File? {
        return try {
            recorder?.stop()
            isRecording = false
            stopQuietly()
            file?.takeIf { it.exists() && it.length() > 0 }
        } catch (e: Exception) {
            isRecording = false
            stopQuietly()
            null
        }
    }

    fun cancel() {
        isRecording = false
        stopQuietly()
        file?.delete()
        file = null
    }

    private fun stopQuietly() {
        try { recorder?.release() } catch (e: Exception) { }
        recorder = null
    }
}
