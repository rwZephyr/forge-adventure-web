# Adventure asset scanner

Standalone Python 3 tool (standard library only) that scans a Forge Adventure
`.tmx` map and emits a JSON manifest of every file the map needs: external
tilesets (`.tsx`), tileset images, image-layer images, Tiled object templates
(`.tx`), template tilesets, and sprite atlases named by object properties.

No third-party packages. Never modifies source files.

## Reproduction (acceptance test)

From the repository root:

```bash
python3 adventure_asset_scan.py \
  --root . \
  --plane forge-gui/res/adventure/Shandalar \
  --common forge-gui/res/adventure/common \
  -o cave_treasure.manifest.json \
  forge-gui/res/adventure/common/maps/map/cave/cave_treasure.tmx
```

Expected result: exit 0, `9 files (7 static + 2 runtime-probed), 1793286 bytes
total`, zero missing, zero cycles. The sample `cave_treasure.manifest.json`
next to this README is that exact run's output.

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
                                [--allow-missing] [-o manifest.json] map.tmx
```

- `--root`: base directory for relative paths in the manifest (default: cwd).
- `--plane` / `--common`: adventure plane/common dirs used to probe `sprite`
  property values the way `Config.getFile()` does. Omit them and sprite values
  are reported as unresolvable (still listed under `runtime_discovery`).
- Exit code: 0 = scan complete, no missing files; 1 = missing files found
  (override with `--allow-missing`); 2 = input map not found / bad arguments.

## Manifest schema

- `files[]`: `{path, kind, size, sha256, referenced_by[], via}` —
  `via` is `static` (Tiled reference graph) or `runtime-probe` (sprite atlas
  found via `--plane`/`--common`).
- `missing[]`: referenced files not found on disk, with `referenced_by`.
- `cycles[]`: files re-entered while on the recursion stack.
- `runtime_discovery[]`: object properties Forge resolves at runtime —
  `sprite-atlas`, `map-reference` (teleport), `dialog-key`, `reward-spec`,
  `enemy-name` — each with source file, raw value, an optional `resolved`
  file record, and a note citing the resolving source code.
- `errors[]`: XML parse / I/O failures (scan continues).
- `totals`: file/byte counts split static vs runtime-probed, plus missing,
  duplicate-reference, and cycle counts.

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
