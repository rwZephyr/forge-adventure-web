# C1: human Bolt ability identity audit

2026-10-18. Read-only audit of PR #19 evidence at
`0e6fb9009c1e6fe6e65c70e16fc6c35d1789d524` and original production source at
`db893dcdb14fdd85a05437ad458dc70ef704ca2e`. Responds to the
[coordinator's C1–C6 conditions](https://github.com/rwZephyr/forge-adventure-web/pull/20#issuecomment-6255174376).
No execution, instrumentation, dependencies or runtime changes in this audit.

**Disposition: C1 remains blocked.** The singleton offers and original gameplay
chain are verified from existing assertions/logs/source. Actual per-call
SpellAbilityView object identities and view→SpellAbility cache pairs were not
recorded. Downstream ability identities do not fill that gap. No authorization
mapping is approved or implemented by this document.

## What the existing execution records establish

The [final human witness log][log] records exactly these three replies. IDs below
are private engine/debugger identifiers, not proposed browser handles. JDI IDs
apply only within that witness JVM; they are not comparable across runs.

| Question | Caller and original input | Offered view / choice count | Corresponding engine ability: observed versus inferred |
| --- | --- | --- | --- |
| Initial Bolt selection | EDT `AWT-EventQueue-0`, Java thread 122; InputPassPriority. Game-0 waiting, latch 1. Log lines 48–49. | View `getId()==214`, host card 1, spell; exactly **1** offered view asserted before reply. Java view object ID unrecorded. | No cache pair captured at this call. Next `playChosenSpellAbility` receives JDI ability object **4146**, engine ID **214**, Bolt card 1 (witness event 12). Original InputPassPriority/phase-loop source passes the chosen ability onward; relating 4146 to this exact offered view is source-based inference, not a recorded pair. |
| Further Bolt choice | Game-0, Java thread 121; InputLockUI. Log lines 55–56. | View ID **214**, host card 1, spell; exactly **1** offered view asserted. Java view object ID unrecorded. | Downstream Bolt payment and resolution use object **4146**, ID **214** (events 25–26, 53). This question's mapped value was not captured. Same numeric ID in both offers does not establish Java reference equality. |
| Mountain ability choice | Game-1, Java thread 125; InputPayManaOfCostPayment. Game-0 waiting, latch 1. Log lines 92–93. | View ID **199**, host card 2, non-spell; exactly **1** offered view asserted. Test additionally requires reference equality with `sa.getView()` for an original ability in Mountain's mana-ability list. Java view object ID unrecorded. | Downstream mana ability is object **4263**, ID **199**, Mountain card 2 (events 35–36). Payment input's `saPaidFor` is Bolt object **4146** (event 32). The offered-view/cache-value pair and the chosen Mountain value entering GameAction were not recorded together. |

[Witness JSON][witness] event indices are zero-based. Events 10, 13 and 33
capture original graph-walk stacks *inside* the respective questions, but have
no offered-view or cache-value identity fields. Event 13 locates the Game-0
question in `PlaySpellAbility.chooseOptionalAdditionalCosts`; event 33 locates
the Game-1 question in that same method under
`InputPayMana.lambda$activateManaAbility$0`. Thus the Mountain question is the
original additional-cost ability selection while playing the selected mana
ability, not an additional EDT menu shown before the payment worker starts.
The term “confirmation” describes the second Bolt choice, not an IGuiGame
boolean confirm dialog.

**Uniqueness:** the [accepted test][test] requires `choices.size()==1` for every
question, validates the exact offered host and spell kind, returns that offered
view, and finally requires `abilityReplies==2 && manaReplies==1`. The three
fresh positive [human-1][h1], [human-2][h2], [human-3][h3] results and final witness
pass these assertions. Initial Bolt selection calls
`getAllPossibleAbilities(player, true)`; the [original filter][card] removes
unplayable abilities using `canPlay(true)` before offering them. This establishes
one offered valid Bolt view in the recorded runs. The pre-cache SpellAbility
list size and each `canPlay` result were not logged: map-key deduplication is
not itself proof that the original list contained exactly one Java ability.

The three ordinary run logs repeat view IDs 214/214/199 and caller ordering:
human-1 and human-2 have Java thread IDs 117/116/120; human-3 has 119/118/122
(EDT/Game-0/Game-1). These repetitions confirm the sequence, not object identity
between separate JVMs. The #19 script has no immutable authorization record;
it checks the fixture's host/kind/count and Mountain view membership. Authentic
gameplay success is not a recorded cross-call authorization proof.

## Original source mapping and copy boundaries

1. [InputPassPriority.onCardSelected][priority] obtains the original playable
   abilities, calls the human controller, stores the returned ability in
   `chosenSa`, then stops the input. [PlayerControllerHuman][human] returns
   that list to the original PhaseHandler; `playChosenSpellAbility` passes each
   chosen ability into `PlaySpellAbility.playSpellAbility`.
2. [PlayerControllerHuman.getAbilityToPlay][cache] rebuilds its mutable
   `spellViewCache` **on every call**. [SpellAbilityView.getMap][view] maps each
   supplied ability's view to that ability; the offered GUI list is a copy of
   the map's key set. The returned view is looked up in the then-current cache.
   There is no persistent cross-call view→ability mapping. The controlled-human
   branch also assigns the cache to the controlling human; not the fixture's
   ordinary human/AI case.
3. [PlaySpellAbility][play] invokes `chooseOptionalAdditionalCosts` for both
   spells and mana abilities. That method builds an additional-cost list and
   calls `getAbilityToPlay` again, then applies optional costs. This explains
   the second Bolt question and Game-1 Mountain question without a replacement
   gameplay path.
4. [GameActionUtil][additional] starts the additional-cost list with the supplied
   original ability. Alternate additional costs can clear it and offer copies.
   `addOptionalCosts` returns the same ability for an empty optional-cost list;
   otherwise it copies. The [Bolt card script][bolt] contains one ordinary R
   damage spell and no alternate/optional costs. Source predicts the ordinary
   same-ability path in this fixture, but the intermediate lists, branches and
   return identities were not observed. Do not promote that prediction to an
   audited authorization allowlist.
5. [SpellAbility.getView][getview] updates and returns its stored view rather
   than allocating a fresh view each call. However, [SpellAbility.copy][copy]
   allocates a **new** view and normally a new ability ID; `lki` copies retain
   the ability ID. [TrackableObject.equals][equals] compares class/hash(ID),
   and SpellAbility equality also compares IDs. Consequently equal IDs,
   `equals`, map membership or successful lookup do **not** certify `==`.
   Cache construction copies the map/list structure, not necessarily the views
   or abilities; optional/alternative-cost construction may copy the abilities.

The existing [observer][observer] samples ability objects at downstream
controller/payment/stack sites. It does not install per-question breakpoints
or record offered views, original ability lists or `spellViewCache` entries.
Nothing in the published evidence establishes the missing Java reference pairs.

## Smallest additional passive observation proposed for separate approval

One fresh run of the **unchanged existing human test**, using the approved
shared-memory JDI approach and isolated offline environment, could close this
specific evidence gap. It would require a narrowly scoped observer extension;
neither that extension nor the run is performed or authorized by this audit.

- At each human `getAbilityToPlay` call, after cache/list construction and before
  GUI dispatch, record caller Java thread ID, controller/input identities, host,
  original ability-list count and each ability JDI object ID, offered view object
  IDs and engine IDs, and exact cache key→value object pairs. Assert initial
  input list and offered list are both singleton; inspect recorded playability
  flags/source branch without invoking getters or any target methods through JDI.
- At the actual IRemote question/return and controller mapping return, capture
  the offered/returned view and mapped ability identities, plus the cache object
  identity. Require that the returned reference is the sole offered reference
  and that the actual current cache maps it to the observed returned ability.
  A controller cache overwrite/reentrant change is a blocker, not something to fix.
- Correlate initial returned Bolt with InputPassPriority's chosen value,
  PhaseHandler's dispatched ability and `chooseOptionalAdditionalCosts`'s
  `original`, `abilities`, chosen and return references. Record list counts and
  optional-cost emptiness; observe actual copy entry/return only if a copy occurs
  on this chain. Never infer copy ancestry from host/name/numeric ID alone.
- For Mountain, correlate the manual EDT card action and its payment input's
  `saPaidFor` with GameAction's captured `chosen` mana ability, the Game-1
  original/additional-cost offer and mapped/returned value. Retain original
  paying-mana/source, stack and resolution observations to anchor the attempt.
- Record input/latch and timing at these sites; no target method invocation,
  decisions, graph mutation or new network transport. Debugger timing is not a
  performance measurement or synchronization proof. Fail on extra candidates,
  unidentified copies/cache remaps, missing debug locals or unsafe capture.

**Review hold:** C2–C6 are now explicit in the [revised design](browser-jvm-interaction-contract.md).
C1's complete per-call identity relation and a safe capture boundary remain
unclosed. Review this gap and the proposed observation before authorizing any
observer work or DTO implementation; no mapping should be invented in the meantime.

[log]: ../experiments/evidence/embedded-bolt-20261018/witness-human-final/stdout.log#L48
[witness]: ../experiments/evidence/embedded-bolt-20261018/witness-human-final/witness.json
[h1]: ../experiments/evidence/embedded-bolt-20261018/human-1/result.json
[h2]: ../experiments/evidence/embedded-bolt-20261018/human-2/result.json
[h3]: ../experiments/evidence/embedded-bolt-20261018/human-3/result.json
[test]: https://github.com/rwZephyr/forge-adventure-web/blob/0e6fb9009c1e6fe6e65c70e16fc6c35d1789d524/forge-gui-desktop/src/test/java/forge/net/embedded/ProtocolGuiGameInProcessTest.java#L385
[priority]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/input/InputPassPriority.java#L343
[card]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/card/Card.java#L7574
[human]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/player/PlayerControllerHuman.java#L1772
[cache]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/player/PlayerControllerHuman.java#L219
[view]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/spellability/SpellAbilityView.java#L20
[play]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/player/PlaySpellAbility.java#L140
[additional]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/GameActionUtil.java#L537
[bolt]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/res/cardsfolder/l/lightning_bolt.txt#L1
[getview]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/spellability/SpellAbility.java#L2385
[copy]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/spellability/SpellAbility.java#L1245
[equals]: https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/trackable/TrackableObject.java#L60
[observer]: https://github.com/rwZephyr/forge-adventure-web/blob/0e6fb9009c1e6fe6e65c70e16fc6c35d1789d524/experiments/ai-teavm/src/observer/java/probe/JdiWitness.java#L144
