# Browser runtime architecture comparison

2026-10-18. **Design only; no architecture has been implemented.**

**Review update:** the next slice is now the [extension plan for the existing
ProtocolGuiGameInProcessTest](embedded-protocol-test-scope.md), rather than a new
harness or browser bridge. It already uses HostedMatch/ProtocolGuiGame/IRemote
with test-only HeadlessGuiDesktop. The unchanged local baseline attempt is
blocked by an uncached desktop Maven plugin; no test pass is claimed. Browser
transport remains a later, unapproved step. The earlier reconnect acceptance
target is withdrawn because current null-reply behavior cannot guarantee
preservation of a pending prompt; that problem is documented separately in the
extension plan.

## Recommendation and evidence

Prefer a **native browser interface backed by the original JVM Forge engine**
for the next bounded experiment. Keep browser-only TeaVM as a research option,
and pause compatibility shims until this choice is reviewed. This recommendation
is conditional on proving the human-input bridge below; it is not a claim that
Adventure is already a headless service.

The evidence favors moving presentation across a boundary that Forge already
has, rather than moving its Java runtime into the browser:

- [Stage B actual results](../../experiments/ai-teavm/RESULTS.md) establish three
  fresh JVM Bolt decisions, genuine target selection, R payment, casting and
  resolution, plus a separate negative control. Passive JDI witnesses establish
  FutureTask, a distinct `Game AI Eval` thread and timed-get result identity.
  They do **not** establish the full match loop, human-input bridge, timeout
  cancellation, remote transport or campaign lifecycle.
- The unchanged TeaVM 0.15.0 attempt failed with **93 severe diagnostics**.
  [Complete output](../../experiments/ai-teavm/evidence/browserOriginal.log) and
  [indexed call chains](../../experiments/ai-teavm/evidence/compiler-diagnostics.json)
  include file/type bootstrap, FutureTask, Thread.stop, localization classloading
  and dependency concurrency/reflection APIs. Static reachability is not proof
  of execution in the fixture. The number of diagnostics is not the number of
  independent fixes, but fixing FutureTask cannot close these other boundaries.
- [Actual Safari evidence](../experiments/safari-session-results.md) establishes
  a genuine SpriteGroup rendering/touch slice and the Stage A factory variants.
  It does not establish full Adventure rendering, rules execution or saves.
