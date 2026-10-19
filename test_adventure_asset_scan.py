#!/usr/bin/env python3
"""Standard-library unit tests for adventure_asset_scan.py.

Run from the repository root:
    python3 -m unittest test_adventure_asset_scan -v
"""

import hashlib
import io
import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import adventure_asset_scan as scan


def w(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    mode = "wb" if isinstance(data, bytes) else "w"
    with open(path, mode) as f:
        f.write(data)
    return path


TMX_BASIC = """<?xml version="1.0" encoding="UTF-8"?>
<map version="1.10" width="10" height="10" tilewidth="16" tileheight="16">
 <tileset firstgid="1" source="tiles/main.tsx"/>
 <tileset firstgid="101" source="tiles/collection.tsx"/>
 <imagelayer name="bg"><image source="img/bg.png"/></imagelayer>
 <objectgroup name="Objects">
  <object id="1" template="obj/chest.tx"/>
 </objectgroup>
</map>
"""
TSX_IMAGE = """<?xml version="1.0"?>
<tileset name="main" tilewidth="16" tileheight="16" tilecount="4" columns="2">
 <image source="../img/tiles.png" width="32" height="32"/>
</tileset>
"""
TSX_COLLECTION = """<?xml version="1.0"?>
<tileset name="collection" tilewidth="16" tileheight="16">
 <tile id="0"><image source="../img/a.png" width="16" height="16"/></tile>
 <tile id="1"><image source="../img/b.png" width="16" height="16"/></tile>
</tileset>
"""
TX_BASIC = """<?xml version="1.0"?>
<template>
 <tileset firstgid="1" source="../tiles/main.tsx"/>
 <object name="Chest" type="reward" gid="1">
  <properties>
   <property name="sprite" value="sprites/chest.atlas"/>
   <property name="reward">[{"type":"gold","count":5}]</property>
  </properties>
 </object>
</template>
"""
ATLAS = "chest.png\nsize: 64,64\nformat: RGBA8888\nfilter: Nearest,Nearest\nrepeat: none\nchest\n  rotate: false\n  xy: 0, 0\n  size: 16, 16\n"


class ScanCase(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = self.tmp.name

    def tearDown(self):
        self.tmp.cleanup()

    def p(self, *parts):
        return os.path.join(self.root, *parts)

    def build_basic(self):
        """A valid map graph: tmx -> 2 tsx (+3 png), imagelayer png, tx -> tsx(dup)."""
        w(self.p("map/level.tmx"), TMX_BASIC)
        w(self.p("map/tiles/main.tsx"), TSX_IMAGE)
        w(self.p("map/tiles/collection.tsx"), TSX_COLLECTION)
        w(self.p("map/obj/chest.tx"), TX_BASIC)
        w(self.p("map/img/tiles.png"), b"TILES")
        w(self.p("map/img/a.png"), b"A")
        w(self.p("map/img/b.png"), b"B")
        w(self.p("map/img/bg.png"), b"BG")
        return self.p("map/level.tmx")

    def run_scan(self, *argv):
        """Run main() capturing stdout; return (exit_code, manifest, stderr)."""
        stdout, stderr = io.StringIO(), io.StringIO()
        old_out, old_err = sys.stdout, sys.stderr
        sys.stdout, sys.stderr = stdout, stderr
        try:
            code = scan.main(["--root", self.root] + list(argv))
        finally:
            sys.stdout, sys.stderr = old_out, old_err
        manifest = json.loads(stdout.getvalue()) if stdout.getvalue().strip() else None
        return code, manifest, stderr.getvalue()

    # -- valid graphs -------------------------------------------------
    def test_valid_graph(self):
        mappath = self.build_basic()
        code, m, err = self.run_scan(mappath)
        self.assertEqual(code, 0, err)
        # map + 2 tsx + 4 png + 1 tx = 8 files, no runtime probing configured
        self.assertEqual(m["totals"]["files"], 8)
        self.assertEqual(m["totals"]["missing"], 0)
        self.assertEqual(m["totals"]["cycles"], 0)
        self.assertEqual(m["totals"]["errors"], 0)
        self.assertEqual(m["totals"]["total_bytes"],
                         sum(f["size"] for f in m["files"]))
        png = next(f for f in m["files"] if f["path"].endswith("map/img/tiles.png"))
        self.assertEqual(png["sha256"], hashlib.sha256(b"TILES").hexdigest())
        self.assertEqual(png["size"], 5)
        # every file record is well-formed
        for f in m["files"]:
            for key in ("path", "kind", "size", "sha256", "referenced_by", "via"):
                self.assertIn(key, f)
            self.assertFalse(os.path.isabs(f["path"]), f["path"])

    def test_duplicates_are_not_cycles(self):
        # main.tsx referenced by the map AND the template: one record, 2 referrers
        mappath = self.build_basic()
        code, m, _ = self.run_scan(mappath)
        self.assertEqual(code, 0)
        tsx = next(f for f in m["files"] if f["path"].endswith("tiles/main.tsx"))
        self.assertEqual(len(tsx["referenced_by"]), 2)
        self.assertEqual(m["totals"]["duplicate_references"], 1)
        self.assertEqual(m["totals"]["cycles"], 0)

    def test_diamond_is_not_a_cycle(self):
        # tmx -> a.tsx -> shared.png and tmx -> b.tx -> shared.png
        w(self.p("d.tmx"),
          '<?xml version="1.0"?><map><tileset source="a.tsx"/>'
          '<objectgroup><object template="b.tx"/></objectgroup></map>')
        w(self.p("a.tsx"),
          '<?xml version="1.0"?><tileset><image source="shared.png"/></tileset>')
        w(self.p("b.tx"),
          '<?xml version="1.0"?><template><object><properties/></object></template>')
        # b.tx references the png via a nested-template-free image element
        w(self.p("b.tx"),
          '<?xml version="1.0"?><template><image source="shared.png"/>'
          '<object/></template>')
        w(self.p("shared.png"), b"S")
        code, m, _ = self.run_scan(self.p("d.tmx"))
        self.assertEqual(code, 0)
        self.assertEqual(m["totals"]["cycles"], 0)
        png = next(f for f in m["files"] if f["path"] == "shared.png")
        self.assertEqual(len(png["referenced_by"]), 2)

    # -- missing files --------------------------------------------------
    def test_missing_strict_fails(self):
        w(self.p("m.tmx"),
          '<?xml version="1.0"?><map><tileset source="nope.tsx"/></map>')
        code, m, err = self.run_scan(self.p("m.tmx"))
        self.assertEqual(code, 1)
        self.assertEqual(m["totals"]["missing"], 1)
        self.assertEqual(m["missing"][0]["referenced_by"], ["m.tmx"])
        self.assertIn("missing: 1", err)

    def test_missing_lenient_passes(self):
        w(self.p("m.tmx"),
          '<?xml version="1.0"?><map><tileset source="nope.tsx"/></map>')
        code, m, _ = self.run_scan("--allow-missing", self.p("m.tmx"))
        self.assertEqual(code, 0)
        self.assertEqual(m["totals"]["missing"], 1)

    def test_missing_input_map_exit_2(self):
        code, m, _ = self.run_scan(self.p("does-not-exist.tmx"))
        self.assertEqual(code, 2)
        self.assertIsNone(m)

    # -- malformed xml / unreadable --------------------------------------
    def test_malformed_xml_always_fails(self):
        w(self.p("bad.tmx"), '<?xml version="1.0"?><map><unclosed>')
        for argv in ([self.p("bad.tmx")], ["--allow-missing", self.p("bad.tmx")]):
            code, m, _ = self.run_scan(*argv)
            self.assertEqual(code, 1, argv)
            self.assertEqual(len(m["errors"]), 1)
            self.assertIn("XML parse error", m["errors"][0]["error"])

    def test_unreadable_file_always_fails(self):
        # Deterministic regardless of privileges: simulate the read failure
        # that chmod 000 would cause for a non-root user.
        w(self.p("m.tmx"),
          '<?xml version="1.0"?><map><tileset source="t.tsx"/></map>')
        w(self.p("t.tsx"),
          '<?xml version="1.0"?><tileset><image source="x.png"/></tileset>')
        w(self.p("x.png"), b"secret")
        import unittest.mock as mock
        real_sha = scan.sha256_of

        def boom(path, chunk=65536):
            if path.endswith("x.png"):
                raise OSError(13, "Permission denied")
            return real_sha(path, chunk)

        with mock.patch.object(scan, "sha256_of", boom):
            for argv in ([self.p("m.tmx")], ["--allow-missing", self.p("m.tmx")]):
                code, m, _ = self.run_scan(*argv)
                self.assertEqual(code, 1, argv)
                self.assertTrue(any("unreadable" in e["error"] for e in m["errors"]))

    # -- cycles ------------------------------------------------------------
    def test_cycle_reports_closed_path_and_fails_strict(self):
        w(self.p("c.tmx"),
          '<?xml version="1.0"?><map><objectgroup>'
          '<object template="cyc.tx"/></objectgroup></map>')
        w(self.p("cyc.tx"),
          '<?xml version="1.0"?><template><object template="c.tmx"/></template>')
        code, m, _ = self.run_scan(self.p("c.tmx"))
        self.assertEqual(code, 1)
        self.assertEqual(len(m["cycles"]), 1)
        cyc = m["cycles"][0]["cycle"]
        self.assertEqual(cyc[0], cyc[-1])          # closed loop
        self.assertEqual(cyc, ["c.tmx", "cyc.tx", "c.tmx"])
        # lenient mode tolerates the cycle but still reports it
        code, m, _ = self.run_scan("--allow-missing", self.p("c.tmx"))
        self.assertEqual(code, 0)
        self.assertEqual(len(m["cycles"]), 1)

    # -- path safety ---------------------------------------------------------
    def test_absolute_reference_rejected(self):
        w(self.p("m.tmx"),
          '<?xml version="1.0"?><map><tileset source="/etc/hostname"/></map>')
        for argv in ([self.p("m.tmx")], ["--allow-missing", self.p("m.tmx")]):
            code, m, _ = self.run_scan(*argv)
            self.assertEqual(code, 1, argv)  # rejections always fail
            self.assertEqual(len(m["rejected"]), 1)
            self.assertIn("absolute", m["rejected"][0]["reason"])
            self.assertEqual(m["totals"]["files"], 1)  # only the map itself

    def test_parent_traversal_rejected(self):
        w(self.p("sub/m.tmx"),
          '<?xml version="1.0"?><map><tileset source="../../outside.tsx"/></map>')
        code, m, _ = self.run_scan(self.p("sub/m.tmx"))
        self.assertEqual(code, 1)
        self.assertEqual(len(m["rejected"]), 1)
        self.assertIn("escapes --root", m["rejected"][0]["reason"])

    def test_symlink_escape_rejected(self):
        outside = tempfile.TemporaryDirectory()
        self.addCleanup(outside.cleanup)
        secret = os.path.join(outside.name, "secret.png")
        with open(secret, "wb") as f:
            f.write(b"SECRET")
        try:
            os.symlink(secret, self.p("link.png"))
        except OSError:
            self.skipTest("cannot create symlinks here")
        w(self.p("m.tmx"),
          '<?xml version="1.0"?><map><tileset source="t.tsx"/></map>')
        w(self.p("t.tsx"),
          '<?xml version="1.0"?><tileset><image source="link.png"/></tileset>')
        code, m, _ = self.run_scan(self.p("m.tmx"))
        self.assertEqual(code, 1)
        self.assertEqual(len(m["rejected"]), 1)
        self.assertIn("escapes --root", m["rejected"][0]["reason"])
        # the outside file must not appear in the manifest
        self.assertFalse(any("secret" in f["path"] for f in m["files"]))

    def test_symlink_inside_root_allowed(self):
        w(self.p("real.png"), b"DATA")
        try:
            os.symlink(self.p("real.png"), self.p("alias.png"))
        except OSError:
            self.skipTest("cannot create symlinks here")
        w(self.p("m.tmx"),
          '<?xml version="1.0"?><map><tileset source="t.tsx"/></map>')
        w(self.p("t.tsx"),
          '<?xml version="1.0"?><tileset><image source="alias.png"/></tileset>')
        code, m, _ = self.run_scan(self.p("m.tmx"))
        self.assertEqual(code, 0)
        self.assertEqual(len(m["rejected"]), 0)
        # canonical path recorded once
        pngs = [f for f in m["files"] if f["path"] == "real.png"]
        self.assertEqual(len(pngs), 1)

    def test_map_outside_root_rejected(self):
        other = tempfile.TemporaryDirectory()
        self.addCleanup(other.cleanup)
        mappath = os.path.join(other.name, "m.tmx")
        with open(mappath, "w") as f:
            f.write('<?xml version="1.0"?><map/>')
        code, m, _ = self.run_scan(mappath)
        self.assertEqual(code, 2)
        self.assertIsNone(m)

    # -- probe directory confinement -------------------------------------------
    def test_probe_dir_outside_root_rejected(self):
        mappath = self.build_basic()
        other = tempfile.TemporaryDirectory()
        self.addCleanup(other.cleanup)
        for flag in ("--plane", "--common"):
            code, m, err = self.run_scan(flag, other.name, mappath)
            self.assertEqual(code, 2, flag)
            self.assertIsNone(m, flag)
            self.assertIn("outside --root", err, flag)

    def test_probe_dir_symlink_escape_rejected(self):
        mappath = self.build_basic()
        other = tempfile.TemporaryDirectory()
        self.addCleanup(other.cleanup)
        link = self.p("evil-plane")
        try:
            os.symlink(other.name, link)
        except OSError:
            self.skipTest("cannot create symlinks here")
        code, m, err = self.run_scan("--plane", link, mappath)
        self.assertEqual(code, 2)
        self.assertIsNone(m)
        self.assertIn("outside --root", err)

    def test_probe_dir_inside_root_allowed(self):
        # --plane/--common inside root keep working (sprite probing intact).
        mappath = self.build_basic()
        w(self.p("planeX/sprites/chest.atlas"), ATLAS)
        w(self.p("planeX/sprites/chest.png"), b"CHESTPNG")
        code, m, _ = self.run_scan("--plane", self.p("planeX"), mappath)
        self.assertEqual(code, 0)
        sprites = [r for r in m["runtime_discovery"]
                   if r["kind"] == "sprite-atlas"]
        self.assertEqual(len(sprites), 1)
        self.assertIsNotNone(sprites[0]["resolved"])
        self.assertTrue(sprites[0]["resolved"]["path"].startswith("planeX/"))

    # -- reproducibility -------------------------------------------------------
    def test_deterministic_output(self):
        mappath = self.build_basic()
        _, m1, _ = self.run_scan(mappath)
        _, m2, _ = self.run_scan(mappath)
        j1 = json.dumps(m1, indent=2)
        j2 = json.dumps(m2, indent=2)
        self.assertEqual(j1, j2)
        # no absolute paths or timestamps by default
        self.assertNotIn("generated_utc", m1)
        for f in m1["files"]:
            self.assertFalse(os.path.isabs(f["path"]))
        self.assertNotIn(self.root, j1)

    def test_timestamp_opt_in(self):
        mappath = self.build_basic()
        code, m, _ = self.run_scan("--timestamp", mappath)
        self.assertEqual(code, 0)
        self.assertIn("generated_utc", m)

    # -- runtime discovery separation -------------------------------------------
    def test_runtime_assets_reported_separately(self):
        mappath = self.build_basic()
        w(self.p("common/sprites/chest.atlas"), ATLAS)
        w(self.p("common/sprites/chest.png"), b"CHESTPNG")
        code, m, _ = self.run_scan(
            "--common", self.p("common"), mappath)
        self.assertEqual(code, 0)
        sprites = [r for r in m["runtime_discovery"]
                   if r["kind"] == "sprite-atlas"]
        self.assertEqual(len(sprites), 1)
        self.assertEqual(sprites[0]["value"], "sprites/chest.atlas")
        self.assertIsNotNone(sprites[0]["resolved"])
        via = {f["path"]: f["via"] for f in m["files"]}
        self.assertEqual(via["common/sprites/chest.atlas"], "runtime-probe")
        self.assertEqual(via["common/sprites/chest.png"], "runtime-probe")
        # static totals exclude runtime-probed files
        static = [f for f in m["files"] if f["via"] == "static"]
        self.assertEqual(m["totals"]["static_bytes"],
                         sum(f["size"] for f in static))
        # reward-spec is identified but never resolved to a file
        rewards = [r for r in m["runtime_discovery"]
                   if r["kind"] == "reward-spec"]
        self.assertEqual(len(rewards), 1)
        self.assertIsNone(rewards[0]["resolved"])

    def test_sprite_without_probe_dirs_unresolved(self):
        mappath = self.build_basic()
        code, m, _ = self.run_scan(mappath)
        self.assertEqual(code, 0)
        sprites = [r for r in m["runtime_discovery"]
                   if r["kind"] == "sprite-atlas"]
        self.assertEqual(len(sprites), 1)
        self.assertIsNone(sprites[0]["resolved"])
        self.assertEqual(m["totals"]["runtime_probed_files"], 0)


if __name__ == "__main__":
    unittest.main()
