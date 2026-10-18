# Muse — Campaign saves, player identity and self-hosting (research report)

Branch: `research/muse-saves-hosting`. Research only: no game-code changes, no TeaVM/GWT
toolchain investigation (Codex's lane), no portrait UI (Claude's lane), no deployments or
account registrations. All claims below are grounded in the fork's source at
`db893dcd` (fork of `Card-Forge/forge`, `master`); anything not verified from source is
marked **[assumption]** or **[unverified]**.

## 0. TL;DR

- A Forge Adventure campaign save is already a **single self-contained byte blob per
  slot**: zlib-deflated Java serialization of a `WorldSaveHeader` plus a
  `SaveFileData` map (`player`, `world`, `worldStage`, `pointOfInterestChanges`).
- Overworld-only saving is the existing, deliberate semantic (saving is blocked inside
  dungeon maps; autosaves fire on POI/duel entry and post-defeat respawn — never
  mid-battle). The product requirement "preserve overworld saving, no mid-battle
  saves" matches what the code already does. **No format change is needed.**
- The cheapest path to persistent browser campaigns is a **storage-backend swap, not a
  format migration**: keep the blob bytes identical; change only where bytes are
  written. All save I/O funnels through `WorldSave.getSaveDir()/getSaveFile()` and
  raw `java.io.File` — a small, well-defined seam.
- Recommended staging: **(1)** browser-local MVP (IndexedDB + `.sav` export/import,
  zero server, static hosting) → **(2)** optional thin save service (per-user
  directories, same bytes, login binds slots to accounts) → **(3)** community
  self-host packaging. Multi-user is a solved-by-isolation problem: slots are already
  fully independent snapshots; there is no shared world state to synchronize.
