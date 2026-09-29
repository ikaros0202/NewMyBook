package com.xinyue.reader.feature.reader

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

class ReaderTextToSpeechController(context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var ready = false
    private var pendingSpeech: PendingSpeech? = null
    private var completion: (() -> Unit)? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                val languageResult = engine?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                    languageResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    engine?.language = Locale.getDefault()
                }
                engine?.setOnUtteranceProgressListener(listener)
                pendingSpeech?.also { pending ->
                    pendingSpeech = null
                    speak(pending.text, pending.onComplete)
                }
            }
        }
    }

    fun speak(text: String, onComplete: () -> Unit) {
        if (!ready) {
            pendingSpeech = PendingSpeech(text, onComplete)
            return
        }
        completion = onComplete
        engine?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            UUID.randomUUID().toString(),
        )
    }

    fun stop() {
        pendingSpeech = null
        completion = null
        engine?.stop()
    }

    fun shutdown() {
        stop()
        engine?.shutdown()
        engine = null
        ready = false
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            val callback = completion
            completion = null
            if (callback != null) mainHandler.post(callback)
        }

        @Deprecated("Deprecated by Android")
        override fun onError(utteranceId: String?) {
            completion = null
        }
    }

    private data class PendingSpeech(
        val text: String,
        val onComplete: () -> Unit,
    )
}
