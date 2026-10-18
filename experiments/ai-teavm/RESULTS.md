# Stage B first-slice results on Silent

2026-10-18, America/Vancouver. Branch `codex/gate2-ai-baseline`, based on reviewed
draft PR #13. User approved only the original JVM fixture/control, metadata and
dependency inventory, and unchanged-runtime TeaVM compilation. Originals match
`db893dcdb14fdd85a05437ad458dc70ef704ca2e`; successful PR #8/#11 experiments remain
unchanged and all earlier PRs remain unmerged.

## Outcome

**Verified on JVM:** three fresh successful Bolt/Mountain decisions, original
target selection, one R paid from a Mountain, stack casting, three damage,
graveyard movement and lethal game-end state. A separate empty-hand control
returns null with no payment/damage. Passive shared-memory JDI witnesses prove
the genuine FutureTask / Game AI Eval / timed-get path without Forge edits.

**Blocked on TeaVM:** the one unchanged-runtime attempt failed with **93 severe
compiler problems**, exit 1. Complete compiler output and reachable call chains
are retained. No runnable app.js remains and no browser execution is claimed.
No compatibility adapters, thread rewrite, localization replacement, dependency
stubs, library upgrade or workaround compiler retry was performed.

## Commands and actual results

Exact scripts/expanded flags are in [README](README.md); all builds used existing
JDK 17.0.12, portable Maven 3.9.16 / Gradle 9.6.1 and isolated offline caches.

| Command | Actual result |
| --- | --- |
| `Prepare-Forge.ps1` | Four-module parent/core/game/AI BUILD SUCCESS, 18.165 s, zero Checkstyle violations; 154 core, 822 game and 192 AI Java sources compiled. 2,610 original input files hashed and later rechecked. |
| `Invoke-Probe.ps1 -Task jvmControl -WriteLocks` | Final run BUILD SUCCESSFUL in 1m 2s; three fresh positives and one fresh empty-hand negative exit 0 under 15-second external bounds. |
| `... -Task metadataInventory -WriteLocks` | Final audit successful in 26 s: 149 TriggerType entries; 203 ApiType entries / 200 effect classes; 193 AI mappings / 147 AI classes; 491 public signatures audited. Fixture policy grants 346 signatures. |
| `... -Task jvmTrace -WriteLocks` | Final shared-memory observer successful in 44 s; positive 9 events and negative 11 events, both path witnesses true, original child exit 0, no observer watchdog expiry. |
| `... -Task dependencyInventory -WriteLocks` | BUILD SUCCESSFUL in 22 s; 63 configuration entries / 58 unique coordinates, all 58 cached-POM license declarations resolved. |
| `... -Task browserOriginal -WriteLocks` | BUILD FAILED in 55 s, measured wrapper 55.671 s, native exit 1, 93 severe TeaVM diagnostics. Host audited all 346 requested constructors before generation. First reported blocker: File.toPath. |
| `scripts/collect_results.py` | Passed: JVM observations, original path witnesses, 2,610 source/class/POM hashes, constructor/dependency/license inventories and all 93 compiler diagnostics verified. No game/browser/server is started by this collector. |

## Genuine fixture and JVM observations

Original CardRules/PaperCard/CardFactory parse/build the unchanged Bolt and
Mountain scripts. TypeLists is required bootstrap data: original FileSection /
CardType.Helper parsing loads all genuine types, as in original FModel wiring,
without importing GUI/FModel or adding a provider. Original Localizer/Lang use
the copied unchanged English properties through their original file/classloader
path. No message or data singleton was faked.

Positive registered decks contain AI twelve Mountains/two Bolts and opponent
ten Mountains. Original zones contain ten Mountains in each library, AI two
Bolts in hand and two untapped Mountains on battlefield. Phase MAIN1, AI active
and priority player, life 20/3, empty stack/pools, GameStage.Play, original
last-state snapshot and no simulation/rotating profile. Two candidate Bolts and
two mana abilities are visible to the preflight; original deduplication reduces
the selection list to three before threaded evaluation. No target is preselected.

| Fresh run | Original random initialization | AI decision | Whole fixture | Result |
| --- | --- | --- | --- | --- |
| Positive 1 | about 8.02 s | 39.341 ms | 9,071.562 ms | Pass |
| Positive 2 | about 8.02 s | 46.643 ms | 9,125.837 ms | Pass |
| Positive 3 | about 8.02 s | 35.151 ms | 8,989.062 ms | Pass |
| Empty hand | about 8.02 s | 79.268 ms | 8,885.472 ms | Pass, null decision |

