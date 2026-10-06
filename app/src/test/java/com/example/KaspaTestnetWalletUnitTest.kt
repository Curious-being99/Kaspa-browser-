package com.example

import com.example.network.CryptoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KaspaTestnetWalletUnitTest {

    @Test
    fun testTestnet10AddressDerivation() {
        val testMnemonic = "apple banana cherry date elderberry fig grape honeydew kiwi lemon mango nectarine"
        val keyPair = CryptoUtils.deriveKaspaKeyPair(testMnemonic, prefix = "kaspatest")

        assertNotNull(keyPair)
        assertNotNull(keyPair.kaspaAddress)
        assertTrue("Address must start with kaspatest: prefix", keyPair.kaspaAddress.startsWith("kaspatest:"))
        assertEquals("Public key must be 32 bytes (64 hex characters)", 64, keyPair.publicKeyHex.length)
        assertTrue("Derived address must pass valid testnet address check", CryptoUtils.isValidTestnetAddress(keyPair.kaspaAddress))
    }

    @Test
    fun testTestnetAddressValidationStrictness() {
        val testMnemonic = "apple banana cherry date elderberry fig grape honeydew kiwi lemon mango nectarine"
        val testnetKey = CryptoUtils.deriveKaspaKeyPair(testMnemonic, prefix = "kaspatest")
        val mainnetKey = CryptoUtils.deriveKaspaKeyPair(testMnemonic, prefix = "kaspa")

        // Testnet address must be valid for testnet
        assertTrue(CryptoUtils.isValidTestnetAddress(testnetKey.kaspaAddress))
        assertTrue(CryptoUtils.isAnyValidKaspaAddress(testnetKey.kaspaAddress))

        // Mainnet address must NOT be valid as testnet address
        assertFalse(CryptoUtils.isValidTestnetAddress(mainnetKey.kaspaAddress))
        assertTrue(CryptoUtils.isAnyValidKaspaAddress(mainnetKey.kaspaAddress))

        // Random garbage must fail
        assertFalse(CryptoUtils.isValidTestnetAddress("kaspatest:invalid"))
        assertFalse(CryptoUtils.isValidTestnetAddress(""))
        assertFalse(CryptoUtils.isValidTestnetAddress("ethereum:0x123"))
    }

    @Test
    fun testBip39PassphraseDerivation() {
        val testMnemonic = "apple banana cherry date elderberry fig grape honeydew kiwi lemon mango nectarine"
        val keyNoPassphrase = CryptoUtils.deriveKaspaKeyPair(testMnemonic, prefix = "kaspatest", passphrase = "")
        val keyWithPassphrase1 = CryptoUtils.deriveKaspaKeyPair(testMnemonic, prefix = "kaspatest", passphrase = "secret_password_1")
        val keyWithPassphrase2 = CryptoUtils.deriveKaspaKeyPair(testMnemonic, prefix = "kaspatest", passphrase = "secret_password_2")

        // Passphrases must alter the derived address
        org.junit.Assert.assertNotEquals(keyNoPassphrase.kaspaAddress, keyWithPassphrase1.kaspaAddress)
        org.junit.Assert.assertNotEquals(keyWithPassphrase1.kaspaAddress, keyWithPassphrase2.kaspaAddress)

        // All derived addresses must still be valid Kaspa Testnet 10 addresses
        assertTrue(CryptoUtils.isValidTestnetAddress(keyNoPassphrase.kaspaAddress))
        assertTrue(CryptoUtils.isValidTestnetAddress(keyWithPassphrase1.kaspaAddress))
        assertTrue(CryptoUtils.isValidTestnetAddress(keyWithPassphrase2.kaspaAddress))
    }

    @Test
    fun testWalletSetupFlowKeyGeneration() {
        val mnemonic = CryptoUtils.generateMnemonic()
        assertNotNull(mnemonic)
        val words = mnemonic.trim().split("\\s+".toRegex())
        assertEquals("Setup flow must generate exactly 12 words", 12, words.size)

        val account = CryptoUtils.deriveDecentralizedAccount(
            customHandle = "Kaspa Setup Test Wallet",
            seedMnemonic = mnemonic,
            networkPrefix = "kaspatest",
            passphrase = "setup_passphrase_test"
        )
        assertNotNull(account)
        assertTrue("Account must have kaspatest: address", account.kaspaAddress.startsWith("kaspatest:"))
        assertTrue("Derived address must be valid Testnet 10 CashAddr", CryptoUtils.isValidTestnetAddress(account.kaspaAddress))
    }
}
