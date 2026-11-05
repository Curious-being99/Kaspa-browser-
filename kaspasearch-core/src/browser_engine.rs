use crate::api::{BrowserEvent, EngineCapabilities, NavigationCommand};

pub trait BrowserEngine {
    type Error;

    fn capabilities(&self) -> EngineCapabilities;
    fn execute(&mut self, command: NavigationCommand) -> Result<(), Self::Error>;
    fn poll_event(&mut self) -> Option<BrowserEvent>;
}
