package com.comixreader.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import kotlin.random.Random

class ApiCipherTest {
    private fun randomKeys(seed: Int): CipherKeys {
        val r = Random(seed)
        return CipherKeys(
            tables = List(3) { (0..255).shuffled(r) },
            keys = List(3) { List(24) { r.nextInt(256) } },
        )
    }

    @Test
    fun decryptReversesSign() {
        val keys = randomKeys(7)
        val cipher = ApiCipher(keys)
        val message = "/manga?keyword=one piece&page=2"
        val token = cipher.sign("/api/v1/manga", "keyword=one piece&page=2")
        assertEquals(message, cipher.decrypt(token))
    }

    @Test
    fun signatureIsUrlSafeWithoutPadding() {
        val token = ApiCipher(randomKeys(1)).sign("/api/v1/chapters/123", "")
        assertFalse(token.contains('='))
        assertFalse(token.contains('+'))
        assertFalse(token.contains('/'))
        // Decodes as URL-safe Base64.
        assertTrue(Base64.getUrlDecoder().decode(token).isNotEmpty())
    }

    @Test
    fun differentKeysGiveDifferentSignatures() {
        val a = ApiCipher(randomKeys(1)).sign("/api/v1/manga", "page=1")
        val b = ApiCipher(randomKeys(2)).sign("/api/v1/manga", "page=1")
        assertNotEquals(a, b)
    }

    @Test
    fun unicodeSurvivesRoundTrip() {
        val cipher = ApiCipher(randomKeys(3))
        val token = cipher.sign("/api/v1/manga", "keyword=進撃の巨人 ★")
        assertEquals("/manga?keyword=進撃の巨人 ★", cipher.decrypt(token))
    }

    @Test
    fun rejectsIncompleteKeys() {
        val bad = CipherKeys(tables = List(3) { List(256) { 0 } }, keys = List(3) { listOf(1) })
        assertFalse(bad.isUsable)
    }

    @Test
    fun canonicalSortsAndIndexesArrays() {
        val params = listOf(
            "page" to "1",
            "types[]" to "manhwa",
            "order[score]" to "desc",
            "types[]" to "manga",
            "keyword" to " solo ",
        )
        assertEquals(
            listOf(
                "keyword" to "solo",
                "order[score]" to "desc",
                "page" to "1",
                "types[0]" to "manhwa",
                "types[1]" to "manga",
            ),
            ApiCipher.canonical(params),
        )
    }
}
