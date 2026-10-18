#!/usr/bin/env python3
"""adventure_asset_scan.py -- static dependency scanner for Forge Adventure maps.

Given a Forge Adventure .tmx map, recursively discovers the files the map
needs: external tilesets (.tsx), tileset images, image-layer images, Tiled
object templates (.tx), template tilesets, and libGDX sprite atlases referenced
by object properties. Produces a JSON manifest with resolved paths, sizes and
SHA-256 hashes, and reports the total asset size.

The traversal mirrors Forge's actual loader
(forge-gui-mobile/src/forge/adventure/util/TemplateTmxMapLoader.java):
  * <tileset source="..."> resolved relative to the .tmx
    (getDependencyFileHandles / getRelativeFileHandle(tmxFile, ...))
  * <image source="..."> inside a .tsx resolved relative to the .tsx
  * <imagelayer>/<image source="..."> resolved relative to the .tmx
  * <object template="..."> resolved relative to the .tmx (loadObject override)
One deliberate difference: the scanner also follows <tileset source="...">
inside .tx templates (full static closure). Forge's texture preloader only
scans the .tmx root for tilesets, so the scanner is a superset there; see README.

Dependencies that Forge resolves at *runtime* (object properties such as
`sprite`, `teleport`, `dialog`, reward JSON, enemy names) are reported under
`runtime_discovery` instead of being silently dropped. `sprite` values can be
probed with --plane/--common, mirroring Config.getFile()'s
plane-dir-then-common-dir lookup.

Safety: every reference is resolved against --root and rejected (never
traversed) when it is absolute, escapes --root via "..", or escapes via a
symlink. Rejections always fail the scan, even with --allow-missing.

Reproducibility: the default manifest contains no absolute paths and no
timestamps, so identical inputs produce byte-identical JSON. Pass --timestamp
to opt back into a generation timestamp.

Standard library only. Never modifies source files.
"""

import argparse
import hashlib
import json
import os
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

TOOL_VERSION = "1.1.0"

IMAGE_EXTS = (".png", ".jpg", ".jpeg", ".bmp", ".gif")

# Object properties that name runtime-resolved resources. Values are file-ish
# (sprite), map names (teleport), or data keys (dialog/reward/enemy).
RUNTIME_PROPS = {
    "sprite": "sprite-atlas",
    "teleport": "map-reference",
    "dialog": "dialog-key",
    "reward": "reward-spec",
    "enemy": "enemy-name",
}


def sha256_of(path, chunk=65536):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(chunk), b""):
            h.update(block)
    return h.hexdigest()


def parse_atlas_images(atlas_path):
    """Minimal libGDX .atlas page parser: returns image filenames declared as
    texture pages. A page is a non-indented, non-empty line without ':' that
    ends with a known image extension. Region names are indented, so they are
    skipped. Documented limitation: exotic atlas layouts may be missed."""
    images = []
    try:
        with open(atlas_path, "r", encoding="utf-8", errors="replace") as f:
            for line in f:
                s = line.strip()
                if not s or line[0] in (" ", "\t") or ":" in s:
                    continue
                if s.lower().endswith(IMAGE_EXTS):
                    images.append(s)
    except OSError:
        pass
    return images


