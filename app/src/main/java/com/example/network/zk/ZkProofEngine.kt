package com.example.network.zk

import com.example.network.CryptoUtils
import org.json.JSONObject
import java.math.BigInteger
import java.security.SecureRandom

/**
 * Mathematical Zero-Knowledge Proof (ZKP) Engine over secp256k1
 *
 * Implements a non-interactive Zero-Knowledge Proof of Knowledge (Sigma Protocol with Fiat-Shamir transform).
 * Proves knowledge of the private key x corresponding to a public key P = x · G
 * without revealing ANY information about x.
 *
 * Soundness: Special Soundness (knowledge extractor extracts witness from two transcripts).
 * Zero-Knowledge: Perfect Zero-Knowledge (simulator produces indistinguishable transcripts without witness).
 */
object ZkProofEngine {

    private val secureRandom = SecureRandom()

    data class ZkProof(
        val statement: String,
        val publicKeyHex: String,
        val commitmentRxHex: String,
        val commitmentRyHex: String,
        val challengeHex: String,
        val responseHex: String,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("protocol", "Schnorr-Sigma-NIZKP-secp256k1")
            put("statement", statement)
            put("publicKeyHex", publicKeyHex)
            put("commitmentRxHex", commitmentRxHex)
            put("commitmentRyHex", commitmentRyHex)
            put("challengeHex", challengeHex)
            put("responseHex", responseHex)
            put("timestamp", timestamp)
        }

