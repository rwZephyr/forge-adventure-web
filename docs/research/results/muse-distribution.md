# Muse — Community distribution and asset licensing (research report)

Research only: no game-code changes, no shared-document edits, no existing-report
modifications. All measurements were taken from the local clone of
`rwZephyr/forge-adventure-web` at `db893dcd` (fork of `Card-Forge/forge`,
`master`) with `du`; all code references are to that tree. Claims not verified
from source are marked **[assumption]** or **[unverified]**. **No definitive
legal-clearance claims are made here** — this is a risk inventory for the owner
(and counsel, if it ever matters), not a legal opinion. Work is independent of
Codex's TeaVM track and the portrait-UI track.

Direction sources read: root `AGENTS.md`, `docs/PROJECT.md`,
`docs/research/INTEGRATION.md` (PR #6 synthesis).

## 0. TL;DR

- A browser distribution is dominated by **data, not code**: `forge-gui/res/` is
  **471 MB** tracked in git (55,659 files). The adventure module itself needs a
  large subset: `adventure/` (155 MB), the card database `cardsfolder/`
  (134 MB, 34,088 per-card `.txt` files), plus skins/audio/languages.
- **Card images are never bundled.** Forge downloads them on demand from the
  Scryfall API (`api.scryfall.com/cards/named?fuzzy=…&format=image`) into a local
  cache dir. A browser build can do the same — which keeps WotC artwork out of
  the distributed bundle entirely.
- Licensing is layered and uneven: **GPL-3.0** source (clean, with copyleft
  obligations); mostly Apache-2.0/MIT third-party deps with one flag
  (**jgrapht**, EPL-2.0/LGPL-2.1, used in the rules engine); carefully attributed
  community art/music with mixed license verifiability; and **WotC IP** (card
  names, oracle text, artwork) with **no documented permission in the repo** —
  the largest unresolved risk, shared with every Forge distribution, not unique
  to the web port.
- Smallest legally supportable prototype: static hosting, GPL-compliant source
  availability, full attribution shipped, card art fetched client-side from
  Scryfall (not bundled), trimmed languages, and music limited to
  verifiably-licensed tracks (or shipped silent initially). Details in §7.

---

## 1. Asset inventory (measured)

All sizes from `du -sh` on the working tree. `forge-gui/res/` is **fully
git-tracked** (verified: `git ls-files forge-gui/res | wc -l` = 55,659) — there
is no separate asset-download step for the base game; the repo *is* the asset
bundle.

### 1.1 Adventure planes — `forge-gui/res/adventure/` = 155 MB

| Plane | Size | Notes |
|---|---|---|
| `common/` | 102 MB | Shared by all planes (see 1.2) |
| `Innistrad` | 22 MB | |
| `Shandalar Old Border` | 18 MB | |
| `Realm of Legends` | 12 MB | The product contract names this as desired |
| `Shandalar` | 2.0 MB | Small — most content lives in `common/` |
| `Crystal_Kingdoms` | 764 KB | |
| `Amonkhet` | 288 KB | |

### 1.2 Shared adventure content — `adventure/common/` = 102 MB

| Category | Size | Contents (verified listing) |
|---|---|---|
| `music/` | 55 MB | Per-biome `.mp3` tracks (black/blue/boss/castle/cave/…) |
| `maps/` | 16 MB | TMX tile maps |
| `sprites/` | 8.9 MB | Sprite atlases (`.atlas` + `.png`) |
| `custom_card_pics/` | 7.5 MB | Artwork for custom/adventure cards **[assumption: bundled custom art]** |
| `skin/` | 5.4 MB | libGDX UI skin (`ui_skin.json` + atlases) |
| `ui/` | 4.6 MB | UI layouts (`save_load.json`, portrait variants, …) |
| `decks/` | 2.7 MB | Starter/precon decks (`.dck`) |
| `world/` | 1.9 MB | Plane data: `world.json`, `enemies.json`, `shops.json`, `heroes.json`, `points_of_interest.json`, `items.json`, `quests.json`, `cardprices.txt` |
| `particle_effects/` | 480 KB | Includes `Particle Park License.txt` |
| `custom_cards/` | 288 KB | Custom card definitions |

### 1.3 Card database — `forge-gui/res/cardsfolder/` = 134 MB

- **34,088** per-card `.txt` files, organized `a/`–`z/` subdirectories (verified
  count via `find … -name "*.txt" | wc -l`).
- Forge's own scripting format (`Name:`, `ManaCost:`, `Types:`, `T:`, `SVar:`,
  `Oracle:` — verified by reading a sample file). The *format and scripting*
  are community-authored; the `Oracle:` text reproduces WotC card text (§4).
