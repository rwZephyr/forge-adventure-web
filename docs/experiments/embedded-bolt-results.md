# Authentic embedded Bolt extension — results and reproduction

2026-10-18. Bounded JVM test extension approved after PR #17's review and
PR #18's unchanged baseline. Branch `codex/embedded-bolt-tests`, stacked on
PR #18. Original runtime/source pin:
`db893dcdb14fdd85a05437ad458dc70ef704ca2e`.

## Scope and original paths

Only two source files are extended:

- [ProtocolGuiGameInProcessTest](../../forge-gui-desktop/src/test/java/forge/net/embedded/ProtocolGuiGameInProcessTest.java):
  opt-in strict answers, original HostedMatch fixture hooks, controller actions,
  primitive checkpoint assertions, thread/latch/graph-entry observations and
  atomic failure propagation to the TestNG owner. The historical method is
  unchanged, and RecordingRemote's default mode retains its historical answers.
- [JdiWitness](../../experiments/ai-teavm/src/observer/java/probe/JdiWitness.java):
  opt-in `--testng` observation of those same tests. Historical observer modes
  remain available. JDK shared-memory JDI only; no socket fallback, target
  method invocation, game setup or decisions in the observer.

TestUtils, HeadlessGuiDesktop, SingleThreadTest, POMs, ProtocolGuiGame, inputs,
rules, AI, cards and Adventure code are preserved. This is neither a second
game harness nor a browser transport or production headless service.

Both players have 40-card original decks. The existing start-game hook moves
match-created cards with original zone APIs, sets the approved small position
and MAIN1/priority, then refreshes last-state snapshots. It supplies no
SpellAbility, target, paid mana or damage. All subsequent progress goes through
the original phase loop and original player controllers.

| Case | Actual required behavior |
| --- | --- |
| Human | Bolt in hand, one untapped Mountain, empty pool/stack, opposing AI at 20. Select Bolt via IGameController; answer exact offered abilities; select the opposing player through InputSelectTargets; click Mountain through InputPayManaOfCostPayment. Observe one R paid from that source, tap, one stack item at 20 life, then loop resolution to 17, graveyard Bolt and empty stack. Concession is cleanup only. |
| AI | Human at 3; AI has two Bolts and two untapped Mountains, empty pools/stack, nonempty libraries. The original PlayerControllerAi/AiController chooses and casts; human only passes priority. Observe original opponent target, one paid R and one tapped Mountain, stack at 3 life, then resolution to 0 and an original GameOutcome naming AI as winner. |
| Empty hand | AI deck/hand contains no Bolts; two Mountains remain. Stop at first settled human priority after the AI's original phase-loop decision. Require no spell, payment, damage or tapped source; original mana candidates/tasks are allowed. Cleanup by concession. |
| Unanswered prompt | Deliberately omit the real human getAbilityToPlay answer. Record an immutable failure in AtomicReference before throwing. The owning TestNG invocation must receive its exact error/artifact and assert the expected reason and untouched pre-cast/payment/damage position. A generic test failure cannot satisfy this control. |

The human case establishes **opposing-player targeting and 20 → 17 life only**.
It establishes neither creature targeting nor destruction. Neither case calls
the AI brain, ComputerUtil's play helpers or resolveStack directly from the test.

## Execution record

Existing JDK 17 / portable Maven 3.9.16; all Maven runs offline. The final
normal six-module reactor compiled successfully and ran the unanswered control
with **1 test, 0 failures/errors/skips**, native exit 0. No new dependency was
acquired. The final TestNG cases then ran sequentially in fresh JVMs with
separate home/temp/appdata/report paths and the same compiled class hashes.

