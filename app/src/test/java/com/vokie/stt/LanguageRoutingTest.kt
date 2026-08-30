package com.vokie.stt

import com.vokie.communication.PacketV2
import com.vokie.domain.model.Message
import com.vokie.domain.model.VokieLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageRoutingTest {
    @Test fun preferredProductionModeRequestsProfileInputLanguage() {
        assertEquals(SttLanguage.TAMIL, resolveProductionSttLanguage(SttRecognitionMode.PREFERRED_LANGUAGE, UserLanguageProfile.same(VokieLanguage.TA), SttLanguage.AUTO))
        assertEquals(SttLanguage.HINDI, resolveProductionSttLanguage(SttRecognitionMode.PREFERRED_LANGUAGE, UserLanguageProfile.same(VokieLanguage.HI), SttLanguage.AUTO))
        assertEquals(SttLanguage.ENGLISH, resolveProductionSttLanguage(SttRecognitionMode.PREFERRED_LANGUAGE, UserLanguageProfile.same(VokieLanguage.EN), SttLanguage.AUTO))
    }

    @Test fun autoDetectedTamilOverridesTamilPreference() {
        val result = resolveSttLanguage(SttLanguage.AUTO, "ta", UserLanguageProfile.same(VokieLanguage.TA))
        assertEquals(SttLanguage.TAMIL, result.language)
        assertEquals(SttLanguage.TAMIL, result.detectedLanguage)
        assertEquals(LanguageSelectionSource.AUTO_DETECTED, result.source)
    }

    @Test fun autoDetectedEnglishOverridesTamilPreference() {
        val result = resolveSttLanguage(SttLanguage.AUTO, "en", UserLanguageProfile.same(VokieLanguage.TA))
        assertEquals(VokieLanguage.EN, result.language.messageLanguage)
    }

    @Test fun autoDetectedBengaliOverridesHindiPreference() {
        val result = resolveSttLanguage(SttLanguage.AUTO, "bn", UserLanguageProfile.same(VokieLanguage.HI))
        assertEquals(VokieLanguage.BN, result.language.messageLanguage)
    }

    @Test fun unavailableOrUnsupportedAutoLidUsesPreferredFallbackNeverEnglish() {
        listOf(null, "unsupported", "auto").forEach { detected ->
            val result = resolveSttLanguage(SttLanguage.AUTO, detected, UserLanguageProfile.same(VokieLanguage.TA))
            assertEquals(SttLanguage.TAMIL, result.language)
            assertNull(result.detectedLanguage)
            assertEquals(LanguageSelectionSource.PREFERRED_FALLBACK, result.source)
        }
    }

    @Test fun explicitTamilDoesNotRequireAutoLid() {
        val result = resolveSttLanguage(SttLanguage.TAMIL, "en", UserLanguageProfile.same(VokieLanguage.EN))
        assertEquals(SttLanguage.TAMIL, result.language)
        assertNull(result.detectedLanguage)
        assertEquals(LanguageSelectionSource.EXPLICIT_SELECTED, result.source)
    }

    @Test fun packetAndReceiverPreserveDetectedSenderLanguage() {
        val senderMessage = Message("11111111-1111-1111-1111-111111111111", "sender", 1_700_000_000_000, "How are you?", VokieLanguage.EN.code)
        val decoded = PacketV2.decode(PacketV2.fromMessage(senderMessage).single()) as PacketV2.Decoded.MessagePacket
        assertEquals(VokieLanguage.EN.code, decoded.packet.languageCode)
        val receiverMessage = Message(decoded.packet.messageId, decoded.packet.sourceDeviceId, decoded.packet.timestamp, decoded.packet.payload.toString(Charsets.UTF_8), decoded.packet.languageCode)
        assertEquals(VokieLanguage.EN.code, receiverMessage.language)
    }
}
