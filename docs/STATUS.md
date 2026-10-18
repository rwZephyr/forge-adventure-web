# Project status

Last updated: 2026-10-17

## Current milestone
**Gate 0 verified; Gate 1 component experiment compiled.** The Forge mobile-development
reactor compiles and its existing tests pass on Silent. The original SpriteGroup
browser artifact is built; browser rendering/input and iPhone Safari remain pending.
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
- Exact commands, warnings and limits: [results](../experiments/adventure-teavm/RESULTS.md)
  and [reproduction instructions](../experiments/adventure-teavm/README.md).

## Next bounded assignment
1. After separate approval, temporarily serve only the built artifact on
   `127.0.0.1:8765`; verify visible sprites, actual draw-order flips, pointer
   coordinates, resize and diagnostics. Stop the listener after testing.
2. Arrange an approved actual portrait iPhone Safari test. Desktop emulation is
   not device verification; LAN/public access is not currently approved.
3. Review the bounded experiment draft PR. Original rules/AI reachability remains
   a separate future gate; do not begin a whole-game port, accounts or saves.

## Handoff
- **Branch:** `codex/spritegroup-teavm`; bounded experiment draft PR.
- **Files changed:** `.gitignore`, this status, and `experiments/adventure-teavm/`.
- **What works (verified):** baseline compile/test and isolated TeaVM compilation;
  offline builds and original source/asset checks pass.
- **Commands and test results:** recorded in experiment `RESULTS.md` and evidence manifest.
- **Blockers:** separate loopback-server approval pending; actual iPhone Safari untested.
- **Next bounded task:** browser rendering/input/resize checks on this artifact,
  followed by a separately approved phone test. No additional game implementation.
