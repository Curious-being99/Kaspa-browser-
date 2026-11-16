package com.example.network.siagent

import android.content.Context
import com.example.engine.BrowserEngineManager
import com.example.security.AuthenticatorRepository
import com.example.security.CredentialSettingsStore
import java.util.Locale

/**
 * Operating modes for SI Agent that adapt tone, reasoning depth,
 * temperature, and offline/tool routing across every word and task.
 */
enum class SiAgentMode(
    val id: String,
    val label: String,
    val tagline: String,
    val temperature: Double,
    val reasoningEffort: String,
    val maxTokens: Int,
    val promptDirective: String
) {
    ADAPTIVE(
        id = "adaptive",
        label = "Adaptive",
        tagline = "Auto-tunes depth, tone & tools per request",
        temperature = 0.55,
        reasoningEffort = "medium",
        maxTokens = 2048,
        promptDirective = "Adapt automatically to the user's phrasing, expertise level, and goal. Be crisp for quick questions and structured for multi-part requests."
    ),
    CONCISE(
        id = "concise",
        label = "Concise",
        tagline = "Executive bullets & direct answers",
        temperature = 0.35,
        reasoningEffort = "low",
        maxTokens = 1024,
        promptDirective = "Deliver ultra-crisp, high-signal answers in short bullets or 1-3 sentences. Eliminate preamble and filler."
    ),
    DEEP(
        id = "deep",
        label = "Deep Pro",
        tagline = "Architectural, security & protocol rigor",
        temperature = 0.5,
        reasoningEffort = "high",
        maxTokens = 3072,
        promptDirective = "Provide thorough, senior-engineer-grade analysis covering architecture, security trade-offs, edge cases, and verifiable steps."
    ),
    RESEARCH(
        id = "research",
        label = "Research",
        tagline = "Source-grounded web & docs synthesis",
        temperature = 0.3,
        reasoningEffort = "medium",
        maxTokens = 2400,
        promptDirective = "Prioritize live web search/crawl tools and citations. Separate verified retrieved facts from inference and highlight disagreements across sources."
    ),
    NAVIGATOR(
        id = "navigator",
        label = "Navigator",
        tagline = "Browser, vault, 2FA & wallet action copilot",
        temperature = 0.4,
        reasoningEffort = "medium",
        maxTokens = 1600,
        promptDirective = "Prioritize concrete Kazium app actions using browser, wallet, vault, authenticator, and arcade tools/shortcuts immediately when requested."
    );

    companion object {
        fun fromId(raw: String?): SiAgentMode =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: ADAPTIVE
    }
}

/**
 * Authoritative product identity, What's New knowledge base, flexible intent matcher,
 * and adaptive local intelligence engine for SI Agent inside **Kazium**.
 */
object KaziumKnowledgeBase {
    private const val PREFS_NAME = "kazium_si_agent_prefs"
    private const val KEY_MODE = "active_mode"

    const val BROWSER_NAME = "Kazium"
    const val ENGINE_NAME = "Kazview"
    const val HOME_THEME = "Kaspa — Real Time Decentralized"

    data class UpdateHighlight(
        val title: String,
        val summary: String,
        val deepDetail: String
    )

