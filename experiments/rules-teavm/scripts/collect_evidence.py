"""Validate actual observations and inventory local artifacts; never starts a browser/server."""
from pathlib import Path
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET
from compare_results import compare

ROOT = Path(__file__).resolve().parents[3]
PROJECT = ROOT / "experiments/rules-teavm"
EVIDENCE = PROJECT / "evidence"

def read(path):
    return json.loads(path.read_text(encoding="utf-8"))

def write(name, value):
    (EVIDENCE / name).write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def pom_path(coordinate):
    group, artifact, version = coordinate.split(":")
    local = ROOT / ".local-tools/m2" / group.replace(".", "/") / artifact / version / f"{artifact}-{version}.pom"
    if local.is_file(): return local
    cached = ROOT / ".local-tools/gradle-cache/caches/modules-2/files-2.1" / group / artifact / version
    return next(cached.glob(f"*/{artifact}-{version}.pom"), None)

def declared_licenses(coordinate, seen=None):
    seen = set() if seen is None else seen
    if coordinate in seen: return {"licenses": [], "note": "POM parent cycle"}
    seen.add(coordinate)
    path = pom_path(coordinate)
    if path is None: return {"licenses": [], "note": "No cached POM; not resolved"}
    xml = ET.parse(path).getroot()
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    licenses = [{"name": node.findtext("m:name", namespaces=ns), "url": node.findtext("m:url", namespaces=ns)}
                for node in xml.findall("m:licenses/m:license", ns)]
    if licenses: return {"licenses": licenses, "sourcePom": path.relative_to(ROOT).as_posix()}
    parent = xml.find("m:parent", ns)
    if parent is not None:
        parent_coord = ":".join(parent.findtext("m:" + key, namespaces=ns) or "" for key in ["groupId", "artifactId", "version"])
        result = declared_licenses(parent_coord, seen)
        result["inheritedFrom"] = parent_coord
        return result
    return {"licenses": [], "sourcePom": path.relative_to(ROOT).as_posix(), "note": "No license declared in cached POM"}

