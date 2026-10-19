# D3b Phase 1 — capture ownership remains blocked

2026-10-19. Documentation-only follow-up to [PR #25](https://github.com/rwZephyr/forge-adventure-web/pull/25),
base `8503d76ef93ac94ec2d2e2cc3375f1b62b569f1d`. Production references below
are pinned to unchanged Forge `db893dcdb14fdd85a05437ad458dc70ef704ca2e`.
The [review on #25](https://github.com/rwZephyr/forge-adventure-web/pull/25#issuecomment-6272501767)
correctly treats settled-prompt quiescence as a hypothesis.

**Result: stop before Phase 2.** This audit did not establish an all-writer
ownership/publication protocol for a reusable projector. Original callbacks
identify input transitions and individual waiters; none of the inspected
callbacks alone grants exclusive access to the mutable game/view graph.
This is an evidence gap, **not a proof that an adapter-side protocol is impossible**.
No new compilation, JVM execution, graph capture, adapter, action dispatch,
overlap guard or negative-control execution is claimed by this PR.

## Actual source ordering

| Original path | Ordering established; missing guarantee |
| --- | --- |
| [InputQueue.setInput, lines 95–112][queue] | Pushes the input into its concurrent deque, checks game-over, acquires/releases `syncPoint`, then synchronously notifies observers. The monitor is not held over prompt work or a projection, and game mutations generally do not acquire it. |
| [InputProxy.update, lines 56–74][proxy] | Reads the actual input, publishes it in an AtomicReference, sets the current GUI player, then posts a runnable. That runnable **reads the current proxy again**, enters/leaves `syncPoint`, and calls `showMessageInitial`. It can display a replacement input rather than the one that caused the post. |
| [GuiDesktop.invokeInEdtLater, lines 104–105][desktop] | Uses real `SwingUtilities.invokeLater`. Posting and the input/queue publication provide ordering for preceding work; they do not fence unrelated/future mutators. |
| [InputSyncronizedBase, lines 22–55][syncinput], [IGuiGame.awaitInput][await] | `showAndWait` calls `setInput` **before** `awaitLatchRelease`. `awaitInput` receives the real completion latch and normally calls `done.await()`. A prompt callback may therefore precede the calling thread's park. Latch count 1 identifies an unfinished input, not exclusion of other workers. |
| [InputBase.showMessageInitial, lines 77–94][base] | Resets `finished`, cancels the waiting notice, calls `showMessage`, then `showSelection`. `updateButtons` can occur inside `showMessage`, before the final selection update. A button event is not the end of the original prompt runnable. |
| [HostedMatch controller/observer wiring, lines 215–253][host], [GameEventForwarder, lines 45–96][forwarder] | HostedMatch registers its forwarder on human input queues. Notifications/priority events flush through `handleGameEvents` on the notifying thread. Forwarder state is ordinary mutable state; these methods do not acquire an adapter-wide ownership barrier. Do not infer observer completion from a peer's button event. |
| [ProtocolGuiGame, lines 156–245, 322–339, 461–472, 532–581][protocol] | Buttons use bare `send`, with no graph synchronization. Card-bearing prompts, selection and card callbacks use `syncAndSend`; ability questions use `syncAndSendAndWait`. These **collect/flush graph data**, not a mutual-exclusion lock. A peer sees these callbacks after the collection has already happened. |
| [GameProtocolSender, lines 12–35][sender] | Directly calls the in-process remote on the current caller. There is no serial executor, transaction boundary or writer-completion message supplied by the sender. |

Thus the source permits this ordering, without asserting that every run has it:

```text
Game-0: setInput -> notify observers -> enqueue EDT prompt -> ... -> awaitInput
EDT:                                  showMessage -> buttons -> showSelection
                                      ^ buttons need not follow Game-0's park
```

Posting another EDT runnable can put a copy after the current EDT prompt work.
It serializes that copy with EDT work; it does not, by itself, stop Game-0 or
Game-1. The original `syncPoint` publishes prior writes but is not a graph lock.
Successful latch release orders work before `countDown` with a subsequent
successful await; observing an unreleased latch is a different condition.

## Genuine cast, targeting and payment paths

1. [InputPassPriority.onCardSelected][priority] obtains original playable
   abilities and asks `PlayerControllerHuman.getAbilityToPlay`. The human
   controller builds its view-to-ability cache and calls the GUI. Original
   `stop` removes that input and releases Game-0. The phase loop then passes
   the chosen ability to original `PlaySpellAbility`.
2. [PlaySpellAbility.chooseOptionalAdditionalCosts, lines 140–149][play] asks
   again on Game-0. Original targeting uses `InputSelectTargets`; selecting the
   opposing player checks original target legality, adds the target, publishes
   selection and stops when the original maximum is reached
   ([lines 337–369 and 405–448][targets]). No custom target insertion is needed.
3. Original payment reaches [PlayerControllerHuman.applyManaToCost][human-payment],
   which constructs `InputPayManaOfCostPayment` and waits. Its constructor pushes the actual
   paid-for ability and cost into original payment state
   ([lines 24–42][costpayment]). Game-0 is parked while the EDT can accept the
   manual Mountain action.
4. [InputPayMana.onCardSelected/activateManaAbility][mana] selects the genuine
   mana ability; it sets `locked`, then invokes the original payment runnable
   at lines 331–355. [GameAction.invoke, lines 2554–2559][action] runs inline
   on a Game thread or submits to the [cached Game executor][threads] from EDT.
   It returns **void**, not a task handle the GUI adapter can join. In the
   recorded fixture the new worker is Game-1, not the parked Game-0.
5. Game-1 runs original `PlaySpellAbility`, including the Mountain's additional
   cost offer, taps/produces mana through the original logic, and calls
   `payManaFromAbility`. It finishes its lambda with `onStateChanged`.
   [InputPayMana lines 389–428][mana] can publish button/message refreshes;
   `updateMessage` also resets `locked`. That plain field is not a completion
   token for a projector on another thread.
6. When paid, `onStateChanged` calls `done` and `stop`. Original `stop` runs
   `onStop`, selection, EDT finished scheduling and queue replacement **before**
   `countDown`. Game-0 can then finish casting, publish stack priority and resolve
   Bolt via the original phase loop after authentic priority decisions.

The normal singleton-Mountain branch has a useful property: `onStateChanged` is
the lambda's last call, and `countDown` is `stop`'s last statement. **This audit
does not claim that this normal branch keeps mutating the graph after its latch
release.** It is promising evidence for a narrower future handoff proof. It does
not turn an earlier refresh on the still-current payment input into such a proof.
The UI/controller call returning also need not mean the asynchronously submitted
mana runnable has finished.

Input identity alone is insufficient for generations. `showMessageInitial`
re-reads the current input; payment can refresh the same input; `removeInput`
notifies observers before releasing the old latch. The adapter must distinguish
new original waits from re-shown/consumed prompts before issuing a capability.

## What the existing runs actually show

- [PR #25 stdout][d3a-log] records a genuine parked `InputPassPriority`, original
  Game-0 and EDT identities and no post-setup graph capture. It stops before
  casting/payment. It cannot establish a Game-1 capture interval.
- [PR #22's first capture failure, lines 329–343][failed-log] records the same
  payment input first at a passing checkpoint, then at a refresh rejected before
  projection because the worker predicate no longer held. The subsequent graph
  overlap was caused during the historical failure cleanup. **This is not evidence
  that a properly admitted normal Bolt run necessarily overlaps.** PR #23 fixed
  that test cleanup policy; it did not introduce production graph ownership.
- [A successful PR #22 run, lines 385–441][passed-log] records multiple button
  events for the same payment input and a later new priority input after payment.
  The successful checkpoints use fixture-specific latches, approximate pool
  counters and repeated fingerprints. Those accepted observations remain valid;
  they do not establish a reusable producer-completion contract.
- [PR #21's C1 evidence][c1] already establishes the three genuine singleton
  offers on EDT, Game-0 and Game-1 and their reference relationships. C1 remains
  closed for the fixture. Ability identity is not the blocker reopened here.

The [Tracker source][tracker] explicitly describes a non-thread-safe game-owned
ledger; `freeze` is change batching, not a cross-thread read lock.
[DeltaSyncManager.collectDeltas, lines 106–144][delta] catches concurrent walk
failures, retries and can ship partially collected data. An exception-free
callback/packet therefore cannot certify a coherent new frame. An overlap guard
around GUI graph callbacks would miss mutations performed between those callbacks;
guard/fingerprint agreement is diagnostic evidence, not synchronization.

The `ProtocolGuiGame` comments describing sync helpers as game-thread-only and
card-bearing prompts as already parked are not enforced ownership checks. C1
actually recorded the ability-question path on EDT, Game-0 and Game-1; each calls
the same graph-sync machinery on its caller. Those comments cannot supply the
missing guarantee for a new projector.

## The minimum unresolved obligation

Before any projector runs, a **match-local capture permit** must establish who
cannot mutate the graph for the whole copy, and how all preceding writes become
visible. Neither a button notification nor one stored original latch provides
that permit. No such protocol was established by this audit or the prior runs.

The smallest candidate worth reviewing is an **adapter-side await-entry handoff**:
hold the original calling worker inside the GUI await callback while a generation-
checked EDT task finishes prompt work and performs an authorized copy. It must
first account for every other producer, including the prior payment worker and
queued stale-input work, and revoke admission during dispatch. A new callback
gate would hold only its caller unless those other paths participate in the proof.
It is not enough to rename `invokeAndWait` or a new adapter monitor as quiescence.
This candidate is **not implemented or asserted safe** here, and is not a request
to rewrite Forge's internal locks, move payment logic or wrap the engine in a new
executor.

Recommended next review: require a concrete normal-Bolt handoff argument showing
the last graph-affecting operation for each producer, actual input-generation
transitions, release/publication edges and refusal before graph reads on every
unmatched path. Where the existing hooks cannot supply that argument, identify the
specific missing lifecycle observation before proposing any source adaptation.
A passive, no-projector ordering witness of the existing human sequence could
test this candidate after its protocol is defined; it would not itself prove
exclusive access. The present task stops at this unresolved ownership gate.

## Deferred adapter and verification scope

No projector/admission API is published by this PR. Once capture ownership is
reviewed, the minimal slice still needs the accepted viewer allowlist, cached
immutable frames, exact C1 reference validation and C2–C6: atomic immutable
authorization publication, EDT live lookup/recheck/dispatch in one runnable,
original manual Mountain provenance, typed canonical payloads and an ID ledger
that distinguishes conflicts from exact retries. No raw Java object may reach
the consumer. Cached resync must not trigger a fresh graph read.

Overlap, inconsistent frame and unknown-question diagnostics must record failure
without throwing asynchronously into Forge. The standalone owner must observe it
and exit the supervised process; an unanswered required call must not return null
or fabricate a selection. No cleanup concession, cancel, automatic pass or input
release is permissible. PR #25's throwing diagnostic helpers are not a reusable
implementation of that new failure contract and were not changed here.

**One end-to-end run and all new negative controls remain unexecuted.** In
particular, no unsupported capture is attempted merely to test whether it throws.
The planned refusal must happen before dereferencing the game graph.

### Genuine 50-cycle strategy: candidate, not an executed test

The existing successful log has five distinct non-bootstrap input objects at
button callbacks: initial priority, targeting, payment, stack priority and
resolved priority. Repeated payment buttons/cached reads are not extra cycles.
The [audit inventory](d3b-capture-audit.json) records the exact old-log identities
and first source lines; these are within-run diagnostic identities only.

A possible later extension is to continue **explicitly admitted normal priority
passes** after Bolt resolves, using new `InputPassPriority` instances constructed
by the original [chooseSpellAbilityToPlay, lines 1771–1773][human] and original
phase/turn loop. Do not reset fixture state, replay a cached frame, click End Turn,
set auto-yield or alter decks/preferences to force the count. Count a cycle only
when a new original await generation is observed and one authorized coherent
frame is published; an input refresh does not count. Stop at 50 or a predefined
time/turn bound, and stop on an unsupported required decision or diagnostic failure.
The existing fixture has nonempty libraries, but skipped phases/yield preferences
and real AI land turns mean the audit does not establish that this will produce
50 supported human decisions within the bound. That strategy needs its own
source/ownership review after the first end-to-end run. No 50-cycle claim or
extended execution is authorized by a passing five-frame log.

[queue]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/input/InputQueue.java#L95
[proxy]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/input/InputProxy.java#L56
[desktop]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-desktop/src/main/java/forge/GuiDesktop.java#L104
[syncinput]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/input/InputSyncronizedBase.java#L22
[await]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gui/interfaces/IGuiGame.java#L327
[base]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/input/InputBase.java#L77
[host]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/HostedMatch.java#L215
[forwarder]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gui/control/GameEventForwarder.java#L45
[protocol]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/net/ProtocolGuiGame.java#L156
[sender]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/net/GameProtocolSender.java#L12
[priority]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/input/InputPassPriority.java#L342
[play]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/player/PlaySpellAbility.java#L140
[targets]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/input/InputSelectTargets.java#L337
[human]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/player/PlayerControllerHuman.java#L1771
[human-payment]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/player/PlayerControllerHuman.java#L2715
[costpayment]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/input/InputPayManaOfCostPayment.java#L24
[mana]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/input/InputPayMana.java#L331
[action]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/GameAction.java#L2554
[threads]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-core/src/main/java/forge/util/ThreadUtil.java#L25
[tracker]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/trackable/Tracker.java#L29
[delta]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/net/server/DeltaSyncManager.java#L106
[d3a-log]: ../../experiments/standalone-bolt/evidence/bootstrap-1/standalone.stdout.log
[failed-log]: ../experiments/evidence/stage-c-dto-20261018/capture-gate/console.log
[passed-log]: ../experiments/evidence/stage-c-dto-20261018/dto-final-2/console.log
[c1]: ../experiments/c1-identity-results.md
