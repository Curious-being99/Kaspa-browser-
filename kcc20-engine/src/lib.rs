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

const KCC20_ARTIFACT_FIXTURE: &str = include_str!("../../app/src/test/resources/kcc20-reference/artifact-template.json");

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


fn decode_hex(value: &str) -> Result<Vec<u8>, String> {
    bytes(&Value::String(value.to_string()))
}

fn hex_string(value: &[u8]) -> String {
    value.iter().map(|b| format!("{b:02x}")).collect()
}

fn explicit_push(value: &[u8]) -> Vec<u8> {
    let mut out = Vec::new();
    match value.len() {
        0 => out.push(0),
        1..=75 => out.push(value.len() as u8),
        76..=255 => { out.push(0x4c); out.push(value.len() as u8); },
        256..=65535 => { out.push(0x4d); out.extend_from_slice(&(value.len() as u16).to_le_bytes()); },
        _ => return Vec::new(),
    }
    out.extend_from_slice(value);
    out
}

fn decode_reference_state(program: &[u8], template_hash: &[u8], suffix: &[u8]) -> Result<(u64, Vec<u8>, u8, u8, Vec<u8>, Vec<u8>), String> {
    if program.len() != 4031 || program[0] != 0x6b || program.get(146..) != Some(suffix) { return Err("Not the exact pinned Argent KCC20 reference program".into()); }
    let state = &program[1..146];
    let mut p = 0usize;
    let mut field = |n: usize| -> Result<Vec<u8>, String> {
        if p >= state.len() || state[p] as usize != n { return Err("Malformed KCC20 state push".into()); }
        p += 1;
        if p + n > state.len() { return Err("Truncated KCC20 state".into()); }
        let v = state[p..p+n].to_vec(); p += n; Ok(v)
    };
    if field(32)? != template_hash { return Err("Unknown KCC20 template hash".into()); }
    let raw_amount = field(8)?;
    let amount = u64::from_le_bytes(raw_amount.try_into().map_err(|_| "Invalid token amount")?);
    if amount > i64::MAX as u64 { return Err("KCC20 amount exceeds Argent signed-int64 range".into()); }
    let owner = field(32)?;
    let owner_scheme = field(1)?[0];
    let borrow_scheme = field(1)?[0];
    let borrow_guard = field(32)?;
    let extension = field(32)?;
    if p != state.len() { return Err("Unexpected KCC20 state trailing bytes".into()); }
    Ok((amount, owner, owner_scheme, borrow_scheme, borrow_guard, extension))
}

fn encode_reference_program(amount: u64, owner: &[u8], owner_scheme: u8, borrow_scheme: u8, borrow_guard: &[u8], extension: &[u8], template_hash: &[u8], suffix: &[u8]) -> Result<Vec<u8>, String> {
    if owner.len()!=32 || borrow_guard.len()!=32 || extension.len()!=32 || template_hash.len()!=32 { return Err("Invalid KCC20 state field length".into()); }
    let mut state = Vec::new();
    for field in [template_hash.to_vec(), amount.to_le_bytes().to_vec(), owner.to_vec(), vec![owner_scheme], vec![borrow_scheme], borrow_guard.to_vec(), extension.to_vec()] {
        state.extend(explicit_push(&field));
    }
    if state.len()!=145 { return Err("Generated KCC20 state has wrong length".into()); }
    let mut program=vec![0x6b]; program.extend(state); program.extend_from_slice(suffix);
    if program.len()!=4031 { return Err("Generated KCC20 program has wrong length".into()); }
    Ok(program)
}


fn minimal_push(value: &[u8]) -> Vec<u8> {
    if value.is_empty() || (value.len()==1 && value[0]==0) { return vec![0]; }
    if value.len()==1 && (1..=16).contains(&value[0]) { return vec![0x50+value[0]]; }
    if value.len()==1 && value[0]==0x81 { return vec![0x4f]; }
    explicit_push(value)
}

