"""Compare exported browser case JSON to the actual JVM control; never infer execution."""
import argparse
import json
from pathlib import Path

def compare(reference, browser, variant):
    if not reference.get("completed") or not reference.get("passed"):
        raise ValueError("JVM comparator is not a passing completed run")
    if not browser.get("completed"):
        raise ValueError("Browser run did not complete")
    expected = {row["id"]: row for row in reference["cases"]}
    actual = {row["id"]: row for row in browser["cases"]}
    if len(actual) != len(browser["cases"]) or actual.keys() != expected.keys():
        raise ValueError("Missing, duplicated or unexpected browser cases")
    failures = []
    for name, control in expected.items():
        observed = actual[name]
        if variant == "missing-amount" and name == "K2":
            observations = observed.get("observations", {})
            if (observed.get("passed") is not False
                    or observed.get("actualClass") != "forge.game.keyword.SimpleKeyword"
                    or observations.get("keyword") != "BUSHIDO"
                    or observations.get("amount") != 1
                    or "exact concrete class" not in observed.get("error", "")):
                failures.append("K2 did not demonstrate the expected silent fallback/class assertion")
            continue
        for field in ["passed", "expectedClass", "actualClass", "observations"]:
            if observed.get(field) != control.get(field):
                failures.append(name + ": mismatch in " + field)
    passed_count = sum(row.get("passed") is True for row in browser["cases"])
    if browser.get("totalCases") != len(actual) or browser.get("passedCases") != passed_count:
        failures.append("Browser aggregate counts disagree with cases")
    if browser.get("passed") != (passed_count == len(actual)):
        failures.append("Browser aggregate pass flag disagrees with cases")
    if variant == "missing-amount" and browser.get("passed") is not False:
        failures.append("Negative artifact must not claim overall success")
    if failures:
        raise ValueError("\n".join(failures))
    return {"variant": variant, "comparisonPassed": True, "cases": len(actual),
            "negativeControlDemonstrated": variant == "missing-amount"}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jvm", type=Path)
    parser.add_argument("browser", type=Path)
    parser.add_argument("--variant", choices=["default", "explicit", "missing-amount"], required=True)
    args = parser.parse_args()
    result = compare(json.loads(args.jvm.read_text(encoding="utf-8")),
                     json.loads(args.browser.read_text(encoding="utf-8")), args.variant)
    print(json.dumps(result, indent=2))

if __name__ == "__main__":
    main()
