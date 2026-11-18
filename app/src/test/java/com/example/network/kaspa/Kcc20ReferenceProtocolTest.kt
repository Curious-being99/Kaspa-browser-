package com.example.network.kaspa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import com.example.network.Blake3
import org.junit.Test
import java.math.BigInteger

class Kcc20ReferenceProtocolTest {
    private fun state(amount: Long, ownerByte: Byte) = Kcc20ReferenceProtocol.State(
        amount = BigInteger.valueOf(amount),
        owner = ByteArray(32) { ownerByte },
        ownerScheme = 0x00,
        borrowScheme = 0x00,
        borrowGuard = ByteArray(32),
        extensionCommitment = ByteArray(32)
    )

    @Test
    fun stateEncodingMatchesReference145ByteLayout() {
        val encoded = Kcc20ReferenceProtocol.encodeState(state(1000, 0x11))
        assertEquals(145, encoded.size)
        val decoded = Kcc20ReferenceProtocol.decodeState(encoded)
        assertEquals(BigInteger.valueOf(1000), decoded.amount)
        assertEquals(0x00, decoded.ownerScheme)
        assertTrue(decoded.owner.contentEquals(ByteArray(32) { 0x11 }))
        assertEquals(Kcc20ReferenceProtocol.TEMPLATE_HASH, Kcc20ReferenceProtocol.bytesToHex(encoded.copyOfRange(1, 33)))
    }

    @Test
    fun programRebuildMatchesPinnedPublicMintArtifactTemplate() {
        val program = Kcc20ReferenceProtocol.programFor(state(1000, 0x11))
        assertEquals(4031, program.size)
        val decoded = Kcc20ReferenceProtocol.verifyProgram(program)
        assertEquals(BigInteger.valueOf(1000), decoded.amount)
        assertEquals(35, Kcc20ReferenceProtocol.p2shScript(program).size)
    }

    @Test
    fun versionOneTransactionIdCommitsCovenantBindingButNotComputeBudget() {
        val script = "aa20" + "11".repeat(32) + "87"
        val input = KaspaTransactionEngine.KaspaTransactionInput(
            previousOutpoint = KaspaTransactionEngine.KaspaOutpoint("22".repeat(32), 0),
            sequence = 0,
            sigOpCount = 0,
            computeBudget = 14
        )
        val boundOutput = KaspaTransactionEngine.KaspaTransactionOutput(
            amount = 50_000_000,
            scriptPublicKey = KaspaTransactionEngine.KaspaScriptPublicKey(0, script),
            covenant = KaspaTransactionEngine.KaspaCovenantBinding(0, "33".repeat(32))
        )
        val transaction = KaspaTransactionEngine.KaspaTransaction(
            version = 1,
            inputs = listOf(input),
            outputs = listOf(boundOutput)
        )
        val higherBudget = transaction.copy(
            inputs = listOf(input.copy(computeBudget = 32))
        )
        val changedFamily = transaction.copy(
            outputs = listOf(boundOutput.copy(
                covenant = KaspaTransactionEngine.KaspaCovenantBinding(0, "44".repeat(32))
            ))
        )
        assertEquals(
            KaspaTransactionEngine.calcTransactionId(transaction),
            KaspaTransactionEngine.calcTransactionId(higherBudget)
        )
        assertNotEquals(
            KaspaTransactionEngine.calcTransactionId(transaction),
            KaspaTransactionEngine.calcTransactionId(changedFamily)
        )
    }

    @Test
    fun versionOneMassIncludesComputeBudgetsAndCovenantBindings() {
        val input = KaspaTransactionEngine.KaspaTransactionInput(
            previousOutpoint = KaspaTransactionEngine.KaspaOutpoint("55".repeat(32), 0),
            sequence = 0,
            sigOpCount = 0,
            computeBudget = 14
        )
        val script = "aa20" + "66".repeat(32) + "87"
        val unbound = KaspaTransactionEngine.KaspaTransactionOutput(
            50_000_000,
            KaspaTransactionEngine.KaspaScriptPublicKey(0, script)
        )
        val bound = unbound.copy(
            covenant = KaspaTransactionEngine.KaspaCovenantBinding(0, "77".repeat(32))
        )
        val unboundTx = KaspaTransactionEngine.KaspaTransaction(1, listOf(input), listOf(unbound))
        val boundTx = unboundTx.copy(outputs = listOf(bound))
        assertEquals(
            34L,
            KaspaTransactionEngine.calculateSerializedByteSize(boundTx) -
                KaspaTransactionEngine.calculateSerializedByteSize(unboundTx)
        )
        assertTrue(
            KaspaTransactionEngine.calculateReferenceV1ComputeMass(boundTx) >
                KaspaTransactionEngine.calculateSerializedByteSize(boundTx)
        )
    }

    @Test
    fun keyedBlake3MatchesRustyKaspaPayloadDigestVector() {
        assertEquals(
            "9c0ca2acb45e92ffe6ceb4ae29188b35c82d9676cdd3ce067fd6ccc30a9c4a38",
            Kcc20ReferenceProtocol.bytesToHex(Blake3.keyedHash("PayloadDigest", ByteArray(0)))
        )
    }

