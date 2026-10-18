# C1 passive identity observation

2026-10-18. **One authorized fresh run; C1 reference relationship demonstrated
for review. DTO implementation remains on hold.** Stacked on PR #20 at
`34ca03ece65fca0dce92dab832a0911554a07524`. The prior
[audit](../architecture/bolt-ability-identity-audit.md) accurately describes the
older evidence gap; this run supplies the missing observations.

Only [JdiWitness.java](../../experiments/ai-teavm/src/observer/java/probe/JdiWitness.java)
changed among source files: opt-in `--c1 OUTPUT` selects the unchanged existing
`ProtocolGuiGameInProcessTest.humanBoltTargetsOpponentWithManualMountainPayment`.
Existing observer entry points remain available; they were not rerun. Production
Forge code, all tests, POMs and the cache were unchanged. No DTOs, target-method
invocation, new dependencies, Maven acquisition, network listener or server.

## Actual result and identities

[Full witness](evidence/c1-identity-20261018/witness.json): observer exit 0,
child exit 0, TestNG **1 passed / 0 failed / 0 skipped**; `identityPassed`,
original-path, EDT-action and input-latch checks true. There are 25 identity
events plus 59 original-path observations. Observer interval 36.627 seconds;
debugger timing is not a performance measurement. Exactly one test JVM was
launched, with no retry or extra scenario.

All numbers below are **JDI object identities within that one JVM**, except
explicitly named Java thread IDs. Ability/view engine IDs 214/199 are retained
in the raw evidence only as labels; they are not the identity proof.

| Question | Thread (Java ID) / input object | Candidate abilities / GUI views | Cache object and actual key→value | Actual mapped method return |
| --- | --- | --- | --- | --- |
| Initial Bolt offer | EDT `AWT-EventQueue-0` (120); InputPassPriority **4106** | **1 / 1**; ability **4154**, view **4161** | **4144**: view **4161** → ability **4154** | **4154**, stored view **4161** |
| Bolt additional-cost offer | Game-0 (119); InputLockUI **4172** | **1 / 1**; ability **4154**, view **4161** | **4176**: view **4161** → ability **4154** | **4154**, stored view **4161** |
| Mountain additional-cost mana offer | Game-1 (123); InputPayManaOfCostPayment **4234** | **1 / 1**; ability **4287**, view **4294** | **4292**: view **4294** → ability **4287** | **4287**, stored view **4294** |

Every question uses controller object **3719**. In each call, `gui-offer` records
the actual ProtocolGuiGame argument list; `peer-reply` records the actual view
returned by the unchanged scripted IRemote; `mapped` records `resultView` and
the live cache just before lookup; `ability-return` records the actual JDI
MethodExit return value. These references agree exactly. The cache and its
key/value pairs remain unchanged during each question. The cache is rebuilt
between calls, while both Bolt calls retain the **same ability and same view**.
Both the pre-cache ability list and offered view list are singleton, closing
the older map-deduplication gap. The unchanged original playability filter and
the test's offered-host/spell-kind assertions still execute.

The initial input's original waiter is Game-0 (119), latch **4108**, count 1;
at the Game-0 question that old latch is count 0. At the Mountain question,
Game-0 waits on payment latch **4235**, count 1. Identity events include caller
IDs, input IDs, nanosecond observation times, stack frames and raw waiter state
bits (657/5/657); the [test log](evidence/c1-identity-20261018/stdout.log) records
WAITING/RUNNABLE/WAITING. No graph-overlap atomic failure, graph-retry or
partial-packet warning was found. This is bounded observation, not a general
ownership or safe browser-state-capture result.

## Binding the questions to the original attempt

- Initial InputPassPriority stores **4154** in its selected-ability list. The
  original PhaseHandler passes **4154** to `playChosenSpellAbility` on Game-0.
- Bolt `chooseOptionalAdditionalCosts` receives original **4154**, offers
  `[4154]`, chooses **4154**, observes zero optional costs and returns **4154**.
  The Game-0 offer is therefore the same authorized attempt's original ability,
  not a lookalike selected by card name or engine ID.
- The manual EDT Mountain action selects physical card object **4286**.
  `mana-dispatch` captures chosen ability **4287**, whose cache value's host is
  that same physical card **4286**, and payment input `saPaidFor` **4154**.
  `mana-execute` observes **4287** on Game-1 with the same `saPaidFor` **4154**.
  Mountain optional-cost processing receives/offers/chooses/returns **4287**,
  also with zero optional costs. `mana-pay` then passes that same chosen ability
  to the original mana-pool payment call for **4154**.
- Bolt's original human cost/payment, stack-add and resolution observations all
  retain ability **4154**. Its original `payingMana` contains mana object
  **4340**, color **8** (Forge RED), source game-card ID 2. The unchanged test
  asserts exactly one R from the clicked Mountain, tapped Mountain, opposing
  player target, one Bolt on stack at life 20, then original resolution to 17,
  Bolt in graveyard and empty stack. Log lines 115 and 131 record those checks.

**Copy/remapping result:** no SpellAbility copy was observed within the monitored
initial-selection/additional-cost call chains; both helpers return their input
references unchanged. Three distinct cache maps contain the pairs above; map
rebuilding does not create a new view/ability in this run. This does not imply
all Forge choices or alternative costs retain references.