- `editions/` (5.9 MB): set metadata. `formats/`, `blockdata/` likewise
  support the DB.

### 1.4 Not bundled: card images

- **No `pics/` directory exists in the repo** (verified). Card, token, and
  booster artwork is downloaded on demand — see §5.
- This is the single most fortunate fact for distribution: the heaviest and
  most IP-sensitive asset class is already externalized by design.

### 1.5 Other `res/` categories (mostly not needed for Adventure)

| Category | Size | Needed for Adventure? |
|---|---|---|
| `languages/` | 63 MB | 18 files (card names per locale) — trimmable; English-only is a fraction **[unverified exact per-file sizes]** |
| `quest/` | 38 MB | Quest mode — not Adventure |
| `skins/` | 18 MB | Desktop/mobile UI skins; Adventure uses `adventure/common/skin` + `ui` |
| `music/` | 17 MB | Non-adventure soundtrack (`Credit.txt`: Kevin MacLeod, CC BY 3.0) |
| `deckgendecks/` | 11 MB | Deck generation — Adventure uses `adventure/common/decks` |
| `conquest/`, `draft/`, `puzzle/`, `cube/`, `geneticaidecks/`, `tokenscripts/`, `lists/` | ~20 MB combined | Other game modes — not Adventure |
| `sound/` | 1.1 MB | Shared SFX — likely needed |
| `effects/` | 1.6 MB | Particle/shader effects — likely needed |

### 1.6 Bundled vs downloaded vs generated

| Class | Status | Mechanism |
|---|---|---|
| Source code | Bundled (git) | GPL-3.0 |
| Adventure planes, sprites, maps, UI, decks, world JSON | Bundled (git, `res/adventure/`) | Community art, mixed licenses (§4) |
| Card database (`cardsfolder/`) | Bundled (git) | Community scripting; contains WotC oracle text |
| Card/token artwork | **Downloaded on demand** | Scryfall API → local cache (§5) |
| Adventure music/SFX | Bundled (git) | Attributed; per-track licenses uneven (§4) |
| Compiled JS/WASM bundle | **Generated** | Build artifact of the web port (does not exist yet) |
| Save files | Generated at runtime | `.sav` blobs; `.adv` full backups (§1.7 of saves report) |
| `settings.json`, price caches | Generated/downloaded | `GuiDownloadPrices`, Scryfall bulk sync |

### 1.7 What dominates a browser bundle

Code is negligible: `forge-gui-mobile` 9.3 MB, `forge-game` 6.7 MB,
`forge-ai` 2.8 MB, `forge-core` 1.5 MB (working-tree sizes, mostly source).
**Distribution weight is ~99% assets.** A Shandalar-only adventure bundle at
current contents is roughly: `common/` 102 + `Shandalar/` 2 + `cardsfolder/`
134 + skins/sound/effects ~21 + one language ~4 ≈ **~260 MB** before any
trimming — and music alone is 55 MB of the common dir. Realm of Legends adds
12 MB. These are measured inputs; §7 discusses what can be cut.

---

## 2. Licensing and attribution matrix