- Biggest open dependency (flagged, not investigated — Codex's track): whether
  `java.io.ObjectOutputStream` / `DeflaterOutputStream` survive the browser compiler
  toolchain. If the game ends up running as Java on a server with a thin browser
  client, saves need **zero** changes.

---

## 1. Grounded map of the existing save system

### 1.1 Entry points

| Symbol | File | Role |
|---|---|---|
| `WorldSave.save(String text, int slot)` | `forge-gui-mobile/src/forge/adventure/world/WorldSave.java` | Single write path for manual/auto/quick saves |
| `WorldSave.load(int slot)` | same | Single read path; returns `false` if file missing/unreadable |
| `WorldSave.generateNewWorld(...)` | same | Character creation → new campaign (called from `NewGameScene`) |
| `WorldSave.autoSave()` / `quickSave()` / `quickLoad()` | same | Convenience wrappers |
| `SaveLoadScene` | `forge-gui-mobile/src/forge/adventure/scene/SaveLoadScene.java` | Slot UI (10 manual slots + auto + quick), save/load dialogs |
| `SaveFileData` | `forge-gui-mobile/src/forge/adventure/util/SaveFileData.java` | `HashMap<String, byte[]>`; per-key Java-serialized values; nested sub-maps |
| `SaveFileContent` | `forge-gui-mobile/src/forge/adventure/util/SaveFileContent.java` | Interface: `load(SaveFileData)` / `save()` |
| `WorldSaveHeader` | `forge-gui-mobile/src/forge/adventure/world/WorldSaveHeader.java` | Save name, preview image, save date (custom `writeObject`) |

### 1.2 Slots, filenames, directories

- `WorldSave.filename(int slot)`: slot 1–10 → `"{slot}_save_slot.sav"`; `-1` →
  `auto_save.sav`; `-2` → `quick_save.sav` (`AUTO_SAVE_SLOT`, `QUICK_SAVE_SLOT`).
- `filenameToSlot(String)` / `isSafeFile(String)` validate names before listing —
  the slot UI only ever opens names matching this scheme (**verified** in
  `SaveLoadScene.updateFiles()`).
- `getSaveDir()` = `ForgeConstants.USER_ADVENTURE_DIR + Config.instance().getPlane()`,
  where `USER_ADVENTURE_DIR = USER_DIR + "adventure/"`
  (`forge-gui/src/main/java/forge/localinstance/properties/ForgeConstants.java:260`)
  and `getPlane()` returns the active plane folder name (`Config.java:274`).
  **Saves are already namespaced per plane** (Shandalar vs. Realm of Legends live in
  separate directories). Any multi-user design must preserve this namespacing:
  user → plane → slot.
- `settings.json` in `USER_ADVENTURE_DIR` holds `SettingData` (active plane,
  `lastActiveSave` filename, display prefs) — `Config.java:70`. It is the only other
  user-mutable file in the adventure dir besides saves and `<user>` planes.

### 1.3 Byte format (verified from `WorldSave.save`/`load`)

Write pipeline: `FileOutputStream` → `DeflaterOutputStream` (zlib DEFLATE) →
`ObjectOutputStream`. Read pipeline is the exact reverse
(`FileInputStream` → `InflaterInputStream` → `ObjectInputStream`).

Stream contents, in order:
1. `WorldSaveHeader` — custom `writeObject`: `name` (UTF), `preview` (Pixmap
   encoded as PNG via `Serializer.WritePixmap`), `saveDate` (`java.util.Date`).
2. `SaveFileData` "mainData" — a `HashMap<String, byte[]>` with four keys, each a
   nested Java-serialized `SaveFileData`:
   - `"player"` → `AdventurePlayer.save()` (`player/AdventurePlayer.java:798`)
   - `"world"` → `World.save()` (`world/World.java:127`)
   - `"worldStage"` → `WorldStage.save()` (`stage/WorldStage.java:476`)
   - `"pointOfInterestChanges"` → `PointOfInterestChanges.Map.save()`

`SaveFileData` stores each value with its own `ObjectOutputStream` into a
`byte[]`, so the map is a map of individually-serialized blobs. Scalar helpers
exist for `String/int/long/float/double/boolean/Vector2/Rectangle/Pixmap`;
arbitrary objects go through `storeObject` (plain Java serialization).

### 1.4 What's inside each blob

**Player** (`AdventurePlayer.save`): statistics, full difficulty parameter set,
identity (name, race, avatar index, gender, color identity), adventure mode
(`AdventureModes`: Standard, Constructed, Chaos, Pile, Custom, Commander, Precon,
CommanderPrecon), fantasy/custom-deck flags, world position, gold/life/maxLife/
shards, inventory (`ItemData[]`), equipped items, owned boosters (`Deck[]`),
blessing, character flags, quest flags, quests, events, active + stored decks
(**stored as card-name strings**, re-resolved against the card DB on load),
deck loadouts, selected deck index, card collection (**name strings**),
new/favorite/auto-sell cards (as `PaperCard[]` objects).

**World** (`World.save`): `biomeImage` (Pixmap → PNG bytes), `biomeMap`
(`long[][]`), `terrainMap` (`int[][]`), width/height, map object/POI id tables,
`seed`. The generated tile arrays are stored in full, not just the seed (the seed
is retained and used as a regeneration fallback — see 1.6).

**WorldStage** (`WorldStage.save`): live enemy list as parallel arrays —
respawn timeouts, **enemy names**, x/y positions, quest stage IDs — plus
`globalTimer`. On load, enemies are re-instantiated by name via
`WorldData.getEnemy(name)` (`WorldStage.load`, line 441+): enemy *definitions*
come from current resources, the save only pins *which/where/when*.

**POI changes**: delta journal of point-of-interest state (visited, cleared,
bookmarked, looted) keyed by POI id.

### 1.5 Save/load triggers (the overworld-only semantic)

- **Manual save**: `SaveLoadScene.save()` → `WorldSave.save(...)`. Explicitly
  refused inside dungeon maps: `TileMapScene.instance().currentMap().isInMap()`
  shows "game not saved" — comment in source: *"Saving needs to be disabled
  inside maps until we can capture and load exact map state. Otherwise location
  based events for quests can be skipped by saving and then loading outside the
  map."* (**verified**, `SaveLoadScene.java` `loadSave()`/`save()`).
- **Autosave** fires at exactly three overworld transition points (**verified**):
  1. entering a point of interest (`WorldStage.java:234`, shows "A U T O S A V E"),
  2. entering a duel with a mob (`WorldStage.java:144`),
  3. after defeat respawn at a POI (`GameStage.java:724`).
- There is **no mid-battle save** anywhere in the adventure module. The product
  constraint "preserve overworld saving; long-term mid-battle saves unnecessary"
  is therefore already the code's behavior — nothing to remove or retrofit.

### 1.6 Crash safety and version tolerance (verified)

- **Interrupted writes**: `save()` renames any existing file to `.old` *before*
  writing; on `IOException` (or a serialization error detected mid-build) it calls
  `restoreBackup()` and deletes the partial file; on success the `.old` is
  deleted. Single-writer, last-good-wins. No checksums — corruption is detected
  only by deserialization failure.
- **serialVersionUID drift**: `SaveFileData.DecompressibleInputStream` overrides
  `readClassDescriptor()` to substitute the *local* class descriptor when the
  stream's SUID mismatches (adapted from a known StackOverflow recipe, cited in
  source). Old saves load best-effort against new classes; a corrupted key shows
  "SaveFileData Error — Press Resume on the Main Menu to try with missing {key}".
