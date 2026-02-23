use anyhow::Context;
use serde::{Deserialize, Serialize};
use std::time::Duration;

const CREATE_VM_TIMEOUT: Duration = Duration::from_secs(20);
const DELETE_VM_TIMEOUT: Duration = Duration::from_secs(10);
const GET_VM_TIMEOUT: Duration = Duration::from_secs(2);

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
}

#[derive(Debug, Deserialize)]
pub struct VmResponse {
    pub id: String,
    pub ip: String,
    #[serde(default = "default_ssh_port")]
    pub ssh_port: u16,
    #[serde(default = "default_ssh_user")]
    pub ssh_user: String,
    pub ssh_private_key: String,
}

#[derive(Debug, Deserialize)]
pub struct VmStatus {
    pub id: String,
    pub ip: String,
}

fn default_ssh_port() -> u16 {
    22
}

fn default_ssh_user() -> String {
    "root".to_string()
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

    pub async fn get_vm(&self, vm_id: &str) -> anyhow::Result<VmStatus> {
        let url = format!("{}/vms/{vm_id}", self.base_url);
        let resp = self
            .client
            .get(&url)
            // Keep polling bounded so SSH wait deadlines are respected even if tunnel/network stalls.
            .timeout(GET_VM_TIMEOUT)
            .send()
            .await
            .with_context(|| format!("send get vm request for {vm_id}"))?;
        let status = resp.status();
        if !status.is_success() {
            let text = resp.text().await.unwrap_or_default();
            anyhow::bail!("failed to get vm {vm_id}: {status} {text}");
        }
        resp.json()
            .await
            .with_context(|| format!("decode vm status response for {vm_id}"))
    }
}
