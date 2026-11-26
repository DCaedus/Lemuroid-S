package com.swordfish.lemuroid.lib.saves

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RetroArchStatesTest {
    @Test
    fun rzipRoundTrip() {
        // Several chunks, partly compressible like a real state.
        val state = ByteArray(300_000) { i -> if (i % 7 == 0) Random(i).nextInt().toByte() else 0 }
        val encoded = RetroArchStates.encodeRzip(state)
        assertTrue(String(encoded, 0, 8, Charsets.US_ASCII) == "#RZIPv\u0001#")
        assertTrue(encoded.size < state.size)
        assertArrayEquals(state, RetroArchStates.decode(encoded))
    }

    @Test
    fun rzipEmptyAndRaw() {
        assertArrayEquals(ByteArray(0), RetroArchStates.decode(RetroArchStates.encodeRzip(ByteArray(0))))
        val raw = byteArrayOf(1, 2, 3)
        assertArrayEquals(raw, RetroArchStates.decode(raw))
    }
}