| Final run | Actual result | Whole launcher elapsed seconds |
| --- | --- | --- |
| Human 1 / 2 / 3 | Each 1 test, 1 pass, zero failures/skips; opposing AI 20 → 17, R paid from clicked Mountain, stack/resolution/graveyard assertions pass | 38.201 / 37.191 / 38.336 |
| AI 1 / 2 / 3 | Each 1 test, 1 pass, zero failures/skips; human 3 → 0, original AI winner, one genuine R payment/cast/resolution | 36.703 / 34.772 / 32.242 |
| Empty-hand final | 1 pass; no cast, payment, damage or tapped Mountain | See run JSON |
| Unanswered required prompt | 1 pass by detecting the exact atomic failure on the TestNG owner; no credited cast/payment/target/damage | Final offline reactor log/result |
| Historical embedded baseline | Unchanged method: 1 pass | 35.075 |
| Historical SingleThreadTest | Unchanged file: 1 pass, 19 answers pumped on Game-0 | 35.148 |

These times include full FModel/card bootstrap; they are not AI benchmarks.
The separate final passive runs all pass both the selected TestNG test and the
observer's nonvacuous path/thread/latch checks:

| Witness | Recorded original execution |
| --- | --- |
| Human, 59 observations | Original PlayerControllerHuman.playChosenSpellAbility with PhaseHandler.mainLoopStep ancestry; InputSelectTargets, CostPayment/payManaCost, Mountain selection, stack insertion and resolveStack. Actual Java IDs: loop 121, EDT 122, payment worker Game-1 125. |
| AI, 36 observations | Original PhaseHandler → PlayerControllerAi → AiController. FutureTask object 4126, evaluator Java ID 123, caller 119, EDT 120. Three original candidates; NORMAL state 2 returns ability object 4130 / ability ID 213 / Bolt card 42, identical to ComputerUtil.handlePlayingSpellAbility and stack insertion, with the same card at resolution. |
| Empty hand, 23 observations | Two original mana candidates; FutureTask 4123 runs on evaluator 125, caller 121, EDT 122, completes NORMAL state 2 with null outcome. No Bolt evaluation/cast. |

All final witnessed futures complete normally, both observer and child exit 0,
no watchdog expires, and no target method is invoked by JDI. Each added case
asserts no surviving Game AI Eval thread; after all runs zero java/javaw
processes remained visible. No graph-operation overlap or inconsistent
checkpoint was reported in the final runs. This is bounded observational
evidence, not a general concurrency guarantee.

[Run index](evidence/embedded-bolt-20261018/runs.json),
[complete evidence manifest](evidence/embedded-bolt-20261018/evidence-manifest.json),
[final human witness](evidence/embedded-bolt-20261018/witness-human-final/witness.json),
[final AI witness](evidence/embedded-bolt-20261018/witness-ai-final/witness.json),
[empty witness](evidence/embedded-bolt-20261018/witness-empty/witness.json).

### Development findings, preserved rather than hidden

- Initial test compilation failed on CardPool's PaperCard removal signature
  and Iterable lacking size(). Both were test-code API mistakes, corrected in
  the approved file; no original API/POM workaround.
- The first runtime script treated a duplicate payment-button refresh as a
  second required payment action. It also assumed only one Bolt ability
  question. Actual original calls are Bolt selection on EDT, Bolt confirmation
  on Game-0 and Mountain mana-ability selection on Game-1. The strict script
  validates all three exact offered objects/cardinalities and logs duplicate
  button refreshes without a second click. Its failure reached the test owner.
- Development fresh profiles exposed the existing platform name-dialog default.
  Final cases explicitly set/restore the transient PLAYER_NAME fixture
  preference, avoiding reliance on that default. Final required game prompts
  have no fallback. Music-file warnings remain unchanged test-platform output.
- Preliminary successful JDI traces labeled inherited payManaCost entries by
  the requested human class, although their class/receiverClass fields correctly
  identified PlayerController/AI receivers. The observer now labels the actual
  declaring class; both witnesses were repeated successfully. Preliminary
  traces are retained and are not substituted for the final evidence.
- A TestNG CLI-help probe unexpectedly emitted 14 default zero-test report
  files under workspace-root test-output. The full source/cache audit detected
  them. Creation timestamps match that probe; its XML records total=0. They
  were copied to private evidence, hash-verified and removed individually with
  resolved workspace-bound paths, no recursive deletion and no unrelated file
  removal. The text manifest/cleanup record is published; static report assets
  are excluded. This did not run a game or change source/configuration.