These are JVM observations on Silent, not browser/mobile timings. Each runs in
its own Java process. Parent-process elapsed times include startup and remain
below the independent 15-second bound. AI deadline remains two seconds.

All positives chose an original SpellApiBased Lightning Bolt, DealDamage API,
opponent player 1, exact DamageDealAi/DamageDealEffect products. Original
`ComputerUtil.playStack(sa, ai, game)` returned true, with R from Mountain,
one tapped source, one paying mana ability and one stack item before resolution.
Original `MagicStack.resolveStack` plus `checkStateEffects(true)` yielded life
20/0, Bolt in graveyard, empty stack and game over. No surviving Game AI Eval
thread was found afterward. No full game/turn loop was run.

### Important correction to the approved negative-control prediction

An empty hand with two original Mountains **does not produce an empty original
candidate list**. The real extractor includes their mana abilities. Empty-hand
reference preflight contains two mana candidates and zero spell/Bolt candidates;
the original thread evaluates both and returns null. Life/stack and untapped
lands remain unchanged; no surviving evaluator remains.

Thus the earlier scope's prediction of zero task/lambda entries is disproved.
The control is a successful no-spell/no-payment/no-damage control, **not** an
empty-list-guard or zero-thread control. We did not filter the engine's list,
remove mana abilities, rewrite the AI or report zero entries. Bolts are omitted
from this control's nonempty registered AI deck; moving them to graveyard would
leave them in the original available-card list. Negative deck is twelve
Mountains; both libraries still contain ten. This is documented fixture setup,
not replacement rules. No broader negative-control claim is made.

### Original threaded-path proof

JDI uses `dt_shmem`, no socket fallback, source patches, bytecode rewrite or
target method invocation. Breakpoints inspect original frames/fields then
resume immediately. The separately traced positive also completes actual paid
casting/resolution; the three unpaused controls remain separate observations.

Positive trace shows:

1. Nonempty original list (three) at AiController:1669, then FutureTask construction.
2. Caller thread 1 creates FutureTask object 2526 and enters timed get at 1782.
3. Distinct Java thread 2528 named Game AI Eval enters the original lambda at
   1687, then `canPlayAndPayFor` for Lightning Bolt card 22 / ability object 2532.
4. Timed-get finally at 1819 sees the same FutureTask in NORMAL state 2 with
   outcome object 2532/card 22. That matches the chosen card and original cast.
5. Fixture exits 0, timeout session inactive and no surviving evaluator.

Negative trace has two original mana candidates, a real task/lambda and two
`canPlayAndPayFor` entries for Mountain; NORMAL FutureTask outcome is null.
The external evidence validator verifies these relationships, not merely the
observer's pass flag. Thread/object IDs are run-local debugger observations.

## First reported TeaVM blocker and actual reachable chain

The first diagnostic in preserved compiler order is:

```text
ERROR: Method java.io.File.toPath()Ljava/nio/file/Path; was not found
    at forge.util.FileUtil.readAllLines(FileUtil.java:256)
    at forge.util.FileUtil.readFile(FileUtil.java:205)
    at forge.util.FileUtil.readFile(FileUtil.java:185)
    at probe.OriginalAiFixture.bootstrap(OriginalAiFixture.java:39)
    at probe.OriginalAiFixture.run(OriginalAiFixture.java:61)
    at probe.BrowserMain.main(BrowserMain.java:4)
```

This is the genuine type-data resource bootstrap. It is the **first reported
diagnostic**, not a claim that it is the only blocker or that changing this
method would make the AI work. The original localization bootstrap independently
fails at URLClassLoader via Localizer.setLanguage:164 → initialize:44 → fixture
bootstrap:35 → run:61 → BrowserMain:4.

The predicted original concurrency blockers are also now **verified compiler
failures**, independently of those resource failures:

```text
ERROR: Class java.util.concurrent.FutureTask was not found
    at forge.ai.AiController.chooseSpellAbilityToPlayFromList(AiController.java:1685)
    at forge.ai.AiController.chooseSpellAbilityToPlayImpl(AiController.java:1453)
    at forge.ai.AiController.chooseSpellAbilityToPlay(AiController.java:1425)
    at probe.OriginalAiFixture.run(OriginalAiFixture.java:127)
    at probe.BrowserMain.main(BrowserMain.java:4)
```

