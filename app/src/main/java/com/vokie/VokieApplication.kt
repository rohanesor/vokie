package com.vokie

import android.app.Application
import com.vokie.data.*

class VokieApplication : Application() {
    val messageRepository = FakeMessageRepository()
    val settingsRepository = FakeSettingsRepository()
    val transport = DemoTransport()
    val stt = DemoSpeechToTextEngine()
    val tts = DemoTextToSpeechEngine()
}
