package com.example

import com.example.network.kaspa.Kcc20KasivoService
import org.bouncycastle.crypto.digests.Blake3Digest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Kcc20CellOwnerMatchTest {
    private val publicKey = "e7f7369478ebdbfadbb8e802af6cbf4ee1ee5ce43c0c0b6d3df115ddb1b96037"
    private val address = "kaspa:qzrsq2mfj9sf7uye3u5q7juejzlr0axk5jz9fpg4vqe76erdyvxxze84k9nk7"

    @Test
    fun matchesExactPublicKeyOwner() {
        assertTrue(Kcc20KasivoService.cellOwnerMatchesWallet(
            publicKey, "", "00", publicKey, address
        ))
    }

    @Test
    fun matchesExactAddressOwnerWithoutSubstringMatching() {
        assertTrue(Kcc20KasivoService.cellOwnerMatchesWallet(
            "", address, "00", publicKey, address
        ))
        assertFalse(Kcc20KasivoService.cellOwnerMatchesWallet(
            "", address + "junk", "00", publicKey, address
        ))
    }

    @Test
    fun matchesP2pkhSchnorrOwnerHash() {
        val keyBytes = publicKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val digest = Blake3Digest()
        val hash = ByteArray(32)
        digest.update(keyBytes, 0, keyBytes.size)
        digest.doFinal(hash, 0)
        val owner = "p2pkh:" + hash.joinToString("") { "%02x".format(it) }
        assertTrue(Kcc20KasivoService.cellOwnerMatchesWallet(
            owner, "", "01", publicKey, address
        ))
    }

    @Test
    fun rejectsWrongP2pkhOwnerHash() {
        assertFalse(Kcc20KasivoService.cellOwnerMatchesWallet(
            "p2pkh:" + "00".repeat(32), "", "01", publicKey, address
        ))
    }
}
