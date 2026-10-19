# D3c launcher safety correction — execution remains HOLD

Source-only follow-up to the author audit of PR #27 at
`c43d0bba9a3e9c67a26af79a42824e6bf80359c8`. This is implementation for Marvin's
independent review, not execution approval. Original Forge/Java witness sources
and the failed `d3c-first-1` public/private evidence remain unchanged.

## Changes and exact source locations

| Area | Source | Behavior |
| --- | --- | --- |
| Authorization and safety gate | [Run-Lifecycle.ps1:1–13](../../experiments/standalone-bolt/Run-Lifecycle.ps1#L1-L13) | Keeps approval/fresh-label guards, then unconditionally refuses before helper load, CIM, environment mutation or process startup. No bypass option. |
| Original failure | [LauncherSafety.ps1:2–20](../../experiments/standalone-bolt/LauncherSafety.ps1#L2-L20) | Retains the original ErrorRecord, exception and stage; secondary failures are separate. If finalization is the first failure, it becomes primary. Public evidence contains stage/type/HResult, never exception messages, stacks or arguments. |
| Strict main argument | [LauncherSafety.ps1:22–75](../../experiments/standalone-bolt/LauncherSafety.ps1#L22-L75) | Restricted argument parsing identifies the first main-class token after recognized JVM options; requires the exact case-sensitive class, one classpath and the single expected result-file argument. Decoys in option values/classpaths or later arguments do not authorize. Unknown options, argument files and ambiguous quoting refuse. |
| Process cleanup | [LauncherSafety.ps1:77–105](../../experiments/standalone-bolt/LauncherSafety.ps1#L77-L105) | Independently attempts inspection, termination, checked timed wait, exit observation, each output stream, recording and disposal. Failure at one step does not skip the others or later owned processes. |
| Ownership and dispatch | [Run-Lifecycle.ps1:60–125](../../experiments/standalone-bolt/Run-Lifecycle.ps1#L60-L125) | Registers the launcher-created root immediately after Start; child handles enter terminating cleanup only after creation-time and rechecked PID/parent identity. Keeps expected executable equality and live-exit checks. Unconfirmed handles receive disposal only. |
| Outer finalization | [Run-Lifecycle.ps1:194–237](../../experiments/standalone-bolt/Run-Lifecycle.ps1#L194-L237) | Separately attempts every captured environment restoration/check, integrity evidence, result and failure evidence. An enclosing catch records unexpected outer-finally errors; the original ErrorRecord is rethrown after finalization. |

Child validation still records independent executable/main, parent, creation-time,
field-presence and field-length verdicts. Main rejection reasons are fixed enums;
actual class names, arbitrary paths and command-line text are never copied into
diagnostics. Exception type names are limited to 160 characters. ErrorRecord
messages remain in memory for the owner-side rethrow, not in JSON. Private console
output from a future original-error rethrow would require the usual publication
privacy review; there is no automatic log publication.

The restricted parser is intentionally not a general Windows/Java command-line
parser. The synthetic accepted launch shape is grounded in the unchanged JDI
observer's `home/options/main` construction and original POM `argLine`. Actual
JDI/CIM formatting remains unverified; no alternate quoting or unknown option is
silently accepted. Java source, classpath acquisition and gameplay are unchanged.

## Deadline gate: not solved by an operation-timeout parameter

The three CIM calls now request `-OperationTimeoutSec 5`; existing elapsed-time
budgets and process/output timed waits remain, with wait results checked. However,
a synchronous provider/native operation can prevent the PowerShell supervisor
from checking its own clock. These parameters do **not** establish an unconditional
wall-clock bound for the launcher, cleanup, filesystem I/O or CIM initialization.

Accordingly the launcher has an unconditional safety stop at line 11. Even a
fresh run label and `-ApprovedSingleRun` cannot start it. No launcher invocation
was used to test that gate: it was inspected and parsed only. Source below the
gate is review material, not an executable-ready certification.

The remaining specific obstacle is independent supervision that cannot itself
be held inside the blocking CIM/native operation and can terminate only the
owned process tree. Designing that boundary, including interrupted connector
launch/cleanup ownership, requires separate review; no worker process, job
object, external watchdog, service or dependency is introduced here. Do not
remove the gate merely because a further run is approved. Its removal needs a
reviewed supervision argument/source change as well.

## Static evidence and reproduction

From the repository root in existing PowerShell 7, **the only verification script
to execute** is:

```powershell
./experiments/standalone-bolt/Test-LauncherSafetyStatic.ps1
```

It dot-sources only helper definitions, parses the launcher without invoking it,
and uses strings/in-memory ScriptMethods as fake processes. It does not call
CIM, Process.Start, Java, Maven, environment restoration or original gameplay.
Final result: **30/30 checks passed**, execution-tool exit 0. Three PowerShell
files parsed with zero errors. Checks cover main-class decoys/quoting, false
timed waits, child kill/dispose failures followed by observer cleanup, disposal
after recording failure, restoration/evidence continuation, original ErrorRecord
and exception identity reaching the synthetic owner, and message privacy.
Earlier source iterations had 28/28 and 30/30 passing checks; private outputs
are retained separately. The published output corresponds to the final source.

[Complete synthetic output and source/output hashes](../../experiments/standalone-bolt/evidence/launcher-safety-static/manifest.json)
are static evidence, not a D3c lifecycle run. `git diff --check`, documentation
links and historical public/private evidence hash checks also passed. Actual
OS termination, output-reader failures, six-variable restoration and original
Forge lifecycle ordering have **not** been exercised by this correction.

Evidence writes are independent attempts, not a promise that broken storage can
persist data. `result.json` is a snapshot at its write point; later evidence I/O
failure still reaches the owner as a nonzero exception. `failures.json` is a later
snapshot, with a separate console attempt. Never infer success from an earlier
result alone if owner exit/failure evidence disagrees. Primary exception identity
is retained even when evidence writes fail.

No witness execution, compilation, dependency acquisition, network listener,
service change or merge occurred. Stop for independent review; no execution is
self-approved by these synthetic checks.
