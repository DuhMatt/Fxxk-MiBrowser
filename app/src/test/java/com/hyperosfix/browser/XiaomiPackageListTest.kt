package com.hyperosfix.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class XiaomiPackageListTest {

    @Test
    fun `voice assist intent utility candidates cover current Super XiaoAi builds`() {
        assertEquals(
            listOf(
                "com.xiaomi.voiceassistant.utils.b3",
                "com.xiaomi.voiceassistant.utils.t2",
            ),
            XiaomiPackageList.CLASS_VOICE_ASSIST_INTENT_UTILS_CANDIDATES
        )
    }
}
