# Project status

Last updated: 2026-10-18

## Current milestone
**Gate 0 verified. Gate 1's bounded SpriteGroup checks pass on desktop Chromium
and in the owner-guided actual iPhone Safari session. Gate 2 Stage A's expected
factory outcomes are reproduced on Safari: explicit 15/15, missing-amount 14/15
with only K2 falling back, default 5/15.** No complete Adventure port or full
rules/AI compatibility is demonstrated. Stage B remains unimplemented.

## Known setup
- Repository: `rwZephyr/forge-adventure-web`, fork of `Card-Forge/forge`; upstream
  default branch `master`.
- Target: native portrait iPhone Safari, initially independent single-player
  Adventure campaigns; no accounts, saves or multiplayer implemented.
- Pinned browser tools: libGDX 1.14.2 / gdx-TeaVM 1.6.1 / TeaVM 0.15.0.
- Approved portable Maven 3.9.16 / Gradle 9.6.1 and existing JDK 17.0.12;
  isolated repository-local caches. No system dependencies/settings changed.

## Baseline and SpriteGroup
- Seven-module baseline compiled online/offline; 16 existing tests passed.
  These are not comprehensive mechanics/AI tests.
- [PR #8](https://github.com/rwZephyr/forge-adventure-web/pull/8), branch
  `codex/spritegroup-teavm`, head `57ed01d137e45ea2a304b64ef979402347cb0843`,
  remains draft and unmerged. Original SpriteGroup and treasure assets compile
  unchanged. Implementation is on that separate branch.
- Desktop Chromium 155 verified visible sprites, four BA/AB draw-order reversals,
  pointer input, resize/DPR and reset after the bounded launcher preload fix.
- Actual iPhone 14: owner-operated Safari, screenshots and feedback verify both
  treasures, matching BA/AB draw-child order, dragging/release, rotation/input
  and exact reset. Fixed 320x480 world/drag bounds and overlapping text remain
  layout limitations. Subjective response feels good; no phone benchmark.
- Device version initially reported as 26.6.1. Pasted page UA identifies Safari
  Version/26.6.1 and OS compatibility token 18_7; Settings OS not independently
  established. Do not infer support for other iOS versions.

## Stage A factory results
- [PR #11](https://github.com/rwZephyr/forge-adventure-web/pull/11), branch
  `codex/gate2-factories`, head `226bb0fb6109a2a5f31b5dd931e8e1db3ac379c5`,
  remains draft and unmerged. Based on approved design PR #9; Forge classes and
  module POMs unchanged. [Original results](../experiments/rules-teavm/RESULTS.md).
- Original offline core/game compile and JVM 15-row control pass. All three
  TeaVM variants compile. Chromium explicit passes 15/15; missing-amount gives
  sole K2 SimpleKeyword/BUSHIDO/amount 1 at 14/15; default fails 5/15.
- Actual phone: one complete user-pasted JSON export per variant. Existing JVM
  comparator passes explicit and expected negative control. Default is rejected
  against JVM as expected; all semantic case fields match Chromium default.
  Safari TypeError wording differs for T1/T2/invalid.trigger and is preserved.
- Each variant has two additional owner-reported repeat results at the same
  count. These are summary observations, not six additional exported JVM
  comparisons. Explicit repeats have no console errors reported; negative repeats
  confirm empty capture; default's full export has empty capture.
- Owner used private browsing for suspected caching; no cache diagnosis or
  header/code workaround was introduced.
- Metadata audit: 149 TriggerType entries / 137 distinct classes, 144 constructor
  signatures (143 negative). Initial manifest omission and 47 broader-reachability
  compiler errors remain documented; no stub or engine rewrite was introduced.

## Completed private Safari session
- [Results, evidence and reproduction](experiments/safari-session-results.md),
  separate local branch `codex/safari-test-plan`, based on PR #11.
- Preflight verified six SpriteGroup and 526 factory recorded hashes and all
  924 staged copies; no links or extra files. No rebuild or new dependency.
- One explicitly approved temporary HTTP listener bound only to Silent's exact
  Tailscale IPv4 / TCP 8765 served the demo copies. Existing Serve, firewall,
  routes, DNS, services and credentials were not changed.
- Fresh unique-query retrieval succeeded over cellular with Tailscale on.
  With phone Tailscale off, SpriteGroup and representative explicit factory URL
  did not retrieve; zero fresh-marker HTTP requests reached the running server.
  Safari timeout/Private Relay warning documented without bypassing protections;
  default/missing-amount off-mode URLs were not separately tested.
- **Stopped and verified:** zero TCP 8765 listeners, original demo process gone,
  saved before/after Serve JSON byte-identical. Both PRs rechecked draft/unmerged.
  No test listener remains. Raw network snapshots/logs and screenshots stay
  private; sanitized JSON/comparison evidence is recorded in the results folder.
- HTTP over Tailscale does not establish HTTPS secure-context/PWA/storage support
  or a working full Adventure port.

## Next bounded step and handoff
- Review the completed actual-phone evidence; keep PRs #8 and #11 draft/unmerged.
- Separately review/approve Stage B's genuine Game/bootstrap/AI experiment and a
  fixture that exercises AiController's threaded spell evaluation. Do not
  implement it until approved; an empty-deck turn-advancing game is insufficient.
- No additional server, network or deployment authorization persists from this
  completed single session. Whole-game porting, accounts, saves and unrelated
  services remain outside scope.
- Current branch contains the access plan, session report, sanitized evidence and
  this status. No Forge class, experiment runtime, POM or original asset changed.
