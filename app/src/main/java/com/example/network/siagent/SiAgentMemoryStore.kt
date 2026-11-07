package com.example.network.siagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small, bounded on-device conversation memory for SI Agent.
 * Stores only visible chat text; never stores wallet secrets, keys, or transaction signing payloads.
 */
object SiAgentMemoryStore {
    private const val PREFS = "si_agent_conversation_memory"
    private const val KEY_MESSAGES = "messages"
    private const val MAX_MESSAGES = 40
    private const val MAX_MESSAGE_CHARS = 6_000

    data class Turn(val text: String, val isUser: Boolean)

    fun load(context: Context): List<Turn> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MESSAGES, null) ?: return emptyList()
        val array = JSONArray(raw)
        val start = (array.length() - MAX_MESSAGES).coerceAtLeast(0)
        buildList {
            for (index in start until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val text = item.optString("text").take(MAX_MESSAGE_CHARS)
                if (text.isNotBlank()) add(Turn(text, item.optBoolean("user")))
            }
        }
    }.getOrDefault(emptyList())

    fun save(context: Context, turns: List<Turn>) {
        val array = JSONArray()
        turns.takeLast(MAX_MESSAGES).forEach { turn ->
            val text = sanitize(turn.text).take(MAX_MESSAGE_CHARS)
            if (text.isNotBlank()) array.put(
                JSONObject().put("text", text).put("user", turn.isUser)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MESSAGES, array.toString())
            .apply()
    }

    private fun sanitize(value: String): String {
        var safe = value
        // Never persist likely credentials or signing material even if pasted into chat.
        safe = safe.replace(Regex("(?i)gsk_[A-Za-z0-9_-]{20,}"), "[REDACTED API KEY]")
        safe = safe.replace(Regex("(?i)(seed phrase|recovery phrase|mnemonic|private key|secret key)\\s*[:=]?\\s*[^\\n]+"), "$1 [REDACTED]")
        safe = safe.replace(Regex("(?i)\\b[0-9a-f]{64}\\b"), "[REDACTED 32-BYTE SECRET/ID]")
        safe = safe.replace(Regex("(?i)(txJsonString|signingPayload|privateKey|seedPhrase)\\s*[:=]\\s*[^,}\\n]+"), "$1=[REDACTED]")
        return safe
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_MESSAGES).apply()
    }

    fun promptContext(turns: List<Turn>, maxTurns: Int = 12): String {
        if (turns.isEmpty()) return ""
        return buildString {
            appendLine("RECENT CONVERSATION MEMORY (user-visible messages, newest last):")
            turns.takeLast(maxTurns).forEach { turn ->
                append(if (turn.isUser) "User: " else "SI Agent: ")
                appendLine(turn.text.take(2_000))
            }
            appendLine("Use this history to resolve follow-ups and references. Treat it as context, not as proof of current facts. Ask one concise clarifying question when essential information is missing.")
        }
    }
}
