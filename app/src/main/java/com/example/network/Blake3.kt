package com.example.network

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure Kotlin implementation of the official BLAKE3 cryptographic hash function (256-bit / 32-byte output).
 * Supports arbitrary input sizes via standard BLAKE3 tree hashing.
 */
object Blake3 {

    private val IV = intArrayOf(
        0x6A09E667.toInt(),
        0xBB67AE85.toInt(),
        0x3C6EF372.toInt(),
        0xA54FF53A.toInt(),
        0x510E527F.toInt(),
        0x9B05688C.toInt(),
        0x1F83D9AB.toInt(),
        0x5BE0CD19.toInt()
    )

    private const val CHUNK_START = 1
    private const val CHUNK_END = 2
    private const val PARENT = 4
    private const val ROOT = 8

    private val MSG_PERMUTATION = intArrayOf(2, 6, 3, 10, 7, 0, 4, 13, 1, 11, 12, 5, 9, 14, 15, 8)

    fun hash(data: ByteArray): ByteArray {
        val hasher = Hasher()
        hasher.update(data)
        return hasher.finalize()
    }

    private fun g(s: IntArray, a: Int, b: Int, c: Int, d: Int, mx: Int, my: Int) {
        s[a] = s[a] + s[b] + mx
        s[d] = Integer.rotateRight(s[d] xor s[a], 16)
        s[c] = s[c] + s[d]
        s[b] = Integer.rotateRight(s[b] xor s[c], 12)
        s[a] = s[a] + s[b] + my
        s[d] = Integer.rotateRight(s[d] xor s[a], 8)
        s[c] = s[c] + s[d]
        s[b] = Integer.rotateRight(s[b] xor s[c], 7)
    }

    private fun round(s: IntArray, m: IntArray) {
        g(s, 0, 4, 8, 12, m[0], m[1])
        g(s, 1, 5, 9, 13, m[2], m[3])
        g(s, 2, 6, 10, 14, m[4], m[5])
        g(s, 3, 7, 11, 15, m[6], m[7])

        g(s, 0, 5, 10, 15, m[8], m[9])
        g(s, 1, 6, 11, 12, m[10], m[11])
        g(s, 2, 7, 8, 13, m[12], m[13])
        g(s, 3, 4, 9, 14, m[14], m[15])
    }

    private fun permute(m: IntArray): IntArray {
        val next = IntArray(16)
        for (i in 0 until 16) {
            next[i] = m[MSG_PERMUTATION[i]]
        }
        return next
    }

    private fun compress(
        cv: IntArray,
        blockWords: IntArray,
        counter: Long,
        blockLen: Int,
        flags: Int
    ): IntArray {
        val state = IntArray(16)
        System.arraycopy(cv, 0, state, 0, 8)
        System.arraycopy(IV, 0, state, 8, 4)
        state[12] = (counter and 0xFFFFFFFFL).toInt()
        state[13] = (counter ushr 32).toInt()
        state[14] = blockLen
        state[15] = flags

        var m = blockWords
        for (r in 0 until 7) {
            round(state, m)
            if (r < 6) {
                m = permute(m)
            }
        }
        return state
    }

    private fun wordsFromBytesLE(bytes: ByteArray, offset: Int, len: Int): IntArray {
        val words = IntArray(16)
        val limit = offset + len
        var wIdx = 0
        var bIdx = offset
        while (bIdx < limit && wIdx < 16) {
            var word = 0
            val remaining = minOf(4, limit - bIdx)
            for (i in 0 until remaining) {
                word = word or ((bytes[bIdx + i].toInt() and 0xFF) shl (i * 8))
            }
            words[wIdx] = word
            wIdx++
            bIdx += 4
        }
        return words
    }

    private fun parentOutput(leftCv: IntArray, rightCv: IntArray, key: IntArray, flags: Int): Output {
        val blockWords = IntArray(16)
        System.arraycopy(leftCv, 0, blockWords, 0, 8)
        System.arraycopy(rightCv, 0, blockWords, 8, 8)
        return Output(
            inputCv = key,
            blockWords = blockWords,
            counter = 0L,
            blockLen = 64,
            flags = PARENT or flags
        )
    }

