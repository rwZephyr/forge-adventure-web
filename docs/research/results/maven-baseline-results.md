# Unchanged embedded desktop baseline — actual results

2026-10-18, Windows host. Owner approved Maven Central cache acquisition through the
normal desktop `test-compile` lifecycle, then the original embedded test offline.
**Both Maven phases succeeded; the existing test passes unchanged. Stopped for
review before any Bolt extension.**

Execution branch: `codex/maven-baseline-inventory`. Starting local commit:
`32b6a5d4` (acquisition proposal). Original Forge source pin:
`db893dcdb14fdd85a05437ad458dc70ef704ca2e`. PR #17's reviewed plan is unchanged.
Publication branch: `codex/maven-baseline-results`, based directly on PR #17's
unchanged head. Earlier local commits with absolute machine paths are excluded
from this branch's new history.

## Commands and results

Existing Oracle JDK **17.0.12**, portable Maven **3.9.16**, repository-local
`.local-tools/m2`. Maven's version output in both complete logs verifies those
actual runtime paths. No software installation, POM/plugin/source modification,
profile workaround, server or service configuration change.

The normal checkout `.mvn/maven.config` selects its existing
`.mvn/local-settings.xml`. It has server definitions but no repository URLs;
contents were neither changed nor copied into evidence. Existing Maven HTTP
blocking remains in place.

PowerShell from repository root (`$repo` denotes its absolute path):

In a disposable PowerShell process, reproduce the approved isolation first:
Replace `<JDK17>` with the existing JDK 17 installation directory; `<WORKSPACE>`
in published output means the repository's absolute root. These placeholders
remove machine-specific absolute paths, not dependency or tool versions.

```powershell
$repo = (Get-Location).Path.Replace('\', '/')
$isolation = "$repo/.local-tools/embedded-baseline-20261018"
$env:JAVA_HOME = '<JDK17>'
$env:JAVA_TOOL_OPTIONS = "-Duser.home=$isolation/home -Djava.io.tmpdir=$isolation/tmp -Djava.awt.headless=true -Xmx1536m"
$env:APPDATA = "$isolation/appdata"
$env:LOCALAPPDATA = "$isolation/localappdata"
$env:TEMP = "$isolation/tmp"
$env:TMP = "$isolation/tmp"
```

The isolation directories already exist. Run the following commands in that
process, check `$LASTEXITCODE` after each, then exit the disposable shell to
discard its environment settings. The actual run used a wrapper that captured
both commands separately and restored the saved process environment.

```powershell
& "$repo/.local-tools/apache-maven-3.9.16/bin/mvn.cmd" -B -ntp -T1 `
  "-Dmaven.repo.local=$repo/.local-tools/m2" -pl forge-gui-desktop -am `
  -C test-compile

& "$repo/.local-tools/apache-maven-3.9.16/bin/mvn.cmd" -o -B -ntp -T1 `
  "-Dmaven.repo.local=$repo/.local-tools/m2" -pl forge-gui-desktop -am `
  '-Dtest=forge.net.embedded.ProtocolGuiGameInProcessTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' '-DfailIfNoTests=false' test
```

Process-only JAVA_HOME=`<JDK17>`. JAVA_TOOL_OPTIONS sets
user.home and java.io.tmpdir to `.local-tools/embedded-baseline-20261018/home`
and `tmp`, `-Djava.awt.headless=true -Xmx1536m`. APPDATA/LOCALAPPDATA use sibling
`appdata`/`localappdata`; TEMP/TMP use `tmp`. The wrapper also ran `mvn --version`
before each phase. The offline wrapper verified exact process-environment
restoration. Full command arguments and UTC timing are retained in result JSON.

| Phase | Actual outcome |
| --- | --- |
| Online test-compile | Six-module BUILD SUCCESS, native exit 0; Maven about 68 seconds, wrapper 73.543 seconds including version check. Original GMaven `get-changelog-from-tag` executed successfully on JDK 17. Desktop compiled 409 main and 142 test sources. |
| Offline unchanged test | Six-module BUILD SUCCESS, native/wrapper exit 0; Maven about 86 seconds, wrapper 89.106 seconds. Surefire: **1 test, 0 failures, 0 errors, 0 skipped**. Suite 32.418 seconds; case 23.85 seconds. |
| Integrity audit | All 58,903 tracked file hashes unchanged; all 58,905 inspected files outside generated-output locations unchanged. Existing tool/profile/settings configuration unchanged. Offline run made no cache changes. |

The online wrapper initially returned 1 **after Maven succeeded**, because its
restoration check detected empty strings where variables had originally been
absent. A process-only probe reproduced PowerShell's null-to-empty binding in
`SetEnvironmentVariable`. The isolated child shell had exited; no system
environment was changed. The audit wrapper was corrected to remove originally
absent process variables explicitly. Its original false flag is preserved,
alongside the successful offline restoration check. This was an audit-helper
correction, not a Forge/Maven workaround or a second online build.

The initial snapshot helper was also corrected/optimized before acquisition;
only this task's identified helper processes were stopped. No other agent or
service was terminated.

## What the original test demonstrated

