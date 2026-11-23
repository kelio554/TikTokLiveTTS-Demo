package net.mkhalil.taktik.data.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Uses the device's installed Android TTS engine; no cloud voice API is called. */
class AndroidTtsReader(context: Context) : TextToSpeech.OnInitListener {
    private val main = Handler(Looper.getMainLooper())
    private val ready = AtomicBoolean(false)
    @Volatile private var engine: TextToSpeech? = null

    init {
        main.post { engine = TextToSpeech(context.applicationContext, this) }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val tts = engine ?: return
            val locale = Locale.getDefault()
            var result = tts.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                result = tts.setLanguage(Locale.US)
            }
            ready.set(result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED)
            tts.setSpeechRate(1.0f)
        }
    }

    fun speakComment(text: String) {
        val clean = text.trim().take(500)
        if (clean.isEmpty() || !ready.get()) return
        main.post {
            engine?.speak(clean, TextToSpeech.QUEUE_ADD, null, "comment-${System.nanoTime()}")
        }
    }

    fun stop() {
        main.post { engine?.stop() }
    }

    fun shutdown() {
        main.post {
            ready.set(false)
            engine?.stop()
            engine?.shutdown()
            engine = null
        }
    }
}
