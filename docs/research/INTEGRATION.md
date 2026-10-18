# Research synthesis and next implementation gates

**2026-10-17.** This document integrates three **separate draft research PRs**, not a runnable/browser-compatible port.

- [PR #3: Portrait UX](https://github.com/rwZephyr/forge-adventure-web/pull/3) — source inventory of Adventure menus, portrait JSON, touch HUD and duel UI. No Safari test.
- [PR #4: Codex runtime and TeaVM](https://github.com/rwZephyr/forge-adventure-web/pull/4) — launcher/dependency graph, high-risk browser blockers and a bounded browser-sprite experiment. No complete build/browser artifact.
- [PR #5: Muse saves/hosting](https://github.com/rwZephyr/forge-adventure-web/pull/5) — existing compressed Java save structure and proposed storage adapters. Not browser-validated.

These reports remain on independent draft branches. Findings below are a **proposed integration decision**, not implementation or performance evidence. Review their source evidence directly before changing game code.

## Product contract

Minimum desired project: authentic **Forge Adventure**, ideally including Realm of Legends, running from a regular mobile Safari/desktop URL; portrait-first, touch-friendly, normal independent single-player campaigns that save in the overworld and resume later. Future community members can self-host without relying on Kasm. Separate user logins/cloud saves may follow, but co-op/shared worlds/PvP/trading are **not** prerequisites. No need to preserve active mid-battle state after a server restart. Continue accepting upstream sets, rules, AI and Adventure improvements. GPL-3.0 obligations and third-party asset rights must be reviewed before distribution.

## What the three reports jointly establish

- **UX reuse looks promising, subject to real device testing.** `Forge.isLandscapeMode` already selects portrait assets for many Adventure screens, `GameHUD` has touchpad input, `MatchScreen` contains portrait-specific battlefield layout, and `CardZoom` supports touch. Actual Safari safe-area scaling, controls, fonts, memory, responsiveness and complete match UX are untested.
- **Save reuse looks promising only *after* a capable runtime exists.** `WorldSave` compresses Java object streams with player/world/stage/POI data. Preserve slot semantics and payloads where possible; `SaveLoadScene`, settings and full-backup code also do direct file I/O. The existing serialization/reflection codec cannot be assumed to work in TeaVM, so IndexedDB is a *potential storage adapter*, not a sufficient solution by itself.
- **A direct whole-game browser port has major unresolved engine risks.** Native filesystem/resource discovery, reflection in real rules/AI factories, Java thread/blocking coordination, fonts, Box2D, native APIs, shader/asset packaging and the serialization codec. None were compiled for TeaVM; presence of compatible libGDX/TeaVM versions only removes one narrow version mismatch.
- **No baseline compilation succeeded.** On Silent, JDK 17 was available, Java 26 was default, `mvn` was missing, and a build command failed at shell lookup. Codex compiled two unchanged small source files against a locally cached older libGDX 1.13.5 jar. That does not establish the declared 1.14.2 build or any browser operation.

## Recommended implementation sequence (each is a separate task/PR)

**Gate 0: repeatable Forge baseline (in parallel with planning the web spike).**
- Inspect installed tools, obtain explicit approval before installing Maven/Gradle/dependencies, altering environment or running asset updater, or opening a port.
- Use process-local JDK 17 and Maven 3.8.1+ without changing machine defaults where possible.
- Build only the `forge-gui-mobile-dev` reactor closure using `-pl ... -am -T1`, record exact dependency failures and versions. Do not mistake environment failure for Forge compilation failure.
- **Pass:** repeatable complete baseline compile and relevant tests, or an explicitly recorded blocker that does not prevent the independent browser-slice experiment.

**Gate 1: smallest original-Forge browser slice (Codex's proposed `SpriteGroup` experiment).**
- Add a separate `experiments/adventure-teavm/` target; do not modify the original class or the Maven reactor.
- Run a portrait libGDX `Stage` using the **unchanged actual** Adventure `SpriteGroup.java` and a tiny manifest of genuine treasure sprite atlas assets.
- Drag actors to change overlapping Y/draw order; verify actual ordering, pointer coordinates, asset loading and resize. Use a pinned matching gdx-TeaVM/libGDX version set (Codex report proposes gdx-TeaVM 1.6.1/libGDX 1.14.2, toolchain requirements still to be verified).
- **Pass:** compiled/transpiled artifact, real source and assets, functional mouse/touch including actual iPhone portrait Safari, logs/time measurements. **Does not prove** the Adventure map, Forge rules or AI work.
- **Stop:** report exact browser compiler or runtime failure if blocked; no unrelated rewrites or blank mockups sold as a success.

**Gate 2: independently test hardest gameplay compatibility, not just graphics.**
- Codex designs a narrowly scoped fixture for actual Forge rule/AI paths, especially `TriggerType`, `ReplacementType`, `Keyword`, `SpellApiToAi`, and action/decision synchronization. Capture transitive reachability and compiler diagnostics.
- **Pass:** original nontrivial Forge factory/rule/AI behavior can execute correctly in chosen browser runtime and return decisions without freezing render/event flow. Avoid hand-coded replacements that make the test meaningless.
- **Stop/reassess:** if robust support demands extensive rule and AI rewrites, compare server-side JVM rules/AI plus a true native portrait web UI before investing in a full browser-only port.

**Gate 3: actual Adventure scene.**
- Preserve template/map loading (not a flat screenshot/stock TMX demo), stage objects, collisions and HUD; use a single small authentic POI as test fixture.
- Verify portrait navigation, map object interactions, and full transition back to world.

**Gate 4: complete Magic duel.**
- Actual Adventure encounter → Forge match/rules engine & AI → UI priority/targets/attacks → completion → rewards → overworld. This is the first gate demonstrating meaningful **game play** rather than isolated infrastructure.

**Gate 5: save codec, then storage.**
- Test real compressed `.sav` fixture round-trip and compatibility with desktop saves **before** choosing browser IndexedDB. Preserve current slot selection, per-plane namespace, explicit failure handling, backup/export/import. Test Safari durability/eviction/quota with real-sized saves. Only then add optional per-user accounts/cloud storage.

**Gate 6: distributable package and enhancements.**
- Portrait usability regressions, optional web service with isolated accounts, docs and reproducible community self-hosting; leave multiplayer as later design work.

## Practical decisions / risk management

1. Keep the experimental backend out of the standard Forge build until it passes basic gates; favour adapters and generated metadata over permanent changes to thousands of Forge classes.
2. **Visual success is not overall feasibility success.** Gate 1 is deliberately modest; Gate 2 and Gate 4 have more architectural value.
3. Early Go/No-Go decisions should be driven by actual compiler logs and device tests, not theoretical compatibility or the willingness of an AI agent to generate large patches.
4. Keep Silent safe: existing KoboldCPP/SillyTavern/RustDesk and saves should not be modified; set memory/worker limits and obtain explicit approval for environment installs, servers and local network access.
5. Each agent uses its own worktree/branch and logs exact commands, test results, failures and next handoff; keep all three research PRs open until reviewed and decide separately whether to merge each one.
6. Source code is GPL-3.0, but asset provenance and added web dependencies require separate checks prior to public packaging; do not promise legal clearance based solely on the root license.

## Proposed immediate Codex task

> Read AGENTS.md, docs/PROJECT.md and PR #4's report. We have completed the feasibility research. First, propose exact Maven/Gradle/JDK/toolchain setup for Silent without altering existing services; ask for explicit approval before installations/network servers. After approval, complete a bounded Forge baseline compile with JDK 17 and implement **only Gate 1**, the unchanged SpriteGroup + original treasure atlas browser experiment under experiments/adventure-teavm/. Provide a local build command, the generated web artifact, and verification logs. Do not claim Forge Adventure is playable. Do not implement accounts, saves, a new game UI, or the whole Forge engine. Open a draft PR. Distinguish compiler failure from asset/runtime/input issues.

This proposal requires user approval before Codex installs tools or starts a LAN server. The report is documentation-only; no environment changes or builds were performed to create it.
