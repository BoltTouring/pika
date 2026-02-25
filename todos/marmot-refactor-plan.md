# Marmot Refactor Plan (Branch: `marmot-refactor`)

## Decision Update
- Keep Phase 1 and current Phase 2 work.
- Do not revert.
- Internal RPC remains ACP-only.
- Temporarily freeze Cloudflare Workers to increase delivery speed on Fly + MicroVM.

## Current State
- Phase 1 (control-plane schema extraction) is complete.
- Phase 2 (ACP-only protocol cut in Rust/CLI/control-plane) is largely complete.
- Remaining cleanup/hardening is required before moving deeper:
  - control-plane persisted-state compatibility for prior PI protocol values
  - CLI `--brain` semantics cleanup in `agent new`

## Priority Order (Updated)
1. Finish Phase 2 hardening.
2. Insert and land Workers freeze phase.
3. Continue remaining refactor phases on ACP/Fly/MicroVM only.

## Execution Plan

### PR1 (Done): Control-plane schema crate extraction
- `crates/pika-agent-control-plane` extracted.
- `pika-server -> cli` dependency edge removed.

### PR1.1 (Open): provider-client contract tests
- Restore/retain provider client request/response contract tests in server-owned client modules (or centralize shared client code with tests).

Acceptance:
- Contract tests for Fly + MicroVM + Workers client adapters used by `pika-server`.

### PR2 (Done): ACP-only protocol cut (Rust/CLI/control-plane)
- `crates/pika-agent-protocol` is ACP-only.
- CLI no longer uses PI protocol switching for control-plane provisioning.
- `ProtocolKind` is ACP-only in control-plane schema.

Acceptance:
- No `AgentProtocol::Pi` in Rust.
- No `ProtocolKind::Pi` in control-plane schema.

### PR2.1 (Required next): Phase 2 hardening
Tasks:
- Add backward-compat handling for persisted control-plane state containing historical PI protocol values.
- Fix/clarify `agent new --brain` behavior:
  - either remove/disable options that no longer affect provisioning behavior,
  - or wire behavior explicitly and document it.

Acceptance:
- Server state load does not drop runtimes on protocol decode mismatch from legacy state.
- CLI agent-new surface has no misleading protocol/brain semantics.

### PR2.2 (New): Workers freeze
Tasks:
- Disable Workers provider path for now in CLI/server with explicit, user-facing "temporarily disabled" errors.
- Stop treating Workers as active execution target in plan/CI/smokes/docs.
- Keep code in tree behind freeze guard (no full deletion in this phase).

Acceptance:
- `agent new --provider workers` fails fast with intentional message.
- Active CI/smokes for this refactor do not depend on Workers.
- Fly + MicroVM flow remains intact.

### PR3: Runtime helper extraction (`pika-marmot-runtime`) for ACP/Fly/MicroVM
Tasks:
- Extract shared helpers for CLI + sidecar + harness:
  - identity bootstrap
  - MDK open/new helpers
  - processed-event dedupe persistence
- Replace duplicated implementations.

Acceptance:
- No duplicate `load_or_create_keys` / `new_mdk` across CLI, sidecar, harness.

### PR4: Shared welcome/message ingest primitives
Tasks:
- Extract and reuse welcome + message ingest primitives in CLI and sidecar.

Acceptance:
- CLI and sidecar share common ingest logic with parity tests.

### PR5: MicroVM provider extraction
Tasks:
- Create `crates/pika-agent-microvm`.
- Move shared microvm provisioning/default/script generation from CLI + server.

Acceptance:
- No copy-pasted microvm provisioning/scripts across CLI and server.

### PR6: Relay/default profile normalization
Tasks:
- Centralize defaults into explicit profiles.
- Replace per-component hardcoded relay defaults where appropriate.

Acceptance:
- Defaults are intentional and centrally defined.

### PR7 (Optional): `mdk_support` convergence (app + NSE)
- Only after core refactor stabilizes.

## Workers Re-entry (Later)
When Fly + MicroVM + ACP core stabilize, decide one of:
1. Re-enable Workers on top of shared ACP core, and remove legacy `brain` request fields from Workers APIs/state (or map them internally as deprecated aliases during a short migration window).
2. Remove Workers code entirely if still not needed, including all remaining Workers-specific `brain` fields/usages.

## Immediate Next Steps for Coding Agent
1. Land PR2.1 hardening.
2. Land PR2.2 Workers freeze.
3. Continue with PR3 (runtime helper extraction) on ACP/Fly/MicroVM only.

## Guardrails
- No `AgentProtocol::Pi` in Rust protocol core.
- No `ProtocolKind::Pi` in control-plane schema.
- No PI protocol defaults in CLI control-plane flows.
- Workers is frozen (explicitly disabled) until re-entry decision.

## Test Strategy
Per PR:
- `cargo test -p pika-agent-control-plane`
- `cargo test -p pika-agent-protocol`
- `cargo test -p pikachat`
- `cargo check -p pika-server`
- `cargo test -p pikachat-sidecar` (when touched)
- `cargo test -p pikachat-wasm` (when touched)

Smokes (active):
- control-plane local demo
- Fly path smoke
- MicroVM path smoke

Smokes (paused during freeze):
- Workers-specific smoke jobs

## Done Criteria
- ACP is the sole internal RPC protocol.
- Shared crates own protocol/runtime helper core.
- Fly + MicroVM are stable on shared ACP-first architecture.
- Workers either re-enabled cleanly on shared core or deliberately removed later.
