# Shared AI contributor instructions

This repository is an experimental fork of [Card-Forge/forge](https://github.com/Card-Forge/forge) exploring **Forge Adventure Web**. These instructions apply to Codex and any other coding assistant working here.

## Read before working
1. Read `docs/PROJECT.md` for goals, scope, and constraints.
2. Read `docs/STATUS.md` for the last verified result and next milestone.
3. Inspect the actual source and build configuration before making technical claims.

## Project priorities
- Preserve genuine upstream Forge Adventure mechanics, content, card rules, AI, and progression.
- Target a **native browser experience**, prioritizing portrait iPhone Safari and touch input. Desktop browsers are also important.
- Start with **one user's independent single-player Adventure campaign** (including Realm of Legends if the current upstream build supports it).
- Persistent character saves, multi-user accounts, easy self-hosting, and multiplayer are later stages; do not implement them without a specific task.
- Investigate libGDX browser compatibility (including gdx-TeaVM); do not assume Forge itself already compiles to the web. Streaming/Kasm is not the primary architecture.

## Working rules
- Keep changes small, scoped, and reversible. Prefer a separate browser target or adapter over invasive changes to upstream modules.
- Do not modify or overwrite unrelated projects, local services, or user save files.
- Do not change deployment, network exposure, credentials, or hosting without explicit approval. Never commit secrets.
- Before substantial implementation, outline the proposed change, affected components, risks, and how it will be tested.
- Run relevant checks where feasible. Report exact commands and results; never claim a build, test, or iOS behavior was verified unless it actually was.
- Explicitly distinguish **verified**, **hypothesized**, and **blocked**. A blank browser canvas is not a functional Forge Adventure port.
- Preserve upstream copyright, licensing, and attribution. Assess dependency licenses before importing external code.
- Avoid adding unrelated features, broad refactors, or a custom game/rules engine.

## Handoff between AI tools
- Update `docs/STATUS.md` after each substantive task: what changed, actual verification, blockers, and the next bounded step.
- Record durable technical decisions in `docs/PROJECT.md` or a new focused document, not only in chat.
- Prefer a feature branch and reviewable commits. Do not push directly to `master` unless explicitly requested.
- When uncertain about a prior agent's claim, verify it from source rather than repeating it.
