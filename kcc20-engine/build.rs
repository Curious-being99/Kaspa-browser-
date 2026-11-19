use std::{env, fs, path::PathBuf};

fn main() {
    println!("cargo:rerun-if-env-changed=KCC20_RUNTIME_MANIFEST");
    let manifest = env::var("KCC20_RUNTIME_MANIFEST")
        .expect("KCC20_RUNTIME_MANIFEST must point to the generated argentc runtime manifest");
    println!("cargo:rerun-if-changed={manifest}");
    let source = fs::read_to_string(&manifest)
        .expect("failed to read generated Argent KCC20 runtime manifest");
    let parsed: serde_json::Value =
        serde_json::from_str(&source).expect("generated Argent runtime manifest is invalid JSON");
    for key in ["artifact_id", "template_hash", "prefix_hex", "suffix_hex"] {
        assert!(parsed.get(key).and_then(serde_json::Value::as_str).is_some(),
            "generated Argent runtime manifest is missing string field {key}");
    }
    let out = PathBuf::from(env::var_os("OUT_DIR").expect("OUT_DIR is not set"));
    fs::write(out.join("kcc20-runtime.json"), source)
        .expect("failed to stage generated Argent runtime manifest");
}
