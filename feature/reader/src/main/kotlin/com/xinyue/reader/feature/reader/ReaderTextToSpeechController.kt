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
    private var engineInitialized = false
    private var closed = false
    private var initializationFailure: String? = null
    private var currentSpeech: SpeechRequest? = null

    init {
        try {
            engine = TextToSpeech(context.applicationContext) { status ->
                // Always defer initialization until the constructor has assigned the engine.
                mainHandler.post { initialize(status) }
            }
        } catch (_: RuntimeException) {
            initializationFailure = ENGINE_UNAVAILABLE
        }
    }

    fun speak(text: String, onComplete: () -> Unit, onFailure: (String) -> Unit) {
        onMainThread {
            val request = SpeechRequest(UUID.randomUUID().toString(), text, onComplete, onFailure)
            currentSpeech = request
            when {
                closed -> finish(request.id, ENGINE_UNAVAILABLE)
                initializationFailure != null -> {
                    if (engineInitialized) initialize(TextToSpeech.SUCCESS)
                    else finish(request.id, initializationFailure)
                }
                ready -> speakReady(request)
                // While the engine initializes, only the newest request remains pending.
                else -> Unit
            }
        }
    }

    fun stop() {
        onMainThread {
            currentSpeech = null
            try {
                engine?.stop()
            } catch (_: RuntimeException) {
                // The request is already invalidated even if the system engine has disconnected.
            }
        }
    }

    fun shutdown() {
        onMainThread {
            stop()
            closed = true
            ready = false
            try {
                engine?.shutdown()
            } catch (_: RuntimeException) {
                // A disconnected engine must not prevent disposal of the reading screen.
            }
            engine = null
        }
    }

    private fun initialize(status: Int) {
        if (closed) return
        val speechEngine = engine
        engineInitialized = status == TextToSpeech.SUCCESS && speechEngine != null
        initializationFailure = try {
            when {
                status != TextToSpeech.SUCCESS || speechEngine == null -> ENGINE_UNAVAILABLE
                else -> {
                    val languageResult = speechEngine.setLanguage(Locale.SIMPLIFIED_CHINESE)
                    when {
                        languageResult == TextToSpeech.LANG_MISSING_DATA -> CHINESE_DATA_MISSING
                        languageResult == TextToSpeech.LANG_NOT_SUPPORTED -> CHINESE_NOT_SUPPORTED
                        languageResult < 0 -> CHINESE_INITIALIZATION_FAILED
                        speechEngine.voice?.isNetworkConnectionRequired == true -> {
                            val offlineVoice = speechEngine.voices?.firstOrNull { voice ->
                                voice.locale.language == Locale.CHINESE.language &&
                                    !voice.isNetworkConnectionRequired &&
                                    TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features
                            }
                            if (offlineVoice == null ||
                                speechEngine.setVoice(offlineVoice) != TextToSpeech.SUCCESS
                            ) CHINESE_OFFLINE_MISSING else null
                        }
                        else -> null
                    }
                }
            }
        } catch (_: RuntimeException) {
            ENGINE_UNAVAILABLE
        }
        ready = initializationFailure == null
        if (ready) {
            try {
                if (speechEngine?.setOnUtteranceProgressListener(listener) != TextToSpeech.SUCCESS) {
                    ready = false
                    initializationFailure = ENGINE_UNAVAILABLE
                }
            } catch (_: RuntimeException) {
                ready = false
                initializationFailure = ENGINE_UNAVAILABLE
            }
        }
        currentSpeech?.let { request ->
            if (ready) speakReady(request) else finish(request.id, initializationFailure)
        }
    }

    private fun speakReady(request: SpeechRequest) {
        val result = try {
            engine?.speak(request.text, TextToSpeech.QUEUE_FLUSH, Bundle(), request.id)
        } catch (_: RuntimeException) {
            TextToSpeech.ERROR
        }
        if (result != TextToSpeech.SUCCESS) finish(request.id, SPEECH_FAILED)
    }

    // All state and callbacks are handled on the main thread, including ID validation.
    // A callback queued before stop() cannot complete a replacement speech request.
    private fun finish(utteranceId: String?, failure: String? = null) {
        val request = currentSpeech?.takeIf { it.id == utteranceId } ?: return
        currentSpeech = null
        if (failure == null) {
            request.onComplete()
        } else {
            stop()
            request.onFailure(failure)
        }
    }

    private fun onMainThread(action: () -> Unit) {
        if (Looper.myLooper() == mainHandler.looper) action() else mainHandler.post(action)
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            mainHandler.post { finish(utteranceId) }
        }

        @Deprecated("Deprecated by Android")
        override fun onError(utteranceId: String?) {
            mainHandler.post { finish(utteranceId, SPEECH_FAILED) }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            val message = if (errorCode == TextToSpeech.ERROR_NOT_INSTALLED_YET) {
                CHINESE_DATA_MISSING
            } else SPEECH_FAILED
            mainHandler.post { finish(utteranceId, message) }
        }
    }

    private data class SpeechRequest(
        val id: String,
        val text: String,
        val onComplete: () -> Unit,
        val onFailure: (String) -> Unit,
    )

    private companion object {
        const val ENGINE_UNAVAILABLE = "朗读引擎不可用，请检查系统文字转语音设置。"
        const val CHINESE_DATA_MISSING = "缺少中文朗读语音，请先在系统文字转语音设置中安装中文语音。"
        const val CHINESE_NOT_SUPPORTED = "当前朗读引擎不支持中文，请在系统文字转语音设置中选择支持中文的引擎。"
        const val CHINESE_INITIALIZATION_FAILED = "中文朗读初始化失败，请检查系统文字转语音设置。"
        const val CHINESE_OFFLINE_MISSING = "当前中文语音需要联网，请先在系统文字转语音设置中安装离线中文语音。"
        const val SPEECH_FAILED = "朗读未能完成，请检查系统文字转语音设置后重试。"
    }
}
