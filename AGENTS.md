# Shared AI contributor instructions

This repository is an experimental fork of [Card-Forge/forge](https://github.com/Card-Forge/forge) exploring **Forge Adventure Web**. These instructions apply to Codex, Claude, Muse, and any other coding assistant.

## Before work
1. Read `docs/PROJECT.md` for the stable goals.
2. Read `docs/STATUS.md` and `docs/COORDINATION.md` for the latest consolidated handoff.
3. **Inspect the active draft PRs and their own evidence before making claims.** `master` does not yet contain most experiments, and all documentation can lag a working branch. Identify the exact head and base commit when reporting.
4. If starting from a fresh chat/account, assume you have no shared conversation memory. Use the supplied project brief; do not assume access to another AI agent's local files, tests, or approvals.

## Project priorities
- Preserve **genuine upstream Forge Adventure gameplay, Java card rules, AI, progression, maps, and assets** rather than building a mock game engine.
- Goal: independent single-player campaigns in a normal browser URL, especially touch-friendly portrait iPhone Safari; desktop browser support also matters.
- Streaming/remote desktop is not the intended delivery architecture. Evaluate libGDX/TeaVM, but do not assume full Forge compatibility. A browser UI backed by an authentic Java engine remains an alternative if the all-browser path fails.
- Multiplayer, account systems, persistent saves, self-hosting, visual polish and broad Adventure content come after a genuine rules/AI feasibility result. Do not expand scope without approval.

## Truthfulness and evidence
- Distinguish **source inspection**, **recorded author test**, **independent reproduction**, **predicted blocker**, and **observed failure**.
- A compiler success is not a browser pass; a SpriteGroup or factory pass is not a playable Adventure scene or full rules engine.
- Preserve raw logs, representative JSON and hashes where feasible. Report exact commands, input commits, browser/device, results and remaining limitations.
- Keep JVM comparison fixtures and deliberate negative controls; do not call a reduced/mocked call graph a successful original-engine test.

## Working rules and approvals
- Each assignment gets a small **separate branch and draft PR** with an exact task scope, success/failure criteria and reproduction notes. Never commit to `master`, merge PRs, or alter another agent's branch without express owner approval.
- Do not touch Silent's existing home services, Tailscale Serve, port 443, firewalls, network exposure, authentication, user saves or system settings. Even a loopback/private static server requires separate, session-specific owner approval; previous approvals are not standing permission.
- No installs, downloads, changed Maven/Gradle global settings, new service listeners, or replacing substantial Forge functionality without task-specific approval. Prefer approved portable JDK17/Maven/Gradle, offline caches and bounded one-worker builds.
- Do not commit secrets, sensitive logs or unrelated screenshots. Preserve licensing notices; GPL source rights do not automatically settle card art and third-party content rights.
- Investigate actual original code and dependencies; propose **minimal source-preserving boundaries** before edits. No blanket stubs, rules/AI substitutes, trimmed enums or deceptive success conditions.
- If a task uncovers a new architectural blocker, document it and stop for review rather than quietly expanding into a full port.

## Agent coordination
- Codex: lead implementation. [PR #15](https://github.com/rwZephyr/forge-adventure-web/pull/15) completed the approved Stage B unchanged-runtime baseline: genuine Forge AI succeeds on JVM; pinned TeaVM compile fails with 93 severe diagnostics. **No next architecture implementation or compatibility adapter is approved.**
- Muse/Marvin: Adventure asset scanner [draft PR #10](https://github.com/rwZephyr/forge-adventure-web/pull/10), plus independent bounded utility/review work. Avoid duplicating Codex's runtime implementation.
- Claude: a *fresh account* may be assigned independent technical review. Read the provided onboarding task and cited source paths before starting; default is read-only until explicitly authorized to edit.
- ChatGPT: coordination, evidence review and draft PR integration; owner decides approvals, network access and merges.
- See `docs/COORDINATION.md` for the open PR dependency graph, assigned work, and handoff expectations. Update the status in your branch after substantive work but do not modify another agent's branch.
