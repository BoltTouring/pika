use anyhow::Context;
use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;
use std::time::Duration;

const CREATE_VM_TIMEOUT: Duration = Duration::from_secs(20);
const DELETE_VM_TIMEOUT: Duration = Duration::from_secs(10);

#[derive(Debug, Clone)]
pub struct MicrovmSpawnerClient {
    client: reqwest::Client,
    base_url: String,
}

#[derive(Debug, Serialize)]
pub struct CreateVmRequest {
    pub flake_ref: Option<String>,
    pub dev_shell: Option<String>,
    pub cpu: Option<u32>,
    pub memory_mb: Option<u32>,
    pub ttl_seconds: Option<u64>,
    pub spawn_variant: Option<String>,
    pub guest_autostart: Option<GuestAutostartRequest>,
}

#[derive(Debug, Serialize, Clone)]
pub struct GuestAutostartRequest {
    pub command: String,
    pub env: BTreeMap<String, String>,
    pub files: BTreeMap<String, String>,
}

#[derive(Debug, Deserialize)]
pub struct VmResponse {
    pub id: String,
    pub ip: String,
}

impl MicrovmSpawnerClient {
    pub fn new(base_url: impl Into<String>) -> Self {
        let mut base_url = base_url.into();
        while base_url.ends_with('/') {
            base_url.pop();
        }
        Self {
            client: reqwest::Client::new(),
            base_url,
        }
    }

    pub fn base_url(&self) -> &str {
        &self.base_url
    }

    pub async fn create_vm(&self, req: &CreateVmRequest) -> anyhow::Result<VmResponse> {
        let url = format!("{}/vms", self.base_url);
        let resp = self
            .client
            .post(&url)
            .json(req)
            .timeout(CREATE_VM_TIMEOUT)
            .send()
            .await
            .context("send create vm request")?;
        let status = resp.status();
        if !status.is_success() {
            let text = resp.text().await.unwrap_or_default();
            anyhow::bail!("failed to create vm: {status} {text}");
        }
        resp.json().await.context("decode create vm response")
    }

    pub async fn delete_vm(&self, vm_id: &str) -> anyhow::Result<()> {
        let url = format!("{}/vms/{vm_id}", self.base_url);
        let resp = self
            .client
            .delete(&url)
            .timeout(DELETE_VM_TIMEOUT)
            .send()
            .await
            .context("send delete vm request")?;
        let status = resp.status();
        if !status.is_success() {
            let text = resp.text().await.unwrap_or_default();
            anyhow::bail!("failed to delete vm {vm_id}: {status} {text}");
        }
        Ok(())
    }
}
