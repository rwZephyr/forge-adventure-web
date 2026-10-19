# Forge Adventure Web — project brief

**Status:** Feasibility / prototype; not yet a working web port.

## Purpose
Explore a community-shareable way to play **the real MTG Forge Adventure** from a URL, without installing or sideloading Forge. The experience should be usable on iPhone in portrait orientation and retain Forge's actual gameplay rather than imitating it.

Source baseline: this repository is a fork of [Card-Forge/forge](https://github.com/Card-Forge/forge). Keep ongoing upstream card-set, rules, and Adventure improvements practical to merge.

## Priorities, in order
1. **Feasibility:** establish upstream build and identify browser incompatibilities.
2. **Authentic browser proof of concept:** render an actual Forge Adventure scene with genuine assets and input on mobile Safari.
3. **End-to-end single-player play:** overworld navigation, encounter, Forge AI match, return to Adventure, and rewards.
4. **Portrait usability:** adapt controls, menus, and battlefield for touch; not merely squeeze a landscape screen.
5. **Persistent saves:** reliably close and reopen a campaign, with export/import as appropriate.
6. **Optional self-hosting:** player logins and separate persistent campaigns accessible via ordinary browser URL.
7. **Stretch ideas:** independent concurrent players, social features, PvP, shared exploration, co-op. No shared loot or shared world required for an initial release.

## Technical approach to test, not assume
- Evaluate a libGDX browser target, with gdx-TeaVM a first candidate and GWT or a browser front end backed by Java as possible alternatives.
- Investigate real Forge dependencies, Java-to-web compatibility, asset loading, save handling, AI, and Safari constraints before making major architectural commitments.
- Prefer reuse of Forge Adventure logic and rendering where workable. Do not replace the Forge rules engine.
- Kasm/desktop streaming is a possible temporary workaround, **not** the intended community-distributed architecture.
- Browser-only execution of the entire rules engine is aspirational until proven. A Java backend is an acceptable fallback if it enables a genuinely native portrait browser interface.
- Following Stage B's verified JVM decisions and 93 unchanged-runtime TeaVM diagnostics, the [source-based architecture comparison](architecture/browser-runtime-comparison.md) recommends testing the existing JVM `IGuiGame` / `IGameController` boundary with a native browser interface. This is a conditional design recommendation, not approval or implementation; human-input transport, headless bootstrap and Adventure return/save boundaries remain unproven. Compatibility shims are paused pending architecture review.
- The [existing embedded test extension](architecture/embedded-protocol-test-scope.md) now has accepted [genuine human/AI Bolt evidence](experiments/embedded-bolt-results.md) in PR #19, following the unchanged Maven baseline in PR #18. It reuses test-only HeadlessGuiDesktop and establishes opposing-player targeting, manual payment, original loop resolution and threaded AI execution. It does not establish production headless hosting or general synchronization safety.
- The [Stage C interaction contract](architecture/browser-jvm-interaction-contract.md) has a [test-only DTO Bolt proof](experiments/stage-c-dto-results.md): three fresh successful original-game sequences and negative controls. Its observed failure-cleanup graph overlap prompted a separate test-only correction; [two authorized cleanup checks now pass](experiments/cleanup-verification-results.md), pending evidence review. This does not establish production capture safety. Browser transport, reconnect, network exposure and actual Safari connectivity still require subsequent review/approval.

## Environment
- The owner's Codex Desktop runs on **Silent**, a Windows 11 machine (Ryzen 7 3700X, RTX 3060 Ti, 16 GB RAM) with other existing home services. Avoid assuming spare resources or changing those services.
- Development may also use cloud coding agents. Do not require a particular vendor's assistant for routine maintenance.
- Do not make the service public or deploy to the home network without approval.

## Done means
A milestone counts as complete only when its behavior was demonstrated and documented, including exact build/test steps and caveats. Focus on the smallest measurable experiment first.