The actual method was
`forge.net.embedded.ProtocolGuiGameInProcessTest.humanControllerRunsThroughInProcessRemote`.
Its existing 40-Drifting-Meadow human versus 40-Plains AI fixture initialized
original FModel, HostedMatch, ProtocolGuiGame/IRemote and stock controllers,
played a land through IGameController, progressed into turn 3 and conceded.
The unchanged test's assertions all passed.

Recorded output:

- One human battlefield card; 19 button prompts.
- 3 full-state sends; 30 state-bearing and 30 event-bearing deltas.
- 193 new / 79 changed objects, 153 raw GameEvents, zero serialized wrappers.
- One synchronous `getAbilityToPlay -> Play land` reply, observed on
  **AWT-EventQueue-0**. This supports the plan's calling-thread reply distinction.
- No newly loaded classes in the test's selected transport-class list.
  RemoteClientGuiGame was already loaded before its comparison.

These are observed baseline counts, not fixed expectations for later Bolt
tests or a comprehensive no-network proof. This run does **not** establish
Bolt targeting/payment/resolution, AI FutureTask/timed-get identity witnesses,
visibility filtering, reconnect, a browser interface or Adventure bootstrap.
No sibling test, extra repetitions or extension was run after this success.

## Cache acquisition and boundaries

[Artifact/license inventory](../../experiments/evidence/embedded-baseline-approved-20261018/acquired-artifacts.json)
records the actual coordinates, paths, sizes, SHA-256s and cached-POM license
declarations. [Complete cache delta](../../experiments/evidence/embedded-baseline-approved-20261018/after-offline-changes.json).

- **237 new cache files:** 37 JARs, 54 POMs and 146 checksum/resolver support
  files. New JAR/POM bytes total **20,991,202** (about 20.0 MiB).
- All new `_remote.repositories` provenance entries identify **central**.
  No reactor repository declarations or settings URL override were added.
  No non-Central repository requirement was encountered.
- One existing resolver metadata file changed:
  `com/google/code/findbugs/jsr305/2.0.1/_remote.repositories`, recording the
  newly acquired JAR. **No existing JAR/POM was replaced or removed.**
- The offline test left the complete cache snapshot identical to the
  post-acquisition snapshot: no additional acquisition or online retry.
- Cached-POM license declarations resolve for 88 of 91 acquired JAR/POM rows.
  JUnit 3.8.2's JAR/POM and forge-parent 9's POM lack a resolved declaration in
  this audit; no distribution clearance is claimed. This does not alter the
  successful unchanged test result.

The actual acquisition includes the plugin/adapters, Groovy 2.4.9 and all nine
direct desktop/test cache misses from the proposal, plus the resolved needed
transitives and parents. Existing Gradle Groovy and other cached versions were
not substituted. Packaging, install, deploy and broad go-offline goals were
not executed.

Original source/configuration and the reviewed extension plan remained
byte-identical throughout the Maven phases. Observed writes were confined to
generated project build output and the approved isolated cache/home/temp
locations. No unexpected changes outside those locations were found in the
project file audit. Full before/after snapshots stay under the task's private
`.local-tools/embedded-baseline-20261018/approved-run` directory.

After the test/wrapper exited, zero Java/javaw processes remained visible on
the test host. No listener/server was started by this task, no port/deployment action
was performed, and no unrelated service or save was modified.

## Evidence and next step

[Evidence manifest](../../experiments/evidence/embedded-baseline-approved-20261018/evidence-manifest.json)
links complete Maven logs, result JSON, full cache delta, artifact/license
inventory, input hash/verification summary, TestNG result and Surefire result.
Public text copies use UTF-8/LF while retaining every log line and its order;
absolute workspace/JDK paths are replaced by the documented placeholders. The public
Surefire XML omits system properties; its original complete XML remains in
`forge-gui-desktop/target/surefire-reports` with its hash recorded. No private
settings contents are published.

## Publication review

The owner accepts the pass as an **author-reported result pending evidence
review**. This publication adds no independent execution or test rerun.
Existing local logs/results were reviewed for credential/token patterns,
private-key material, URL credentials or sensitive query parameters, personal
paths and machine identifiers. No credentials or tokens were found. URLs are
project/source, Maven documentation/metadata and dependency-license references.
Dependency version strings that resemble IPv4 addresses are coordinates, not
network addresses. Machine-specific absolute paths and the host nickname were
removed from new evidence/report files. All Maven output lines, warnings,
diagnostics, result counts and timing remain available with those substitutions.
The manifest records substitution counts and hashes of the public copies.

Downloaded JARs/POMs, target/build output, temporary profiles/settings, private
snapshots and audit helpers are excluded. The cache inventory is text metadata
(coordinates, sizes, hashes and license declarations), not downloaded artifacts.
The public Surefire copy retains testcase/output evidence while omitting system
properties. Complete original files remain locally; no private profile/settings
contents are published. No additional download, environment change, source
adaptation, Bolt implementation, server or merge accompanies publication.

Nonfatal output includes no `forge-` tags (changelog fallback), ordinary Java
deprecation/unchecked warnings, and card-data/set warnings during the original
FModel bootstrap. None was suppressed or addressed with source changes.

**The Maven baseline blocker is resolved for this pinned checkout/toolchain.**
Stop here for owner/Claude review of PR #17's existing extension plan. Any Bolt
test implementation, additional architecture work or service/network setup
requires its own approval; PR #17 stays draft and unmerged.
