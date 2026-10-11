package com.xinyue.reader.feature.reader

import android.os.Bundle
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowTextToSpeech

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [ControlledTextToSpeechShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class ReaderTextToSpeechControllerTest {
    @Before
    fun prepareChineseLanguage() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.SIMPLIFIED_CHINESE)
    }

    @Test
    fun `engine language and synthesis failures reach the caller without completing speech`() {
        for (case in listOf("engine", "language", "network")) {
            val (controller, engine) = newController()
            val failures = mutableListOf<String>()
            var completed = 0
            engine.languageResult = if (case == "language") TextToSpeech.LANG_MISSING_DATA else 0
            if (case == "network") engine.initialVoice = chineseVoice("network", network = true)
            controller.speak("中文测试", { completed++ }) {
                assertThat(Looper.myLooper()).isSameInstanceAs(Looper.getMainLooper())
                failures += it
            }
            engine.onInitListener.onInit(if (case == "engine") TextToSpeech.ERROR else TextToSpeech.SUCCESS)
            idleMain()
            assertThat(failures).hasSize(1)
            assertThat(failures.single()).contains(if (case == "engine") "引擎不可用" else "中文")
            assertThat(completed).isEqualTo(0)
            assertThat(engine.utteranceIds).isEmpty()
            if (case == "language") {
                engine.languageResult = TextToSpeech.LANG_COUNTRY_AVAILABLE
                controller.speak("安装中文语音后重试", { completed++ }, failures::add)
                assertThat(engine.utteranceIds).hasSize(1)
                engine.utteranceProgressListener.onDone(engine.utteranceIds.single())
                idleMain()
                assertThat(completed).isEqualTo(1)
                assertThat(failures).hasSize(1)
            }
            controller.shutdown()
        }

        val (controller, engine) = newController()
        val offlineVoice = chineseVoice("offline", network = false)
        ShadowTextToSpeech.addVoice(offlineVoice)
        engine.initialVoice = chineseVoice("network", network = true)
        engine.onInitListener.onInit(TextToSpeech.SUCCESS)
        idleMain()
        assertThat(engine.currentVoice).isEqualTo(offlineVoice)

        val failures = mutableListOf<String>()
        var completed = 0
        engine.speakResult = TextToSpeech.ERROR
        controller.speak("拒绝合成", { completed++ }, failures::add)
        assertThat(failures).hasSize(1)
        engine.speakResult = TextToSpeech.SUCCESS
        controller.speak("运行失败", { completed++ }) {
            assertThat(Looper.myLooper()).isSameInstanceAs(Looper.getMainLooper())
            failures += it
        }
        Thread { engine.utteranceProgressListener.onError(engine.utteranceIds.last(), TextToSpeech.ERROR_SYNTHESIS) }
            .apply { start(); join() }
        idleMain()
        assertThat(failures).hasSize(2)
        assertThat(completed).isEqualTo(0)
        controller.shutdown()
    }

    @Test
    fun `queued callbacks from an old utterance cannot finish the replacement`() {
        val (controller, engine) = initializedController()
        val completed = mutableListOf<String>()
        val failures = mutableListOf<String>()
        controller.speak("旧任务", { completed += "old" }, failures::add)
        val oldId = engine.utteranceIds.last()
        engine.utteranceProgressListener.onDone(oldId)
        controller.speak("新任务", {
            assertThat(Looper.myLooper()).isSameInstanceAs(Looper.getMainLooper())
            completed += "new"
        }, failures::add)
        val newId = engine.utteranceIds.last()
        engine.utteranceProgressListener.onError(oldId, TextToSpeech.ERROR_SYNTHESIS)
        idleMain()
        assertThat(completed).isEmpty()
        assertThat(failures).isEmpty()
        Thread { engine.utteranceProgressListener.onDone(newId) }.apply { start(); join() }
        idleMain()
        assertThat(completed).containsExactly("new")
        assertThat(failures).isEmpty()
        controller.shutdown()
    }

    @Test
    fun `stop invalidates pending speech and already queued completion or failure`() {
        val (controller, engine) = newController()
        var completed = 0
        val failures = mutableListOf<String>()
        controller.speak("初始化等待中", { completed++ }, failures::add)
        controller.stop()
        engine.onInitListener.onInit(TextToSpeech.SUCCESS)
        idleMain()
        assertThat(engine.utteranceIds).isEmpty()

        controller.speak("正在朗读", { completed++ }, failures::add)
        val id = engine.utteranceIds.last()
        engine.utteranceProgressListener.onDone(id)
        engine.utteranceProgressListener.onError(id, TextToSpeech.ERROR_SYNTHESIS)
        controller.stop()
        idleMain()
        assertThat(completed).isEqualTo(0)
        assertThat(failures).isEmpty()
        assertThat(engine.isStopped).isTrue()
        controller.shutdown()
    }

    private fun newController(): Pair<ReaderTextToSpeechController, ControlledTextToSpeechShadow> {
        val controller = ReaderTextToSpeechController(ApplicationProvider.getApplicationContext())
        val engine = Shadow.extract<ControlledTextToSpeechShadow>(ShadowTextToSpeech.getLastTextToSpeechInstance())
        return controller to engine
    }

    private fun initializedController(): Pair<ReaderTextToSpeechController, ControlledTextToSpeechShadow> =
        newController().also { (_, engine) ->
            engine.onInitListener.onInit(TextToSpeech.SUCCESS)
            idleMain()
        }

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    private fun chineseVoice(name: String, network: Boolean) = Voice(
        name, Locale.SIMPLIFIED_CHINESE, Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL, network, emptySet(),
    )
}

@Implements(TextToSpeech::class)
class ControlledTextToSpeechShadow : ShadowTextToSpeech() {
    var languageResult = TextToSpeech.LANG_COUNTRY_AVAILABLE
    var speakResult = TextToSpeech.SUCCESS
    var initialVoice: Voice? = null
    val utteranceIds = mutableListOf<String>()

    @Implementation
    public override fun setLanguage(locale: Locale): Int {
        super.setLanguage(locale)
        initialVoice?.let { setVoice(it) }
        return languageResult
    }

    @Implementation
    public override fun speak(text: CharSequence, queueMode: Int, params: Bundle, utteranceId: String): Int {
        utteranceIds += utteranceId
        return speakResult
    }
}
