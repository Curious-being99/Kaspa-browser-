import Foundation

/// Kaspa Wallet & KCC-20 Transaction Bridge for iOS
/// Connects the iOS client to the shared wallet protocol with Face ID authorization.
class KaspaWalletBridge: ObservableObject {
    @Published var lastError: String?

    /// Validates whether an address string is a valid Kaspa mainnet/testnet address
    func isValidAddress(_ address: String) -> Bool {
        let trimmed = address.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 61 && trimmed.count <= 70 else { return false }
        return trimmed.hasPrefix("kaspa:") || trimmed.hasPrefix("kaspatest:") ||
               trimmed.hasPrefix("kaspadev:") || trimmed.hasPrefix("kaspasim:")
    }

    /// Builds the standard KCC-20 covenant JSON transfer payload
    func buildKcc20Payload(token: String, amount: String, recipient: String) -> String? {
        guard isValidAddress(recipient) else {
            self.lastError = "Invalid recipient address"
            return nil
        }

        let payloadDict: [String: Any] = [
            "p": "kcc-20",
            "op": "transfer",
            "tick": token.trimmingCharacters(in: .whitespacesAndNewlines).uppercased(),
            "amt": amount.trimmingCharacters(in: .whitespacesAndNewlines),
            "to": recipient.trimmingCharacters(in: .whitespacesAndNewlines)
        ]

        guard let jsonData = try? JSONSerialization.data(withJSONObject: payloadDict, options: []),
              let jsonString = String(data: jsonData, encoding: .utf8) else {
            self.lastError = "Failed to encode KCC-20 payload"
            return nil
        }

        return jsonString
    }

    /// Formats raw sompi amount into human-readable KAS decimal
    func formatSompi(_ sompi: UInt64) -> String {
        let kas = sompi / 100_000_000
        let remainder = sompi % 100_000_000
        return String(format: "%llu.%08llu KAS", kas, remainder)
    }

    /// Executes a biometric-gated wallet transaction
    func sendWithBiometrics(
        biometricManager: BiometricAuthManager,
        reason: String,
        onSuccess: @escaping () -> Void,
        onFailure: @escaping (String) -> Void
    ) {
        biometricManager.authenticate(reason: reason) { success in
            if success {
                onSuccess()
            } else {
                onFailure(biometricManager.authError ?? "Biometric authorization failed")
            }
        }
    }
}