- [Claude's review, PR #16](https://github.com/rwZephyr/forge-adventure-web/pull/16)
  proposes a narrow concurrency seam but predates the actual Stage B compile.
  [Marvin's assessment](https://github.com/rwZephyr/forge-adventure-web/pull/15#issuecomment-6252604312)
  groups the later diagnostics into broader gaps. Both are read-only reviews,
  not new runs; their estimates of fix size and removable logging diagnostics
  remain hypotheses. This recommendation rests on the actual evidence and
  original source boundaries, not those estimates.

Original source links below pin **`db893dcdb14fdd85a05437ad458dc70ef704ca2e`**.
All five inspected core/game/AI/shared-GUI/mobile module trees match that pin
in the PR #15 parent (`fcd1ed00aa5e4a2d6711c8963323d81b35fbf889`). This task
read source and preserved evidence; it ran no builds, game, browser or server.

## Comparison

| Concern | Browser-only TeaVM | Native browser + authoritative JVM |
| --- | --- | --- |
| Rules, cards, AI | Compile originals and dependencies into JS; constructor metadata plus substantial Java API compatibility work remain. | Original core/game/AI and card scripts execute on JDK 17; demonstrated fixture is the starting point. |
| Concurrency | Must establish real task/deadline/cancellation semantics and responsive Safari execution; successful compilation alone is insufficient. | Retain original game thread, evaluator thread, FutureTask and timed get; browser only waits for engine prompts/state. Full GUI handoff still needs proof. |
| UI | Potential reuse of Adventure libGDX rendering through gdx-TeaVM; only SpriteGroup is proven. | Native DOM/canvas/WebGL duel UI consumes projections. Selective libGDX/TeaVM rendering reuse remains possible without compiling rules/AI into it. |
| Adventure | Whole reachable scene/world/content/save graph needs browser support. | Campaign logic/content should remain authoritative Java too, but scene and graphics coupling prevents assuming a ready server API. Requires a separately reviewed boundary. |
| Persistence | Java serialization, files, Pixmap and browser storage need an explicit bridge. | Original save format can stay Java-owned, but isolating paths, scene dependencies and crash consistency remain work. |
| Operation | Static hosting/offline potential; download, memory and mobile CPU costs unmeasured. | Requires live JVM and connectivity; latency, process memory and private hosting must be measured. No offline play promised. |
| Upstream maintenance | Maintain compiler metadata, class-library/platform adaptations and behavioral parity across dependency upgrades. | Maintain prompt/view schema and platform/campaign adapters; engine and AI stay on their intended runtime. Neither option is maintenance-free. |

## What the original Adventure flow actually owns

| Boundary | Verified source and behavior | Architectural implication |
| --- | --- | --- |
| Encounter entry | [WorldStage:136][world-stage] initializes DuelScene, schedules scene transition and autosaves at 144. [MapStage:1183][map-duel] records `currentMob`, freezes controls, then initializes DuelScene and passes the dungeon effect. | The transition carries a real enemy, selected campaign deck, effects and return context. A browser must not invent enemy decks or rewards. The inspected map entry has no equivalent autosave call; do not assume every duel entry is checkpointed identically. |
| Match construction | [DuelScene:793][duel-init] copies the selected deck (or event deck). [enter:365][duel-enter] builds RegisteredPlayers, starting life/shards, items/blessings/dungeon effects, enemy-generated decks, teams and variants; maps the human to `MatchController.instance` and starts HostedMatch at 564. | A generic Constructed match is not an Adventure encounter. Keep this assembly policy on Java; exposing it without scene/texture dependencies is a future reviewed seam. |
| Duel state | [Game:322][game] constructs players, GameAction, stack and phase machinery. [GameView:36][game-view] retains transient Game/Match references and trackable presentation data. [HostedMatch:215][hosted] attaches each human controller to its GUI and event handling. | Game/Match own duel truth. GameView is a presentation graph, not a separate simulation or automatically safe browser JSON. |
| Completion | [mobile MatchController:412][finish] calls DuelScene.GameEnd when match-over and the disableWinLose setting applies. [DuelScene:144][duel-end] reads the **match winner**, applies shards/ante, clears blessing, then schedules return to the previous `IAfterMatch` scene at 242. | Match completion and individual game completion differ. Do not infer a campaign win from a browser animation or one lethal game in a multi-game match. Other win/lose-screen paths also need coverage before general Adventure support. |
| Rewards/progression | [WorldStage:171][world-result] calls `win`, generates enemy loot, removes enemy and updates quests through animation callbacks. [MapStage:844][map-result] has distinct win/loss handling; [getReward:1001][map-reward] updates POI deletion/respawn/dialog state. [RewardScene:186][reward] credits loot through `clearGenerated`; [AdventurePlayer:1089][player-result] applies defeat penalties, while win adds a shard. | Campaign effects are distributed across scenes, timers, actors and player methods. Calling only `AdventurePlayer.win()` or copying reward formulas would omit genuine mechanics. Loot credit is not simply immediate at duel end. |
| Save/load | [Current:18][current] resolves the static WorldSave's player/world. [WorldSave:73][save-load] inflates Java objects, restores player/world/POI changes and WorldStage, then emits load signals. [save:174][save-write] writes player/world/stage/POI data with backup handling and updates Config. | There is one global campaign context, not a multi-tenant engine. Preserve Java ownership; isolate one disposable process/profile for a first experiment. No user save should be loaded or written by a probe. |
| Graphics in persistence | [World:103][world-save] loads a Pixmap and serialized world data; [WorldStage:476][stage-save] saves live enemy actor positions/timers. [WorldSaveHeader:20][save-header] serializes a Pixmap preview; saving posts Gdx UI work. | Neither the campaign model nor save service is currently graphics-independent. A headless port cannot be declared complete by omitting these fields or replacing save routines. |

The inspected save payload contains campaign/world data, not an active Game,
FutureTask, pending human-input latch or duel stack. A browser reconnect to a
**live** JVM session can be designed; resuming a duel after JVM restart is a
different, unproven persistence feature. Campaign life is not simply assigned
the duel's final life: the inspected return path uses explicit campaign win/loss
and shard/ante policies.

## Smallest practical browser integration point

Use the existing pair **`IGuiGame` out / `IGameController` in**, retaining
`PlayerControllerHuman` and its input machinery on the JVM. There is a concrete
candidate for an additive bridge: unchanged [ProtocolGuiGame:37][protocol-gui]
already accepts an [IRemote][remote] embedding peer, sends GUI calls and state,
and blocks for replies. HostedMatch recognizes ProtocolGuiGame and registers
[GameEventForwarder][forwarder] for both game events and input-queue changes.
This is a stronger starting point than a new engine-specific `castBolt` API.

Proposed flow, **not implemented**:

```mermaid
sequenceDiagram
    participant B as Native browser UI
    participant X as JVM browser bridge
    participant H as Original human input/controller
    participant G as Original game/AI
    G->>H: Priority or target/payment decision
    H->>X: IGuiGame prompt and view update
    X->>B: Versioned visible state + prompt ID
    B->>X: Action ID + prompt ID + entity/choice ID
    X->>H: Allowed IGameController action or dialog reply
    H->>G: Validated choice; release original wait
    G->>G: Original cost, target, stack and AI processing
    G->>X: Settled events/view at synchronization point
    X->>B: New revision and pending decision
```

The actual action path is [IGameController][controller] →
[PlayerControllerHuman:2818][human-actions] → [InputProxy:99][input-proxy], which
resolves server CardView/PlayerView IDs back to real cards/players and dispatches
to the current input. Human priority uses [InputPassPriority][priority] through
[chooseSpellAbilityToPlay:1703][human-priority]. Casting remains
`PlayerControllerHuman.playChosenSpellAbility` → [PlaySpellAbility:80][play].
Human costs use its original HumanCostDecision and mana-payment entry at 2709.
Targeting remains [TargetSelection:181][target-selection] →
[InputSelectTargets:338][targets], including `sa.canTarget` and restriction
checks. Sending a selected player does not grant the browser authority to apply
damage, alter mana or construct a SpellAbility.

Not every decision is a click. [IGuiGame][gui] also asks synchronous ability,
choice, order, confirmation and damage-allocation questions. ProtocolGuiGame's
`syncAndSendAndWait` sends current views before blocking; [GameProtocolSender][sender]
uses IRemote's correlated reply. The bridge should retain choice objects on
Java and send opaque choice IDs and server-generated labels, never executable
`FSerializableFunction` objects. Validate replies against that pending question.
Unsupported prompts must fail visibly and stop the experiment; no guessed
defaults or automatic decisions that replace human play.

The existing Netty [GameProtocolHandler][handler] and [object encoder][encoder]
demonstrate dispatch/replies, but their Java serialization and reflective
ProtocolMethod dispatch are **not a browser protocol**. Proposed additive
bridge: consume ProtocolGuiGame events in-process through IRemote; translate an
explicit allowlist into JSON DTOs; map browser decisions to original controller
methods/server-side objects. Keep the Java networking codec off the browser
boundary. Do not expose cheats, macros or arbitrary reflected methods.

### Synchronization and ownership contract to prove

- Keep original game execution and GUI/input dispatch distinct. HostedMatch
  starts the match via GameAction.invoke; [GameAction:2554][game-action] routes
  through ThreadUtil. [InputSyncronizedBase][input-wait] waits on a latch through
  `IGuiGame.awaitInput`; InputProxy schedules prompt display through
  [FThreads/GuiBase][fthreads]. The bridge needs a live input/GUI dispatcher while
  the game is blocked. Queuing the reply solely behind that blocked game task
  can deadlock. Do not replace these waits or run AI inline.
- [PhaseHandler:1065][phase] invokes the player's controller, then its original
  `playChosenSpellAbility`; [PlayerControllerAi:849][ai-controller] delegates
  to AiController. Preserve [AiController:1685–1819][ai-task], FutureTask,
  `Game AI Eval`, timed get and cleanup. Browser connection latency must not
  become part of the AI evaluator's deadline.
  Full-loop AI casting uses `PlayerControllerAi.playChosenSpellAbility` →
  [ComputerUtil.handlePlayingSpellAbility:83][ai-play] → original CostPayment
  and `addAndUnfreeze`; PR #15 instead called the original `playStack` helper
  after choosing. The proposed proof must witness this actual loop path too.
- Copy immutable browser DTOs at established settled view/input points;
  ProtocolGuiGame explicitly requires game-thread graph synchronization. Do not
  walk mutable Game/View graphs on a network worker or retain them for later
  serialization. A separate UI-dispatched prompt must use an already captured
  view revision; unresolved ordering must be measured and fixed at the bridge.
- Envelope: session/game generation, monotonically increasing state revision,
  pending prompt ID and unique action ID. Reject stale prompt/game IDs and
  duplicates before dispatch. Acknowledge the resulting revision; reconnect
  sends a full current snapshot and pending prompt, never blindly replays casts.
  Start with full small-fixture snapshots; porting Java trackable deltas is not
  necessary for the first proof.
- Project visible information **on Java**. [CardView:628][visibility] defines
  zone visibility, separate face-down visibility and explicit look permissions.
  Raw GameView/DeltaPacket transmission could expose hidden cards. Preserve
  concealed counts/opaque handles, reveal permissions and alternate-face rules;
  do not trust CSS hiding. IDs, legal candidates and prompt data require the
  same filtering. Original legality checks remain authoritative after routing.

## Placement of code and content

| JVM authority | Browser-native presentation |
| --- | --- |
| Original core/game/AI, all factories/effects, CardRules/PaperCard/CardFactory, deck generation, costs, targeting validation, random state, priority, stack, triggers/replacements, victory and AI threads. Original localization/type data and Java dependencies. | Touch controls, layout, card/zone/stack display, prompts and animations. IDs and server-provided state/choices; no client implementation of legality, AI or effects. |
| Eventually original AdventurePlayer, World/POI changes, encounter/deck/effect assembly, quests, loot generation/application and save lifecycle in one isolated campaign context. Scene/platform extraction remains a separate review gate. | Original Adventure assets/maps/dialog presentation; optional selective gdx-TeaVM rendering reuse. World movement prediction can be visual only; collisions, encounters and progression must use authoritative original behavior. Do not grow a second Adventure simulation. |
| Browser bridge/session bookkeeping and visible-state projection. No accounts/multiplayer in the first slice. | Client schema handling, reconnect UI and asset caching keyed to the same pinned content version. Saves remain JVM-owned for this architecture. |

This separates presentation, not Forge mechanics or content. Retain original
attribution/licenses; the existing dependency license inventory is not blanket
clearance for distributing artwork, card images or new transport dependencies.

## First measurable end-to-end proof: browser ↔ original duel

Propose one isolated JVM host, one browser and a fixed, tiny Bolt/Mountain duel
UI. Reuse the original scripts and audited JVM setup from PR #15. A controlled
initial position is test setup, not game logic. Exercise HostedMatch and the
real phase loop instead of calling the brain or resolving the stack from a
browser endpoint. [PhaseHandler:1014][phase-setup] provides an existing
start-game hook before its main loop; use that hook for the documented fixture
position, with nonempty libraries. Handle any preceding startup/mulligan/first
player prompts through original decisions rather than silently skipping them.

Two fresh scenarios keep the proof small and assertions deterministic:

1. **Human path:** human has Bolt and an untapped Mountain; AI has Mountains
   and no spells, life 20. Browser selects Bolt, answers original target prompt
   with the AI player, pays R through the original payment UI, and passes when
   prompted. Prove original human casting, Mountain tap/payment, stack entry,
   then resolution to AI life 17, graveyard Bolt and empty stack. Capture a
   pre-resolution revision with life still 20. No endpoint calls DamageDealEffect
   or MagicStack.resolveStack directly. Stop the test at the settled assertion;
   this is not a complete campaign or win.
2. **AI path:** fresh original AI fixture, human life 3, AI has two Mountains and
   two Bolts, nonempty libraries, AI active in MAIN1. Browser renders the AI
   stack item and supplies only genuine human priority passes. Prove game
   thread → original PlayerControllerAi/AiController → distinct Game AI Eval →
   FutureTask/timed get → original target/payment/cast → phase-loop resolution
   and genuine GameOutcome. This adds the topology PR #15 did not test. Repeat
   with AI empty hand: expect no spell, payment or damage, while allowing the
   original mana candidates and evaluator task observed in Stage B.

Acceptance: three fresh successful runs of each positive scenario; separate
negative; browser final projection agrees with Java assertions at every named
revision; exact original path witnesses, no bypasses; no hidden AI hand/library
identities in captured payloads; stale/duplicate actions cannot double-cast or
pay twice. Reconnect is excluded pending a separate prompt/null-reply design;
the existing cancellation path does not preserve every pending prompt.
Record startup/memory/payload sizes and browser
action-to-settled-state timings; use a provisional foreground loopback target
of 500 ms for a simple human action excluding deliberation/AI time. Failure of
that target is reported, not hidden by animation. Repeat on actual iPhone Safari
only after an approved private arrangement. Timeout/cancel semantics remain
separately unproven unless explicitly exercised.

Before any listener: first test IRemote event capture and scripted replies
in-process. Then request approval for exact temporary transport/listening scope
and shutdown. Prefer an isolated additive browser target and reuse existing JVM
GUI-platform dispatch/bootstrap initially (it may still require a local desktop
runtime). Existing HeadlessGuiDesktop in the test tree is a starting point for
the in-process proof, with inherited Swing EDT and test dialog defaults;
production headless hosting remains unproven. The browser would draw its own
interface; no framebuffer/video/input streaming. **No new headless GuiBase
implementation or production promotion of the test implementation is approved.**
If original bootstrap cannot run without UI side effects or unexpectedly asks
unhandled prompts, stop and report that boundary rather than stub it.

### Separate Adventure acceptance gate

After the duel bridge passes, design one real encounter from a disposable
campaign: original selected deck/enemy/effects → original match → original
return/quest/loot application → original save/load round trip in an isolated
profile. Require enemy/POI state, shards, blessing, ante when applicable, loot
credit and quest effects to occur once across disconnect/retry. A browser
"return" button or fabricated reward is not a pass. Preserve original content;
do not call a copied `setWinner` algorithm or substitute a mock overworld.

This gate may need reviewed extraction of semantic work from DuelScene,
MapStage/WorldStage and RewardScene, plus a graphics/platform/save-preview
boundary. An alternative is retaining the original libGDX runtime within the
JVM host initially. Neither is demonstrated. Identify the exact minimal edits
and original call chains first; do not begin broad headless Adventure work as
part of the duel proof. Save migration, durable mid-duel resume and accounts
remain outside it.

## Risks, review boundaries and reversal criteria

| Risk | Consequence / containment |
| --- | --- |
| GUI platform and globals | HostedMatch uses FModel, GuiBase, sound/haptics and preferences; Adventure uses Current/WorldSave/scene singletons and Gdx. One process/profile per experiment avoids pretending these are tenant-safe. Headless bootstrap and resource cost are unproven. |
| Prompt surface | Targeting is only one of many choices: X, optional/additional costs, modes, combat allocation, ordering, mulligans, replacement decisions and reveals. Cover a tiny real subset first; unknown prompts fail visibly. Full card coverage demands a systematic prompt inventory later. |
| Concurrent waits and mutable views | Wrong dispatch order can deadlock, send old targets, or serialize an inconsistent graph. Audit thread identities and capture revisions at the existing input/event boundaries; retain original waits and AI deadlines. |
| Network delay/mobile suspension | Browser may disappear while JVM waits or continues AI. Separate session connectivity from engine state; explicitly specify waiting/reconnect/abandon behavior. No automatic AI takeover or server timeout concession in the first proof. |
| Campaign commit and saves | Return callbacks, reward credit, POI removal and save writes are not one transaction. Outcome IDs and a future reviewed checkpoint/journal boundary are needed for exactly-once effects; do not claim original backups provide crash-atomic settlement. |
| Schema/version coupling | Trackable properties, entity IDs, prompt objects and Java protocol evolve. Pin client/server/content versions, own a narrow DTO schema, and compare payload/state assertions on upstream upgrades. Avoid mirroring every Java object in JS. |
| Hosting costs | A live Java host adds operational complexity and memory/CPU use on Silent. Measure before making capacity claims; no extra services or exposure are authorized by this design. |

Proposed **additive** work requiring a later implementation approval: IRemote
browser DTO bridge, allowlisted action/reply dispatcher, small native duel UI,
fixture host and end-to-end assertions. Possible **original-runtime boundary
changes requiring separate review**: headless GuiBase/EDT bootstrap; separating
Adventure semantic transitions from scene/timer rendering; isolated campaign
paths and save-preview hooks; durable encounter settlement. No change to
AiController, FutureTask/threading, targeting, costs, effects, rules, cards or
progression formulas is proposed. Each discovered need gets an exact source
diff and test plan before implementation.

Evidence that would change the recommendation:

- Favor browser-only again if a separately approved, maintained and licensed
  compatibility solution closes the **whole observed reachable gap set**,
  preserves original threaded decisions/cancellation and passes the full
  human/AI loop on Safari with acceptable foreground latency/memory. Require
  genuine Adventure encounter/return/save evidence before calling it a port.
  A FutureTask canary or disappearance of logging errors alone is insufficient.
- Reconsider JVM hosting if the existing GUI/controller boundary cannot support
  native browser decisions without replacing rule/AI behavior, or if genuine
  Adventure extraction requires invasive sustained forks; also if measured
  latency/resource needs are unacceptable or offline play becomes mandatory.
  First report the exact failing seam and costs, rather than assume either
  architecture is impossible.

**Next decision:** review the existing embedded test's narrowly scoped extension
before any bridge/transport work. Both architectures remain unimplemented; all earlier drafts stay
unmerged. No shim, service, installation or network change follows from this
recommendation without approval.

[world-stage]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/stage/WorldStage.java#L136
[map-duel]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/stage/MapStage.java#L1183
[duel-init]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/scene/DuelScene.java#L793
[duel-enter]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/scene/DuelScene.java#L365
[game]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/Game.java#L322
[game-view]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/GameView.java#L36
[hosted]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/HostedMatch.java#L215
[finish]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/screens/match/MatchController.java#L412
[duel-end]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/scene/DuelScene.java#L144
[world-result]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/stage/WorldStage.java#L171
[map-result]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/stage/MapStage.java#L844
[map-reward]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/stage/MapStage.java#L1001
[reward]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/scene/RewardScene.java#L186
[player-result]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/player/AdventurePlayer.java#L1089
[current]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/util/Current.java#L18
[save-load]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/world/WorldSave.java#L73
[save-write]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/world/WorldSave.java#L174
[world-save]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/world/World.java#L103
[stage-save]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/stage/WorldStage.java#L476
[save-header]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/world/WorldSaveHeader.java#L20
[protocol-gui]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/net/ProtocolGuiGame.java#L37
[remote]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/net/IRemote.java#L6
[forwarder]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gui/control/GameEventForwarder.java#L34
[controller]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/interfaces/IGameController.java#L17
[human-actions]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/player/PlayerControllerHuman.java#L2818
[input-proxy]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/input/InputProxy.java#L99
[priority]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/input/InputPassPriority.java#L340
[human-priority]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/player/PlayerControllerHuman.java#L1703
[play]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/player/PlaySpellAbility.java#L80
[target-selection]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/player/TargetSelection.java#L181
[targets]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/input/InputSelectTargets.java#L338
[gui]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gui/interfaces/IGuiGame.java#L44
[sender]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/net/GameProtocolSender.java#L5
[handler]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/net/GameProtocolHandler.java#L20
[encoder]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/net/CompatibleObjectEncoder.java#L77
[game-action]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/GameAction.java#L2554
[input-wait]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/input/InputSyncronizedBase.java#L11
[fthreads]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gui/FThreads.java#L26
[phase]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/phase/PhaseHandler.java#L1065
[ai-controller]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-ai/src/main/java/forge/ai/PlayerControllerAi.java#L849
[ai-task]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-ai/src/main/java/forge/ai/AiController.java#L1685
[ai-play]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-ai/src/main/java/forge/ai/ComputerUtil.java#L83
[visibility]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/card/CardView.java#L628
[phase-setup]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/phase/PhaseHandler.java#L1014