    private class Output(
        val inputCv: IntArray,
        val blockWords: IntArray,
        val counter: Long,
        val blockLen: Int,
        val flags: Int
    ) {
        fun chainingValue(): IntArray {
            val state = compress(inputCv, blockWords, counter, blockLen, flags)
            val cv = IntArray(8)
            for (i in 0 until 8) {
                cv[i] = state[i] xor state[i + 8]
            }
            return cv
        }

        fun rootBytes(): ByteArray {
            val state = compress(inputCv, blockWords, counter, blockLen, flags or ROOT)
            val out = ByteArray(32)
            for (i in 0 until 8) {
                val word = state[i] xor state[i + 8]
                out[i * 4 + 0] = (word and 0xFF).toByte()
                out[i * 4 + 1] = ((word ushr 8) and 0xFF).toByte()
                out[i * 4 + 2] = ((word ushr 16) and 0xFF).toByte()
                out[i * 4 + 3] = ((word ushr 24) and 0xFF).toByte()
            }
            return out
        }
    }

    private class ChunkState(
        val key: IntArray,
        val chunkCounter: Long,
        val flags: Int
    ) {
        private val block = ByteArray(64)
        private var blockLen = 0
        private var blocksCompressed = 0
        private var cv = key

        fun len(): Int = blocksCompressed * 64 + blockLen

        fun update(data: ByteArray, offset: Int, length: Int): Int {
            var cursor = offset
            var remaining = length
            while (remaining > 0) {
                if (blockLen == 64) {
                    val blockWords = wordsFromBytesLE(block, 0, 64)
                    var blockFlags = flags
                    if (blocksCompressed == 0) {
                        blockFlags = blockFlags or CHUNK_START
                    }
                    val state = compress(cv, blockWords, chunkCounter, 64, blockFlags)
                    val nextCv = IntArray(8)
                    for (i in 0 until 8) {
                        nextCv[i] = state[i] xor state[i + 8]
                    }
                    cv = nextCv
                    blocksCompressed++
                    blockLen = 0
                }
                val take = minOf(remaining, 64 - blockLen)
                System.arraycopy(data, cursor, block, blockLen, take)
                blockLen += take
                cursor += take
                remaining -= take
            }
            return cursor - offset
        }

        fun output(): Output {
            val blockWords = wordsFromBytesLE(block, 0, blockLen)
            var blockFlags = flags
            if (blocksCompressed == 0) {
                blockFlags = blockFlags or CHUNK_START
            }
            blockFlags = blockFlags or CHUNK_END
            return Output(cv, blockWords, chunkCounter, blockLen, blockFlags)
        }
    }

    class Hasher(val key: IntArray = IV, val flags: Int = 0) {
        private var chunkState = ChunkState(key, 0L, flags)
        private val cvStack = Array<IntArray?>(54) { null }
        private var cvStackLen = 0

        fun update(data: ByteArray) {
            var offset = 0
            var remaining = data.size
            while (remaining > 0) {
                if (chunkState.len() == 1024) {
                    val chunkOutput = chunkState.output()
                    val chunkCv = chunkOutput.chainingValue()
                    val totalChunks = chunkState.chunkCounter + 1
                    addChunkCv(chunkCv, totalChunks)
                    chunkState = ChunkState(key, totalChunks, flags)
                }
                val want = 1024 - chunkState.len()
                val take = minOf(remaining, want)
                chunkState.update(data, offset, take)
                offset += take
                remaining -= take
            }
        }

        private fun addChunkCv(newCv: IntArray, totalChunks: Long) {
            var cv = newCv
            var count = totalChunks
            while ((count and 1L) == 0L) {
                val parentCv = cvStack[--cvStackLen]!!
                val parent = parentOutput(parentCv, cv, key, flags)
                cv = parent.chainingValue()
                count = count ushr 1
            }
            cvStack[cvStackLen++] = cv
        }

        fun finalize(): ByteArray {
            var output = chunkState.output()
            var count = cvStackLen
            while (count > 0) {
                count--
                val parentCv = cvStack[count]!!
                output = parentOutput(parentCv, output.chainingValue(), key, flags)
            }
            return output.rootBytes()
        }
    }
}
