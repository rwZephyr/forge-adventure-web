# Project status

Last updated: 2026-10-17

## Current milestone
**Gate 0 verified; Gate 1 verified on desktop Chromium, pending actual iPhone
Safari. Gate 2 Stage A factory checks verified on desktop Chromium with explicit
constructor metadata.** No complete Adventure port or full rules/AI compatibility
has been demonstrated; Stage B remains unimplemented.

## Known setup
- Repository: `rwZephyr/forge-adventure-web`, fork of `Card-Forge/forge`.
- Upstream default branch: `master`.
- Target: native Forge Adventure in portrait-oriented mobile Safari first, with independent single-player campaigns as the initial game scope.
- Bounded browser toolchain: libGDX 1.14.2 / gdx-TeaVM 1.6.1 / TeaVM 0.15.0.
- Project owner develops primarily using Codex Desktop on Silent, and may switch between AI coding tools.

## Verified results
- [PR #8](https://github.com/rwZephyr/forge-adventure-web/pull/8), branch
  `codex/spritegroup-teavm`, head `57ed01d137e45ea2a304b64ef979402347cb0843`,
  remains draft and unmerged. Its implementation/evidence is on that branch,
  not included in this design branch.
- Approved portable Maven 3.9.16 / Gradle 9.6.1 and installed JDK 17.0.12:
  seven-module baseline compiled online/offline; 16 existing tests passed.
  These tests are not mechanics/AI coverage.
- Unchanged SpriteGroup and original treasure assets compiled for TeaVM.
  Desktop Chromium 155 visibly rendered sprites and demonstrated four order
  reversals during dragging, resize, DPR 1.5 input and reload reset. The initial
  blank loading canvas was fixed in the launcher; it is no longer outstanding.
- Temporary approved listener used only 127.0.0.1:8765 and was stopped.
  Physical touch and actual iPhone Safari remain unverified.
- [Actual results and evidence at the tested commit](https://github.com/rwZephyr/forge-adventure-web/blob/57ed01d137e45ea2a304b64ef979402347cb0843/experiments/adventure-teavm/RESULTS.md).

## Stage A implementation and actual results
- Branch `codex/gate2-factories`, based on approved design PR #9, separate from
  PR #8. Original Forge modules/POMs remain unchanged.
- [Results](../experiments/rules-teavm/RESULTS.md) and
  [reproduction](../experiments/rules-teavm/README.md): original offline core/game
  compile passed; JVM control passed 15 rows (nine factories plus enum/name cases).
- All three TeaVM variants compile with existing approved tools and isolated
  caches. In Chromium 155, explicit constructor metadata passes all 15 rows on
  three loads; missing-amount control fails only K2 with SimpleKeyword/amount 1
  on three loads; default fails with missing constructors and silent fallbacks.
- Six browser/JVM comparisons and seven comparator self-checks passed. Raw logs,
  actual JSON, screenshots, input/artifact hashes, locks and dependency/license
  inventories are committed with the experiment.
- Corrected design counts: 149 TriggerType entries, 137 distinct trigger classes;
  144 explicit constructor signatures, 143 negative. Initial generator missed
  whitespace on DayTimeChanges; actual JVM enum inventory now audits completeness.
- Initial broader predicate dispatch caused 47 severe TeaVM errors reaching
  logging/platform/JDK behavior. Scoped concrete predicate calls compile; full
  failures remain documented. No stub, source rewrite or broad workaround.
- Separate loopback server approval was received; only build/web was served at
  127.0.0.1:8765, and the test server was stopped. No listener remained. No LAN,
  Tailscale, existing-service, save/account or system dependency changes.
- Stage B (real Game/bootstrap/AI/threaded spell evaluation) is not implemented
  or verified. Actual iPhone Safari remains pending its private arrangement.

## Next bounded assignment
1. Agree on a private actual-iPhone Safari arrangement before enabling access.
   Do not modify Silent's Tailscale or existing services without specific approval.
2. Review Stage A's draft PR and its actual evidence; repeat the factory artifact
   on actual Safari alongside PR #8's SpriteGroup artifact when arranged.
3. Separately review/approve Stage B; localization/bootstrap, wider dependency
   reachability and threaded spell evaluation remain unproven. Do not start it yet.
4. Keep PR #8 draft and unmerged. No whole-game port, custom rules, accounts,
   saves or unrelated services are part of this assignment.

## Handoff
- **Branch:** `codex/gate2-factories`; draft Stage A implementation, no merges.
- **Files:** experiments/rules-teavm, scoped ignore rules, this status and corrected
  counts/constants in the approved design document.
- **Verification:** original source preservation, offline compile, JVM comparator,
  three TeaVM variants, actual Chromium results and deliberate fallback control.
- **Next decision:** review results and agree on private phone access; Stage B waits.
