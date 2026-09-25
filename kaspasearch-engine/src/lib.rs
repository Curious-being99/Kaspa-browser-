use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;

pub mod federator;
pub mod privacy;

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

    let rt = tokio::runtime::Runtime::new().unwrap();
    let json_res = rt.block_on(async {
        let engine = federator::FederatedSearchEngine::new();
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

    let rt = tokio::runtime::Runtime::new().unwrap();
    let json_res = rt.block_on(async {
        let engine = federator::FederatedSearchEngine::new();
        let results = engine.search(&query_str, 1).await;
        serde_json::to_string(&results).unwrap_or_else(|_| "[]".to_string())
    });

    env.new_string(json_res).unwrap().into_raw()
}