- **Total world-load failure**: `WorldSave.load()` catches exceptions from the
  world/stage/POI section and falls back to `world.generateNew(0)` rather than
  refusing to load at all.
- **Resource evolution**: decks, collection, and enemies are referenced **by name**
  and re-resolved against the *current* card DB / `world/*.json` at load. Card DB
  updates and enemy rebalancing do not break saves; Java-serialized objects
  (`PaperCard`, `ItemData`, `Deck`, quest/event objects) are the version-sensitive
  surface, mitigated by the SUID override above.

### 1.7 What does NOT exist

- **No export/import.** The only data-out path in the adventure module is "copy
  collection to clipboard" in the deck editor (`AdventureDeckEditor.java:535`).
  A `.sav` file is already portable as bytes, so export/import is a small UI +
  download/upload feature, not a format project.
- **No accounts, no server, no multi-user concepts.** Slots are a flat per-plane
  namespace; "player identity" today = the character name typed at creation
  (`NewGameScene` → `generateNewWorld(name, ...)`).
- Save I/O uses **`java.io.File` directly everywhere** (not libGDX `Gdx.files`).
  This is the porting seam: on any browser toolchain without a real filesystem,
  these call sites need a storage backend (see §3). Flagging the dependency for
  Codex's compiler track — not investigated here.

---

## 2. MVP storage models: (a) browser-local vs (b) server-managed

### Model A — Browser-local storage + export/import (recommended for MVP)

How: keep the blob bytes **bit-identical**. Persist per-slot bytes in
**IndexedDB** (key: `{plane}/{slot}`), with explicit Export (download `.sav`)
and Import (file upload → validate via `isSafeFile`-equivalent → store) actions
in the save UI. `settings.json` equivalent (active plane, last active save) goes
in the same store.

| Concern | Assessment |
|---|---|
| Capacity | localStorage (~5 MB) is **unsuitable** — a save embeds a PNG biome image plus full `long[][]`/`int[][]` tile arrays; size is **[unverified]** but plausibly low-single-digit MB. IndexedDB quota (Safari: disk-backed, origin quota, typically hundreds of MB) is the correct target. **Must measure a real save before committing to any limit.** |
| Safari eviction | Safari may evict origin storage under pressure and applies a 7-day cap to script-writable storage for sites without user interaction. Mitigations: export/import as the durable backup story from day one; "last saved" timestamp + storage-persistence prompt (`navigator.storage.persist()`); document that the browser copy is a cache, the exported `.sav` is the archive. |
| Interrupted writes | IndexedDB transactions are atomic — strictly better than today's `.old`-rename dance, which should be retained only for the file-based (self-host server / desktop) backend. |
| Migrations | Blob is opaque bytes; migrations = SUID-tolerant deserialization (already built) + per-plane namespacing. No schema migrations to write. |
| Backups/recovery | Export `.sav` (identical to desktop format — a desktop Forge could even read it **[assumption: same class versions]**); optional auto-export to Downloads on save **[UX decision]**. |
| Cost to self-host | **Zero server.** Static file hosting (any HTTPS static host) + the game bundle. This best matches "easy for other Forge enthusiasts to self-host." |

### Model B — Server-managed saves behind accounts (later stage)

How: a thin save service. Authenticated user → server-side per-user directory
`{userId}/{plane}/{slot}.sav` (or object-store keys with the same structure);
the game client PUTs/GETs the identical blob bytes. The Java `SaveStorage`
abstraction (see §3) gets an HTTP implementation; the game code is unchanged
above that seam.

| Concern | Assessment |
|---|---|
| Cross-device / durability | Solves Safari eviction; the server copy is the archive. |
| Isolation | Trivially satisfied: the OS/filesystem (or key prefix) is the boundary. Server must re-validate slot filenames server-side (never trust client paths — path traversal), enforce quotas, and require auth on every save endpoint. |
| Cost to self-host | Higher: needs the game bundle host **plus** the save service, TLS, an account system, backups, abuse controls. Still modest (single small service, no realtime anything), but it is a second moving part. |
| Login providers | Avoid commercial hosted identity as a hard dependency (project constraint). Options in increasing weight: local username+password (self-hosted), magic-link email, or pluggable OAuth (GitHub/Google) as *optional*. Recommend starting with local accounts; the save service should not care which. |