Additional actual diagnostics report FutureTask at timed get (1782) and
cancellation (1797), and Thread.stop at 1811. The renderer prints a conservative
reachable chain through the PlayBeforeLandDrop branch at 1453; the fixture
does not take that branch. A static compiler chain is not a runtime trace.
The JVM debugger separately witnesses the real fixture's nonempty selection.

Other diagnostics include tinylog/Sentry lock and process/management APIs,
ConcurrentLinkedQueue/CopyOnWriteArraySet, reflection Constructor.canAccess,
classloader enumeration and Guava EventBus/cache lock operations reached by
original game events/resolution. Full readable output is
[browserOriginal.log](evidence/browserOriginal.log); all **93** diagnostics with
their preserved chains are indexed in
[compiler-diagnostics.json](evidence/compiler-diagnostics.json).
No diagnostic was removed by changing virtual dispatch or dependency behavior.

## Harness failures retained and corrected before the compiler attempt

- Initial Gradle plugin lookup omitted Maven Central from pluginManagement;
  matching the existing Stage A repository settings resolved it entirely offline.
- Initial target assertion treated Iterable<Player> as List; corrected harness
  iteration, without changing AI targeting.
- Initial setup omitted proper priority/state bootstrap and type data. The
  recorded failure showed zero Mountain mana abilities. Original resetPriority /
  copyLastState calls and genuine TypeLists parsing corrected setup, without
  injecting mana, adding a mana ability or changing a card script.
- First fully payable cold AI call timed out in Oracle JDK SecureRandom entropy
  seeding. The original timeout log captures FutureTask.get and the Game AI Eval
  stack in SeedGenerator/DRBG → original MyRandom → DamageDealAi. Final fixtures
  initialize that same original generator before the deadline. No seed/provider,
  JDK security setting or timeout was replaced; the roughly eight-second cost
  remains measured within the fixture's external watchdog.
- First debugger witness captured the correct path and a passing fixture, but
  its host waited for debuggee shutdown before disposing the debug connection.
  Host cleanup was corrected to detach first. Final positive/negative witness
  children exit 0. No Forge runtime change was involved.

Initial logs/JSON are retained alongside final results; none is attributed to
TeaVM. Only **one native TeaVM generation attempt** occurred, and implementation
stopped after its compiler failure.

## Smallest recommended next experiment — separate review required

Before attempting any Game/resource/logging adaptation, propose a **compile-only
concurrency canary** using the same pinned compiler and the exact JDK
FutureTask/Thread/start/timed-get contract (with AITimeoutTracker only if needed
to isolate the shared deadline). No Game, card database, logger backend,
localization replacement or server. Its purpose is to isolate the hard AI-task
platform boundary from the broader Game/bootstrap diagnostics; it is not a substitute AI test
and cannot count as Stage B success.

If that boundary requires a class-library compatibility contribution, review
its exact API/semantics/licensing and success/exception/timeout/cancel/interrupt
tests before implementing it. Preserve original AiController's call graph;
do not replace FutureTask with synchronous evaluation. Resource loading
(File.toPath/URLClassLoader), logging/locks and event dispatch remain independent
review items even if concurrency becomes supported. No such canary or adapter
was implemented in this first slice.

## Evidence and limits

[verification.json](evidence/verification.json) records actual validated facts;
[evidence-manifest.json](evidence/evidence-manifest.json) hashes raw files;
input-manifest includes 2,610 original source/class/POM inputs. Constructor
manifests include full 491-signature audit and 346-signature fixture policy,
with actual JVM enum/map inventory. Dependency inventory includes all resolved
58 coordinates and cached-POM declared licenses (including existing AI
commons-math3, Apache-2.0); declarations are not distribution clearance.

No browser, HTTP/debug TCP server, dependency install, network/Tailscale/firewall,
other service, user save or account changes occurred. JDK shared-memory debug
sessions and fixture processes were closed. Existing draft PRs remain unmerged.
This demonstrates the genuine path on JVM and concrete compiler blockers on
the pinned TeaVM toolchain; original AI **execution under TeaVM remains blocked**.
