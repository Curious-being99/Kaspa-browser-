# KaspaBrowser 🚀
### Next-Generation Decentralized Android Web Browser & Mesh-Integrated Ecosystem

**KaspaBrowser** is an Android web browser and decentralized network gateway built with **Kotlin** and **Jetpack Compose (Material 3)**. It unifies standard Web browsing, decentralized peer-to-peer mesh discovery, cryptographic identity management, on-device local node hosting, and native **Kaspa BlockDAG (KAS)** wallet utilities into a streamlined mobile client.

---

## 🌟 Key Features

### 🌐 1. Dual-Stack Gateway & Web Browser
- **Unified Protocol Handler**: Seamlessly navigates standard web addresses (`http://`, `https://`) and decentralized Web3 protocols (`ipfs://`, `hyper://`, `.kas`, `.mesh`, and custom local peer endpoints).
- **Dual-Stack DNS & Mesh Resolver**: Automatically resolves local and peer nodes on local mesh networks before routing out to public gateways.
- **Modern Web Engine**: Powered by an integrated Android WebView equipped with safe file picker fallbacks, cookie isolation, progressive Web App (PWA) manifest auto-detection, and customizable desktop/mobile User Agents.
- **Offline & Cache Mode**: Offline browsing cache with granular cache-clearing controls.

### 🛡️ 2. Cryptographic Identity & Key Management
- **Decentralized Accounts**: Create, import, and manage hierarchical deterministic (HD) seed phrases and keypairs.
- **Ed25519 & Secp256k1 Cryptography**: Hardware-backed key derivation with cryptographic hashing (SHA-256, Blake2b).
- **Account Backup & Export**: Safe export of private keys and recovery phrases with PIN and biometric protection.

### 💰 3. Native Kaspa BlockDAG (KAS) Wallet
- **Real-Time DAG Explorer & Balances**: Query live UTXO balances, sompis denominations, and transaction histories across mainnet and testnet endpoints.
- **Fast Transactions**: Compose, sign, and broadcast KAS transactions directly to public and local Kaspa nodes.
- **QR Code Scanner & Address Validation**: Built-in address syntax validator for `kaspa:` and `kaspatest:` bech32 formats.

### 📡 4. P2P Mesh Radar & Local Node Hosting
- **Live Mesh Radar**: Discovers nearby nodes, peers, and local web services over Wi-Fi Direct, Local Area Networks (LAN), and NSD (Network Service Discovery).
- **On-Device Micro Daemon**: Host lightweight local web pages, decentralized documents, or P2P data packets directly from your phone.
- **Peer Latency & Routing Metrics**: Real-time ping, hops, bandwidth consumption, and signal strength auditing.

### 📊 5. Real-Time Traffic Audit & Privacy Shield
- **Data Usage Analytics**: Monitor upstream and downstream bandwidth in real time.
- **Tracker & Script Protection**: Built-in content shielding to mitigate intrusive tracking and reduce mobile data overhead.
- **Live Traffic Logs**: Detailed ledger of intercepted network calls, DNS queries, and decentralized gateway lookups.

### 📱 6. PWA Launcher & Desktop Integration
- **Zero-Install Web Apps**: Pin decentralized dApps and standard web apps directly to the Android Home Screen using Android Pin Shortcut APIs.
- **Dynamic Icons & Standalone Viewing**: Launches installed web apps in dedicated immersive views.

---

## 🏗️ Project Architecture & Structure

The codebase is engineered following modern Android architecture guidelines (**MVVM**, **Unidirectional Data Flow**, and **Clean Architecture**):

