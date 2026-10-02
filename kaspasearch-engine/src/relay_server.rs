/// Kaspa Privacy Relay Protocol (KRP/1) Standalone & Embedded Server Engine.
/// Provides complete Entry Relay & Exit Relay server implementations.

use std::sync::Arc;
use tokio::net::{TcpListener, TcpStream};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use serde::{Deserialize, Serialize};

use crate::crypto::{
    hex_to_bytes, bytes_to_hex, x25519_diffie_hellman, hkdf_sha256,
    chacha20_poly1305_decrypt, chacha20_poly1305_encrypt, fill_csprng_bytes
};
use crate::relay::{KrpFrame, KRP_CELL_SIZE, KRP_MAGIC};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RelayServerRole {
    EntryNode,
    ExitNode,
}

#[derive(Debug, Clone)]
pub struct KrpRelayServerConfig {
    pub role: RelayServerRole,
    pub listen_address: String,
    pub listen_port: u16,
    pub private_key_hex: String,
    pub public_key_hex: String,
}

pub struct KrpRelayServer {
    config: KrpRelayServerConfig,
    http_client: reqwest::Client,
}

impl KrpRelayServer {
    pub fn new(config: KrpRelayServerConfig) -> Self {
        let http_client = reqwest::Client::builder()
            .user_agent("Mozilla/5.0 (Linux; Android 14; Mobile; KRP1/1.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.6613.127 Mobile Safari/537.36")
            .timeout(std::time::Duration::from_secs(15))
            .redirect(reqwest::redirect::Policy::limited(10))
            .gzip(true)
            .brotli(true)
            .build()
            .unwrap_or_default();

        Self {
            config,
            http_client,
        }
    }

    /// Starts the KRP/1 Relay Server listener loop
    pub async fn start(&self) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let addr = format!("{}:{}", self.config.listen_address, self.config.listen_port);
        let listener = TcpListener::bind(&addr).await?;
        println!("[KRP/1 Relay Server] Listening on {} as {:?}...", addr, self.config.role);

        let server_arc = Arc::new(self.clone_self());