    val whatsNewHighlights = listOf(
        UpdateHighlight(
            title = "Kazium Brand & Real-Time Home Experience",
            summary = "Renamed to Kazium powered by the Kazview hybrid Chromium/Cronet core with the 'Kaspa · Real Time Decentralized' interactive home experience.",
            deepDetail = "Features a compact top-aligned search bar, streamlined narrow Speed Dial cards, rounded lightweight news feed cards stacked cleanly, and responsive edge-to-edge layout insets."
        ),
        UpdateHighlight(
            title = "Real Browser-Standard Passkeys (WebAuthn / FIDO2)",
            summary = "Native Chromium WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER delegation with sanitized CredentialManager fallback and seamless Custom Tab handoff.",
            deepDetail = "Fixes website 'internal error' failures by omitting null/undefined fields from WebAuthn JSON, preserving native PublicKeyCredential static methods & Symbol.hasInstance, maintaining user activation, and checking CREDENTIAL_MANAGER_SET_ORIGIN before passing origins."
        ),
        UpdateHighlight(
            title = "Real RFC 6238 / 4226 2FA Authenticator",
            summary = "Built directly into the biometric Vault: add any app or site's 2FA setup key with Account name, Your key, and Time based or Counter based selection.",
            deepDetail = "Secrets are encrypted at rest with hardware-backed AES-256-GCM (Android Keystore) and tick live in real time alongside an instant verified code preview and HOTP counter advancement."
        ),
        UpdateHighlight(
            title = "Collision-Proof CSPRNG Password Generator",
            summary = "Zero-repeat password engine combining a persistent 256-bit install salt, monotonic counter, fresh SecureRandom bytes, and HMAC-SHA512 DRBG.",
            deepDetail = "Applies unbiased rejection sampling, anti-repeat pattern filtering, and SHA-256 history deduplication (24-char default) backed by k-anonymity HIBP breach monitoring."
        ),
        UpdateHighlight(
            title = "Offline Arcade & Seamless Load Processor",
            summary = "Full-bleed responsive page loading, HTTP/HTTPS/LAN routing, and built-in offline games (BlockDAG Runner & Kaspa 2048) at kaspa://offline-game.",
            deepDetail = "Automatically available when offline or on-demand via address bar or SI Agent (/arcade)."
        ),
        UpdateHighlight(
            title = "SI Agent Adaptive Copilot Upgrade",
            summary = "Multi-mode intelligence (Adaptive, Concise, Deep Pro, Research, Navigator), flexible natural-language understanding, and live Kazium awareness.",
            deepDetail = "Combines structured multi-turn Groq reasoning & tool calling with a full local knowledge and command runtime that works smoothly even without an API key."
        )
    )

