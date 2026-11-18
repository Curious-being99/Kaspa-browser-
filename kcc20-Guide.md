# KCC-20 on Kaspa L1: a reference for coding agents

Version 2, 2026-11-17. Semantics: rusty-kaspa v2.1.0 (tag `v2.1.0`, commit `01b532e8`), Kaspa WASM SDK v2.1.0. Networks: `mainnet`, `testnet-10`.

This file is for an AI that builds KCC-20 software: wallets, senders, indexers, pools, swap pages. Follow it literally. Every fact carries its source. Where this file says "not established", do not fill the gap with a guess.

## 0. How to read this file

### 0.1 Source keys

| Key | What it points to |
|---|---|
| `REF` | The KCC-20 reference program: github.com/argent-lang/kcc20-reference, commit `c8a0871`, app `KCC20PublicMint`, contract `KCC20`, artifact id `b48c90c8e287d227821ac40f0875a753e329e1ab3c8fdfa1477f55419cfa26ab` (generator `argentc` 0.1.0, as the artifact states). Specification: github.com/kaspanet/kccs, `kcc-0020.md` at `3fbec52` (Last Call), with KCC-1 for encodings. |
| `RK:path` | A file in rusty-kaspa at tag `v2.1.0`. |
| `TX:<txid>` | A mainnet transaction. Every `TX` in this file was fetched from `https://api.kaspa.org/transactions/<txid>` on 2026-11-17 and is accepted. |
| `ENGINE` | Checked on 2026-11-17 by running the rusty-kaspa v2.1.0 script engine through the harness in Appendix B. |
| `RUN` | A code block in this file that was executed on 2026-11-17, with the result stated beside it. |

### 0.2 Words

- **Cell**: one UTXO holding KCC-20 tokens.
- **Family**: all cells of one token. Its identity is one covenant ID.
- **Leader**: the cell input that calls `transfer` and authorizes the outputs.
- **Delegator**: another cell input of the same family that calls `transfer_delegator`.
- **Continuation**: an output bound to the family's covenant ID and authorized by the leader.
- **Base units**: the integer amount in a cell. Decimals are display metadata; they are not in the cell.

## 1. The model in one page

1. A cell is a P2SH output: scriptPublicKey version 0, script `aa20 <BLAKE2b-256(program)> 87`.
2. The program (redeem script) is the reference KCC20 program with the cell's state written into a fixed window. The program is 4,031 bytes: a 1-byte prefix (`6b`), the 145-byte state, a 3,885-byte suffix. Everything outside the window is the same in every reference cell (REF; RUN in 1.6).
3. The program's template hash is `9703112ee6e3555107cd168858992b77d3b74f655205b2b463b1f9ec2ec73cf7` = KCC-1 `TemplateHash(prefix, suffix)` = `BLAKE3(le64(len prefix) ‖ prefix ‖ le64(len suffix) ‖ suffix)`. The state's first field repeats this hash, so a cell carries its own program's identity (REF; RUN in 1.6).
4. The token's identity is its **covenant ID** (KIP-20), never its name or ticker. Every cell of the family is bound to the same 32-byte covenant ID.
5. The covenant ID is fixed at genesis: `covenant_id(outpoint of the authorizing input; for each bound output: index, value, spk version, spk script)` (`RK:consensus/core/src/hashing/covenant_id.rs`). Unbound outputs (change, fees) do not enter it. ENGINE: a genesis with its bound output's value changed but its ID kept is refused with "do not correspond to the expected genesis hashing"; changing an unbound output leaves the ID valid (B.4).
6. After genesis, an output bound to covenant ID X must be authorized by an input that carries X (a continuation). An output bound to an ID its authorizing input does not carry is a new genesis; its ID is recomputed and checked. An input's "authorized outputs", as the program sees them, are only its continuations (`RK:crypto/txscript/src/covenants.rs` `CovenantsContext::from_tx`).
7. Because the state is inside the program, **a cell's address changes whenever its state changes**. The covenant ID does not. Never key a balance by address.

### 1.1 State fields, in order (145 bytes)

Each field is a KCC-1 explicit push: one length byte, then the payload.

| # | Field | Push | Payload |
|---|---|---|---|
| 0 | template | `20` | 32 bytes, the template hash |
| 1 | `amount` | `08` | 8 bytes, little-endian signed-magnitude (the top bit of byte 7 is the sign) |
| 2 | `owner` | `20` | 32 bytes; meaning set by `owner_scheme` |
| 3 | `owner_scheme` | `01` | 1 byte, 0x00 to 0x04 |
| 4 | `borrow_scheme` | `01` | 1 byte, 0x00 to 0x03 |
| 5 | `borrow_guard` | `20` | 32 bytes |
| 6 | `extension_commitment` | `20` | 32 bytes; all zeros for a plain token |

33 + 9 + 33 + 2 + 2 + 33 + 33 = 145. RUN: `encodeState` (A.2) reproduces the KCC-20 EncodeState conformance vector (amount 1000, owner `11…11`, scheme 1, borrow 1, guard `f401000000000000` + zeros).

### 1.2 Owner schemes (REF, `require_owner_authorized`)

| Scheme | `owner` holds | Witness the owner supplies |
|---|---|---|
| 0x00 P2PK Schnorr | 32-byte x-only public key | 65 bytes: 64-byte Schnorr signature ‖ sighash byte |
| 0x01 P2PKH Schnorr | BLAKE3(32-byte public key) | 97 bytes: public key ‖ 65-byte signature |
| 0x02 P2PKH ECDSA | BLAKE3(33-byte public key) | 98 bytes: public key ‖ 65-byte signature |
| 0x03 P2SH | 32-byte script hash | 1 byte: the index of an input whose scriptPublicKey is `P2SH(owner)` |
| 0x04 Covenant ID | 32-byte covenant ID | empty; the transaction must spend at least one input carrying that covenant ID |

A regular `kaspa:q…` address is scheme 0x00 with the address payload as `owner`.

Scheme 0x04 hands the cell to whatever program owns that covenant ID: the cell only checks that an input of that ID is present. The owning program must therefore constrain every cell it owns in every transaction it can be part of; otherwise any transaction that spends the owner can move all its cells (REF).

### 1.3 Borrow schemes (REF, `transfer`, path 0x01)

0x00 disabled; 0x01 amount threshold (first 8 bytes of `borrow_guard`); 0x02 a Schnorr key in `borrow_guard`; 0x03 a hash chain. The borrow path lets output 0 receive more tokens with the same owner without the owner's signature, and requires output 0 to carry at least the leader input's KAS. No borrow-path spend is used or verified in this guide (section 10).

### 1.4 Entries and their rules (REF)

`transfer(KCC20State[] next_states, byte[] witness)`, dispatch tag `79c71c23`. Enforced by the leader:

- The leader is the first input of its family in input order (`OpCovInputIdx(cov, 0) == this input`).
- 0 to 2 delegators: at most 3 cell inputs per family per transaction.
- 1 to 3 continuations. Every output of the family in the transaction is authorized by the leader (`OpCovOutputCount == OpAuthOutputCount`).
- `next_states.length` equals the number of continuations; `next_states[k]` describes the k-th continuation in output order.
- Sum of output amounts equals sum of input amounts (leader plus delegators). Every output amount is at least 0; zero is allowed.
- Every output's owner scheme is at most 0x04, borrow scheme at most 0x03.
- Every delegator and every output has the leader's `extension_commitment`.
- Each continuation is exactly the program holding its `next_states[k]`.
- `witness[0]` is the path: 0x00 normal (the owner authorizes with the rest of the witness), 0x01 borrow.

`transfer_delegator(byte[] witness)`, dispatch tag `fd3ef14a`. Rules: at least 2 inputs of the family; this input is not the first; it authorizes no outputs; its owner authorizes with the witness. **No path byte** in a delegator witness.

Each tag is the first 4 bytes of BLAKE3 of the entry's signature. RUN: `BLAKE3("transfer({int,byte[32],byte,byte,byte[32],byte[32]}[],byte[])")` starts `79c71c23`.

The program has no mint, no burn and no admin key. Supply comes only from a genesis or from another program of the family (3.5).

### 1.5 Address of a cell from its state

```
program  = prefix (1 byte) ‖ EncodeState(state) (145 bytes) ‖ suffix (3,885 bytes)
spk      = aa20 ‖ BLAKE2b-256(program) ‖ 87          (BLAKE2b, 32-byte digest, no key)
address  = bech32 with prefix "kaspa" (mainnet) or "kaspatest" (testnets), version byte 8, payload BLAKE2b-256(program)
```

Kaspa's bech32 checksum masks each prefix character with `& 0x1f` and appends a zero, and uses a 40-bit BCH polynomial; a Bitcoin bech32 library gives wrong checksums. A.2 contains an encoder. RUN: A.2 rebuilt the program of the live cell `TX:36cecdf023f0a6c54c8090d9f1c85e8130eee51a9538db715dd15b7a29cde576` output 2 from its state and got its on-chain address `kaspa:pz7qn55n90rrghs05cyzc2qajf77wvz3n6z5jywp7tu2h7dvwcv5xf4qft0wq`.

### 1.6 Where to get the program bytes

The program is revealed in full as the last push of every cell spend. A.3 extracts it and checks its template hash. Pin it once, then compare byte for byte. RUN on `TX:de36063206e944bb1716645b03fbb5198f2079452286744d31b05ce8511e74f8` input 0 and `TX:c03d5ea0f459aefdd08e767f9728da60aa63eacd7839ae1befb190e383c0c466` input 1: 4,031 bytes, template hash `9703112e…3cf7`, self-consistent. The source text is in the REF repository.

Other cell programs exist on mainnet: the same contract compiled with other limits, issuer-controlled families with a larger state, older layouts. They are not the reference program. A program whose template hash you have not pinned is not the reference, whatever its tags say: its own state can name its own template hash, so self-consistency alone proves nothing about its rules.

## 2. Reading balances

### 2.1 Where cells come from