        loop {
            match listener.accept().await {
                Ok((socket, peer_addr)) => {
                    let server_ref = Arc::clone(&server_arc);
                    tokio::spawn(async move {
                        if let Err(e) = server_ref.handle_connection(socket).await {
                            eprintln!("[KRP/1 Relay Server] Client error {}: {}", peer_addr, e);
                        }
                    });
                }
                Err(e) => {
                    eprintln!("[KRP/1 Relay Server] Accept error: {}", e);
                }
            }
        }
    }

    fn clone_self(&self) -> Self {
        Self {
            config: self.config.clone(),
            http_client: self.http_client.clone(),
        }
    }

    async fn handle_connection(&self, mut socket: TcpStream) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let mut buffer = [0u8; KRP_CELL_SIZE * 4];
        let n = socket.read(&mut buffer).await?;
        if n < KRP_CELL_SIZE {
            return Err("Received packet smaller than 1024-byte KRP cell".into());
        }

        let frame = KrpFrame::deserialize_from_cell(&buffer[..n])?;

        match self.config.role {
            RelayServerRole::EntryNode => {
                self.process_entry_relay(&mut socket, &frame).await?;
            }
            RelayServerRole::ExitNode => {
                self.process_exit_relay(&mut socket, &frame).await?;
            }
        }

        Ok(())
    }

    /// Process Entry Relay Hop:
    /// Unwraps Outer Layer using Entry Key derived via X25519/HKDF.
    /// Entry Relay ONLY sees Exit Node endpoint, NOT the target website or inner payload!
    async fn process_entry_relay(
        &self,
        socket: &mut TcpStream,
        frame: &KrpFrame,
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let mut node_priv = [0u8; 32];
        let hex_priv = hex_to_bytes(&self.config.private_key_hex);
        if hex_priv.len() >= 32 {
            node_priv.copy_from_slice(&hex_priv[..32]);
        }

        let shared_secret = x25519_diffie_hellman(&node_priv, &frame.ephemeral_pubkey);
        let entry_key_vec = hkdf_sha256(b"KRP1-Entry-Salt", &shared_secret, b"KRP1-Entry-Session-Key", 32);
        let mut entry_key = [0u8; 32];
        entry_key.copy_from_slice(&entry_key_vec[..32]);

        // Decrypt Outer Layer & Verify Poly1305 MAC Auth Tag
        let outer_plaintext = chacha20_poly1305_decrypt(
            &entry_key,
            &frame.nonce,
            &frame.ciphertext,
            &frame.tag,
            b"KRP1-Outer-AAD-Entry"
        )?;

        let outer_str = String::from_utf8(outer_plaintext)?;

        #[derive(Deserialize)]
        struct OuterEnvelope {
            exit_host: String,
            exit_port: u16,
            inner_nonce_hex: String,
            inner_ciphertext_hex: String,
            inner_tag_hex: String,
            client_pubkey_hex: String,
        }

        let outer: OuterEnvelope = serde_json::from_str(&outer_str)?;

        // Build Inner KRP Frame to forward to Exit Node
        let inner_nonce_bytes = hex_to_bytes(&outer.inner_nonce_hex);
        let inner_ciphertext_bytes = hex_to_bytes(&outer.inner_ciphertext_hex);
        let inner_tag_bytes = hex_to_bytes(&outer.inner_tag_hex);
        let client_pub_bytes = hex_to_bytes(&outer.client_pubkey_hex);

        let mut inner_nonce = [0u8; 12];
        if inner_nonce_bytes.len() >= 12 { inner_nonce.copy_from_slice(&inner_nonce_bytes[..12]); }

        let mut inner_tag = [0u8; 16];
        if inner_tag_bytes.len() >= 16 { inner_tag.copy_from_slice(&inner_tag_bytes[..16]); }

        let mut client_pub = [0u8; 32];
        if client_pub_bytes.len() >= 32 { client_pub.copy_from_slice(&client_pub_bytes[..32]); }

        let inner_frame = KrpFrame {
            magic: *KRP_MAGIC,
            version: 1,
            frame_type: 2,
            circuit_id: frame.circuit_id,
            ephemeral_pubkey: client_pub,
            nonce: inner_nonce,
            payload_len: inner_ciphertext_bytes.len() as u16,
            ciphertext: inner_ciphertext_bytes,
            tag: inner_tag,
        };

        let forwarded_cell = inner_frame.serialize_to_1024_cell();

        // Connect to Exit Node and relay stream
        let exit_addr = format!("{}:{}", outer.exit_host, outer.exit_port);
        let mut exit_stream = TcpStream::connect(&exit_addr).await
            .map_err(|e| format!("Exit node TCP connection error {}: {}", exit_addr, e))?;

        exit_stream.write_all(&forwarded_cell).await?;

        let mut exit_resp_buf = [0u8; KRP_CELL_SIZE * 4];
        let resp_len = exit_stream.read(&mut exit_resp_buf).await?;

        if resp_len > 0 {
            socket.write_all(&exit_resp_buf[..resp_len]).await?;
        } else {
            return Err("Exit node closed stream with zero-byte response".into());
        }

        Ok(())
    }

    /// Process Exit Relay Hop:
    /// Unwraps Inner Layer using Exit Key derived via X25519/HKDF.
    /// Resolves DNS EXCLUSIVELY at the Exit node (no client DNS leak), fetches target website, and returns response.
    async fn process_exit_relay(
        &self,
        socket: &mut TcpStream,
        frame: &KrpFrame,
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let mut node_priv = [0u8; 32];
        let hex_priv = hex_to_bytes(&self.config.private_key_hex);
        if hex_priv.len() >= 32 {
            node_priv.copy_from_slice(&hex_priv[..32]);
        }

        let shared_secret = x25519_diffie_hellman(&node_priv, &frame.ephemeral_pubkey);
        let exit_key_vec = hkdf_sha256(b"KRP1-Exit-Salt", &shared_secret, b"KRP1-Exit-Session-Key", 32);
        let mut exit_key = [0u8; 32];
        exit_key.copy_from_slice(&exit_key_vec[..32]);

        // Decrypt Inner Layer & Verify Poly1305 MAC Auth Tag
        let inner_plaintext = chacha20_poly1305_decrypt(
            &exit_key,
            &frame.nonce,
            &frame.ciphertext,
            &frame.tag,
            b"KRP1-Inner-AAD-Exit"
        )?;

        let inner_str = String::from_utf8(inner_plaintext)?;

        #[derive(Deserialize)]
        struct InnerPayload {
            destination_url: String,
            method: String,
            headers: String,
            body_hex: Option<String>,
        }

        let inner: InnerPayload = serde_json::from_str(&inner_str)?;

        // Execute Request to Web Destination (DNS resolved exclusively through Exit node)
        let mut req = self.http_client.request(
            reqwest::Method::from_bytes(inner.method.as_bytes())?,
            &inner.destination_url
        );

        let mut has_ua = false;
        if let Ok(headers_map) = serde_json::from_str::<std::collections::HashMap<String, String>>(&inner.headers) {
            for (k, v) in headers_map {
                if k.eq_ignore_ascii_case("user-agent") {
                    has_ua = true;
                }
                req = req.header(&k, &v);
            }
        }

        if !has_ua {
            req = req.header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile; KRP1/1.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.6613.127 Mobile Safari/537.36")
                     .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                     .header("Accept-Language", "en-US,en;q=0.9")
                     .header("Sec-Ch-Ua", "\"Chromium\";v=\"128\", \"Not;A=Brand\";v=\"24\", \"Google Chrome\";v=\"128\"")
                     .header("Sec-Ch-Ua-Mobile", "?1")
                     .header("Sec-Ch-Ua-Platform", "\"Android\"");
        }

        if let Some(body_hex) = inner.body_hex {
            let body_bytes = hex_to_bytes(&body_hex);
            if !body_bytes.is_empty() {
                req = req.body(body_bytes);
            }
        }

        let resp = req.send().await?;
        let status = resp.status().as_u16();

        let mut hdrs = std::collections::HashMap::new();
        for (k, v) in resp.headers().iter() {
            let key_lower = k.as_str().to_lowercase();
            if key_lower == "content-encoding" || key_lower == "transfer-encoding" || key_lower == "content-length" {
                continue;
            }
            if let Ok(val_str) = v.to_str() {
                hdrs.insert(k.as_str().to_string(), val_str.to_string());
            }
        }

        let body_bytes = resp.bytes().await?.to_vec();

        #[derive(Serialize)]
        struct ExitResponsePayload {
            status_code: u16,
            status_message: String,
            headers: std::collections::HashMap<String, String>,
            body_hex: String,
            exit_node_ip: String,
        }

        let exit_resp = ExitResponsePayload {
            status_code: status,
            status_message: "OK".to_string(),
            headers: hdrs,
            body_hex: bytes_to_hex(&body_bytes),
            exit_node_ip: self.config.listen_address.clone(),
        };

        let resp_json = serde_json::to_string(&exit_resp)?;

        // Encrypt Response Back with Exit Key + Auth Tag
        let mut resp_nonce = [0u8; 12];
        fill_csprng_bytes(&mut resp_nonce);

        let (resp_ciphertext, resp_tag) = chacha20_poly1305_encrypt(
            &exit_key,
            &resp_nonce,
            resp_json.as_bytes(),
            b"KRP1-Response-AAD-Exit"
        );

        let resp_frame = KrpFrame {
            magic: *KRP_MAGIC,
            version: 1,
            frame_type: 2,
            circuit_id: frame.circuit_id,
            ephemeral_pubkey: frame.ephemeral_pubkey,
            nonce: resp_nonce,
            payload_len: resp_ciphertext.len() as u16,
            ciphertext: resp_ciphertext,
            tag: resp_tag,
        };

        let resp_cell = resp_frame.serialize_to_1024_cell();
        socket.write_all(&resp_cell).await?;

        Ok(())
    }
}