    @Test
    fun versionOneTransactionIdMatchesRustyKaspaV210Vector() {
        val transaction = KaspaTransactionEngine.KaspaTransaction(
            version = 1,
            inputs = listOf(
                KaspaTransactionEngine.KaspaTransactionInput(
                    previousOutpoint = KaspaTransactionEngine.KaspaOutpoint("00".repeat(32), 0),
                    signatureScript = "",
                    sequence = 0,
                    sigOpCount = 0,
                    computeBudget = 111
                )
            ),
            outputs = emptyList(),
            lockTime = 0,
            subnetworkId = "00".repeat(20),
            gas = 0,
            payload = ""
        )
        assertEquals(
            "5978e7aa1a9ba8fdf12dae6aa39aa198a91985e91192b291e207d4d6246349e6",
            KaspaTransactionEngine.calcTransactionId(transaction)
        )
        assertEquals(
            KaspaTransactionEngine.calcTransactionId(transaction),
            KaspaTransactionEngine.calcTransactionId(
                transaction.copy(inputs = listOf(transaction.inputs.single().copy(computeBudget = 222)))
            )
        )
    }


    @Test
    fun argentAndSilverscriptSourcesMatchPinnedCompiledReferenceArtifact() {
        val context = javaClass
        val fixture = context.getResourceAsStream("/kcc20-reference/artifact-template.json")!!
            .bufferedReader().use { it.readText() }
        fun stringField(name: String): String =
            Regex("\"" + Regex.escape(name) + "\"\\s*:\\s*\"([^\"]*)\"")
                .find(fixture)?.groupValues?.get(1)
                ?: error("Missing string field in pinned KCC20 fixture: $name")
        fun intField(name: String): Int =
            Regex("\"" + Regex.escape(name) + "\"\\s*:\\s*(\\d+)")
                .find(fixture)?.groupValues?.get(1)?.toInt()
                ?: error("Missing integer field in pinned KCC20 fixture: $name")
        val argentSource = context.getResourceAsStream("/kcc20-reference/kcc20.ag")!!
            .bufferedReader().use { it.readText() }
        val silverscript = context.getResourceAsStream("/kcc20-reference/KCC20.sil")!!
            .bufferedReader().use { it.readText() }

        assertEquals(Kcc20ReferenceProtocol.ARTIFACT_ID, stringField("artifact_id"))
        assertEquals(Kcc20ReferenceProtocol.REFERENCE_SOURCE_COMMIT, stringField("source_commit"))
        assertEquals(Kcc20ReferenceProtocol.ARGENT_COMPILER_REVISION, stringField("argent_compiler_revision"))
        assertEquals(Kcc20ReferenceProtocol.TEMPLATE_HASH, stringField("template_hash"))
        assertEquals(145, intField("state_length"))
        assertEquals(4031, intField("program_length"))

        Kcc20ReferenceProtocol.verifyCompilerArtifactTemplate(
            stringField("artifact_id"),
            stringField("prefix_hex"),
            stringField("suffix_hex")
        )

        assertTrue(argentSource.contains("actor KCC20 owns KCC20State"))
        assertTrue(argentSource.contains("entry transfer("))
        assertTrue(argentSource.contains("delegate transfer_delegator("))
        assertTrue(argentSource.contains("require(output_total == input_total)"))
        assertTrue(argentSource.contains("require_owner_authorized(owner, owner_scheme, path_witness)"))

        assertTrue(silverscript.contains("contract KCC20("))
        assertTrue(silverscript.contains("require(OpCovOutputCount(gen__cov_id) == OpAuthOutputCount(this.activeInputIndex))"))
        assertTrue(silverscript.contains("validateOutputState("))
        assertTrue(silverscript.contains("entry transfer_delegator("))
    }

    @Test(expected = IllegalArgumentException::class)
    fun compilerArtifactWithChangedBytecodeIsRejected() {
        val fixture = javaClass.getResourceAsStream("/kcc20-reference/artifact-template.json")!!
            .bufferedReader().use { it.readText() }
        fun stringField(name: String): String =
            Regex("\"" + Regex.escape(name) + "\"\\s*:\\s*\"([^\"]*)\"")
                .find(fixture)?.groupValues?.get(1)
                ?: error("Missing string field in pinned KCC20 fixture: $name")
        fun intField(name: String): Int =
            Regex("\"" + Regex.escape(name) + "\"\\s*:\\s*(\\d+)")
                .find(fixture)?.groupValues?.get(1)?.toInt()
                ?: error("Missing integer field in pinned KCC20 fixture: $name")
        val suffix = stringField("suffix_hex")
        val changed = (if (suffix.startsWith("00")) "01" else "00") + suffix.substring(2)
        Kcc20ReferenceProtocol.verifyCompilerArtifactTemplate(
            stringField("artifact_id"),
            stringField("prefix_hex"),
            changed
        )
    }

}
