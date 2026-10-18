# Project status

Last updated: 2026-10-17

## Current milestone
**Gate 0 verified; Gate 1 verified on desktop Chromium, pending actual iPhone
Safari. Gate 2 factory experiment designed only.** No complete Adventure web port
or browser rules/AI compatibility has been demonstrated.

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

## Current design assignment
- Branch `codex/gate2-factory-design`, based on master
  `db893dcdb14fdd85a05437ad458dc70ef704ca2e` to keep PR #8 separate.
- [Gate 2 design](experiments/gate-2-factory-design.md) records original classes,
  dependencies, nine factory cases, constructor metadata variants, concrete-class
  and parsed-value assertions, silent-fallback control, and pass/fail criteria.
- Source verified: TriggerType eagerly scans every entry; ReplacementType scans
  at factory invocation; Keyword silently falls back to SimpleKeyword. AI's
  nonempty spell list reaches FutureTask, Game AI Eval and timed get. TeaVM
  coroutine support makes execution necessary to resolve threading compatibility.
- Stage B is separate: nonempty decks/libraries, original Bolt/Mountain scripts,
  payable spell and lethal legal target, actual AiController path evidence and
  genuine cast/payment/resolution, with an empty-hand negative control.
- Documentation only. No Gate 2 implementation/build/test, dependency install,
  phone test, server/network exposure or Tailscale/service changes.

## Next bounded assignment
1. Agree on a private actual-iPhone Safari arrangement before enabling access.
   Do not modify Silent's Tailscale or existing services without specific approval.
2. Review/approve Stage A before implementation in its own target and branch.
   Record actual JVM/compiler/browser results, including failures.
3. Review Stage B after Stage A results; unchanged localization/bootstrap and
   threaded spell evaluation remain unproven prerequisites.
4. Keep PR #8 draft and unmerged. No whole-game port, custom rules, accounts,
   saves or unrelated services are part of this assignment.

## Handoff
- **Branch:** `codex/gate2-factory-design`; design only, separate from PR #8.
- **Files changed:** this status and `docs/experiments/gate-2-factory-design.md`.
- **Verification:** original sources/POMs and cached compiler source APIs inspected;
  original core/game/AI diff against baseline empty. No new runtime results claimed.
- **Next decision:** approve/revise Stage A; separately agree on private phone access.
