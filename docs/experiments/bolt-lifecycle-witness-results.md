# D3c — single authorized attempt stopped at process validation

**Blocked, not a lifecycle pass.** The one owner-authorized attempt compiled
successfully, then failed the launcher's child executable/entry-point check.
No retry, source correction or additional scenario was executed. PR #27 remains
draft, stacked on #26; the executed implementation is unchanged.

## Execution and first failure

[Owner authorization](https://github.com/rwZephyr/forge-adventure-web/pull/27#issuecomment-6275217598)
approved exactly one attempt of the [reviewed plan](bolt-lifecycle-witness-plan.md)
at `f7e9e1fdba6ce56fa050c37f7f527c554e402e38`. From the repository root in
PowerShell 7, the exact command was:

```powershell
./experiments/standalone-bolt/Run-Lifecycle.ps1 -Label d3c-first-1 -ApprovedSingleRun
```

The label was unused and the worktree clean. Execution used the existing JDK 17,
99 existing classpath entries with the TestNG JAR excluded, and the existing
offline cache. No Maven invocation or dependency acquisition occurred. Read-only
CIM process-inspection permission was available for the documented supervision.

The launcher's result interval (after its initial integrity inventory) was
2026-10-19 19:17:34.939–19:17:38.759 UTC. The execution tool reported exit **1**:

```text
Run-Lifecycle.ps1:89
Unexpected owned child executable/entry point
```

The [unchanged guard](../../experiments/standalone-bolt/Run-Lifecycle.ps1#L88)
requires both the expected Java executable path and a command line containing
`experiment.BoltLifecycleMain`. Before this guard, the launcher retained the
child handle and checked its creation time and parent identity. The guard failed;
the retained evidence **does not contain the actual CIM ExecutablePath or
CommandLine values**. Its combined disjunction cannot identify which comparison
failed, nor establish the reason. It would be unsupported to label this a Forge
ordering failure, an unexpected executable, or a harmless path mismatch.

## Actual outcomes

| Component | Observed result |
| --- | --- |
| Offline `javac` | Exit 0; six approved source files compiled. Stderr contains only the inherited `JAVA_TOOL_OPTIONS` notice. |
| Observer process | Started, then force-terminated by the launcher; exit −1, no timeout. |
| Direct child process | Retained by the launcher, then force-terminated; exit −1. Expected entry-point validation did not pass. |
| Lifecycle trace | `ordering.jsonl` exists with **zero bytes / zero events**. |
| Lifecycle/gameplay assertions | **Not established:** input generations, three C1 questions, payment ordering, cast, target, mana, resolution and 20→17 result. |
| Environment | Absent/present restoration preflight passed; all six environment variables restored exactly. |
| Integrity | 2,087 protected source/config/card/historical-evidence files and 2,393 cache files unchanged; before/after inventory files are byte-identical. |

The compile PID was 92840, observer PID 93492 and direct child PID 31148.
`result.json` records both forced terminations through retained owned handles;
there was no broad process-name kill. At 19:18:19 UTC, separate read-only
`Get-Process` checks found none of these three PIDs present. This verifies the
recorded processes' exits, not Game-1 thread death or execution of the driver.
No concession, cancellation, input release, manual latch release or other
gameplay cleanup was used.

## Evidence and limitations

[Complete retained evidence and manifest](../../experiments/standalone-bolt/evidence/d3c-first-1/manifest.json)
include compilation/observer stdout and stderr, the empty trace, classpath/source
inputs, full before/after hash inventories, launcher result, failure transcription
and post-exit PID checks. The compile and observer stdout files contain only a
newline; both stderr logs contain only the Java-options notice.

`target-process.json`, target stdout/stderr, `driver-result.json` and
`ordering-result.json` were **not produced**. The observer did not record its
successful connector-launch/target-PID checkpoint before termination. The exact
interruption point inside launch is unobserved. Target output therefore cannot
be claimed complete or recovered; absent files are listed in the manifest and
are not replaced with fabricated empty logs or result objects. The launcher's
primary exception was visible in the execution tool but was not included in
`result.json`; `launcher-failure.txt` explicitly identifies its supplemental
transcription provenance rather than claiming to be a redirected raw stream.

Private originals remain in `.local-tools/bolt-lifecycle/d3c-first-1/`.
Public copies normalize repository absolute paths and line endings/UTF-8; the
manifest records original/public sizes and SHA-256 hashes, source hashes,
normalization rules and privacy checks. Logs were inspected for credentials,
host/user/profile identifiers and private paths. No classes, dependency binaries,
downloaded POMs or temporary profiles are published. Historical PR evidence and
the four executed witness/launcher source files remain unchanged.

## Review gate

The current blocker is launch supervision, before a usable lifecycle observation.
The smallest proposed next step is review of the executable/command-line guard
and its missing failure diagnostics. Any change to persist or reconcile those
values, and any fresh attempt, requires separate approval. Do not relax ownership
validation based on this incomplete evidence. No correction or second run is
included here. The ten-run batch, projector and await-entry hold remain on hold.
No capture permit or concurrency guarantee follows from this attempt.
