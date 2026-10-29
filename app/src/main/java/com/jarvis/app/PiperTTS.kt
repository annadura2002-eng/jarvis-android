package com.jarvis.app

import android.content.Context
import android.media.MediaPlayer
import com.k2fsa.sherpa.onnx.*
import java.io.File

class PiperTTS(private val context: Context) {
    private var tts: OfflineTts? = null
    private var mediaPlayer: MediaPlayer? = null

    fun init(modelPath: String) {
        try {
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = "$modelPath/model.onnx",
                        tokens = "$modelPath/tokens.txt",
                        lexicon = "$modelPath/lexicon.txt",
                        dataDir = ""
                    ),
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                )
            )
            tts = OfflineTts(config = config)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun speak(text: String) {
        tts?.let {
            val audio = it.generate(text, 0, 1.0f)
            if (audio.samples.isNotEmpty()) {
                // Sherpa возвращает FloatArray (звук), сохраняем во временный файл
                val sampleRate = audio.sampleRate
                val file = File(context.cacheDir, "piper_audio.wav")
                writeWavFile(file, audio.samples, sampleRate)
                
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                    start()
                }
            }
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {}
    }

    private fun writeWavFile(file: File, samples: FloatArray, sampleRate: Int) {
        val header = ByteArray(44)
        val dataSize = samples.size * 2
        val totalSize = dataSize + 36
        
        // Заголовок WAV (упрощенно)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalSize and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        
        file.outputStream().use { out ->
            out.write(header)
            val buffer = ByteArray(dataSize)
            var i = 0
            for (sample in samples) {
                val s = (sample * 32767).toInt()
                buffer[i++] = (s and 0xff).toByte()
                buffer[i++] = ((s shr 8) and 0xff).toByte()
            }
            out.write(buffer)
        }
    }
}
