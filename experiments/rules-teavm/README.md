# Original Forge factory probe — Stage A

This implements the factory stage approved after [PR #9](https://github.com/rwZephyr/forge-adventure-web/pull/9).
It is an isolated Java/TeaVM feasibility test, not Adventure, a duel, AI or a
substitute rules engine. Original Forge classes and Maven POMs remain unchanged.
See [RESULTS.md](RESULTS.md) for actual outcomes, failures and limits.

## Scope and assertions

`FactoryCases.java` calls the original TriggerType, ReplacementType and Keyword
factories. JVM and browser execute that same compiled harness. Nine factory rows
cover TriggerAlways, TriggerLifeGained, ReplaceDraw, ReplaceGainLife, Flying,
Bushido:2, Enchant:Card.Creature:creature, Multikicker:1 R and Kicker:1 R. Six
additional rows inventory all three enums and test two invalid names and an
unknown keyword. Checks throw explicitly; JVM `-ea` is unnecessary.

Every product must have the exact concrete class, preserved input and parsed
values. Trigger/replacement checks cover both intrinsic values, independent
instances and parameter copies after input mutation. The only predicate calls
are concrete TriggerAlways/TriggerLifeGained/ReplaceGainLife methods. No
ReplaceDraw predicate, host-based descriptions or keyword createTraits is called.
Host is deliberately null; there is no fake Card, Game or Localizer.

The actual JVM inventory is **149 TriggerType entries, 137 distinct trigger
classes**. An initial whitespace-sensitive source pattern omitted DayTimeChanges;
the corrected generator compares its complete mapping with the JVM inventory.
`reflection-constructors.tsv` allows only 144 original constructors: 137 trigger,
two replacement, five keyword. The compiler audits every signature against the
unchanged JVM bytecode without initializing Forge classes.

Three independent compiler processes produce:

- `default`: gdx-TeaVM/TeaVM defaults, no Forge reflection additions.
- `explicit`: constructor-only ReflectionPolicy; broad gdx reflection disabled.
- `missing-amount`: identical explicit policy except KeywordWithAmount's no-arg
  constructor is withheld. K2 must become SimpleKeyword and fail its class check.

The compiler policy is host-side only and registered through TeaVM's SPI service.
No blanket Forge reflection, retained arbitrary methods, stubs or engine patches.
Fresh compiler processes prevent policy state leaking between variants. A custom
TeaVMTool launcher permits this control while retaining the pinned gdx-TeaVM
backend extensions; the plugin's server/run tasks are disabled.

## Reproduce on Silent

Prerequisites already approved/installed in the earlier baseline task:
`.local-tools/apache-maven-3.9.16`, `.local-tools/gradle-9.6.1`, isolated
`.local-tools/m2` and `.local-tools/gradle-cache`, existing
`C:/Program Files/Java/jdk-17`, and existing Python 3. No installer/download is
included. The portable setup is described on [PR #8's branch](https://github.com/rwZephyr/forge-adventure-web/blob/57ed01d137e45ea2a304b64ef979402347cb0843/experiments/adventure-teavm/README.md).
If prerequisites/caches are missing, obtain approval before installing them.

From repository root in PowerShell:

```powershell
./experiments/rules-teavm/scripts/Prepare-Forge.ps1
./experiments/rules-teavm/scripts/Invoke-Probe.ps1 -Task jvmControl
./experiments/rules-teavm/scripts/Invoke-Probe.ps1 -Task browserDefault
./experiments/rules-teavm/scripts/Invoke-Probe.ps1 -Task browserExplicit
./experiments/rules-teavm/scripts/Invoke-Probe.ps1 -Task browserMissingAmount
./experiments/rules-teavm/scripts/Invoke-Probe.ps1 -Task dependencyInventory
python experiments/rules-teavm/scripts/test_compare_results.py
```

All builds run offline, with locked dependencies, process-local JDK 17 and
isolated caches. Scripts accept `-JdkHome` for another existing JDK 17. Maven runs
only `-pl forge-game -am -DskipTests compile`, not install/package/deploy.
Gradle and each JavaExec use at most 1536 MiB, one worker; compiler processes run
sequentially and single-use Gradle daemons exit. Use `-WriteLocks` only to
intentionally regenerate the committed lockfile. No downloads were needed here.

Prepare-Forge hashes original sources/classes/POMs into `build/input-manifest.json`.
Every harness compile checks these hashes. Run preparation again after rebuilding
Forge; no stale snapshot is accepted. The generator also audits original enum
mappings. Compilation products, tools and caches are ignored. Scripts overwrite
their named evidence logs when rerun; preserve a run's evidence before rerunning.

Compiler outputs: `build/web/{default,explicit,missing-amount}/index.html`, app.js,
source map, debug sources and Forge license. A compiler error removes app.js;
never count partial output or a generated page as a pass. Successful generation
still requires browser execution and comparison.

## Browser test — separate server approval required

After explicit approval to serve just this artifact locally:

```powershell
./experiments/rules-teavm/scripts/Serve-Loopback.ps1 -Approved
# Open http://127.0.0.1:8765/default/ (then explicit/ and missing-amount/).
# Ctrl+C stops the temporary server after testing.
```

Only `build/web` is served, bound to 127.0.0.1. No LAN-facing address, Tailscale,
firewall, credential or service change. The build itself never starts a listener.
Actual Safari/iPhone tests require a separately agreed private arrangement.

Each page displays overall status, case summary, raw JSON, uncaught errors and
console warn/error captures. `window.__factoryProbe` contains the result, but a
readiness flag alone cannot pass. Export the actual `#results` JSON, browser/OS
identity and diagnostics; reload three times in fresh page state. Expected
negative artifact status is FAIL, specifically K2 with SimpleKeyword, amount 1.

Compare exported files, for example:

```powershell
python experiments/rules-teavm/scripts/compare_results.py experiments/rules-teavm/build/jvm-results.json experiments/rules-teavm/evidence/browser-explicit-1.json --variant explicit
python experiments/rules-teavm/scripts/compare_results.py experiments/rules-teavm/build/jvm-results.json experiments/rules-teavm/evidence/browser-missing-amount-1.json --variant missing-amount
```

Positive artifacts must match every JVM case/class/value/enum inventory. The
negative comparator permits only the specified K2 failure; every other row must
match JVM. It rejects missing/duplicate rows, incorrect aggregate status and
wrong subclasses. Its seven mutation-based self-checks are comparator tests,
not browser observations. A 15-second independent observation bound applies per
load; no in-page timer is treated as proof that a frozen JS thread progressed.

## Source and licensing

Forge source baseline: `db893dcdb14fdd85a05437ad458dc70ef704ca2e`, unchanged
core/game modules used in place. [Approved design and pinned source references](../../docs/experiments/gate-2-factory-design.md).
No AI/GUI module or original game asset is included. The compiler classpath is
wider than transpiled reachability; it does not prove compatibility of everything
in those modules. Preserve upstream GPL notices and corresponding source when
sharing generated output. Dependency hashes and a license inventory accompany
the results; this private experiment is not public distribution clearance.