```
├── .github/
│   └── workflows/
│       └── release.yml          # Automated CI/CD for signed/unsigned Release APK publishing
├── app/
│   ├── build.gradle.kts         # Module build script (Compile SDK 36, Target SDK 36, Min SDK 26)
│   ├── proguard-rules.pro       # R8/ProGuard rules for serialization, Room, and coroutines
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/example/
│           │   ├── MainActivity.kt               # Main entry point & Edge-to-Edge setup
│           │   ├── data/
│           │   │   ├── AppDatabase.kt            # Room database definition
│           │   │   ├── Daos.kt                   # Data Access Objects (History, Bookmarks, Nodes)
│           │   │   └── Entities.kt               # SQLite database entities
│           │   ├── model/
│           │   │   ├── KaspaWalletModels.kt      # KAS UTXO, transaction, and balance models
│           │   │   └── NetworkModels.kt          # Mesh, node, peer, and traffic packet models
│           │   ├── network/
│           │   │   ├── CryptoUtils.kt            # Key generation, hashing, and hex tools
│           │   │   ├── DomainConstants.kt        # Default seed nodes, public APIs, and gateways
│           │   │   ├── DualStackResolver.kt      # DNS and decentralized protocol resolver
│           │   │   ├── KaspaWalletService.kt     # Kaspa REST/RPC service client
│           │   │   ├── LocalNodeManager.kt       # On-device micro-daemon and routing logic
│           │   │   ├── NetworkDiscoveryManager.kt# LAN/NSD/Mesh peer discovery engine
│           │   │   └── PwaShortcutHelper.kt      # Home screen shortcut & launcher manager
│           │   ├── ui/
│           │   │   ├── BrowserGatewayScreen.kt   # Dual-stack WebView browser & tab manager
│           │   │   ├── DecentralizedAccountDialog.kt # Key management & Seed dialogs
│           │   │   ├── KaspaWalletView.kt        # Kaspa wallet balance, send, and receive UI
│           │   │   ├── MainScreen.kt             # Navigation bar, Scaffold, and root view
│           │   │   ├── MeshRadarScreen.kt        # Interactive P2P visual radar & peer map
│           │   │   ├── PwaInstallDialog.kt       # PWA confirmation & icon dialog
│           │   │   ├── TrafficAuditScreen.kt     # Network inspector & privacy metrics
│           │   │   └── theme/                    # Material 3 dark-first Cyberpunk theme
│           │   └── viewmodel/
│           │       └── DecentralViewModel.kt     # Unified reactive state manager
│           └── res/
│               ├── drawable/                     # Adaptive icons, vectors, and graphics
│               └── values/                       # Strings, colors, and themes
└── gradle/
    └── libs.versions.toml       # Gradle Version Catalog
```

---

## 🛠️ Technology Stack

| Layer | Technology |
|---|---|
| **Language** | [Kotlin 2.0+](https://kotlinlang.org/) |
| **UI Framework** | [Jetpack Compose (Material 3)](https://developer.android.com/jetpack/compose) |
| **Target OS** | Android 16 (API 36) / Minimum Android 8.0 (API 26) |
| **Asynchronous Engine** | Kotlin Coroutines & `StateFlow` |
| **Local Persistence** | [AndroidX Room Database](https://developer.android.com/training/data-storage/room) |
| **Networking** | OkHttp3, Ktor, and Android NSD |
| **Serialization** | `kotlinx.serialization` (JSON) |
| **Code Shrinking & Security** | ProGuard / R8 optimized rules |
| **CI/CD Build Automation** | GitHub Actions (`actions/setup-java@v5`, JDK 21) |

---

## 🚀 Building & Publishing

### Local Compilation
To compile the debug version using Gradle:
```bash
gradle :app:assembleDebug
```

To build a release bundle locally:
```bash
gradle :app:assembleRelease
```

### Automated GitHub Release Action
Pushing any version tag (e.g., `v1.0.0`) automatically triggers `.github/workflows/release.yml`:
- **If Keystore Secrets are configured** in GitHub (`RELEASE_KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`, `RELEASE_KEY_ALIAS`), it builds and signs `KaspaBrowser-release-signed.apk` with `apksigner` (v1/v2/v3 schemes) and attaches it to the GitHub Release.
- **If Secrets are absent**, it builds `KaspaBrowser-release-unsigned.apk` and publishes it to the GitHub Release.

---

## 📄 License
Open source under the [Apache License 2.0](LICENSE).
