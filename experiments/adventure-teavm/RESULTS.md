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
| `./experiments/adventure-teavm/scripts/Build-Browser.ps1 -Offline` after the preload correction | Exit 0, `BUILD SUCCESSFUL in 36s`. Corrected browser launcher compiled and transpiled; fixture checks passed again. |

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

Entry point: `build/dist/js/webapp/index.html`. The initial two builds had
`app.js` at 1,526,119 bytes and a 193,294-byte source map. After the tested preload
correction, they are **1,528,796 bytes** and **193,620 bytes**, respectively.
These are debug/instrumented sizes, not production budgets. The
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

**Desktop rendering/input/resize verified after explicit local-test approval.**
The temporary server used `Serve-Loopback.ps1 -Approved` and served only the
generated directory. `netstat` confirmed `127.0.0.1:8765` as its listener. It was
stopped after testing, and a final check found **zero** remaining test listeners.
No other service, firewall rule or network exposure was changed.

The first browser run failed: the custom preload listener only cleared the
screen, never called the backend asset loader and never signaled preload
completion. The page remained on its loading message with no sprites and no
treasure requests. This was a launcher/harness bug, not a Forge/TeaVM compiler
failure. The correction calls `AssetInstance.getLoaderInstance().preload(...)`,
waits for downloads and required files, then calls `WebApplication.setPreloadReady()`.
The original Forge class and assets were unchanged. An empty favicon was also
declared to remove an incidental missing-favicon request.

Tests used Codex's in-app **Chromium 155 desktop browser on Windows**, automated
desktop pointer drags, explicit viewport overrides and the default high-DPI
viewport. This did **not** enable touch or emulate an iPhone. Actual draw callback
order and child order matched on every recorded observation:

| Check | Observed result |
| --- | --- |
| Portrait 390×844, DPR ≈1 | Both original treasure sprites visible; 8 atlas regions, 1 texture; initial draw/child order `BA`, A.y=200, B.y=220. |
| Drag `(180,430) → (180,330)` | A.y=282.1, order `AB`, 10 pointer events. Visual overlap changed correspondingly. |
| Drag `(180,320) → (180,430)` | A.y=191.8, order restored to `BA`, 20 pointer events. |
| Resize to 844×390 landscape | Both sprites remained visible with letterboxing; coordinates/order preserved. |
| Landscape drag `(420,200) → (420,130)` | A.y=277.9, order `AB`, 30 pointer events; input mapping remained correct after resize. |
| Restore default 319×572, DPR 1.5, then drag `(160,200) → (160,270)` | Canvas 478×858; A.y=207.7, order `BA`, 40 pointer events. Four order reversals total; no active pointer left after release. |
| Reload | A/B reset to original positions, `BA`, zero pointer events. Ready again in about 1.43 seconds with HTTP 304/cache validation. |

On the first corrected localhost reload, readiness was **1469.2 ms**. Across
the first **3600** sampled render intervals (idle, tool activity, resize and four
drags), median was **18 ms**, p95 **18.5 ms**, maximum **33.4 ms**. This is an
instrumented desktop observation, not a sustained drag benchmark, mobile budget
or cold-profile startup guarantee. No page diagnostic errors or console
warnings/errors were recorded in the corrected run; the debug GL wrapper was
enabled. Backend assets returned HTTP 200, followed by 304 on reload.

Evidence: [diagnostics and checks](evidence/browser-results.json),
[server requests](evidence/server-requests.txt),
[initial failed preload](evidence/initial-preload-failure.png),
[portrait before drag](evidence/portrait-initial.png),
[portrait after drag](evidence/portrait-after-drag.png),
[landscape after drag](evidence/landscape-after-drag.png), and
[high-DPI after drag](evidence/high-dpi-after-drag.png).

**Actual iPhone Safari remains unverified.** Silent's loopback listener cannot
serve a phone on the LAN. No LAN/public serving, tunnel, hosting or firewall
change has been performed. Desktop device emulation would be recorded as such.

Gate 0 compilation/tests is verified. Gate 1 compilation, genuine source/asset
selection and desktop visual/pointer/resize checks are verified. Real touch and
actual portrait iPhone Safari remain open.
The rules/AI, actual scene, full duel and save gates have not been attempted.
This does not establish a playable Forge Adventure port.

## Warnings and limitations

- No compilation or test failure occurred. The first browser build warned that
  TeaVM's annotation processor declares `RELEASE_11`, less than source `17`;
  compilation still succeeded.
- The initial browser preload failure and its bounded launcher correction are
  recorded above. A blank loading canvas was not counted as a successful run.
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
