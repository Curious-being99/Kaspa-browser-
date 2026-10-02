use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jboolean, jbyteArray, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;

pub mod crypto;
pub mod federator;
pub mod privacy;
pub mod reader;
pub mod relay;
pub mod relay_server;
pub mod security;

use std::sync::OnceLock;

static SEARCH_RUNTIME: OnceLock<tokio::runtime::Runtime> = OnceLock::new();
static SEARCH_ENGINE: OnceLock<federator::FederatedSearchEngine> = OnceLock::new();
static RELAY_CORE: OnceLock<relay::NativePrivacyRelayCore> = OnceLock::new();

fn get_relay_core() -> &'static relay::NativePrivacyRelayCore {
    RELAY_CORE.get_or_init(relay::NativePrivacyRelayCore::new)
}

fn get_search_runtime() -> &'static tokio::runtime::Runtime {
    SEARCH_RUNTIME.get_or_init(|| {
        tokio::runtime::Builder::new_multi_thread()
            .worker_threads(2)
            .enable_all()
            .thread_name("kaspa-search-worker")
            .build()
            .expect("Failed to initialize Kaspa Search Tokio Runtime")
    })
}

fn get_search_engine() -> &'static federator::FederatedSearchEngine {
    SEARCH_ENGINE.get_or_init(federator::FederatedSearchEngine::new)
}

// ============================================================================
// SearchEngine / EmbeddedRustSearchEngine Existing JNI Exports
// ============================================================================

#[no_mangle]
pub extern "system" fn Java_com_example_network_SearchEngine_nativeSanitizeUrl(
    mut env: JNIEnv,
    _class: JClass,
    input: JString,
) -> jstring {
    let raw_str: String = match env.get_string(&input) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };

    let clean_str = privacy::PrivacyGuard::sanitize_url(&raw_str);
    env.new_string(clean_str).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_example_network_SearchEngine_nativeAstFilter(
    mut env: JNIEnv,
    _class: JClass,
    input: JString,
) -> jstring {
    let raw_html: String = match env.get_string(&input) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };

    let clean_html = privacy::PrivacyGuard::filter_html_ast(&raw_html);
    env.new_string(clean_html).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_example_network_SearchEngine_nativeSearch(
    mut env: JNIEnv,
    _class: JClass,
    input: JString,
) -> jstring {
    let query_str: String = match env.get_string(&input) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("[]").unwrap().into_raw(),
    };

    let rt = get_search_runtime();
    let engine = get_search_engine();
    let json_res = rt.block_on(async {
        let results = engine.search(&query_str, 1).await;
        serde_json::to_string(&results).unwrap_or_else(|_| "[]".to_string())
    });

    env.new_string(json_res).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_example_network_EmbeddedRustSearchEngine_nativeSanitizeUrl(
    mut env: JNIEnv,
    _class: JClass,
    input: JString,
) -> jstring {
    let raw_str: String = match env.get_string(&input) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };

    let clean_str = privacy::PrivacyGuard::sanitize_url(&raw_str);
    env.new_string(clean_str).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_example_network_EmbeddedRustSearchEngine_nativeSearch(
    mut env: JNIEnv,
    _class: JClass,
    input: JString,
) -> jstring {
    let query_str: String = match env.get_string(&input) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("[]").unwrap().into_raw(),
    };

    let rt = get_search_runtime();
    let engine = get_search_engine();
    let json_res = rt.block_on(async {
        let results = engine.search(&query_str, 1).await;
        serde_json::to_string(&results).unwrap_or_else(|_| "[]".to_string())
    });

    env.new_string(json_res).unwrap().into_raw()
}

// ============================================================================
// KaspaPrivacyEngine Priority 1 JNI Exports
// ============================================================================

