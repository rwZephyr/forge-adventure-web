# PR #23: two authorized cleanup verification runs

2026-10-18 owner-local date; 2026-10-19 UTC. Reviewed source:
`ffd0ec1e4a3c79026aa3aa03f726e8d6be5a5b83`. Following
[Marvin's source review and the coordinator's runtime authorization](https://github.com/rwZephyr/forge-adventure-web/pull/23#issuecomment-6259017775),
**exactly two sequential Maven/Surefire runs passed**. No test-policy source
change, retry, direct TestNG-main execution or positive DTO rerun occurred.

## Commands and actual results

Run A was executed once through the unchanged scoped launcher:

```powershell
./experiments/embedded-dto/Run-Scoped.ps1 -Case capture -Label cleanup-success-new -Maven
```

Only after A's result, process exits, source/cache checks and environment
restoration passed, Run B was executed once:

```powershell
./experiments/embedded-dto/Run-Scoped.ps1 -Case unanswered -Label cleanup-unanswered-new -Maven
```

Both labels were unused at launch and now contain preserved private originals.
The launcher invokes portable Maven 3.9.16 with `-o -B -ntp -T1`, the existing
`.local-tools/m2` cache, `-pl forge-gui-desktop -am` and its exact single-method
selector. It sets the already-approved isolated home/tmp/appdata environment,
JDK 17 and headless Java options. No plugin was skipped and no POM was edited.

| Run | TestNG method | Result | Maven / fork exit | Cleanup concessions | Graph overlaps |
| --- | --- | --- | --- | --- | --- |
| A | `humanBoltCaptureCheckpoint` | 1 PASS, 0 failed/skipped | 0 / 0 | 1 | 0 |
| B | `browserDtoUnansweredPromptIsReported` | 1 PASS, 0 failed/skipped | 0 / 0 | 0 | 0 |

[Run index](evidence/cleanup-verification-20261018/runs.json),
[A's complete console](evidence/cleanup-verification-20261018/cleanup-success-new/console.log),
[B's complete console](evidence/cleanup-verification-20261018/cleanup-unanswered-new/console.log)
and [file manifest](evidence/cleanup-verification-20261018/manifest.json) preserve
the actual outputs, reports, hashes, normalization and process observations.

## What each run establishes

**A:** Original Forge targeted the opposing player, paid one real RED manually
from Mountain, used the original stack, resolved Bolt for 20 → 17 life, and
put Bolt in the graveyard. After `human-success`, a new terminal
`capture-coherent` checkpoint reports the original priority input parked,
submitted/completed worker counts 2/1, one active loop, empty stack and agreeing
engine/view state. Only then does `cleanup-concede` report
`success-only settled checkpoint`. No atomic/secondary failure or overlapping
graph operation appears. The test and normal cleanup both passed.

**B:** The scenario deliberately refused the required `getAbilityToPlay` reply.
Its atomic artifact records `required-question` / `deliberately unanswered
getAbilityToPlay` on AWT EDT thread 67. The owning TestNG method on thread 27
logged `negative-owner-received`, `sameAtomicArtifact=true`,
`noCastOrPayment=true`, then **passed**. The existing owner assertions checked
the artifact action/reason, cross-thread origin and reference identity
`received.getCause() == artifact.error()`. Before throwing, the original graph
check established empty stack, both lives 20, untapped Mountain and empty human
mana pool; the owner also required no stack, targeting or manual-payment credit.
This is an attempted cast stopped at its required question, not a completed cast.

There is exactly one intentional atomic failure, zero secondary failures,
zero `cleanup-concede` entries and zero overlapping graph-operation errors.
The failed scenario was not reused for another method in that JVM. Cached
input identity in the failure log remains diagnostic context; this run does
not add another live-reference/JDI identity audit or a post-abort graph walk.

## Actual fork exits, not direct TestNG-main exits

Both builds log Surefire's `org.apache.maven.surefire.testng.TestNGProvider`.
Passive OS process observation captured separate Maven and `java -jar
...surefirebooter...jar` processes, their parent IDs, creation/exit timestamps
and native exit codes. The observed fork PIDs were 77300 (A) and 59912 (B),
both exit **0**. Maven also exited 0 in each run. Before and after each run the
Java inventory was empty; no process was killed and no owned JVM remained.

| Measurement | A | B |
| --- | --- | --- |
| TestNG method duration | 21.166 s | 20.435 s |
| Surefire fork lifetime, including bootstrap/reporting | 22.155 s | 21.503 s |
| Observed whole launcher interval, including offline compilation | 67.636 s | 64.833 s |
| Fork exit after reported TestNG finish second (rounded up) | 0.833 s | 1.074 s |

The final row uses TestNG's **one-second-resolution** finish timestamp: it is
an upper bound on post-method exit latency, not an exact latency measurement.
The [A](evidence/cleanup-verification-20261018/cleanup-success-new/process-observation.json)
and [B](evidence/cleanup-verification-20261018/cleanup-unanswered-new/process-observation.json)
records contain the original UTC OS timestamps. A passive PowerShell observer
launched the existing script unchanged, retained process handles and inspected
only its descendant Java processes. Its 240-second observation limit never
expired; it was not a process-killing watchdog or a test timeout.

**Verified here:** the actual Surefire-forked JVM exited promptly even for B's
abandoned original input. **Not executed here:** `org.testng.TestNG.main`.
The earlier cached-bytecode inspection of that direct runner remains background
information and is not the explanation for these Maven runs. No exit-method
instrumentation was added, so process exit alone does not identify the exact
`System.exit`/shutdown implementation branch. Arbitrary VM/native/reporting
hangs and forced post-admission races remain outside these two checks.

## Integrity, privacy and limits

Both runs record the same on-disk test source SHA-256:
`ca1c9cc44601a7a46255dd791b9d6d4e2335d49d4f7ea0907680b8a45ed08ed7`.
The launcher hash is unchanged:
`416cd2e6d7c72c8d07fa26a6d09eb339c6024cd6add3a6aa18dfe8a6d055c1a6`.
[Integrity evidence](evidence/cleanup-verification-20261018/integrity.json)
records 17 unchanged source/configuration hashes and all **2,393 cache files
unchanged**, with no additions/removals/content changes. Compilation generated
ordinary project-local outputs. No unexpected tracked source/configuration
change was observed; no environment settings outside the launcher's process
were written.

Each launcher passed the absent/present environment-restoration preflight and
exact restoration of all six documented variables. Original PR #22 evidence,
its three positive DTO runs and regressions, and C1's historical
`environmentRestored:false` record are preserved unchanged.

Private originals remain outside Git. Publication removes entire Surefire
system-property blocks, substitutes workspace/JDK paths and any XML hostname,
and normalizes UTF-8/LF/whitespace and VM lambda suffixes. The manifest records
original/published hashes and sizes. Published XML/JSON was parsed and evidence
checked for credentials, tokens and remaining private path/hostname details.
No JARs, POM downloads, compiled outputs, temporary profiles or private homes
are committed.

The two required outcomes are supported for review. They do not exercise every
exception/interleaving in the [policy](failure-cleanup-policy.md), prove general
graph ownership, or establish browser/service readiness. No production code,
observer, launcher, dependency, listener, browser or network/service changes,
further implementation or merges. Next: review this evidence, then await a
separate owner instruction.
