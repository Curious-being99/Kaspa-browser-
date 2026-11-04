//! Linux CEF process adapter.
//! The CEF host is a separate process; page JavaScript never receives this pipe.
use crate::api::{sanitize_navigation_url, BrowserEvent, BrowserPlatform, EngineCapabilities, EngineKind, NavigationCommand, NavigationKind};
use crate::browser_engine::BrowserEngine;
use serde_json::Value;
use std::io::{BufRead, BufReader, Write};
use std::process::{Child, ChildStdin, Command, Stdio};
use std::sync::mpsc::{self, Receiver, TryRecvError};
use std::thread;

pub struct LinuxCefEngine { child: Child, stdin: ChildStdin, events: Receiver<Value> }

impl LinuxCefEngine {
    pub fn launch(executable: &str, initial_url: &str) -> Result<Self, String> {
        let url = sanitize_navigation_url(initial_url).map_err(|e| e.to_string())?;
        let mut child = Command::new(executable).arg(format!("--url={url}"))
            .stdin(Stdio::piped()).stdout(Stdio::piped()).stderr(Stdio::inherit())
            .spawn().map_err(|e| format!("failed to start Linux CEF host: {e}"))?;
        let stdin = child.stdin.take().ok_or("CEF host stdin unavailable")?;
        let stdout = child.stdout.take().ok_or("CEF host stdout unavailable")?;
        let (tx, events) = mpsc::channel();
        thread::Builder::new().name("kaspa-cef-events".into()).spawn(move || {
            for line in BufReader::new(stdout).lines() {
                let Ok(line) = line else { break };
                if let Ok(value) = serde_json::from_str::<Value>(&line) {
                    if tx.send(value).is_err() { break; }
                }
            }
        }).map_err(|e| format!("failed to start CEF event reader: {e}"))?;
        Ok(Self { child, stdin, events })
    }
    fn send(&mut self, command: &str) -> Result<(), String> {
        writeln!(self.stdin, "{command}").map_err(|e| format!("CEF command write failed: {e}"))?;
        self.stdin.flush().map_err(|e| format!("CEF command flush failed: {e}"))
    }
    fn decode_event(v: Value) -> Option<BrowserEvent> {
        Some(match v.get("event")?.as_str()? {
            "navigation_started" => BrowserEvent::NavigationStarted { url: v.get("url")?.as_str()?.into() },
            "navigation_committed" => BrowserEvent::NavigationCommitted { url: v.get("url")?.as_str()?.into() },
            "load_progress" => BrowserEvent::LoadProgress { percent: v.get("percent")?.as_u64()?.min(100) as u8 },
            "load_finished" => BrowserEvent::LoadFinished { url: v.get("url")?.as_str()?.into() },
            "load_failed" => BrowserEvent::LoadFailed { url: v.get("url")?.as_str()?.into(), code: v.get("code")?.as_i64()? as i32 },
            "title_changed" => BrowserEvent::TitleChanged { title: v.get("title")?.as_str()?.into() },
            _ => return None,
        })
    }
}
impl BrowserEngine for LinuxCefEngine {
    type Error = String;
    fn capabilities(&self) -> EngineCapabilities {
        EngineCapabilities { platform: BrowserPlatform::Linux, engine: EngineKind::ChromiumEmbeddedFramework,
            supports_javascript: true, supports_service_workers: true, supports_web_messages: false,
            supports_downloads: false, supports_media_playback: true }
    }
    fn execute(&mut self, command: NavigationCommand) -> Result<(), String> {
        match command.kind {
            NavigationKind::Navigate => {
                let raw = command.url.as_deref().ok_or("navigate command requires a URL")?;
                let url = sanitize_navigation_url(raw).map_err(|e| e.to_string())?;
                self.send(&format!("NAVIGATE\t{url}"))
            }
            NavigationKind::Reload => self.send("RELOAD"),
            NavigationKind::Back => self.send("BACK"),
            NavigationKind::Forward => self.send("FORWARD"),
            NavigationKind::Stop => self.send("STOP"),
        }
    }
    fn poll_event(&mut self) -> Option<BrowserEvent> {
        match self.events.try_recv() {
            Ok(v) => Self::decode_event(v),
            Err(TryRecvError::Empty | TryRecvError::Disconnected) => None,
        }
    }
}
impl Drop for LinuxCefEngine {
    fn drop(&mut self) { let _ = self.send("QUIT"); let _ = self.child.kill(); let _ = self.child.wait(); }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn decodes_navigation_progress_and_title_events() {
        assert_eq!(LinuxCefEngine::decode_event(serde_json::json!({"event":"load_progress","percent":45})), Some(BrowserEvent::LoadProgress { percent: 45 }));
        assert_eq!(LinuxCefEngine::decode_event(serde_json::json!({"event":"title_changed","title":"KaspaBrowser"})), Some(BrowserEvent::TitleChanged { title: "KaspaBrowser".into() }));
    }
    #[test]
    fn ignores_unknown_events() {
        assert_eq!(LinuxCefEngine::decode_event(serde_json::json!({"event":"internal_debug"})), None);
    }
}
