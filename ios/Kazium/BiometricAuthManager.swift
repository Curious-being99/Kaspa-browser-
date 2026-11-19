import Foundation
import LocalAuthentication

/// Kazium iOS Hardware Biometric Vault Manager
/// Manages Face ID and Touch ID authentication backed by Apple's Secure Enclave.
class BiometricAuthManager: ObservableObject {
    @Published var isUnlocked: Bool = false
    @Published var biometryType: LABiometryType = .none
    @Published var authError: String?

    init() {
        checkBiometricAvailability()
    }

    /// Checks whether Face ID, Touch ID, or Optic ID is available on this Apple device
    func checkBiometricAvailability() {
        let context = LAContext()
        var error: NSError?

        if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) {
            self.biometryType = context.biometryType
        } else {
            self.biometryType = .none
        }
    }

    /// Prompts the user with Face ID / Touch ID to unlock Kaspa wallet keys and vault credentials
    func authenticate(reason: String = "Unlock Kazium Vault & Kaspa Wallet", completion: @escaping (Bool) -> Void) {
        let context = LAContext()
        context.localizedCancelTitle = "Cancel"
        var error: NSError?

        // 1. First attempt biometric authentication (Face ID / Touch ID)
        if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) {
            context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason) { success, authenticationError in
                DispatchQueue.main.async {
                    if success {
                        self.isUnlocked = true
                        self.authError = nil
                        completion(true)
                    } else {
                        self.authError = authenticationError?.localizedDescription
                        completion(false)
                    }
                }
            }
        } 
        // 2. Safe fallback to device passcode if biometrics are not enrolled or locked
        else if context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) {
            context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason) { success, passcodeError in
                DispatchQueue.main.async {
                    if success {
                        self.isUnlocked = true
                        self.authError = nil
                        completion(true)
                    } else {
                        self.authError = passcodeError?.localizedDescription
                        completion(false)
                    }
                }
            }
        } else {
            DispatchQueue.main.async {
                self.authError = "Biometric authentication is not supported or enabled on this device."
                completion(false)
            }
        }
    }

    /// Locks the vault and clears unlocked session state
    func lock() {
        DispatchQueue.main.async {
            self.isUnlocked = false
        }
    }
}
