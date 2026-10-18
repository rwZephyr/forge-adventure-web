# Project status

Last updated: 2026-10-17

## Current milestone
**Gate 0 verified; Gate 1 desktop component experiment verified.** The Forge mobile-development
reactor compiles and its existing tests pass on Silent. The original SpriteGroup
browser artifact renders, sorts during pointer dragging, and survives portrait/
landscape resize in desktop Chromium. Actual touch/iPhone Safari remain unverified.
This is not a functional Adventure port.

## Known setup
- Repository: `rwZephyr/forge-adventure-web`, fork of `Card-Forge/forge`.
- Upstream default branch: `master`.
- Target: native Forge Adventure in portrait-oriented mobile Safari first, with independent single-player campaigns as the initial game scope.
- Bounded browser target: libGDX 1.14.2 / gdx-TeaVM 1.6.1 / TeaVM 0.15.0. Only an original SpriteGroup component has compiled for the browser.
- Project owner develops primarily using Codex Desktop on Silent, and may switch between AI coding tools.

## Verified results
- Branch `codex/spritegroup-teavm`, source baseline `db893dcdb14fdd85a05437ad458dc70ef704ca2e`.
- Portable Maven 3.9.16 / Gradle 9.6.1 installed after approval, checksums verified,
  under ignored `.local-tools/`. Existing JDK 17.0.12 is selected per process.
  System defaults, existing services, user caches and save files are untouched.
- Seven-module mobile-development reactor compile succeeded online and offline,
  without POM/engine changes. Existing tests: 16 passed (3 game, 13 GUI), zero
  failures/errors/skips, using disposable test profile/temp directories.
- `Build-Browser.ps1 -WriteLocks`, then `-Offline`: Java compilation and TeaVM
  JavaScript generation succeeded. Fixture verification confirms original source
  selection, exact treasure bytes, and no other compiled Forge classes.
- After local-test approval, desktop Chromium 155 displayed the original sprites
  and verified four `BA`/`AB` order reversals, pointer coordinates, portrait/landscape
  resizing, high-DPI input and reload reset. An initial preload harness bug was
  fixed without changing Forge. Screenshots and diagnostics are in the experiment.
- The temporary server was bound only to `127.0.0.1:8765`, then stopped; no remaining
  listener. Observed readiness ~1.47 s and p95 frame interval ~18.5 ms on desktop;
  no mobile performance or full gameplay claim.
- Exact commands, warnings and limits: [results](../experiments/adventure-teavm/RESULTS.md)
  and [reproduction instructions](../experiments/adventure-teavm/README.md).

## Next bounded assignment
1. Arrange an approved actual portrait iPhone Safari test. Desktop Chromium is
   not device verification; LAN/public access is not currently approved.
2. Review the bounded experiment draft PR #8. Original rules/AI reachability remains
   a separate future gate; do not begin a whole-game port, accounts or saves.

## Handoff
- **Branch:** `codex/spritegroup-teavm`; bounded experiment draft PR.
- **Files changed:** `.gitignore`, this status, and `experiments/adventure-teavm/`.
- **What works (verified):** baseline compile/test and isolated TeaVM compilation;
  offline builds, original source/asset checks and desktop rendering/input/resize pass.
- **Commands and test results:** recorded in experiment `RESULTS.md` and evidence manifest.
- **Blockers:** real touch and actual iPhone Safari untested; no LAN/public serving approval.
- **Next bounded task:** separately approved phone test on this artifact.
  No additional game implementation or expansion of network access.
