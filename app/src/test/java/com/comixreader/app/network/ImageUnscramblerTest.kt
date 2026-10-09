package com.comixreader.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageUnscramblerTest {
    @Test
    fun tileOrderIsAPermutation() {
        for (algo in listOf(null, "1", "2", "3")) {
            for (seed in listOf(1, 42, -7, 123456789)) {
                val order = ImageUnscrambler.sourceTileFor(seed, algo)
                assertEquals((0 until 25).toList(), order.sorted())
            }
        }
    }

    @Test
    fun unknownHeadersMeanNoWork() {
        val how = ImageUnscrambler.instructions { null }
        assertFalse(how.needsWork)
    }

    @Test
    fun gridNeedsKnownLayout() {
        val headers = mapOf("x-scramble-grid" to "4x4", "x-scramble-seed" to "99")
        assertFalse(ImageUnscrambler.instructions { headers[it] }.needsGrid)
        val ok = mapOf("x-scramble-grid" to "5x5", "x-scramble-seed" to "99")
        assertTrue(ImageUnscrambler.instructions { ok[it] }.needsGrid)
    }

    @Test
    fun recognisesImageSignatures() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + ByteArray(10)
        assertTrue(ImageUnscrambler.looksLikeImage(jpeg))
        assertFalse(ImageUnscrambler.looksLikeImage(ByteArray(12)))
    }

    @Test
    fun xorIsReversible() {
        val image = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + ByteArray(30) { it.toByte() }
        val headers = mapOf("x-enc-seed" to "12345", "x-enc-len" to "16")
        val how = ImageUnscrambler.instructions { headers[it] }
        val scrambled = ImageUnscrambler.process(image, how)
        assertFalse(scrambled.contentEquals(image))
        assertTrue(ImageUnscrambler.process(scrambled, how).contentEquals(image))
    }
}
