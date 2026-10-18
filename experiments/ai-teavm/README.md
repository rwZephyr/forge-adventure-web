# Original Forge AI / TeaVM baseline probe

Approved Gate 2 Stage B **first slice only**, separate from PRs #8/#11. Original
Forge core/game/AI, card scripts and module POMs are unchanged. This target adds
test setup, result assertions, host-side metadata/auditing and a passive JDK
debugger witness. No compatibility implementation or runtime adapter.

[Actual results and blockers](RESULTS.md). The JVM fixture passes, but the
unchanged-runtime TeaVM attempt fails; there is no runnable browser artifact.
Compilation/input/output evidence is under [evidence](evidence/).

## Prerequisites already approved on Silent

- Existing Oracle JDK 17.0.12 at `C:/Program Files/Java/jdk-17`.
- Portable Maven 3.9.16 and Gradle 9.6.1 under repository `.local-tools/`;
  isolated `.local-tools/m2` and `.local-tools/gradle-cache`.
- Existing Python 3.12.10. No downloads/installation or global environment edits.
- Normal cached dependency graph from Stage A plus Forge AI's existing
  commons-math3 3.6.1. Guava remains 33.3.1-android; TeaVM 0.15.0,
  gdx-TeaVM 1.6.1 and libGDX 1.14.2 stay pinned.

## Exact reproduction

PowerShell from repository root, sequentially:

```powershell
./experiments/ai-teavm/scripts/Prepare-Forge.ps1
./experiments/ai-teavm/scripts/Invoke-Probe.ps1 -Task jvmControl -WriteLocks
./experiments/ai-teavm/scripts/Invoke-Probe.ps1 -Task metadataInventory -WriteLocks
./experiments/ai-teavm/scripts/Invoke-Probe.ps1 -Task jvmTrace -WriteLocks
./experiments/ai-teavm/scripts/Invoke-Probe.ps1 -Task dependencyInventory -WriteLocks
./experiments/ai-teavm/scripts/Invoke-Probe.ps1 -Task browserOriginal -WriteLocks
# Expected nonzero compiler exit: preserve diagnostics and stop; no adapter retry.
& 'C:/Users/Silent/AppData/Local/Programs/Python/Python312/python.exe' experiments/ai-teavm/scripts/collect_results.py
```

These are explicit steps, not an unattended server/deployment command. Scripts
record native output and fail on nonzero exit. `-WriteLocks` records resolved
configurations from the existing offline caches, including the compiler
configuration; it does not request a download. Gradle's server/run tasks are
disabled. No `install`, `package`, `deploy`, asset updater or GUI launcher runs.

`Prepare-Forge.ps1` selects JDK 17 for its process, expands to portable Maven
`-o -B -ntp -T1 -Dmaven.repo.local=<repo>/.local-tools/m2 -pl forge-ai -am
-DskipTests compile`, verifies originals against baseline
`db893dcdb14fdd85a05437ad458dc70ef704ca2e`, hashes source/class/POM inputs and
selects unchanged Lightning Bolt/Mountain scripts, English localization and
TypeLists. The two card scripts are embedded as generated Java string lines;
original CardRules parses them. Localizer and type bootstrap use genuine
file-backed resources and original parsers; no browser resource adapter exists.

`Invoke-Probe.ps1` expands to portable Gradle `-p experiments/ai-teavm --offline
--no-daemon --max-workers=1 --console=plain <task> [--write-locks]`, JDK 17,
1536 MiB host JVMs and repository-local Gradle cache. Java children use an
isolated `.local-tools/ai-test-home`, temp and AppData directories. Settings are
process-local and restored by wrappers. Fixtures have a 15-second parent
watchdog per fresh child; AI's original timeout remains two seconds.

JVM control creates three fresh positive Java processes and one empty-hand
negative. Each keeps the original SecureRandom and initializes it before the
AI deadline; cold entropy initialization is measured separately. No seed,
provider, AI timeout or outcome is overridden. Three decisions are all genuine
threaded choices, followed by original paid casting and stack resolution.

The Windows-only `jvmTrace` uses installed JDK JDI `dt_shmem`. It refuses socket
fallback, uses no target method invocation or Forge instrumentation, and reads
original source-line frames/fields at named sites. Positive/negative witness
processes are separate from the three unpaused controls. It disposes the debug
connection before waiting for VM shutdown, and destroys only its own child on
watchdog expiry. This is local shared-memory debugging, not a TCP debug server.

The metadata task inventories all original ApiType effects and AI map products:
491 public signatures audited, 346 granted by the fixture compiler policy.
The policy includes Stage A's 144, 200 unique effect classes and the two original
AI products used here (DamageDealAi/ManaAi); it does not trim Forge enums/maps.
Host reflection for inventory does not replace runtime factories. Browser
compiler entry is `probe.BrowserMain`; JVM-only runner, debugger and inventory
entry points are not called by that entry.

`collect_results.py` validates actual saved results, path witnesses and input
hashes; reuses Stage A's cached-POM license inventory helper without changing
that target; copies raw evidence; indexes all compiler errors and their call
chains; and rejects a retained runnable app.js after compilation failure.
It executes no game, browser or server. Replaying evidence validation is not a
new gameplay test. All original Forge attribution/licensing remains applicable;
dependency declarations are inventoried, not legal clearance for distribution.

The existing experiments and unrelated services/saves are untouched. A future
browser runtime test, dependency install or boundary adaptation needs separate
review/approval. No complete match runner or Adventure port is included.
