use jni::objects::{JObject, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use kaspa_consensus_core::hashing::sighash::SigHashReusedValuesUnsync;
use kaspa_consensus_core::subnets::SubnetworkId;
use kaspa_consensus_core::tx::{
    CovenantBinding, PopulatedTransaction, ScriptPublicKey, Transaction, TransactionInput,
    TransactionOutpoint, TransactionOutput, UtxoEntry,
};
use kaspa_hashes::Hash;
use kaspa_txscript::caches::Cache;
use kaspa_txscript::covenants::CovenantsContext;
use kaspa_txscript::{EngineCtx, TxScriptEngine};
use serde_json::{json, Value};
use std::str::FromStr;

fn bytes(value: &Value) -> Result<Vec<u8>, String> {
    let hex = value.as_str().ok_or_else(|| "Expected hex string".to_string())?;
    if hex.len() % 2 != 0 || !hex.bytes().all(|b| b.is_ascii_hexdigit()) {
        return Err("Malformed hex value".to_string());
    }
    (0..hex.len())
        .step_by(2)
        .map(|i| u8::from_str_radix(&hex[i..i + 2], 16).map_err(|e| e.to_string()))
        .collect()
}

fn number(value: &Value, name: &str) -> Result<u64, String> {
    value
        .as_u64()
        .or_else(|| value.as_str().and_then(|s| s.parse::<u64>().ok()))
        .ok_or_else(|| format!("Invalid numeric field: {name}"))
}

fn optional_hash(value: &Value) -> Result<Option<Hash>, String> {
    if value.is_null() {
        return Ok(None);
    }
    let value = value.as_str().ok_or_else(|| "Expected covenant ID hex".to_string())?;
    if value.len() != 64 {
        return Err("Covenant ID must be 32-byte hex".to_string());
    }
    Hash::from_str(value)
        .map(Some)
        .map_err(|e| format!("Invalid covenant ID: {e}"))
}

fn required_hash(value: &Value, name: &str) -> Result<Hash, String> {
    optional_hash(value)?
        .ok_or_else(|| format!("Missing {name}"))
}

fn validate(json_text: &str) -> Result<Value, String> {
    let request: Value = serde_json::from_str(json_text).map_err(|e| e.to_string())?;
    let version = number(&request["version"], "version")? as u16;
    if version != 1 {
        return Err("KCC20 script-engine validation requires transaction version 1".to_string());
    }

    let input_values = request["inputs"].as_array().ok_or("Missing transaction inputs")?;
    let output_values = request["outputs"].as_array().ok_or("Missing transaction outputs")?;
    let utxo_values = request["utxos"].as_array().ok_or("Missing spent UTXO entries")?;
    if input_values.is_empty() || input_values.len() != utxo_values.len() {
        return Err("Every transaction input must have exactly one corresponding node UTXO entry".to_string());
    }

    let mut inputs = Vec::with_capacity(input_values.len());
    for input in input_values {
        let txid = required_hash(&input["txid"], "input txid")?;
        let index = number(&input["index"], "input index")? as u32;
        let sequence = number(&input["sequence"], "input sequence")?;
        let budget = number(&input["computeBudget"], "input computeBudget")?;
        if budget > u16::MAX as u64 {
            return Err("computeBudget exceeds u16".to_string());
        }
        inputs.push(TransactionInput::new_with_compute_budget(
            TransactionOutpoint::new(txid, index),
            bytes(&input["sigScript"] )?,
            sequence,
            budget as u16,
        ));
    }

    let mut outputs = Vec::with_capacity(output_values.len());
    for output in output_values {
        let script = ScriptPublicKey::from_vec(
            number(&output["spkVersion"], "output spkVersion")? as u16,
            bytes(&output["script"] )?,
        );
        let covenant = if output["covenant"].is_null() {
            None
        } else {
            let binding = &output["covenant"];
            Some(CovenantBinding::new(
                number(&binding["authorizingInput"], "authorizingInput")? as u16,
                required_hash(&binding["covenantId"], "covenantId")?,
            ))
        };
        outputs.push(TransactionOutput::with_covenant(
            number(&output["value"], "output value")?,
            script,
            covenant,
        ));
    }

    let mut utxos = Vec::with_capacity(utxo_values.len());
    for utxo in utxo_values {
        let script = ScriptPublicKey::from_vec(
            number(&utxo["spkVersion"], "UTXO spkVersion")? as u16,
            bytes(&utxo["script"] )?,
        );
        utxos.push(UtxoEntry::new(
            number(&utxo["amount"], "UTXO amount")?,
            script,
            number(&utxo["daaScore"], "UTXO daaScore")?,
            utxo["isCoinbase"].as_bool().unwrap_or(false),
            optional_hash(&utxo["covenantId"] )?,
        ));
    }

    let mut tx = Transaction::new(
        version,
        inputs,
        outputs,
        number(&request["lockTime"], "lockTime")?,
        SubnetworkId::default(),
        number(&request["gas"], "gas")?,
        bytes(&request["payload"] )?,
    );
    tx.finalize();

    let populated = PopulatedTransaction::new(&tx, utxos.clone());
    let covenant_context = CovenantsContext::from_tx(&populated)
        .map_err(|e| format!("Covenant context rejected transaction: {e}"))?;
    let cache = Cache::new(10_000);
    let reused = SigHashReusedValuesUnsync::new();
    let mut input_results = Vec::with_capacity(tx.inputs.len());

    for (index, input) in tx.inputs.iter().enumerate() {
        let context = EngineCtx::new(&cache)
            .with_reused(&reused)
            .with_covenants_ctx(&covenant_context);
        let limit = input.compute_commit.allowed_script_units();
        let mut engine = TxScriptEngine::from_transaction_input_with_script_units_limit(
            &populated,
            input,
            index,
            &utxos[index],
            context,
            Default::default(),
            limit,
        );
        let result = engine.execute();
        input_results.push(json!({
            "index": index,
            "valid": result.is_ok(),
            "error": result.err().map(|e| e.to_string()),
            "usedScriptUnits": engine.used_script_units().0,
            "allowedScriptUnits": limit,
        }));
    }

    let mass_calculator = kaspa_consensus_core::mass::MassCalculator::new(
        1,
        10,
        kaspa_consensus_core::constants::STORAGE_MASS_PARAMETER,
    );
    let non_contextual = mass_calculator.calc_non_contextual_masses(&tx);
    let contextual = mass_calculator.calc_contextual_masses(&PopulatedTransaction::new(&tx, utxos));
    let valid = input_results.iter().all(|entry| entry["valid"] == true);

    Ok(json!({
        "valid": valid,
        "txid": tx.id().to_string(),
        "inputs": input_results,
        "mass": {
            "compute": non_contextual.compute_mass,
            "transient": non_contextual.transient_mass,
            "storage": contextual.map(|mass| mass.storage_mass),
        }
    }))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_example_network_kaspa_Kcc20RustyKaspaEngine_validateNative(
    mut env: JNIEnv,
    _object: JObject,
    input: JString,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let input: String = env.get_string(&input).map(|s| s.into()).unwrap_or_default();
        match validate(&input) {
            Ok(value) => value,
            Err(error) => json!({ "valid": false, "error": error }),
        }
    }))
    .unwrap_or_else(|_| json!({ "valid": false, "error": "Rusty-Kaspa engine panicked" }));

    env.new_string(result.to_string())
        .map(|value| value.into_raw())
        .unwrap_or(std::ptr::null_mut())
}