rusty-kaspa v2.1.0 has no query "all cells of covenant ID X" or "all cells owned by key K". A cell owned by key K sits at a P2SH address that only exists once the state is known, so `getUtxosByAddresses(K's address)` never returns it. Balances therefore come from an index: a service, or your own scanner, that reads accepted transactions, decodes each leader's `next_states` from its signature script, and records the continuations.

What a usable index result must give you for each cell: `txid`, `vout`, the KAS it carries, the covenant ID, and either the program (`redeemScriptHex`) or the full state so you can rebuild the program. Some indexes require an API key; that is the operator's rule, not the protocol's.

Indexer rules that matter (RK `covenants.rs`; REF):

- One transaction can carry several families, one leader each (a token-for-token trade). Decode every leader, not the first one.
- A leader's continuations are the outputs it authorizes **in its own covenant ID**. The same input may also authorize a genesis of another covenant (a market listing, a pool); those outputs are not cells of this family.
- Group delegators with their leader by the covenant ID of the coin they spend.
- Owners other than scheme 0x00 have no `kaspa:q…` address. Store them as scheme plus 32-byte owner.

### 2.2 Why an index result is only a candidate

An index is a ledger, not your node. It can list a cell that was spent since, a cell that was never accepted, or bytes that do not hash to the address. Treat every result as a claim to check.

### 2.3 Checks before spending a cell (all MUST pass)

1. **Program.** The program is 4,031 bytes and equals the pinned reference program byte for byte outside the state window (bytes 1 to 145).
2. **State.** The window parses as the seven pushes of 1.1 exactly; the template field equals `9703112e…3cf7`; the amount is not negative.
3. **Rebuild.** Encoding the parsed state into the pinned program gives the same bytes.
4. **Address.** The P2SH of the program, encoded for your network, equals the cell's address.
5. **Node.** Your own node's `getUtxosByAddresses([address])` holds the outpoint `txid:vout`; its amount equals the KAS you expect; its entry's covenant ID equals the family's covenant ID.
6. **Family.** That covenant ID is the token you mean: from the user, a list you pinned, or a genesis you checked. Never from a name or ticker.

A.4 implements checks 1 to 5; you supply 6. RUN on the live cell `TX:36cecdf0…` output 2: the true cell passes; the same cell with an edited amount is refused at check 4 (the address no longer matches); expecting another family is refused at check 5; an outpoint the address does not hold (`TX:f3fe248b…` output 1) is refused at check 5; one changed byte outside the window is refused at check 1. The public REST endpoint `/addresses/<address>/utxos` does not return covenant IDs, so the RUN took the ID from the creating output; a wallet MUST use its own node's UTXO entry.

## 3. Transaction shapes

Common to every shape:

- Version 1. On v1 every input has `sigOpCount` 0 and a `computeBudget` (section 4). The SDK's `createTransaction` returns a version 0 transaction with `sigOpCount` 1 on each input (RUN with SDK v2.1.0); set `version = 1`, `sigOpCount = 0` and the budgets before anyone signs.
- Every continuation carries `covenant: { authorizingInput: <leader's input index>, covenantId: <family ID> }`. Change and fee outputs carry none.
- For scheme 0x00 cells and plain KAS inputs, every signature is Schnorr with sighash type `01` (SIGHASH_ALL).
- The v1 sighash covers the version, every input outpoint and sequence, the spent UTXO's script and amount, every output with its covenant binding, the lock time, subnetwork, gas and payload. It does not cover compute budgets or signature scripts (`RK:consensus/core/src/hashing/sighash.rs` `calc_schnorr_signature_hash`, `hash_output`). ENGINE on `TX:de36063206e944bb…`: changing one output by 1 sompi breaks both signatures; changing compute budgets does not.
- The v1 transaction ID excludes signature scripts, compute budgets and the mass field, and includes the payload (`RK:consensus/core/src/hashing/tx.rs` `id_v1`). The ID is known before signing.
- Leader signature script, owner scheme 0x00, normal path:

```
PushMinimal(amounts) PushMinimal(owners) PushMinimal(owner_schemes) PushMinimal(borrow_schemes) PushMinimal(borrow_guards) PushMinimal(extension_commitments)
PushMinimal(00 ‖ 64-byte signature ‖ 01)
04 79c71c23
PushMinimal(program)
```

  Each of the first six pushes holds that field of every next state, concatenated in order (KCC-1 §3.4.6). Exact bytes of the KCC-20 conformance vector for two next states (600 to owner `22…22` scheme 0, 400 to owner `33…33` scheme 1 borrow 1), with a placeholder witness of 65 bytes of `0x44`. RUN: A.2's `transferArgs`, `pushMinimal` and the tag reproduce these bytes.

```
10 5802000000000000 9001000000000000
40 2222222222222222222222222222222222222222222222222222222222222222 3333333333333333333333333333333333333333333333333333333333333333
02 0001
02 0001
40 00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000
40 00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000
42 00 4444444444444444444444444444444444444444444444444444444444444444444444444444444444444444444444444444444444444444444444444444444444
04 79c71c23
```

- Delegator signature script, scheme 0x00: `PushMinimal(64-byte signature ‖ 01) 04 fd3ef14a PushMinimal(program)`.
- Sizes on mainnet: leader with 1 next state 4,218 bytes (`TX:8c8d554e5b3235534d40f4d0c62afe8cf69c769b9b7ec47475f31186137cf245`), with 2 next states 4,324 bytes (`TX:de36063206e944bb…`), delegator 4,105 bytes (`TX:c03d5ea0f459aefd…`).
- Payload: none required. A payload is signed and changes the transaction ID.
- KAS in a cell: the program sets no rule for a cell's KAS on the normal path. Storage mass does (4.2): below about 0.08 KAS a cell cannot be created at all. Values seen on mainnet: 0.2 KAS (`TX:1842808d7c30f9aae2f317085b51346b51bb8f695bda31fe9fe0d0b55832b64e` output 0), 0.3 (`TX:b7fd0a9af952079277f55a12513f7508921463b913d470f5787c96e7be64a042` output 2), 0.5 (`TX:de36063206e944bb…`), 1 (`TX:644afc958a926f114a5aa893e6026b36c2dce65fd7e9043a723df1ecc59b2db0` output 1).

### 3.1 Transfer to one owner, with change

Example: `TX:de36063206e944bb1716645b03fbb5198f2079452286744d31b05ce8511e74f8`. 903,000,000 base units: 1,250,000 to a new owner, 901,750,000 back to the sender.

| Inputs, in order | Signs | Budget on chain | Budget needed (ENGINE) |
|---|---|---|---|
| 0: the sender's cell, leader, `transfer` | sender's key, SIGHASH_ALL | 128 | 14 (144,843 units) |
| 1: the sender's plain KAS coin (fee and the new cell's KAS) | sender's key, SIGHASH_ALL | 11 | 10 (100,001 units) |

| Outputs, in order | Binding | KAS |
|---|---|---|
| 0: recipient's cell, `next_states[0]` | `{authorizingInput: 0, covenantId: family}` | 0.5 |
| 1: sender's change cell, `next_states[1]` | `{authorizingInput: 0, covenantId: family}` | 0.5 |
| 2: KAS change to the sender | none | rest minus fee |

The program does not fix the order of recipient and change; `next_states` must follow the order of the authorized outputs.

A whole-coin transfer has one continuation: `TX:8c8d554e5b3235534d40f4d0c62afe8cf69c769b9b7ec47475f31186137cf245` (leader 126,700 units, budget 12 needed).

### 3.2 Split

Same shape as 3.1 with every continuation owned by the same key. Example: `TX:c26e67e9322c131a880126c9d8591888c418d571ec011416c1cdc4640d55c919` (one cell of 2,100,000,000,000,000 into 1,000,000,000,000 and 2,099,000,000,000,000, one owner; leader 144,855 units). A three-way split is allowed (ENGINE, B.3); four continuations are refused (ENGINE).

### 3.3 Merge

Example: `TX:c03d5ea0f459aefdd08e767f9728da60aa63eacd7839ae1befb190e383c0c466`, three cells of one owner into one. There is no plain KAS input; the fee comes from the KAS the cells carry.

| Inputs, in order | Entry | Signs | Units (ENGINE) |
|---|---|---|---|
| 0: cell | `transfer` (leader) | its owner | 185,186 (budget 18) |
| 1: cell | `transfer_delegator` | its owner | 137,037 (budget 13) |
| 2: cell | `transfer_delegator` | its owner | 137,037 (budget 13) |

Outputs: 0: the merged cell, authorized by input 0; 1: KAS change, unbound.

Two cells into one: `TX:687ed7353265485d4332eed49f6f5fee7d9eb7f82e1855bc65a6ad5371c453da` (leader 155,742 units, delegator 137,037). A merge can deliver to another owner: `TX:b59b4400779f071edf4d064a3e20a6d7b408341a2c7e3b729bfdfb501fee4f1b` (three cells of one owner into one cell of another). Each delegator is authorized by its own owner, so cells of different owners can be merged when each owner signs their own input (ENGINE). Four cell inputs are refused (ENGINE).

### 3.4 Cells owned by a covenant (scheme 0x04)

The leader needs no signature; the transaction must spend an input of the owning covenant. Example: `TX:c107c6e500d7cb7f5779b8300e9b814c08507f6237bfe8458a24af85a1dfb0e3` input 1 (owner = covenant `35ec40ca…`; 44,343 units, budget 4 on chain). Used by pools and curves (section 5).

### 3.5 Mint

The reference cell cannot mint: inputs and outputs balance (REF). Supply comes from:

**(a) A genesis with a fixed supply.** Example: `TX:7d424a92d5157e2bd96986ce36cf9df43ddb499c16226ceb3cc574265e434f43`.