### Comparison summary

| | A: browser-local | B: server-managed |
|---|---|---|
| Persistent campaigns | Yes (IndexedDB + export) | Yes (server copy) |
| Survives Safari eviction | Only via exported `.sav` | Yes |
| Cross-device | Via manual export/import | Yes |
| Server to operate | None | Small save service + auth |
| Self-host difficulty | Trivial (static files) | Moderate |
| Format change required | None | None |

**Recommendation: ship A first, add B as an opt-in stage.** Both use identical
bytes; A→B migration is literally uploading the IndexedDB blobs. Do not design
or build B before browser gameplay works (per `docs/research/README.md`: login/
cloud save comes *after* gameplay works).

---

## 3. Staged migration plan (minimal, no shared world)

### The one architectural seam

Every save byte currently passes through `WorldSave.getSaveDir()/getSaveFile()`
and `java.io.File`. The minimal intervention is a narrow storage interface used
only by `WorldSave` (and `Config` for `settings.json`):

```
Game code            WorldSave.save()/load()
     |                        |
     v                        v
SaveStorage.write(slot, bytes) / .read(slot) / .list() / .delete(slot)
     |                        |
     +-- FileSaveStorage      +-- IndexedDbSaveStorage      +-- HttpSaveStorage
        (desktop/today,            (browser MVP,                 (later: PUT/GET
         java.io.File)              toolchain interop)           per-user saves)
```

Data flow (browser MVP):

```
[Adventure overworld] --save()--> [WorldSave: build blob bytes, unchanged]
      --> [SaveStorage.write("Shandalar/3_save_slot.sav", bytes)]
      --> [IndexedDB, atomic transaction]
[Load] reverses the path; Export streams the bytes as a .sav download;
Import validates the name (isSafeFile-equivalent) and stores the bytes.
```

Data flow (later, server stage):

```
[Browser] --PUT /saves/{user}/{plane}/{slot}--> [save service]
      --> [disk: /data/saves/{userId}/{plane}/{slot}.sav]
Login binds {user}; the service never lists or reads across users.
```

No game-logic change is proposed at any stage. In particular: **no shared world,
no enemy synchronization, no shared loot, no co-op** — each slot remains a fully
independent snapshot, exactly as today. Multi-user support is purely
*namespacing* (user → plane → slot), which is why it can wait until after
gameplay works.

### Stage 0 — measure (before writing code)
1. Produce a real `.sav` from desktop/mobile Forge; record sizes (per plane,
   early vs late game). This decides IndexedDB headroom and whether the PNG
   biome image should be excluded from the blob later (**[unverified]** size is
   the largest unknown in this report).
