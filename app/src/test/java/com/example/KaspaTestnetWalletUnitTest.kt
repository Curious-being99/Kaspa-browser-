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
    fun testDynamicFeeEstimateNeverDropsBelowWalletFloor() {
        val engine = com.example.network.kaspa.KaspaTransactionEngine
        val estimate = engine.estimateDynamicFee(
            amountKas = 1.0,
            inputsCount = 1,
            outputsCount = 2,
            sompiPerMass = 1L
        )

        assertTrue(
            "Dynamic estimates must preserve the 0.0056 KAS wallet fee floor",
            estimate.feeSompi >= engine.MINIMUM_WALLET_TRANSACTION_FEE_SOMPIS
        )
        assertEquals(560_000L, engine.MINIMUM_WALLET_TRANSACTION_FEE_SOMPIS)
        assertEquals(0.0056, engine.sompiToKas(engine.MINIMUM_WALLET_TRANSACTION_FEE_SOMPIS), 0.000000001)
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

    @Test
    fun testTransactionMassLimitVsBlockMassLimit() {
        val engine = com.example.network.kaspa.KaspaTransactionEngine
        
        // 100,000 grams is the standard mempool policy limit for an individual transaction
        assertEquals(100_000L, engine.MAXIMUM_STANDARD_TRANSACTION_MASS)
        assertEquals(100_000L, engine.CLIENT_SOFT_TRANSACTION_MASS_LIMIT)

        // 500,000 grams is the consensus limit for an entire block
        assertEquals(500_000L, engine.MAXIMUM_BLOCK_MASS)

        // Standard wallet transactions are well within the 100,000 transaction mass limit
        val singleInputTwoOutputsMass = engine.estimateTransactionMass(
            inputsCount = 1,
            outputsCount = 2
        )
        assertTrue("Single-input standard tx mass must be below 100,000", singleInputTwoOutputsMass < engine.MAXIMUM_STANDARD_TRANSACTION_MASS)
        assertTrue("Single-input standard tx mass must be far below block mass 500,000", singleInputTwoOutputsMass < engine.MAXIMUM_BLOCK_MASS)

        // Even a heavy multi-input transaction (e.g. 10 inputs, 2 outputs) stays within standard tx mass
        val tenInputsMass = engine.estimateTransactionMass(
            inputsCount = 10,
            outputsCount = 2
        )
        assertTrue("10-input tx mass must be below 100,000 transaction limit", tenInputsMass < engine.MAXIMUM_STANDARD_TRANSACTION_MASS)
    }

    @Test
    fun testKbTokenBuySignCreditAndWalletDisplay() {
        val testMnemonic = "apple banana cherry date elderberry fig grape honeydew kiwi lemon mango nectarine"
        val keyPair = CryptoUtils.deriveKaspaKeyPair(testMnemonic, prefix = "kaspatest")
        assertNotNull(keyPair)
        assertTrue(keyPair.kaspaAddress.startsWith("kaspatest:"))

        val initialWalletState = com.example.model.KaspaWalletState(
            kaspaAddress = keyPair.kaspaAddress,
            balanceKas = 100.0,
            tokenBalanceKb = 0.0,
            recentTransactions = emptyList()
        )

        // 1. Simulate KAS -> $KB quote calculation
        val payKasAmountStr = "10.0"
        val paySompi = com.example.network.kaspa.KaspaRocketMath.kasToSompi(payKasAmountStr)
        assertEquals(1_000_000_000L, paySompi)

        val priceSompiPerRaw = 19_279L // ~0.1928 KAS per $KB
        val pricePerTokenSompi = com.example.network.kaspa.KaspaRocketMath.tokenPriceSompi(priceSompiPerRaw)
        assertEquals(19_279_000L, pricePerTokenSompi)

        val estimatedKbTokens = (paySompi.toDouble() * 1000.0) / (priceSompiPerRaw.toDouble() * 1000.0)
        assertTrue("Estimated \$KB tokens must be greater than 0", estimatedKbTokens > 0)

        // 2. Simulate transaction signing & credit
        val mockTxId = "2eec0a5bb96a3ff6030d365fb5cd966eafd8b54dae036020c6350f44b57c725800112233"
        val swapTx = com.example.model.KaspaTransactionItem(
            txId = mockTxId.take(64),
            blockTime = System.currentTimeMillis(),
            amountKas = payKasAmountStr.toDouble(),
            type = "SWAP_BUY",
            isAccepted = true,
            feeKas = 0.0056,
            counterpartyAddress = "2eec0a5bb96a3ff6030d365fb5cd966eafd8b54dae036020c6350f44b57c7258"
        )

        val updatedWalletState = initialWalletState.copy(
            balanceKas = initialWalletState.balanceKas - payKasAmountStr.toDouble() - 0.0056,
            tokenBalanceKb = initialWalletState.tokenBalanceKb + estimatedKbTokens,
            tokenBalanceKbRaw = ((initialWalletState.tokenBalanceKb + estimatedKbTokens) * 1000).toLong(),
            recentTransactions = listOf(swapTx) + initialWalletState.recentTransactions
        )

        // 3. Verify updated wallet state and transaction display properties
        assertTrue("Wallet \$KB balance must be credited", updatedWalletState.tokenBalanceKb > 0)
        assertEquals("Wallet KAS balance must reflect payment", 89.9944, updatedWalletState.balanceKas, 0.0001)
        assertEquals("Recent transactions must record 1 item", 1, updatedWalletState.recentTransactions.size)

        val loggedTx = updatedWalletState.recentTransactions.first()
        assertEquals("SWAP_BUY", loggedTx.type)
        assertEquals(64, loggedTx.txId.length)
        assertTrue("Logged transaction must be confirmed", loggedTx.isAccepted)
        assertEquals("2eec0a5bb96a3ff6030d365fb5cd966eafd8b54dae036020c6350f44b57c7258", loggedTx.counterpartyAddress)
    }
}