The mana record's source object **4341** differs from physical Mountain **4286**.
Do not claim their reference equality. Original
[Mana construction](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/mana/Mana.java#L77)
stores a CardCopyService LKI source snapshot for an in-play card. That explains
the source representation by source inspection; this observer did not trace
the CardCopyService copy operation. The payment oracle is the original test's
color/source-card-ID/tap assertion, while ability-to-payment binding above uses
actual ability and physical-host references, not that source-ID coincidence.

For this fixture only, the reviewed authorization relation can now be expressed
without hard-coding IDs: let `S,V` be the unique initial actual Bolt ability/view;
the next Bolt offer and return must be exactly `S,V`. Let `M,W` be the unique
actual mana ability/view of the manually selected Mountain; its original worker
offer and return must be exactly `M,W`, and the payment input must bind `M` to
`S`. This is evidence for reviewing an allowlist, not an implementation or a
solution for obtaining future authorization at an unproven capture boundary.

## Observation method and limits

The C1 mode adds source-line breakpoints and narrowly armed, same-thread JDI
MethodExit requests. It inspects original locals and known ArrayList,
LinkedHashMap, FCollection and object fields; it never calls a target getter,
`equals`, collection method or other target method. Return values come from
`MethodExitEvent.returnValue()`. Unsupported layouts/missing locals/returns,
non-singleton offers, cache changes, unexpected questions/copies or mismatched
references cause failure. No field writes, GC pinning, game decisions or
threading adapters were added. Shared-memory `dt_shmem` only; no socket fallback.

The source was compiled before this one run. Its SHA-256 is
`785f6951330b723cac88b392046170a330709fcfa2e0d11540e632cdf1b0dc0f`.
Unchanged test source SHA-256 remains
`c3ca6b6a00b91755c5eb5d131d2f47ad99ecec72e673beed71c8e5cedaedbf16`.
Passive breakpoints perturb scheduling; the earlier unpaused #19 runs remain
the independent gameplay evidence. No general concurrency, privacy projection,
async-dialog, browser or reconnect guarantee follows from this observation.

**Environment verification limitation:** the local launcher's post-restore
comparison reports `environmentRestored:false` in the preserved
[run record](evidence/c1-identity-20261018/run-result.json). Original environment
values were not persisted, so the mismatching variable/cause cannot be recovered
from this run; do not claim the comparison passed. All six assignments were
Process-scoped inside the disposable PowerShell process, which exited. No
system/user environment settings were written. The child logs confirm the
intended fresh home/temp paths. No rerun or environment repair was attempted.

[Integrity evidence](evidence/c1-identity-20261018/integrity.json) records identical
before/after hashes for 17 source/config/card inputs, 3,328 existing compiled
class files and all 2,393 Maven-cache files. No inputs changed and no Java/javaw
process remained. This is a scoped integrity check, not an all-disk audit.
Git's source diff contains only JdiWitness; generated output stays below the
new repository-local isolation directory. Complete logs and TestNG XML are
published with workspace/JDK path substitutions and VM lambda-address suffix
normalization; JDI identities are preserved. No binary build output, downloaded
artifacts, HTML report assets, temporary profiles or original environment values are
committed. [Manifest](evidence/c1-identity-20261018/manifest.json) identifies the
sanitized files and original hashes; originals/inventory snapshots stay local.

## Exact reproduction (not a second-run authorization)

Use the existing compiled PR #19 test/runtime and local-cache classpath produced
by its normal offline build. No Maven lifecycle/test rerun was needed here.
The actual local wrapper is `.local-tools/c1-identity-20261018/Run-C1.ps1`; it
refuses a second start once `one-run/run-started.json` exists, snapshots inputs
before/after, and restores saved process variables (with the limitation above).
Its substantive commands are below; use a **new** path and obtain approval
before another run. Run in a disposable PowerShell process from repository root.

```powershell
$repo = (Get-Location).Path
$root = "$repo/.local-tools/c1-identity-20261018"
$run = "$root/one-run"
$env:JAVA_HOME = '<JDK17>' # existing installation, no install
foreach ($name in @('observer-classes','one-run/home','one-run/tmp',
  'one-run/appdata','one-run/localappdata','one-run/witness')) {
  New-Item -ItemType Directory -Force -Path "$root/$name" | Out-Null
}
& "$env:JAVA_HOME/bin/javac.exe" --add-modules jdk.jdi `
  -d "$root/observer-classes" `
  experiments/ai-teavm/src/observer/java/probe/JdiWitness.java `
  experiments/ai-teavm/src/main/java/probe/Json.java
if ($LASTEXITCODE -ne 0) { throw 'Observer compilation failed' }
[xml]$report = Get-Content "$repo/forge-gui-desktop/target/surefire-reports/TEST-forge.net.embedded.ProtocolGuiGameInProcessTest.xml"
$classpath = ($report.testsuite.properties.property |
  Where-Object name -eq 'surefire.test.class.path').value
# All nonempty entries were checked present; stop rather than acquire anything.
$env:JAVA_TOOL_OPTIONS = "-Duser.home=$run/home -Djava.io.tmpdir=$run/tmp -Djava.awt.headless=true -Xmx1536m"
$env:APPDATA = "$run/appdata"; $env:LOCALAPPDATA = "$run/localappdata"
$env:TEMP = "$run/tmp"; $env:TMP = "$run/tmp"
Set-Location "$repo/forge-gui-desktop"
& "$env:JAVA_HOME/bin/java.exe" --add-modules jdk.jdi `
  -cp "$root/observer-classes;$classpath" probe.JdiWitness --c1 "$run/witness"
# Record exit code, witness.json and the actual one-test XML; close this shell.
```

Compilation produced no diagnostics. One `--c1` launch produced the results
above; no other mode, test case or retry was executed. The inherited child
launch preserves the original JVM opens and 210-second external watchdog.
Review this evidence, including the environment-check limitation, before any
further work. PRs #17–#20 stay unchanged/draft/unmerged; DTO implementation and
browser-safe capture remain separate gates.
