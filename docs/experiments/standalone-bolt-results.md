# D3a — listener-free standalone Forge bootstrap

**Verified:** one fresh standalone JVM run initialized original Forge outside
TestNG, booted the human/AI Bolt fixture and reached authentic parked human
priority. Both compilation and execution exited 0. **Unresolved:** production
state-capture ownership. No DTO extraction or transport is implemented.

Branch `codex/standalone-bolt-bootstrap`, stacked on PR #24 at
`55164bf309e1a0e38997c206762dc3a1c2635d81`. Original production source remains
unchanged against pinned Forge `db893dcdb14fdd85a05437ad458dc70ef704ca2e`.

## Actual run

Command, from the repository root in PowerShell 7:

```powershell
./experiments/standalone-bolt/Run-Standalone.ps1 -Label bootstrap-1
```

The existing JDK reported Java 17.0.12 in the recorded stack frames. `javac`
compiled the experimental entry point and three unchanged original headless
helpers against 99 existing local classpath entries, excluding the TestNG JAR.
Neither Maven nor TestNG executed. No dependency acquisition occurred.

| Observation | Recorded result |
| --- | --- |
| Compile child | PID 88676; exit 0; 1,982 ms launcher observation |
| Standalone child | PID 89344; exit 0; 23,059 ms launcher observation |
| Original controller binding | `PlayerControllerHuman`, main thread #1 |
| Bootstrap answers | Play and Keep only, real `AWT-EventQueue-0` #64 |
| Fixture setup | Original start hook on `Game-0` #59; original human and AI controllers |
| Final input | Original `InputPassPriority`; same reference in proxy, concurrent queue and await record |
| Wait ownership | `Game-0` #59, daemon, WAITING; original latch count 1 |
| EDT lifecycle check | Same EDT #64 as prompt display; no action dispatched |
| Exit | Explicit experiment `System.exit(0)`; no watchdog/supervisor timeout; both child handles report exited |
| Environment | Six exact restorations passed; absent/present restoration preflight passed |
| Integrity | 2,066 protected source/config/card files unchanged; 2,393 local-cache files unchanged |

Setup used the original life setters with 20 for both players and arranged one
human Lightning Bolt in hand and one untapped Mountain in play. It asserted the
original card/controller presence, untapped source, empty mana pools/stack and
nonempty libraries. The original start hook selects turn 1 MAIN1 as in the
accepted test fixture. Logged life/phase values describe that arrangement;
they are not later graph reads or a coherent state snapshot. No cast, payment, targeting, AI evaluation
or resolution is claimed by this run; those belong to the accepted earlier tests.

The original wait stack, recorded verbatim in the complete stdout log, is:

```text
CountDownLatch.await
IGuiGame.awaitInput
StandaloneBolt$ProbeGui.awaitInput
InputSyncronizedBase.awaitLatchRelease / showAndWait
InputPassPriority.showAndWait
PlayerControllerHuman.chooseSpellAbilityToPlay
PhaseHandler.mainLoopStep / mainGameLoop / startFirstTurn
GameAction.startGame -> Match.startGame -> HostedMatch start lambda
ThreadPoolExecutor.runWorker
```

Only Game-0 and the Swing EDT matched the relevant worker-name filter at the
recorded observation. The EDT was non-daemon and waiting for events. This is a
time-local stack sample, not a proof of absence of every asynchronous worker.
After execution, an independent `Get-Process` check found neither owned child PID
and no Java/javac/javaw process. No process was terminated by name or broad search.

Complete stderr preserves Forge's upcoming-set/unassigned-card warnings and
`Unable to load music file: ..\forge-gui\res\music\match\Hitman.mp3`.
They did not prevent original match/input startup; they were not patched or hidden.
No experiment failure, unexpected required question or thread/input mismatch was
recorded. One run was sufficient for this bootstrap milestone; no historical
positive/negative tests were rerun.

## Original integration points

Source references below are repository-relative links to unchanged code. The
new probe is entirely under
[`experiments/standalone-bolt`](../../experiments/standalone-bolt/README.md).

