package com.example.dapp

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.util.Locale

internal object EvmActivityRepository {
    data class Activity(
        val hash: String,
        val blockNumber: Long,
        val kind: String,
        val from: String,
        val to: String,
        val value: String,
        val status: String,
        val contract: String = ""
    )

    data class Result(
        val latestBlock: Long,
        val scannedFromBlock: Long,
        val items: List<Activity>,
        val warnings: List<String>
    )

    private const val TRANSFER_TOPIC = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"

    fun load(network: EvmWalletEngine.Network, address: String, blockWindow: Long = 100L): Result {
        require(address.matches(Regex("0x[0-9a-fA-F]{40}"))) { "Invalid EVM address" }
        require(blockWindow in 1L..500L) { "History scan window must be between 1 and 500 blocks" }
        val account = address.lowercase(Locale.ROOT)
        val latestHex = EvmWalletEngine.rpc(network, "eth_blockNumber", JSONArray()).optString("result")
        val latest = parseHex(latestHex)
        val fromBlock = (latest - blockWindow + 1).coerceAtLeast(0L)
        val found = LinkedHashMap<String, Activity>()
        val warnings = mutableListOf<String>()

        var block = latest
        while (block >= fromBlock) {
            val blockHex = "0x" + block.toString(16)
            try {
                val result = EvmWalletEngine.rpc(
                    network,
                    "eth_getBlockByNumber",
                    JSONArray().put(blockHex).put(true)
                ).optJSONObject("result")
                if (result != null) {
                    val transactions = result.optJSONArray("transactions") ?: JSONArray()
                    for (index in 0 until transactions.length()) {
                        val tx = transactions.optJSONObject(index) ?: continue
                        val from = tx.optString("from").lowercase(Locale.ROOT)
                        val to = tx.optString("to").lowercase(Locale.ROOT)
                        if (from != account && to != account) continue
                        val hash = tx.optString("hash")
                        if (hash.isBlank()) continue
                        val receipt = try {
                            EvmWalletEngine.rpc(network, "eth_getTransactionReceipt", JSONArray().put(hash))
                                .optJSONObject("result")
                        } catch (_: Exception) {
                            null
                        }
                        val status = when (receipt?.optString("status")) {
                            "0x1" -> "Success"
                            "0x0" -> "Failed"
                            else -> "Pending / receipt unavailable"
                        }
                        found[hash] = Activity(
                            hash = hash,
                            blockNumber = block,
                            kind = if (from == account) "Native sent" else "Native received",
                            from = from,
                            to = to,
                            value = tx.optString("value", "0x0"),
                            status = status
                        )
                    }
                }
            } catch (e: Exception) {
                warnings += "Native transaction scan stopped at block $block: ${e.message ?: "RPC error"}"
                break
            }
            block--
        }

        val topicAddress = "0x" + account.removePrefix("0x").padStart(64, '0')
        val ranges = mutableListOf<Pair<Long, Long>>()
        var start = fromBlock
        while (start <= latest) {
            val end = (start + 499).coerceAtMost(latest)
            ranges += start to end
            start = end + 1
        }
        for ((rangeStart, rangeEnd) in ranges) {
            for (incoming in listOf(false, true)) {
                val topics = JSONArray().put(TRANSFER_TOPIC)
                if (incoming) {
                    topics.put(JSONObject.NULL).put(topicAddress)
                } else {
                    topics.put(topicAddress)
                }
                val filter = JSONObject()
                    .put("fromBlock", "0x" + rangeStart.toString(16))
                    .put("toBlock", "0x" + rangeEnd.toString(16))
                    .put("topics", topics)
                try {
                    val logs = EvmWalletEngine.rpc(network, "eth_getLogs", JSONArray().put(filter))
                        .optJSONArray("result") ?: JSONArray()
                    for (index in 0 until logs.length()) {
                        val log = logs.optJSONObject(index) ?: continue
                        if (log.optBoolean("removed", false)) continue
                        val topicsOut = log.optJSONArray("topics") ?: continue
                        if (topicsOut.length() < 3) continue
                        val from = topicsOut.optString(1).takeLast(40).let { "0x$it" }.lowercase(Locale.ROOT)
                        val to = topicsOut.optString(2).takeLast(40).let { "0x$it" }.lowercase(Locale.ROOT)
                        if (from != account && to != account) continue
                        val hash = log.optString("transactionHash")
                        if (hash.isBlank()) continue
                        val blockNumber = parseHex(log.optString("blockNumber"))
                        val contract = log.optString("address")
                        val logIndex = log.optString("logIndex")
                        val key = "$hash:$logIndex"
                        val isNft = topicsOut.length >= 4
                        found[key] = Activity(
                            hash = hash,
                            blockNumber = blockNumber,
                            kind = when {
                                isNft && from == account -> "NFT sent"
                                isNft -> "NFT received"
                                from == account -> "Token sent"
                                else -> "Token received"
                            },
                            from = from,
                            to = to,
                            value = if (isNft) topicsOut.optString(3) else log.optString("data", "0x0"),
                            status = "Success (transfer event)",
                            contract = contract
                        )
                    }
                } catch (e: Exception) {
                    warnings += "Token/NFT transfer logs unavailable for blocks $rangeStart–$rangeEnd: ${e.message ?: "RPC error"}"
                }
            }
        }
        return Result(
            latestBlock = latest,
            scannedFromBlock = fromBlock,
            items = found.values.sortedWith(compareByDescending<Activity> { it.blockNumber }.thenBy { it.hash }),
            warnings = warnings.distinct()
        )
    }

    private fun parseHex(value: String): Long {
        require(value.startsWith("0x", true) && value.length > 2) { "RPC returned an invalid block number" }
        return BigInteger(value.substring(2), 16).longValueExact()
    }
}
