# Extension plan: existing embedded Forge test

2026-10-18. **For review before implementation.** This narrows PR #17's next
step to extending `ProtocolGuiGameInProcessTest`; no second gameplay harness,
browser/JSON transport, networking, reconnect, dependency download, service
change or original gameplay modification is included.

Source pin: `db893dcdb14fdd85a05437ad458dc70ef704ca2e`. Read alongside
[Marvin's review](https://github.com/rwZephyr/forge-adventure-web/pull/17#issuecomment-6253094994).
Two review suggestions need correction: an immediate scripted `sendAndWait`
reply may legitimately execute on the game thread, and private hidden CardViews
inside an in-process event are not themselves a test failure. The checks must
distinguish calling-thread replies from dispatched actions, and private objects
from an explicitly limited disclosure contract.

## Existing baseline: source behavior versus actual execution

[The existing test][test] already constructs genuine HostedMatch,
ProtocolGuiGame(IRemote), LobbyPlayerHuman/PlayerControllerHuman and an AI.
It uses TestUtils/FModel, disables actionable highlights and replay recording,
and seeds the original random provider with `Random(7)` for this test.

The source-defined scenario is 40 Drifting Meadows versus 40 Plains. Its peer
receives full state/deltas/raw GameEvents, answers blocking choices immediately,
selects one land through IGameController on the EDT, passes priority, then
concedes at turn >= 3. Assertions cover one land on the battlefield, continued
state flow, a getAbilityToPlay question, unwrapped events and game-over. The
transport check compares a selected class list before/after and rejects newly
loaded transport/serialization classes; it is not a universal proof of no
network activity. The test logs blocking thread names but does not assert their
identities, Bolt targeting, payment, resolution or an AI FutureTask.

`RecordingRemote.sendAndWait` currently handles ability/choice/confirmation/
dialog methods, chooses generic defaults in several branches and returns null
for other methods. The client loop also answers unrecognized button prompts
with OK. Preserve the historical baseline method; new Bolt cases need a strict
script rather than these generic fallbacks. Its RecordingRemote is also reused
by [ProtocolGuiGameSingleThreadTest][single], so changes must preserve that
existing test's default behavior and separately rerun it.

**Actual attempt on Silent:** one offline, unchanged-source Maven invocation
failed before the desktop module's compile/test phases. Parent, core, game, AI
and shared GUI succeeded; desktop plugin resolution failed on uncached
`org.codehaus.gmaven:groovy-maven-plugin:2.1.1`. Exit 1, Maven elapsed 33.786 s.
**The selected test did not execute; no local baseline pass is claimed.**
[Complete baseline log](../experiments/evidence/embedded-baseline-20261018/maven-baseline.log),
UTF-8/LF copy, SHA-256 `44a5f5f7971bb371810780b5a5eff0e05a9df2924b9957393f95421c3e0f0c8f`. Raw output remains under
`.local-tools/embedded-baseline-20261018/maven-baseline.log`.

Exact Maven arguments from repository root, existing portable Maven/JDK 17:

```powershell
& "$repo/.local-tools/apache-maven-3.9.16/bin/mvn.cmd" -o -B -ntp -T1 `
  "-Dmaven.repo.local=$repo/.local-tools/m2" -pl forge-gui-desktop -am `
  '-Dtest=forge.net.embedded.ProtocolGuiGameInProcessTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' '-DfailIfNoTests=false' test
```

Here `$repo` denotes the absolute checkout path. Process-only environment:
JAVA_HOME=`C:/Program Files/Java/jdk-17`; JAVA_TOOL_OPTIONS contains
`-Duser.home=<repo>/.local-tools/embedded-baseline-20261018/home`,
`-Djava.io.tmpdir=<repo>/.local-tools/embedded-baseline-20261018/tmp`,
`-Djava.awt.headless=true -Xmx1536m`; APPDATA/LOCALAPPDATA point to sibling
`appdata`/`localappdata`, TEMP/TMP to `tmp`. Directories were created locally;
the original environment was restored. No profile override files exist in the
asset locations inspected. No install/package/GUI/server command ran, and no
POM/plugin bypass or online retry followed the cache failure. A dependency
acquisition, including transitive/missing desktop artifacts, needs separate
inventory and approval before fetching anything.

## Exact implementation footprint proposed

1. Extend **the existing test file** with human-Bolt, AI-Bolt, empty-hand and
   required-answer-failure cases; add small nested script/observation helpers to
   its RecordingRemote. Reuse its HostedMatch/ProtocolGuiGame/IRemote setup.
   Keep legacy behavior for the original method and SingleThreadTest; strict
   scripts are opt-in for the new cases. No new application main or match runner.
2. Reuse the already available passive [JdiWitness][witness], adding only an
   opt-in mode that launches the **same TestNG test methods**, rather than its
   historical standalone fixture. Retain historical modes. Installed JDK JDI
   `dt_shmem` only, no socket fallback, target method invocation, bytecode/source
   instrumentation or new dependency. It observes the test; it owns no game
   setup or decisions. A thread name or cast event alone cannot prove a
   FutureTask/timed-get result.
   Launch from the same desktop module working directory and isolated profile
   as Surefire so GuiDesktop resolves the original asset directory correctly.
   The opt-in test-process watchdog is 210 seconds (the existing 180-second
   test bound plus 30 seconds for reporting/cleanup), not the old small-fixture
   observer's 15 seconds. This changes only the observer's external guard;
   original AI deadlines remain unchanged. Record full FModel startup time.
3. Documentation/evidence only beyond those two files. Leave TestUtils,
   HeadlessGuiDesktop, ProtocolGuiGame, IRemote, input classes, ThreadUtil,
   PlayerControllerHuman, AI, game/cards, POMs and all runtime code unchanged.
   If those limits prevent execution or reliable observation, stop and report
   the exact boundary rather than add an adapter.

Run cases sequentially in isolated JVMs/profiles. Snapshot/restore mutable
test preferences and MyRandom around added cases; do not save preferences.
Retain the existing deterministic seed policy and report it as test setup,
distinct from PR #15's original SecureRandom fixture. Preserve the configured
HostedMatch AI timeout (default five seconds), assert non-simulation AI mode,
and do not add or lengthen deadlines to make a trace pass.

## Minimal fixture and action additions

Use original cards loaded by FModel from the unchanged Bolt/Mountain scripts.
Give both RegisteredPlayers nonempty 40-card decks; TestUtils already disables
deck legality for tests. At existing [HostedMatch.setStartGameHook][hook], after
normal startup decisions and before [PhaseHandler.mainGameLoop][loop], arrange
the small position using cards actually created by the match and original zone
movement APIs. This is test setup only: no synthetic SpellAbility, selected
target, prepaid mana or pre-applied damage. Record card/player IDs and original
script hashes. Resolve players by RegisteredPlayer identity, not list order.

| Case | Position at hook | Script and assertions |
| --- | --- | --- |
| Human Bolt | Human life 20, one Bolt in hand, one untapped Mountain on battlefield, empty pool/stack; AI life 20, a Mountain in hand, no spells; both libraries nonempty. Set human MAIN1/priority using existing fixture APIs; refresh original last-state snapshot. | Select Bolt through `gui.getGameController(owner).selectCard`. Answer the exact offered Bolt ability. Observe original InputSelectTargets, then `selectPlayer(aiView)`; do not call `targets.add`. Observe InputPayManaOfCostPayment and select the Mountain through the same controller. Pass only at InputPassPriority. Assert original targeting/cost path, target identity, one R paid by that Mountain, tap, one Bolt stack item while AI life is still 20; then phase-loop resolution to 17, Bolt graveyard, empty stack. Concede only for cleanup after assertions; that concession is not the success criterion. |
| AI Bolt | Human life 3, a Mountain in hand and no spells; AI life 20, two Bolts in hand and two untapped Mountains; empty pools/stack, nonempty libraries; AI MAIN1/priority. | Human script supplies original startup decisions and priority passes only. Require original PlayerControllerAi → AiController task decision, opponent target, genuine payment/cast, observed stack state with human life still 3, then loop-driven resolution to 0 and GameOutcome naming AI as winner. No direct brain/ComputerUtil/resolveStack calls from the test. |
| AI empty hand | Same AI setup but no Bolts in its deck or hand; two Mountains still on battlefield, nonempty libraries. | Stop at the first subsequent settled human priority input. No spell cast, payment, damage or tapped Mountains. Allow original mana candidates and a real evaluator task returning null, as PR #15 established; do not assert zero tasks or filter the candidate list. Then cleanup. |

Arrange remaining cards in libraries rather than leaving random opening-hand
spells or accidentally emptying a library. The hook is the only controlled
state construction point. After it, all progress uses the original loop and
player decisions. Do not turn this into a turn simulator or a second fixture
executable.

**Payment detail:** [InputPayMana.onOk][pay] means **Auto**, calls
ComputerUtilMana and temporarily uses PlayerControllerAi. The human test must
explicitly click Mountain. Original payment may complete automatically when
the cost is satisfied; do not require an extra OK that could pass the next
priority prompt. Likewise one legal Bolt target may finish target input without
another OK. Identify actual input objects/classes and enabled buttons rather
than advancing a fixed list of blind clicks or matching only localized text.

Capture immutable primitive assertions at original event/input synchronization
points, including before the next pass can resolve the stack. Keep direct
Game/Card/SpellAbility inspection private to the test oracle. Recorded raw
events/views contain mutable references; reading their old values later would
not prove a pre-resolution state. Event capture must not resolve or mutate the
game. Propagate worker/EDT failures to the owning TestNG test.

## Thread and original-path acceptance

Record Thread ID/name, Swing EDT flag, scenario, protocol method, input identity
and call/reply/action phase. Thread IDs and actual call stacks are decisive:
`ThreadUtil.isGameThread` only checks the `Game` prefix and also matches
`Game AI Eval`, so it cannot distinguish those threads by itself.

| Observation | Required assertion |
| --- | --- |
| Start hook / loop / human cast | Real game execution, not Swing EDT. Passive witness at `PlayerControllerHuman.playChosenSpellAbility:1782` has PhaseHandler.mainLoopStep ancestry. Witness original target input and CostPayment/payMana path for the same Bolt. |
| Target/payment/priority prompt display | Original InputProxy schedules initial display via FThreads onto Swing EDT. Assert EDT at the relevant showPromptMessage/updateButtons callbacks, correlating them to the current input. Do not assert every protocol event is EDT: initial state/openView and game events have other callers. |
| Controller actions | Inside every dispatched selectCard/selectPlayer/selectButtonOk/concede lambda, assert Swing EDT and ID distinct from the waiting loop thread and evaluator. Schedule via existing FThreads.invokeInEdtLater; never enqueue behind the game's latch wait. |
| Immediate synchronous replies | Capture entry/return of sendAndWait and assert the reply is produced immediately on that **same calling thread**, with no wait for another queued EDT action. Ability selection from InputPassPriority.onCardSelected can enter on EDT; questions from original game code can enter on the game thread. A blanket “no replies on game thread” assertion would be wrong. |
| Game input wait | Passive witness at InputSyncronizedBase.awaitLatchRelease captures the original input and game-side wait; an answering controller action uses the matching current input. Normal completion releases the original latch and the same game proceeds. Do not replace awaitInput or pump it inline; SingleThreadTest covers that separate topology. |
| AI task | Witness caller stack PhaseHandler → PlayerControllerAi → AiController; FutureTask at 1685, worker entry at 1687, thread creation/start at 1777, timed get at 1782 and NORMAL result at 1819. Match future/ability/card identities across frames and cast. Evaluator ID differs from caller/EDT. Witness original ComputerUtil.handlePlayingSpellAbility payment, then PhaseHandler's resolveStack path; no surviving evaluator. |

[InputProxy][proxy], [InputPassPriority][priority] and [ProtocolGuiGame][protocol]
also expose a contract wrinkle: a selectCard action on EDT calls
getAbilityToPlay → syncAndSendAndWait, whose documentation says graph walks are
game-thread-only. The existing test does that while the game waits for input.
Record that actual caller/latch relationship; do not invent a universal
game-thread assertion or silently move dispatch to satisfy the comment. If a
real race or inconsistent state is observed, report it as a blocker for later
review. This slice does not rewrite synchronization or certify concurrent graph
access as generally safe.

Use three fresh **unpaused** successful JVM runs per positive scenario plus
the empty-hand control, and separate passive-witness runs. Debugger timing is
not a performance result. A missing frame/site, timeout, abnormal FutureTask
result, mismatched ability or failed observed run is a failure/inconclusive
trace, never substituted with a thread-name log. Timeout/cancel/interrupt/stop
semantics remain outside the demonstrated happy path.

## Java-side visibility regression, without a browser bridge

At settled initial/pre-resolution/post-resolution checkpoints, inspect the
captured full GameView and relevant state deltas as **private in-process data**.
Prove the raw graph contains actual AI hand/library CardViews and names even
though [CardView.canBeShownTo][visibility] denies their faces to the human.
This is an expected finding, not a failed embargo on private Java objects.

Add a test-only, Java-side identity-disclosure assertion helper in the same
test: enumerate the fixture's hand/library/battlefield/stack/graveyard cards,
require a nonempty actual human-viewer set, and use original canBeShownTo plus
canFaceDownBeShownTo for face decisions. Its output for assertions is only an
immutable set of allowed card face IDs; it is **not** a DTO/JSON serializer or a
production projector. Assert against independently specified fixture truth:
own hand and face-up public cards allowed; AI hand and both libraries' faces
denied absent explicit permission; Bolt becomes public on stack/graveyard.
Match IDs rather than searching strings globally, since hidden and visible
cards can have the same name. Hidden zone counts may be public; card identities,
alternate faces and private links are not automatically safe because an ID is
primitive. Do not export raw IDs of hidden cards as a browser contract.

Negative regression: deliberately submit the known hidden AI-card face ID to
the test-only disclosure validator and require rejection; visible Bolt passes.
Raw full state/deltas must remain classified private. This proves only the
small identity-disclosure contract, **not that arbitrary GameView/DeltaPacket,
events, strings or prompt data are browser-safe**. Reveal/temporary-look,
face-down/alternate-state and general prompt projection need later systematic
coverage; do not add a production projection in this slice. Never use an empty
viewer list: canBeShownToAny treats it as unrestricted.

## Required unanswered prompts must fail visibly

New strict scripts allow only explicitly understood input classes and blocking
methods with validated offered choices/counts. Do not use the baseline's
`default -> null`, “first item”, caller-default confirmation or generic OK as
fallbacks. Record every question and exact chosen response. Unexpected input,
missing matching ability, disabled required action, malformed reply or elapsed
question bound records an atomic failure **before** throwing, including method,
input ID/class and thread. The TestNG owner checks that failure after every
wait; it must not disappear as an EDT/background exception or become a later
apparently successful concession. Positive cases require zero unanswered calls
and `gui.isPaused()==false` throughout.

First enumerate actual startup/mulligan/play-draw questions when the unchanged
baseline can run. Give each an explicit scenario answer using offered original
objects and check its cardinality. An unexpected startup prompt blocks the
case for inspection; the fixture hook does not bypass startup or justify a
generic “accept defaults” responder.

Add one negative control using the same human-Bolt match: deliberately omit
the answer for its real getAbilityToPlay request. Assert explicit unanswered-
prompt failure reaches the test owner, no cast/payment/damage is credited, and
the fixture is boundedly cleaned up. The control passes by **detecting failure**;
it does not treat null/cancel as a valid answer. Use original concession/input
release for cleanup, preserve the failure first, and keep an outer process
watchdog that can terminate only its own test JVM. Do not shut down global
executors or alter original latch/AI logic. Retain the existing 180-second test
bound; shorter scripted waits need an error identifying the stuck input.

## Separate known problem: null replies and reconnect

[ReplyPool.cancelAll][replies] completes pending questions with null.
[FServerManager][disconnect] calls it on channelInactive; its AFK comment limits
safe cancellation to particular priority subprompts. That limitation does not
make every disconnect question null-safe. [PlayerControllerHuman:732][null-loop]
iterates the getChoices result without a null guard. ProtocolGuiGame's paused
send helpers return null, and confirm/option wrappers may choose a default.
The strict in-process peer neither pauses nor cancels replies, so its explicit
failure policy **does not fix or test these production paths**.

PR #17's earlier “disconnect/reconnect recovers the same pending prompt” target
is withdrawn from this slice and unproven on current machinery. A future design
must choose prompt preservation/re-delivery versus an owner-approved settlement
policy; no reconnect code, null-safety patch, automatic pass/concede policy or
ReplyPool change is proposed here.

## Existing test-only headless platform and stop conditions

[HeadlessGuiDesktop][headless] is already in the **test tree** and extends
Swing-based GuiDesktop. [TestUtils][utils] installs it, initializes original
FModel/card/type/localization data and test preferences. It suppresses audio,
returns -1 for option dialogs, supplies input defaults, logs bug reports and
returns HeadlessNetworkGuiGame when asked for a new GUI. This test instead
injects ProtocolGuiGame explicitly. EDT methods are inherited from
[GuiDesktop][desktop], including SwingUtilities.invokeLater/isEventDispatchThread.

Reuse it unchanged. Its dialog defaults/no-ops, static state, full card bootstrap
and Swing dependency are test facilities, not proof of a production headless
service, resource isolation or authentic Adventure bootstrap. Strict peer
answers cover required game decisions through the injected protocol; any
required decision that unexpectedly escapes to a headless platform default is
a blocker to report, not successful human input.

Approval would cover only the two source-file extensions and assertions above.
First prerequisite is an actually executed unchanged baseline once dependencies
are available under separately approved acquisition. Then require baseline and
SingleThreadTest regression passes, three fresh human/AI successes, empty-hand
and unanswered-prompt controls, thread/path witnesses, visibility assertions,
complete failure logs and no leftover fixture/evaluator. No Adventure, Safari,
transport, reconnect, multi-session or production-service claims follow.

[test]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-desktop/src/test/java/forge/net/embedded/ProtocolGuiGameInProcessTest.java#L54
[single]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-desktop/src/test/java/forge/net/embedded/ProtocolGuiGameSingleThreadTest.java#L37
[witness]: ../../experiments/ai-teavm/src/observer/java/probe/JdiWitness.java
[hook]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/HostedMatch.java#L68
[loop]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/phase/PhaseHandler.java#L1014
[pay]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/input/InputPayMana.java#L376
[proxy]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/input/InputProxy.java#L55
[priority]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/input/InputPassPriority.java#L342
[protocol]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/net/ProtocolGuiGame.java#L181
[visibility]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/card/CardView.java#L628
[replies]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/net/ReplyPool.java#L46
[disconnect]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/net/server/FServerManager.java#L1239
[null-loop]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/player/PlayerControllerHuman.java#L732
[headless]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-desktop/src/test/java/forge/net/HeadlessGuiDesktop.java#L20
[utils]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-desktop/src/test/java/forge/net/TestUtils.java#L41
[desktop]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-desktop/src/main/java/forge/GuiDesktop.java#L104