After the final witness sequence, work was limited to integrity checks,
diagnostic-output cleanup and evidence/documentation preparation.

## Thread, graph and disclosure checks

Runtime trace lines record monotonic nanoseconds from script creation, actual
Java thread IDs/names, Swing EDT status, input class/identity and waiter/latch
state. Original awaitInput delegates unchanged to the original latch wait.
ObservedGui delegates graph/update/event calls unchanged and asserts that two
different threads do not enter overlapping graph-operation boundaries. This
guard is a test assertion, not a synchronization fix or new dispatch policy.
Same-thread original flush recursion remains unchanged.
The instrumentation adds timing/memory-order observations; it does not prove
all original mutators or internal retry paths are race-free. Obsolete button
events are logged by input identity, not used as snapshots of old game state.

Every controller lambda checks EDT dispatch. Target/payment/priority display
callbacks check EDT; synchronous replies return immediately on their actual
calling thread. Settled state/visibility checks require the original loop
thread parked in WAITING with its original latch still at one. Entry/return
timestamps around original graph operations retain actual caller/wait state,
including any transition before the wait is fully parked. Those observations
do not certify arbitrary concurrent graph access as safe.

Visibility checkpoints inspect private Java state only. The raw full GameView
and delta history must contain a known hidden AI card's identity while original
canBeShownTo/canFaceDownBeShownTo deny its face to the nonempty actual human
viewer set. Independent fixture expectations deny both libraries and AI hand
faces, allow own hand/public zones, and make Bolt public on stack/graveyard.
The test-only immutable allowed-face set explicitly rejects submission of a
known hidden face ID and accepts visible Bolt. It is not a DTO/projector;
arbitrary views, deltas, events, strings and prompts remain unsafe to export.
Reveal, alternate/face-down-state and broad prompt projection need later work.

The existing test-only HeadlessGuiDesktop remains a Swing-based bootstrap with
platform dialog/audio defaults. Added cases set an explicit transient player
name and restore that preference, highlights, replay preference and MyRandom;
they do not save preferences. Required game prompts have no generic fallback.
Unexpected questions record atomic failure. No original AI deadline is
lengthened or AI simulation mode substituted.

## Reproduction with approved tools/cache

Use the existing JDK 17, portable Maven 3.9.16 and `.local-tools/m2`. Work in a
disposable PowerShell process at repository root, with a fresh isolated path
per run. Replace `<JDK17>` with the existing JDK installation; do not install
anything or fall back online if dependencies are missing.

```powershell
$repo = (Get-Location).Path.Replace('\', '/')
$isolation = "$repo/.local-tools/embedded-bolt-reproduction/human-1"
foreach ($name in @('home','tmp','appdata','localappdata','reports')) {
  New-Item -ItemType Directory -Force -Path "$isolation/$name" | Out-Null
}
$env:JAVA_HOME = '<JDK17>'
$env:JAVA_TOOL_OPTIONS = "-Duser.home=$isolation/home -Djava.io.tmpdir=$isolation/tmp -Djava.awt.headless=true -Xmx1536m"
$env:APPDATA = "$isolation/appdata"
$env:LOCALAPPDATA = "$isolation/localappdata"
$env:TEMP = "$isolation/tmp"
$env:TMP = "$isolation/tmp"
& "$repo/.local-tools/apache-maven-3.9.16/bin/mvn.cmd" -o -B -ntp -T1 `
  "-Dmaven.repo.local=$repo/.local-tools/m2" -pl forge-gui-desktop -am `
  '-Dtest=forge.net.embedded.ProtocolGuiGameInProcessTest#humanBoltTargetsOpponentWithManualMountainPayment' `
  '-Dsurefire.failIfNoSpecifiedTests=false' '-DfailIfNoTests=false' test
