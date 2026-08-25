package com.hyperosfix.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaomiPackageListTest {

    @Test
    fun `s2 hook stays disabled below HyperOS 4 VoiceAssist threshold`() {
        assertFalse(
            XiaomiPackageList.shouldHookVoiceAssistS2(
                XiaomiPackageList.VOICE_ASSIST_S2_MIN_VERSION_CODE - 1
            )
        )
    }

    @Test
    fun `s2 hook is enabled at HyperOS 4 VoiceAssist threshold`() {
        assertTrue(
            XiaomiPackageList.shouldHookVoiceAssistS2(
                XiaomiPackageList.VOICE_ASSIST_S2_MIN_VERSION_CODE
            )
        )
    }

    @Test
    fun `s2 hook remains enabled above HyperOS 4 VoiceAssist threshold`() {
        assertTrue(
            XiaomiPackageList.shouldHookVoiceAssistS2(
                XiaomiPackageList.VOICE_ASSIST_S2_MIN_VERSION_CODE + 1
            )
        )
    }
}
