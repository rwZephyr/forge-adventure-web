# Adventure asset scanner

Standalone Python 3 tool (standard library only) that scans a Forge Adventure
`.tmx` map and emits a JSON manifest of every file the map needs: external
tilesets (`.tsx`), tileset images, image-layer images, Tiled object templates
(`.tx`), template tilesets, and libGDX sprite atlases referenced by object
properties.

No third-party packages. Never modifies source files.

## Reproduction (acceptance test)

From the repository root:

```bash
python3 adventure_asset_scan.py \
  --root . \
  --plane forge-gui/res/adventure/Shandalar \
  --common forge-gui/res/adventure/common \
  -o docs/tools/adventure-asset-scan/cave_treasure.manifest.json \
  forge-gui/res/adventure/common/maps/map/cave/cave_treasure.tmx
```

Expected result: exit 0, `9 files (7 static + 2 runtime-probed), 1793286 bytes
total`, zero missing, zero rejected, zero cycles, zero errors. The sample
`cave_treasure.manifest.json` next to this README is that exact run's output
(regenerated with v1.1.1; see "Intentional differences" below).

### Discovered files for `cave_treasure.tmx`

| File | Kind | Bytes | Via |
|---|---|---|---|
| `…/maps/map/cave/cave_treasure.tmx` | tmx | 1,580 | static |
| `…/maps/tileset/main.tsx` | tsx | 635,866 | static |
| `…/maps/tileset/main.png` | image | 790,816 | static |
| `…/maps/tileset/buildings.tsx` | tsx | 112,669 | static (referenced 3×: map + 2 templates) |
| `…/maps/tileset/buildings.png` | image | 231,923 | static |
| `…/maps/obj/entry_up.tx` | tx | 366 | static |
| `…/maps/obj/treasure.tx` | tx | 985 | static |
| `…/sprites/treasure.atlas` | atlas | 387 | runtime-probe (`sprite` property) |
| `…/sprites/treasure.png` | image | 18,694 | runtime-probe (via atlas) |

Static closure: **1,774,205 bytes** (7 files). Runtime-probed sprites add
19,081 bytes. **Total: 1,793,286 bytes.**

### Cross-check against Forge's loader

Traversal mirrors
`forge-gui-mobile/src/forge/adventure/util/TemplateTmxMapLoader.java`:

- `<tileset source>` resolved relative to the `.tmx`
  (`getDependencyFileHandles`, `getRelativeFileHandle(tmxFile, …)`)
- `<image source>` inside a `.tsx` resolved relative to the `.tsx`
  (image-tileset vs per-tile collection branches reproduced)
- `<imagelayer>/<image source>` resolved relative to the `.tmx`
- `<object template>` resolved relative to the `.tmx` (`loadObject` override)
- `sprite` property values probed plane-dir-then-common-dir, mirroring
  `Config.getFile()` (`Config.java:247`); teleport names resolve via
  `Config.getCommonFilePath()` at runtime (`MapStage.java:839`)

One deliberate difference: the scanner follows `<tileset source>` inside `.tx`
templates (full static closure). Forge's texture preloader only scans the
`.tmx` root, so the scanner is a superset there — verified by reading
`getDependencyFileHandles`, which never parses template files.

## Usage

```bash
python3 adventure_asset_scan.py [--root DIR] [--plane DIR] [--common DIR]
                                [--allow-missing] [--timestamp]
                                [-o manifest.json] map.tmx
```

- `--root`: designated repository root. Manifest paths are relative to it,
  the input map must lie inside it, and no reference may escape it (see
  "Path safety"). Default: current directory.
- `--plane` / `--common`: adventure plane/common dirs **inside `--root`**,
  used to probe `sprite` property values the way `Config.getFile()` does.
  Directories outside `--root` (including via symlinks) are rejected with
  exit 2 before any asset file is read. Omit them and sprite values are
  reported as unresolvable (still listed under `runtime_discovery`).
- `--timestamp`: include a `generated_utc` timestamp in the manifest. Off by
  default so identical inputs produce byte-identical JSON.
- `--allow-missing`: tolerate missing files and dependency cycles (exit 0).
  **Does not suppress** parse errors, unreadable files, or path escapes —
  those always fail.

Exit codes: `0` = clean scan; `1` = scan failures (see failure policy below);
`2` = bad invocation (map not found, map outside `--root`, bad arguments).

Failure policy (strict by default): missing files and dependency cycles fail
unless `--allow-missing` is given. Malformed XML, unreadable dependencies, and
references escaping `--root` **always** fail, in both modes.

## Tests

```bash
python3 -m unittest test_adventure_asset_scan -v
```