class Scanner:
    def __init__(self, root, plane_dir=None, common_dir=None):
        # Canonical root: all references must stay inside it (after symlink
        # resolution). plane/common are independent probe dirs and may live
        # anywhere; candidates must stay inside their own probe dir.
        self.root = os.path.realpath(os.path.abspath(root))
        self.plane_dir = os.path.realpath(os.path.abspath(plane_dir)) if plane_dir else None
        self.common_dir = os.path.realpath(os.path.abspath(common_dir)) if common_dir else None
        self.files = {}          # realpath -> record
        self.missing = {}        # realpath -> record
        self.rejected = []       # path-escape rejections (always fatal)
        self.cycles = []         # {path, cycle: [closed loop of relpaths]}
        self.runtime = []        # runtime_discovery entries
        self.errors = []         # {path, error}; parse/I-O failures, always fatal
        self._visiting = []      # recursion stack (realpaths) for cycle detection
        self.duplicates = 0

    # -- path handling -------------------------------------------------
    def rel(self, realpath):
        return os.path.relpath(realpath, self.root)

    @staticmethod
    def _within(path, base):
        """True if real path `path` is inside real dir `base` (or equal)."""
        try:
            return os.path.commonpath([path, base]) == base
        except ValueError:
            return False  # e.g. different drives on Windows

    def resolve_ref(self, base_dir, source, ref_from):
        """Resolve a Tiled `source` attribute like Forge's getRelativeFileHandle
        (relative to the referencing file's directory). Returns the canonical
        realpath, or None after recording a rejection for unsafe references."""
        if os.path.isabs(source):
            self._reject(source, ref_from, "absolute path reference")
            return None
        joined = os.path.normpath(os.path.join(base_dir, source))
        real = os.path.realpath(joined)
        if not self._within(real, self.root):
            self._reject(source, ref_from,
                          "reference escapes --root: %s" % self.rel(real))
            return None
        return real

    def _reject(self, source, ref_from, reason):
        self.rejected.append({
            "source": source,
            "reason": reason,
            "referenced_by": self.rel(ref_from) if ref_from else None,
        })

    # -- core traversal --------------------------------------------------
    def add_ref(self, realpath, kind, ref_from, via="static"):
        ref_rel = self.rel(ref_from) if ref_from else None
        if realpath in self._visiting:
            # Genuine cycle: target is on the current recursion stack.
            # Report the complete closed loop, not just the re-entered file.
            idx = self._visiting.index(realpath)
            loop = [self.rel(p) for p in self._visiting[idx:]] + [self.rel(realpath)]
            self.cycles.append({"path": self.rel(realpath), "cycle": loop})
            return
        if realpath in self.files:
            # Repeated reference (diamond), NOT a cycle: merge and count.
            rec = self.files[realpath]
            if ref_rel and ref_rel not in rec["referenced_by"]:
                rec["referenced_by"].append(ref_rel)
                self.duplicates += 1
            if rec.get("via") == "runtime-probe" and via == "static":
                rec["via"] = "static"  # static reachability wins
            return
        if realpath in self.missing:
            rec = self.missing[realpath]
            if ref_rel and ref_rel not in rec["referenced_by"]:
                rec["referenced_by"].append(ref_rel)
            return

        if not os.path.isfile(realpath):
            self.missing[realpath] = {
                "path": self.rel(realpath),
                "referenced_by": [ref_rel] if ref_rel else [],
                "expected_at": self.rel(realpath),
            }
            return

        try:
            size = os.path.getsize(realpath)
            digest = sha256_of(realpath)
        except OSError as e:
            # Unreadable dependency: a hard error, never suppressed.
            self.errors.append({"path": self.rel(realpath),
                                "error": "unreadable file: %s" % e})
            return

        rec = {
            "path": self.rel(realpath),
            "kind": kind,
            "size": size,
            "sha256": digest,
            "referenced_by": [ref_rel] if ref_rel else [],
            "via": via,
        }
        self.files[realpath] = rec
        self._visiting.append(realpath)
        try:
            self._parse(realpath, kind, realpath)
        finally:
            self._visiting.pop()

    @staticmethod
    def _kind_of(realpath):
        ext = os.path.splitext(realpath)[1].lower()
        return {".tmx": "tmx", ".tsx": "tsx", ".tx": "tx",
                ".atlas": "atlas"}.get(ext, "image" if ext in IMAGE_EXTS else "other")

    def _parse(self, realpath, kind, ref_from):
        base_dir = os.path.dirname(realpath)
        if kind == "atlas":
            via = self.files.get(realpath, {}).get("via", "static")
            for img in parse_atlas_images(realpath):
                target = self.resolve_ref(base_dir, img, ref_from)
                if target is not None:
                    self.add_ref(target, "image", ref_from, via=via)
            return
        if kind == "image":
            return
        try:
            tree = ET.parse(realpath)
        except ET.ParseError as e:
            # Malformed XML: a hard error, never suppressed by --allow-missing.
            self.errors.append({"path": self.rel(realpath),
                                "error": "XML parse error: %s" % e})
            return
        except OSError as e:
            self.errors.append({"path": self.rel(realpath), "error": str(e)})
            return
        root = tree.getroot()

        if kind == "tmx":
            self._parse_tmx(root, base_dir, ref_from)
        elif kind == "tsx":
            self._parse_tsx(root, base_dir, ref_from)
        elif kind == "tx":
            self._parse_tx(root, base_dir, ref_from)
        # collect runtime-discovery properties from any xml type
        self._collect_runtime_props(root, realpath)

    def _ref(self, base_dir, source, kind, ref_from, via="static"):
        target = self.resolve_ref(base_dir, source, ref_from)
        if target is not None:
            self.add_ref(target, kind, ref_from, via=via)

    def _parse_tmx(self, root, base_dir, ref_from):
        # External tilesets: <tileset source="..."/> resolved vs the .tmx.
        # Inline tilesets: <tileset><image source="..."/></tileset> vs the .tmx.
        for ts in root.iter("tileset"):
            src = ts.get("source")
            if src:
                self._ref(base_dir, src, "tsx", ref_from)
            img = ts.find("image")
            if img is not None and img.get("source"):
                self._ref(base_dir, img.get("source"), "image", ref_from)
        # Image layers.
        for layer in root.iter("imagelayer"):
            img = layer.find("image")
            if img is not None and img.get("source"):
                self._ref(base_dir, img.get("source"), "image", ref_from)
        # Object templates: resolved vs the .tmx (TemplateTmxMapLoader.loadObject).
        for obj in root.iter("object"):
            tmpl = obj.get("template")
            if tmpl:
                self._ref(base_dir, tmpl, "tx", ref_from)

    def _parse_tsx(self, root, base_dir, ref_from):
        # Image tileset: <tileset><image source="..."/> resolved vs the .tsx.
        # Collection tileset: <tile><image source="..."/></tile> vs the .tsx.
        # (Mirrors the if/else in TemplateTmxMapLoader.getDependencyFileHandles.)
        img = root.find("image")
        if img is not None and img.get("source"):
            self._ref(base_dir, img.get("source"), "image", ref_from)
        else:
            for tile in root.iter("tile"):
                img = tile.find("image")
                if img is not None and img.get("source"):
                    self._ref(base_dir, img.get("source"), "image", ref_from)

    def _parse_tx(self, root, base_dir, ref_from):
        # Templates may declare their own tilesets (resolved vs the .tx).
        # NOTE: Forge's texture preloader ignores these; the scanner reports
        # the full static closure (documented superset, see README).
        for ts in root.iter("tileset"):
            src = ts.get("source")
            if src:
                self._ref(base_dir, src, "tsx", ref_from)
        for img in root.iter("image"):
            if img.get("source"):
                self._ref(base_dir, img.get("source"), "image", ref_from)
        # Nested templates do not occur in Tiled data, but handle them so the
        # cycle detector is exercised on adversarial input.
        for obj in root.iter("object"):
            tmpl = obj.get("template")
            if tmpl:
                self._ref(base_dir, tmpl, "tx", ref_from)

    # -- runtime discovery ----------------------------------------------
    def _collect_runtime_props(self, root, realpath):
        src_rel = self.rel(realpath)
        for props in root.iter("properties"):
            for p in props.iter("property"):
                name = p.get("name")
                if name in RUNTIME_PROPS:
                    # value may be an attribute or element text (reward JSON)
                    value = p.get("value")
                    if value is None:
                        value = (p.text or "").strip()
                    self.runtime.append({
                        "kind": RUNTIME_PROPS[name],
                        "property": name,
                        "value": value,
                        "source": src_rel,
                        "resolved": self._probe_runtime(RUNTIME_PROPS[name], value, realpath),
                        "note": self._runtime_note(RUNTIME_PROPS[name], name, value),
                    })

    def _probe_runtime(self, kind, value, ref_from):
        """Mirror Config.getFile(): probe plane dir, then common dir.
        Candidates must stay inside their probe dir; escapes are rejected."""
        if kind != "sprite-atlas" or not value:
            return None
        if os.path.isabs(value):
            self._reject(value, ref_from, "absolute path in sprite property")
            return None
        for d in (self.plane_dir, self.common_dir):
            if not d:
                continue
            cand = os.path.realpath(os.path.normpath(os.path.join(d, value)))
            if not self._within(cand, d):
                self._reject(value, ref_from,
                             "sprite path escapes probe dir: %s" % d)
                return None
            if os.path.isfile(cand):
                # record the atlas (and its png pages) as runtime-probed files
                self.add_ref(cand, "atlas", None, via="runtime-probe")
                st = self.files.get(cand)
                if st:
                    return {"path": st["path"], "size": st["size"],
                            "sha256": st["sha256"]}
        return None

    @staticmethod
    def _runtime_note(kind, name, value):
        if kind == "sprite-atlas":
            return ("Object 'sprite' property; resolved at runtime via "
                    "Config.getFile(): plane dir, then common dir. When absent, "
                    "MapStage.java substitutes code defaults "
                    "('sprites/portal.atlas' for portals, 'sprites/treasure.atlas' "
                    "for rewards).")
        if kind == "map-reference":
            return ("Teleport target map name; resolved at runtime via "
                    "Config.getCommonFilePath() in MapStage.loadMapFile(). "
                    "Empty value = no teleport. Not recursed: target discovery "
                    "is runtime plane logic.")
        if kind == "dialog-key":
            return "Dialog key resolved at runtime via adventure dialog data."
        if kind == "reward-spec":
            return ("Reward JSON resolved at runtime against the card database "
                    "(e.g. randomCard specs); not a file dependency.")
        if kind == "enemy-name":
            return "Enemy name resolved at runtime via enemies.json / WorldData."
        return ""

    # -- manifest ----------------------------------------------------------
    def manifest(self, map_realpath, include_timestamp=False):
        files = sorted(self.files.values(), key=lambda r: r["path"])
        static_bytes = sum(r["size"] for r in files if r["via"] == "static")
        runtime_bytes = sum(r["size"] for r in files if r["via"] == "runtime-probe")
        doc = {
            "tool": "adventure-asset-scan",
            "version": TOOL_VERSION,
            "map": self.rel(map_realpath),
            "plane_dir": self.rel(self.plane_dir) if self.plane_dir else None,
            "common_dir": self.rel(self.common_dir) if self.common_dir else None,
            "files": files,
            "missing": sorted(self.missing.values(), key=lambda r: r["path"]),
            "rejected": sorted(self.rejected, key=lambda r: (r["source"], str(r["referenced_by"]))),
            "cycles": self.cycles,
            "runtime_discovery": self.runtime,
            "errors": self.errors,
            "totals": {
                "files": len(files),
                "static_files": sum(1 for r in files if r["via"] == "static"),
                "runtime_probed_files": sum(1 for r in files if r["via"] == "runtime-probe"),
                "static_bytes": static_bytes,
                "runtime_probed_bytes": runtime_bytes,
                "total_bytes": static_bytes + runtime_bytes,
                "missing": len(self.missing),
                "rejected": len(self.rejected),
                "duplicate_references": self.duplicates,
                "cycles": len(self.cycles),
                "errors": len(self.errors),
            },
        }
        if include_timestamp:
            doc["generated_utc"] = datetime.now(timezone.utc).isoformat()
        return doc