| | |
|---|---|
| Input 0 | the creator's plain KAS coin, signed SIGHASH_ALL |
| Output 0 | the cell holding the whole supply, owner = creator, bound `{authorizingInput: 0, covenantId: X}`, 0.5 KAS |
| Output 1 | a second program of the same family (here the reference app's `TokenSeed`), bound the same way, 0.5 KAS |
| Output 2 | change, unbound |

X is computed from input 0's outpoint and outputs 0 and 1 (1, item 5). With the SDK: `tx.populateGenesisCovenants([{ authorizingInput: 0, outputs: [0, 1] }])` (UNTESTED here). Read the ID back from the output binding, and check it again in the transaction your wallet returns. A genesis can carry extra unbound outputs: `TX:3c7df6bb290c4201830885d5be8cf029b7ea1ea875961d50ddf87d7d65519a04` (outputs 2 to 4 unbound). The node's mass for `TX:7d424a92…` is 2,640 g.

**(b) A minter program in the family.** Example: `TX:b7fd0a9af952079277f55a12513f7508921463b913d470f5787c96e7be64a042`, a bridge mint into family `cbe213ce…`. Input 0: the bridge covenant (another family); input 1: the minter (tag `e3505180`, in the token's family). Output 0: bridge continuation (authorized by input 0); output 1: minter continuation (authorized by input 1); output 2: the new cell (authorized by input 1). The minter's rules are not covered here (section 10).

**(c) Issuer-controlled families** use their own cell program with a larger state and mint through an issuer program: example `TX:4c4cabba4e7e4847e3db0eb321277caab302cfe2a19a0c4c058a449fadc8f330`. Out of scope (section 10).

The reference app's `PublicMint` is not covered (section 10).

### 3.6 Burn

The reference cell has no burn. Tokens leave circulation through a normal transfer to owner scheme 0x03 with `owner = BLAKE2b-256(0x6a)` = `d379e50f3e2203f9a92584916335bb7cd014b237ceaa8b36f1d3d80d0cc88b32`, the P2SH of a lone OP_RETURN. Scheme 0x03 needs an input whose script is `P2SH(owner)`, and a script that is only OP_RETURN can never be satisfied, so the cell can never move again (REF; RUN: BLAKE2b-256(0x6a) = `d379e50f…8b32`). Nothing on chain changes the supply number; an index has to count cells with this owner as burned.

Example: `TX:1842808d7c30f9aae2f317085b51346b51bb8f695bda31fe9fe0d0b55832b64e`. Input 0: leader cell of 20,000,000; input 1: a KAS coin. Output 0: 10,000 base units to the burn owner (scheme 0x03), 0.2 KAS; output 1: 19,990,000 back to the sender, 1 KAS; output 2: KAS change. Its payload (starting `4b5a4231`) belongs to the application that built it; a burn needs no payload.

## 4. Fees and mass

Formulas from `RK:consensus/core/src/mass/mod.rs`, `RK:consensus/core/src/mass/units.rs` and `RK:mining/src/mempool/check_transaction_standard.rs`. A.1 implements them. RUN: compute mass by hand equals the node's `mass` field on nine mainnet transactions (`644afc95` 8,011; `f3fe248b` 17,638; `de360632` 19,818; `c03d5ea0` 63,453; `c26e67e9` 19,818; `7d424a92` 2,640; `1842808d` 8,471; `b7fd0a9a` 172,442; `687ed735` 40,694).

### 4.1 Compute mass, by hand

```
size = 2 + 8                                                   version, input count
     + Σ inputs (32 + 4 + 8 + sigScriptBytes + 8 + 2)           outpoint, script length, script, sequence, compute budget (v1)
     + 8                                                        output count
     + Σ outputs (8 + 2 + 8 + spkBytes + (bound ? 34 : 0))      value, spk version, spk length, spk, covenant binding
     + 8 + 20 + 8 + 32 + 8 + payloadBytes                       lock time, subnetwork, gas, payload hash, payload length, payload
compute   = size + 10 · Σ outputs (2 + spkBytes) + 100 · Σ computeBudget
transient = 4 · size
relay fee floor (sompi) = 100 · max(compute, ceil(transient · 0.5))
```

- Use the **final** signature script lengths; the cell program is in them.
- The floor is 100,000 sompi per kilogram (`RK:mining/src/mempool/config.rs` `DEFAULT_MINIMUM_RELAY_TRANSACTION_FEE`).
- Storage mass is not part of the fee floor (`RK:mining/src/mempool/check_transaction_standard.rs`: "Storage mass does not require an additional relay-fee floor").
- The SDK's `calculateTransactionMass` does not count v1 compute budgets. RUN with SDK v2.1.0: `TX:c03d5ea0…` SDK 13,711, node 63,453; `TX:687ed735…` SDK 9,488, node 40,694. When storage mass is larger it returns that instead (`TX:de360632…`: SDK 159,843, node compute 19,818). Never price a fee with it.

### 4.2 Storage mass, and why small outputs are expensive

KIP-9 with UTXO plurality (`RK:consensus/core/src/mass/mod.rs` `utxo_plurality`, `calc_storage_mass`):

```
plurality(spk, bound) = ceil((63 + spkBytes + (bound ? 32 : 0)) / 100)
storage = max(0, Σ outputs C·p²/value − input term),  C = 10^12
input term = Σ inputs C·p²/value         when Σp(outputs) = 1, or at most 2 inputs with Σp(inputs) = 1, or Σp(outputs) = Σp(inputs) = 2
           = Σp(inputs) · (C / mean)      otherwise, mean = Σ input values / Σp(inputs)
```

A cell is P2SH (35-byte script) with a covenant ID: plurality 2, so each cell output adds `4·10^12 / value`. A plain P2PK output (34 bytes) has plurality 1. A 0.5 KAS cell adds 80,000 g; a 0.05 KAS cell 800,000 g. RUN: hand storage equals the engine's `MassCalculator` on `644afc95` (40,628) and `f3fe248b` (37,219).

Per-transaction limits in the mempool: compute ≤ 500,000, storage ≤ 500,000, transient ≤ 1,000,000 (`RK:consensus/core/src/config/params.rs` `block_mass_limits`, `RK:mining/src/mempool/check_transaction_limits.rs`). A transaction over the storage limit is refused with "transaction storage mass of N is larger than max allowed size of 500000" (`RK:mining/errors/src/mempool.rs`). So a single cell output below 0.08 KAS (4·10^12 / 500,000 sompi) cannot be created unless inputs credit it back. Mainnet accepted transfers with storage 159,843 g (`TX:de360632…`) and 237,803 g (`TX:1842808d…`).

When a node accepts a transaction over RPC it computes the storage mass and writes it into the transaction itself (`RK:consensus/src/pipeline/virtual_processor/utxo_validation.rs` `validate_mempool_transaction_in_utxo_context`). A transaction inside a block is checked against its mass field (`RK:consensus/src/processes/transaction_validator/tx_validation_in_utxo_context.rs`). If you build blocks or relay raw transactions, write the right value.

Under contention the mempool orders by fee over `max(compute, storage, transient · 0.5)` (`RK:consensus/core/src/mass/mod.rs` `normalized_max`), so storage-heavy transactions are picked last.

### 4.3 Compute budgets per input

- 1 budget unit = 10,000 script units = 100 g. Each input gets 9,999 units free. Allowed = budget · 10,000 + 9,999 (`RK:consensus/core/src/mass/units.rs`, `RK:consensus/core/src/tx.rs` `allowed_script_units`).
- Needed budget = `ceil(max(0, used − 9,999) / 10,000)`.
- One Schnorr signature check costs 100,000 units.
- Measured (ENGINE, real mainnet spends): P2PK input 100,001 (budget 10); leader 1→1 126,700 (12); leader 1→2 144,843 (14); leader 2→1 155,742 (15); leader 3→1 185,186 (18); delegator 137,037 (13); scheme-0x04 leader 1→2 44,343 (4). Units depend on the state; measure every transaction.
- Builders that do not measure overpay. `TX:de360632…` committed 128 for a leader that needs 14: compute mass 19,818 instead of 8,418 (ENGINE), a fee of 0.0198 KAS instead of 0.0084.
- The mempool counts signature opcodes in each P2SH program without running it and refuses more than 15 as non-standard: "transaction input #N has M signature operations which is more than the allowed max amount of 15" (`RK:mining/src/mempool/check_transaction_standard.rs` `MAX_STANDARD_P2SH_SIG_OPS`, `RK:mining/errors/src/mempool.rs`). The reference cell is under the limit (every spend above was accepted). This matters for programs you write next to it.

### 4.4 The free under-budget rejection

An input whose script runs past its allowance is refused with:

```
script units exceeded the amount committed in the input: used=N, limit=L
```

(`RK:crypto/txscript/errors/src/lib.rs` `ExceededCommittedScriptUnits`). A refused transaction moves nothing and pays nothing. ENGINE on `TX:de360632…` input 0 at budget 13: `used=144688, limit=139999`. N is the count when the limit was crossed, so it can be below the full run (144,688 against 144,843).

### 4.5 Walking the budget up without a new signature

Compute budgets are in neither the v1 sighash nor the v1 transaction ID (section 3). You can change them after signing and the signatures stay valid. ENGINE on `TX:de360632…` with its original mainnet signatures: budgets 14, 128 and 300 all pass; 13 fails as above; the ID is `de360632…` in every case.

The catch: the fee is inputs minus outputs, and outputs are signed. Each budget unit adds 100 g of compute mass, so the fee already in the transaction must cover the higher mass. Procedure:

1. Before signing, run every input on the script engine (Appendix B) and set each budget to the measured need. If you cannot run the engine, commit an upper bound.
2. Price the fee on compute mass with the largest budgets you are willing to commit. Sign once.
3. Submit. On `used=N, limit=L` for input i, set budget i to `ceil((N − 9,999) / 10,000)`, plus 1 if that equals the refused budget; keep every signature script; resubmit. Repeat. Stop when the mass needs more fee than the transaction pays; then rebuild and re-sign.
4. Lowering a budget below what you priced is always safe for the fee.

## 5. Swaps

### 5.1 Proven on mainnet: a constant-product pool for one family against KAS

A pool covenant (ID `35ec40ca5e0f991ae96b1c714e384f2904c482c0aecfae7556dc7979269701ca`) trades the family `cbe213cecb9e943edaa31a85a697e8174c7338bbd5d2a38ef94db20b53f7ea44`. The token reserve is one reference cell owned by the pool's covenant ID (scheme 0x04); the KAS reserve is the pool cell's own KAS.

| Step | Txid |
|---|---|
| genesis | `TX:644afc958a926f114a5aa893e6026b36c2dce65fd7e9043a723df1ecc59b2db0` |
| buy (KAS in, tokens out) | `TX:c107c6e500d7cb7f5779b8300e9b814c08507f6237bfe8458a24af85a1dfb0e3`, `TX:bac1ef1ce4ee8e7c73a6861263a0410ebf1dec7a90fd492c38d4dbabbd8045a4` |
| sell (tokens in, KAS out) | `TX:f3fe248b5e499f32a72763746ca09eb0b275bf4e77b5fdedb59632b4e654346b` |
| owner withdraws tokens | `TX:36cecdf023f0a6c54c8090d9f1c85e8130eee51a9538db715dd15b7a29cde576` |

Genesis: input 0 the owner's KAS coin; input 1 the owner's cell, leader. Output 0 the pool cell, 93.11 KAS, bound to a new covenant P authorized by input 0 (a genesis); output 1 the reserve cell, 3,000,000 base units, owner scheme 0x04 = P, bound to the family, authorized by input 1; output 2 change.

Buy (`TX:c107c6e5…`):

| Inputs | Outputs |
|---|---|
| 0: pool cell, pool entry (tag `c425e7d6`), no signature | 0: pool continuation, KAS + paid (authorized by 0) |
| 1: reserve cell, KCC-20 leader, scheme 0x04, no signature | 1: reserve cell, amount − bought (authorized by 1) |
| 2: trader's KAS coin, trader signs SIGHASH_ALL | 2: trader's new cell, scheme 0x00 (authorized by 1) |
| | 3: trader's change |

Sell (`TX:f3fe248b…`):

| Inputs | Outputs |
|---|---|
| 0: pool cell, pool entry (tag `8bc0097a`) | 0: pool continuation, KAS − paid out (authorized by 0) |
| 1: reserve cell, KCC-20 leader, scheme 0x04 | 1: reserve cell, amount + sold: a merge (authorized by 1) |
| 2: trader's cell, `transfer_delegator`, trader signs | 2: trader's KAS payout, unbound |

Owner withdraw (`TX:36cecdf0…`): input 0 pool cell (tag `62c2257c`), input 1 reserve leader, input 2 owner's KAS coin; outputs: pool continuation, reserve cell 2,302,042, owner's new cell 1,610,800, change.

The pool program's own source is not published with this guide, so its pricing rule is not verified here; only its transaction shapes and chain behavior are (section 10).

Weaknesses, from chain data and the UTXO model:

- **Thin pools move far.** Before the sell the pool held 94.11 KAS against 2,968,218 base units (about 31.7 KAS per 1,000,000 units). Selling 1,000,000 units paid 23.66 KAS (pool KAS 94.11 → 70.447: `TX:c107c6e5…` output 0, `TX:f3fe248b…` output 0).
- **The owner changes the price.** An owner who can withdraw reserves can move the price (`TX:36cecdf0…`: reserve 3,912,842 → 2,302,042). A pool price is not an oracle.
- **One pool cell.** Every swap spends the same UTXO, so two swaps built on the same state conflict; only one can be accepted. The other is refused and pays nothing.
- **Batching is capped** by the cell's limit of 3 inputs and 3 outputs per family.
- **Scheme 0x04 control.** The pool program, not the cell, decides where the reserve cell goes (1.2).

### 5.2 Proven in part: a self-selling curve

A curve covenant (`6f2a1cca345611a56975534222569134e95896b769c7432e238451c43e3a8641`) owns the unsold supply of family `d35f0e5182859b275c96768a2249921c367d1f937c6624844c6726a59ac90eff` as one cell (scheme 0x04) and sells it for KAS.

- init: `TX:83df3c1e2c0bff6d07b977bfafbe863713eacf11d0bde72547dd21504dde9579`. Input 0 the curve coin (tag `4fefac71`); output 0 the curve continuation; output 1 the supply cell, a genesis of the token family authorized by input 1 (a plain KAS input).
- buys: `TX:c74a980208b180b2e210a6b8067281862a7c964e715a8422a5618b9e83c77984` (the supply cell held 200,000,000; 500 units sold for 0.50000562 KAS), `TX:cbf62816eaaeb20ee4c7de343c15f70f6545f015a15d9f8a9fa4c6319c6d1dfa`, `TX:6b335a40b9c931dcdfa10b57c715167f361c7a58a3144ad85e3c7a0af97aac53`. Shape: input 0 curve coin (tag `08957bde`), input 1 supply cell leader (scheme 0x04), input 2 buyer's KAS coin; outputs: curve continuation (KAS + cost), supply continuation, buyer's cell, change.
- A sell back to the curve: no mainnet example. Not proven.

### 5.3 Not proven here

- A two-party atomic swap of token A for token B in one transaction (two leaders, one per family). Such transactions exist on mainnet (2.1); none is documented here with a txid.
- A token-for-KAS swap between two wallets with no covenant in between.
- Order books and escrows.

## 6. Wallets

### 6.1 What every wallet path shares

1. Build the unsigned v1 transaction yourself as rusty-kaspa "safe JSON" (`Transaction.serializeToSafeJSON()`), with each input's `utxo` filled (address, amount, scriptPublicKey, covenantId). Set version, budgets, sequences, lock time and outputs before signing. Sequences, lock time and outputs are signed.
2. Ask the wallet to sign **every** input with sighash type 1: `signInputs = inputs.map((_, index) => ({ index, sighashType: 1 }))`. The wallet signs the cell input like one of its own; the cell's program checks that signature against the state's owner key.
3. The wallet returns safe JSON, sometimes wrapped as `{ txJsonString }`. Normalize: `typeof r === 'string' ? r : (r && typeof r.txJsonString === 'string' ? r.txJsonString : JSON.stringify(r))`.
4. For each cell input the wallet's `signatureScript` is `41 ‖ 64-byte signature ‖ 01`. Refuse anything else. Replace it with the cell's script (A.2 `leaderScript`, `delegatorScript`). Plain KAS inputs keep the wallet's script.
5. Build the final transaction **from the JSON the wallet returned**, not from your copy. Check its inputs (txid, index) and outputs (value, script, binding) equal what you built; then put back the budgets you priced. Wallets and builders set sequences you may not expect: RUN shows `TX:de360632…` only reproduces its ID with sequence 1 on its KAS input, and `TX:c03d5ea0…` with sequences 0, 1 and 2.
6. Run the script engine on the final transaction (Appendix B), then submit through your node: `rpc.submitTransaction({ transaction: tx, allowOrphan: false })` after `Transaction.deserializeFromSafeJSON(json)` and `tx.finalize()` (UNTESTED here: no node RPC was reachable from the test machine).

ENGINE: a leader, a delegator and a fee coin signed the way a wallet signs (a bare `41 ‖ sig ‖ 01`), then assembled with A.2, were accepted (B.3, last line).

### 6.2 Kasware

```js
// UNTESTED here
const signed = await window.kasware.signPskt({ txJsonString, options: { signInputs } });
```

One object argument. Leave auto-finalize off: the wallet cannot build a cell script; your code does. Active account: `(await window.kasware.getAccounts())[0]`.

### 6.3 Kaspire

```js
// UNTESTED here
const signed = await window.kaspire.request({ method: 'signPskt', params: { sender: activeAddress, txJsonString, options: { signInputs } } });
```

Detect with `window.kaspire && window.kaspire.isKaspire === true`. Active account: `(await window.kaspire.request({ method: 'getAccounts' }))[0]`. It signs and never broadcasts. When locked it rejects instead of asking to unlock.

### 6.4 Kasla (hosted account)

Kasla signs only after the user approves the transaction on Kasla's own page, opened in a popup:

```js
// UNTESTED here. Open the popup inside the user's click, before any await, or browsers block it.
const popup = window.open('about:blank', 'kasla-approve', 'width=480,height=700');
const r = await fetch('https://kasperopay.com/pay/kasla/sign-pskt', {
  method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + sessionToken },
  body: JSON.stringify({ txJsonString, signInputs, description, reference_id })
});
const d = await r.json();                 // { success, approval_url, request_id }
popup.location.href = d.approval_url;
// then poll GET https://kasperopay.com/pay/kasla/sign-status/<request_id> (same header) every few seconds:
// { status: 'signed', txJsonString } | { status: 'cancelled' | 'failed', error_message }
```

Kasla never broadcasts; you do. Signing a KCC-20 cell input through this flow is not established (section 10).

### 6.5 Wallets that cannot sign cell inputs today

Kastle (`kastle.signTx(networkId, txJsonString)`) has been observed returning P2SH inputs with an empty `signatureScript`, silently. Do not offer it for cell spends until that changes.

### 6.6 Network check

- An extension can be on another network than your node; Kasware, for example, can be switched to an L2. Signatures made there are useless on Kaspa mainnet.
- No wallet API that reports the network is verified in this guide. What you can check: the wallet's active address prefix (`kaspa:` for mainnet, `kaspatest:` for testnets) against your node's network.
- The decisive check is the engine run before broadcast (section 7, rule 4): a signature that does not verify is refused there.

### 6.7 Account-switch check

Before every signature, compare the wallet's active address with the address your session was built for, and refuse on a mismatch. Subscribe to `accountsChanged` on `kasware`, `kastle` and `kaspire` and end the session when it fires. Without this, a payout can go to the wrong account.

## 7. Testing discipline (rules)

1. Work on testnet-10 first: `kaspatest:` addresses and a node started for testnet-10, running v2.1.0.
2. Every path you build ends with the txid of an accepted transaction. Record it next to the code that built it.
3. Nothing counts as done without that txid. An engine pass is necessary and not sufficient.
4. Before broadcasting, run every input of the exact signed transaction on the rusty-kaspa script engine (Appendix B). Also check: the engine's transaction ID is the one you expect; compute mass by hand (A.1) equals the engine's; the fee covers the floor; storage mass is at most 500,000.
5. Show every attack below refused: by the engine, and on testnet-10 by the node, with the node's message recorded word for word.
   - wrong amount: outputs sum above, and below, the inputs;
   - wrong owner: the leader or a delegator signed by a key that is not its owner;
   - change sent elsewhere: an output or its `next_states` entry changed after the owner signed;
   - a continuation whose script is not the program holding its `next_states` entry;
   - a continuation without its covenant binding;
   - a fourth continuation; a fourth cell input;
   - a delegator from another family;
   - a cell spent twice: two transactions spending the same cell; the node must refuse the second;
   - a forged cell an index lists but the node does not have: your verifier (2.3) must refuse it before building, and a transaction spending it must be refused by the node.
6. What this guide verified: every attack in rule 5 except the last two, refused on the v2.1.0 engine (B.3). Node messages for any of them, and the two node-level attacks, are **not** verified here. You must produce them yourself.
7. Measure compute budgets and fees; do not guess them (section 4).
8. After you submit a spend, do not reuse any of its inputs or outputs until you know which transaction the node accepted. A rerun from stale state fails with "is not unspent", or, if the first attempt did not land, repeats an action you meant to do once.

## 8. Pre-launch security checklist

Run every line before any real money. Each line is yes or no.

- [ ] The token is identified by covenant ID everywhere: storage, UI, APIs, logs. Name and ticker are display only.
- [ ] The cell program is pinned (template hash and full bytes) and compared byte for byte before any cell is read or spent.
- [ ] Every cell from an index passes all checks of 2.3 against your own node before use.
- [ ] Addresses are derived from programs, never taken from input.
- [ ] State encoding passes the KCC-1 and KCC-20 conformance vectors.
- [ ] Each continuation is the program holding exactly its `next_states` entry, in order, bound to the family with the leader as authorizing input. Change is unbound.
- [ ] Output sum equals input sum; no negative amount; base units are integers end to end (BigInt or decimal strings, never floating point).
- [ ] Recipient address prefix matches the node's network.
- [ ] Version 1; every input has `sigOpCount` 0 and a measured `computeBudget`; sequences and lock time are set before signing.
- [ ] Fee priced by hand on compute mass, not with `calculateTransactionMass`.
- [ ] Storage mass at most 500,000; no cell below 0.08 KAS; no plain change output so small it dominates storage mass.
- [ ] Every P2SH program you add has at most 15 signature opcodes.
- [ ] The wallet's returned transaction is checked against what you built (inputs, outputs, bindings) before you assemble cell scripts.
- [ ] Wallet active address equals the session address; network prefix checked; Kastle not offered for cell spends.
- [ ] The final signed transaction passes the script engine and has the expected ID.
- [ ] Every attack of 7.5 is refused on the engine and on a testnet-10 node, messages recorded.
- [ ] Every path has a testnet-10 txid, then a small mainnet txid.
- [ ] After broadcast, your state waits for acceptance before the next spend of any output; if you rely on an index, tell it about the transaction or wait for it.
- [ ] Scheme 0x04 owners: the owning program constrains every cell it owns in every transaction it can join.
- [ ] Pools: the price is labeled a pool price, not an oracle; depth and price impact are shown before a trade; a minimum-out is enforced by the program.
- [ ] No key, seed or API key is logged, printed or sent to a browser.

## 9. Common mistakes, with symptom and fix

| Mistake | Symptom | Fix | Source |
|---|---|---|---|
| Reading only the first leader of a transaction | One family's side of a multi-token transaction vanishes from your ledger, and every later cell of it | Decode every leader; group delegators and continuations by covenant ID | REF; 2.1 |
| Treating every output a leader authorizes as a continuation | A transfer that also creates another covenant looks malformed and is skipped | Continuations are the authorized outputs in the leader's own covenant ID; others are geneses | `RK:crypto/txscript/src/covenants.rs` |
| Keying balances by address | Balances disappear after every transfer | Key by covenant ID plus owner | 1, item 7 |
| v1 inputs left with SDK defaults | The node refuses a non-zero `sigOpCount`, or the fee as under the floor | `sigOpCount` 0, `computeBudget` set; count 100 g per budget unit and 34 bytes per binding | `RK:consensus/core/src/mass/mod.rs`; RUN (3) |
| Budget too small | "script units exceeded the amount committed in the input: used=N, limit=L" | Raise the budget (4.5); one signature check is 100,000 units | `RK:crypto/txscript/errors/src/lib.rs`; ENGINE |
| Pricing the fee with `calculateTransactionMass` | Fee far under the floor; refused | Compute mass by hand (4.1) | RUN (4.1) |
| Storage mass without plurality | Estimates four times too low for cells | Plurality 2 for every bound P2SH output (4.2) | `RK:consensus/core/src/mass/mod.rs` |
| Using a 100,000 g storage cap from older releases | Valid transfers refused by your own code | The v2.1.0 per-transaction storage limit is 500,000 g | `RK:consensus/core/src/config/params.rs`; `TX:1842808d…` at 237,803 g |
| Reading a transaction ID from safe JSON | Wrong ID after changing payload, version or bindings | The SDK keeps the `id` it was given until `finalize()`; finalize, then read | RUN (B.2 recomputes IDs) |
| Rebuilding the signed transaction from your own copy | Signatures fail because the wallet set sequences | Start from the wallet's returned JSON | 6.1 item 5 |
| Signing on the wrong network or account | A useless signature, or a payout to the wrong account | Network and account checks (6.6, 6.7) | 6.6, 6.7 |
| Auto-finalize on in the wallet | The wallet fails after the user approves | Auto-finalize off; assemble cell scripts yourself | 6.2 |
| More than 15 signature opcodes in one P2SH program | "transaction input #N has M signature operations which is more than the allowed max amount of 15" | Keep each program at 15 or fewer | `RK:mining/src/mempool/check_transaction_standard.rs` |
| A cell output below about 0.08 KAS | "transaction storage mass of N is larger than max allowed size of 500000" | Give cells more KAS | 4.2 |
| Trusting an index after a broadcast | Lineage gaps; spent cells still listed | Verify on your node (2.3); wait for acceptance | 2.2 |
| Thin pool | A sale far below the quoted spot price | Show depth and impact; enforce minimum-out | 5.1, `TX:f3fe248b…` |

## 10. Not covered

Not established by this guide. Do not treat as known.

- The borrow path (0x01) and borrow schemes 0x01 to 0x03.
- Spending cells with owner schemes 0x01, 0x02 or 0x03 (creating cells owned by the burn owner is covered).
- A non-zero `extension_commitment`.
- The reference app's `PublicMint` and `TokenSeed` entries.
- Minter programs (3.5 b), issuer-controlled families, other compilations of the cell and older layouts.
- The rules inside the pool and curve programs (5.1, 5.2); the curve's sell.
- Atomic two-party swaps, P2P swaps without a covenant, order books, escrows.
- Node messages for the attacks of 7.5, and node-level double-spend and forged-cell refusals.
- Testnet-10 txids: this guide's examples are mainnet only.
- A wallet API that reports its network; the Kasware, Kaspire and Kasla calls of section 6 were not run here.
- Signing a KCC-20 cell input through Kasla.
- A node-side index by covenant ID: not in rusty-kaspa v2.1.0.
- Token metadata on chain (name, ticker, image).
- Reorgs, replace-by-fee, fees above the floor under contention.
- Time locks (the reference cell has none).

---

## Appendix A. Code

Every block here was run on 2026-11-17 with Node.js 22. BLAKE2b-256 and BLAKE3 are passed in as functions: use any correct implementation (unkeyed, 32-byte digest).

### A.1 Mass by hand (`mass.js`)

RUN: compute mass equals the node's on the nine transactions of section 4; storage equals the engine's `MassCalculator` on `644afc95` and `f3fe248b`.

```js
'use strict';
// Mass by hand, rusty-kaspa v2.1.0 (consensus/core/src/mass/mod.rs, mining/src/mempool/check_transaction_standard.rs).
// tx: { version, payloadHex,
//       inputs:  [{ sigScriptHex, computeBudget }],
//       outputs: [{ value: BigInt, spkHex, covenant: bool }] }   spkHex is the script without its 2-byte version
// entries (for storage mass): [{ amount: BigInt, spkHex, covenant: bool }] in input order
const C = 10n ** 12n;                         // STORAGE_MASS_PARAMETER = SOMPI_PER_KASPA * 10_000

function size(tx) {
  let n = 2 + 8;                              // version, input count
  for (const i of tx.inputs) n += 32 + 4 + 8 + i.sigScriptHex.length / 2 + 8 + (tx.version >= 1 ? 2 : 0);
  n += 8;                                     // output count
  for (const o of tx.outputs) n += 8 + 2 + 8 + o.spkHex.length / 2 + (o.covenant ? 2 + 32 : 0);
  n += 8 + 20 + 8 + 32 + 8 + (tx.payloadHex || '').length / 2;   // lock time, subnetwork, gas, payload hash, payload len, payload
  return n;
}
function computeMass(tx) {
  const spk = tx.outputs.reduce((a, o) => a + 2 + o.spkHex.length / 2, 0);
  const budgets = tx.inputs.reduce((a, i) => a + Number(i.computeBudget), 0);
  return size(tx) + 10 * spk + 100 * budgets;
}
const transientMass = tx => size(tx) * 4;                       // TRANSIENT_BYTE_TO_MASS_FACTOR
// The relay floor: 100 sompi per gram of max(compute, ceil(transient * 0.5)), storage excluded.
const minRelayFee = tx => BigInt(Math.max(computeMass(tx), Math.ceil(transientMass(tx) * 0.5))) * 100n;

const plurality = (spkHex, covenant) => BigInt(Math.ceil((63 + spkHex.length / 2 + (covenant ? 32 : 0)) / 100));
function storageMass(entries, outputs) {
  let outsP = 0n, harmOuts = 0n;
  for (const o of outputs) { const p = plurality(o.spkHex, o.covenant); outsP += p; harmOuts += C * p * p / BigInt(o.value); }
  const insP = entries.reduce((a, e) => a + plurality(e.spkHex, e.covenant), 0n);
  const relaxed = outsP === 1n || (entries.length <= 2 && (insP === 1n || (outsP === 2n && insP === 2n)));
  if (relaxed) {
    const harmIns = entries.reduce((a, e) => { const p = plurality(e.spkHex, e.covenant); return a + C * p * p / BigInt(e.amount); }, 0n);
    return harmOuts > harmIns ? harmOuts - harmIns : 0n;
  }
  const sumIns = entries.reduce((a, e) => a + BigInt(e.amount), 0n);
  let mean = sumIns / insP; if (mean < 1n) mean = 1n;
  const arith = insP * (C / mean);
  return harmOuts > arith ? harmOuts - arith : 0n;
}
// Script units: 9,999 free per input, then 10,000 per budget unit (consensus/core/src/mass/units.rs, tx.rs)
const budgetFor = used => Math.max(0, Math.ceil((used - 9999) / 10000));
module.exports = { size, computeMass, transientMass, minRelayFee, plurality, storageMass, budgetFor };
```

### A.2 Cell encoding, addresses and signature scripts (`kcc20.js`)

RUN: the KCC-20 EncodeState vector (1.1) and the transfer-arguments vector (section 3) reproduce exactly; the address of the live cell of 1.5 matches; ENGINE: every transaction of B.3 was built with this file and a program taken from chain with A.3.

```js
'use strict';
// KCC-20 reference cell: encoding, addresses, signature scripts. No dependencies.
// You supply: blake2b256(bytes) -> 32 bytes (unkeyed BLAKE2b, 32-byte digest).
// The cell program (4,031 bytes) is taken from any spend on chain with programFromSpend (A.3) and pinned by its template hash.
const TEMPLATE = '9703112ee6e3555107cd168858992b77d3b74f655205b2b463b1f9ec2ec73cf7';
const STATE_START = 1, STATE_LEN = 145, PROGRAM_LEN = 4031;
const TAG_TRANSFER = '79c71c23', TAG_DELEGATOR = 'fd3ef14a';
const ZERO32 = '00'.repeat(32);
const hex = b => Buffer.from(b).toString('hex');
const bytes = h => Buffer.from(String(h).replace(/^0x/i, ''), 'hex');
const le = (n, v) => { const o = Buffer.alloc(n); let x = BigInt(v); for (let i = 0; i < n; i++) { o[i] = Number(x & 255n); x >>= 8n; } return o; };

// KCC-1 pushes
function pushExplicit(b) {
  const n = b.length;
  if (n === 0) return Buffer.from([0x00]);
  if (n <= 75) return Buffer.concat([Buffer.from([n]), b]);
  if (n <= 255) return Buffer.concat([Buffer.from([0x4c, n]), b]);
  if (n <= 65535) return Buffer.concat([Buffer.from([0x4d]), le(2, n), b]);
  return Buffer.concat([Buffer.from([0x4e]), le(4, n), b]);
}
function pushMinimal(b) {
  if (b.length === 0) return Buffer.from([0x00]);
  if (b.length === 1 && b[0] >= 1 && b[0] <= 16) return Buffer.from([0x50 + b[0]]);
  if (b.length === 1 && b[0] === 0x81) return Buffer.from([0x4f]);
  return pushExplicit(b);
}
// int: 8-byte little-endian signed-magnitude
function int8(v) {
  const n = BigInt(v), MAX = 9223372036854775807n;
  if (n > MAX || n < -MAX) throw new Error('int out of range');
  const o = le(8, n < 0n ? -n : n); if (n < 0n) o[7] |= 0x80; return o;
}
const b32 = h => { const b = bytes(h); if (b.length !== 32) throw new Error('need 32 bytes'); return b; };
const byte1 = v => { if (!Number.isInteger(v) || v < 0 || v > 255) throw new Error('byte'); return Buffer.from([v]); };

// state = { amount, owner, ownerScheme, borrowScheme, borrowGuard, extensionCommitment }
function encodeState(s) {
  const out = Buffer.concat([
    pushExplicit(b32(TEMPLATE)), pushExplicit(int8(s.amount)), pushExplicit(b32(s.owner)),
    pushExplicit(byte1(s.ownerScheme ?? 0)), pushExplicit(byte1(s.borrowScheme ?? 0)),
    pushExplicit(b32(s.borrowGuard ?? ZERO32)), pushExplicit(b32(s.extensionCommitment ?? ZERO32))]);
  if (out.length !== STATE_LEN) throw new Error('state is ' + out.length + ' bytes');
  return out;
}
function parseState(stateBytes) {
  const b = Buffer.from(stateBytes), want = [[0x20, 32], [0x08, 8], [0x20, 32], [0x01, 1], [0x01, 1], [0x20, 32], [0x20, 32]], v = [];
  if (b.length !== STATE_LEN) throw new Error('state length');
  let i = 0;
  for (const [op, n] of want) { if (b[i] !== op) throw new Error('bad state push at byte ' + i); v.push(b.subarray(i + 1, i + 1 + n)); i += 1 + n; }
  const m = Buffer.from(v[1]), neg = (m[7] & 0x80) !== 0; m[7] &= 0x7f;
  return { template: hex(v[0]), amount: m.readBigUInt64LE(0) * (neg ? -1n : 1n), owner: hex(v[2]), ownerScheme: v[3][0],
           borrowScheme: v[4][0], borrowGuard: hex(v[5]), extensionCommitment: hex(v[6]) };
}
// program = prefix ‖ state ‖ suffix; `ref` is any reference program (its own state is replaced)
function programFor(state, ref) {
  const r = bytes(ref);
  if (r.length !== PROGRAM_LEN) throw new Error('not a reference-sized program');
  return Buffer.concat([r.subarray(0, STATE_START), encodeState(state), r.subarray(STATE_START + STATE_LEN)]);
}
const stateOf = program => parseState(bytes(program).subarray(STATE_START, STATE_START + STATE_LEN));

// P2SH scriptPublicKey and bech32 address (version 8)
const p2shScript = (program, blake2b256) => 'aa20' + hex(blake2b256(bytes(program))) + '87';
const CHARSET = 'qpzry9x8gf2tvdw0s3jn54khce6mua7l';
const GEN = [0x98f2bc8e61n, 0x79b76d99e2n, 0xf33e5fb3c4n, 0xae2eabe2a8n, 0x1e4f43e470n];
function polymod(values) {
  let c = 1n;
  for (const v of values) { const c0 = c >> 35n; c = ((c & 0x07ffffffffn) << 5n) ^ BigInt(v); for (let j = 0; j < 5; j++) if ((c0 >> BigInt(j)) & 1n) c ^= GEN[j]; }
  return c ^ 1n;
}
function encodeAddress(prefix, version, payload) {
  const data = [version, ...payload], words = []; let acc = 0, bits = 0;
  for (const b of data) { acc = (acc << 8) | b; bits += 8; while (bits >= 5) { bits -= 5; words.push((acc >> bits) & 31); } }
  if (bits > 0) words.push((acc << (5 - bits)) & 31);
  const mod = polymod([...prefix].map(c => c.charCodeAt(0) & 0x1f).concat([0], words, [0, 0, 0, 0, 0, 0, 0, 0]));
  const cs = []; for (let i = 7; i >= 0; i--) cs.push(Number((mod >> BigInt(5 * i)) & 31n));
  return prefix + ':' + words.concat(cs).map(w => CHARSET[w]).join('');
}
const p2shAddress = (program, network, blake2b256) => encodeAddress(network, 8, blake2b256(bytes(program)));   // network: 'kaspa' | 'kaspatest'

// transfer(next_states, witness) arguments: one push per field, holding that field of every state in order (KCC-1 §3.4.6)
function transferArgs(states) {
  const col = f => Buffer.concat(states.map(f));
  return Buffer.concat([
    pushMinimal(col(s => int8(s.amount))), pushMinimal(col(s => b32(s.owner))),
    pushMinimal(col(s => byte1(s.ownerScheme ?? 0))), pushMinimal(col(s => byte1(s.borrowScheme ?? 0))),
    pushMinimal(col(s => b32(s.borrowGuard ?? ZERO32))), pushMinimal(col(s => b32(s.extensionCommitment ?? ZERO32)))]);
}
// A wallet asked to sign an input with sighashType 1 returns "41" ‖ 64-byte Schnorr signature ‖ "01".
function sig65(walletSigScriptHex) {
  const h = String(walletSigScriptHex || '').toLowerCase();
  if (!/^41[0-9a-f]{128}01$/.test(h)) throw new Error('expected 41 ‖ 64-byte signature ‖ 01 (SIGHASH_ALL)');
  return bytes(h.slice(2));
}
// leader, owner scheme 0x00, normal path: args ‖ push(00 ‖ sig65) ‖ 04 79c71c23 ‖ push(program)
const leaderScript = (nextStates, walletSigScriptHex, program) => hex(Buffer.concat([
  transferArgs(nextStates), pushMinimal(Buffer.concat([Buffer.from([0x00]), sig65(walletSigScriptHex)])),
  Buffer.from('04' + TAG_TRANSFER, 'hex'), pushMinimal(bytes(program))]));
// delegator, owner scheme 0x00: push(sig65) ‖ 04 fd3ef14a ‖ push(program). No path byte.
const delegatorScript = (walletSigScriptHex, program) => hex(Buffer.concat([
  pushMinimal(sig65(walletSigScriptHex)), Buffer.from('04' + TAG_DELEGATOR, 'hex'), pushMinimal(bytes(program))]));

module.exports = { TEMPLATE, STATE_START, STATE_LEN, PROGRAM_LEN, TAG_TRANSFER, TAG_DELEGATOR, ZERO32,
  pushExplicit, pushMinimal, int8, encodeState, parseState, programFor, stateOf, p2shScript, p2shAddress, encodeAddress,
  transferArgs, sig65, leaderScript, delegatorScript };
```

### A.3 Program from a spend (`program-from-spend.js`)

RUN results in 1.6.

```js
'use strict';
// Recover the KCC-20 cell program from any leader spend and check its KCC-1 template hash.
// sigScriptHex: the signature script of an input whose next-to-last push is 79c71c23 (transfer) or fd3ef14a (delegator).
// blake3(bytes) -> 32 bytes (unkeyed BLAKE3). The program is the LAST push; the state window is bytes 1..145.
function lastPushes(hex, n) {                       // push-only parser (data pushes only, as KCC-20 sig scripts are)
  const b = Buffer.from(hex, 'hex'), out = []; let i = 0;
  while (i < b.length) {
    const op = b[i++]; let len;
    if (op === 0x00) { out.push(Buffer.alloc(0)); continue; }
    if (op >= 0x51 && op <= 0x60) { out.push(Buffer.from([op - 0x50])); continue; }
    if (op <= 0x4b) len = op; else if (op === 0x4c) len = b[i++]; else if (op === 0x4d) { len = b.readUInt16LE(i); i += 2; }
    else if (op === 0x4e) { len = b.readUInt32LE(i); i += 4; } else throw new Error('not push-only at byte ' + (i - 1));
    out.push(b.subarray(i, i + len)); i += len;
  }
  return out.slice(-n);
}
function programFromSpend(sigScriptHex, blake3) {
  const [tag, program] = lastPushes(sigScriptHex, 2);
  const le8 = n => { const o = Buffer.alloc(8); o.writeBigUInt64LE(BigInt(n)); return o; };
  const STATE_START = 1, STATE_LEN = 145;                       // reference KCC20: stateStart 1, stateLen 145
  const prefix = program.subarray(0, STATE_START), suffix = program.subarray(STATE_START + STATE_LEN);
  const templateHash = Buffer.from(blake3(Buffer.concat([le8(prefix.length), prefix, le8(suffix.length), suffix]))).toString('hex');
  const stateTemplateField = program.subarray(STATE_START + 1, STATE_START + 33).toString('hex');   // first state push: 20 ‖ template hash
  return { tag: tag.toString('hex'), program: program.toString('hex'), programBytes: program.length, templateHash, selfConsistent: templateHash === stateTemplateField };
}
module.exports = { programFromSpend, lastPushes };
```

### A.4 Cell verifier (`verify-cell.js`)

RUN results in 2.3.

```js
'use strict';
// Verify one KCC-20 reference cell before spending it.
// K = require('./kcc20.js'); ref = a reference program (hex) you pinned (A.3); blake2b256(bytes) -> 32 bytes.
// coin   = { redeemScriptHex, address, txid, vout, kasSompi }   from any index; trust none of it
// expect = { covenantId, network: 'kaspa' | 'kaspatest' }
// utxosAt(address) -> [{ txid, vout, amount, covenantId }] from YOUR node (getUtxosByAddresses)
function makeVerifier(K, ref, blake2b256) {
  const R = String(ref).toLowerCase();
  const prefix = R.slice(0, K.STATE_START * 2), suffix = R.slice((K.STATE_START + K.STATE_LEN) * 2);
  return async function verifyCell(coin, expect, utxosAt) {
    const r = String(coin.redeemScriptHex).toLowerCase();
    // 1. the pinned program, byte for byte outside the 145-byte state window
    if (r.length !== R.length || !r.startsWith(prefix) || !r.endsWith(suffix)) throw new Error('not the pinned KCC20 program outside the state window');
    // 2. the window parses as the seven state pushes, with the pinned template hash and a non-negative amount
    const state = K.stateOf(r);
    if (state.template !== K.TEMPLATE) throw new Error('state template field is not the pinned template hash');
    if (state.amount < 0n) throw new Error('negative amount');
    // 3. rebuilding the program from the state alone gives the same bytes
    if (Buffer.from(K.programFor(state, R)).toString('hex') !== r) throw new Error('rebuild mismatch');
    // 4. the address is the P2SH of these bytes on your network
    const addr = K.p2shAddress(r, expect.network, blake2b256);
    if (addr !== coin.address) throw new Error(`P2SH of the program is ${addr}, the coin says ${coin.address}`);
    // 5. your node holds the outpoint there, with that KAS, under the family's covenant ID
    const u = (await utxosAt(addr)).find(x => x.txid === coin.txid && Number(x.vout) === Number(coin.vout));
    if (!u) throw new Error('outpoint is not in the node UTXO set at this address (spent, not yet accepted, or forged)');
    if (String(u.covenantId || '').toLowerCase() !== expect.covenantId) throw new Error(`UTXO carries covenant ${u.covenantId || 'none'}, not ${expect.covenantId}`);
    if (coin.kasSompi != null && BigInt(u.amount) !== BigInt(coin.kasSompi)) throw new Error('KAS amount differs from the claim');
    return { ok: true, state, address: addr, kasSompi: String(u.amount) };
  };
}
module.exports = { makeVerifier };
```

`utxosAt` against your node (UNTESTED here: no node RPC was reachable from the test machine; field names as returned by the v2.1.0 WASM SDK's `getUtxosByAddresses`):

```js
// UNTESTED here
const utxosAt = async addr => ((await rpc.getUtxosByAddresses({ addresses: [addr] })).entries || []).map(e => {
  const op = e.outpoint || (e.entry && e.entry.outpoint) || {};
  return { txid: String(op.transactionId).toLowerCase(), vout: Number(op.index),
           amount: BigInt(e.amount ?? (e.entry && e.entry.amount) ?? 0), covenantId: e.covenantId ?? (e.entry && e.entry.covenantId) ?? null };
});
```

## Appendix B. Running the node's script engine

### B.1 Harness

Build inside a rusty-kaspa `v2.1.0` checkout so it uses that workspace's lock file:

1. `git clone --depth 1 --branch v2.1.0 https://github.com/kaspanet/rusty-kaspa`
2. Add `"kccrun",` to `members` in the root `Cargo.toml`.
3. Create `kccrun/Cargo.toml` and `kccrun/src/main.rs` as below.
4. `cargo build --release -p kccrun`.

```toml
[package]
name = "kccrun"
version = "0.1.0"
edition.workspace = true

[dependencies]
kaspa-consensus-core.workspace = true
kaspa-txscript.workspace = true
kaspa-hashes.workspace = true
secp256k1.workspace = true
serde_json.workspace = true
```

```rust
// kccrun: run every input of a v1 transaction through the rusty-kaspa v2.1.0 script engine.
// stdin JSON:
// { version, lockTime, payload,
//   inputs:  [{ txid, index, sigScript, sequence, computeBudget }],
//   outputs: [{ value, spkVersion, script, covenant: { authorizingInput, covenantId } | null }],
//   utxos:   [{ amount, spkVersion, script, daaScore, covenantId | null }],
//   sign?:   [{ idx, key, prefix, wprefix, suffix, hashType? }]   sigScript = prefix ‖ push(wprefix ‖ sig64 ‖ type) ‖ suffix
//   derive?: [secret hex]  → prints x-only keys }
// prints { txid, inputs: [{ idx, ok, err, units }], mass: { compute, transient, storage } }
use kaspa_consensus_core::hashing::sighash::{calc_schnorr_signature_hash, SigHashReusedValuesUnsync};
use kaspa_consensus_core::hashing::sighash_type::SigHashType;
use kaspa_consensus_core::subnets::SubnetworkId;
use kaspa_consensus_core::tx::{
    CovenantBinding, PopulatedTransaction, ScriptPublicKey, Transaction, TransactionInput, TransactionOutpoint, TransactionOutput, UtxoEntry,
};
use kaspa_hashes::Hash;
use kaspa_txscript::caches::Cache;
use kaspa_txscript::covenants::CovenantsContext;
use kaspa_txscript::{EngineCtx, TxScriptEngine};
use serde_json::{json, Value};
use std::io::Read;
use std::str::FromStr;

fn hx(v: &Value) -> Vec<u8> {
    let s = v.as_str().unwrap_or("");
    (0..s.len() / 2).map(|i| u8::from_str_radix(&s[2 * i..2 * i + 2], 16).unwrap()).collect()
}
fn num(v: &Value) -> u64 {
    if let Some(n) = v.as_u64() { n } else { v.as_str().map(|s| s.parse().unwrap()).unwrap_or(0) }
}
fn hash(v: &Value) -> Option<Hash> {
    v.as_str().filter(|s| s.len() == 64).map(|s| Hash::from_str(s).unwrap())
}
fn push(b: &[u8]) -> Vec<u8> {
    let n = b.len();
    let mut o = if n <= 75 { vec![n as u8] } else if n <= 255 { vec![0x4c, n as u8] } else { vec![0x4d, (n & 255) as u8, (n >> 8) as u8] };
    o.extend_from_slice(b);
    o
}

fn main() {
    let mut s = String::new();
    std::io::stdin().read_to_string(&mut s).unwrap();
    let j: Value = serde_json::from_str(&s).unwrap();
    if let Some(keys) = j["derive"].as_array() {
        let out: Vec<String> = keys.iter().map(|k| {
            let kp = secp256k1::Keypair::from_seckey_slice(secp256k1::SECP256K1, &hx(k)).unwrap();
            kp.x_only_public_key().0.serialize().iter().map(|b| format!("{b:02x}")).collect()
        }).collect();
        println!("{}", json!({ "xonly": out }));
        return;
    }
    let inputs: Vec<TransactionInput> = j["inputs"].as_array().unwrap().iter().map(|i| {
        let op = TransactionOutpoint::new(hash(&i["txid"]).unwrap(), num(&i["index"]) as u32);
        TransactionInput::new_with_compute_budget(op, hx(&i["sigScript"]), num(&i["sequence"]), num(&i["computeBudget"]) as u16)
    }).collect();
    let outputs: Vec<TransactionOutput> = j["outputs"].as_array().unwrap().iter().map(|o| {
        let spk = ScriptPublicKey::from_vec(num(&o["spkVersion"]) as u16, hx(&o["script"]));
        let cov = if o["covenant"].is_null() { None } else {
            Some(CovenantBinding::new(num(&o["covenant"]["authorizingInput"]) as u16, hash(&o["covenant"]["covenantId"]).unwrap()))
        };
        TransactionOutput::with_covenant(num(&o["value"]), spk, cov)
    }).collect();
    let utxos: Vec<UtxoEntry> = j["utxos"].as_array().unwrap().iter().map(|u| {
        UtxoEntry::new(num(&u["amount"]), ScriptPublicKey::from_vec(num(&u["spkVersion"]) as u16, hx(&u["script"])), num(&u["daaScore"]),
                       false, hash(&u["covenantId"]))
    }).collect();
    let mut tx = Transaction::new(num(&j["version"]) as u16, inputs, outputs, num(&j["lockTime"]), SubnetworkId::default(), 0, hx(&j["payload"]));
    if let Some(signs) = j["sign"].as_array() {
        let mut scripts = vec![];
        {
            let populated = PopulatedTransaction::new(&tx, utxos.clone());
            let reused = SigHashReusedValuesUnsync::new();
            for sgn in signs {
                let idx = num(&sgn["idx"]) as usize;
                let ht = SigHashType::from_u8(if sgn["hashType"].is_null() { 1 } else { num(&sgn["hashType"]) as u8 }).unwrap();
                let h = calc_schnorr_signature_hash(&populated, idx, ht, &reused);
                let msg = secp256k1::Message::from_digest_slice(h.as_bytes().as_slice()).unwrap();
                let kp = secp256k1::Keypair::from_seckey_slice(secp256k1::SECP256K1, &hx(&sgn["key"])).unwrap();
                let sig: [u8; 64] = *kp.sign_schnorr(msg).as_ref();
                let mut w = hx(&sgn["wprefix"]);
                w.extend_from_slice(&sig);
                w.push(ht.to_u8());
                let mut sc = hx(&sgn["prefix"]);
                sc.extend(push(&w));
                sc.extend(hx(&sgn["suffix"]));
                scripts.push((idx, sc));
            }
        }
        for (idx, sc) in scripts { tx.inputs[idx].signature_script = sc; }
    }
    tx.finalize();
    let populated = PopulatedTransaction::new(&tx, utxos.clone());
    let cov_ctx = match CovenantsContext::from_tx(&populated) {
        Ok(c) => c,
        Err(e) => { println!("{}", json!({ "txid": tx.id().to_string(), "covenantsError": format!("{e}") })); return; }
    };
    let cache = Cache::new(10_000);
    let reused = SigHashReusedValuesUnsync::new();
    let mut res = vec![];
    for (idx, input) in tx.inputs.iter().enumerate() {
        let ctx = EngineCtx::new(&cache).with_reused(&reused).with_covenants_ctx(&cov_ctx);
        let limit = input.compute_commit.allowed_script_units();
        let mut vm = TxScriptEngine::from_transaction_input_with_script_units_limit(&populated, input, idx, &utxos[idx], ctx, Default::default(), limit);
        let r = vm.execute();
        res.push(json!({ "idx": idx, "ok": r.is_ok(), "err": r.err().map(|e| format!("{e}")), "units": vm.used_script_units().0 }));
    }
    let sigs: Vec<String> = tx.inputs.iter().map(|i| i.signature_script.iter().map(|b| format!("{b:02x}")).collect()).collect();
    let mc = kaspa_consensus_core::mass::MassCalculator::new(1, 10, kaspa_consensus_core::constants::STORAGE_MASS_PARAMETER);
    let nc = mc.calc_non_contextual_masses(&tx);
    let cm = mc.calc_contextual_masses(&PopulatedTransaction::new(&tx, utxos.clone()));
    println!("{}", json!({ "txid": tx.id().to_string(), "inputs": res, "sigScripts": sigs,
        "mass": { "compute": nc.compute_mass, "transient": nc.transient_mass, "storage": cm.map(|c| c.storage_mass) } }));
}
```

The engine checks scripts and covenant bindings only. Fees, the UTXO set, double spends, sequence locks, standardness and mempool limits are the node's.

### B.2 Replaying mainnet spends

Feed a transaction with each input's UTXO (amount, script, covenant ID). On 2026-11-17 these reproduced their mainnet IDs and passed every input: `f3fe248b…`, `1842808d…`, `de360632…` (sequences 0, 1), `8c8d554e…` (0, 1), `c26e67e9…` (0, 1), `687ed735…` (0, 1), `c03d5ea0…` and `b59b4400…` (0, 1, 2), `b7fd0a9a…`, `7d424a92…`, `c107c6e5…`. Sequences are not in the public REST response; they were found by matching the transaction ID.

### B.3 Attacks on the reference cell (engine verdicts)

Built with A.2, test keys and the program taken from chain with A.3; signed by `kccrun`. 2026-11-17.

| Case | Verdict |
|---|---|
| honest transfer 1000 → 600 to B + 400 change | accepted |
| outputs total 1001 of 1000 | refused: input 0, "script ran, but verification failed" |
| outputs total 999 of 1000 | refused: input 0, same |
| leader signed by a key that is not the owner | refused: input 0, same |
| change re-pointed to a third owner after the owner signed | refused: input 0, same; input 1, "false stack entry at end of script execution" |
| continuation holds a different state than its `next_states` entry | refused: input 0 |
| continuation with another `extension_commitment` | refused: input 0 |
| four continuations | refused: input 0 |
| split into three continuations | accepted |
| a continuation without its covenant binding | refused: input 0 |
| a continuation with amount −1 | refused: input 0 |
| merge three cells of one owner | accepted |
| merge with B's delegator signed by A | refused: input 1 |
| merge of A's and B's cells, each signing their own | accepted |
| four cell inputs | refused: input 0 |
| delegator from another family | refused: inputs 0 and 1 |
| same program and state, input carrying no covenant ID | refused: "covenant id 0000…0000 input 0 is out of bounds" |
| wallet-style signatures assembled with `leaderScript` and `delegatorScript` | accepted; the same leader at budget 1: `used=20721, limit=19999` |

### B.4 Budgets, sighash and genesis (engine, real mainnet data)

| Test | Result |
|---|---|
| `TX:de360632…` as signed (budgets 128, 11) | both inputs pass; leader 144,843 units |
| leader budget 14, same signatures | pass; same ID; compute mass 8,418 |
| leader budget 13 | refused: `used=144688, limit=139999` |
| leader budget 300, same signatures | pass; same ID (a node would still require the fee for 37,018 g) |
| KAS input budget 9 | refused: `used=100000, limit=99999` |
| one output 1 sompi lower | both signatures fail |
| `TX:7d424a92…` (genesis) as on chain | pass; ID reproduced |
| same genesis, covenant ID replaced | refused: "do not correspond to the expected genesis hashing" |
| same genesis, a bound output's value changed | refused: same message |
| same genesis, the unbound change changed | covenant check passes; the signature fails |

## Appendix C. Transaction index

| Txid (mainnet) | Shape | Section |
|---|---|---|
| `de36063206e944bb1716645b03fbb5198f2079452286744d31b05ce8511e74f8` | transfer with change | 3.1, 4 |
| `8c8d554e5b3235534d40f4d0c62afe8cf69c769b9b7ec47475f31186137cf245` | whole-coin transfer | 3.1 |
| `c26e67e9322c131a880126c9d8591888c418d571ec011416c1cdc4640d55c919` | split | 3.2 |
| `c03d5ea0f459aefdd08e767f9728da60aa63eacd7839ae1befb190e383c0c466` | merge 3 → 1 | 3.3 |
| `687ed7353265485d4332eed49f6f5fee7d9eb7f82e1855bc65a6ad5371c453da` | merge 2 → 1 | 3.3 |
| `b59b4400779f071edf4d064a3e20a6d7b408341a2c7e3b729bfdfb501fee4f1b` | merge 3 → 1 to another owner | 3.3 |
| `7d424a92d5157e2bd96986ce36cf9df43ddb499c16226ceb3cc574265e434f43` | genesis, fixed supply | 3.5 |
| `3c7df6bb290c4201830885d5be8cf029b7ea1ea875961d50ddf87d7d65519a04` | genesis with unbound extra outputs | 3.5 |
| `b7fd0a9af952079277f55a12513f7508921463b913d470f5787c96e7be64a042` | mint by a minter program | 3.5 |
| `4c4cabba4e7e4847e3db0eb321277caab302cfe2a19a0c4c058a449fadc8f330` | issuer-family mint (other program) | 3.5 |
| `1842808d7c30f9aae2f317085b51346b51bb8f695bda31fe9fe0d0b55832b64e` | burn to the OP_RETURN owner | 3.6 |
| `36cecdf023f0a6c54c8090d9f1c85e8130eee51a9538db715dd15b7a29cde576` | pool owner withdraw; live cell of 1.5 and 2.3 | 5.1 |
| `644afc958a926f114a5aa893e6026b36c2dce65fd7e9043a723df1ecc59b2db0` | pool genesis | 5.1 |
| `c107c6e500d7cb7f5779b8300e9b814c08507f6237bfe8458a24af85a1dfb0e3` | pool buy | 5.1 |
| `bac1ef1ce4ee8e7c73a6861263a0410ebf1dec7a90fd492c38d4dbabbd8045a4` | pool buy | 5.1 |
| `f3fe248b5e499f32a72763746ca09eb0b275bf4e77b5fdedb59632b4e654346b` | pool sell | 5.1 |
| `83df3c1e2c0bff6d07b977bfafbe863713eacf11d0bde72547dd21504dde9579` | curve init | 5.2 |
| `c74a980208b180b2e210a6b8067281862a7c964e715a8422a5618b9e83c77984` | curve buy | 5.2 |
| `cbf62816eaaeb20ee4c7de343c15f70f6545f015a15d9f8a9fa4c6319c6d1dfa` | curve buy | 5.2 |
| `6b335a40b9c931dcdfa10b57c715167f361c7a58a3144ad85e3c7a0af97aac53` | curve buy | 5.2 |
