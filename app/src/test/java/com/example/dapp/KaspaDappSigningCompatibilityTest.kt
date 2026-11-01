package com.example.dapp

import com.example.network.CryptoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class KaspaDappSigningCompatibilityTest {
    @Test
    fun kip5PersonalMessageProducesRawVerifiableSchnorrSignature() {
        val privateKey = BigInteger.ONE
        val publicKey = CryptoUtils.to32ByteArray(CryptoUtils.pointMultiply(privateKey).x)
        val message = "Sign in to KaspaRocket\nDomain: kasparocket.fun\nNonce: test-only".toByteArray(Charsets.UTF_8)

        val signature = CryptoUtils.signKaspaPersonalMessage(privateKey, message)

        assertEquals(128, signature.length)
        assertTrue(signature.matches(Regex("[0-9a-fA-F]{128}")))
        assertTrue(CryptoUtils.verifyKaspaPersonalMessage(signature, message, publicKey))
        assertTrue(!CryptoUtils.verifyKaspaPersonalMessage(signature, "different message".toByteArray(), publicKey))
    }
}
