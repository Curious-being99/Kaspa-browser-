package com.example.dapp

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvmWalletEngineTest {
    @Test
    fun publishesOnlyTheTwoSupportedMainnetChains() {
        assertEquals(202555L, EvmWalletEngine.network(202555L).chainId)
        assertEquals(38833L, EvmWalletEngine.network(38833L).chainId)
        assertEquals("0x3173b", EvmWalletEngine.network(202555L).chainIdHex)
        assertEquals("0x97b1", EvmWalletEngine.network(38833L).chainIdHex)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownChainIds() {
        EvmWalletEngine.network(1L)
    }

    @Test
    fun derivesStandardBip39EthereumAccountAtLedgerCompatiblePath() {
        val mnemonic = "test test test test test test test test test test test junk"
        assertEquals("0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266", EvmWalletEngine.address(mnemonic))
    }

    @Test
    fun signsLegacyTransactionWithSelectedChainId() {
        val mnemonic = "test test test test test test test test test test test junk"
        val tx = JSONObject()
            .put("from", "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266")
            .put("to", "0x0000000000000000000000000000000000000001")
            .put("nonce", "0x0")
            .put("gas", "0x5208")
            .put("gasPrice", "0x3b9aca00")
            .put("value", "0x1")
            .put("data", "0x")
        val signed = EvmWalletEngine.signTransaction(
            mnemonic,
            EvmWalletEngine.network(202555L),
            tx
        )
        assertTrue(signed.startsWith("0x"))
        assertTrue(signed.length > 100)
    }

    @Test
    fun signsEip712TypedData() {
        val mnemonic = "test test test test test test test test test test test junk"
        val typedData = """{"types":{"EIP712Domain":[{"name":"name","type":"string"},{"name":"version","type":"string"},{"name":"chainId","type":"uint256"}],"Mail":[{"name":"contents","type":"string"}]},"primaryType":"Mail","domain":{"name":"Example","version":"1","chainId":202555},"message":{"contents":"hello"}}"""
        val signature = EvmWalletEngine.signTypedData(mnemonic, typedData)
        assertTrue(signature.startsWith("0x"))
        assertEquals(132, signature.length)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTransactionForDifferentChain() {
        val tx = JSONObject()
            .put("chainId", "0x1")
            .put("to", "0x0000000000000000000000000000000000000001")
            .put("nonce", "0x0")
            .put("gas", "0x5208")
            .put("gasPrice", "0x3b9aca00")
        EvmWalletEngine.signTransaction(
            "test test test test test test test test test test test junk",
            EvmWalletEngine.network(202555L),
            tx
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSenderThatDoesNotMatchDerivedAccount() {
        val tx = JSONObject()
            .put("from", "0x0000000000000000000000000000000000000001")
            .put("to", "0x0000000000000000000000000000000000000002")
            .put("nonce", "0x0")
            .put("gas", "0x5208")
            .put("gasPrice", "0x3b9aca00")
        EvmWalletEngine.signTransaction(
            "test test test test test test test test test test test junk",
            EvmWalletEngine.network(38833L),
            tx
        )
    }
}
