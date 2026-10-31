use kaspa_consensus_core::{
    subnets::SubnetworkId,
    tx::{
        CovenantBinding, PopulatedTransaction, Transaction, TransactionInput, TransactionOutpoint,
        TransactionOutput, UtxoEntry,
    },
};
use kaspa_hashes::Hash;
use kaspa_txscript::{
    caches::Cache,
    covenants::CovenantsContext,
    EngineCtx, EngineFlags, SigCacheKey, TxScriptEngine,
};
use kaspa_consensus_core::hashing::sighash::SigHashReusedValuesUnsync;
use serde_json::Value;
use std::{env, error::Error, fs, str::FromStr};

fn field<'a>(value: &'a Value, camel: &str, snake: &str) -> Result<&'a Value, Box<dyn Error>> {
    value.get(camel).or_else(|| value.get(snake)).ok_or_else(|| {
        format!("missing required field {camel}/{snake}").into()
    })
}

fn string<'a>(value: &'a Value, camel: &str, snake: &str) -> Result<&'a str, Box<dyn Error>> {
    field(value, camel, snake)?.as_str().ok_or_else(|| {
        format!("field {camel}/{snake} must be a string").into()
    })
}

fn number(value: &Value, camel: &str, snake: &str) -> Result<u64, Box<dyn Error>> {
    let raw = field(value, camel, snake)?;
    if let Some(n) = raw.as_u64() {
        return Ok(n);
    }
    if let Some(s) = raw.as_str() {
        return Ok(s.parse()?);
    }
    Err(format!("field {camel}/{snake} must be an unsigned integer").into())
}

fn parse_hash(raw: &str) -> Result<Hash, Box<dyn Error>> {
    Ok(Hash::from_str(raw)?)
}

fn parse_spk(value: &Value) -> Result<kaspa_consensus_core::tx::ScriptPublicKey, Box<dyn Error>> {
    let version = number(value, "version", "version")? as u16;
    let script = hex::decode(string(value, "script", "script")?)?;
    Ok(kaspa_consensus_core::tx::ScriptPublicKey::from_vec(version, script))
}

fn parse_fixture(root: &Value) -> Result<(Transaction, Vec<UtxoEntry>, String), Box<dyn Error>> {
    let tx_json = root.get("transaction").ok_or("missing transaction object")?;
    let inputs_json = tx_json.get("inputs").and_then(Value::as_array).ok_or("transaction.inputs must be an array")?;
    let outputs_json = tx_json.get("outputs").and_then(Value::as_array).ok_or("transaction.outputs must be an array")?;
    let entries_json = root.get("utxoEntries").or_else(|| root.get("utxo_entries"))
        .and_then(Value::as_array).ok_or("utxoEntries must be an array")?;

    if inputs_json.len() != entries_json.len() {
        return Err("input count does not match populated UTXO entry count".into());
    }

    let mut inputs = Vec::with_capacity(inputs_json.len());
    for input in inputs_json {
        let outpoint = field(input, "previousOutpoint", "previous_outpoint")?;
        let txid = string(outpoint, "transactionId", "transaction_id")?;
        let index = number(outpoint, "index", "index")? as u32;
        let signature_script = hex::decode(string(input, "signatureScript", "signature_script")?)?;
        let sequence = number(input, "sequence", "sequence")?;
        let sigops = number(input, "sigOpCount", "sig_op_count").unwrap_or(1).min(255) as u8;
        inputs.push(TransactionInput::new(
            TransactionOutpoint::new(parse_hash(txid)?, index),
            signature_script,
            sequence,
            sigops,
        ));
    }

    let mut outputs = Vec::with_capacity(outputs_json.len());
    for output in outputs_json {
        let amount = number(output, "amount", "value")?;
        let spk_json = field(output, "scriptPublicKey", "script_public_key")?;
        let spk = parse_spk(spk_json)?;
        let binding = output.get("covenant").filter(|v| !v.is_null()).map(|v| {
            Ok::<CovenantBinding, Box<dyn Error>>(CovenantBinding::new(
                number(v, "authorizingInput", "authorizing_input")? as u16,
                parse_hash(string(v, "covenantId", "covenant_id")?)?,
            ))
        }).transpose()?;
        outputs.push(TransactionOutput::with_covenant(amount, spk, binding));
    }

    let version = number(tx_json, "version", "version")? as u16;
    let lock_time = number(tx_json, "lockTime", "lock_time")?;
    let gas = number(tx_json, "gas", "gas")?;
    let payload_hex = string(tx_json, "payload", "payload")?;
    let payload = if payload_hex.is_empty() { Vec::new() } else { hex::decode(payload_hex)? };
    let tx = Transaction::new(
        version, inputs, outputs, lock_time, SubnetworkId::default(), gas, payload
    );

    let mut entries = Vec::with_capacity(entries_json.len());
    for entry in entries_json {
        let amount = number(entry, "amount", "amount")?;
        let spk = parse_spk(field(entry, "scriptPublicKey", "script_public_key")?)?;
        let daa = number(entry, "blockDaaScore", "block_daa_score")?;
        let coinbase = field(entry, "isCoinbase", "is_coinbase")?.as_bool().ok_or("isCoinbase must be boolean")?;
        let covenant_id = entry.get("covenantId").or_else(|| entry.get("covenant_id"))
            .and_then(Value::as_str).filter(|s| !s.is_empty())
            .map(parse_hash).transpose()?;
        entries.push(UtxoEntry::new(amount, spk, daa, coinbase, covenant_id));
    }

    let required_id = root.get("requiredCovenantId").or_else(|| root.get("required_covenant_id"))
        .and_then(Value::as_str).unwrap_or("").to_owned();
    Ok((tx, entries, required_id))
}

fn main() -> Result<(), Box<dyn Error>> {
    let path = env::var("KCC20_PREFLIGHT_FIXTURE").map_err(|_| {
        "set KCC20_PREFLIGHT_FIXTURE to a fixture containing the transaction and exact populated UTXO entries"
    })?;
    let root: Value = serde_json::from_str(&fs::read_to_string(path)?)?;
    let (tx, entries, required_covenant_id) = parse_fixture(&root)?;

    if !required_covenant_id.is_empty() {
        let expected = parse_hash(&required_covenant_id)?;
        for (index, entry) in entries.iter().enumerate() {
            if entry.covenant_id != Some(expected) {
                return Err(format!("input {index} does not carry required covenant id {required_covenant_id}").into());
            }
        }
    }

    let populated = PopulatedTransaction::new(&tx, entries);
    let covenants_ctx = CovenantsContext::from_tx(&populated)?;
    let sig_cache: Cache<SigCacheKey, bool> = Cache::new(10_000);
    let reused_values = SigHashReusedValuesUnsync::new();
    let flags = EngineFlags::default();

    for index in 0..tx.inputs.len() {
        let utxo_entry = &populated.entries[index];
        let context = EngineCtx::new(&sig_cache)
            .with_reused(&reused_values)
            .with_covenants_ctx(&covenants_ctx);
        let mut engine = TxScriptEngine::from_transaction_input(
            &populated,
            &tx.inputs[index],
            index,
            utxo_entry,
            context,
            flags,
        );
        engine.execute().map_err(|error| {
            format!("consensus script-engine rejected input {index}: {error:?}")
        })?;
    }

    println!(
        "{{\"ok\":true,\"txid\":\"{}\",\"inputs_checked\":{},\"engine\":\"rusty-kaspa-v2.1.0\"}}",
        tx.id(),
        tx.inputs.len()
    );
    Ok(())
}
