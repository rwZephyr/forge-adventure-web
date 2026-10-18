# Original Adventure SpriteGroup browser experiment

This is an isolated libGDX/TeaVM component probe, not a playable Adventure port.
It compiles `../../forge-gui-mobile/src/forge/adventure/stage/SpriteGroup.java`
directly, unchanged, alongside two harness classes. `GameStage` in Adventure uses
this original class to sort sprites by Y. The harness uses two Scene2D `Image`
actors and original treasure atlas regions; it does not load `Forge`, `FModel`,
rules, AI, a campaign, collisions, saves, accounts, or a substitute game.

The original treasure atlas and PNG are selected from
`../../forge-gui/res/adventure/common/sprites/` at build time. They are not copied
into version control or edited. Mint/amber actor tint distinguishes A and B.

## Portable tool setup on Silent

Tool/dependency downloads require owner approval. Approval was given in this
task for repository-local Maven, Gradle and the required build dependencies.
The installation script uses official ZIPs with pinned checksums; it changes no
machine/user PATH, registry, Java installation, credentials, or services.

From the repository root in PowerShell:

```powershell
./experiments/adventure-teavm/scripts/Install-PortableTools.ps1 -Download
./experiments/adventure-teavm/scripts/Invoke-ForgeBaseline.ps1 -Phase Compile
./experiments/adventure-teavm/scripts/Invoke-ForgeBaseline.ps1 -Phase Test
./experiments/adventure-teavm/scripts/Build-Browser.ps1
./experiments/adventure-teavm/scripts/Verify-Fixture.ps1
```

All scripts select the existing `C:/Program Files/Java/jdk-17` by process-local
`JAVA_HOME`; build scripts accept `-JdkHome` for another existing JDK 17.
Portable tools/caches and raw logs are under ignored `.local-tools/`.
Maven uses `.local-tools/m2`, Gradle uses `.local-tools/gradle-cache`.
Neither modifies the pre-existing user Maven/Gradle cache. Tests use disposable
profile/temp directories under `.local-tools/test-home`, one fork, and a 1536 MiB
maximum JVM heap. Builds use one worker; the Gradle single-use daemon exits at
the end, and TeaVM runs in that JVM. The root Maven reactor/POMs are untouched.

Pinned versions: JDK 17.0.12 on Silent, Maven 3.9.16, Gradle 9.6.1,
gdx-TeaVM plugin/backend 1.6.1, libGDX 1.14.2, TeaVM 0.15.0.
The generated `gradle.lockfile` pins the resolved experiment dependencies.
Plugin versions are explicitly pinned in `build.gradle`. After filling caches:

```powershell
./experiments/adventure-teavm/scripts/Invoke-ForgeBaseline.ps1 -Phase Compile -Offline
./experiments/adventure-teavm/scripts/Build-Browser.ps1 -Offline
```

Use `Build-Browser.ps1 -WriteLocks` only when intentionally regenerating the
lockfile. Default builds use existing locks. First-time online resolution may
download several hundred MB for tools and both build graphs. No game asset
updater, packaging/install/deploy lifecycle or game launcher is invoked.

## Artifact and browser checks

The generated static directory is `build/dist/js/webapp/`; open `index.html`
through HTTP after server approval. File URLs do not provide the same asset
loading environment and are not the supported reproduction path. The artifact
contains the original treasure files, backend runtime resources, JavaScript,
source map and source references for debugging. It is ignored by Git and can be
rebuilt; no binaries or caches belong in the PR.

**Server startup needs separate approval.** The build never starts the plugin's
Jetty/dev server; its run/dev-server tasks are disabled. After explicit approval:

```powershell
./experiments/adventure-teavm/scripts/Serve-Loopback.ps1 -Approved
# Open http://127.0.0.1:8765 in a local browser; Ctrl+C stops the server.
```

This serves only the generated artifact and binds to `127.0.0.1`, not all
interfaces. It cannot provide an iPhone on the LAN access. Any LAN-facing or
remote serving arrangement requires its own approval; no such setup is included.

1. Confirm two visible chests, no error text and a ready status.
2. A starts at `(112, 200)`, B at `(136, 220)`, both 96×96 in a 320×480 logical
   viewport. Initial draw/child order is `BA`: lower-Y A draws last.
3. Drag a chest vertically across the other's Y coordinate. The order must flip
   because the original `SpriteGroup.drawChildren` sorted it; the harness never
   sorts or calls `UpdateActorZ`. Drag back and verify the order flips again.
4. Check pointer event counts/coordinates and actor position updates. Repeat
   with mouse and touch where available; distinguish simulated touch from a
   real mobile device test.
5. Resize landscape/portrait. The FitViewport letterboxes the scene; dragging
   must still map correctly. Test a high-DPI configuration separately.
6. Capture browser/OS/device, load errors, network requests, first-ready time,
   and frame interval statistics. No canvas or readiness flag alone proves
   successful rendering or input.

`window.__spriteGroupProbe` exposes readiness, region/texture counts, actual
draw callback order, sorted child order, actors, last pointer, frame count,
order-change count, error events and up to 3600 measured frame intervals. Timing
includes this diagnostic instrumentation and browser/tool scheduling; it is not
a game benchmark. GL errors throw with `useDebugGL`; uncaught errors are shown
on the page. Console `probe` messages log readiness and each order change.
The same measurements, browser/version and resource timings are serialized into
the `#diagnostics` JSON element once per second for read-only inspection. After
a resize, allow a render frame and the next diagnostic sample to settle before
recording geometry. The custom preload launcher must load the backend manifest
and signal completion; a plain clear-screen listener leaves the app loading.

An actual portrait iPhone Safari pass remains a distinct required Gate 1 check.
Desktop Chromium or device emulation cannot establish Safari compatibility.
See [RESULTS.md](RESULTS.md) for the actual runs and limitations.

## Attribution and dependency scope

Forge is GPL-3.0 (with upstream file notices preserved); see the repository
`LICENSE`. The build copies it to `FORGE-LICENSE.txt` with the static artifact.
Keep the original source, notices and corresponding source when distributing.
Treasure assets retain their original provenance; this experiment makes no
claim of blanket asset-rights clearance for public redistribution.

libGDX 1.14.2, gdx-TeaVM 1.6.1 and TeaVM 0.15.0 identify Apache-2.0 licenses.
gdx-TeaVM bundles additional classpath assets and runtime scripts (including
Howler and libGDX's JS/Wasm helpers) even for this JS probe. These are backend
dependencies, not additional Forge game content. Refer to the pinned projects'
LICENSE/NOTICE files and the lockfile; public packaging needs a complete
third-party notice review, beyond this local proof of concept.

Sources: [runtime investigation, PR #4](https://github.com/rwZephyr/forge-adventure-web/pull/4),
[research synthesis, PR #6](https://github.com/rwZephyr/forge-adventure-web/pull/6),
[gdx-TeaVM usage at 1.6.1](https://github.com/xpenatan/gdx-teavm/blob/1.6.1/docs/usage.md),
[its Gradle wrapper](https://github.com/xpenatan/gdx-teavm/blob/1.6.1/gradle/wrapper/gradle-wrapper.properties),
[Maven downloads/checksums](https://maven.apache.org/download.cgi).
