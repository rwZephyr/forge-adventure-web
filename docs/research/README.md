# Independent research tracks (October 2026)

Research assignments for **Forge Adventure Web**, a community-shareable native browser port of upstream MTG Forge Adventure.

## Context carried over from project discussions

- **Minimum viable product (MVP):** open an HTTPS URL on iPhone Safari or a desktop browser, play a **real** Forge Adventure/Realm of Legends campaign, save in the overworld, return later.
- Portrait iPhone touch UI is a core design priority, not polish to add at the end.
- Prefer reusing upstream Forge gameplay, AI, content, cards, and libGDX interface where feasible; do not rewrite the Magic rules engine.
- **Multiple people do not need a shared world.** Initially, each player has a separate single-player campaign and save. Individual login/cloud save comes **after gameplay works**.
- Shared loot, real-time multiplayer, co-op, and PvP are optional future work. They must not drive the first architecture.
- Short in-memory battle reconnection would be nice; durable saves **mid-battle** are unnecessary. Preserve the existing overworld-save behaviour if viable.
- A game-rendering/browser-streaming system such as Kasm may be a temporary test, but **is not** the desired distributable client architecture.
- Eventually the fork should be easy for other Forge enthusiasts to self-host and maintain alongside upstream Forge updates.
- The owner's Codex Desktop runs on **Silent** (Windows 11, Ryzen 7 3700X, RTX 3060 Ti, 16 GB RAM) alongside existing services. Do not modify system/service configuration.
- No compiler path, WebAssembly strategy, asset packaging approach, UI port, or save compatibility is yet proven.

## Responsibilities and non-overlap

| Agent | Assignment | Focus | Assigned output |
|---|---|---|---|
| Codex | [TASK-CODEX.md](TASK-CODEX.md) | Actual source/runtime dependencies, baseline build, TeaVM/GWT feasibility | `docs/research/results/codex-runtime.md` |
| Claude | [TASK-CLAUDE.md](TASK-CLAUDE.md) | Portrait-touch Adventure UX inventory and migration plan | `docs/research/results/claude-portrait-ux.md` |
| Muse | [TASK-MUSE.md](TASK-MUSE.md) | Save persistence, accounts, and community self-hosting architecture | `docs/research/results/muse-saves-hosting.md` |

Each agent owns one report and **should not edit** `master`, `docs/STATUS.md`, existing `docs/PROJECT.md`, or another agent's report during this exploratory phase.

## Collaboration procedure
1. Read root `AGENTS.md`, `docs/PROJECT.md`, and your own assignment.
2. Use an **independent branch/worktree** for each agent (or separate cloud checkout). Avoid multiple agents editing one local working tree simultaneously.
3. Perform source-grounded research. No system installs, game-code changes, network exposure, deployments, or broad refactors.
4. Put findings in your assigned report with paths/symbols and exact verification results. If a command could not be run, explicitly say so.
5. Open a separate draft PR from your research branch to the fork's `master` (or provide the report text if GitHub write access is unavailable). Don't merge the agent PR automatically.
6. The human owner will review cross-track findings, choose architecture, and update the central `docs/STATUS.md` in a follow-up integration task.

Notes: GitHub Issues are currently disabled on this repository, so assignments are tracked here instead. Files are usable by Codex, Claude, Muse, or a replacement agent. Neither an AI model nor a chat has automatic access to other agents' private conversations; the versioned repo files are the handoff.
