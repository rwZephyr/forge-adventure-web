# D3c — original Bolt lifecycle witness, submitted before execution

**Execution update:** the [single approved attempt](bolt-lifecycle-witness-results.md)
compiled successfully, then stopped at the launcher child executable/entry-point
guard with an empty trace. No retry or implementation change followed. The plan
below preserves the pre-execution scope at reviewed head `f7e9e1fd`; statements
about proposed/unexecuted work below describe that submission, not a runtime pass.
This PR is stacked on #26 at
`bfeb88083db7346cbc108a3df602b9b7ccb4062f`. It follows the
[reconciled review conditions](https://github.com/rwZephyr/forge-adventure-web/pull/26#issuecomment-6274427257).
It tests the narrow original Bolt sequence, not general game-graph ownership.
Original source locations are pinned in the
[D3b ordering audit](../architecture/d3b-capture-ordering.md); accepted
[C1 identity evidence](c1-identity-results.md) and
[Stage C gameplay assertions](stage-c-dto-results.md) remain historical results.

## Components and trust boundary

- [`BoltLifecycleMain`](../../experiments/standalone-bolt/src/experiment/BoltLifecycleMain.java)
  extends PR #25's standalone approach as a separate entry point. It retains the
  original deck/zone fixture, explicit Play/Keep bootstrap and original human/AI
  controllers. It selects Bolt, the opponent, Mountain, then one ordinary priority
  pass. It leaves the resulting next human priority unanswered and exits the
  disposable JVM. PR #25's original entry point and historical evidence are unchanged.
- [`BoltLifecycleObserver`](../../experiments/standalone-bolt/src/experiment/BoltLifecycleObserver.java)
  uses JDK 17's existing Windows `dt_shmem` JDI connector, as the accepted C1
  witness did. There is **no socket fallback**. It reads fields, arguments,
  receiver identities and stack frames; it never invokes target methods.
  Shared memory is local OS debugger IPC, not a browser transport/listener.
- [`LifecycleJson`](../../experiments/standalone-bolt/src/experiment/LifecycleJson.java)
  encodes diagnostic primitives/collections only. It refuses arbitrary Java
  objects; it is not a game projector, DTO boundary or JSON input protocol.
- [`Run-Lifecycle.ps1`](../../experiments/standalone-bolt/Run-Lifecycle.ps1)
  compiles offline and supervises one observer/target pair in a fresh ignored
  directory. Nothing in this PR invokes that launcher.

No original Forge source, controller, AI, input, payment or synchronization code
changes. No TestNG scenario is called. The unchanged test-only `TestUtils`,
`HeadlessGuiDesktop` and `HeadlessNetworkGuiGame` are rebuilt as bootstrap helpers;
this does not promote them to production headless hosting.

## Lifecycle observations and exact assertions

The debugger owns the generation registry. Keys are actual JDI object identities
of original Input receivers, not card names, ability IDs or hash-code guesses.
Previously seen input instances never count as new generations. The driver
retains input/wait references for this small run; no engine object is sent to a peer.

| Observation site | Planned meaning and assertion |
| --- | --- |
| `InputQueue.setInput:104` | After the original deque push, before `syncPoint`/observer notification: record **queue publication** with actual argument/queue/caller. Only the registered human's queue counts. |
| `InputProxy.update:73` | Atomic input has been set; sample actual value **before this prompt post**. An older queued runnable can already display the new input, so this is not asserted to precede every display. Lock-UI transitions do not count as input generations. |
| `InputBase.showMessageInitial` entry and method exit | Record actual Input receiver, display depth and completion. This distinguishes the whole prompt runnable from buttons emitted inside it. |
| `ProtocolGuiGame.updateButtons` / `showPromptMessage` entry | Find the actual originating Input receiver in the original stack. Compare it with the current atomic proxy. Classify current-awaited, **published-pending-await**, stale/consumed, or lock/unbound transition. A pending-await callback is legitimate. Repeated callbacks do not create generations. Unexplained/missing synchronized-input bindings fail. |
| Driver GUI `awaitInput` entry/return markers | Retain actual input/latch/owner; delegate unchanged original wait. Assert one await per input in this fixture and return at count zero on the same owner. There is no capture gate or wait for a driver grant inside await entry. |
| `InputSyncronizedBase.stop:55` | Read the original `cdlDone` identity/count immediately **before** the original `countDown` call. This is not the release linearization point or a worker-exit event. |
| Driver consumption/dispatch markers | Driver waits for recorded await readiness, then performs live proxy/queue/latch checks and controller selection in one EDT runnable. Each input is consumed at most once. Display must have completed. Stale queued callbacks cannot select anything. |
| First publication following payment | Inspect the **same original payment latch** at zero and require its Game-0 await-return observation before a fresh generation; publisher must be that waiter. A new generation while payment is unresolved fails immediately. |

Normal expected original generations are exactly: initial priority, targeting,
payment, stack priority, resolved priority. Play/Draw and Keep/Mulligan are
separate bootstrap generations, not extra successful Bolt cycles. Any unexpected
normal generation or repeated await of one instance stops the experiment.

The in-process endpoint also records **proxy samples**, explicitly labelled as
samples rather than claimed callback origins. The debugger's real stack receiver
is the binding authority. Driver label/current-input mismatches do not guess an
origin or drive a selection; the trace identifies stale refreshes. The original
callback methods are final in places and carry no Input argument, which is why
proxy-only inference is insufficient and passive JDI is used.

## C1 authorization and original-game result

Before the cast, the live EDT driver calls the same original playable-ability
query used by PR #22 and requires exactly one valid Bolt spell ability. An
immutable authorization is published in an AtomicReference before controller
selection. Exactly three original `getAbilityToPlay` questions must follow:

1. EDT initial Bolt offer: exact authorized spell/view and offered host reference.
2. Game-0 additional-cost Bolt offer: same authorized reference pair.
3. Game-1 Mountain additional-cost offer: exact authorized original mana pair,
   while Game-0's recorded payment latch is still closed.

At each call, the original live `spellViewCache` must contain exactly the offered
key and mapped ability by `==`; the answer is that actual offered view. The
observer records those authorization references as JDI identities. No matching
ID/name fallback, copied ability, null reply or default option exists. Unknown
required questions record a failure then remain unanswered until process disposal.

The small oracle subscribes to **original GameEvents**; `@AllowConcurrentEvents`
avoids introducing Guava's implicit per-subscriber serialization. It checks:

- Original Mountain-tapped event for the held original source view.
- Original Bolt cast event with a real stack instance. On its original emitting
  Game-0 thread, the accepted Stage C target/payment assertions check one original
  opposing-player target and one RED payment from the manually selected Mountain.
- Original opponent life-change event with literal old=20, new=17; no damage is
  calculated or applied by this code.
- Original non-fizzled Bolt-resolution event and its original graveyard transition.

Card-copy semantics are kept separate from C1: original Mana records an LKI source
snapshot, so the established payment oracle uses RED/source-card-ID/live tap state,
not source-object equality with Mountain. Original zone moves also copy Cards and
views; the graveyard event matches the fixture's card ID. These result assertions
do not substitute IDs for the exact ability/view/cache reference checks above.

These are narrow gameplay assertions, not a new view/engine traversal, fingerprint
or state projection. No graph is read solely to format a log. Only explicit
fixture arrangement moves original cards; subsequent gameplay uses original
controller actions. No Auto/OK payment, direct target insertion, direct resolver,
life mutation or fake event supplies the result.

## Observation versus source causality

`ordering.jsonl` records observer sequence/time, target marker sequence/time where
available, actual receiver/latch/thread IDs and event-thread suspension metadata.
Observer and target clocks/counters are separate. Event delivery/log ordering
across threads is not asserted to be source causality.

The unchanged source places the normal Mountain runnable's final call at
`onStateChanged` (`InputPayMana:332–355`). The paid path runs `done` and `stop`;
`stop` performs its graph-affecting callbacks/queue removal before `countDown`
(`InputSyncronizedBase:44–55`). Successful Game-0 await return supplies the
release/await ordering; the new-generation breakpoint independently samples
that same latch at zero. A pre-countdown breakpoint alone is insufficient.

After release, Java stack unwinding and executor bookkeeping may remain on
Game-1. Queued EDT finished/selection/input-display work may also remain; prompt
callbacks can refresh consumed inputs or re-read a replacement input. The trace
records those origins/transitions instead of declaring the worker dead. Only
owned **process** exits are independently supervised. No Game-1 thread-death
claim or all-writer capture permit results from this witness.

JDI uses `SUSPEND_EVENT_THREAD`, not an application capture hold. Debugger pauses,
small diagnostic reference registries, event subscribers and logging perturb
scheduling and may mask/expose interleavings. Even repeated success would support
only this normal five-generation hypothesis, not safe projection or a general
runtime synchronization guarantee.

## Proposed one-run execution — owner approval required

**Do not execute until the owner approves this plan.** No batch/retry option is
provided. The approval switch is an accidental-launch guard, not approval itself.
From the repository root in PowerShell 7, the proposed command is:

```powershell
./experiments/standalone-bolt/Run-Lifecycle.ps1 -Label d3c-first-1 -ApprovedSingleRun
```

Use a fresh label if occupied. Required existing inputs: installed JDK 17,
accepted compiled Forge reactor/classpath, repository-local cache and original
POM module opens. The TestNG JAR is removed from the classpath. `javac -g` compiles
only the three new experiment classes and three unchanged headless helpers;
there is no Maven/plugin invocation, dependency acquisition or source fallback.
Pinned original breakpoint sites must be present; missing debug information or
unavailable shared-memory transport is a failure, not a reason to use sockets.

Preflight checks CIM access to the launcher's own process **before Java starts**.
It is used only for ownership observations of the launched observer's direct
child: retained Process handles, parent identity, creation time, Java executable
and exact target entry point. Unavailable ownership inspection stops before launch.
An approved execution must have sufficient read-only process-inspection access;
this is not authorization to alter services or machine security settings.

Fresh `.local-tools/bolt-lifecycle/<label>/` home/temp/AppData/classes/output
directories isolate the run. Six environment variables are restored exactly,
with absent/present preflight. Before/after protected-source/config/card and
Maven-cache inventories must agree. The launcher also protects historical
standalone files/evidence and records compilation source hashes.

Bounds: compile 60 seconds; original driver 100 seconds after match start;
target self-watchdog 150 seconds; observer event loop 190 seconds after launch;
outer observer supervision 220 seconds, with bounded 10-second child-exit and
output-reader observations. Connector-launch stalls are additionally supervised
through the direct owned-child handle. These are nested bounds, not a promised
total wall time including integrity hashing.

On the first ordering violation, the observer records its diagnostic and kills
only its owned target **before resuming the failing event thread**. A recorded
target failure is received as its exact atomic Throwable JDI identity, type and
message; the launcher receives the observer's failed result/exit. It may not have
a driver-result file if disposal precedes that write. Logs copied from the target
are joined/checked before the observer result is finalized.

No diagnostics are thrown asynchronously into original workers. A required
unanswered question remains blocked; the standalone owner/supervisor disposes
the process. Success also uses explicit process exit with the last input left
unanswered. Never concede, release an original latch, click cancel, pass for
cleanup or retry after failure. No broad process-name kill is used.

### Acceptance for the first run

- Offline compile succeeds; observer and original standalone target exit 0;
  no forced termination, survivor or missing output.
- Five distinct normal original generations; pending-await and refresh/stale
  callbacks classified without invented origins or generation inflation.
- Released payment latch/await-return precedes new Game-0 publication as checked
  above; no new human input while payment is unresolved.
- Three exact C1 offers, original target/mana/stack/resolution evidence and literal
  opponent life transition 20→17; next human priority remains unanswered.
- No unexpected required question, diagnostic violation, environment/integrity
  change, dependency/network/service action or graph projection.

Preserve complete private target/observer logs, JSONL trace, driver/ordering/run
results, process observations and inventories. Before future publication, inspect
for credentials/private identifiers, normalize repository paths and generated
lambda addresses, and record original/public hashes and sizes. Do not commit
classes, JARs, downloaded POMs or profiles. No runtime evidence is included now.

If that one run passes, report it and **request separate authorization for up to
ten fresh runs**, halting at the first violation. No ten-run/50-cycle execution,
projector, await-entry hold or browser work follows automatically. Stop after
publishing this implementation/plan for independent review.