2. Confirm which `java.io` APIs the chosen browser toolchain supports
   (Codex's track) — specifically `ObjectOutputStream`, `ObjectInputStream`,
   `DeflaterOutputStream`/`InflaterInputStream`, `java.io.File`.

### Stage 1 — browser-local MVP
3. Implement the `SaveStorage` seam with `FileSaveStorage` preserving today's
   behavior byte-for-byte (desktop regression: existing saves must still load).
4. Add `IndexedDbSaveStorage` behind the same interface for the browser build.
5. Add Export/Import `.sav` to the save UI; persist `settings.json`-equivalent
   (plane, last active save) in the same store.
6. Acceptance tests §5, items 1–5, 7–9.

### Stage 2 — optional save service (only after gameplay works)
7. Minimal HTTP service: `PUT/GET/DELETE /saves/{plane}/{slot}`, auth on every
   route, per-user directory isolation, quotas, rate limits. Same bytes.
8. Login: start with self-hosted local accounts; keep the provider pluggable.
9. Migration path for Stage-1 users: import their exported `.sav` files.
10. Acceptance tests §5, items 6, 10.

### Stage 3 — community self-host packaging
11. One container/compose file: static game bundle + optional save service
    (disabled by default → pure static hosting = Stage 1 behavior).
12. Documented backup story (server data dir / object store), update procedure
    (replace bundle; saves are version-tolerant per §1.6), and the security
    boundaries in §4.

---

## 4. Threats, risks, safeguards

- **License (GPL-3.0).** The repo root `LICENSE` is GPL version 3. Any distributed
  web port — client JS bundle and, if one is operated/distributed, a Java save
  backend derived from this code — must preserve GPL compliance: keep copyright/
  attribution, ship or offer the corresponding source of the port, no proprietary
  relicensing. Community self-hosting is compatible with this; "just the APK/site,
  no source" distribution is not. (Not legal advice; owner to confirm.)
- **Save-service abuse.** Arbitrary byte storage behind auth: enforce per-user
  quotas (bounded by measured save size × slots), rate-limit writes, cap request
  bodies, authenticate every route. Never serve uploaded bytes with an executable
  content type; serve `.sav` downloads as `application/octet-stream`.
- **Data isolation.** The security boundary is the user directory/key prefix.
  Never construct filesystem paths from unauthenticated client input; re-validate
  slot filenames server-side with the same strictness as `filenameToSlot`
  (reject `..`, separators, unexpected extensions). One user's `list()` must be
  incapable of naming another user's files.
- **Service exposure.** Bind the save service to localhost / private network by
  default behind the owner's existing reverse proxy; do not expose it or change
  home-network services without explicit approval (per `AGENTS.md` working rules
  and `docs/PROJECT.md` environment notes re: the Silent machine).
- **Save compatibility across updates.** The SUID override is best-effort, not a
  guarantee: a refactor that renames classes/fields can still break loads. Keep
  the "resume with missing data" path working, and treat "old save loads after
  update" as a release acceptance test (§5.5).
- **Browser storage loss.** Safari eviction is the top data-loss risk in Model A;
  the export story is the safeguard, not an afterthought. Never silently discard:
  quota errors must surface as user-visible errors, never silent truncation.

---

## 5. Acceptance tests

1. **Durability (browser):** save in the overworld → fully close the browser →
   reopen the URL → campaign resumes with identical position, gold, life, deck,
   quest state, and POI changes.
2. **Export/import round-trip:** export slot 3 as `.sav` → import into a clean
   browser profile → loads and plays identically.
3. **Interrupted write:** kill the process mid-save (desktop) / fail the IndexedDB
   transaction (browser) → previous save intact and loadable; no zero-length or
   half-written slot file is listed.
4. **Slot + plane isolation:** 10 manual slots, auto, and quick coexist; a
   Shandalar save never appears in the Realm of Legends slot list and vice versa.
5. **Forward compatibility:** a save written by the current build loads after a
   code update that bumps a `serialVersionUID` (exercises the SUID-override
   path); missing keys degrade via the "resume with missing data" dialog.
6. **Multi-user isolation (Stage 2):** user A's slot list, reads, writes, and
   deletes are invisible to and unaffected by user B; unauthenticated requests
   to `/saves/...` are rejected.
7. **Quota behavior:** with storage nearly full, saving surfaces a clear error;
   no silent data loss, and the previous good save remains.
8. **Size budget:** measured `.sav` sizes (early/late game, per plane) are
   recorded and fit IndexedDB quota with ≥10× headroom; if not, the biome-image
   exclusion is designed before MVP.
9. **Settings migration:** active plane and last-active-save survive an app
   update (the `settings.json`-equivalent migrates or is re-derivable).
10. **Concurrent tabs (documented behavior):** two tabs on the same campaign —
    last-write-wins is explicitly documented, or a tab-level lock warns the user.

## 6. Explicit uncertainties

- **[Unverified]** Real `.sav` file sizes (could not run the game in this
  environment). Everything about storage headroom depends on measuring this.
- **[Not investigated — Codex's track]** Whether TeaVM/GWT supports
  `java.io.ObjectOutputStream`, `ObjectInputStream`,
  `DeflaterOutputStream`/`InflaterInputStream`, and what replaces `java.io.File`.
  If serialization doesn't survive the toolchain, the fallback is a second
  serializer for the `SaveFileData` map (its structure is simple enough to
  re-encode) — a contained change, but a change.
- **[Assumption]** The fork's adventure module is unmodified upstream save logic
  (no fork-specific save changes found; `git log` on the fork shows only docs
  scaffolding so far — **[spot-checked, not exhaustively audited]**).
- Safari's exact eviction/quota behavior for this workload; `navigator.storage.persist()`
  grant rates.
- Whether a desktop-written `.sav` loads in the browser build depends on class
  parity between the two builds (**[assumption]**: same classes, same names).
- Realm of Legends support in the current upstream build (**[unverified]** —
  `AGENTS.md` conditions single-player scope on "if the current upstream build
  supports it"; plane list is data-driven from `res/adventure/`).

---

*Report by Muse (A-team, saves/hosting track). Sources: files and line references
above, verified against `rwZephyr/forge-adventure-web@db893dcd`. No game code was
modified. Open questions for the owner are in §6; cross-track dependency is
Codex's toolchain verdict on `java.io` serialization (§3, Stage 0.2).*