"""Audit owner-exported Safari default failure against JVM and recorded Chromium."""
import json
import sys
from pathlib import Path
repo = Path(__file__).resolve().parents[4]
session = Path(__file__).resolve().parent
sys.path.insert(0, str(repo / "experiments/rules-teavm/scripts"))
from compare_results import compare
safari = json.loads((session / "iphone-factory-default-01.json").read_text())
jvm = json.loads((repo / "experiments/rules-teavm/evidence/jvm-results.json").read_text())
chrome = json.loads((repo / "experiments/rules-teavm/evidence/browser-default-1.json").read_text())
try:
    compare(jvm, safari, "default")
except ValueError as error:
    rejection = str(error)
else:
    raise AssertionError("Default unexpectedly matched the passing JVM")
expected = {r["id"]: r for r in chrome["cases"]}
actual = {r["id"]: r for r in safari["cases"]}
assert len(actual) == len(safari["cases"]) == 15
assert actual.keys() == expected.keys()
assert safari["schemaVersion"] == 1 and safari["completed"] is True
assert safari["passed"] is False and safari["passedCases"] == 5 and safari["totalCases"] == 15
for name, control in expected.items():
    for field in ("passed", "expectedClass", "actualClass", "observations", "errorClass"):
        assert actual[name].get(field) == control.get(field), (name, field)
assert sum(r["passed"] is True for r in actual.values()) == 5
differences = [{"id": name, "chromium": expected[name].get("error"),
                "safari": row.get("error")}
               for name, row in actual.items()
               if row.get("error") != expected[name].get("error")]
result = {"variant": "default", "jvmMatch": False,
          "expectedJvmRejection": rejection,
          "chromiumCaseFieldsMatch": True, "cases": 15, "passedCases": 5,
          "browserErrorTextDifferences": differences}
(session / "iphone-factory-default-01-comparison.json").write_text(
    json.dumps(result, indent=2) + "\n", encoding="utf-8")
print(json.dumps(result, indent=2))
