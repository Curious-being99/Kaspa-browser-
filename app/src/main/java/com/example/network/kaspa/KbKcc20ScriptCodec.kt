package com.example.network.kaspa

/**
 * KCC-1 push primitives used by the KCC-20 standard transfer assembler.
 * Mirrors the pure helpers in production standard wallets (PushMinimal / PushExplicit).
 */
object KbKcc20ScriptCodec {

    fun concat(vararg parts: ByteArray): ByteArray {
        val n = parts.sumOf { it.size }
        val out = ByteArray(n)
        var o = 0
        for (p in parts) {
            System.arraycopy(p, 0, out, o, p.size)
            o += p.size
        }
        return out
    }

    /** Length-prefixed push: OP_PUSHDATA forms for n > 75. */
    fun lengthPrefixed(payload: ByteArray): ByteArray {
        val n = payload.size
        return when {
            n <= 75 -> concat(byteArrayOf(n.toByte()), payload)
            n <= 0xff -> concat(byteArrayOf(0x4c, n.toByte()), payload)
            n <= 0xffff -> concat(
                byteArrayOf(0x4d, (n and 0xff).toByte(), ((n shr 8) and 0xff).toByte()),
                payload
            )
            else -> {
                require(n <= 0xffffffffL) { "push payload too large" }
                concat(
                    byteArrayOf(
                        0x4e,
                        (n and 0xff).toByte(),
                        ((n shr 8) and 0xff).toByte(),
                        ((n shr 16) and 0xff).toByte(),
                        ((n shr 24) and 0xff).toByte()
                    ),
                    payload
                )
            }
        }
    }

    /**
     * KCC-1 PushMinimal (invocation arguments).
     * Small integers 1..16 → OP_1..OP_16; -1 → OP_1NEGATE; else length-prefixed.
     */
    fun pushMinimal(payload: ByteArray): ByteArray {
        if (payload.isEmpty()) return byteArrayOf(0x00)
        if (payload.size == 1) {
            val b = payload[0].toInt() and 0xff
            if (b in 1..16) return byteArrayOf((0x50 + b).toByte())
            if (b == 0x81) return byteArrayOf(0x4f) // OP_1NEGATE
        }
        return lengthPrefixed(payload)
    }

    /** KCC-1 PushExplicit (state fields): length-prefixed only (never OP_1 for 0x01). */
    fun pushExplicit(payload: ByteArray): ByteArray {
        if (payload.isEmpty()) return byteArrayOf(0x00)
        return lengthPrefixed(payload)
    }

    fun hexToBytes(hex: String): ByteArray {
        val h = hex.trim().lowercase().removePrefix("0x")
        require(h.length % 2 == 0 && h.matches(Regex("[0-9a-f]*"))) { "invalid hex" }
        return ByteArray(h.length / 2) { i ->
            h.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
