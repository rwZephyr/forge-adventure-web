# Project status

Last updated: 2026-10-18

## Stage C design for review
- PR #20 is conditionally accepted under coordinator C1–C6, with DTO implementation
  **not authorized**. [C1 identity audit](architecture/bolt-ability-identity-audit.md)
  confirms three singleton offered views and original caller paths from existing
  #19 evidence. Per-call view object IDs/cache pairs were not recorded; matching
  ability IDs and downstream JDI objects cannot close the cross-call identity gate.
- Revised design names atomic immutable authorization publication, EDT-confined
  live handle resolution and a single recheck/dispatch runnable, original mana
  provenance, and typed canonical payload/`actionIdConflict` handling. These are
  requirements only. Next: review the audit gap and narrowly proposed passive
  observation; no instrumentation or run has been performed for this follow-up.
- Owner accepts PR #19's authentic human/AI JVM Bolt milestone following
  independent adversarial review. Its source/evidence remains unchanged and draft.
- [Browser/JVM interaction contract](architecture/browser-jvm-interaction-contract.md)
  proposes a viewer-specific immutable projection, prompt-scoped action choices,
  EDT revalidation, duplicate/stale-action handling and a DTO-only scripted peer
  within the existing embedded test. No raw Java graph crosses the proposed boundary.
- Blocking questions: coherent capture across original graph mutators and exact
  authorization of synchronous ability questions (including EDT callers). Passing
  #19 observations do not establish general synchronization safety.
- Documentation/source inspection only; no new test/build execution, dependencies,
  runtime changes, reconnect, browser transport or listeners. Next bounded step:
  review this design before approving any DTO proof implementation. Existing PRs
  remain unmerged.

