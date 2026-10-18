# Gate 2 Stage B — implementation scope for review

Prepared 2026-10-18. **Design and source inspection only. Stage B is not
implemented, compiled or executed.** This refines the separately reviewed
[PR #9 design](https://github.com/rwZephyr/forge-adventure-web/pull/9) using
[Stage A](../../experiments/rules-teavm/RESULTS.md) and
[actual Safari evidence](safari-session-results.md), published in
[draft PR #12](https://github.com/rwZephyr/forge-adventure-web/pull/12).
PRs #8, #9, #11 and #12 remain unmerged. Source baseline is
`db893dcdb14fdd85a05437ad458dc70ef704ca2e`; original core/game/AI sources and
POMs still match it. No listener, installation or network change is proposed
as part of implementing the offline baseline below.

## Objective and stopping point

One genuine Forge AI decision must choose a payable Lightning Bolt and its
opponent target, then original Forge must pay R, cast it on the stack, resolve
three damage and apply state-based actions. The experiment must prove entry
into the original `AiController` FutureTask / `Game AI Eval` / timed-get path.
Empty-deck turn advancement, direct `DamageDealAi` calls, preselected targets,
free casting, direct damage/life mutation after setup, or `future.run()` on the
caller cannot pass.

First implementation slice, if approved: a JVM reference fixture and an
unchanged-runtime TeaVM compile attempt. If bootstrap/linking fails, preserve
the complete diagnostics and reachability chains and stop at the first boundary
that requires adaptation. Do not keep adding substitutes until an artifact builds.
The runtime changes listed below require review before implementation.

## What Stage A establishes and leaves open

- Exact factory classes and values pass with explicit constructor metadata in
  Chromium and exported Safari JSON. Default fails 5/15. With one constructor
  withheld, Bushido silently becomes SimpleKeyword/amount 1 and the exact-class
  assertion detects it. Successful initialization is insufficient.
- Stage A's 144 constructor signatures cover its trigger/replacement/keyword
  cases; they do not establish ApiType, card construction, localization, Game,
  mana payment, effect dispatch, AI or concurrency compatibility.
- The initial broader predicate dispatch produced 47 severe compiler errors
  reaching logger/Sentry locks, classloader enumeration, process/management,
  SQL, file-lock and other platform APIs. Narrowing Stage A's concrete calls
  was legitimate for hostless factory predicates. It is not an acceptable way
  to remove the real AI/Game call graph in Stage B.
- Safari results include one full export per variant and two owner-reported
  repeat summaries. They are evidence for those cases on that device, not full
  rules/AI coverage or a mobile performance budget.

## Verified source path and inputs

Links below pin original source at the inspected baseline.

| Original component | Required role / source finding |
| --- | --- |
| [LobbyPlayerAi](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-ai/src/main/java/forge/ai/LobbyPlayerAi.java#L32), PlayerControllerAi | Construct both genuine AI players; get the original brain from `PlayerControllerAi.getAi()`. No human GUI or overridden controller. |
| [AiController](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-ai/src/main/java/forge/ai/AiController.java#L1419) | `chooseSpellAbilityToPlay()` arms the timeout, enters its normal implementation, and eventually selects from a nonempty ability list. Simulation must be disabled and asserted. |
| [Threaded evaluation](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-ai/src/main/java/forge/ai/AiController.java#L1668) | Empty-list guard at 1669; FutureTask at 1685; `canPlayAndPayFor(sa)` at 1754; Thread construction/start and timed get at 1777–1782. Timeout cleanup includes cancellation, join and stop. |
| [AITimeoutTracker](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-core/src/main/java/forge/util/AITimeoutTracker.java#L183) | Shared deadline/session depth, ConcurrentHashMap, synchronized sections, ThreadLocal limits and interruption checks; begin/end and arm/disarm must balance. |
| [ComputerUtil.playStack](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-ai/src/main/java/forge/ai/ComputerUtil.java#L221) | Exact public signature is `(SpellAbility sa, Player ai, Game game)`; original CostPayment/AiCostDecision pay costs before stack insertion. Do not use `playNoStack`. |
| ComputerUtilAbility, ComputerUtilCost, ComputerUtilMana, AiCostDecision; game.cost.CostPayment/CostPartMana | Original candidate extraction, affordability, real mana activation/payment and cost bookkeeping. `SpellAbility.getPayingMana()` and `getPayingManaAbilities()` expose payment evidence. |
| [SpellApiToAi](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-ai/src/main/java/forge/ai/SpellApiToAi.java#L76), ai.ability.DamageDealAi | Genuine DealDamage AI mapping; reflection constructs and caches an instance. Assert exact DamageDealAi, not a generic fallback. |
| [ApiType](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/ability/ApiType.java#L234), AbilityFactory, effects.DamageDealEffect | ApiType's stateless effects are instantiated eagerly across the enum. A Bolt-only fixture therefore needs more effect-constructor coverage than DealDamage alone. |
| Game, Match, GameRules/GameType/GameStage; RegisteredPlayer, Player; CardRules, PaperCard, Deck/DeckSection; CardFactory | Real two-player controlled snapshot and original card construction. Mirror the resource/card construction pattern in [HumanControlledGame](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/test/java/forge/player/HumanControlledGame.java#L38), without copying its human-controller override or empty-deck fixture. |
| [MagicStack.resolveStack](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/zone/MagicStack.java#L590), GameAction.checkStateEffects, PhaseHandler | Original stack/effect resolution, graveyard movement and lethal state checks. |
| [Localizer](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-core/src/main/java/forge/util/Localizer.java#L147), Lang | Original bootstrap uses File → URLClassLoader → ResourceBundle. A copied properties file alone does not demonstrate browser bootstrap. |

Use genuine tracked `forge-gui/res/cardsfolder/l/lightning_bolt.txt`,
`.../m/mountain.txt` and `forge-gui/res/languages/en-US.properties`.
Select/hash bytes at build time and embed/copy only those fixture resources;
never search the filesystem, download cards or load a full card database at
runtime. Lightning Bolt's original script is R, Instant, SP$ DealDamage,
ValidTgts$ Any, NumDmg$ 3; Mountain's original basic-land behavior generates
mana through Forge. No handwritten mana/effect/card parser.

Working-checkout SHA-256 values inspected on Silent:

| Input | SHA-256 |
| --- | --- |
| Lightning Bolt script | `330ba48b8090d1b2312f9404d71612cd3bbf49b960974b99ebf5c0804bb4711e` |
| Mountain script | `d421d9639d7b7f2a8c4ed04fa2fa8526cbc768733a92b0a1a453535ee99a3799` |
| en-US.properties | `4c9e40f974c076389e13a809223bbd32bc7e1c031ca9d9691205a4e981b6db43` |

Record raw and LF-normalized hashes in implementation: these are text resources
and checkout newline policy may change raw bytes across machines.

## Exact fixture and actions

1. Initialize original Localizer/Lang on JVM using the genuine English resource.
   Construct original Match/Game/RegisteredPlayer objects and two LobbyPlayerAi
   instances with no simulation option and no rotating/random profile. Existing
   `AiProfileUtil.getAIProp` falls back to original AiProps defaults without
   loading profile files; assert this configuration.
2. AI registered deck: twelve Mountains and two Bolts. Opponent deck: ten
   Mountains. Create corresponding real Card instances once and place via
   original zones: AI library ten Mountains, hand two Bolts, battlefield two
   untapped Mountains; opponent library ten Mountains, other zones empty.
   Check unique card IDs, ownership/controllers, counts and zone identity.
   This conserves the cards in the registered fixture; no draw/mulligan loop.
3. Set life 20/3 only during setup; original `Game.setAge(GameStage.Play)` and
   `PhaseHandler.devModeSet(PhaseType.MAIN1, aiPlayer)` establish legal timing.
   Empty stack, empty mana pools, normal untapped sources, no transient triggers,
   no cards/SVars causing land-drop, Living End or simulation early returns.
   Set `game.AI_TIMEOUT = 2`, `setUseSimulation(null)` and assert both simulation
   predicates false. SpellAbilityPicker is still constructed by the original
   brain; disabling its mode does not prove its dependencies disappear.
4. Audit original nonland/nonmana candidate abilities: at least one real Bolt
   candidate, payable R, legal opponent target and timing. Do not assign its
   target or return value in the harness. Call only the public original brain
   `chooseSpellAbilityToPlay()` to obtain the decision.
5. Require an original Bolt SpellAbility, ApiType.DealDamage and the opponent
   chosen by original DamageDealAi. Either physical Bolt is acceptable; record
   its card ID rather than requiring a particular interchangeable object.
6. Call `ComputerUtil.playStack(selected, aiPlayer, game)`. Assert true, original
   mana activation, one real R paid, a Mountain tapped, paying-mana/source
   records, one Bolt on the stack and opponent life still 3. No cost waiver,
   injected mana, direct target assignment or direct effect invocation.
7. Call original `game.getStack().resolveStack()` and
   `game.getAction().checkStateEffects(true)`. Assert damage 3, opponent life
   3 → 0, cast Bolt in graveyard, stack empty, AI life 20, and original lethal
   outcome. Follow original moved-card identity rather than a stale object.
8. Repeat from three fresh identical fixtures. Separately use the same
   nonempty libraries/Mountains but no Bolts in hand: audit zero eligible spell
   candidates, null decision, no spell-evaluation task/lambda entry, no payment
   or damage. This negative control never stands in for the real decision.

No extra turns, full match runner, alternative cards, Adventure rendering,
accounts, persistence or multiplayer.

## Thread-path evidence and watchdog

Require evidence at original sites, correlated by run/game/player/card IDs:

- Nonempty original list past the guard; original FutureTask constructed.
- `new Thread(future, "Game AI Eval")`, start and lambda entry on that Java
  thread/coroutine identity, distinct from the caller.
- `canPlayAndPayFor` actually entered for Bolt and returned WillPlay, followed
  by original target selection evidence; timed `get` completes with that ability.
- Real cost/stack/resolution stages, task completion and inactive timeout session
  after the public call. No cancellation/expired/null outcome counted as a pass.

Prefer JDK debugger evidence and source-mapped browser logpoints/breakpoints
on unchanged code. Keep paused diagnostic traces separate from unpaused runs:
pausing consumes real timeout deadlines. A decision result alone does not prove
the threaded path. If installed tools cannot expose these original sites, stop
as **inconclusive** and submit a minimal passive observer proposal for review.
Do not silently add an AiController hook, rewrite bytecode, or substitute a direct
AI call. Observer overhead and timeout effects must be reported if later approved.

Use a 15-second independent bound per fresh run, not a mobile performance target.
JVM: parent process records/kills only its launched fixture PID on timeout.
Browser proposal: run the whole original Java fixture in one dedicated worker;
parent page owns the watchdog and can terminate a stalled worker. All Forge
objects and Java coroutine threads stay in that one worker. It does not move
AI evaluation into a separate worker/RPC implementation. A watchdog in the same
blocked JS event loop is insufficient. Worker launch/result reporting is probe
scaffolding and must be tested as an entry-boundary concern; no browser run or
server startup is authorized by this scope alone.

Output JSON stages: bootstrap, card/zone preconditions, metadata products,
candidate/path witnesses, chosen ability/target, paid mana and sources, stack
before/after, damage/life/zones/outcome, deadline/task cleanup, elapsed time,
errors/stacks, input/artifact hashes and browser/runtime identity. Capture
uncaught errors, console and raw compiler reachability diagnostics separately.

## New pinned-runtime inspection finding

[TeaVM documents coroutine-based Thread emulation](https://teavm.org/docs/runtime/coroutines.html),
not arbitrary JDK concurrency coverage or parallel shared-memory execution.
Read-only inspection of the already cached **0.15.0** classlib found:

- Source and binary JARs contain TThread, TThreadLocal, TConcurrentHashMap and
  TTimeUnit, but **no Future/FutureTask, LockSupport or ReentrantLock entries**.
- TThread.start schedules execution; interrupt flags/handlers and timed join
  exist. **No stop method is declared**. getStackTrace returns an empty array.
- Source JAR SHA-256:
  `e3a36f2a8cbd8cc0b1b8614f845b8e4923507311006d38ca6e9681ce04632df3`;
  binary JAR:
  `7d805be4670a892a34c89d9d750a8afd65e2f8201b7b3a10343634ea19d1c410`.

These are verified archive/source facts, not a Stage B compiler result. Other
compiler inputs could affect resolution; an actual unchanged-runtime compile
must establish the first unresolved symbol/call chain. FutureTask/timed-get and
reachable timeout cleanup are therefore **predicted blockers**, not demonstrated
TeaVM execution failures. The presence of Thread emulation cannot discharge them.
Full inspection metadata is in
[stage-b-preflight-20261018.json](evidence/stage-b-preflight-20261018.json).

## Candidate runtime-boundary changes — review before implementation

| Boundary / verified reason | Smallest candidate to review after actual failure | Required invariant |
| --- | --- | --- |
| Localization: File/URLClassLoader/ResourceBundle bootstrap | Add a bundled-resource initialization/provider boundary to original Localizer, keeping desktop initialization intact and genuine properties/formatting/fallback behavior. Exact API/diff proposed only after the failing call is known. | No fake Localizer, hardcoded message stubs or language-engine replacement. JVM file-backed behavior remains testable. |
| FutureTask/Future/timed wait: absent from inspected classlib | Evaluate a narrowly scoped TeaVM class-library compatibility contribution, preserving original AiController's Thread/FutureTask API call graph and task state, exception propagation, timed get, cancellation and interruption semantics. Assess licensing before importing implementation code. | No synchronous facade, direct run(), polling loop that blocks the event loop, ignored timeout, or bypass of the original evaluation lambda. Implementation is not preapproved. |
| Timeout cleanup: reachable stop call; stack traces empty in cached Thread | Propose an explicit platform capability boundary only if actual linking requires it; retain cancellation, interrupt, join and the surviving-task check. Report unsupported hard-stop and terminate the whole isolated worker on failure. | A timed-out/still-running task is a failed run. Never delete cleanup, suppress a timeout or claim unavailable diagnostic frames. |
| Logging/Sentry/platform reachability: actual Stage A failures | Propose a real browser diagnostics backend or an explicit host-services boundary after recording reachability. Keep errors visible and preserve desktop behavior. | No wholesale library stubs, silent logger replacement or stripping engine dispatch to make compilation pass. |
| StaticData/filesystem/bootstrap if reached by genuine cards/Game | First prove the exact dependency; propose only an injectable provider for the genuine selected card/resource data if necessary. | No fake FModel/StaticData, replacement rules engine or blanket null returns. |
| Path observability if debugger tools fall short | A separately reviewed passive observer at the few named sites; exact source locations, default behavior and compiled-input hashes disclosed. | Same original choices, Thread/FutureTask/timed-get, costs and resolution. Instrumented/adapted runs labeled separately from unchanged baseline. |

Constructor metadata in compiler-side policy is distinct from these runtime
changes. Retain Stage A coverage, inventory the full original ApiType stateless
effect constructor set and the actually used AI implementations, and compare
against JVM enumeration. Assert exact DamageDealEffect and DamageDealAi.
Do not trim ApiType/SpellApiToAi enums/maps or treat untested Stage A signatures
as adequate. Counts are to be audited during implementation, not guessed here.

## Affected files, dependencies and execution order

Proposed **new** target `experiments/ai-teavm/`, leaving
`experiments/adventure-teavm/` and `experiments/rules-teavm/` unchanged:

- Shared `OriginalAiFixture`, result schema and JVM/browser entry points under
  `src/main/java/probe/`; compiler-side driver/constructor policy under
  `src/compiler/`; small worker/diagnostics page under `src/main/web/`.
- Pinned build/lock files, build-time fixture resource selection/hash manifest,
  offline preparation/invocation scripts, raw results and reproduction report.
- Planned tasks: `jvmControl`, `metadataInventory`, `browserOriginal`,
  `dependencyInventory`; no implicit dev-server/run tasks. Platform-adapted
  variants or original Forge edits are absent until separately reviewed.

Inputs: freshly compiled unchanged forge-core/game/**ai** classes/resources,
existing Stage A normal runtime dependency graph and compiler metadata APIs,
plus original forge-ai dependency commons-math3 **3.6.1**. Existing cached JAR
verified at SHA-256
`1e56d7b058d28b65abd256b8458e3885b674c1d588fa43cd7d1cbb9c7ef2b308`.
Keep Guava 33.3.1-android, commons-lang3 3.18.0/text 1.12.0, rssreader 3.8.2,
tinylog 2.7.0/slf4j-api 2.0.16, Sentry 8.21.1, JGraphT 1.5.2 and their normal
transitives; pin TeaVM 0.15.0, gdx-TeaVM 1.6.1 and libGDX 1.14.2. No GUI/Adventure
module, TestNG runtime or full card database. Lock/hash the resolved closure and
extend the existing dependency/license inventory. Cached availability is not a
new-install approval; missing artifacts stop offline work pending approval.

After scope approval, use installed JDK 17 and approved portable tools/caches:

1. Original Maven closure, expanded as portable `mvn.cmd -o -B -ntp -T1
   -Dmaven.repo.local=<repo>/.local-tools/m2 -pl forge-ai -am -DskipTests compile`.
   No install/package/deploy, updater, GUI launcher or system settings.
2. JVM fixture/control and diagnostic path witnesses. Three fresh full fixture
   runs and empty-hand negative must pass before browser output is a comparator.
3. Audit full constructor metadata, resolved dependencies/licenses and classlib
   symbol coverage. Preserve original source/class/POM hashes.
4. TeaVM unchanged-runtime attempt with explicit audited metadata, existing
   Gradle offline/no-daemon/one-worker, 1536 MiB heap and sequential compilers.
   Preserve errors, reachability and failed stage; do not apply an adapter yet.
5. If an artifact exists, ask for a fresh separately approved local serving/test
   session. Browser/Safari evidence requires original path witnesses plus actual
   payment/cast/resolution and independent watchdog. The previous Safari session
   ended; its permission does not authorize a new listener.

**Pass:** all genuine stages and thread-path witnesses, three fresh fixtures
agree, negative control is correct, and original deadlines/task cleanup complete.
Label unchanged versus adapted runtime and browser identity separately. **Fail:**
missing dependency/compiler symbol, bootstrap/reflection failure, wrong class,
early-return/no task, missing payment/stack/damage, timeout/deadlock, surviving
task or engine-state mismatch. **Inconclusive:** decision/result without reliable
path evidence or browser test not performed. None establishes whole-game AI
quality, parallel CPU execution or full browser-port feasibility.

## Review requested

Review the unchanged baseline slice, exact fixture/assertions, path-evidence
method and independent watchdog. Then review any concrete boundary patch after
its unmodified compiler/runtime failure is recorded. No Stage B build, gameplay,
boundary adaptation, dependency installation or server was performed to prepare
this scope. If preserving genuine threading semantics requires a larger runtime
port, stop and report that boundary rather than broadening this experiment.