# Inspect $LASTEXITCODE and the actual selected test report, not reactor success alone.
```

The recorded development/final-compilation runs used that unchanged offline
reactor. For fresh acceptance JVMs, the already-produced Surefire classpath
and parent POM opens were reused with the existing TestNG main. No lifecycle
plugin was changed or bypassed during the preceding normal build.

```powershell
[xml]$report = Get-Content "$repo/forge-gui-desktop/target/surefire-reports/TEST-forge.net.embedded.ProtocolGuiGameInProcessTest.xml"
$classpath = ($report.testsuite.properties.property |
  Where-Object { $_.name -eq 'surefire.test.class.path' }).value
[xml]$pom = Get-Content "$repo/pom.xml"
$opens = $pom.SelectSingleNode('//*[local-name()="argLine"]').InnerText.Trim() -split '\s+'
Set-Location "$repo/forge-gui-desktop"
& "$env:JAVA_HOME/bin/java.exe" @opens -cp $classpath org.testng.TestNG `
  -methods forge.net.embedded.ProtocolGuiGameInProcessTest.humanBoltTargetsOpponentWithManualMountainPayment `
  -d "$isolation/reports"
```

Repeat with three distinct fresh JVM/home/temp/report paths for each positive
method. Other exact method names are `aiBoltRunsThroughOriginalPhaseLoop`,
`aiEmptyHandDoesNotCastOrPay`, `unansweredRequiredPromptIsReportedToTestOwner`
and the unchanged `humanControllerRunsThroughInProcessRemote`. The unchanged
sibling is
`forge.net.embedded.ProtocolGuiGameSingleThreadTest.inputsCanBeAnsweredOnTheWaitingThread`.
Actual launcher watchdog: 210 seconds, terminating only its own test JVM;
existing TestNG methods retain their 180-second bound. Close the disposable
shell to discard its environment. Recorded Maven wrappers restored their
saved process environment explicitly.

For the separate passive observation, compile only the observer/unchanged Json
helper into generated output and launch the same selected TestNG method from
the desktop working directory with the same isolation:

```powershell
Set-Location $repo
$observerOut = "$repo/.local-tools/embedded-bolt-reproduction/observer-classes"
New-Item -ItemType Directory -Force -Path $observerOut | Out-Null
& "$env:JAVA_HOME/bin/javac.exe" --add-modules jdk.jdi -d $observerOut `
  experiments/ai-teavm/src/observer/java/probe/JdiWitness.java `
  experiments/ai-teavm/src/main/java/probe/Json.java
Set-Location "$repo/forge-gui-desktop"
& "$env:JAVA_HOME/bin/java.exe" --add-modules jdk.jdi -cp "$observerOut;$classpath" `
  probe.JdiWitness --testng "$isolation/witness" `
  forge.net.embedded.ProtocolGuiGameInProcessTest.aiBoltRunsThroughOriginalPhaseLoop
```

The observer has a 210-second external watchdog, verifies the actual TestNG
result and preserves failing/inconclusive traces. It uses dt_shmem only.
Debugger timing is not a performance measurement. Timeout/cancel/interrupt/stop
semantics remain outside this happy-path experiment.

## Boundaries

No production/runtime, card or synchronization adaptation; no reconnect,
null-reply fix, browser transport, listener, deployment, installation,
dependency acquisition, original POM change or merge. The known production
null-reply/reconnect problem in PR #17 remains separate and unresolved.

The integrity audit compared 58,903 original tracked files: only the two
approved source extensions and expected documentation differ. Original runtime,
cards, POMs, private Maven settings and profile/tool configuration remain
unchanged. All 2,393 Maven cache files are byte-identical to PR #18's completed
cache snapshot. After cleaning the CLI diagnostic output, no unexpected
non-documentation project output remains outside the allowed generated paths.
[Integrity and cleanup evidence](evidence/embedded-bolt-20261018/integrity-audit.json).

Publication retains complete build/test/witness output with absolute workspace
and JDK paths replaced by `<WORKSPACE>`/`<JDK17>`, UTF-8/LF normalization and
Surefire system properties omitted. Originals remain private locally. No
downloaded JAR/POM, compiled class, temporary profile/settings, helper script,
static generated HTML/image/JS report bundle or credential is committed.
Review is required before any additional gameplay scenario, synchronization
adaptation, production projection, transport or Adventure integration.
