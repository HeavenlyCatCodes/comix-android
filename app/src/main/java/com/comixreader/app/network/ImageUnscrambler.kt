package com.comixreader.app.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import java.io.ByteArrayOutputStream

/**
 * comix.to serves some chapter pages obfuscated. Response headers say how:
 *  - `x-enc-seed` / `x-enc-len` / `x-enc-algo`: the first bytes are XORed
 *    with a pseudo-random key stream.
 *  - `x-scramble-grid: 5x5` / `x-scramble-seed` / `x-scramble-algo` /
 *    `x-scramble-hash`: the picture is cut into 25 tiles and shuffled.
 * This class reverses both so the reader gets a normal image.
 */
object ImageUnscrambler {
    private const val GRID = 5

    class Instructions(
        val xorSeed: Int?,
        val xorLength: Int?,
        val xorAlgo: String?,
        val gridSeed: Int?,
        val gridAlgo: String?,
    ) {
        val needsXor get() = xorSeed != null && xorSeed != 0 && xorLength != null
        val needsGrid get() = gridSeed != null && gridSeed != 0
        val needsWork get() = needsXor || needsGrid
    }

    fun instructions(header: (String) -> String?): Instructions {
        val grid = header("x-scramble-grid")
        val gridAlgo = header("x-scramble-algo")
        val knownGridAlgo = gridAlgo == null || gridAlgo in setOf("1", "2", "3")
        val gridSeed = header("x-scramble-seed")?.toLongOrNull()?.toInt()
            ?.takeIf { grid == "${GRID}x$GRID" && knownGridAlgo }
            ?.let { it xor hashSalt(header("x-scramble-hash")) }
        return Instructions(
            xorSeed = header("x-enc-seed")?.toLongOrNull()?.toInt(),
            xorLength = header("x-enc-len")?.toIntOrNull(),
            xorAlgo = header("x-enc-algo"),
            gridSeed = gridSeed,
            gridAlgo = gridAlgo,
        )
    }

    /** Returns JPEG/WebP/PNG bytes ready for any decoder. */
    fun process(bytes: ByteArray, how: Instructions): ByteArray {
        var data = bytes
        if (how.needsXor) data = unXor(data, how.xorSeed!!, how.xorLength!!, how.xorAlgo)
        if (how.needsGrid) data = unshuffle(data, how.gridSeed!!, how.gridAlgo) ?: data
        return data
    }

    // The server sends a short code; the real salt is a fixed value per code.
    private fun hashSalt(code: String?): Int = when (code?.trim()) {
        "03632" -> 58414
        "02900" -> 117532
        else -> 0
    }

    // ---- byte stream ------------------------------------------------------

    private fun unXor(input: ByteArray, seed: Int, length: Int, algo: String?): ByteArray {
        if (algo != "2") return xorWith(input, length, lcgStream(seed))
        // Version 2 has shipped with a few variants; keep the one that yields an image.
        val attempts = listOf(
            { xorWith(input, length, xorshiftStream(seed or 1, highByte = false)) },
            { xorWith(input, length, xorshiftStream(seed, highByte = false)) },
            { xorWith(input, length, xorshiftStream(seed or 1, highByte = true)) },
            { xorWith(input, length, lcgStream(seed)) },
        )
        var first: ByteArray? = null
        for (attempt in attempts) {
            val out = attempt()
            if (looksLikeImage(out)) return out
            if (first == null) first = out
        }
        return first!!
    }

    private inline fun xorWith(input: ByteArray, length: Int, crossinline next: () -> Int): ByteArray {
        val out = input.copyOf()
        val n = minOf(out.size, length)
        for (i in 0 until n) out[i] = (out[i].toInt() xor next()).toByte()
        return out
    }

    private fun lcgStream(seed: Int): () -> Int {
        var state = seed
        return {
            state = state * 1_000_005 + 1_234_567_891
            state ushr 24
        }
    }

    private fun xorshiftStream(seed: Int, highByte: Boolean): () -> Int {
        var state = seed
        return {
            state = xorshift(state)
            if (highByte) state ushr 24 else state and 0xFF
        }
    }

    private fun xorshift(value: Int): Int {
        var x = value
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        return x
    }

    fun looksLikeImage(b: ByteArray): Boolean {
        if (b.size < 12) return false
        val jpeg = b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte()
        val png = b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte()
        val webp = String(b, 0, 4, Charsets.US_ASCII) == "RIFF" && String(b, 8, 4, Charsets.US_ASCII) == "WEBP"
        val gif = String(b, 0, 3, Charsets.US_ASCII) == "GIF"
        return jpeg || png || webp || gif
    }

    // ---- tile grid --------------------------------------------------------

    /** For every output tile, the index of the scrambled tile that belongs there. */
    internal fun sourceTileFor(seed: Int, algo: String?): IntArray {
        val count = GRID * GRID
        val shuffled = IntArray(count) { it }
        var state = if (algo == "3") seed or 1 else seed
        for (i in count - 1 downTo 1) {
            state = if (algo == "3") xorshift(state) else state * 1_664_525 + 1_013_904_223
            val j = ((state.toLong() and 0xFFFF_FFFFL) % (i + 1)).toInt()
            val t = shuffled[i]
            shuffled[i] = shuffled[j]
            shuffled[j] = t
        }
        val source = IntArray(count)
        shuffled.forEachIndexed { position, tile -> source[tile] = position }
        return source
    }

    private fun unshuffle(bytes: ByteArray, seed: Int, algo: String?): ByteArray? {
        val scrambled = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val tileW = scrambled.width / GRID
        val tileH = scrambled.height / GRID
        val output = Bitmap.createBitmap(scrambled.width, scrambled.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        // Pixels outside the evenly divisible area are never shuffled.
        canvas.drawBitmap(scrambled, 0f, 0f, null)
        val source = sourceTileFor(seed, algo)
        for (dest in 0 until GRID * GRID) {
            val from = source[dest]
            val src = Rect(
                (from % GRID) * tileW, (from / GRID) * tileH,
                (from % GRID + 1) * tileW, (from / GRID + 1) * tileH,
            )
            val dst = Rect(
                (dest % GRID) * tileW, (dest / GRID) * tileH,
                (dest % GRID + 1) * tileW, (dest / GRID + 1) * tileH,
            )
            canvas.drawBitmap(scrambled, src, dst, null)
        }
        scrambled.recycle()
        val out = ByteArrayOutputStream(bytes.size)
        output.compress(Bitmap.CompressFormat.JPEG, 92, out)
        output.recycle()
        return out.toByteArray()
    }
}
