"""Assertion/comparator self-checks using mutated copies, not browser observations."""
import copy
import json
from pathlib import Path
import unittest
from compare_results import compare

REFERENCE = json.loads((Path(__file__).resolve().parents[1] / "build/jvm-results.json").read_text(encoding="utf-8"))

def fallback_fixture():
    result = copy.deepcopy(REFERENCE)
    row = next(row for row in result["cases"] if row["id"] == "K2")
    row["actualClass"] = "forge.game.keyword.SimpleKeyword"
    row["observations"] = {"keyword": "BUSHIDO", "original": "Bushido:2", "amount": 1}
    row["passed"] = False
    row["error"] = "Assertion failed: exact concrete class"
    result["passedCases"] -= 1
    result["passed"] = False
    return result

class ComparatorChecks(unittest.TestCase):
    def test_identical_control(self):
        self.assertTrue(compare(REFERENCE, copy.deepcopy(REFERENCE), "explicit")["comparisonPassed"])

    def test_fallback_is_failure_in_positive_artifact(self):
        with self.assertRaises(ValueError): compare(REFERENCE, fallback_fixture(), "explicit")

    def test_negative_control_accepts_only_expected_fallback(self):
        self.assertTrue(compare(REFERENCE, fallback_fixture(), "missing-amount")["negativeControlDemonstrated"])

    def test_missing_case_rejected(self):
        result = copy.deepcopy(REFERENCE); result["cases"].pop()
        with self.assertRaises(ValueError): compare(REFERENCE, result, "explicit")

    def test_enum_mismatch_rejected(self):
        result = copy.deepcopy(REFERENCE); result["cases"][0]["observations"]["names"].pop()
        with self.assertRaises(ValueError): compare(REFERENCE, result, "explicit")

    def test_wrong_subclass_rejected(self):
        result = copy.deepcopy(REFERENCE)
        row = next(row for row in result["cases"] if row["id"] == "K5")
        row["actualClass"] = "forge.game.keyword.KeywordWithCost"
        with self.assertRaises(ValueError): compare(REFERENCE, result, "explicit")

    def test_false_success_flag_rejected(self):
        result = fallback_fixture(); result["passed"] = True
        with self.assertRaises(ValueError): compare(REFERENCE, result, "missing-amount")

if __name__ == "__main__":
    unittest.main()
