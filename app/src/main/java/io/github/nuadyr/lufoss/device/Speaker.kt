package io.github.nuadyr.lufoss.device

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Spoken turn-by-turn directions through the system text-to-speech engine. */
class Speaker(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) stop()
        }

    /** Starts the speech engine early so the first direction is not delayed. */
    fun prime() {
        if (tts != null) return
        tts = TextToSpeech(appContext) { status ->
            val engine = tts
            if (status == TextToSpeech.SUCCESS && engine != null) {
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                val result = engine.setLanguage(Locale.UK)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale.getDefault())
                }
                ready = true
                pending?.let { speakNow(engine, it) }
                pending = null
            }
        }
    }

    fun speak(text: String) {
        if (!enabled) return
        prime()
        val engine = tts
        if (ready && engine != null) speakNow(engine, text) else pending = text
    }

    fun stop() {
        pending = null
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun speakNow(engine: TextToSpeech, text: String) {
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lufoss-direction")
    }
}