def main():
    baseline = "db893dcdb14fdd85a05437ad458dc70ef704ca2e"
    unchanged = subprocess.check_output(["git", "diff", baseline, "--", "forge-core", "forge-game", "forge-ai", "pom.xml"], cwd=ROOT)
    if unchanged.strip(): raise RuntimeError("Original Forge modules/POM changed")
    jvm = read(EVIDENCE / "jvm-results.json")
    if not jvm["passed"] or jvm["totalCases"] != 15: raise RuntimeError("JVM control did not pass")
    mappings = read(EVIDENCE / "trigger-mappings.json")
    names = jvm["cases"][0]["observations"]["names"]
    if set(mappings) != set(names): raise RuntimeError("Incomplete source-to-JVM enum mapping")
    comparisons = []
    for variant in ["explicit", "missing-amount"]:
        for run in range(1, 4):
            result = compare(jvm, read(EVIDENCE / f"browser-{variant}-{run}.json"), variant)
            result["run"] = run; comparisons.append(result)
    defaults = [read(EVIDENCE / f"browser-default-{run}.json") for run in range(1, 4)]
    if defaults[0] != defaults[1] or defaults[1] != defaults[2] or defaults[0]["passed"]:
        raise RuntimeError("Default outcome not repeatable or unexpectedly passing")
    for row in defaults[0]["cases"]:
        if row["id"] in ["K2", "K3", "K4", "K5"]:
            if row.get("actualClass") != "forge.game.keyword.SimpleKeyword" or row["passed"]:
                raise RuntimeError("Default silent fallback was not demonstrated")
    write("comparisons.json", comparisons)

    # Final clean builds must preserve the exact runtime bytes actually tested.
    clean_comparison = []
    for row in read(EVIDENCE / "pre-clean-artifacts.json"):
        if row["path"].endswith(("/app.js", "/index.html")):
            unchanged = digest(PROJECT / row["path"]) == row["sha256"]
            if not unchanged: raise RuntimeError("Runtime changed; browser must rerun: " + row["path"])
            clean_comparison.append({"path": row["path"], "testedBytesUnchanged": unchanged, "sha256": row["sha256"]})
    write("clean-output-comparison.json", clean_comparison)

    metadata = []
    constructors = [line.split("\t")[0] for line in (PROJECT / "reflection-constructors.tsv").read_text().splitlines() if line and not line.startswith("#")]
    artifacts = []
    for variant in ["default", "explicit", "missing-amount"]:
        folder = PROJECT / "build/web" / variant
        js = (folder / "app.js").read_text(encoding="utf-8")
        absent = []
        for cls in constructors:
            package, simple = cls.rsplit(".", 1)
            symbol = {"forge.game.trigger": "fgt", "forge.game.replacement": "fgr", "forge.game.keyword": "fgk"}[package] + "_" + simple
            start = js.find(symbol + ", {\n"); end = js.find("\n    }", start)
            if start < 0 or end < 0 or '"<init>"' not in js[start:end]: absent.append(cls)
        if variant == "explicit" and absent: raise RuntimeError("Explicit constructor metadata missing: " + str(absent))
        if variant == "missing-amount" and absent != ["forge.game.keyword.KeywordWithAmount"]:
            raise RuntimeError("Negative metadata omission not isolated: " + str(absent))
        metadata.append({"variant": variant, "missingConstructors": absent})
        for path in sorted(folder.rglob("*")):
            if path.is_file(): artifacts.append({"path": path.relative_to(PROJECT).as_posix(), "bytes": path.stat().st_size, "sha256": digest(path)})
    write("reflection-metadata.json", metadata)
    write("artifacts.json", artifacts)
    source_checks = []
    for variant in ["explicit", "missing-amount"]:
        for package, simple in [("trigger", "TriggerType"), ("trigger", "TriggerAlways"), ("trigger", "TriggerLifeGained"),
                                ("replacement", "ReplacementType"), ("replacement", "ReplaceDraw"), ("replacement", "ReplaceGainLife"),
                                ("keyword", "Keyword"), ("keyword", "SimpleKeyword"), ("keyword", "KeywordWithCost"),
                                ("keyword", "KeywordWithType"), ("keyword", "Kicker")]:
            relative = f"forge/game/{package}/{simple}.java"
            original = ROOT / "forge-game/src/main/java" / relative
            copied = PROJECT / "build/web" / variant / "src" / relative
            if original.read_text(encoding="utf-8") != copied.read_text(encoding="utf-8"):
                raise RuntimeError("Original debug source mismatch: " + str(copied))
            source_checks.append({"variant": variant, "source": relative, "newlineNormalizedMatch": True})
    write("source-copy-checks.json", source_checks)
    dependencies = read(EVIDENCE / "dependencies.json")
    licenses = [{"coordinate": coord, **declared_licenses(coord)} for coord in sorted({row["coordinate"] for row in dependencies})]
    write("dependency-licenses.json", licenses)

    builds = []
    for task in ["browserDefault", "browserExplicit", "browserMissingAmount"]:
        path = EVIDENCE / f"{task}.log"; log = path.read_text(encoding="utf-8-sig")
        success = "FACTORY_COMPILER severeProblems=0" in log and "BUILD SUCCESSFUL" in log
        if not success: raise RuntimeError("Final compiler run failed: " + task)
        builds.append({"task": task, "exitCode": 0, "result": re.search(r"BUILD SUCCESSFUL in (.+)", log).group(1),
                       "classes": int(re.search(r"INFO: Classes compiled: (\d+)", log).group(1)),
                       "methods": int(re.search(r"INFO: Methods compiled: (\d+)", log).group(1)), "log": path.name})
    initial_failures = []
    for path in sorted(EVIDENCE.glob("initial-*.log")):
        errors = [line.removeprefix("ERROR: ") for line in path.read_text(encoding="utf-8-sig").splitlines() if line.startswith("ERROR: ")]
        initial_failures.append({"log": path.name, "compilerErrorCount": len(errors), "compilerErrors": errors})
    write("build-results.json", {"baseline": baseline, "jdk": "17.0.12+8-LTS-286", "maven": "3.9.16", "gradle": "9.6.1", "teavm": "0.15.0", "gdxTeaVM": "1.6.1", "python": "3.12.10", "offline": True,
          "originalModulesUnchanged": True, "jvmPassedCases": 15, "triggerEntries": len(names), "distinctTriggerClasses": len(set(mappings.values())),
          "constructorSignatures": len(constructors), "builds": builds, "initialFailures": initial_failures,
          "stageAChromiumPassedWithExplicitMetadata": True, "safariVerified": False, "stageBImplemented": False})
    hashes = [{"path": p.name, "bytes": p.stat().st_size, "sha256": digest(p)} for p in sorted(EVIDENCE.iterdir()) if p.is_file() and p.name != "evidence-hashes.json"]
    write("evidence-hashes.json", hashes)
    print("Validated six browser/JVM comparisons, three default failures, complete constructor metadata, original source preservation and artifact hashes.")
    print("Dependency license declarations resolved:", sum(bool(row["licenses"]) for row in licenses), "/", len(licenses))

if __name__ == "__main__":
    main()