| Layer | License / status | Evidence | Distribution implication |
|---|---|---|---|
| Forge source code | **GPL-3.0** | Root `LICENSE` (GPL v3, 29 June 2007); per-file GPL headers (e.g. `GuiDownloadPicturesHQ.java`) | Copyleft: distributing the web bundle (JS) = conveying the work → must preserve copyright notices and make corresponding source available. No proprietary relicensing. |
| libGDX (gdx, gdx-ai, gdx-box2d, gdx-freetype, controllers) | Apache-2.0 **[well-established upstream license; not re-verified in-repo]** | `pom.xml` deps `com.badlogicgames.gdx` | Permissive; attribution via NOTICE conventions. Compatible with GPL-3.0 distribution. |
| guava, commons-lang3/math3/text, slf4j-api, tinylog | Apache-2.0 / MIT | `pom.xml`; `res/licenses/{tinylog,xpp3,xstream,multiline-label}-license.txt` | Permissive; keep bundled license texts. |
| sentry-java | MIT **[upstream; not re-verified]** | `pom.xml` `io.sentry:sentry` | Permissive. Note: telemetry in a browser bundle should be opt-in/disabled — product decision, not legal. |
| textratypist, tenpatch | Apache-2.0 **[upstream]** | `pom.xml` | Permissive. |
| **jgrapht-core** | **EPL-2.0 / LGPL-2.1 dual** **[upstream; flag]** | `forge-game/pom.xml:33`; used in `forge-game/.../GameAction.java` (rules engine path → in any gameplay bundle) | Weak copyleft in the distributed artifact. For a compiled-to-JS bundle this needs explicit review — do not assume "it's just a library". Flagged, not resolved. |
| Adventure sprites/tiles | Mixed; artist-owned, "respective licence" terms | `docs/Credit-and-Thanks.md`: wampastompa, iknowkingrabbit, Elthen, DeepDiveGameStudio, krishna-palacio, opengameart.org (+ linked tile-credit spreadsheet) | Attribution is documented; **per-asset license terms are not all verifiable from the repo**. Redistribution inherits each artist's terms. |
| game-icons.net icons | CC BY 3.0 | `docs/Credit-and-Thanks.md` | Attribution required; documented. |
| Adventure music | Attributed; licenses uneven | `docs/Credit-and-Thanks.md` lists artists (Vindsvept, Fiechter, Logan Epic Canto, Kevin MacLeod…); only `res/music/Credit.txt` states a license (CC BY 3.0, MacLeod). **No per-track license file found under `adventure/common/music/`** | Highest-risk bundled asset class after WotC IP: 55 MB of music with incomplete license traceability in-repo. |
| Particle Park effects | Licensed (text included) | `adventure/common/particle_effects/Particle Park License.txt` | Keep the file with any distribution. |
| XMage targeting arrows | Permission granted | `docs/Credit-and-Thanks.md` ("thanks to the XMage team for permission") | Permission scope **[unverified]** — was it for Forge generally or the desktop client specifically? Flag. |
| **WotC IP: card names, oracle text, mechanics** | **No permission documented in repo** | `cardsfolder/` reproduces card text for 34k cards; README: "Forge operates independently and is not affiliated with Wizards of the Coast." | The central unresolved risk. Every Forge distribution (desktop included) carries it; the web port inherits it unchanged. Not a new risk created by this project — but a public URL arguably changes the exposure profile vs a desktop download. **Flagged, not resolved; no clearance claimed.** |
| **WotC IP: card artwork** | Not bundled; fetched from Scryfall at runtime | §5 | Keeping art out of the bundle is the correct posture; do not regress it by bundling images "for convenience". |
| Scryfall API/data | Third-party terms apply | `https://api.scryfall.com` used by `GuiDownloadPicturesHQ`, `ScryfallBulkDataSync` | Scryfall's API/usage terms need separate review before a public browser client hammers it (rate limits, attribution expectations). Not reviewed here. |

---

## 3. Card images: how Forge obtains and caches artwork

Verified from `forge-gui/src/main/java/forge/gui/download/`:

1. `GuiDownloadPicturesHQ.getNeededFiles()` enumerates all common + variant
   cards (+ tokens), skips images already in `CACHE_CARD_PICS_DIR`
   (`ForgeConstants.CACHE_CARD_PICS_DIR`), and maps each missing card to a
   Scryfall API URL: `https://api.scryfall.com/cards/named?fuzzy={name}&set={code}&format=image`
   (`GuiDownloadPicturesHQ.java`, `addDLObject`).
2. A set-code normalization table handles Forge↔Scryfall code mismatches
   (`CFX→CON`, `MPS_AKH→AKH`, …) plus per-card manual overrides.
