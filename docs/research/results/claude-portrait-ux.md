# Forge Adventure Web — Portrait-first UX research

**Research track:** Portrait UI (originally assigned to Claude; completed by ChatGPT via repository source inspection)  
**Date:** 2026-10-17  
**Repository inspected:** `rwZephyr/forge-adventure-web`, `master` as of this research session  
**Status:** Source-grounded design study, **not** a built or tested browser port

## Executive findings

1. **Portrait is already a first-class path in parts of upstream Forge Adventure.** `NewGameScene`, `StartScene`, `MapViewScene`, `InventoryScene`, `SaveLoadScene`, `DeckSelectScene`, `QuestLogScene`, `RewardScene`, `SettingsScene`, `PlayerStatisticScene` and `GameHUD` select portrait-specific UI JSON by orientation. The referenced layout *files* were not individually inspected here: confirm asset packaging and rendering at runtime.
2. **The duel UI already distinguishes portrait from landscape.** `MatchScreen.FieldScroller` has portrait-specific multi-player panel sizing; `VPlayerPanel`, `VZoneDisplay`, `VPhaseIndicator`, and `CardZoom` have orientation branches. It would be a mistake to discard these before testing them.
3. **Touch interactions are present**, not hypothetical. `GameHUD` creates a libGDX touchpad and touch handlers; combat card tap attempts selection while long-press opens `CardZoom`; `CardZoom` itself has tap/pan/long-press gestures. We should preserve Forge's existing input semantics unless testing shows a need to improve them.
4. **Two UI families coexist.** The Adventure menus use libGDX Scene2D + `UIActor` JSON data, while actual Magic duels, deck editing, and some shops use Forge's mobile `FScreen`/`FDeckEditor` system through `ForgeScene`. A single change to JSON layout files will **not** fix every screen.
5. **Biggest UX risk:** giving the player enough context during a complicated duel (opponent battlefield, current prompt, stack/priority, multiple target selection, attacking/blocking, card text) at ~390x844 CSS px. Other significant risks: tiny hit areas, iOS virtual keyboard, viewport/safe-area assumptions, assets, gesture conflicts, and rich deckbuilding controls. Actual iPhone behavior is **not yet verified**.

## Evidence / source map

Repository paths in the links below are direct pointers to verified source inspected during this investigation; findings about the behavior of the browser port are design hypotheses until running on Safari.

