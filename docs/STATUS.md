# Project status

Last consolidated: **2026-10-18**. This is a coordination snapshot published on a separate draft branch; it is not an assertion that experiment PRs are merged into `master`.

## Current milestone
**Gate 0 confirmed; Gate 1 actual iPhone Safari probe passed; Gate 2 Stage A selected factories passed; Gate 2 Stage B genuine original Forge AI passed on JVM but unchanged TeaVM failed compilation with 93 severe diagnostics.** No real duel, Adventure scene, or campaign save has run in a browser. The next decision is an architecture review: broad Java-to-browser compatibility investment versus a browser-native client with an authoritative original JVM rules/AI backend.

## Results by gate

| Gate | Recorded evidence | Limits |
| --- | --- | --- |
| Gate 0 — baseline | [PR #8](https://github.com/rwZephyr/forge-adventure-web/pull/8): JDK 17 seven-module Forge mobile-dev baseline compiled online/offline, 16 pre-existing tests passed. Portable Maven 3.9.16, Gradle 9.6.1. | Small existing suite, not rules/AI gameplay coverage. |
| Gate 1 — original rendering | [PR #8](https://github.com/rwZephyr/forge-adventure-web/pull/8) transpiles unchanged Adventure `SpriteGroup` with genuine treasure atlas/PNG, visibly runs and drags/sorts in desktop Chromium. [PR #12](https://github.com/rwZephyr/forge-adventure-web/pull/12) records actual iPhone Safari rendering, dragging, rotation and reset, including successful private-tailnet retrieval. | Harness uses a fixed 320×480 viewport with text/layout limitations; not an Adventure scene. Approved one-off test server was stopped; no standing network permission. |
| Gate 2 Stage A — authentic factories | [PR #11](https://github.com/rwZephyr/forge-adventure-web/pull/11): JVM 15/15; TeaVM with explicit **constructor-only reflection metadata** 15/15 in Chromium across three loads. No metadata: 5/15; deliberate missing constructor: 14/15, exact expected silent fallback. PR #12 reports actual Safari 15/15, 14/15, 5/15 respectively (one full exported JSON per variant plus two owner-reported repeats). | Original selected `TriggerType`, `ReplacementType`, `Keyword` pathways work in those cases; no complete factory/ability dispatcher, Game/AI or bootstrap proven. Wider reachability had 47 severe compiler errors in an initial attempt. |
| Gate 2 Stage B — authentic spell AI | [PR #15](https://github.com/rwZephyr/forge-adventure-web/pull/15): original JVM AI three fresh Lightning Bolt positives passed targeting, mana payment, stack/resolution and lethal state; empty-hand negative returned null/no damage but still ran the real threaded mana evaluator. Shared-memory JDI traced original `FutureTask` → `Game AI Eval` → timed-get; 491 constructor signatures audited and 346 retained for this fixture. | One unchanged TeaVM compile failed with **93 severe diagnostics** and no runnable browser artifact. Verified compiler blockers include resource loading, `FutureTask`, `Thread.stop`, `ThreadUtil` executors, logging/event locks, reflection and dependencies. No adaptations attempted. |
| Gate 3 preparation — map assets | [PR #10](https://github.com/rwZephyr/forge-adventure-web/pull/10) adds Muse's stdlib TMX dependency scanner, 18 passing unit tests and `cave_treasure` sample: 9 files / 1,793,286 bytes, including separately probed sprite assets. | Does not render a map or resolve all runtime-defined assets. v1.1.1 fixed out-of-root `--plane`/`--common` probe directories; 21/21 independent unit tests passed. Full cave asset scan was reported by Muse, not independently rerun here. |

## Decisions and next steps
1. **Stop for architecture review.** No new TeaVM JCL adaptation, game/rules rewrite, compatibility shim, browser listener or JVM backend implementation is yet authorized. PR #15 supplies the demonstrated blockers.
2. [Claude's independent concurrency review (#16)](https://github.com/rwZephyr/forge-adventure-web/pull/16) predicted a potential focused `FutureTask`/Thread shim before PR #15; several predictions are now confirmed compilation failures, but actual browser task semantics remain untested.
3. Muse's independent read-only PR #15 review counts 40 unique missing API headlines/~19 groups and argues the remaining statically reachable gaps are too broad for a small class-library shim. See its PR #15 conversation comment; this is analysis, not a second compile.
4. Consider an **authentic JVM backend + browser-native interface** design alongside the browser-only alternative. No implementation has been approved. The owner reviews all draft PRs, chooses architecture and authorizes merges/network tests separately.

## Handoff requirements
State exact PR/branch/commit, changed files, actual commands/results and independent reproduction status, code paths, known limitations, whether any approval is needed, and the next bounded experiment. Do not mark a full browser port complete from isolated component tests.
