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

fn bounded_number(value: &Value, name: &str, max: u64) -> Result<u64, String> {
    let parsed = number(value, name)?;
    if parsed > max {
        return Err(format!("{name} exceeds its consensus integer width"));
    }
    Ok(parsed)
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
    if json_text.is_empty() || json_text.len() > 4 * 1024 * 1024 {
        return Err("Rust validation request is empty or exceeds 4 MiB".to_string());
    }
    let request: Value = serde_json::from_str(json_text).map_err(|e| e.to_string())?;
    let version = bounded_number(&request["version"], "version", u16::MAX as u64)? as u16;
    if version != 1 {
        return Err("KCC20 script-engine validation requires transaction version 1".to_string());
    }

    let input_values = request["inputs"].as_array().ok_or("Missing transaction inputs")?;
    let output_values = request["outputs"].as_array().ok_or("Missing transaction outputs")?;
    let utxo_values = request["utxos"].as_array().ok_or("Missing spent UTXO entries")?;
    if input_values.is_empty() || input_values.len() > 256 || output_values.is_empty() || output_values.len() > 256 {
        return Err("Transaction must contain 1..=256 inputs and outputs".to_string());
    }
    if input_values.len() != utxo_values.len() {
        return Err("Every transaction input must have exactly one corresponding node UTXO entry".to_string());
    }

    let mut inputs = Vec::with_capacity(input_values.len());
    let mut tx_inputs_outpoints = Vec::with_capacity(input_values.len());
    let mut seen_outpoints = std::collections::HashSet::with_capacity(input_values.len());
    for input in input_values {
        let txid = required_hash(&input["txid"], "input txid")?;
        let index = bounded_number(&input["index"], "input index", u32::MAX as u64)? as u32;
        if !seen_outpoints.insert((txid, index)) {
            return Err("Duplicate transaction input outpoint".to_string());
        }
        let sequence = number(&input["sequence"], "input sequence")?;
        let signature_script = bytes(&input["sigScript"])?;
        if signature_script.len() > 100_000 {
            return Err("Input signature script exceeds 100 KiB".to_string());
        }
        let budget = bounded_number(&input["computeBudget"], "computeBudget", u16::MAX as u64)?;
        if budget == 0 {
            return Err("Version-1 input computeBudget must be non-zero".to_string());
        }
        inputs.push(TransactionInput::new_with_compute_budget(
            TransactionOutpoint::new(txid, index),
            signature_script,
            sequence,
            budget as u16,
        ));
        tx_inputs_outpoints.push((txid, index));
    }

    let mut outputs = Vec::with_capacity(output_values.len());
    let mut total_output_value = 0u128;
    for output in output_values {
        let script_bytes = bytes(&output["script"])?;
        if script_bytes.len() > 10_000 {
            return Err("Output script public key exceeds 10 KiB".to_string());
        }
        let value = number(&output["value"], "output value")?;
        total_output_value = total_output_value
            .checked_add(value as u128)
            .ok_or_else(|| "Transaction output value sum overflowed".to_string())?;
        let script = ScriptPublicKey::from_vec(
            bounded_number(&output["spkVersion"], "output spkVersion", u16::MAX as u64)? as u16,
            script_bytes,
        );
        let covenant = if output["covenant"].is_null() {
            None
        } else {
            let binding = &output["covenant"];
            let authorizing_input = bounded_number(
                &binding["authorizingInput"],
                "authorizingInput",
                (input_values.len() - 1) as u64,
            )? as u16;
            Some(CovenantBinding::new(
                authorizing_input,
                required_hash(&binding["covenantId"], "covenantId")?,
            ))
        };
        outputs.push(TransactionOutput::with_covenant(
            value,
            script,
            covenant,
        ));
    }

    let mut utxos = Vec::with_capacity(utxo_values.len());
    let mut total_input_value = 0u128;
    for (index, utxo) in utxo_values.iter().enumerate() {
        let utxo_txid = required_hash(&utxo["txid"], "UTXO outpoint txid")?;
        let utxo_index = bounded_number(&utxo["index"], "UTXO outpoint index", u32::MAX as u64)? as u32;
        let input = &tx_inputs_outpoints[index];
        if utxo_txid != input.0 || utxo_index != input.1 {
            return Err(format!("Node UTXO entry {index} does not match its transaction input outpoint"));
        }
        let script_bytes = bytes(&utxo["script"])?;
        if script_bytes.len() > 10_000 {
            return Err("Spent UTXO script public key exceeds 10 KiB".to_string());
        }
        let amount = number(&utxo["amount"], "UTXO amount")?;
        total_input_value = total_input_value
            .checked_add(amount as u128)
            .ok_or_else(|| "Transaction input value sum overflowed".to_string())?;
        let is_coinbase = utxo["isCoinbase"]
            .as_bool()
            .ok_or_else(|| "UTXO isCoinbase must be a boolean".to_string())?;
        let script = ScriptPublicKey::from_vec(
            bounded_number(&utxo["spkVersion"], "UTXO spkVersion", u16::MAX as u64)? as u16,
            script_bytes,
        );
        utxos.push(UtxoEntry::new(
            amount,
            script,
            number(&utxo["daaScore"], "UTXO daaScore")?,
            is_coinbase,
            optional_hash(&utxo["covenantId"] )?,
        ));
    }
    if total_input_value < total_output_value {
        return Err("Transaction outputs exceed the node-confirmed input value".to_string());
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
    let contextual = mass_calculator
        .calc_contextual_masses(&PopulatedTransaction::new(&tx, utxos))
        .ok_or_else(|| "Rusty-Kaspa could not calculate contextual storage mass".to_string())?;
    let scripts_valid = input_results.iter().all(|entry| entry["valid"] == true);

    Ok(json!({
        "valid": scripts_valid,
        "txid": tx.id().to_string(),
        "inputs": input_results,
        "mass": {
            "compute": non_contextual.compute_mass,
            "transient": non_contextual.transient_mass,
            "storage": contextual.storage_mass,
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bounded_number_rejects_consensus_width_overflow() {
        assert!(bounded_number(&json!(65_536u64), "version", u16::MAX as u64).is_err());
        assert_eq!(bounded_number(&json!("65535"), "version", u16::MAX as u64).unwrap(), 65_535);
    }

    #[test]
    fn validate_rejects_duplicate_input_outpoints() {
        let txid = "00".repeat(32);
        let input = json!({
            "txid": txid,
            "index": 0,
            "sequence": 0,
            "computeBudget": 1,
            "sigScript": ""
        });
        let request = json!({
            "version": 1,
            "inputs": [input.clone(), input],
            "outputs": [{}],
            "utxos": [{}, {}]
        });
        let error = validate(&request.to_string()).unwrap_err();
        assert!(error.contains("Duplicate transaction input outpoint"));
    }

    #[test]
    fn validate_rejects_node_utxo_for_another_outpoint() {
        let request = json!({
            "version": 1,
            "lockTime": 0,
            "gas": 0,
            "payload": "",
            "inputs": [{
                "txid": "00".repeat(32),
                "index": 0,
                "sequence": 0,
                "computeBudget": 1,
                "sigScript": ""
            }],
            "outputs": [{
                "value": 0,
                "spkVersion": 0,
                "script": "51",
                "covenant": null
            }],
            "utxos": [{
                "txid": "11".repeat(32),
                "index": 0,
                "amount": "1",
                "spkVersion": 0,
                "script": "51",
                "daaScore": "0",
                "isCoinbase": false,
                "covenantId": null
            }]
        });
        let error = validate(&request.to_string()).unwrap_err();
        assert!(error.contains("does not match its transaction input outpoint"));
    }
}