def main(argv=None):
    ap = argparse.ArgumentParser(
        description="Scan a Forge Adventure .tmx map and emit a JSON asset manifest.")
    ap.add_argument("map", help="path to the .tmx map file (must be inside --root)")
    ap.add_argument("-o", "--output", default="-",
                    help="manifest output path (default: stdout)")
    ap.add_argument("--root", default=os.getcwd(),
                    help="designated repository root: manifest paths are relative "
                         "to it and no reference may escape it (default: cwd)")
    ap.add_argument("--plane",
                    help="adventure plane dir, e.g. forge-gui/res/adventure/Shandalar "
                         "(used to probe 'sprite' property values like Config.getFile)")
    ap.add_argument("--common",
                    help="adventure common dir, e.g. forge-gui/res/adventure/common")
    ap.add_argument("--allow-missing", action="store_true",
                    help="tolerate missing files and dependency cycles (exit 0). "
                         "Parse errors, unreadable files and path escapes still fail.")
    ap.add_argument("--timestamp", action="store_true",
                    help="include a generation timestamp in the manifest "
                         "(off by default for byte-reproducible output)")
    args = ap.parse_args(argv)

    scanner = Scanner(args.root, args.plane, args.common)

    map_real = os.path.realpath(os.path.abspath(args.map))
    if not os.path.isfile(map_real):
        print("error: map not found: %s" % args.map, file=sys.stderr)
        return 2
    if not Scanner._within(map_real, scanner.root):
        print("error: map is outside --root (%s)" % scanner.root, file=sys.stderr)
        return 2

    scanner.add_ref(map_real, "tmx", None)
    manifest = scanner.manifest(map_real, include_timestamp=args.timestamp)
    out = json.dumps(manifest, indent=2) + "\n"
    if args.output == "-":
        sys.stdout.write(out)
    else:
        with open(args.output, "w", encoding="utf-8") as f:
            f.write(out)

    t = manifest["totals"]
    print("map: %s" % manifest["map"], file=sys.stderr)
    print("files: %d (%d static + %d runtime-probed), %d bytes total" % (
        t["files"], t["static_files"], t["runtime_probed_files"], t["total_bytes"]),
        file=sys.stderr)
    for key in ("missing", "rejected", "cycles", "errors"):
        if t[key]:
            print("%s: %d" % (key, t[key]), file=sys.stderr)

    # Failure policy (strict by default):
    #   --allow-missing tolerates missing files and cycles only.
    #   Parse errors, unreadable files and path escapes always fail.
    if manifest["errors"] or manifest["rejected"]:
        return 1
    if (t["missing"] or t["cycles"]) and not args.allow_missing:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
