# Muse — Campaign saves, player identity and self-hosting (research only)

## Mission
Determine the least invasive road from **one authentic single-player Forge Adventure save in a browser** to **separate persistent campaigns for friends behind a URL** and, eventually, a community-friendly self-hosted release.

## Read first
- Root `AGENTS.md`
- `docs/PROJECT.md`
- `docs/research/README.md`
- This assignment. Codex owns the actual browser compiler audit; Claude owns portrait UI.

## Product constraints
- Initially independent campaigns, **not** one shared world. No synchronizing enemy positions or shared treasure.
- Existing Forge character creation, card collection, decks, quests and overworld save semantics should survive.
- Save/reopen **in overworld** is required. Reliable long-term mid-battle save states are **not**.
- Phone browser through HTTPS, ideally players install nothing; later an account/login binds each player's save slots.
- Separate players must never overwrite or view each other's save files.
- Distributable for Forge community to self-host; no hard dependency on Kasm or a commercial hosted identity provider.

## Questions to answer
1. Inspect current Adventure save types, file paths, character creation/save slots, save/load triggers, compressed serialization, resource/version dependencies, and import/export possibilities. Cite exact classes/paths; distinguish facts from assumptions.
2. Compare MVP storage models: (a) browser-local storage plus reliable export/import/backups, and (b) optional server-managed saves tied to authenticated accounts. Discuss Safari storage eviction, capacity limits, interrupted writes, migrations, backups and recovery.
3. Recommend minimal multi-user architecture **after gameplay works**: save ownership, characters, sessions, server storage, optional login providers, and isolation. Avoid designing co-op or synchronous shared worlds.
4. Suggest a packaging/deployment plan that community members could reasonably operate (static assets plus optional small save service, or another justified model), including update/migration strategy and security boundaries.
5. Identify licensing/IP, public-hosting abuse, data isolation and service exposure risks without making policy guarantees. Include acceptance tests for save durability, multi-user isolation, restore, and upgrades.

## Deliverable
Write only `docs/research/results/muse-saves-hosting.md` on branch `research/muse-saves-hosting` (or unique branch). Include:
- Grounded map of existing save code and file formats
- A/B comparison of browser-local and server saves
- Recommended staged migration plan, with simple data-flow sketch
- Threats and risks / safeguards
- 5–10 acceptance tests and explicit uncertainties

**Restrictions:** no TeaVM/GWT toolchain investigation, no portrait UI design, no game-code modifications, no deployments/account registrations, and no edits to shared STATUS or other agent files. If an exact feature is unknown, state that instead of guessing. Open a draft PR for findings where possible.
