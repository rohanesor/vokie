package com.vokie.stt

import com.vokie.communication.PacketV2
import com.vokie.domain.model.Message
import com.vokie.domain.model.VokieLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageRoutingTest {
    @Test fun selectedInputLanguageIsAlwaysTheExplicitWhisperLanguage() {
        assertEquals(SttLanguage.ENGLISH, resolveProductionSttLanguage(UserLanguageProfile(VokieLanguage.EN, VokieLanguage.HI)))
        assertEquals(SttLanguage.HINDI, resolveProductionSttLanguage(UserLanguageProfile(VokieLanguage.HI, VokieLanguage.TA)))
        assertEquals(SttLanguage.TAMIL, resolveProductionSttLanguage(UserLanguageProfile(VokieLanguage.TA, VokieLanguage.HI)))
    }

    @Test fun onlyPrototypeLanguageCodesResolveForStt() {
        assertEquals(SttLanguage.ENGLISH, SttLanguage.fromWhisperCode("en"))
        assertEquals(SttLanguage.HINDI, SttLanguage.fromWhisperCode("hi"))
        assertEquals(SttLanguage.TAMIL, SttLanguage.fromWhisperCode("ta"))
        assertNull(SttLanguage.fromWhisperCode("auto"))
        assertNull(SttLanguage.fromWhisperCode("bn"))
    }

    @Test fun packetPreservesExplicitSenderLanguage() {
        val sender = Message("11111111-1111-1111-1111-111111111111", "sender", 1_700_000_000_000, "எனக்கு உதவி தேவை", VokieLanguage.TA.code)
        val decoded = PacketV2.decode(PacketV2.fromMessage(sender).single()) as PacketV2.Decoded.MessagePacket
        assertEquals(VokieLanguage.TA.code, decoded.packet.languageCode)
    }
}