3. `GuiDownloadService.retrieveManifestDirectory()` scrapes a directory listing
   at `https://downloads.cardforge.org/images/cards/` to know which sets have
   pre-packaged images.
4. `ScryfallBulkDataSync` notes its bulk downloads come from `*.scryfall.io`,
   "not api.scryfall.com — unthrottled".

**For the browser distribution, three options exist; the code already supports
the pattern:**

- **(a) Dynamic fetch (recommended):** the browser client requests card images
  from Scryfall at render time and caches in IndexedDB/Cache API. No WotC art
  in the bundle; no art in the repo; matches desktop behavior. Requires: CORS
  (Scryfall API sends permissive CORS headers **[well-known; re-verify at
  implementation]**), rate-limit discipline (debounce/prefetch only visible
  cards), and a text-only fallback when offline.
- **(b) Bundled subset:** ship art only for cards in the adventure starter
  pools. Smaller privacy surface, but puts WotC art *in* the distributed
  bundle — strictly worse from an IP standpoint, and the subset goes stale as
  sets are added.
- **(c) Self-hosted image proxy:** the optional save-service host also proxies
  Scryfall. Adds a moving part and makes the *host* the fetcher — no licensing
  advantage, some rate-limit advantage. Not recommended for the prototype.

---

## 4. Static browser release vs self-hosted service (distribution view only)

No account/save architecture redesign here (that is the saves track). Purely:
what must be shipped and operated.

| Dimension | A: static browser release | B: + optional self-hosted service |
|---|---|---|
| What is distributed | Game JS/WASM bundle + asset pack (planes, card DB, UI, audio subset) | Same, plus a small server component |
| Hosting needed | Any static HTTPS host (pages, object storage + CDN) | Above + a host for the service, TLS, backups |
| Third-party runtime deps | None beyond what's compiled in | Same + server runtime |
| Card images | Client → Scryfall directly (a) | Same; proxying optional, not recommended |
| Licensing delta | None — same bundle either way | Server component is also GPL-3.0-derived if it reuses Forge code → same source-availability duty |
| Update story | Replace the bundle + asset pack (see §6) | Same, plus service redeploy |
| Abuse surface | None (static files) | Save/image endpoints need quotas, rate limits, auth (saves-track concern) |
| Self-host difficulty | Trivial: copy files to any static host | Moderate: + service config, data dir, TLS |

**Recommendation:** the asset/licensing analysis does not discriminate between
A and B — the bundle contents and obligations are identical. Ship A first; B
adds operational burden with no distribution or licensing benefit. The only
reason to prefer B is the saves track's durability argument, which is out of
scope here.

---

## 5. Upstream maintenance implications

Verified mechanisms in the fork:

- **Releases are full packages, not deltas.** `forge/download/AutoUpdater.java`
  downloads `forge-gui-desktop-{version}.tar.bz2` from GitHub releases (or a
  snapshot installer jar). There is no binary-delta or asset-patch channel.
- **The card DB is git-tracked.** New sets arrive as new/changed
  `cardsfolder/*.txt` files + `editions/` metadata in a release. A web
  distribution re-bundles the DB per release — same cadence as desktop.
- **Card images follow automatically** under option (a): new cards resolve
  through Scryfall at view time; no repackaging needed. (Under option (b) every
  set would force an art-subset rebuild — another reason to prefer (a).)
- **Adventure content** (`res/adventure/`) is versioned with the repo; plane
  JSON references cards by name, so DB updates flow through without content
  changes **[assumption: plane JSON schema stability; spot-checked, not
  audited]**.
- **Practical consequence:** the web port needs a repeatable "cut a release"
  procedure — pin upstream commit, run the asset trim (§7), rebuild the
  bundle, publish. Upstream Forge moves fast (new sets ~4×/year); each release
  re-triggers the license/attribution check for *changed* assets only, not a
  full re-audit — provided the trim tooling records what it included.

---

## 6. Major uncertainties

1. **[Unverified]** Per-file sizes inside `languages/` (63 MB / 18 files) —
   needed to size the English-only trim precisely.