| Observed feature | Verified source |
|---|---|
| Portrait flags and sizing | [`Forge.isLandscapeMode()`](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/Forge.java#L817-L819); [`Scene.getIntendedWidth/Height`](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/adventure/scene/Scene.java#L77-L83) |
| JSON-driven Adventure interface | [`UIActor` creates Scene2D actors from `UIData`](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/adventure/util/UIActor.java#L32-L100); [`UIScene` loads layouts and owns stage input](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/adventure/scene/UIScene.java#L215-L229) |
| Start/new character | [`StartScene`](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/adventure/scene/StartScene.java#L43-L55), [`NewGameScene`](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/adventure/scene/NewGameScene.java#L64-L66) |
| Exploration/HUD | [`GameHUD` portrait HUD and movement touchpad](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/adventure/stage/GameHUD.java#L115-L164); [`HudScene` touch dispatch](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/adventure/scene/HudScene.java#L127-L145); [`TileMapScene` camera position adjusted in portrait](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/adventure/scene/TileMapScene.java#L88-L110) |
| World map and quest log | [`MapViewScene` portrait layout and zoom/overlay controls](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/adventure/scene/MapViewScene.java#L57-L67); [`QuestLogScene`](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/adventure/scene/QuestLogScene.java#L28-L54) |
| Inventory and rewards/shop | [`InventoryScene` portrait and equip/use/delete](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/adventure/scene/InventoryScene.java#L79-L89); [`RewardScene` portrait and card scaling](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/adventure/scene/RewardScene.java#L82-L94); [`ShopScene` is a `ForgeScene`](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/adventure/scene/ShopScene.java#L20-L57) |
| Deck management/editor | [`DeckSelectScene` portrait buttons](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/adventure/scene/DeckSelectScene.java#L39-L65); [`AdventureDeckEditor` extends `FDeckEditor` and supports sections](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/adventure/scene/AdventureDeckEditor.java#L48-L90); [`DeckEditScene` bridges into `FScreen`](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/adventure/scene/DeckEditScene.java#L12-L53) |
| Save/Load menu UX only | [`SaveLoadScene` portrait view and slots](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/adventure/scene/SaveLoadScene.java#L59-L110). Save *persistence* is Muse's investigation, not this report. |
| Duel entry | [`DuelScene` starts Adventure/Commander duel](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/adventure/scene/DuelScene.java#L367-L400); [`ForgeScene` hands input to mobile Forge controls](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/adventure/scene/ForgeScene.java#L33-L53) |
| Battlefield layout | [`MatchScreen` field scroller portrait/landscape branches](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/screens/match/MatchScreen.java#L1160-L1205); [`VField` battlefield row layout and stacking](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/screens/match/views/VField.java#L21-L38) |
| Priority and phases | [`VPrompt` confirmation/cancel and prompt text](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/screens/match/views/VPrompt.java#L87-L121); [`VPhaseIndicator` portrait and landscape branches](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/screens/match/views/VPhaseIndicator.java#L123-L145) |
| Zones, stack and player overview | [`VPlayerPanel` zone tabs](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/screens/match/views/VPlayerPanel.java#L1033-L1060); [`VZoneDisplay` portrait card cascade](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/screens/match/views/VZoneDisplay.java#L74-L109); [`VStack` uses dropdown](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/screens/match/views/VStack.java#L104-L121); [`MatchScreen` tabs for Game/Players/Log/Stack](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/screens/match/MatchScreen.java#L149-L169) |
| Card touch, zoom and combat damage | [`VCardDisplayArea.CardAreaPanel` selects on tap, zooms on long-press](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/screens/match/views/VCardDisplayArea.java#L728-L801); [`CardZoom` portrait paths](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/card/CardZoom.java#L802-L860); [`VAssignCombatDamage` interactive damage allocation dialog](https://github.com/rwZephyr/forge-adventure-web/blob/master/forge-gui-mobile/src/forge/screens/match/views/VAssignCombatDamage.java#L85-L146) |

## Screen/action inventory: current patterns vs proposed portrait behavior

Difficulty estimates are **relative UX/porting risks, not verified bugs**. Reuse decisions are conditional on the client compiling and loading correctly.

| Screen or task | Existing source/input, verified | Portrait plan / reuse choice (proposal) | UX risk |
|---|---|---|---|
| Start / Continue / Resume | `StartScene` uses `start_menu_portrait.json` and named Scene2D actions | Reuse portrait JSON and existing buttons; ensure browser area and touch targets fit | Low |
| Character creation and starter choices | `NewGameScene` has portrait JSON, avatar selectors, mode/difficulty callbacks | Reuse; vertically scroll longer choices; verify form/selectors with virtual keyboard | Medium |
| World exploration | `GameScene`, `WorldStage`, `GameHUD`; touchpad movement | Reuse map renderer/touchpad/HUD; test stop/start behavior, pointer-capture release, pinch vs joystick, browser scrolling | Medium |
| Dungeon / town scene | `TileMapScene` + `MapStage`; portrait camera correction | Reuse renderer and touch controls; watch edge clipping and hotspot discoverability | Medium |
| Map / journal | `MapViewScene` portrait; zoom and overlay options; `QuestLogScene` portrait and list/details | Reuse; prefer segmented list/details on phones, inspect text overflow | Low–medium |
| Dialogue and choices | `MenuScene`, `UIScene`, `Controls` Scene2D dialogs | Reuse dialogs; verify scrolling, long options, close/back, keyboard | Medium |
| Inventory/equipment | `InventoryScene` portrait with equip/use/repair/delete and item buttons | Reuse functionality; finger-sized item selection + separate detail panel; don't require precision dragging | Medium |
| Rewards, treasure, purchases | `RewardScene` uses `items_portrait.json`, card-size adjustments; `ShopScene` uses mobile Forge screen | Keep card grids and full-card preview; verify visible price and confirmation buttons on narrow screen | Medium |
| Deck selection/edit/collection | `DeckSelectScene` portrait; `AdventureDeckEditor` subclasses `FDeckEditor`, Commander/Main/Sideboard | Preserve Forge deck legality/filtering; likely adapt card list, category tabs and menu density rather than port to new HTML | High |
| Save/load/menu | `SaveLoadScene` portrait; `StartScene` Save/Load/Backup/Resume controls | Reuse appearance; ensure users understand local vs remote save once backend exists | Low UX (storage out of scope) |
| Enter AI duel / return | `DuelScene` bridges Adventure into `MatchScreen` and postmatch scene | Preserve this transition; validate rendering/input restored after win/loss and return | High integration |
| Battlefield and hand | `MatchScreen` already has portrait field panels, `VField` rows/stacking and `VZoneDisplay` | First test actual portrait 1v1 without redesign; if crowded, adapt panel sizing and scroll/toggle zones | High |
| Card inspection vs playing a card | `CardAreaPanel.tap()` tries selection; `longPress()` shows `CardZoom` | Preserve selection, offer explicit accessible Inspect action to avoid dependence on hidden long press | High |
| Priority, stack, phase stops | `VPrompt` OK/Cancel, `VStack` menu, `VPhaseIndicator` | Keep always-visible active decision and clear Pass/Resolve action; stack should not cover targets | High |
| Multiple targets / targeting relationships | `MatchScreen` draws targeting arcs; `VCardDisplayArea` card selection | Use tap-to-select with clear numbered target summary and confirm; verify targeting remains legible with overflowed zones | Very high |
| Attacking, blocking, damage order | Forge already has selectable battlefield and `VAssignCombatDamage` | Stage combat choices, clearly show selected attackers/blockers and allow undo/confirm; test wide and crowded boards | Very high |
| 3+ players / Commander duel | `MatchScreen` has multiple `VPlayerPanel` logic; `VPlayers` panel | Treat as a later responsive UX milestone, not MVP; consider active-opponent switcher while preserving state | Very high |

### Input distinctions to preserve

- `GameHUD` touchpad uses a `ChangeListener` and zeroes input on touch release. The map should stop moving when the finger is lifted; test cancel cases when Safari changes focus or a dialog opens.
- `UIScene` supports gamepad/keyboard selection and Scene2D touch. Browser changes should not remove keyboard or desktop input. Its keyboard-navigation machinery is not a replacement for appropriate touch targets.
- Combat card activation is context-dependent: `CardAreaPanel.tap` calls `selectCard` first, then zooms when no selection is possible; long-press zooms directly. A general UX change to "tap always inspects" would break game interactions, so prefer optional inspect button/gesture.
- `VPrompt` supplies OK/Cancel and prompt text. Avoid hiding the current prompt or priority state behind a menu.
- Existing portrait card zoom and card-zone cascade make a **test-first reuse** strategy sensible. The main risk is small browser viewports rather than lack of mobile UI logic.

## Portrait wireframes (design concepts, not current screenshots)

**A. Overworld (reuse existing HUD; adaptive safe-area):**

```text
┌─────────────────────────┐
│ Life  Gold   Shards   ☰ │ ← top safe-area + status
├─────────────────────────┤
│                         │
│     WORLD / DUNGEON     │
│     real libGDX map     │
│                         │
│   ◉ move       [interact]│ ← thumb-size buttons
│                         │
├─────────────────────────┤
│ Map  Deck  Bag  Quests  │ ← existing actions
└─────────────────────────┘
```

**B. Duel (preserve Forge match controller, layer visible decision context):**

```text
┌─────────────────────────┐
│ Opponent 18   [Stack 2] │
├─────────────────────────┤
│ Opponent field (scroll) │
│  [creatures] [lands]    │
├─────────────────────────┤
│ My field (scroll/zoom)  │
│ [creatures] [lands]     │
│                        │
│ Hand / zones (expand)   │
├─────────────────────────┤
│ Choose blockers: 2/3    │ ← contextual message
│ [Undo]   [Confirm/Pass] │ ← VPrompt concepts
└─────────────────────────┘
```

**C. Card inspection / targets:** tap card when actionable = Forge selection; long press or explicit Inspect icon = existing `CardZoom`. In a targeting prompt show selected object(s) and required count; do not make users infer selection from tiny highlights or moving arrows alone.

These sketches are **requirements and layout hypotheses**, not an assertion that existing Forge renders like this. Prefer testing/adapting current `FScreen` layouts before introducing any overlay subsystem.

## Prioritized difficulties and recommended scope

**P0 — prove usability before redesign**
1. Real portrait Adventure overworld controls and browser-safe viewport: touchpad movement, interact, HUD menu, map, safe areas, background/foreground.
2. Real portrait **one-on-one AI duel**: hand, field, card zoom, priority/stack, simple cast/target and attack/block actions.
3. Back-and-forth to Adventure: after a battle, inspect reward screen and resume control.

**P1 — address actual failures**
- Visual hierarchy / card readability: long press and zoom discoverability, larger detail view, scroll zones, selected-card highlights.
- Input resilience: accidental browser pinch/scroll/back, long press interpreted as text selection, stuck movement on touch cancel, virtual keyboard over controls.
- Overflow: tiny opponent panels, many tokens, equipment/auras, multiple triggers and choices, long names or translated labels.
- Touch accessibility: aim for approximately 44 CSS px touch targets for primary actions where possible; use contrast/shape in addition to color cues.

**P2 — advanced scenarios**
- Dense deck editor, filters, quantity changes, equipment and commerce lists.
- Commander multiplayer or 3+ player boards: separate design/testing track after 1v1 works.
- Responsive desktop / iPad layouts and optional landscape presentation.

## First human-usable UX milestone / acceptance checklist

**Scope:** unmodified upstream single-player Adventure/Realm of Legends at an iPhone portrait viewport, *once Codex can deliver a running game client*. No accounts, saves, shared worlds, or co-op in this UX milestone.

1. Launch `StartScene` in Safari portrait. Inspect all labels and controls for clipping and sensible zoom/scaling.
2. Use `NewGameScene` to select difficulty, mode, avatar/starter, and start campaign. Try rotating device and restoring portrait.
3. Walk around world using touchpad, release to stop, open map, zoom/pan map, enter a POI/dungeon. Test one-handed input.
4. Open inventory, inspect an item, equip/use where possible, return without losing position. Test quest log and dialogue choices.
5. Trigger AI duel. Inspect an opponent card and a hand card using the existing zoom action. Attempt cast, pay mana, select target, respond to priority, and inspect the stack.
6. Declare attackers and blockers, withdraw one selection, confirm, and test a multi-block or multi-target scenario where the UI allows it.
7. Win/lose encounter; view reward/loot; confirm return to Adventure HUD and responsive controls.
8. Edit a deck and inspect a card while using filters or sections. Test Commander tab only when available in that scenario.
9. Test Safari dynamic address bars, notch/home indicator, suspend/resume after 30+ seconds, device rotation and return, accidental system gestures, and a tiny phone viewport.
10. Check touch hit size, layout clipping, long text readability, labels without color-only cues, and responsiveness in a crowded battlefield (tokens/attachments).

Record each with device/iOS/browser version, CSS viewport + DPR, exact action, pass/fail, screenshot or video, and Forge source build/commit. **No such test has yet been performed.**

## Recommendations for Codex integration

- **Do not commission a complete replacement UI yet.** The source already contains a portrait implementation for core Adventure and duel screens.
- First ask Codex to preserve and instantiate the existing libGDX `Scene`/`UIScene` and `ForgeScene`/`MatchScreen` view types; this reduces port scope if dependencies permit.
- Verify where runtime `ui/*_portrait.json` layout files reside and whether they are delivered in the browser asset bundle. Their code references are verified here, their bytes/rendering were not audited.
- Treat viewport-size adaptation independently from engine/renderer compatibility. A browser build could load successfully but be unreadable, or be usable in native Android portrait but not run in Safari.
- Avoid mixing this report with save/auth/deployment changes; those are a separate research track.

## Research limitations

This investigation read code through the GitHub repository connector. It **did not** clone/build Forge, launch a game, inspect assets visually, execute in TeaVM, run Safari, or measure touch sizes. Some Adventure UI JSON files are external runtime assets and were not individually fetched. References document source observations, not proven runtime behavior. The proposed wireframes and severity estimates are design judgments requiring physical-device testing.
