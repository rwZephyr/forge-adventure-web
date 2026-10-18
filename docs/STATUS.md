# Project status

Last consolidated: **2026-10-18**. This is a coordination snapshot published on a separate draft branch; it is not an assertion that experiment PRs are merged into `master`.

## Current milestone
**Gate 0 confirmed; Gate 1 bounded actual iPhone Safari probe passed; Gate 2 Stage A selected Forge factories passed with explicit TeaVM metadata; Gate 2 Stage B genuine rules/AI is not yet tested.** The goal remains a genuine playable Forge Adventure browser port; no entire Adventure scene, AI duel or campaign save has run in a browser.

## Results by gate

| Gate | Recorded evidence | Limits |
| --- | --- | --- |
| Gate 0 — baseline | [PR #8](https://github.com/rwZephyr/forge-adventure-web/pull/8): JDK 17 seven-module Forge mobile-dev baseline compiled online/offline, 16 pre-existing tests passed. Portable Maven 3.9.16, Gradle 9.6.1. | Small existing suite, not rules/AI gameplay coverage. |
| Gate 1 — original rendering | [PR #8](https://github.com/rwZephyr/forge-adventure-web/pull/8) transpiles unchanged Adventure `SpriteGroup` with genuine treasure atlas/PNG, visibly runs and drags/sorts in desktop Chromium. [PR #12](https://github.com/rwZephyr/forge-adventure-web/pull/12) records actual iPhone Safari rendering, dragging, rotation and reset, including successful private-tailnet retrieval. | Harness uses a fixed 320×480 viewport with text/layout limitations; not an Adventure scene. Approved one-off test server was stopped; no standing network permission. |
| Gate 2 Stage A — authentic factories | [PR #11](https://github.com/rwZephyr/forge-adventure-web/pull/11): JVM 15/15; TeaVM with explicit **constructor-only reflection metadata** 15/15 in Chromium across three loads. No metadata: 5/15; deliberate missing constructor: 14/15, exact expected silent fallback. PR #12 reports actual Safari 15/15, 14/15, 5/15 respectively (one full exported JSON per variant plus two owner-reported repeats). | Original selected `TriggerType`, `ReplacementType`, `Keyword` pathways work in those cases; no complete factory/ability dispatcher, Game/AI or bootstrap proven. Wider reachability had 47 severe compiler errors in an initial attempt. |
| Gate 2 Stage B — authentic spell AI | [PR #13](https://github.com/rwZephyr/forge-adventure-web/pull/13) proposes a genuine Lightning Bolt/Mountain AI decision with real Thread/FutureTask/timed-get, legal target, R mana payment, stack/cast/damage and an empty-hand negative. Owner has approved **only** the JVM comparator, metadata/dependencies and unchanged-runtime TeaVM *compile attempt*. | No Stage B implementation or result in the last reviewed GitHub PRs. Cached TeaVM 0.15.0 classlib lacks `FutureTask`/some concurrency support, and `ApiType` widens reflection; these are predicted blockers, not yet observed Stage B compile failures. |
| Gate 3 preparation — map assets | [PR #10](https://github.com/rwZephyr/forge-adventure-web/pull/10) adds Muse's stdlib TMX dependency scanner, 18 passing unit tests and `cave_treasure` sample: 9 files / 1,793,286 bytes, including separately probed sprite assets. | Does not render a map or resolve all runtime-defined assets. One out-of-root `--plane`/`--common` asset-read edge case remains flagged for a follow-up. |

## Decisions and next steps
1. Codex executes only the previously approved offline Stage B baseline. On first compiler/bootstrap/runtime boundary requiring changed Forge behavior or adapters, **stop and report**, not silently patch.
2. Claude (fresh account) can independently review Stage B's concurrency/TeaVM runtime feasibility, focusing on predicted vs demonstrated blockers, narrow compatibility strategies, and preserving real AI semantics. Read-only unless user authorizes code edits.
3. Muse addresses the flagged scanner root-containment edge case and can review real Stage B diagnostics when they exist.
4. The owner reviews all draft PRs and authorizes merges and any browser/network tests separately. `master` remains behind; see `docs/COORDINATION.md` for the PR stacking arrangement.

## Handoff requirements
State exact PR/branch/commit, changed files, actual commands/results and independent reproduction status, code paths, known limitations, whether any approval is needed, and the next bounded experiment. Do not mark a full browser port complete from isolated component tests.
