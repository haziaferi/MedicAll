package com.hazia.medicall.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SmokeTest {
    @Test
    fun coreLoads() {
        assertEquals("medicall", CoreInfo.NAME)
    }
}