2. **[Unverified]** Per-track music licenses under `adventure/common/music/` —
   the 55 MB question behind §7's audio recommendation.
3. **[Unverified]** Scope of the XMage targeting-arrow permission (Forge-wide
   vs client-specific).
4. **[Unverified]** Scryfall API terms as they apply to a public browser
   client (rate limits, attribution, bulk-data rules) — requires reading
   https://scryfall.com/docs/api, not done here.
5. **[Assumption]** jgrapht's license posture in a TeaVM-compiled JS bundle —
   flagged for review, not resolved.
6. **[Assumption]** WotC's posture toward community rules engines is unchanged
   by a browser port. A public URL is more visible than a desktop download;
   visibility ≠ permission. No clearance claimed or implied.
7. **[Unverified]** Whether `custom_card_pics/` (7.5 MB) art is fully
   community-owned.

---

## 7. Recommendations: smallest legally supportable community prototype

A minimal, defensible first public package — proposals, not clearance:

1. **Static hosting only** (dimension §4): one JS/WASM bundle + trimmed asset
   pack. No server, no accounts.
2. **Bundle contents (trimmed):** `adventure/common/` minus `music/` (ship
   silent or with only verifiably-licensed tracks — resolves uncertainty #2
   by exclusion), one plane (Shandalar 2 MB to start; +12 MB for Realm of
   Legends when desired), `cardsfolder/` (134 MB — required for the rules
   engine; the WotC-text flag in §2 stands regardless), `editions/`,
   one language, `skin/`+`ui/`+`sound/`+`effects/`, starter `decks/`.
   Estimated order: **~150–170 MB** before compression **[estimate from
   measured components, not a built bundle]**.
3. **Card art: dynamic fetch only** (§3a). Never bundle WotC artwork; ship a
   clean text-only fallback.
4. **Ship the paperwork:** root `LICENSE` (GPL-3.0), `docs/Credit-and-Thanks.md`
   attribution, the `Particle Park License.txt`, the `res/licenses/` texts, and
   a `DISTRIBUTION.md` recording: upstream commit pinned, asset trim list,
   third-party dep licenses (including the jgrapht flag), and the explicit
   statement that card artwork is fetched from Scryfall at runtime under
   Scryfall's terms.
5. **GPL compliance mechanics:** publish the exact source tree (including web
   glue) used to build the bundle — a tagged repo state satisfies this; keep
   the trim script in-repo so the bundle is reproducible.
6. **Do not:** bundle card images, bundle all 18 languages, ship the
   full 55 MB music dir without resolving uncertainty #2, add a server
   component before it's needed, or present any of this as legally cleared.
7. **Per-release ritual:** re-run the trim, diff the asset list against the
   previous release, re-check licenses only for added/changed assets,
   re-verify the Scryfall integration still behaves.

## 8. Estimated distribution risks (ranked)

1. **WotC IP (card text/art)** — inherent to Forge; a public URL may draw more
   attention than desktop distribution. Mitigation: art stays out of the
   bundle; text risk is unchanged from upstream. No clearance available to claim.
2. **Music licensing gaps** — 55 MB bundled with incomplete in-repo license
   traceability. Mitigation: exclusion until verified (cost: silent prototype).
3. **Weak-copyleft dependency (jgrapht)** in a JS bundle — needs a real answer
   before public packaging.
4. **Community-art redistribution terms** — documented attribution exists, but
   several assets are "respective licence" without the licence text in-repo.
   Mitigation: ship the credit file; chase the few missing licence texts
   before calling the package final.
5. **Scryfall reliance** — operational (rate limits) and terms-based; a popular
   public client could strain the implicit social contract. Mitigation:
   aggressive client caching, prefetch discipline, text fallback.
6. **Bundle size** (~150–260 MB) — a UX/hosting cost, not a legal one; CDN +
   compression + trims manage it.

---

*Report by Muse (A-team, distribution/licensing track). Measurements and code
references verified against `rwZephyr/forge-adventure-web@db893dcd` on
2026-10-17/18. No game code, shared documents, or existing reports were
modified. Open legal questions are flagged, not answered — the owner should
treat §2's WotC and music rows as "do not ship publicly until reviewed"
rather than as cleared.*