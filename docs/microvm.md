---
summary: General microVM platform requirements for generic Nix artifact execution and cache-first startup
read_when:
  - working on vm-spawner or microVM startup flow
  - changing artifact bootstrap behavior
  - planning Nix cache strategy for agent execution
---

# MicroVM Requirements

## Goal

MicroVM startup should feel Replit-like: fast, predictable, and cache-first.
The system is generic for any runnable Nix derivation, not specific to `pi`.

## Core Requirements

- Runtime execution must come from Nix store artifacts.
- No per-VM package install/build on the interactive startup path.
- Any runnable derivation already in host store or reachable via binary cache should be launchable.
- Spawn inputs should be generic (artifact + command), not app-specific.
- Warm-start behavior should stay near the VM boot floor, with cold work paid once.

## Cache Model

- Tier 1: host-local `/nix/store` reuse.
- Tier 2: shared binary cache with trusted substituters and signing keys.
- Tier 3: source builds as fallback, ideally moved off interactive startup.
- CI should prebuild and publish expected derivations before deployment.
- Cache-hit and cache-miss metrics should be tracked and visible.

## Runtime Model

- vm-spawner resolves artifact identity to store paths (for example flake output paths).
- Guest receives immutable, prebuilt artifacts via store-backed mount/copy.
- VM lifecycle is separate from artifact resolution and readiness checks.
- Logs should record exact store paths used for each VM launch.

## Performance Guardrails (MVP)

- Warm create path: approximately 2-3 seconds.
- SSH ready: target under 10 seconds on healthy hosts.
- Time to first successful agent response: target under 15 seconds on healthy network.
- Regressions should be caught by automated perf checks where practical.

## Direction

Current compatibility paths can remain for MVP, but target state is fully Nix-baked artifacts and cache-driven startup for all agent/tool derivations.
