#include "kazview_native_core.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <cstring>
#include <mutex>
#include <string>
#include <string_view>

#if !defined(KAZVIEW_SKIP_ANDROID_JNI) && defined(__ANDROID__)
#include <jni.h>
#endif

namespace {

constexpr size_t kWarmupRingCapacity = 64;

struct WarmupSlot {
    std::string origin;
    int64_t last_warmup_ms = 0;
};

std::mutex g_warmup_mutex;
std::array<WarmupSlot, kWarmupRingCapacity> g_warmup_ring;
size_t g_warmup_cursor = 0;

bool equals_ascii_ci(std::string_view a, std::string_view b) {
    if (a.size() != b.size()) return false;
    for (size_t i = 0; i < a.size(); ++i) {
        if (std::tolower(static_cast<unsigned char>(a[i])) !=
            std::tolower(static_cast<unsigned char>(b[i]))) {
            return false;
        }
    }
    return true;
}

bool contains_ascii_ci(std::string_view haystack, std::string_view needle) {
    if (needle.empty()) return true;
    if (haystack.size() < needle.size()) return false;
    for (size_t i = 0; i + needle.size() <= haystack.size(); ++i) {
        if (equals_ascii_ci(haystack.substr(i, needle.size()), needle)) {
            return true;
        }
    }
    return false;
}

bool ends_with_ascii_ci(std::string_view value, std::string_view suffix) {
    if (value.size() < suffix.size()) return false;
    return equals_ascii_ci(value.substr(value.size() - suffix.size()), suffix);
}

std::string_view extract_host_view(std::string_view url) {
    auto scheme_pos = url.find("://");
    if (scheme_pos == std::string_view::npos) return {};
    std::string_view rest = url.substr(scheme_pos + 3);
    auto slash = rest.find_first_of("/?#");
    std::string_view authority = (slash == std::string_view::npos) ? rest : rest.substr(0, slash);
    auto at_pos = authority.rfind('@');
    if (at_pos != std::string_view::npos) {
        authority = authority.substr(at_pos + 1);
    }
    if (!authority.empty() && authority.front() == '[') {
        auto close_bracket = authority.find(']');
        return (close_bracket == std::string_view::npos) ? std::string_view{} : authority.substr(1, close_bracket - 1);
    }
    auto colon = authority.find(':');
    return (colon == std::string_view::npos) ? authority : authority.substr(0, colon);
}

} // namespace

extern "C" {

bool kazview_is_speculative_eligible(const char* url, bool privacy_blocked, bool low_ram_device) {
    if (privacy_blocked || low_ram_device || url == nullptr) {
        return false;
    }
    std::string_view raw(url);
    while (!raw.empty() && std::isspace(static_cast<unsigned char>(raw.front()))) raw.remove_prefix(1);
    while (!raw.empty() && std::isspace(static_cast<unsigned char>(raw.back()))) raw.remove_suffix(1);
    if (raw.size() < 10) return false;

    if (!equals_ascii_ci(raw.substr(0, 8), "https://")) {
        return false;
    }

    std::string_view host = extract_host_view(raw);
    if (host.empty() || host.find('.') == std::string_view::npos) {
        return false;
    }
    if (equals_ascii_ci(host, "localhost") || host == "127.0.0.1") {
        return false;
    }

    static constexpr std::array<std::string_view, 12> kAuthAndChallengeTokens = {
        "/cdn-cgi/", "turnstile", "hcaptcha", "recaptcha", "arkose", "datadome",
        "/login", "/signin", "/oauth", "/auth", "/webauthn", "/passkey"
    };
    for (auto token : kAuthAndChallengeTokens) {
        if (contains_ascii_ci(raw, token)) return false;
    }

    static constexpr std::array<std::string_view, 8> kBlockedAuthOrTrackerSuffixes = {
        "accounts.google.com", "doubleclick.net", "google-analytics.com",
        "googleadservices.com", "googlesyndication.com", "criteo.com",
        "taboola.com", "outbrain.com"
    };
    for (auto suffix : kBlockedAuthOrTrackerSuffixes) {
        if (equals_ascii_ci(host, suffix) ||
            (host.size() > suffix.size() &&
             host[host.size() - suffix.size() - 1] == '.' &&
             ends_with_ascii_ci(host, suffix))) {
            return false;
        }
    }
    return true;
}

bool kazview_record_origin_warmup(const char* origin, int64_t now_ms, int64_t cooldown_ms) {
    if (origin == nullptr || origin[0] == '\0') return false;
    std::string normalized(origin);
    std::transform(normalized.begin(), normalized.end(), normalized.begin(),
                   [](unsigned char c) { return static_cast<char>(std::tolower(c)); });

    std::lock_guard<std::mutex> lock(g_warmup_mutex);
    for (auto& slot : g_warmup_ring) {
        if (slot.origin == normalized) {
            if ((now_ms - slot.last_warmup_ms) < cooldown_ms) {
                return false;
            }
            slot.last_warmup_ms = now_ms;
            return true;
        }
    }
    g_warmup_ring[g_warmup_cursor].origin = std::move(normalized);
    g_warmup_ring[g_warmup_cursor].last_warmup_ms = now_ms;
    g_warmup_cursor = (g_warmup_cursor + 1) % kWarmupRingCapacity;
    return true;
}

void kazview_reset_warmup_cache(void) {
    std::lock_guard<std::mutex> lock(g_warmup_mutex);
    for (auto& slot : g_warmup_ring) {
        slot.origin.clear();
        slot.last_warmup_ms = 0;
    }
    g_warmup_cursor = 0;
}

#if !defined(KAZVIEW_SKIP_ANDROID_JNI) && defined(__ANDROID__)
JNIEXPORT jboolean JNICALL
Java_com_example_engine_KazviewNativeCore_nativeIsSpeculativeEligible(
    JNIEnv* env,
    jobject /* thiz */,
    jstring j_url,
    jboolean privacy_blocked,
    jboolean low_ram_device
) {
    if (j_url == nullptr) return JNI_FALSE;
    const char* raw = env->GetStringUTFChars(j_url, nullptr);
    if (raw == nullptr) return JNI_FALSE;
    const bool ok = kazview_is_speculative_eligible(raw, privacy_blocked == JNI_TRUE, low_ram_device == JNI_TRUE);
    env->ReleaseStringUTFChars(j_url, raw);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_engine_KazviewNativeCore_nativeRecordOriginWarmup(
    JNIEnv* env,
    jobject /* thiz */,
    jstring j_origin,
    jlong now_ms,
    jlong cooldown_ms
) {
    if (j_origin == nullptr) return JNI_FALSE;
    const char* raw = env->GetStringUTFChars(j_origin, nullptr);
    if (raw == nullptr) return JNI_FALSE;
    const bool proceed = kazview_record_origin_warmup(raw, static_cast<int64_t>(now_ms), static_cast<int64_t>(cooldown_ms));
    env->ReleaseStringUTFChars(j_origin, raw);
    return proceed ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_engine_KazviewNativeCore_nativeResetWarmupCache(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    kazview_reset_warmup_cache();
}
#endif

} // extern "C"
