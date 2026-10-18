# Claude — Portrait-first Adventure UI feasibility (research only)

## Mission
Map the real Forge Adventure / Realm of Legends player journey, then determine how its current **libGDX UI and interaction patterns** could support a portrait iPhone Safari experience without breaking Magic gameplay.

## Read first
- Root `AGENTS.md`
- `docs/PROJECT.md`
- `docs/research/README.md`
- This assignment. Other agents are researching compiler/toolchain compatibility and saves/hosting; **do not duplicate their audits**.

## Questions to answer
1. Inspect the current Forge Adventure screens, scene graph/widgets/controls and input mappings. Inventory character creation, overworld navigation and encounter triggering, shops, loot/rewards, collection, deck editor, equipment, settings, and returning from encounters to the map. Include Realm of Legends-specific differences only where verified.
2. Inspect Magic battle UI interactions: card selection/zoom and readability, mana/cost prompts, priority/stack, multiple targets, combat attack/block assignments, zones, turn phases, modal decisions, board crowding, and Commander multiplayer if relevant as future.
3. For a portrait viewport of approximately **390 × 844 CSS pixels**, distinguish interfaces that can be reused, adapted through libGDX layout changes, or likely redesigned. Consider small phones, safe areas, dynamic browser chrome, landscape optionality and touch targets.
4. Propose an iPhone portrait interaction model, preferably with a few small **text diagrams / annotated screen wireframes** and interaction sequences. Prioritize no feature loss, discoverability, cards readable on tap, and efficient phases/priority. Do not use generated screenshots pretending to be running Forge.
5. Rank UI obstacles. Provide minimal first-touch milestone and an actual iPhone manual usability test checklist.

## Deliverable
Write only `docs/research/results/claude-portrait-ux.md` on branch `research/claude-portrait-ux` (or unique branch). Include:
- Source-grounded screen/action matrix: current component/path, current input, proposed mobile behaviour, severity, evidence
- Portrait layout concepts and interaction decisions; clearly label hypotheses
- Reuse vs redesign recommendations
- Top risks and proof-of-concept test plan

**Restrictions:** no TeaVM compilation or generic browser-support research, no saving/auth/deployment architecture, no code changes, no edits to shared STATUS or other reports. Source observations must cite actual files/symbols or explicitly mark unverified. Open a draft PR for findings if available.
