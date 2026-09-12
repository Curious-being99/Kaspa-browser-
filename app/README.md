# DecentralNet Browser

DecentralNet is a modern Android browser built with **Jetpack Compose**, **Kotlin**, and a **Dual-Stack P2P Architecture**. It serves standard `http://` and `https://` web content without relying on centralized web servers, traditional DNS, or single points of failure.

---

## 🛠️ Browser Engine Architecture

The browser operates using a multi-layered hybrid engine stack:

| Component | Engine / Technology | Description |
| :--- | :--- | :--- |
| **Rendering & JS Engine** | **Chromium Blink & V8 Engine** | Native Android Chromium container supporting full HTML5, WebGL, WebRTC, CSS3, and ES2024 JavaScript. |
| **Protocol Routing Engine** | **`DualStackResolver` Engine** | Custom Kotlin network gateway intercepting requests and routing between P2P DHT swarms and HTTPS fallback. |
| **P2P Storage Engine** | **IPFS / Kaspa DNet & DHT** | Content-Addressed Storage engine retrieving files by cryptographic hashes (CIDs: `bafy...`, `Qm...`). |
| **Decentralized DNS Engine** | **Handshake (HNS) & Kaspa KNS** | On-chain resolution resolving `.kas`, `.hns`, `.eth`, `.coin`, and `.bit` domains directly from distributed ledgers. |
| **Cryptographic Verification** | **SHA-256 Digest Engine** | In-browser SHA-256 hashing certifying data streams as `VERIFIED_TAMPER_PROOF`. |
| **Local Mesh Seeding Engine** | **Android Room / SQLite (`AppDatabase`)** | Indexed local database storing CID blocks for peer-to-peer mesh seeding and offline server outage protection. |

---

## 🌐 How `http://` and `https://` Function

When navigating to web addresses like `https://current.kas` or `http://mydev.kas`:

1. **Decentralized Domain Resolution (No Central DNS)**:
   - The browser queries the **Kaspa Block DAG Ledger (KNS)**, **Handshake (HNS)**, **ENS (.eth)**, or **OpenNIC** directly.
   - The domain maps directly to a **Content Identifier (CID Hash)** like `bafybeic...`.

2. **P2P Content Delivery (No Central Hosting)**:
   - Pages (HTML, CSS, JS, images) are pulled from a **Distributed Hash Table (DHT)** peer swarm rather than a single IP address.

3. **In-Browser Tamper Verification**:
   - The browser computes a **SHA-256 hash** of incoming payloads locally and verifies it against the signed CID hash.
   - Any tampered payload is instantly rejected (`TAMPERED_HASH_MISMATCH`).

4. **Local Mesh Seeding**:
   - Downloaded CID blocks are indexed into the device's local database with `isSeeding = true`, making your device an active seed node for nearby peers.

---

## 🔒 Strict Pure Decentralized Mode

- You can enable **Strict Pure Decentralized Mode** in settings to block all unencrypted centralized web server connections and route 100% of traffic exclusively through P2P swarms, Handshake, and blockchain ledgers.
