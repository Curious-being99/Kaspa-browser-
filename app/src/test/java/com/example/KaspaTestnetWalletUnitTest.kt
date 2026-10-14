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

    @Test
    fun testAccurateNormalAndFastPriorityFeeTiers() {
        val engine = com.example.network.kaspa.KaspaTransactionEngine
        val normalRate = engine.TOCCATA_MINIMUM_STANDARD_FEE_RATE_SOMPI_PER_GRAM
        val fastRate = 200L

        val normalFeeBreakdown = engine.calculateFeeBreakdown(
            amountKas = 1.0,
            sompiPerMass = normalRate
        )
        val normalRelayMinimum = engine.calculateToccataMinimumStandardFee(
            inputsCount = 1,
            outputsCount = 2
        )
        val expectedNormalFee = maxOf(
            engine.calculateFeeForMass(normalFeeBreakdown.totalMass, normalRate),
            normalRelayMinimum,
            engine.MINIMUM_WALLET_TRANSACTION_FEE_SOMPIS
        )
        assertEquals(expectedNormalFee, normalFeeBreakdown.feeSompis)
        assertEquals(
            engine.sompiToKas(expectedNormalFee),
            normalFeeBreakdown.feeKas,
            0.00000001
        )

        val fastFeeBreakdown = engine.calculateFeeBreakdown(
            amountKas = 1.0,
            sompiPerMass = fastRate
        )
        val fastRelayMinimum = engine.calculateToccataMinimumStandardFee(
            inputsCount = 1,
            outputsCount = 2
        )
        val expectedFastFee = maxOf(
            engine.calculateFeeForMass(fastFeeBreakdown.totalMass, fastRate),
            fastRelayMinimum,
            engine.MINIMUM_WALLET_TRANSACTION_FEE_SOMPIS
        )
        assertEquals(expectedFastFee, fastFeeBreakdown.feeSompis)
        assertEquals(
            engine.sompiToKas(expectedFastFee),
            fastFeeBreakdown.feeKas,
            0.00000001
        )
        assertTrue("Normal fee must not fall below the configured 0.0056 KAS wallet floor", normalFeeBreakdown.feeSompis >= 560_000L)
        assertTrue("Fast fee must not fall below the configured 0.0056 KAS wallet floor", fastFeeBreakdown.feeSompis >= 560_000L)
        assertEquals("Both tiers may equal the floor when the mass-based fee is lower", maxOf(expectedNormalFee, 560_000L), normalFeeBreakdown.feeSompis)
        assertEquals(maxOf(expectedFastFee, 560_000L), fastFeeBreakdown.feeSompis)
    }

    @Test
    fun testTransactionHistoryFilterTabsLogic() {
        val tx1 = com.example.model.KaspaTransactionItem(
            txId = "tx_sent_1",
            blockTime = 1700000000000L,
            amountKas = 5.0,
            type = "SENT",
            isAccepted = true,
            feeKas = 0.0056,
            counterpartyAddress = "kaspatest:qqqq1"
        )
        val tx2 = com.example.model.KaspaTransactionItem(
            txId = "tx_recv_1",
            blockTime = 1700001000000L,
            amountKas = 10.0,
            type = "RECEIVED",
            isAccepted = true,
            feeKas = 0.0,
            counterpartyAddress = "kaspatest:qqqq2"
        )
        val tx3 = com.example.model.KaspaTransactionItem(
            txId = "tx_sent_2",
            blockTime = 1700002000000L,
            amountKas = 1.5,
            type = "SENT",
            isAccepted = true,
            feeKas = 0.0069,
            counterpartyAddress = "kaspatest:qqqq3"
        )

        val allList = listOf(tx1, tx2, tx3)
        val sentList = allList.filter { it.type == "SENT" }
        val recvList = allList.filter { it.type == "RECEIVED" }

        assertEquals(3, allList.size)
        assertEquals(2, sentList.size)
        assertEquals(1, recvList.size)
        assertTrue(sentList.all { it.type == "SENT" })
        assertTrue(recvList.all { it.type == "RECEIVED" })
        assertEquals("tx_recv_1", recvList.first().txId)
    }
}