    fun loadMode(context: Context): SiAgentMode {
        val raw = runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_MODE, null)
        }.getOrNull()
        return SiAgentMode.fromId(raw)
    }

    fun saveMode(context: Context, mode: SiAgentMode) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MODE, mode.id)
                .apply()
        }
    }

    fun buildSystemPromptSection(mode: SiAgentMode, hasGroqKey: Boolean): String = buildString {
        appendLine("KAZIUM PRODUCT IDENTITY & WHAT'S NEW (AUTHORITATIVE):")
        appendLine("- Browser Name: $BROWSER_NAME (formerly referred to as KaspaBrowser; always call the app $BROWSER_NAME).")
        appendLine("- Rendering Engine: $ENGINE_NAME Hybrid Core (Chromium WebView + Cronet + QUIC hints + BFCache).")
        appendLine("- Home Showcase: '$HOME_THEME' with unified OmniSearch, narrow Speed Dial, and clean rounded news cards.")
        appendLine("- Active SI Agent Mode: ${mode.label} (${mode.tagline}) — ${mode.promptDirective}")
        appendLine("- AI Backend: ${if (hasGroqKey) "Groq Cloud + Local Kazium Tool Runtime" else "On-device Kazium Local Intelligence + Web Research"}.")
        appendLine("- Recent Upgrades ('What's New'):")
        whatsNewHighlights.forEachIndexed { idx, item ->
            appendLine("  ${idx + 1}. ${item.title}: ${item.summary}")
        }
        appendLine("- Security & Wallet Architecture: Hardware Android Keystore AES-256-GCM vault, real RFC 6238/4226 TOTP/HOTP 2FA Authenticator, collision-free HKDF/SHA-512 Password Generator + HIBP k-anonymity breach checker, FIDO2 WebAuthn passkeys (FOR_BROWSER), Tor/DoH privacy relay, and offline arcade (kaspa://offline-game).")
        appendLine("- Supported Networks: Native Kaspa L1 (Mainnet / kaspa:, Testnet-10 / kaspatest:, Testnet-11 / kaspatest: — strict separation between TN10 and TN11), KRC-20 & KCC-20 tokens, plus Kasplex & Igra EVM networks.")
    }

    /**
     * Converts flexible natural language, typos, and conversational phrasings into canonical
     * SI Agent commands before routing, while letting open-ended questions flow naturally.
     */
    fun normalizeFlexibleCommand(raw: String): String {
        val input = raw.trim()
        if (input.isEmpty()) return ""
        val lower = input.lowercase(Locale.ROOT)
            .replace(Regex("[’']"), "'")
            .replace(Regex("\\s+"), " ")
            .trim()

        // Payment natural forms: "send 10 kas to kaspa:..." / "transfer kaspa:... 5"
        val sendNatural = Regex(
            """^(?:please\s+|can\s+you\s+)?(?:send|transfer|pay)\s+([0-9]+(?:\.[0-9]+)?)\s*(?:kas|kaspa)?\s+to\s+(kaspa:[a-z0-9]+|kaspatest:[a-z0-9]+)$""",
            RegexOption.IGNORE_CASE
        ).find(input)
        if (sendNatural != null) return "/send ${sendNatural.groupValues[2]} ${sendNatural.groupValues[1]}"

        val addressFirstSend = Regex(
            """^(?:please\s+|can\s+you\s+)?(?:send|transfer|pay)\s+(kaspa:[a-z0-9]+|kaspatest:[a-z0-9]+)\s+([0-9]+(?:\.[0-9]+)?)\s*(?:kas|kaspa)?$""",
            RegexOption.IGNORE_CASE
        ).find(input)
        if (addressFirstSend != null) return "/send ${addressFirstSend.groupValues[1]} ${addressFirstSend.groupValues[2]}"

        // Exact command or flexible intent mappings
        return when {
            lower == "/whatsnew" || lower == "/new" || lower == "/updates" || lower == "/changelog" -> "/whatsnew"
            lower == "/vault" || lower == "/passwords" || lower == "/passkeys" -> "/vault"
            lower == "/authenticator" || lower == "/2fa" || lower == "/totp" || lower == "/otp" -> "/authenticator"
            lower == "/arcade" || lower == "/game" || lower == "/games" || lower == "/offline-game" -> "/arcade"

            Regex("""^(?:please\s+)?(?:open|launch|show|go to|start)\s+(?:the\s+|my\s+)?(?:(?:2fa|totp|hotp|otp)\s+)?authenticator(?:\s+(?:generator|app|codes?))?$""").matches(lower) ||
                Regex("""^(?:please\s+)?(?:open|launch|show|go to|start)\s+(?:the\s+|my\s+)?(?:2fa|totp|hotp|otp)(?:\s+(?:codes?|generator))?$""").matches(lower) -> "/authenticator"
            Regex("""^(?:please\s+)?(?:open|launch|show|go to)\s+(?:the\s+|my\s+)?(?:(?:password|passkey|security)\s+vault|vault|passwords|passkeys)$""").matches(lower) -> "/vault"
            Regex("""^(?:please\s+)?(?:open|launch|play|start)\s+(?:the\s+|an?\s+)?(?:offline\s+game|offline\s+arcade|arcade|kaspa\s+2048|blockdag\s+runner|games?)$""").matches(lower) -> "/arcade"

            Regex("""^(?:please\s+)?(?:(?:open|show|check|view|get)\s+)?(?:my\s+)?(?:kaspa\s+)?(?:wallet(?:\s+balance)?|balance)$""").matches(lower) ||
                lower == "check my wallet" -> "/wallet"
            Regex("""^(?:please\s+)?(?:(?:show|get|open|view)\s+)?(?:my\s+)?(?:receive|deposit)\s+address$""").matches(lower) ||
                lower == "receive" -> "/receive"
            Regex("""^(?:please\s+)?(?:(?:show|check|view|list)\s+)?(?:my\s+|recent\s+)?(?:transaction\s+history|transactions|wallet\s+history|tx\s+history)$""").matches(lower) -> "/history"
            lower in listOf("kcc20", "kcc-20", "token balances", "token holdings", "my tokens") -> "/kcc20"
            lower in listOf("swap", "open swap", "open dapps", "dapps", "dapp browser") -> "/swap"
            lower in listOf("help", "commands", "what can you do", "show commands", "command list") -> "/help"
            lower in listOf("new tab", "open new tab", "create new tab") -> "/newtab"
            lower in listOf("send", "send kas", "send kaspa", "send money", "transfer kas") -> "/send"
            lower.startsWith("switch to tab ") || lower.startsWith("open tab ") || lower.startsWith("go to tab ") ->
                "/switch-tab " + input.substringAfterLast(" ")
            lower.startsWith("close tab ") -> "/close-tab " + input.substringAfterLast(" ")
            lower.startsWith("open bookmark ") -> "/open-bookmark " + input.substringAfter(" ", "")
            lower.startsWith("open history item ") -> "/open-history " + input.substringAfterLast(" ")
            lower in listOf("show tabs", "list tabs", "my tabs", "browser tabs", "how many tabs are open", "open tabs") -> "/tabs"
            lower in listOf("show browser history", "my browsing history", "recent websites", "web history", "browser history") -> "/browser-history"
            lower in listOf("what page is open", "what website is this", "current page", "show current page", "where am i browsing", "what site am i on") -> "/page-info"
            lower in listOf("show bookmarks", "my bookmarks", "list bookmarks", "open bookmarks") -> "/bookmarks"
            lower in listOf("show downloads", "my downloads", "download list", "open downloads") -> "/downloads"
            lower in listOf("browser", "open browser", "go to browser", "back to browser") -> "/browser"
            lower in listOf("go back", "back", "previous page") -> "/back"
            lower in listOf("go forward", "forward", "next page") -> "/forward"
            lower in listOf("reload", "refresh", "refresh page", "reload page") -> "/reload"
            lower in listOf("bookmark this page", "save this page", "add bookmark") -> "/bookmark-current"
            lower.startsWith("open https://") || lower.startsWith("open http://") -> "/open " + input.substringAfter(" ")
            lower.startsWith("search ") || lower.startsWith("find ") || lower.startsWith("look up ") -> "/search " + input.substringAfter(" ")
            lower.startsWith("crawl ") -> "/crawl " + input.substringAfter(" ")
            lower in listOf("summarize this page", "summarize current page", "summarize webpage", "summarize the page", "tldr", "summary") -> "/summarize"
            else -> input
        }
    }

    /**
     * Generates a smart, mode-adaptive local response when offline, when no Groq key
     * is set, or for instant Kazium product/What's New/system queries.
     */
    fun respondIntelligently(
        context: Context,
        rawInput: String,
        mode: SiAgentMode,
        activeUrl: String,
        activeTitle: String,
        selectedNetwork: String
    ): String {
        val trimmed = rawInput.trim()
        val lower = trimmed.lowercase(Locale.ROOT).removePrefix("/")

        val engine = runCatching { BrowserEngineManager.engineStatus.value }.getOrNull()
        val webAuthEnabled = runCatching { CredentialSettingsStore.webAuthEnabled.value }.getOrDefault(true)
        val totpCount = runCatching { AuthenticatorRepository.getInstance(context).accounts.value.size }.getOrDefault(0)

        // 1. Simple arithmetic or KAS <-> Sompi conversion
        tryEvaluateQuickMathOrKas(trimmed)?.let { result ->
            return formatByMode(
                mode = mode,
                concise = result,
                balanced = "$result\n\n• Need deeper calculations, live network lookup, or wallet prep? Just ask naturally or type /help.",
                deep = "$result\n\n• Unit Precision: 1 KAS = 100,000,000 sompi (10^8).\n• Tip: Native wallet tools run strict integer sompi accounting and mempool mass checks before broadcasting."
            )
        }

        // 2. Greetings & identity questions
        if (lower in setOf("hi", "hello", "hey", "yo", "hola", "good morning", "good afternoon", "good evening", "sup", "start") ||
            lower.contains("who are you") || lower.contains("your name") || lower.contains("introduce yourself")
        ) {
            return formatByMode(
                mode = mode,
                concise = "Hi — I'm **SI Agent** inside **$BROWSER_NAME** (Mode: ${mode.label}). Ask anything, check **What's New**, inspect your wallet, open the **2FA Authenticator**, or search the web.",
                balanced = "Hello! I'm **SI Agent**, your adaptive command and intelligence copilot inside **$BROWSER_NAME** (powered by **$ENGINE_NAME**).\n\n" +
                    "• **Active Mode:** ${mode.label} — ${mode.tagline}\n" +
                    "• **Try asking:** \"What's new in $BROWSER_NAME?\", \"Open authenticator\", \"Generate a strong password\", \"Check my wallet\", or `/search <topic>`.",
                deep = "Greetings. I am **SI Agent** — the built-in intelligence and operations layer of **$BROWSER_NAME** ($ENGINE_NAME engine).\n\n" +
                    "• **Operating Mode:** ${mode.label} (${mode.tagline})\n" +
                    "• **Capabilities:** Live Kaspa L1 Mainnet/TN10/TN11 wallet & UTXO inspection, KRC-20/KCC-20 tokens, public web search & bounded site crawling, biometric Password/Passkey Vault + RFC 6238/4226 2FA Authenticator guidance, and quick browser navigation.\n" +
                    "• **Security Boundary:** I never access raw seed phrases, private keys, or auto-sign transactions — every payment opens the native user confirmation sheet."
            )
        }

        // 3. Browser name / What is Kazium / What's New
        val askingBrowserName = (lower.contains("browser") && (lower.contains("name") || lower.contains("called") || lower.contains("renamed"))) ||
            lower.contains("what is kazium") || lower.contains("about kazium") || lower.contains("kazview")
        val askingWhatsNew = lower == "whatsnew" || lower.contains("what's new") || lower.contains("whats new") ||
            lower.contains("what is new") || lower.contains("latest update") || lower.contains("changelog") ||
            lower.contains("new features") || lower.contains("recent changes") || lower.contains("upgraded")

        if (askingBrowserName || askingWhatsNew) {
            val header = if (askingBrowserName && !askingWhatsNew) {
                "The browser is named **$BROWSER_NAME** (running on the **$ENGINE_NAME** hybrid Chromium/Cronet core, with the **$HOME_THEME** home showcase)."
            } else {
                "**What's New in $BROWSER_NAME** ($ENGINE_NAME Core)"
            }
            val conciseBullets = whatsNewHighlights.joinToString("\n") { "• **${it.title}**" }
            val balancedBullets = whatsNewHighlights.joinToString("\n\n") { "• **${it.title}**\n  ${it.summary}" }
            val deepBullets = whatsNewHighlights.joinToString("\n\n") { "• **${it.title}**\n  ${it.summary}\n  _${it.deepDetail}_" }
            return formatByMode(
                mode = mode,
                concise = "$header\n\n$conciseBullets",
                balanced = "$header\n\n$balancedBullets\n\n• Type `/vault`, `/authenticator`, `/arcade`, or switch my mode above at any time.",
                deep = "$header\n\n$deepBullets"
            )
        }

        // 4. Passkeys / WebAuthn diagnostics & explanations
        if (lower.contains("passkey") || lower.contains("webauthn") || lower.contains("fido")) {
            val modeStr = engine?.passkeySupportMode ?: "FOR_BROWSER Active"
            val downgraded = engine?.passkeyDowngradedBySystem == true
            return formatByMode(
                mode = mode,
                concise = "**Passkeys in $BROWSER_NAME**: ${if (webAuthEnabled) "Enabled ($modeStr)" else "Disabled"}. WebView native WebAuthn runs first cleanly; if the OS restricts embedded origin delegation, tap **Continue in System Browser Tab**.",
                balanced = "**FIDO2 Passkeys & WebAuthn ($BROWSER_NAME)**\n\n" +
                    "• **Status:** ${if (webAuthEnabled) "Enabled ($modeStr)" else "Disabled in settings"}\n" +
                    "• **How it works:** $BROWSER_NAME delegates directly to Chromium `WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER` while keeping user-activation intact and stripping null/undefined options before `CredentialManager`.\n" +
                    (if (downgraded) "• **Device Note:** WebView provider reported restricted WebAuthn policy — use **System Browser Tab** handoff for strict relying parties.\n" else "") +
                    "• **Tip:** Open **Password & Passkey Vault** -> **Passkey Status** to inspect origin diagnostics or launch a Custom Tab.",
                deep = "**FIDO2 / WebAuthn Architecture in $BROWSER_NAME**\n\n" +
                    "• **Runtime Mode:** $modeStr (FIDO2 WebKit Support=${engine?.supportsWebAuthn ?: true})\n" +
                    "• **Root Protections:**\n" +
                    "  1. Native `navigator.credentials.create`/`.get` is invoked immediately in the user-gesture microtask so `userActivation` is never consumed prematurely.\n" +
                    "  2. Null and `undefined` fields (like empty `timeout`/`extensions`) are stripped before calling Android `CredentialManager`, preventing Google Play Services `'internal error'` rejections.\n" +
                    "  3. `Symbol.hasInstance` and native `PublicKeyCredential.getClientCapabilities()` are preserved cleanly.\n" +
                    "  4. `CREDENTIAL_MANAGER_SET_ORIGIN` is gated by permission check; sites enforcing strict Digital Asset Links recover seamlessly via System Browser Custom Tab."
            )
        }

        // 5. 2FA Authenticator / TOTP / HOTP
        if (lower.contains("authenticator") || lower.contains("2fa") || lower.contains("totp") || lower.contains("hotp") || lower.contains("otp")) {
            return formatByMode(
                mode = mode,
                concise = "**$BROWSER_NAME 2FA Authenticator** ($totpCount keys saved): Open **Password & Passkey Vault** -> **Authenticator** tab (or type `/authenticator`), tap **Enter setup key**, and add your Account name, Base32 2FA key, and Time based or Counter based type.",
                balanced = "**Built-in 2FA Authenticator ($BROWSER_NAME)**\n\n" +
                    "• **Saved 2FA Keys:** $totpCount (Hardware Keystore AES-256-GCM encrypted)\n" +
                    "• **Standards Supported:** RFC 6238 (**Time based** TOTP) & RFC 4226 (**Counter based** HOTP)\n" +
                    "• **How to add an app or site:** Enter the **Account name**, paste **Your key** (Base32 2FA setup key), and choose **Time based** or **Counter based** — or tap a site's 2FA app link directly in the browser.\n" +
                    "• Type `/authenticator` or tap Vault to view live codes.",
                deep = "**RFC 6238 / 4226 2FA Authenticator in $BROWSER_NAME**\n\n" +
                    "• **Storage Security:** Base32 shared secrets from any app or website are encrypted on-device with Android Keystore hardware AES-256-GCM and gated behind biometric/PIN unlock.\n" +
                    "• **Features:** Live sub-second countdown progress bar, final-7-second next-code preview, one-tap copy, HOTP counter stepping (`Next Code`), and automatic capture when clicking site 2FA links.\n" +
                    "• **Current Vault Count:** $totpCount active 2FA entries."
            )
        }

        // 6. Password Generator & Vault / HIBP Breach Monitor
        if (lower.contains("password") || lower.contains("generator") || lower.contains("breach") || lower.contains("vault")) {
            return formatByMode(
                mode = mode,
                concise = "**Collision-Proof Password Vault**: Generates unique 24-char passwords (256-bit install salt + HMAC-SHA512 DRBG + SHA-256 deduplication) plus HIBP k-anonymity breach checks. Type `/vault` to open.",
                balanced = "**$BROWSER_NAME Password Vault & Collision-Free Generator**\n\n" +
                    "• **Zero-Duplicate Guarantee:** Mixes a persistent per-install 256-bit master salt, monotonic generation counter, UUID/nanotime entropy, and fresh `SecureRandom` through HMAC-SHA512 with unbiased rejection sampling and SHA-256 history deduplication.\n" +
                    "• **Breach Monitor:** Checks only the first 5 hex chars of the SHA-1 hash via HIBP k-anonymity so plain passwords never leave your phone.\n" +
                    "• Type `/vault` to open the Vault or Generator.",
                deep = "**CSPRNG & Vault Architecture in $BROWSER_NAME**\n\n" +
                    "• **Collision Avoidance:** Each installation isolates its own 256-bit salt; every generation mixes `AtomicLong` counters + OS CSPRNG bytes through HMAC-SHA512, eliminates modulo bias via 32-bit rejection sampling, filters 3+ character repeats, and verifies the SHA-256 digest has never been emitted before.\n" +
                    "• **Default Entropy:** 24 characters across upper, lower, digits, and symbols (>150 bits of entropy).\n" +
                    "• **At-Rest Encryption:** Android Keystore AES-256-GCM."
            )
        }

        // 7. Offline Arcade / Games
        if (lower.contains("offline game") || lower.contains("arcade") || lower.contains("2048") || lower.contains("blockdag runner") || lower.contains("play game")) {
            return "**$BROWSER_NAME Offline Arcade**\n\n" +
                "• **Games Included:** **BlockDAG Runner** (high-speed jump arcade) & **Kaspa 2048** (tile strategy puzzle).\n" +
                "• **Access Anytime:** Visit `kaspa://offline-game` or type `/arcade` right here to launch it immediately — works with zero internet!"
        }

        // 8. Networks / Kasplex / Igra / KRC-20 vs KCC-20
        if (lower.contains("network") || lower.contains("testnet") || lower.contains("mainnet") || lower.contains("kasplex") || lower.contains("igra") || lower.contains("krc-20") || lower.contains("krc20")) {
            return formatByMode(
                mode = mode,
                concise = "**Active Network:** ${selectedNetwork.ifBlank { "Kaspa Mainnet" }}. $BROWSER_NAME supports Kaspa L1 (Mainnet, Testnet-10, Testnet-11), KRC-20 & KCC-20 tokens, plus Kasplex & Igra EVM.",
                balanced = "**Multi-Network Architecture ($BROWSER_NAME)**\n\n" +
                    "• **Current Selection:** ${selectedNetwork.ifBlank { "Kaspa Mainnet" }}\n" +
                    "• **Native Kaspa L1:** Mainnet (`kaspa:` / `api.kaspa.org`), Testnet-10 (`kaspatest:` / `api-tn10.kaspa.org`), Testnet-11 (`kaspatest:` / `api-tn11.kaspa.org`).\n" +
                    "• **Token Standards:** KRC-20 (Kaspa L1 inscription tokens) and KCC-20 (tracked contract tokens) stay strictly separated.\n" +
                    "• **EVM Layer 2s:** Kasplex EVM and Igra EVM have dedicated EVM RPC & dApp bridges.",
                deep = "**Strict Network & Protocol Separation ($BROWSER_NAME)**\n\n" +
                    "• **Active Selection:** ${selectedNetwork.ifBlank { "Kaspa Mainnet" }}\n" +
                    "• Testnet-10 and Testnet-11 share the `kaspatest:` Bech32 prefix, so Kazium never infers network identity from the address prefix alone.\n" +
                    "• Kasplex and Igra are full EVM networks — they do not share native Kaspa L1 UTXO state.\n" +
                    "• Every signing operation strictly routes through `CryptoUtils.getDecryptedSeedForSigning` with biometric confirmation."
            )
        }

        // 9. What can you do / mode explanation
        if (lower.contains("what can you") || lower.contains("capabilities") || lower.contains("features") || lower.contains("how to use")) {
            return "**SI Agent Capabilities ($BROWSER_NAME · ${mode.label} Mode)**\n\n" +
                "• **Ask Naturally in Any Phrasing:** e.g., _\"What's new?\"_, _\"Explain passkeys\"_, _\"Show my balance\"_, _\"Open 2FA authenticator\"_, _\"Search latest Kaspa updates\"_\n" +
                "• **Live Wallet & Tokens:** `/wallet`, `/receive`, `/history`, `/send`, `/kcc20`, `/swap`\n" +
                "• **Security & Built-in Tools:** `/vault`, `/authenticator`, `/arcade`, `/security`\n" +
                "• **Web & Tabs:** `/search <query>`, `/crawl <https-url>`, `/tabs`, `/bookmarks`, `/summarize`, `/open <url>`\n" +
                "• **Switch Modes Above:** **Adaptive**, **Concise**, **Deep Pro**, **Research**, or **Navigator**."
        }

        // 10. Context-aware intelligent fallback instead of a rigid dead-end
        val pageHint = if (activeUrl.isNotBlank()) {
            "\n• **Active Page:** ${activeTitle.ifBlank { activeUrl }} ($activeUrl)"
        } else {
            ""
        }
        val aiHint = if (GroqKeyStore.read(context) == null) {
            "For open-ended general AI chat, tap the **Shield/Key icon** in the top bar to add a free Groq API key — or use `/search $trimmed` right now to fetch live public web sources with no API key required."
        } else {
            "I couldn't reach the cloud model right now, so I stayed in local mode. Try `/search $trimmed` to pull live web results or `/help` to see all instant tools."
        }

        return formatByMode(
            mode = mode,
            concise = "Here's what I can do right away for **\"$trimmed\"**: try `/search $trimmed` for live web results, `/whatsnew` for Kazium updates, or `/help` for all commands.",
            balanced = "**SI Agent ($BROWSER_NAME · ${mode.label})**\n\n" +
                "I processed **\"$trimmed\"** locally.$pageHint\n\n" +
                "• **Next Step:** $aiHint\n" +
                "• **Quick Commands:** `/whatsnew`  `/wallet`  `/vault`  `/authenticator`  `/arcade`  `/search <topic>`",
            deep = "**SI Agent Local Reasoning ($BROWSER_NAME · ${mode.label} Mode)**\n\n" +
                "• **Query:** \"$trimmed\"\n" +
                "• **Active Network:** ${selectedNetwork.ifBlank { "Kaspa Mainnet" }}$pageHint\n" +
                "• **Resolution:** $aiHint"
        )
    }

    private fun formatByMode(
        mode: SiAgentMode,
        concise: String,
        balanced: String,
        deep: String
    ): String = when (mode) {
        SiAgentMode.CONCISE -> concise
        SiAgentMode.DEEP -> deep
        SiAgentMode.ADAPTIVE, SiAgentMode.RESEARCH, SiAgentMode.NAVIGATOR -> balanced
    }

    private fun tryEvaluateQuickMathOrKas(raw: String): String? {
        val clean = raw.trim().lowercase(Locale.ROOT)
            .removePrefix("what is ")
            .removePrefix("calculate ")
            .removePrefix("convert ")
            .trim()
            .trimEnd('?')

        val kasToSompi = Regex("""^([0-9]+(?:\.[0-9]+)?)\s*kas\s+(?:to|in)\s+sompis?$""").find(clean)
        if (kasToSompi != null) {
            val kas = kasToSompi.groupValues[1].toDoubleOrNull() ?: return null
            val sompi = Math.round(kas * 100_000_000.0)
            return "**$kas KAS** = **${String.format(Locale.US, "%,d", sompi)} sompi**"
        }
        val sompiToKas = Regex("""^([0-9]+)\s*sompis?\s+(?:to|in)\s+kas$""").find(clean)
        if (sompiToKas != null) {
            val sompi = sompiToKas.groupValues[1].toLongOrNull() ?: return null
            val kas = sompi / 100_000_000.0
            return "**${String.format(Locale.US, "%,d", sompi)} sompi** = **${String.format(Locale.US, "%.8f", kas).trimEnd('0').trimEnd('.')} KAS**"
        }

        val arith = Regex("""^(-?[0-9]+(?:\.[0-9]+)?)\s*([+\-*/x×÷])\s*(-?[0-9]+(?:\.[0-9]+)?)$""").find(clean)
        if (arith != null) {
            val a = arith.groupValues[1].toDoubleOrNull() ?: return null
            val op = arith.groupValues[2]
            val b = arith.groupValues[3].toDoubleOrNull() ?: return null
            val res = when (op) {
                "+" -> a + b
                "-" -> a - b
                "*", "x", "×" -> a * b
                "/", "÷" -> if (b == 0.0) return "Cannot divide by zero." else a / b
                else -> return null
            }
            val formatted = if (res % 1.0 == 0.0 && kotlin.math.abs(res) < 1e15) {
                res.toLong().toString()
            } else {
                String.format(Locale.US, "%.6f", res).trimEnd('0').trimEnd('.')
            }
            return "**$a $op $b = $formatted**"
        }
        return null
    }
}