#[no_mangle]
pub extern "system" fn Java_com_example_network_KaspaPrivacyEngine_nativeIsTrackerOrAd(
    mut env: JNIEnv,
    _class: JClass,
    url: JString,
) -> jboolean {
    let raw_url: String = match env.get_string(&url) {
        Ok(s) => s.into(),
        Err(_) => return JNI_FALSE,
    };

    if privacy::PrivacyGuard::is_tracker_or_ad(&raw_url) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_network_KaspaPrivacyEngine_nativeExtractHostFast(
    mut env: JNIEnv,
    _class: JClass,
    url: JString,
) -> jstring {
    let raw_url: String = match env.get_string(&url) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };

    match privacy::PrivacyGuard::extract_host_fast(&raw_url) {
        Some(host) => env.new_string(host).map(|js| js.into_raw()).unwrap_or(std::ptr::null_mut()),
        None => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_network_KaspaPrivacyEngine_nativeIsGoogleAccountDomain(
    mut env: JNIEnv,
    _class: JClass,
    host: JString,
) -> jboolean {
    let raw_host: String = match env.get_string(&host) {
        Ok(s) => s.into(),
        Err(_) => return JNI_FALSE,
    };

    if privacy::PrivacyGuard::is_google_account_domain(&raw_host) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_network_KaspaPrivacyEngine_nativeIsGoogleAccountOrAuthUrl(
    mut env: JNIEnv,
    _class: JClass,
    url: JString,
) -> jboolean {
    let raw_url: String = match env.get_string(&url) {
        Ok(s) => s.into(),
        Err(_) => return JNI_FALSE,
    };

    if privacy::PrivacyGuard::is_google_account_or_auth_url(&raw_url) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

// ============================================================================
// UBlockEngine Priority 1 JNI Exports
// ============================================================================

#[no_mangle]
pub extern "system" fn Java_com_example_network_UBlockEngine_nativeShouldBlock(
    mut env: JNIEnv,
    _class: JClass,
    url: JString,
) -> jboolean {
    let raw_url: String = match env.get_string(&url) {
        Ok(s) => s.into(),
        Err(_) => return JNI_FALSE,
    };

    if privacy::PrivacyGuard::should_block(&raw_url) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

// ============================================================================
// CryptoUtils Priority 1 JNI Exports
// ============================================================================

#[no_mangle]
pub extern "system" fn Java_com_example_network_CryptoUtils_nativeSha256(
    mut env: JNIEnv,
    _class: JClass,
    input: JString,
) -> jstring {
    let raw_str: String = match env.get_string(&input) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };

    let hash_hex = crypto::sha256(&raw_str);
    env.new_string(hash_hex).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_example_network_CryptoUtils_nativeSha256Raw(
    env: JNIEnv,
    _class: JClass,
    input: JByteArray,
) -> jbyteArray {
    let raw_bytes: Vec<u8> = match env.convert_byte_array(&input) {
        Ok(b) => b,
        Err(_) => return std::ptr::null_mut(),
    };

    let hash = crypto::sha256_raw(&raw_bytes);
    match env.byte_array_from_slice(&hash) {
        Ok(arr) => arr.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_network_CryptoUtils_nativeSha256Bytes(
    env: JNIEnv,
    _class: JClass,
    input: JByteArray,
) -> jstring {
    let raw_bytes: Vec<u8> = match env.convert_byte_array(&input) {
        Ok(b) => b,
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };

    let hash_hex = crypto::sha256_bytes(&raw_bytes);
    env.new_string(hash_hex).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_example_network_CryptoUtils_nativeHexToBytes(
    mut env: JNIEnv,
    _class: JClass,
    hex: JString,
) -> jbyteArray {
    let raw_hex: String = match env.get_string(&hex) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };

    let bytes = crypto::hex_to_bytes(&raw_hex);
    match env.byte_array_from_slice(&bytes) {
        Ok(arr) => arr.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_network_CryptoUtils_nativeGenerateCid(
    mut env: JNIEnv,
    _class: JClass,
    content: JString,
) -> jstring {
    let raw_content: String = match env.get_string(&content) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };

    let cid = crypto::generate_cid(&raw_content);
    env.new_string(cid).unwrap().into_raw()
}

// ============================================================================
// KaspaReaderMode Native JNI Exports
// ============================================================================

#[no_mangle]
pub extern "system" fn Java_com_example_network_KaspaReaderMode_nativeExtractArticle(
    mut env: JNIEnv,
    _class: JClass,
    raw_html: JString,
    url: JString,
) -> jstring {
    let html_str: String = match env.get_string(&raw_html) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("{}").unwrap().into_raw(),
    };

    let url_str: Option<String> = env.get_string(&url).ok().map(|s| s.into());

    let article = reader::NativeReaderEngine::extract_article(&html_str, url_str.as_deref());
    let json = serde_json::to_string(&article).unwrap_or_else(|_| "{}".to_string());
    env.new_string(json).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_example_network_KaspaReaderMode_nativeGenerateReaderHtml(
    mut env: JNIEnv,
    _class: JClass,
    raw_html: JString,
    theme: JString,
    font_size: jni::sys::jint,
) -> jstring {
    let html_str: String = match env.get_string(&raw_html) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };

    let theme_str: String = env.get_string(&theme).map(|s| s.into()).unwrap_or_else(|_| "dark".to_string());

    let article = reader::NativeReaderEngine::extract_article(&html_str, None);
    let full_html = reader::NativeReaderEngine::generate_reader_html(&article, &theme_str, font_size);
    env.new_string(full_html).unwrap().into_raw()
}

// ============================================================================
// KaspaSecurityGuard Native JNI Exports
// ============================================================================

#[no_mangle]
pub extern "system" fn Java_com_example_network_KaspaSecurityGuard_nativeInspectUrl(
    mut env: JNIEnv,
    _class: JClass,
    url: JString,
) -> jstring {
    let raw_url: String = match env.get_string(&url) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("{}").unwrap().into_raw(),
    };

    let verdict = security::SecurityGuard::inspect_url(&raw_url);
    let json = serde_json::to_string(&verdict).unwrap_or_else(|_| "{}".to_string());
    env.new_string(json).unwrap().into_raw()
}

// ============================================================================
// KaspaPrivacyRelayEngine (KRP/1 Dual-Hop Privacy Circuit) JNI Exports
// ============================================================================

#[no_mangle]
pub extern "system" fn Java_com_example_network_KaspaPrivacyRelayEngine_nativeGetDirectoryRelays(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let relays = relay::NativePrivacyRelayCore::get_directory_relays();
    let json = serde_json::to_string(&relays).unwrap_or_else(|_| "[]".to_string());
    env.new_string(json).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_example_network_KaspaPrivacyRelayEngine_nativeCreateCircuit(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let core = get_relay_core();
    let circuit = core.create_circuit();
    let json = serde_json::to_string(&circuit).unwrap_or_else(|_| "{}".to_string());
    env.new_string(json).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_example_network_KaspaPrivacyRelayEngine_nativeGetActiveCircuit(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let core = get_relay_core();
    let circuit = core.get_or_create_active_circuit();
    let json = serde_json::to_string(&circuit).unwrap_or_else(|_| "{}".to_string());
    env.new_string(json).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_example_network_KaspaPrivacyRelayEngine_nativeBuildRelayEnvelope(
    mut env: JNIEnv,
    _class: JClass,
    destination_url: JString,
    method: JString,
    headers_json: JString,
) -> jstring {
    let raw_url: String = match env.get_string(&destination_url) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("{}").unwrap().into_raw(),
    };
    let raw_method: String = match env.get_string(&method) {
        Ok(s) => s.into(),
        Err(_) => "GET".to_string(),
    };
    let raw_headers: String = match env.get_string(&headers_json) {
        Ok(s) => s.into(),
        Err(_) => "{}".to_string(),
    };

    let core = get_relay_core();
    match core.build_relay_envelope(&raw_url, &raw_method, &raw_headers, None) {
        Ok(envelope) => env.new_string(envelope).unwrap().into_raw(),
        Err(e) => {
            let err_json = serde_json::json!({ "error": e }).to_string();
            env.new_string(err_json).unwrap().into_raw()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_network_KaspaPrivacyRelayEngine_nativeRecordBytes(
    _env: JNIEnv,
    _class: JClass,
    bytes_count: jni::sys::jlong,
) {
    if bytes_count > 0 {
        let core = get_relay_core();
        core.record_relayed_bytes(bytes_count as u64);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_network_KaspaPrivacyRelayEngine_nativeSendCellOverTunnel(
    mut env: JNIEnv,
    _class: JClass,
    destination_url: JString,
    method: JString,
    headers_json: JString,
) -> jstring {
    let raw_url: String = match env.get_string(&destination_url) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("{}").unwrap().into_raw(),
    };
    let raw_method: String = match env.get_string(&method) {
        Ok(s) => s.into(),
        Err(_) => "GET".to_string(),
    };
    let raw_headers: String = match env.get_string(&headers_json) {
        Ok(s) => s.into(),
        Err(_) => "{}".to_string(),
    };

    let core = get_relay_core();
    match core.execute_cell_over_tunnel(&raw_url, &raw_method, &raw_headers, None) {
        Ok(json_res) => env.new_string(json_res).unwrap().into_raw(),
        Err(e) => {
            let err_json = serde_json::json!({
                "statusCode": 502,
                "statusMessage": format!("KRP Tunnel Native Error: {}", e),
                "headers": {},
                "bodyHex": "",
                "exitNodeName": "Kaspa KRP Shield"
            }).to_string();
            env.new_string(err_json).unwrap().into_raw()
        }
    }
}


