# Stage C: first browser/JVM interaction contract

2026-10-18. **Proposed design; implementation requires review.** Stacked on
[PR #19](https://github.com/rwZephyr/forge-adventure-web/pull/19), commit
`0e6fb9009c1e6fe6e65c70e16fc6c35d1789d524`. Original production source references
below are pinned to `db893dcdb14fdd85a05437ad458dc70ef704ca2e`.

The accepted [Bolt evidence](../experiments/embedded-bolt-results.md) establishes
human opposing-player targeting (20 → 17), manual Mountain payment, original
stack/resolution, and original threaded AI execution. It establishes neither
browser-safe serialization nor general synchronization safety. This document
proposes one in-process human Bolt DTO proof before any Safari transport work.
No builds/tests were rerun for this design; no source/runtime changes were made.

**C1 review hold:** the [existing-evidence identity audit](bolt-ability-identity-audit.md)
verifies singleton offers but cannot recover per-call view/cache Java identities.
DTO implementation remains unauthorized pending that evidence and review. C2–C6
below are design requirements, not implemented guarantees.

## Private Java boundary and viewer projection

Keep HostedMatch, Game, cards, rules, AI, PhaseHandler, PlayerControllerHuman,
ProtocolGuiGame and its IRemote peer inside Java. The future transport attaches
**after** a separate projector/admission boundary; it never exposes IRemote's
method names, Java objects or controller methods as a general RPC API.

Bind one non-null human viewer to a trusted session context on the Java side;
the client cannot choose or change its viewer. Construct a fresh, deeply
immutable, field-allowlisted `Frame` for that viewer. A full replacement frame
on each meaningful update is sufficient; no browser delta algorithm is needed.

| DTO field | Minimum contents and Java source |
| --- | --- |
| `players` | Opaque player handle, fixture display label, life, active/priority flags; [PlayerView][pv] and [GameView][gv]. No lobby/profile objects. |
| `battlefield`, `hand` | Visible card handle, approved face name/type/mana symbols, controller handle, zone, tapped flag. Human hand only. Enumerate these zones explicitly; no recursive card graph traversal. |
| `stack` | Ordered opaque stack handles, visible source name, activating player, permitted target handles; [StackItemView][stack]. No raw description, yield key, subinstance or ability object. |
| `turn`, `phase` | Primitive turn number and allowlisted phase code from GameView. |
| `payment` | Null outside payment; otherwise the visible spell handle, copied remaining mana shards and human pool counts, selected visible targets, currently offered mana-source choices. The original [payment input][payment] attaches its cost to the SpellAbility; use its [public cost/payment accessors][sa], not parsed prompt text or a second cost calculator. |
| `prompt` | Decision kind, prompt ID, min/max where relevant, selected handles and legal choice capabilities. A transition frame may have `prompt:null`; it offers no action. |

Apply both [CardView visibility predicates][visibility] to the **actual viewer**,
plus the explicit zone/field allowlist. Never use `canBeShownToAny` with an empty
viewer set (that grants visibility), or treat null-zone visibility as permission
to disclose. Unsupported face-down/reveal/alternate-face cases fail the fixture
with a recorded error; this first slice does not implement them. Omit both
libraries and the opponent's hand entirely, including their IDs, order and
per-card placeholders. Hidden counts are unnecessary here.

Handles are random, viewer/session-scoped references held in a private Java map,
not engine IDs or encodings of them. Allocate only after visibility approval;
revoke when visibility ends and issue a new handle on redisclosure. Never allow
old handles to resolve a now-hidden card. Stack references and prompt choices
must pass the same filter. Public display handles alone grant no action.

Raw GameView, DeltaPacket, GameEvents, Selection, CardView, SpellAbilityView,
tracker IDs, arbitrary `toString()`/prompt/log/exception strings and image keys
stay private. Use bounded structured labels for this fixture. Filtering the
root object while retaining nested references is insufficient. The projector
copies primitives and immutable DTO lists only; transport/logging cannot later
walk back into Java state. Artwork and a general card-text sanitizer are deferred.

## Messages and admission

Proposed transport-neutral interfaces (pseudocode, not added source):

```text
BrowserPeer.onFrame(Frame)                         // copied state + prompt atomically
BrowserPeer.onResult(ActionResult)
DecisionPort.submit(TrustedSession, Action)         // asynchronous; never join on EDT
DecisionPort.resync(TrustedSession, ResyncRequest)  // returns last coherent Frame
ViewerProjector.capture(PrivateCheckpoint, Viewer) // Java-only, never peer-callable
```

Every envelope has `v:1`, server-issued `matchId` and `sessionId`. These identify
an incarnation, not credentials; trusted context must authenticate/authorize the
human separately. Server revisions are monotonic integers within that match and
session, advancing on each distinct published frame, including changed choices.
They are unrelated to raw delta sequence numbers. A prompt ID identifies a
decision generation, not an Input object's identity: a new input or changed
legal choice set invalidates the old ID, even if Forge reuses an object.

Minimal messages, with abbreviated example state:

```json
{"type":"frame","v":1,"matchId":"m1","sessionId":"s1","revision":12,"state":{"turn":1,"phase":"MAIN1","players":[{"handle":"pA","label":"Human","life":20},{"handle":"pB","label":"AI","life":20}],"battlefield":[{"handle":"cM","name":"Mountain","controller":"pA","tapped":false}],"hand":[{"handle":"cB","name":"Lightning Bolt"}],"stack":[],"payment":null},"prompt":{"id":"q7","kind":"priority","choices":[{"id":"kCast","op":"cast","entity":"cB"},{"id":"kPass","op":"passPriority"}]}}
{"type":"action","v":1,"matchId":"m1","sessionId":"s1","revision":12,"promptId":"q7","actionId":"a1","choiceId":"kCast"}
{"type":"actionResult","v":1,"matchId":"m1","sessionId":"s1","actionId":"a1","status":"accepted","meaning":"controllerDispatched","basedOnRevision":12,"promptId":"q7"}
{"type":"actionResult","v":1,"matchId":"m1","sessionId":"s1","actionId":"a2","status":"rejected","code":"staleDecision","currentRevision":13}
{"type":"resync","v":1,"matchId":"m1","sessionId":"s1","requestId":"r1","lastSeenRevision":12}
```

Resync replies with `type:resyncResult`, `requestId`, the unchanged complete last
frame and a separate `transitioning` flag, with no game read on the requesting
thread. If none exists yet, return `notReady`. During an in-flight action the
client disables choices; Java rejects new actions as `busy` until the next settled
frame. Cached frames are historical; resync does not increment revision, release
a latch or create a new prompt. Duplicate pending submissions receive `pending`.

| Decision/capability | Original dispatch, with null trigger event |
| --- | --- |
| Known startup `playFirst` / `keepHand` | `IGameController.selectButtonOk()` only for the exact offered InputConfirm / InputConfirmMulligan decision; expose explicit choices rather than silently answering. |
| `priority:cast` | `selectCard(visibleBolt, null, null)` at InputPassPriority; original [playability query/selection][priority] supplies eligibility. |
| `target:selectPlayer` | `selectPlayer(offeredPlayer, null)` at InputSelectTargets. Derive legal players/min/max from original [Selection][target] delivered through [IGuiGame.setSelection][selection]; filter for this viewer. Display all supported legal players, let the client choose the opponent. |
| `payment:activateManaSource` | `selectCard(offeredMountain, null, null)` at InputPayManaOfCostPayment. Use the current input's original [activate query][mana] for source eligibility. **No OK/Auto capability during payment.** Original GameAction invokes payment and input completion. |
| `priority:passPriority` | `selectButtonOk()` only at the fixture's ordinary, enabled InputPassPriority with no yield suggestion. Unsupported yield/confirmation state fails closed. |

For example the target prompt offers `{"id":"kOpponent","op":"selectPlayer",
"entity":"pB"}`; the payment prompt offers `{"id":"kMountain",
"op":"activateManaSource","entity":"cM"}` alongside copied `remaining:["R"]`.
Those IDs belong to their new prompt/revision; `kCast` is no longer valid.

Each choice is an opaque, prompt-scoped capability for exactly one operation
and server-held entity/ability context. The action carries no Java ID, method
name, cost, target list, damage or claimed life total. Reject extra payload fields,
unknown operations and malformed/oversized IDs. No cheats, undo, yield settings,
Auto, cancellation or concession endpoint is included.

Admission serializes submissions per session. Verify trusted match/session,
revision, prompt, operation/input class, visibility and current choice membership;
reserve one action, then perform live handle→entity lookup, visibility and
capability/generation checks and the original [controller][controller] call in
the **same non-suspending EDT runnable**, with no intervening queue hop or wait.
The live registry is EDT-confined; off-thread admission uses immutable published
metadata and must never cache a resolved entity for later dispatch. Revocation
or retirement before this runnable makes lookup fail. If superseded, reject with
**zero controller calls**. Consume that generation before dispatch and admit no other
action until a new settled frame is published. Forge still performs targeting,
playability and payment validation; capabilities cannot override it. Never mutate
SpellAbility targets, mana, life, phase or stack from the adapter.

`accepted` means one validated controller call occurred without a detected
dispatch failure, not that casting or resolution succeeded. A false card-selection
return or unexpected question becomes a recorded contract failure; do not report
successful gameplay. The next original-game frame supplies the actual outcome.
An action may produce no gameplay change; no speculative browser state is trusted.

Keep an action ledger under the **trusted server-side session binding**, keyed
by `actionId`. Its canonical payload is the typed allowlisted record
`(type, v, matchId, sessionId, revision, promptId, choiceId)`; compare every field
by value, independently of JSON member order, with no unexpected fields or
coercions. `actionId` is the ledger key, not an omitted decision field. An exact
retry returns its pending/terminal receipt without dispatch, **before** stale
checks; the same ID with any changed field returns **`actionIdConflict`**, distinct
from `staleDecision`. A payload claiming another session cannot select that
session's ledger. Reserve atomically, including while dispatch is in flight.
For this bounded test cap the ledger at 256 actions,
reject new IDs when full and never evict/re-execute an old ID. A controller
exception after possible mutation is indeterminate: retain the failure receipt,
disable further actions and require investigation; never retry automatically.

## Threading and synchronous questions: explicit implementation gates

[HostedMatch][hosted] keeps original controllers, event forwarding and input
observers. [InputProxy][proxy] displays prompts on the EDT;
[InputQueue.syncPoint][queue] establishes the existing publication edge, and
[InputSyncronizedBase][latch] retains the original game-thread wait/latch release.
Controller dispatch remains `FThreads.invokeInEdtLater`. Original payment can
run on Game-1 through [GameAction.invoke][invoke] while Game-0 waits. The original
[AI FutureTask, Game AI Eval worker and timed get][ai] remain untouched.

**Synchronous questions cannot simply become network waits.** In #19,
Bolt selection executes on EDT, the further Bolt additional-cost choice on
Game-0, and Mountain's additional-cost mana-ability choice on Game-1. Waiting for a
new browser reply on EDT would block prompt/action dispatch. For this fixture,
the explicit `cast` / `activateManaSource` choice authorizes the unique original
ability and the bounded follow-up ability confirmations for that same attempt.
The private IRemote answers immediately on the original caller thread using
the exact offered SpellAbilityView, checked against the authorization's host,
ability kind and original offered ability relation. Never return a fabricated
view, choose a generic first item, return null or answer an unrelated prompt.

C1 is a **pre-implementation** gate: audit the relation between the two Bolt
ability offers and the Mountain offer, including original copies/cache mapping
in [PlayerControllerHuman.getAbilityToPlay][ability]. The current
[audit](bolt-ability-identity-audit.md) leaves the actual cross-call mapping
unverified; no authorization allowlist may be inferred from equal numeric IDs.
If the second offer cannot be tied unambiguously to the authorized cast, or a
non-singleton/unrecognized question occurs, record atomic failure before throwing
and prove owner receipt as in #19; stop for review. General asynchronous dialogs
and multi-ability cards need a separate design; no threading adapter is implicit.
After that gate closes, publish a fully initialized **immutable** authorization
record through `AtomicReference<Authorization>.set` **before queuing** the EDT
controller runnable. Game-0/Game-1 consumers read that same holder with `get`;
the volatile write/read supplies the happens-before edge. Include only audited
binding/sequence data; no unchecked mutable fields become authorization. Keep
question-consumption state in a separate atomic state machine, bound to the
attempt/generation; retire with compare-and-set at completion/failure. This
publication edge does not make referenced Forge objects or graph capture safe.

**Capture safety remains a gate, not a guarantee from #19.** ProtocolGuiGame's
[sync helper contract][protocol] calls for the game thread, yet accepted traces
show EDT graph walks. [DeltaSyncManager][delta] retries concurrent-modification
failures and can emit a partially collected packet. Filtering such a packet or
placing a lock only around the new projector cannot make the source coherent.
Input publication and a WAITING thread sample do not protect every other mutator.

For the proposed test, copy only at bounded, settled checkpoints on EDT after
original prompt display/Selection finishes, with the exact current input and
original waiter/latch recorded. Require the expected original await-input wait,
latch count 1, no controller dispatch/payment task/AI evaluation in flight, and
published fixture readiness. Initial payment is captured before selecting the
Mountain; during Game-1 payment copy nothing, then wait for the next settled
priority input. Queue the capture after `showMessageInitial` returns so early
button refreshes do not publish incomplete Selection. Obsolete callbacks are
discarded before capture; a changed input during capture fails the test.

Retain #19's graph-boundary overlap guard and record caller IDs, start/end times,
input/latch state and action generation. Add explicit fixture task-completion
evidence; do not infer quiescence from thread names alone. No capture on arbitrary
send/delta/event/resync callbacks, mixed frames or synchronization normalization.
Treat original graph-retry/partial-packet warnings as test failures too. If these
known fixture boundaries cannot establish quiescence or an overlap/inconsistent
read is observed, stop and report. General production
capture needs a separately reviewed ownership/barrier design covering **all**
mutators; neither EDT confinement nor posting to GameAction's pool proves it.

## Interrupted sessions (policy only)

Retain the same session, frame, prompt and ledger across a temporary loss of the
peer. No disconnect-triggered controller call, automatic pass, cancellation,
default answer or GUI pause. An admitted action may complete and AI may proceed
naturally to the next human input; an unanswered original input stays pending.
Do not use [ReplyPool.cancelAll][reply], which completes with null; the existing
[channelInactive path][disconnect] and null-sensitive question consumers remain
a separate unresolved problem, not a reconnect implementation to reuse.

Resync here is a read-only in-process request in the same live session, not a
resume/authentication protocol. One command authority per session; conflicting
peers, takeover, transport loss during a new synchronous question, session expiry,
process restart and persisted deduplication require later review. No silent TTL
decision: this bounded test has an explicit owner-controlled lifecycle. A test
deadline records failure and uses the existing test cleanup, never a client-facing
automatic concession policy. Production resource limits/abandonment are unresolved.

## Next slice, for approval: extend the existing test only

Add test-only nested DTO records, strict projector/admission helpers and a
`BrowserPeer` script to the existing [ProtocolGuiGameInProcessTest][test]. Reuse
HostedMatch, the #19 fixture/setup/oracle, private IRemote and original loop;
preserve all historical and #19 tests. No second gameplay harness, production
adapter or serializer/transport dependency. Byte-level parsing/codec safety is
not established by a Java DTO test. The scripted peer receives only DTOs
and submits choice IDs: it cannot access the fixture, Java controller or views.
Keep the Java assertion oracle and malicious-input injector separate from it.

Implement incrementally after approval:

1. Close C1 in a separately approved passive observation/review if existing
   evidence is insufficient; only then seek DTO implementation approval. Audit
   safe capture before any projection; stop on an unsupported boundary.
2. At settled checkpoints project initial state, targeting, payment, stack and
   post-resolution. Validate the DTO type graph recursively: only declared DTOs,
   primitives, bounded strings and immutable lists; no maps of arbitrary objects
   or shared mutable collections. Audit every field/reference against the viewer
   and known hidden-card oracle. Changing live Java state must not alter a prior DTO.
3. Script explicit Play/Keep, cast Bolt, choose opposing player, select Mountain
   for payment and pass offered priority until original resolution. No direct
   Java object lookup from the peer or predetermined damage application.
4. Before each valid action inject wrong session/match, stale revision/prompt,
   foreign/retired/unknown capability, wrong operation and hidden-identity attacks.
   The oracle may supply a known hidden engine ID only to the malicious injector;
   legitimate DTOs never disclose it. Reject it identically to other unknown IDs,
   without revealing whether a hidden card exists. Try opponent-hand/library and
   a formerly visible handle after revocation. Rejections make zero controller
   calls and preserve phase, life, stack, tap/pool/target state, input and latch at
   that parked checkpoint; they do not advance revision or wake the game.
5. Repeat a valid action before and after completion, and reuse its ID with a
   changed canonical field; require `actionIdConflict` and one dispatch only.
   Delay an admitted EDT runnable, revoke its handle/choice or supersede its
   generation on EDT in a controlled test of the admission helper, and
   prove the EDT recheck rejects it without calling Forge. Do not manufacture a
   gameplay phase change to test staleness. Resync must preserve the current
   prompt/ledger; an unanswered prompt must remain pending with no synthetic action.
6. Exercise missing/unsupported required replies and assert the exact atomic
   failure reaches the owning TestNG test before exception propagation can hide
   it. Run the bounded positive scenario in three fresh JVMs plus #19 human/AI,
   empty-hand/unanswered controls and historical regressions using approved
   offline tools/cache. Preserve logs and thread/capture evidence; no downloads.

**Acceptance:** original target is the opponent; before resolution exactly one
original Bolt is on stack with opponent life 20; one red mana is paid from the
manually selected tapped Mountain, proved from the original
`SpellAbility.getPayingMana()` list (size 1, RED, source card ID equal to that
actual Mountain) and `mountain.isTapped()` as in #19. DTO mana/pool counts are
secondary display evidence, never the payment oracle. Original resolution produces life 17, Bolt in
graveyard and empty stack. DTOs show that sequence and safe target/payment state.
Hidden identities cannot be discovered or selected. Invalid/stale actions cannot
advance the game, duplicates cannot dispatch twice, and required-prompt failures
are observable by the test owner. General synchronization, creatures, reconnect,
Safari, Adventure and production headless hosting remain unproven. The existing
[HeadlessGuiDesktop][headless] is test infrastructure, not a deployable service.

The blocking risks are capture ownership and synchronous ability authorization.
Further risks are prompt lifecycle/reentrant refreshes, visibility changes and
string/reference leaks, dispatch/result races, and upstream GUI/input API drift.
Only after the DTO proof passes should a separate review consider codecs,
authenticated transport, TLS/origin/CSRF policy, payload limits, backpressure and
actual iPhone connectivity. Tailscale reachability alone would not authorize a
viewer. No listener or browser transport is part of this plan.

[pv]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/player/PlayerView.java#L191
[gv]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/GameView.java#L65
[stack]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/spellability/StackItemView.java#L35
[payment]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/input/InputPayManaOfCostPayment.java#L21
[mana]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/input/InputPayMana.java#L131
[sa]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/spellability/SpellAbility.java#L558
[visibility]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/card/CardView.java#L622
[priority]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/input/InputPassPriority.java#L342
[target]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/input/InputSelectTargets.java#L70
[selection]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gui/interfaces/IGuiGame.java#L268
[controller]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/player/PlayerControllerHuman.java#L2818
[hosted]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/HostedMatch.java#L215
[proxy]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/input/InputProxy.java#L55
[queue]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/input/InputQueue.java#L95
[latch]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/input/InputSyncronizedBase.java#L22
[invoke]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/GameAction.java#L2554
[ai]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-ai/src/main/java/forge/ai/AiController.java#L1685
[ability]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/player/PlayerControllerHuman.java#L218
[protocol]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/net/ProtocolGuiGame.java#L39
[delta]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/net/server/DeltaSyncManager.java#L109
[reply]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/net/ReplyPool.java#L44
[disconnect]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/net/server/FServerManager.java#L1239
[test]: https://github.com/rwZephyr/forge-adventure-web/blob/0e6fb9009c1e6fe6e65c70e16fc6c35d1789d524/forge-gui-desktop/src/test/java/forge/net/embedded/ProtocolGuiGameInProcessTest.java#L260
[headless]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-desktop/src/test/java/forge/net/HeadlessGuiDesktop.java#L20
