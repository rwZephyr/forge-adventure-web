# Results on Silent

Date: 2026-10-17 (America/Vancouver). Source baseline:
`db893dcdb14fdd85a05437ad458dc70ef704ca2e`. Branch: `codex/spritegroup-teavm`.
PR #4 and PR #6 were reviewed before implementation; neither was merged here.

## Verified compilation and tests

After owner approval, portable Maven **3.9.16** and Gradle **9.6.1** were
checksum-verified and extracted under `.local-tools/`. Both run on the existing
Oracle JDK **17.0.12+8-LTS-286**. No system/user settings were changed.

Commands ran from the repository root. Scripts log expanded invocations and
fail on nonzero exit. Raw local logs are in `.local-tools/logs/`;
[evidence/build-results.json](evidence/build-results.json) records their hashes
and summaries for review.

| Command | Actual result |
| --- | --- |
| `./experiments/adventure-teavm/scripts/Invoke-ForgeBaseline.ps1 -Phase Compile` | Exit 0, seven-module `BUILD SUCCESS`, 1:42. Parent/core/game/AI/GUI/mobile/mobile-dev succeeded, zero Checkstyle violations. |
| `./experiments/adventure-teavm/scripts/Invoke-ForgeBaseline.ps1 -Phase Test` | Exit 0, seven-module `BUILD SUCCESS`, 59.370 s. Game: 3 tests passed; GUI: 13 passed. Zero failures/errors/skips. Other modules had no tests. Disposable test paths and one capped fork were used. |
| `./experiments/adventure-teavm/scripts/Build-Browser.ps1 -WriteLocks` | Exit 0, `BUILD SUCCESSFUL in 42s`. Original source/harness compiled with release 17; TeaVM generated JavaScript/static files; lockfile generated. |
| `./experiments/adventure-teavm/scripts/Build-Browser.ps1 -Offline` | Exit 0, `BUILD SUCCESSFUL in 1m 5s`, including another TeaVM generation from cached dependencies. |
| `./experiments/adventure-teavm/scripts/Invoke-ForgeBaseline.ps1 -Phase Compile -Offline` | Exit 0, seven-module `BUILD SUCCESS`, 1:11, from the isolated local cache. |
| `./experiments/adventure-teavm/scripts/Verify-Fixture.ps1` | Passed: exact original treasure bytes; only SpriteGroup and comparator under the compiled Forge package; source-map class matches original source after newline normalization. |

Baseline compile expands to portable `mvn.cmd -B -ntp -T1
-Dmaven.repo.local=<repo>/.local-tools/m2 -pl forge-gui-mobile-dev -am
-DskipTests compile`. Test mode runs `test` with one fork and temporary home/temp
directories; offline mode adds `-o`. No `install`, `package`, `deploy`, game
launcher or asset updater ran. Tests' Windows APPDATA/LOCALAPPDATA also point
inside `.local-tools/test-home` for the child process.

Browser compilation expands to portable `gradle.bat -p
<repo>/experiments/adventure-teavm --no-daemon --max-workers=1 --console=plain
browserArtifact`, with `--write-locks`/`--offline` as shown. Its single-use JVM
has a 1536 MiB heap and exits afterward; TeaVM runs in-process. The standard
Maven POMs/reactor and all Forge engine source are unchanged.

## Artifact and authenticity

Entry point: `build/dist/js/webapp/index.html`. In both recorded builds,
`app.js` is **1,526,119 bytes** uncompressed and the source map is **193,294
bytes**. These are debug/instrumented sizes, not production budgets. The
artifact includes original treasure content, bundled libGDX classpath assets,
backend runtime scripts, debugging source references and the Forge license.
The evidence manifest records file totals and hashes. A ZIP is retained under
ignored `build/` for local review; no artifact or tool binaries are committed.

Source-set include filters select the **original** SpriteGroup file directly.
The only other application code is the two probe/launcher classes and their
nested classes. The harness never sorts actors; draw callback order is
recorded after the actual `SpriteGroup.drawChildren` execution. It uses
Scene2D Images and test input, not Adventure movement, collisions, rewards,
rules, AI, world generation, saves, or a substitute game.

Treasure content totals **19,110 bytes**. Original/generated SHA-256 values:

| File | SHA-256 |
| --- | --- |
| `treasure.atlas` | `8a3e940df9aefb853c9685ef4d21f31ca1f3dea35a429d5a64462e5c21247b9b` |
| `treasure.png` | `79c3d4d6f833c6c009e06bc4f9b9bccab61be2489e7985ab33a372b8d1022b4d` |

## Browser behavior and remaining gates

**Pending separate loopback-server approval.** Rendering, mouse/touch input,
resize, load time and frame timing have not yet been observed in a browser.
The owner was asked to approve a temporary `127.0.0.1:8765` listener serving
only the artifact, to be stopped after testing. No server was started while
that request was pending; tool-installation approval does not authorize it.

**Actual iPhone Safari remains unverified.** Silent's loopback listener cannot
serve a phone on the LAN. No LAN/public serving, tunnel, hosting or firewall
change has been performed. Desktop device emulation would be recorded as such.

Gate 0 compilation/tests is verified. Gate 1 compilation and genuine source/
asset selection are verified, with visual/input/device criteria still open.
The rules/AI, actual scene, full duel and save gates have not been attempted.
This does not establish a playable Forge Adventure port.

## Warnings and limitations

- No compilation or test failure occurred. The first browser build warned that
  TeaVM's annotation processor declares `RELEASE_11`, less than source `17`;
  compilation still succeeded.
- Gradle reported deprecated features incompatible with Gradle 10. Version
  9.6.1 is pinned; no unrelated library/plugin upgrade was attempted.
- Forge emitted existing deprecated/unchecked Java warnings. Its mobile-dev
  timestamp helper warned about locale-dependent output (`en_CA`). Repeated
  compilation is verified; bit-for-bit release reproducibility is not claimed.
- The 16 baseline tests are a small existing suite, not complete mechanics/AI
  coverage. No desktop game/campaign/save session ran.
- No deployment or public packaging occurred. Original asset provenance and
  dependency notices still require review before public distribution.

See [README.md](README.md) for reproduction and the browser acceptance checks.