fn assemble_reference_witness(request_text: &str) -> Result<String, String> {
    let req: Value=serde_json::from_str(request_text).map_err(|e|e.to_string())?;
    let template=decode_hex(req["templateHash"].as_str().ok_or("Missing template hash")?)?;
    let suffix=decode_hex(req["templateSuffix"].as_str().ok_or("Missing template suffix")?)?;
    let pinned: Value=serde_json::from_str(KCC20_ARTIFACT_FIXTURE).map_err(|e|e.to_string())?;
    if template!=decode_hex(pinned["template_hash"].as_str().ok_or("Missing pinned template")?)? ||
       suffix!=decode_hex(pinned["suffix_hex"].as_str().ok_or("Missing pinned suffix")?)? {
        return Err("Witness template differs from pinned Argent artifact".into());
    }
    let signature=decode_hex(req["signature65"].as_str().ok_or("Missing 65-byte signature")?)?;
    if signature.len()!=65 || signature[64]!=1 { return Err("KCC20 witness requires 65-byte SIGHASH_ALL signature".into()); }
    let program=decode_hex(req["program"].as_str().ok_or("Missing consumed program")?)?;
    let (amount,owner,owner_scheme,borrow_scheme,guard,extension)=decode_reference_state(&program,&template,&suffix)?;
    if owner_scheme!=0 || borrow_scheme!=0 || guard.iter().any(|b|*b!=0) || extension.iter().any(|b|*b!=0) {
        return Err("Witness assembly only supports the guide-verified normal P2PK path".into());
    }
    let leader=req["leader"].as_bool().ok_or("Missing leader flag")?;
    let mut script=Vec::new();
    if leader {
        let states=req["nextStates"].as_array().ok_or("Missing successor states")?;
        if states.is_empty() || states.len()>3 { return Err("KCC20 leader witness requires 1 to 3 successor states".into()); }
        let mut amounts=Vec::new(); let mut owners=Vec::new(); let mut owner_schemes=Vec::new();
        let mut borrow_schemes=Vec::new(); let mut guards=Vec::new(); let mut extensions=Vec::new();
        for item in states {
            let successor=decode_hex(item.as_str().ok_or("Invalid successor program")?)?;
            let (a,o,os,bs,g,e)=decode_reference_state(&successor,&template,&suffix)?;
            if bs!=0 || g.iter().any(|b|*b!=0) || e.iter().any(|b|*b!=0) { return Err("Unsupported successor policy".into()); }
            amounts.extend_from_slice(&a.to_le_bytes()); owners.extend(o); owner_schemes.push(os); borrow_schemes.push(bs); guards.extend(g); extensions.extend(e);
        }
        for column in [amounts,owners,owner_schemes,borrow_schemes,guards,extensions] { script.extend(minimal_push(&column)); }
        let mut sig=vec![0]; sig.extend(signature);
        script.extend(minimal_push(&sig));
        script.extend_from_slice(&[4,0x79,0xc7,0x1c,0x23]);
    } else {
        script.extend(minimal_push(&signature));
        script.extend_from_slice(&[4,0xfd,0x3e,0xf1,0x4a]);
    }
    script.extend(minimal_push(&program));
    let _=amount;
    Ok(hex_string(&script))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_example_network_kaspa_Kcc20RustyKaspaEngine_assembleWitnessNative(
    mut env: JNIEnv, _object: JObject, input: JString
) -> jstring {
    let result=std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let input:String=env.get_string(&input).map(|s|s.into()).unwrap_or_default();
        match assemble_reference_witness(&input) { Ok(v)=>json!({"valid":true,"script":v}), Err(e)=>json!({"valid":false,"error":e}) }
    })).unwrap_or_else(|_|json!({"valid":false,"error":"Rust KCC20 witness assembler panicked"}));
    env.new_string(result.to_string()).map(|v|v.into_raw()).unwrap_or(std::ptr::null_mut())
}

