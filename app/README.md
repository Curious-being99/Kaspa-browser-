# DecentralNet Browser

DecentralNet is a modern, fully decentralized Android web browser built with **Jetpack Compose**, **Kotlin**, and a **Dual-Stack P2P Gateway Architecture**. It serves standard `http://` and `https://` web content without relying on centralized web servers, traditional ICANN DNS, or single points of failure.

---

## 🛠️ Browser Engine Architecture

The browser operates using a multi-layered hybrid engine stack:

| Component | Engine / Technology | Description |
| :--- | :--- | :--- |
| **Rendering & JS Engine** | **Chromium Blink & V8 Engine** | Native Android Chromium container supporting full HTML5, WebAssembly, WebGL, WebRTC, CSS3, and ES2024 JavaScript execution. |
| **Protocol Routing Engine** | **`DualStackResolver` Engine** | Custom Kotlin network gateway intercepting requests and routing between P2P DHT swarms, blockchain resolution, and HTTPS fallback. |
| **P2P Storage Engine** | **IPFS / Kaspa DNet & DHT** | Content-Addressed Storage engine retrieving files by cryptographic hashes (CIDs: `bafy...`, `Qm...`). |
| **Decentralized DNS Engine** | **Handshake (HNS), KNS & ENS** | On-chain resolution resolving `.kas`, `.hns`, `.eth`, `.coin`, and `.bit` domains directly from distributed ledgers. |
| **Cryptographic Verification** | **SHA-256 Digest Engine** | In-browser SHA-256 hashing certifying data streams as `VERIFIED_TAMPER_PROOF`. |
| **Local Mesh Seeding Engine** | **Android Room / SQLite (`AppDatabase`)** | Indexed local database storing CID blocks for peer-to-peer mesh seeding and offline server outage protection. |

### 📐 System Architecture & Request Data Flow

```
+-------------------------------------------------------------------------+
|                    Android Chromium & V8 WebView UI                     |
|           (Renders HTML5, WebAssembly, WebGL & Javascript)             |
+------------------------------------+------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|                  DualStackResolver (Protocol Gateway)                   |
+------------------+------------------+------------------+----------------+
                   |                  |                  |
                   v                  v                  v
+------------------+----+   +---------+--------+   +-----+----------------+
| Handshake / KNS / ENS |   |  P2P Swarm & DHT |   | Direct HTTPS Server  |
| Dec. Name Resolution  |   | Content-Addressed|   |   (Web2 Fallback)    |
|   (HNS / DAG / DoH)   |   | Storage (CID)    |   |                      |
+------------------+----+   +---------+--------+   +-----+----------------+
                   |                  |                  |
                   +------------------+------------------+
                                      |
                                      v
+-------------------------------------------------------------------------+
|                 Local SHA-256 Cryptographic Verification                |
|           (Verifies payload hash matches CID: VERIFIED_TAMPER_PROOF)    |
+------------------------------------+------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|               Android Room Database (`ContentDao` Seeder)               |
|            (Indexes content block locally with isSeeding = true)        |
+-------------------------------------------------------------------------+
```

---

## 📂 Project Directory Structure

```
.
├── README.md                           # GitHub Repository Documentation
├── build.gradle.kts                    # Root Gradle Configuration
├── proguard-rules.pro                  # Security & Shrinking Rules
└── src
    └── main
        ├── AndroidManifest.xml         # Android Permissions & Activity Declarations
        ├── java
        │   └── com
        │       └── example
        │           ├── MainActivity.kt # Entry point activity with Edge-to-Edge window setup
        │           ├── data
        │           │   ├── AppDatabase.kt   # Room Database for P2P CID content & peer nodes
        │           │   ├── Daos.kt          # DAOs for Content, Peers, Accounts, Traffic Audits
        │           │   └── Entities.kt      # ContentEntity, PeerEntity, TrafficAuditEntity
        │           ├── network
        │           │   ├── CryptoUtils.kt               # SHA-256 & CID block hashing
        │           │   ├── DomainConstants.kt           # Custom TLD definitions
        │           │   ├── DualStackResolver.kt         # Core P2P / DoH / Handshake / KNS engine
        │           │   ├── KaspaPrivacyEngine.kt        # Privacy shield & anti-tracking headers
        │           │   ├── KaspaWalletService.kt        # Kaspa RPC node client
        │           │   ├── LocalNodeManager.kt          # P2P daemon node manager
        │           │   └── NetworkDiscoveryManager.kt   # Peer discovery & mesh routing
        │           ├── ui
        │           │   ├── BrowserGatewayScreen.kt      # Main Browser UI & Chromium WebView container
        │           │   └── theme                        # Material 3 Design Theme & Typography
        │           └── viewmodel
        │               └── DecentralViewModel.kt        # State Management & Network Orchestration
        └── res                         # App Icons, Strings, Drawables, and Layout Resources
```

---

## 🌐 How `http://` and `https://` Function

When navigating to web addresses like `https://current.kas`, `http://welcome.hns`, or `https://vitalik.eth`:

1. **Decentralized Domain Resolution (No Central DNS)**:
   - The browser queries **Handshake (HNS)**, **Kaspa Block DAG Ledger (KNS)**, **ENS (.eth)**, or **OpenNIC** directly.
   - The domain maps directly to a **Content Identifier (CID Hash)** like `bafybeic...`.

2. **P2P Content Delivery (No Central Hosting)**:
   - Pages (HTML, CSS, JS, images) are pulled from a **Distributed Hash Table (DHT)** peer swarm rather than a single IP address.

3. **In-Browser Tamper Verification**:
   - The browser computes a **SHA-256 hash** of incoming payloads locally and verifies it against the signed CID hash.
   - Any tampered payload is instantly rejected (`TAMPERED_HASH_MISMATCH`).

4. **Local Mesh Seeding**:
   - Downloaded CID blocks are indexed into the device's local database with `isSeeding = true`, making your device an active seed node for nearby peers.

---

## 🔒 Supported Protocols & TLDs

- **Kaspa Name Service (KNS)**: `.kas`, `.kns`
- **Handshake Protocol (HNS)**: `.hns`, `.forever`, `.p2p`, `.caza`, `.crypto`, `.nb`
- **Ethereum Name Service (ENS)**: `.eth`
- **EmerDNS / OpenNIC**: `.coin`, `.emc`, `.lib`, `.bazar`, `.geek`, `.libre`, `.pirate`, `.oss`, `.bit`
- **Cryptographic P2P CIDs**: `ipfs://`, `kas://`, `mesh://`, `dweb://`, `bafy...`, `Qm...`
- **Standard Web Fallback**: `https://` and `http://` with local P2P mesh fallback on outage.

---

## ⚙️ Building & Running

1. **Clone or Download Repository**:
   - Clone this repository or download the project files.
2. **Build APK**:
   - Execute `./gradlew assembleDebug` to compile and build the APK.