21 tests, standard library only (`unittest`, `tempfile`). They cover: valid
dependency graphs (both tileset kinds, image layers, templates), duplicate
references, missing files in strict/lenient modes, malformed XML in both
modes, unreadable files in both modes, cycle detection with the complete
closed path (strict fail / lenient pass), diamond graphs (not cycles),
absolute-path / `../`-traversal / symlink-escape rejection (always fatal),
probe directories outside `--root` and probe-dir symlink escapes (exit 2
before any read), in-root symlinks (allowed, deduplicated),
map-outside-root rejection, byte-identical output across runs, timestamp
opt-in, and runtime-discovery separation from static dependencies.

## Manifest schema

- `files[]`: `{path, kind, size, sha256, referenced_by[], via}` —
  `via` is `static` (Tiled reference graph) or `runtime-probe` (sprite atlas
  found via `--plane`/`--common`). All paths are relative to `--root`;
  there are no absolute paths in the default manifest.
- `missing[]`: referenced files not found on disk, with `referenced_by`.
- `rejected[]`: references that escape `--root` (absolute paths, `..`
  traversal, symlink escapes), with reason and referrer. Always fatal.
- `cycles[]`: `{path, cycle}` where `cycle` is the complete closed loop of
  root-relative paths (e.g. `["c.tmx", "cyc.tx", "c.tmx"]`). Fails in strict
  mode. Repeated (diamond) references are *not* cycles: they merge into one
  file record with multiple `referenced_by` entries and increment
  `duplicate_references`.
- `runtime_discovery[]`: object properties Forge resolves at runtime —
  `sprite-atlas`, `map-reference` (teleport), `dialog-key`, `reward-spec`,
  `enemy-name` — each with source file, raw value, an optional `resolved`
  file record, and a note citing the resolving source code. Kept separate
  from the static dependency closure by design.
- `errors[]`: XML parse / I/O failures (scan continues to collect
  diagnostics, then exits 1).
- `totals`: file/byte counts split static vs runtime-probed, plus missing,
  rejected, duplicate-reference, cycle, and error counts.

## Path safety

Every `source` attribute is resolved against the referencing file's directory
(exactly like Forge's `getRelativeFileHandle`), then canonicalized with
symlink resolution and checked for containment in `--root`. Absolute paths,
`..` sequences escaping the root, and symlinks pointing outside the root are
rejected, recorded, and never traversed. In-root symlinks are followed and
deduplicated by canonical path. Sprite-probe candidates must likewise stay
inside their `--plane`/`--common` directory, and **both probe directories
must themselves live inside `--root`** (after symlink resolution) — an
out-of-root probe dir is rejected with exit 2 before any asset file is read,
so a hostile atlas can never be pulled in through runtime probing.

## Intentional differences from v1.0.0

1. The default manifest no longer contains the absolute `root` path or the
   `generated_utc` timestamp (use `--timestamp` to opt back in). The
   regenerated sample manifest is byte-reproducible.
2. `missing[].expected_at` is now root-relative instead of absolute.
3. New `rejected[]` section and `rejected`/`errors` totals.
4. `cycles[]` entries now carry the complete closed `cycle` path instead of
   the raw recursion stack.
5. Cycles fail in strict mode (previously exit 0); `--allow-missing` now also
   tolerates cycles but never parse/I-O errors or path escapes.
6. Exit code `2` is now also used when the input map lies outside `--root`.
7. File identity is by canonical (symlink-resolved) path, so in-root symlinks
   deduplicate instead of double-counting.
8. (v1.1.1) `--plane`/`--common` must be inside `--root` after symlink
   resolution; violations exit 2 before any asset file is read.

## Limitations

1. **Superset of Forge's texture preload** (see cross-check above):
   template-declared tilesets are followed statically although Forge's
   `getDependencyFileHandles` ignores them.
2. **Teleport targets are listed, not recursed.** A non-empty `teleport`
   property names another map, but target discovery is runtime plane logic
   (`MapStage.loadMapFile`); following it would pull in unbounded map graphs.
3. **Sprite probing needs `--plane`/`--common`.** Without them, `sprite`
   values are reported unresolvable. Code-defined defaults
   (`sprites/portal.atlas`, `sprites/treasure.atlas` in `MapStage.java:449,489`)
   apply when the property is absent — the scanner notes this but cannot know
   which default a given object type triggers without replicating MapStage.
4. **Minimal `.atlas` parser.** Page images are detected as non-indented
   extension-suffixed lines; exotic atlas layouts could be missed.
5. **`<editorsettings>` is ignored** (editor metadata, not a dependency), as
   is layer tile data (gids reference the already-collected tilesets).
6. **No network, no writes.** The tool only reads the files it scans.
7. **Case-sensitivity follows the host filesystem.** On case-insensitive
   filesystems a `Source="A.PNG"` vs `a.png` mismatch the browser would hit
   may not be flagged.
