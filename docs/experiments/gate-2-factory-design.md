# Gate 2 proposal: original factories, then threaded spell AI

Prepared by Codex, 2026-10-17. **Design only; not implemented or executed.**
Inspected source: `db893dcdb14fdd85a05437ad458dc70ef704ca2e`. Forge core/game/AI
sources are identical at PR #8 head `57ed01d137e45ea2a304b64ef979402347cb0843`.

## Gate boundaries

[PR #8](https://github.com/rwZephyr/forge-adventure-web/pull/8) remains draft,
separate and unmerged. Its corrected Chromium run establishes visible sprites,
pointer dragging, Y-related ordering, resize and high-DPI mapping. The blank
loading canvas was a launcher preload defect and was fixed; actual iPhone Safari
and physical touch are the outstanding Gate 1 checks.

The independent review in the project conversation examined the earlier
`fecf4c19` head and performed no browser tests. This design incorporates its
factory/threading findings with three corrections verified below: only
TriggerType scans constructors during enum initialization; Keyword can fail
silently; advancing turns with empty hands/decks does not exercise spell AI.
A factory-case pass would not retire every rules/ability factory risk.

Phone testing waits for an agreed private arrangement: specify the device and
iOS/Safari versions, artifact hash, serving machine, exact private address and
listener lifetime before starting anything. Use an isolated static-artifact test
with an explicit shutdown step. No changes to Tailscale Serve/Funnel, routes,
ACLs, daemon, certificates, firewall or existing services without separate
approval. Local-test approval does not imply permission for phone/network access.

Phone acceptance: visibly rendered original chests; positive region/texture
counts; physical drags across the other actor's Y and back with matching visible
overlap and recorded coordinates/order; repeat after portrait/landscape rotation;
nonzero canvas backing dimensions/DPR; successful reload/assets and no page/GL
errors. Record device/version, screenshots, diagnostics and timings. Desktop
emulation is insufficient. Compare order against Y and pixels: draw-order and
child-order strings read the same sorted array and are not independent evidence.

## Stage A source and dependencies

Proposed new directory `experiments/rules-teavm/`, independent of PR #8's target.
Use unchanged, freshly compiled `forge-core` and `forge-game` outputs as compiler
inputs; TeaVM determines the reachable JS closure. No copied/edited Forge
classes, shortened enums, substitute factories/rules, AI, GUI or Adventure in
this stage. Full module inputs do not establish full-module browser compatibility.

| Original source | Use and verified behavior |
| --- | --- |
| [TriggerType](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/trigger/TriggerType.java#L164) | All enum entries call `findConstructor` during initialization: `getDeclaredConstructors`, parameter-type inspection and `(Map, Card, boolean)` invocation. Source has 148 entries referencing 136 distinct implementation classes. |
| [ReplacementType](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/replacement/ReplacementType.java#L56) | 40 entries store classes; scanning/invocation happens in `createReplacement`, not enum initialization. |
| [Keyword](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/keyword/Keyword.java#L264) | `getConstructor().newInstance()` exceptions are caught and replaced silently with `new SimpleKeyword()`. Initialization alone proves no reflection success. |
| `forge.game.trigger.TriggerAlways`, `TriggerLifeGained` | Two exact trigger products. |
| `forge.game.replacement.ReplaceDraw`, `ReplaceGainLife` | Two exact replacement products. |
| `forge.game.keyword.SimpleKeyword`, `KeywordWithAmount`, `KeywordWithType`, `KeywordWithCost`, `Kicker` | Five keyword products, including distinct specialized subclasses. |
| `forge.game.CardTraitBase`, `TriggerReplacementBase`; `trigger.Trigger`; `replacement.ReplacementEffect`; `keyword.KeywordInstance`, `KeywordInterface`, `KeywordWithCostInterface`; `cost.Cost`, `CostPartMana`; `forge.card.mana.ManaCost`, `ManaCostShard` | Original base behavior and parsed-value accessors; normal reachable dependencies remain original. |

Hostless factory cases intentionally pass `host = null`. Inspection of
[Trigger's constructor](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/trigger/Trigger.java#L90),
[ReplacementEffect's constructor](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/replacement/ReplacementEffect.java#L103)
and `TriggerReplacementBase.setHostCard` shows storage without host dereferencing
for these cases. Keyword parsing initializes without attaching a host. Confirm
on the JVM before transpilation; failure stops the test instead of introducing
fake Card/Game/Localizer classes. Do not call `ReplaceDraw.canReplace`, host-based
descriptions, or keyword `createTraits`. This tests constructors and selected
predicates, not bootstrap or event dispatch.

Reuse approved portable Maven 3.9.16 / Gradle 9.6.1, installed JDK 17.0.12 and
isolated repository caches, one worker and 1536 MiB heap. No system installation,
global settings, user cache changes, server tasks or service/deployment changes.

| Input | Exact proposed version / scope |
| --- | --- |
| Forge | `forge-core/target/classes`, `forge-game/target/classes` and normal resources, freshly compiled from pinned original sources. Hash inputs and reject stale outputs; root POM/reactor unchanged. |
| Browser compiler | Same gdx-TeaVM plugin/backend 1.6.1, TeaVM 0.15.0, libGDX 1.14.2 as PR #8; plain Java entry point, no GL/Stage required. libGDX is tooling infrastructure, not a claimed rules-module dependency. |
| Core direct libraries | `com.google.guava:guava:33.3.1-android`; `org.apache.commons:commons-lang3:3.18.0`; `org.apache.commons:commons-text:1.12.0`; `com.apptasticsoftware:rssreader:3.8.2`; `org.tinylog:slf4j-tinylog:2.7.0`; `org.tinylog:tinylog-impl:2.7.0`. |
| Game direct libraries | `io.sentry:sentry:8.21.1`; `org.jgrapht:jgrapht-core:1.5.2`. |
| Parent | `org.slf4j:slf4j-api:2.0.16`. |
| Metadata, compiler only | `org.teavm:teavm-extension-spi:0.15.0` and introspection API, already present in compiler graph. No policy code in Forge. |
| Stage B only | Original `forge-ai/target/classes`; `org.apache.commons:commons-math3:3.6.1`; genuine localization resource if bootstrap requires it. |

Versions verified from root/module POMs, PR #8 locks and cached source JARs.
Retain the normal transitive graph: Guava support/annotation artifacts, tinylog
API, jheaps 0.14, apfloat 1.10.1 and compiler/backend transitives. Generate and
lock the complete resolved version/hash inventory before building. Confirm Guava
stays the Android variant. TestNG 7.10.2 is Maven test scope and is not shipped.
Inventory licenses/notices before distributing the bundle: Forge GPL, compiler
and common Apache/MIT dependencies, JGraphT EPL/LGPL choices and all remaining
resolved dependencies. No new external implementation code or game assets here.

## Metadata variants

1. **Default control:** compiler defaults with no Forge metadata additions.
   Record each factory group's outcome independently so a poisoned TriggerType
   initialization cannot hide a keyword fallback. Default failure is useful data.
2. **Explicit metadata:** compiler-side `ReflectionPolicy`, loaded through
   `META-INF/services/org.teavm.extension.spi.reflection.ReflectionPolicy`.
   Return only required constructors from `classAccessibleMembers`: all 136
   TriggerType implementation `(Map, Card, boolean)` constructors, the two
   selected replacement constructors, and five keyword public no-arg
   constructors. Derive/audit a manifest against original enum mappings and
   exact signatures. Do not initialize Forge factories inside the extension.
   No blanket `forge.**` reflection or retention of every method.
3. **Silent-fallback negative control:** fresh separate build/process with the
   same explicit metadata except no `KeywordWithAmount` no-arg constructor.
   Disable broad scanning that could restore it. Run unchanged `Bushido:2`
   assertions: require observed SimpleKeyword and failed concrete-class check.
   Successful page startup must still report failure. If it unexpectedly passes,
   inspect emitted metadata; a control that did not withhold the constructor is
   inconclusive. Do not alter the original keyword factory to force the result.

TeaVM 0.15.0's cached `ReflectionPolicy` / `DefaultReflectionSupplier` sources
verify this API and ServiceLoader mechanism. gdx-TeaVM 1.6.1's cached
`TeaReflectionSupplier` exposes all members of matched classes, potentially
retaining a much larger closure; use constructor-only policy for Forge instead.
Integration and actual reachability remain untested. Log additional reflection
requirements individually; stop for review before broadening scope or replacing
original behavior.

## Stage A exact actions and assertions

Enumerate original enums and compare complete names/counts with the pinned JVM
control. This scans all TriggerType constructors, not all replacement/keyword
constructors. Call original factories, never `new` the expected concrete products
in shared cases. Use an explicit throwing check helper, not Java `assert`, which
may be disabled. Share cases between JVM and browser; isolate JS reporting.

| Case | Factory action (maps shown as key:value) | Mandatory observations |
| --- | --- | --- |
| T1 | `TriggerType.smartValueOf(" always ").createTrigger({Mode:Always, TriggerZones:Battlefield}, null, true)` | `getClass() == TriggerAlways.class`; mode Always, host null, intrinsic true, active zone exactly Battlefield; `performTest(emptyMap)` true. |
| T2 | `TriggerType.LifeGained.createTrigger({Mode:LifeGained, FirstTime:True}, null, false)` | Exact TriggerLifeGained, mode LifeGained, intrinsic false; `performTest` with AbilityKey.FirstTime Boolean true/false returns true/false. No host-dependent validity filters. |
| R1 | `ReplacementType.smartValueOf(" draw ").createReplacement({Event:Draw}, null, true)` | Exact ReplaceDraw, mode Draw, intrinsic true and Event preserved; do not call its host-dependent `canReplace`. |
| R2 | `ReplacementType.GainLife.createReplacement({Event:GainLife}, null, false)` | Exact ReplaceGainLife, mode GainLife, intrinsic false; `canReplace` with AbilityKey.LifeGained 1 / 0 / -1 returns true / false / false. No ValidPlayer/ValidSource/SourceController filters. |
| K1 | `Keyword.getInstance("Flying")` | Exact SimpleKeyword, enum FLYING, original input preserved. Control only: fallback has the same class. |
| K2 | `Keyword.getInstance("Bushido:2")` | Exact KeywordWithAmount, BUSHIDO, original input, amount 2 and amount string `"2"`. SimpleKeyword fallback normally returns amount 1; exact class remains mandatory. |
| K3 | `Keyword.getInstance("Enchant:Card.Creature:creature")` | Exact KeywordWithType, ENCHANT; valid type `"Card.Creature"`, description `"creature"`. Explicit description avoids Lang lookup. |
| K4 | `Keyword.getInstance("Multikicker:1 R")` | Exact KeywordWithCost, MULTIKICKER, cost string `"1 R"`, nonnull original Cost; mana value 2, generic 1, one red shard. |
| K5 | `Keyword.getInstance("Kicker:1 R")` | Exact Kicker, KICKER, same cost assertions. `instanceof KeywordWithCost` would miss the wrong subclass. |

For K4/K5 use `getCost().getCostMana().getMana()` and original `ManaCost.getCMC`,
`getGenericCost`, `getShardCount(ManaCostShard.R)`; no harness mana parser.
For all cases assert independent objects on repeated calls, preserved input and
original/copied parameters; repeat trigger/replacement calls with intrinsic
true/false. Mutate input maps afterward and check copied product values remain.
Invalid trigger/replacement names must throw; unknown keywords must retain the
original UNDEFINED/SimpleKeyword behavior. Expected negative cases are distinct
from unexpected errors.

After Stage A design approval:

1. Compile original core/game closure offline using installed JDK 17 and portable
   `mvn.cmd -o -B -ntp -T1 -Dmaven.repo.local=<repo>/.local-tools/m2 -pl
   forge-game -am -DskipTests compile`. Capture expanded command, logs, exit code
   and input hashes. No install/package/deploy/game launcher.
2. Compile/run the shared JVM control. All nine rows and negative expectations
   must pass before treating browser output as a comparator.
3. Build isolated default/explicit/missing-amount JS variants with existing Gradle,
   `--no-daemon --max-workers=1 --console=plain`, locks, source maps and server
   tasks disabled. Proposed tasks: `jvmControl`, `browserDefault`,
   `browserExplicit`, `browserMissingAmount`; these do not exist yet. Try offline;
   report missing dependencies before downloads outside existing approval.
4. Under an agreed local-test arrangement, load each variant in fresh Chromium
   pages. Repeat on actual Safari when privately available. Export case JSON,
   console/network/errors; stop any approved listener afterward. No reused
   poisoned enum/session. JSON includes expected/actual class, values, error and
   stack, completion status, browser/version and build hash. Retain readable raw
   logs, not only hashes of logs unavailable to reviewers.

**Pass:** explicit-metadata JavaScript matches the passing JVM control on all
nine rows and negative expectations, after three fresh reloads, with no
unexpected errors; missing-amount control visibly fails K2 with SimpleKeyword.
Report default outcome separately and browser-specific results separately.
Without a demonstrated negative control, assertion coverage is incomplete.

**Failure categories:** Java compile; TeaVM link/reachability; enum startup;
constructor lookup/invocation; silent fallback/class mismatch; parsed-value or
predicate mismatch; browser timeout/crash. Use a 15-second external watchdog per
page, a diagnostic bound rather than a performance target. A fix using metadata
is "passes with explicit metadata", not "passes with defaults". Initialization
or compilation alone cannot pass. No claim about ApiType/AbilityFactory,
createTraits, all keywords, complete cards, dispatch or AI follows from Stage A.

## Stage B: separate threaded spell decision, cast and resolution

Do not implement this with Stage A. No full duel or arbitrary 50-turn loop.

Exact originals (paths under each module's `src/main/java/forge/`):

- `forge-ai`: `ai/LobbyPlayerAi.java`, `PlayerControllerAi.java`,
  `AiController.java`, `ComputerUtilAbility.java`, `ComputerUtilCost.java`,
  `ComputerUtil.java`, `SpellApiToAi.java`, `ai/ability/DamageDealAi.java`.
- `forge-core`: `util/AITimeoutTracker.java`, `Lang.java`, `Localizer.java`,
  `card/CardRules.java`, `item/PaperCard.java`, `deck/Deck.java`.
- `forge-game`: `game/Game.java`, `Match.java`, `GameRules.java`, `GameStage.java`,
  `game/player/RegisteredPlayer.java`, `Player.java`, `game/card/CardFactory.java`,
  `game/ability/AbilityFactory.java`, `ApiType.java`,
  `game/zone/MagicStack.java`, `game/phase/PhaseHandler.java`,
  `game/spellability/SpellAbility.java`, plus normal reachable original classes.

Mirror only the no-FModel card construction pattern in
[HumanControlledGame](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/test/java/forge/player/HumanControlledGame.java#L38),
using genuine LobbyPlayerAi/PlayerControllerAi for both players, no human GUI.
Create cards through CardRules/PaperCard/CardFactory from original tracked
[Lightning Bolt](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/res/cardsfolder/l/lightning_bolt.txt)
and [Mountain](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/res/cardsfolder/m/mountain.txt)
script bytes selected/hashed at build time; no full database, updater or runtime
filesystem card search. Retain nonempty registered decks and ten Mountains in
each library. AI hand: two Bolts; battlefield: two untapped Mountains; AI life 20,
opponent life 3, legal opponent target and empty stack. Original zones/state must
match. Use original `Game.setAge(GameStage.Play)` and
`PhaseHandler.devModeSet(MAIN1, aiPlayer)` for a controlled snapshot that skips
opening procedures. Set `game.AI_TIMEOUT = 2`; use `setUseSimulation(null)` and
assert full/hybrid simulation both false. No lands in hand, PlayBeforeLandDrop,
Living End or other early-return distractions.

Prove nonempty nonland/nonmana Bolt candidates, legal timing/target and payable R
using genuine card/mana abilities, then call public
`AiController.chooseSpellAbilityToPlay()`. The intended original path is
[getSpellAbilityToPlay → chooseSpellAbilityToPlayFromList](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-ai/src/main/java/forge/ai/AiController.java#L1659),
past the empty-list guard (1669), into FutureTask lambda (1685),
`canPlayAndPayFor(sa)` (1754) and
[`new Thread(future, "Game AI Eval")`, start, timed get](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-ai/src/main/java/forge/ai/AiController.java#L1777).

Require debugger evidence of nonempty candidates, lambda entry, actual
canPlayAndPayFor with Bolt, eval thread/coroutine identity, and timed get
completion: JDK debugger for reference and source-mapped browser breakpoints at
the same original sites. Keep paused traces separate from unpaused timeout runs;
breakpoints consume deadlines. A returned decision without path evidence is
**inconclusive**. If tooling cannot expose these sites, propose narrow observer
instrumentation separately; do not silently patch AiController or count direct
DamageDealAi calls as threaded evaluation.

Expected result: real Bolt SpellAbility, ApiType.DealDamage, opponent chosen by
original AI; original `ComputerUtil.playStack` succeeds with real mana payment
and stack insertion; original `MagicStack.resolveStack` and state checks produce
damage 3, opponent life 3 → 0, Bolt in graveyard. Three fresh identical fixtures
must agree within an independent 15-second watchdog each. Empty-hand negative
fixture: null decision and zero threaded spell-evaluation entries. Phase/turn
advancement, empty candidate lists, swallowed null/timeouts or direct effect
resolution cannot pass.

Bootstrap prerequisite: `Localizer.setLanguage` uses File/URLClassLoader and
ResourceBundle. Copying genuine `res/languages/en-US.properties` alone may not
suffice. Attempt unchanged bootstrap with the resource; classify missing
filesystem/classloader/StaticData requirements as blockers and propose a narrow
boundary before adaptation. No fake Localizer/FModel/engine is authorized.
AiProfileUtil supports enum defaults without loading profile files; use these,
no random/rotating profiles. Include original DealDamage AI/effect constructor
metadata only if required; record additional reachability separately.

JavaScript is not a native shared-memory JVM, but
[TeaVM documents coroutine emulation of Java Thread and synchronization](https://teavm.org/docs/runtime/coroutines.html).
Forge's FutureTask, timed get, ThreadLocal and timeout cleanup may work, fail or
stall; determine by execution. Classify bootstrap, reflection, thread-start,
timed-wait and cleanup failures separately. A pass establishes this original
path and fixture on the tested browser, not parallel CPU work, all AI behavior,
play quality or mobile performance. Stage B requires separate review after A.

## Verification for this proposal

Verified by inspection: PR #8 open/draft/unmerged at `57ed01d`; Chromium evidence
supersedes blank-canvas concern; source mappings, constructor/predicate behavior,
fallback and AI thread/get path; POM dependencies; cached TeaVM 0.15.0 and
gdx-TeaVM 1.6.1 source APIs. Original core/game/AI diff against baseline is empty.
No Gate 2 build/execution, dependency install, phone test, server or service change.

Next decision: approve/revise **Stage A only**, including constructor metadata
and the intentionally failing fallback artifact. Stage B follows actual Stage A
results and a separate review. Gate 1 phone testing waits for the agreed private
arrangement. PR #8 remains separate and unmerged.
