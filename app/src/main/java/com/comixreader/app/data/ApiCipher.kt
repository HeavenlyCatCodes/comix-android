package com.comixreader.app.data

import kotlinx.serialization.Serializable
import java.util.Base64

/**
 * Keys the site's own JavaScript uses to sign API calls and to encrypt API
 * responses. They are read out of a WebView at run time (see [PageRunner]),
 * never shipped in the app, so a site update only needs a fresh harvest.
 */
@Serializable
data class CipherKeys(
    val tables: List<List<Int>>,
    val keys: List<List<Int>>,
) {
    val isUsable: Boolean
        get() = tables.size == ROUNDS && tables.all { t -> t.size == 256 && t.toSet().size == 256 } &&
            keys.size == ROUNDS && keys.all { it.isNotEmpty() }

    companion object {
        const val ROUNDS = 3
    }
}

/**
 * Three rounds of a byte substitution with a rolling XOR key, chained on the
 * previous output byte, then URL-safe Base64.
 */
class ApiCipher(keys: CipherKeys) {
    private val tables = keys.tables.map { it.toIntArray() }
    private val inverse = tables.map { table -> IntArray(256).also { inv -> table.forEachIndexed { i, v -> inv[v] = i } } }
    private val roundKeys = keys.keys.map { it.toIntArray() }

    init {
        require(keys.isUsable) { "Incomplete cipher keys" }
    }

    /** [path] like `/api/v1/manga`, [query] the canonical `a=1&b=2` string. */
    fun sign(path: String, query: String): String {
        val message = path.removePrefix(API_PREFIX) + if (query.isEmpty()) "" else "?$query"
        var bytes = message.toByteArray(Charsets.UTF_8)
        for (round in 0 until CipherKeys.ROUNDS) bytes = forward(bytes, round)
        return encoder.encodeToString(bytes)
    }

    fun decrypt(token: String): String {
        var bytes = decoder.decode(token)
        for (round in CipherKeys.ROUNDS - 1 downTo 0) bytes = backward(bytes, round)
        return bytes.toString(Charsets.UTF_8)
    }

    private fun forward(input: ByteArray, round: Int): ByteArray {
        val table = tables[round]
        val key = roundKeys[round]
        var chain = CHAIN_START[round]
        return ByteArray(input.size) { i ->
            val out = table[(input[i].toInt() and 0xFF) xor key[i % key.size] xor chain]
            chain = out
            out.toByte()
        }
    }

    private fun backward(input: ByteArray, round: Int): ByteArray {
        val inv = inverse[round]
        val key = roundKeys[round]
        var chain = CHAIN_START[round]
        return ByteArray(input.size) { i ->
            val cipherByte = input[i].toInt() and 0xFF
            val plain = inv[cipherByte] xor key[i % key.size] xor chain
            chain = cipherByte
            plain.toByte()
        }
    }

    companion object {
        const val API_PREFIX = "/api/v1"
        private val CHAIN_START = intArrayOf(189, 133, 32)
        private val encoder = Base64.getUrlEncoder().withoutPadding()
        private val decoder = Base64.getUrlDecoder()

        /**
         * Sorted by parameter name. Array parameters (`name[]`, or repeated
         * names) become `name[0]`, `name[1]`, ... as the site's client does.
         */
        fun canonical(params: List<Pair<String, String>>): List<Pair<String, String>> =
            params.groupBy({ it.first }, { it.second.trim() })
                .toSortedMap()
                .flatMap { (raw, values) ->
                    val name = raw.removeSuffix("[]")
                    if (values.size == 1 && !raw.endsWith("[]")) {
                        listOf(name to values.single())
                    } else {
                        values.mapIndexed { i, v -> "$name[$i]" to v }
                    }
                }
    }
}
