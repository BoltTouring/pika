# Marmot Refactor Plan (Branch: `marmot-refactor`)

## Decision Update (ACP-only)
- Keep the current branch and Phase 1 extraction work.
- Do **not** run dual protocol paths (`pi` + `acp`) going forward.
- Standardize internal RPC layer to **ACP-only** in this refactor.

## Current State (as of now)
- Phase 1 (control-plane crate extraction) is in place on this branch.
- Phase 2 is now implemented ACP-only in Rust control-plane/CLI protocol paths:
  - `crates/pika-agent-protocol` is ACP-only.
  - `cli` `agent new` path provisions ACP only (no PI protocol switch/default).
  - `crates/pika-agent-control-plane` + `pika-server` protocol kind usage is ACP-only.
- Remaining PI text/legacy behavior may still exist outside Phase 2 scope (for example, workers runtime internals), to be removed in PR3.

## Should We Revert?
No.

Rationale:
- Phase 1 changes are orthogonal and useful regardless of protocol choice.
- Reverting now burns time and creates churn with little risk reduction.
- The right move is to pivot immediately while Phase 2 is still early.

## New Execution Plan

### PR1 (already done on branch): Control-plane schema crate
- `crates/pika-agent-control-plane` extracted.
- `pika-server -> cli` dependency edge removed.

### PR1.1 (quick follow-up before deeper refactor)
Restore lost/critical provider-client contract tests in `pika-server` local client modules (or move shared client code into its own crate/module with tests retained).

Acceptance:
- Request/response shape tests exist for Fly + MicroVM + Workers client adapters used by server.

### PR2 (REPLACED): Protocol core import + ACP-only cut
Bring in/keep `crates/pika-agent-protocol`, but make it ACP-only now.

Tasks:
- In `crates/pika-agent-protocol`:
  - Remove `AgentProtocol::Pi`.
  - Remove PI-specific parsing/session helpers.
  - Keep ACP envelope + session + parser as source of truth.
- In CLI:
  - Remove PI protocol options and PI-only harness/session branches.
  - Keep ACP-only path in runtime/harness/session.
- In control-plane schema:
  - Deprecate then remove `ProtocolKind::Pi` in same PR if feasible.

Acceptance:
- No internal Rust code path can emit or accept protocol `pi`.
- Protocol crate tests pass ACP-only.
- Status: complete on this branch.

### PR3: Workers + wasm ACP-only
Tasks:
- In `crates/pikachat-wasm`:
  - Remove PI adapter response exports/functions.
  - Keep ACP parsing/encoding exports.
- In workers runtime wrappers and worker app:
  - Remove PI adapter env vars/branches/parsers.
  - Use ACP-only decode/encode/parse through wasm wrapper.
- Remove `brain=pi` assumptions; make ACP the only runtime protocol path.

Acceptance:
- Workers path has no PI parser logic.
- Worker tests/smokes run ACP-only.

### PR4: Runtime helper extraction (`pika-marmot-runtime`)
Tasks:
- Extract shared helpers used by CLI + sidecar + harness:
  - identity bootstrap
  - MDK open/new helpers
  - processed-event dedupe persistence
- Replace duplicate implementations.

Acceptance:
- No duplicate `load_or_create_keys` / `new_mdk` across CLI, sidecar, harness.

### PR5: Shared welcome/message ingest primitives
Tasks:
- Extract and reuse welcome + message ingest primitives in CLI and sidecar.

Acceptance:
- CLI and sidecar share common ingest logic, with parity tests.

### PR6: MicroVM provider extraction
Tasks:
- Create `crates/pika-agent-microvm`.
- Move shared microvm provisioning/default/script building logic from CLI + server.

Acceptance:
- No copy-pasted microvm provisioning/scripts across CLI and server.

### PR7: Relay/default profile normalization
Tasks:
- Centralize defaults into explicit profiles.
- Replace per-component hardcoded relay defaults where appropriate.

Acceptance:
- Defaults are intentional and centrally defined.

### PR8 (optional): `mdk_support` convergence (app + NSE)
Only after core refactor stabilizes.

## Immediate Next Steps for Coding Agent
1. Finish/stabilize current staged Phase 2 scaffold (add/commit file set cleanly).
2. Execute PR2 as ACP-only protocol cut **before** more worker/runtime wiring.
3. Execute PR3 immediately after PR2 to eliminate remaining PI branches in JS/wasm layer.

## ACP-only Guardrails (must hold after PR3)
- No `AgentProtocol::Pi` in Rust.
- No `ProtocolKind::Pi` in control-plane schema.
- No `parse_pi_*` functions in shared protocol/wasm exports.
- No `PI_ADAPTER_*` env contract in workers/runtime bridge (use ACP equivalents only).
- No `brain=pi` runtime branch in workers provider code.

## Test Strategy
Per PR:
- `cargo test -p pika-agent-control-plane`
- `cargo test -p pika-agent-protocol`
- `cargo test -p pikachat-wasm`
- `cargo test -p pikachat-sidecar`
- `cargo test -p pikachat`
- targeted `pika-server` tests for control-plane + provider adapters

Smokes:
- CLI smoke
- worker ACP smoke
- control-plane local demo

## Done Criteria
- ACP is the sole internal RPC protocol.
- Shared crates own protocol + runtime helper core.
- CLI, sidecar, workers, server consume shared core without PI fallback branches.
- Duplicate Marmot logic removed or consolidated with tests.