fn plan_reference_transfer(request_text: &str) -> Result<Value, String> {
    if request_text.len()>1024*1024 { return Err("Transfer plan request too large".into()); }
    let req: Value=serde_json::from_str(request_text).map_err(|e|e.to_string())?;
    let family=req["covenantId"].as_str().ok_or("Missing covenant ID")?;
    if family.len()!=64 || !family.bytes().all(|b|b.is_ascii_hexdigit()) { return Err("Invalid covenant ID".into()); }
    let sender=decode_hex(req["senderPublicKey"].as_str().ok_or("Missing sender key")?)?;
    let recipient=decode_hex(req["recipientPublicKey"].as_str().ok_or("Missing recipient key")?)?;
    let template=decode_hex(req["templateHash"].as_str().ok_or("Missing template hash")?)?;
    let suffix=decode_hex(req["templateSuffix"].as_str().ok_or("Missing template suffix")?)?;
    let pinned: Value=serde_json::from_str(KCC20_ARTIFACT_FIXTURE).map_err(|e|format!("Invalid embedded Argent artifact fixture: {e}"))?;
    let pinned_template=decode_hex(pinned["template_hash"].as_str().ok_or("Pinned artifact has no template hash")?)?;
    let pinned_suffix=decode_hex(pinned["suffix_hex"].as_str().ok_or("Pinned artifact has no template suffix")?)?;
    if template!=pinned_template || suffix!=pinned_suffix { return Err("Planner template bytes do not match the embedded, pinned Argent artifact".into()); }
    if sender.len()!=32 || recipient.len()!=32 || template.len()!=32 { return Err("Invalid KCC20 public key or template length".into()); }
    let requested=number(&req["amount"],"amount")?;
    if requested==0 || requested>i64::MAX as u64 { return Err("Transfer amount must be positive and within Argent signed-int64 range".into()); }
    let cells=req["cells"].as_array().ok_or("Missing verified cells")?;
    if cells.is_empty() || cells.len()>3 { return Err("Reference transfer requires 1 to 3 verified cells".into()); }
    let mut seen=std::collections::HashSet::new();
    let mut consumed=Vec::new();
    let mut total=0u64;
    let mut first_guard=Vec::new(); let mut first_extension=Vec::new();
    for (i,cell) in cells.iter().enumerate() {
        if cell["covenantId"].as_str().unwrap_or("").to_lowercase()!=family.to_lowercase() { return Err("Cell covenant family mismatch".into()); }
        let outpoint=format!("{}:{}",cell["txid"].as_str().unwrap_or(""),number(&cell["index"],"index")?);
        if !seen.insert(outpoint) { return Err("Duplicate KCC20 cell outpoint".into()); }
        let program=decode_hex(cell["program"].as_str().ok_or("Missing cell program")?)?;
        let (amount,owner,owner_scheme,borrow_scheme,guard,extension)=decode_reference_state(&program,&template,&suffix)?;
        if owner_scheme!=0 || owner!=sender { return Err("Cell is not owned by active wallet under supported P2PK scheme".into()); }
        if borrow_scheme!=0 || guard.iter().any(|b|*b!=0) || extension.iter().any(|b|*b!=0) { return Err("Cell uses an unsupported borrow or extension policy".into()); }
        if i==0 { first_guard=guard.clone(); first_extension=extension.clone(); }
        else if guard!=first_guard || extension!=first_extension { return Err("KCC20 cells have incompatible state policy".into()); }
        total=total.checked_add(amount).ok_or("KCC20 token amount overflow")?;
        consumed.push(json!({"amount":amount.to_string(),"program":hex_string(&program)}));
    }
    if requested>total { return Err("Transfer amount exceeds verified token inputs".into()); }
    let change=total-requested;
    let mut next=vec![encode_reference_program(requested,&recipient,0,0,&vec![0;32],&first_extension,&template,&suffix)?];
    if change>0 { next.push(encode_reference_program(change,&sender,0,0,&first_guard,&first_extension,&template,&suffix)?); }
    if next.len()>3 { return Err("Too many KCC20 successor cells".into()); }
    Ok(json!({
      "valid":true,
      "covenantId":family.to_lowercase(),
      "inputAmount":total.to_string(),
      "outputAmount":total.to_string(),
      "consumed":consumed,
      "successorPrograms":next.iter().map(|p|hex_string(p)).collect::<Vec<_>>()
    }))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_example_network_kaspa_Kcc20RustyKaspaEngine_planTransferNative(
    mut env: JNIEnv, _object: JObject, input: JString
) -> jstring {
    let result=std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let input:String=env.get_string(&input).map(|s|s.into()).unwrap_or_default();
        match plan_reference_transfer(&input) { Ok(v)=>v, Err(e)=>json!({"valid":false,"error":e}) }
    })).unwrap_or_else(|_|json!({"valid":false,"error":"Rust KCC20 planner panicked"}));
    env.new_string(result.to_string()).map(|v|v.into_raw()).unwrap_or(std::ptr::null_mut())
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
    fn rust_planner_conserves_token_amount_and_builds_successors() {
        let pinned: Value = serde_json::from_str(KCC20_ARTIFACT_FIXTURE).unwrap();
        let template = decode_hex(pinned["template_hash"].as_str().unwrap()).unwrap();
        let suffix = decode_hex(pinned["suffix_hex"].as_str().unwrap()).unwrap();
        let sender = vec![0x33; 32];
        let recipient = vec![0x44; 32];
        let program = encode_reference_program(100, &sender, 0, 0, &vec![0;32], &vec![0;32], &template, &suffix).unwrap();
        let request = json!({
            "covenantId": "aa".repeat(32),
            "senderPublicKey": hex_string(&sender),
            "recipientPublicKey": hex_string(&recipient),
            "amount": "40",
            "templateHash": hex_string(&template),
            "templateSuffix": hex_string(&suffix),
            "cells": [{
                "txid": "bb".repeat(32), "index": 0,
                "covenantId": "aa".repeat(32),
                "program": hex_string(&program)
            }]
        });
        let result = plan_reference_transfer(&request.to_string()).unwrap();
        assert_eq!(result["inputAmount"], "100");
        assert_eq!(result["outputAmount"], "100");
        assert_eq!(result["successorPrograms"].as_array().unwrap().len(), 2);
    }

    #[test]
    fn rust_planner_rejects_program_with_unpinned_suffix() {
        let pinned: Value = serde_json::from_str(KCC20_ARTIFACT_FIXTURE).unwrap();
        let template = decode_hex(pinned["template_hash"].as_str().unwrap()).unwrap();
        let suffix = decode_hex(pinned["suffix_hex"].as_str().unwrap()).unwrap();
        let sender = vec![0x33; 32];
        let recipient = vec![0x44; 32];
        let program = encode_reference_program(100, &sender, 0, 0, &vec![0;32], &vec![0;32], &template, &suffix).unwrap();
        let request = json!({
            "covenantId": "aa".repeat(32),
            "senderPublicKey": hex_string(&sender),
            "recipientPublicKey": hex_string(&recipient),
            "amount": "40",
            "templateHash": hex_string(&template),
            "templateSuffix": hex_string(&vec![0x23;3885]),
            "cells": [{
                "txid": "bb".repeat(32), "index": 0,
                "covenantId": "aa".repeat(32),
                "program": hex_string(&program)
            }]
        });
        assert!(plan_reference_transfer(&request.to_string()).is_err());
    }

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