| Original source | Boundary used |
| --- | --- |
| [TestUtils](../../forge-gui-desktop/src/test/java/forge/net/TestUtils.java) | Existing `ensureFModelInitialized`: installs test-only GUI, initializes original FModel/card data/preferences. Has no TestNG import or lifecycle dependency. |
| [HeadlessGuiDesktop](../../forge-gui-desktop/src/test/java/forge/net/HeadlessGuiDesktop.java), [HeadlessNetworkGuiGame](../../forge-gui-desktop/src/test/java/forge/net/HeadlessNetworkGuiGame.java) | Recompiled unchanged test scaffolding, not a production headless service. Inherits the real Swing EDT; broad dialog defaults remain unsuitable for a production client. An explicit ProtocolGuiGame is supplied to this match. |
| [HostedMatch](../../forge-gui/src/main/java/forge/gamemodes/match/HostedMatch.java) | `startMatch`, original controller registration, `GameAction.invoke`, `Match.startGame` and existing start hook. No desktop Main, server manager or network channel is started. |
| [PhaseHandler](../../forge-game/src/main/java/forge/game/phase/PhaseHandler.java) | `setupFirstTurn` calls the fixture hook before `mainGameLoop`; the original loop subsequently requests the human decision. |
| [AbstractGuiGame](../../forge-gui/src/main/java/forge/gamemodes/match/AbstractGuiGame.java) | `setOriginalGameController` is observed by a delegating override. The registered controller is compared by reference with the original player's controller during setup. |
| [InputProxy](../../forge-gui/src/main/java/forge/gamemodes/match/input/InputProxy.java), [InputQueue](../../forge-gui/src/main/java/forge/gamemodes/match/input/InputQueue.java) | Original atomic input reference and concurrent deque provide lifecycle reference observations. No `getActualInput` game-view walk is added. |
| [InputSyncronizedBase](../../forge-gui/src/main/java/forge/gamemodes/match/input/InputSyncronizedBase.java), [IGuiGame](../../forge-gui/src/main/java/forge/gui/interfaces/IGuiGame.java) | Existing input installation and original `CountDownLatch.await`; the override publishes the actual latch/thread/input then delegates. No input implementation is replaced. |
| [ProtocolGuiGame](../../forge-gui/src/main/java/forge/gamemodes/net/ProtocolGuiGame.java) | Existing private in-process endpoint receives original button callbacks. Other raw views/deltas/events are ignored by the endpoint, not projected or sent outside the JVM. Original protocol graph walks still occur. |
| [ThreadUtil](../../forge-core/src/main/java/forge/util/ThreadUtil.java) | Original cached Game executor creates daemon workers. Observed Game-0 owns the wait; EDT ownership is distinct. |
| [HostingServer](../../forge-gui/src/main/java/forge/gamemodes/net/server/HostingServer.java) | `isHosting` is false before match start; absent hosting gives the original priority input a NOOP AFK timeout. No FServerManager startup call exists in the probe. |
| [Accepted Bolt fixture](../../forge-gui-desktop/src/test/java/forge/net/embedded/ProtocolGuiGameInProcessTest.java) | Small deck/zone-arrangement pattern reused with original GameAction methods. No test method is called; historical source/evidence stays unchanged. |

## Lifetime safety and evidence limits

Before running, source inspection identified the non-daemon Swing lifetime and
the difference from TestNG/Surefire exit behavior. The standalone process instead
owns an explicit exit policy: write diagnostics, flush, `System.exit`; a 150-second
self-watchdog bounds stalled shutdown with `Runtime.halt(124)`. The launcher bounds
only its retained compile/Java Process handles (60/180 seconds, then a 10-second
exit observation). No cleanup action is queued to the EDT. There is no concession,
input release, cancel, pass or game mutation for either successful or failed exit.
This is process disposal of an unsaved diagnostic match, not graceful match completion.

The bootstrap does not call the server-start or restart paths which register
network cleanup/relaunch hooks. The watchdog and owned-handle fallback avoid
depending on shutdown-hook completion. Normal explicit exit was observed; forced
exit/failure branches were source-reviewed, not exercised. No guarantee of future
production shutdown, persistent-save safety or reconnect behavior follows.

The launcher snapshots all tracked `forge-*/src/main/java/*.java`, original desktop
network/embedded test helpers/tests, the original JDI witness, root/desktop POMs
and Bolt/Mountain scripts, plus every repository-local Maven cache file. Before and
after inventories agree. Git also shows no original production-source/POM delta
against the pinned Forge baseline. Generated files observed in the run directory
were the eight compiled classes, logs and JSON evidence; no profile files were
created there. This is scoped integrity/environment evidence, not a whole-machine
filesystem audit. No service or network configuration command was run.

Published [complete logs, results and inventories](../../experiments/standalone-bolt/evidence/bootstrap-1/manifest.json)
include original/private and normalized/public hashes and sizes. Normalization
replaces the absolute repository path and generated lambda address, and converts
line endings/encoding. Private originals remain ignored locally. Host/user/profile
and token-pattern checks passed, and complete logs were manually reviewed. No
compiled classes, downloaded artifacts, temporary profiles or private XML are committed.

## Remaining capture boundary — stop for independent review

**Verified:** volatile publication of immutable lifecycle records, original
controller/input reference comparisons, original latch parking and distinct EDT
and game-worker roles. The probe reads no card/player/view graph after its fixture
setup hook. It does not add a graph projector, repeat graph fingerprints or claim
an idle pool/EDT/parked game thread confers exclusive access.

**Unresolved:** an independently reviewed production capture checkpoint, complete
mutator/worker accounting, nested input/payment-worker transitions, publication
ordering, and interaction with original ProtocolGuiGame/forwarder graph walks.
The private endpoint still sits behind a raw in-process protocol; it is not a
browser-safe state boundary. Test-only GUI dialog defaults, global FModel/random
state and process exit also need explicit hosting decisions before reuse.

The specific missing boundary is between original input publication and all of
its consumers: `InputQueue.setInput` notifies observers, `InputProxy.update`
publishes the pointer and schedules EDT `showMessageInitial`, and HostedMatch's
registered forwarder may flush graph-backed events before the original await.
An await callback or EDT runnable alone does not fence these consumers or later
Game-1 payment work. This experiment deliberately observes only lifecycle
references across that interval. A production projector needs an explicit,
reviewed ownership/publication agreement covering those paths, not a new read
inserted into this probe's await override.

Recommended next step: independently review this lifecycle evidence and select one
specific original publication boundary for a separate capture-ownership design.
Do not transplant Stage C's fixture-only parked-input checks into a production DTO
projector. D3b, browser connectivity and any runtime adaptation remain unimplemented.
