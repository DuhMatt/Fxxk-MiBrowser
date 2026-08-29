package com.hyperosfix.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class XiaomiPackageListTest {

    @Test
    fun `voice assist hook targets only the current 8_2_3 intent utility`() {
        assertEquals(
            "com.xiaomi.voiceassistant.utils.t2",
            XiaomiPackageList.CLASS_VOICE_ASSIST_CURRENT
        )
    }
}