        companion object {
            fun fromJson(json: JSONObject): ZkProof {
                return ZkProof(
                    statement = json.optString("statement", ""),
                    publicKeyHex = json.optString("publicKeyHex", ""),
                    commitmentRxHex = json.optString("commitmentRxHex", ""),
                    commitmentRyHex = json.optString("commitmentRyHex", ""),
                    challengeHex = json.optString("challengeHex", ""),
                    responseHex = json.optString("responseHex", ""),
                    timestamp = json.optLong("timestamp", System.currentTimeMillis())
                )
            }
        }
    }

    data class ZkVerificationResult(
        val isValid: Boolean,
        val message: String,
        val verifiedAt: Long = System.currentTimeMillis()
    )

    /**
     * Generates a non-interactive Zero-Knowledge Proof of Knowledge (ZKP)
     * for private key [privateKey] binding it to [statement].
     */
    fun generateZkProof(privateKey: BigInteger, statement: String): ZkProof {
        val n = CryptoUtils.SECP256K1_N
        val d = privateKey.mod(n)
        require(d > BigInteger.ZERO) { "Private key must be non-zero" }

        // 1. Compute public key P = d · G
        val pubPoint = CryptoUtils.pointMultiply(d, CryptoUtils.ECPoint.G)
        val pubKeyHex = CryptoUtils.to32ByteArray(pubPoint.x).joinToString("") { "%02x".format(it) }

        // 2. Prover picks random ephemeral scalar v in [1, n-1]
        var v: BigInteger
        do {
            val vBytes = ByteArray(32)
            secureRandom.nextBytes(vBytes)
            v = BigInteger(1, vBytes).mod(n)
        } while (v <= BigInteger.ZERO)

        // 3. Prover computes commitment point R = v · G
        val commitmentR = CryptoUtils.pointMultiply(v, CryptoUtils.ECPoint.G)
        val rxHex = CryptoUtils.to32ByteArray(commitmentR.x).joinToString("") { "%02x".format(it) }
        val ryHex = CryptoUtils.to32ByteArray(commitmentR.y).joinToString("") { "%02x".format(it) }

        // 4. Fiat-Shamir non-interactive challenge: e = H(G || P || R || statement) mod n
        val challenge = computeFiatShamirChallenge(pubPoint, commitmentR, statement)
        val challengeHex = CryptoUtils.to32ByteArray(challenge).joinToString("") { "%02x".format(it) }

        // 5. Prover computes response scalar: s = (v + e · d) mod n
        val s = v.add(challenge.multiply(d)).mod(n)
        val responseHex = CryptoUtils.to32ByteArray(s).joinToString("") { "%02x".format(it) }

        return ZkProof(
            statement = statement,
            publicKeyHex = pubKeyHex,
            commitmentRxHex = rxHex,
            commitmentRyHex = ryHex,
            challengeHex = challengeHex,
            responseHex = responseHex
        )
    }

    /**
     * Cryptographically verifies the Zero-Knowledge Proof:
     * Checks: s · G == R + e · P
     */
    fun verifyZkProof(proof: ZkProof): ZkVerificationResult {
        return try {
            val n = CryptoUtils.SECP256K1_N
            val p = CryptoUtils.SECP256K1_P

            // 1. Reconstruct public key point P
            val px = BigInteger(proof.publicKeyHex.removePrefix("0x"), 16)
            val pubPoint = derivePointFromX(px)
                ?: return ZkVerificationResult(false, "Invalid public key: point does not lie on secp256k1 curve")

            // 2. Reconstruct commitment point R
            val rx = BigInteger(proof.commitmentRxHex.removePrefix("0x"), 16)
            val ry = BigInteger(proof.commitmentRyHex.removePrefix("0x"), 16)
            val commitmentR = CryptoUtils.ECPoint(rx, ry)

            // Verify R is on the curve: y^2 = x^3 + 7 mod p
            val lhsR = ry.multiply(ry).mod(p)
            val rhsR = rx.pow(3).add(BigInteger.valueOf(7)).mod(p)
            if (lhsR != rhsR) {
                return ZkVerificationResult(false, "Commitment point R is not on secp256k1 curve")
            }

            // 3. Recompute Fiat-Shamir challenge: e' = H(G || P || R || statement) mod n
            val expectedChallenge = computeFiatShamirChallenge(pubPoint, commitmentR, proof.statement)
            val actualChallenge = BigInteger(proof.challengeHex.removePrefix("0x"), 16)
            if (expectedChallenge != actualChallenge) {
                return ZkVerificationResult(false, "Challenge mismatch: Fiat-Shamir transcript hash does not match")
            }

            // 4. Verify Schnorr Zero-Knowledge equation: s · G == R + e · P
            val s = BigInteger(proof.responseHex.removePrefix("0x"), 16)
            val lhs = CryptoUtils.pointMultiply(s, CryptoUtils.ECPoint.G)
            val eP = CryptoUtils.pointMultiply(expectedChallenge, pubPoint)
            val rhs = CryptoUtils.pointAdd(commitmentR, eP)

            if (lhs.x == rhs.x && lhs.y == rhs.y) {
                ZkVerificationResult(
                    isValid = true,
                    message = "Valid Zero-Knowledge Proof (LHS: s·G == RHS: R + e·P on secp256k1)"
                )
            } else {
                ZkVerificationResult(
                    isValid = false,
                    message = "ZK Verification failed: s·G != R + e·P"
                )
            }
        } catch (e: Exception) {
            ZkVerificationResult(false, "Verification error: ${e.message}")
        }
    }

    /**
     * Generates a Zero-Knowledge Proof for a Google Account Decentralized Bridge
     */
    fun generateGoogleBridgeZkProof(
        privateKey: BigInteger,
        googleEmail: String,
        did: String
    ): ZkProof {
        val statement = "zk-bridge:google:${googleEmail.trim().lowercase()}|did:$did|network:decentralnet"
        return generateZkProof(privateKey, statement)
    }

    private fun computeFiatShamirChallenge(
        pubKeyPoint: CryptoUtils.ECPoint,
        commitmentR: CryptoUtils.ECPoint,
        statement: String
    ): BigInteger {
        val n = CryptoUtils.SECP256K1_N
        val pBytes = CryptoUtils.to32ByteArray(pubKeyPoint.x)
        val rxBytes = CryptoUtils.to32ByteArray(commitmentR.x)
        val ryBytes = CryptoUtils.to32ByteArray(commitmentR.y)
        val stmtBytes = statement.toByteArray(Charsets.UTF_8)

        val transcript = pBytes + rxBytes + ryBytes + stmtBytes
        val hash = CryptoUtils.blake2b256(transcript, "ZkProofChallenge".toByteArray(Charsets.UTF_8))
        var challenge = BigInteger(1, hash).mod(n)
        if (challenge == BigInteger.ZERO) {
            challenge = BigInteger.ONE
        }
        return challenge
    }

    private fun derivePointFromX(px: BigInteger): CryptoUtils.ECPoint? {
        val p = CryptoUtils.SECP256K1_P
        val ySquared = px.pow(3).add(BigInteger.valueOf(7)).mod(p)
        val pPlus1Over4 = p.add(BigInteger.ONE).shiftRight(2)
        var py = ySquared.modPow(pPlus1Over4, p)
        if (py.multiply(py).mod(p) != ySquared) return null
        if (py.testBit(0)) {
            py = p.subtract(py)
        }
        return CryptoUtils.ECPoint(px, py)
    }
}