## Current milestone
**Gate 0 verified. Gate 1's bounded SpriteGroup checks pass on desktop Chromium
and in the owner-guided actual iPhone Safari session. Gate 2 Stage A's expected
factory outcomes are reproduced on Safari: explicit 15/15, missing-amount 14/15
with only K2 falling back, default 5/15.** No complete Adventure port or full
rules/AI compatibility is demonstrated. **Stage B's approved first slice now
passes the genuine JVM fixture and path witnesses; unchanged-runtime TeaVM
compilation fails with 93 severe problems. Browser AI execution is blocked.**

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
  published as documentation/evidence-only draft
  [PR #12](https://github.com/rwZephyr/forge-adventure-web/pull/12), branch
  `codex/safari-test-plan`, targeting PR #11's branch so runtime changes are not
  duplicated in its diff. PRs #8/#11 remain unchanged, draft and unmerged.
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

## Stage B first implementation slice (2026-10-18)
- Separate branch `codex/gate2-ai-baseline`, based on reviewed draft PR #13;
  [actual results](../experiments/ai-teavm/RESULTS.md) and
  [exact reproduction](../experiments/ai-teavm/README.md).
- Original offline core/game/AI Maven build passes (four modules, 18.165 s,
  zero Checkstyle violations). Three fresh JVM positive processes pass genuine
  Bolt choice/opponent targeting, real R payment from Mountain, stack casting,
  three damage, graveyard movement and game-end state; empty-hand control passes.
- Installed JDK shared-memory JDI (`dt_shmem`, no debug TCP listener) witnesses
  nonempty original candidates, FutureTask, distinct Game AI Eval thread,
  canPlayAndPayFor for Bolt, and timed-get NORMAL result with the same ability.
  Separate positive/negative witnessed fixtures also pass without Forge edits.
- Corrections to scope assumptions: original TypeLists bootstrap is needed for
  Mountain's mana ability; original SecureRandom cold entropy takes about eight
  seconds and is initialized before the unchanged two-second AI deadline.
  No seed/provider/timeout rewrite. Empty hand retains two original mana
  candidates and runs the task; it returns null without payment/damage. The
  earlier zero-task prediction was disproved, not forced by filtering the engine.
- Audited 491 public constructors, 203 ApiType entries / 200 effect classes,
  193 AI mappings / 147 classes. Fixture compiler policy: 346 signatures;
  dependency inventory: 63 configuration entries / 58 unique coordinates,
  all cached-POM license declarations resolved. 2,610 original inputs rechecked.
- One unchanged-runtime TeaVM attempt fails (exit 1, 93 severe diagnostics).
  First reported chain: File.toPath → FileUtil.readAllLines/readFile → genuine
  type bootstrap → fixture → browser entry. Independent failures confirm missing
  FutureTask (construction/get/cancel), Thread.stop, URLClassLoader, logging locks
  and Guava event/cache platform APIs. Full logs/chains are committed with results.
- **Stopped at compiler failure.** No runnable browser artifact, browser test,
  compatibility adapter, thread/localization replacement or dependency stub.
  Original Forge sources/POMs and successful experiments remain unchanged; no
  server, additional installation or network/service/save changes.

## Architecture comparison — documentation only
- [Source-based comparison](architecture/browser-runtime-comparison.md) traces
  original encounter assembly, duel state, human input/targeting/payment, AI
  turns, return/rewards and campaign saves against pinned original sources.
- Conditional recommendation: native browser interface with authoritative JVM
  Forge, using the existing IGuiGame / IGameController boundary and an additive
  ProtocolGuiGame / IRemote browser bridge. No bridge has been implemented.
- First proposed proof: genuine human Bolt selection/target/payment/resolution
  and a fresh original AI Bolt scenario through the real match/phase loop, with
  path witnesses, browser projections, negative/reconnect/stale-action checks.
- Headless bootstrap and Adventure scene/reward/save separation are distinct
  review gates. Original campaign code still depends on libGDX actors, timers,
  global singletons and Pixmap-backed persistence; no ready headless service is
  claimed. Browser-only compatibility work is paused for architecture review.
- Actual checks this task: source/evidence inspection, source-reference and
  documentation-link checks, git diff whitespace and documentation-only scope;
  all five inspected original module trees match the pinned baseline. No new
  build/runtime/browser test, source adaptation, listener, install or network
  change. Independent reviews were read as assessments, not new executions.

## Next bounded step and handoff
- **Approved authentic embedded Bolt test extension implemented on JVM.**
  [Results and reproduction](experiments/embedded-bolt-results.md), separate
  branch `codex/embedded-bolt-tests`, stacked on draft PR #18. Only the existing
  embedded test and JdiWitness source files are extended; historical embedded
  method/SingleThreadTest and original runtime/cards/POMs remain unchanged.
  Human opposing-player Bolt 20→17 and AI lethal Bolt 3→0 each pass three fresh
  unpaused JVM runs with genuine targeting, Mountain payment, stack and loop
  resolution. Empty-hand and atomic unanswered-prompt controls pass; the TestNG
  owner verifies receipt of the latter's exact recorded failure artifact.
  Both unchanged regressions pass. Final dt_shmem-only witnesses pass human/AI
  paths and empty-hand NORMAL null task; actual caller/EDT/evaluator IDs and
  future/ability/card identity chains retained. No observed graph-operation
  overlap or inconsistent checkpoint; no general synchronization guarantee.
  Test-only hidden-face validation is not a browser payload projector. Creature
  targeting/destruction, reconnect and production headless operation remain
  unproven. Source/config/cache audit and complete sanitized evidence retained;
  incidental CLI zero-test report output was detected and individually cleaned.
  No new dependencies, server, network/service change or original runtime fix.
  Stop for review after the new draft PR; existing PRs remain unmerged.
- Baseline evidence prepared for a separate documentation-only draft PR on
  `codex/maven-baseline-results`, based directly on PR #17's unchanged head.
  Owner accepts the pass as author-reported pending evidence review. Complete
  logs retain all lines with documented workspace/JDK path substitutions;
  credential/private-data review found no secrets. Public evidence hashes are
  recorded; downloaded artifacts, build outputs and temporary profiles excluded.
  Publication uses existing evidence only: no reruns, downloads, environment
  changes or Bolt implementation. Return to standby after publication; PR #17
  stays unchanged, draft and unmerged.
- **Approved Maven acquisition and unchanged embedded baseline now pass.**
  [Actual results/evidence](research/results/maven-baseline-results.md), on the
  separate `codex/maven-baseline-inventory` branch. Original GMaven ran on JDK
  17; normal desktop online test-compile succeeds. The original offline
  ProtocolGuiGameInProcessTest executes: 1 test, 0 failures/errors/skips.
  One human land, actual priority/turn progression and concession; no Bolt work.
- Acquired 37 JARs/54 POMs (237 new cache files), all new resolver origins
  central; one existing jsr305 resolver index updated, no artifact replacement.
  Offline cache unchanged. All 58,903 tracked hashes and inspected files outside
  generated outputs unchanged; reviewed plan/settings/source/POMs preserved.
  Full logs, actual artifact/license inventory and integrity/test results saved.
  No installations, servers, port/service changes or unrelated work modified.
- Historical stop after baseline success: PR #17 remains draft/unmerged.
  The two-file test extension was subsequently approved and is recorded above;
  transport and additional runtime/campaign work remain unapproved.
- Separate read-only [Maven blocker investigation](research/results/maven-baseline-blocker.md)
  on `codex/maven-baseline-inventory`; PR #17's implementation plan is unchanged.
  GMaven is bound to initialize and its execute goal requests test dependency
  resolution. Seventeen expected plugin JAR coordinates are absent from the
  local Maven cache; nine direct desktop/test POM/JAR pairs are also absent.
  Candidate transitive closure and cached build/provider markers are recorded.
  That proposal's cache-miss inventory is historical; approved acquisition and
  actual closure/results are now recorded above. No plugin/POM bypass or
  extension implementation occurred.
- The [embedded test extension plan](architecture/embedded-protocol-test-scope.md)
  now narrows the immediate proposal to the existing
  `ProtocolGuiGameInProcessTest`. No second gameplay harness or implementation.
  Test-only HeadlessGuiDesktop and the single-thread sibling test are documented.
- One unchanged-source offline baseline attempt on Silent: five preceding
  modules pass, desktop stops resolving uncached
  `org.codehaus.gmaven:groovy-maven-plugin:2.1.1`; exit 1, 33.786 seconds.
  Selected test never compiled/ran. Full log/hash retained with the plan. No
  dependency fetch, install, POM workaround or online retry was attempted.
- Proposed additions: human target/manual-mana/cast/resolution and original AI
  phase-loop/FutureTask witnesses, input/action thread identities, scoped
  visibility assertions and unanswered-prompt negative control. Immediate
  synchronous replies stay on their calling thread; controller clicks use EDT.
  Raw Java views are private and are not a browser-safe payload.
- Reconnect handling is excluded. ReplyPool's null completion and non-null-safe
  callers are a separately documented blocker; prior same-prompt reconnect
  acceptance is withdrawn. New source implementation requires review; any
  missing dependency acquisition needs separate approval. Current changes are
  documentation and baseline-attempt evidence only.
- Review the completed actual-phone evidence; keep PRs #8 and #11 draft/unmerged.
- Review first-slice actual results and the documented negative-control correction.
  The [reviewed scope](experiments/gate-2-stage-b-scope.md) is historical design;
  its zero-task negative assumption and predicted blockers now have actual evidence.
- Review the architecture recommendation and existing embedded test extension
  before implementation. The previous FutureTask canary recommendation
  remains historical and unimplemented; it is superseded as the immediate next
  step by the owner's architecture-review request. Any runtime/campaign boundary
  adaptation needs a separate concrete source diff and approval.
- No additional server, network or deployment authorization persists from this
  completed single session. Whole-game porting, accounts, saves and unrelated
  services remain outside scope.
- Current branch `codex/architecture-comparison` adds only the architecture
  design, embedded-test plan/baseline-attempt evidence and PROJECT/STATUS
  documentation updates, stacked on draft PR #15.
  Earlier draft PRs and successful experiments are unchanged and unmerged;
  no original Forge class/POM or asset changed.
