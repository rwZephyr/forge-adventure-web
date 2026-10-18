# Forge Adventure runtime and native browser feasibility

Research date: **2026-10-17 (America/Vancouver)**. Branch: `research/codex-runtime`.
Source baseline: fork commit **`db893dcdb14fdd85a05437ad458dc70ef704ca2e`**; this is the inspected checkout, not a claim about the latest upstream commit. Forge declares `2.0.16-SNAPSHOT`.

## Executive feasibility assessment

**Conditional go for a small TeaVM experiment; no evidence yet for a working Forge browser port.** Adventure already renders through libGDX, but its ordinary launcher brings in Forge's model, rules, AI, file-based resources, and background execution. Substituting a web backend for LWJGL is insufficient.

- **Verified from source:** Adventure lives in `forge-gui-mobile`, uses genuine Forge rules and AI, and includes Realm of Legends content. The root build targets Java 17 and Maven 3.8.1 or newer. libGDX is pinned to 1.14.2 in the mobile and desktop-development targets.
- **Verified by execution:** installed JDK 17 compiled two unchanged Adventure classes against the **already cached libGDX 1.13.5** jar. This establishes a very small Java compilation boundary, not compatibility with the declared dependency set, TeaVM, or a browser.
- **Blocked locally:** `mvn` is unavailable on PATH, and no Maven wrapper is tracked. The attempted offline reactor compile stopped at shell command lookup. No full Forge build or tests ran.
- **Hypothesized:** a narrow renderer/input slice can run through gdx-TeaVM with little adaptation. Full gameplay will need explicit solutions for reflection, cooperative execution, resources, native extensions, and serialization.
- **Unknown:** compiler reachability failures in the complete graph; browser bundle size, memory, AI latency, and actual iPhone Safari behavior. No browser artifact was generated or opened.

This assignment is **research only**: `docs/research/TASK-CODEX.md` and its README prohibit implementation and changes to shared status. Accordingly, only this report changes; the browser experiment below is specified, not implemented. No tools were installed, dependencies downloaded for a build, game launched, save files accessed, services changed, ports opened, or deployments performed.

## Exact entry points, modules, classes, and assets

All Forge source links below are pinned to the inspected commit. Line anchors locate the relevant declarations; accompanying symbols explain what was inspected.

### Build and dependency boundaries

[Root POM](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/pom.xml#L21) sets release/source/target 17, version properties, and the reactor. Its enforcer requires Maven `[3.8.1,)` and Java `17`; CI's [test workflow](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/.github/workflows/test-build.yaml#L12) specifies JDK 17/21 and `mvn -U -B clean test` under Xvfb. These are configuration facts, **not successful runs observed here**. iOS is outside the default reactor and added by `-Pios`; Android, desktop, installer, editor, and LDA are in the default reactor.

The POM-declared closure for the relevant launcher is:

```text
forge-gui-mobile-dev -> forge-gui-mobile
forge-gui-mobile -> forge-core + forge-game + forge-ai + forge-gui
forge-gui -> forge-core + forge-game + forge-ai
forge-ai -> forge-core + forge-game
forge-game -> forge-core
```

This is a **declared module graph**, not Maven's resolved `dependency:tree` output. Relevant third-party declarations:

| Module / source | Dependencies relevant to browser feasibility |
| --- | --- |
| [forge-gui-mobile/pom.xml](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/pom.xml#L32) | gdx, gdx-freetype, gdx-box2d **1.14.2**; gdx-ai **1.8.2**; controllers-core **2.2.4**; TextraTypist **0.8.2**; TenPatch **5.2.3** through JitPack configuration. Source directory is `src`, not `src/main/java`. |
| [forge-gui-mobile-dev/pom.xml](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile-dev/pom.xml#L196) | LWJGL3, desktop gdx/FreeType/Box2D natives **1.14.2**; desktop controllers **2.2.4**; OSHI **6.9.0**; commons-cli **1.9.0**. Desktop dependencies should stay outside a browser launcher. |
| [forge-core/pom.xml](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-core/pom.xml#L15) | Guava **33.3.1-android**, commons-lang3 **3.18.0**, commons-text **1.12.0**, rssreader **3.8.2**, tinylog **2.7.0**. The Android Guava variant does not establish TeaVM compatibility. |
| [forge-game/pom.xml](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/pom.xml#L15), [forge-ai/pom.xml](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-ai/pom.xml#L15) | Rules/session implementation in `forge-game`, including Sentry **8.21.1** and JGraphT **1.5.2**; AI in `forge-ai`, with commons-math3 **3.6.1**. |
| [forge-gui/pom.xml](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/pom.xml#L12) | Shared model/match/controller code, not just widgets: XStream **1.4.21**, Gson **2.13.2**, Netty **4.1.115.Final**, jUPnP **3.0.5**, lz4-java **1.10.2**, Jetty **9.4.57.v20241219**, XMLPull **1.1.3.4a**. Declared dependencies need not all become reachable in a single-player browser build. |

No tracked `*.gwt.xml`, TeaVM integration, Maven wrapper, or separate browser target was found. `forge-gui-desktop` is the Swing target; it is not the Adventure launcher to transplant.

### Launch and scene flow

1. Desktop Adventure entry: [`forge-gui-mobile-dev/src/forge/app/Main.java:23`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile-dev/src/forge/app/Main.java#L23) calls `GameLauncher`. [`GameLauncher.java:78`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile-dev/src/forge/app/GameLauncher.java#L78) obtains `Forge.getApp(...)`; line 141 creates `Lwjgl3Application`. Asset discovery uses local `./res` or `../forge-gui/` paths.
2. Native platform equivalents call the same listener factory: [`forge-gui-android/src/forge/app/Main.java:390`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-android/src/forge/app/Main.java#L390) and [`forge-gui-ios/src/forge/ios/Main.java:159`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-ios/src/forge/ios/Main.java#L159). Native iOS source is not evidence of Safari support.
3. [`forge-gui-mobile/src/forge/Forge.java:146`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/Forge.java#L146) configures `GuiMobile`, clipboard, device adapter, and asset directory. `create()` at line 187 initializes graphics, input, preferences, and skin. Lines 272/284 run `FModel.initialize` through the background asset-update path, followed by fonts and translations. Starting at the existing listener does not avoid this work.
4. `Forge.openAdventure()` at [line 410](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/Forge.java#L410) enables continuous rendering, selects Adventure resources, and calls `Config.loadResources()` before `StartScene`. `switchScene()` at line 1113 manages transitions. `Forge.render()` dispatches to `Adventure.render(delta)` at line 960; [`Adventure.java:68`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/Adventure.java#L68) renders and acts the current scene.
5. [`Scene.java:17`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/scene/Scene.java#L17) is the lifecycle/controller base, tied to `Forge` and `Config`. `UIScene` loads UI actors; `HudScene` combines `GameStage` with `GameHUD`; `ForgeScene` bridges the regular Forge screens. [`StartScene.java:75`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/scene/StartScene.java#L75) enters new/load/resume flows. [`GameScene.java:22`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/scene/GameScene.java#L22) uses `WorldStage`; [`TileMapScene.java:22`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/scene/TileMapScene.java#L22) uses `MapStage` and **`PointOfInterestMapRenderer`** for towns/dungeons.

The combat boundary is concrete: [`DuelScene.java:370`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/scene/DuelScene.java#L370) selects Adventure/Commander/event rules, creates `RegisteredPlayer`s, and calls `GamePlayerUtil.createAiPlayer` at line 492. Lines 539–565 obtain `MatchController.hostMatch()`, configure `GameRules`, and call `HostedMatch.startMatch`. [`HostedMatch.java:280`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/HostedMatch.java#L280) invokes the game's action and starts the actual match. [`LobbyPlayerAi.java:37`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-ai/src/main/java/forge/ai/LobbyPlayerAi.java#L37) constructs `PlayerControllerAi`. [WorldStage encounter/reward flow](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/stage/WorldStage.java#L141) returns results to Adventure and opens `RewardScene`. Replacing this boundary with an imitation battle is outside scope.

### Resource and save bootstrapping

- [`Config.java:64`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/util/Config.java#L64) discovers campaign directories and settings. `getFile()` at line 247 selects localized/plane/common resources with filesystem existence tests; `getAtlas()` at line 356 uses Forge's assets; `loadResources()` at line 617 loads custom card definitions into the real card database.
- Actual content is under `forge-gui/res/adventure/common/` (config, skin, UI JSON, sprite atlases/PNGs, TMX/TSX/templates, world data, particles). Campaign directories override common files. [`Realm of Legends/world/world.json`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/res/adventure/Realm%20of%20Legends/world/world.json) exists and declares a 700×700 world with 16-pixel tiles. Presence and directory discovery are verified; starting or completing that campaign is not.
- [`ForgeConstants.java:93`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/localinstance/properties/ForgeConstants.java#L93) points at `forge-gui/res/cardsfolder`, `tokenscripts`, and `editions`. [`FModel.java:163`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/model/FModel.java#L163) initializes model resources; [`CardStorageReader.java:80`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-core/src/main/java/forge/CardStorageReader.java#L80) reads filesystem directories or `cardsfolder.zip`, with lazy loading and pooled loading paths. Browser asset packaging must cover these reads, not just textures.
- [`WorldSave.java:73`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/world/WorldSave.java#L73) reads compressed Java object streams; `save()` at line 174 writes `WorldSaveHeader` and `SaveFileData` using `DeflaterOutputStream`/`ObjectOutputStream`. Files include `auto_save.sav`, `quick_save.sav`, and numbered slots under a plane-specific user Adventure directory. [`SaveFileData.java:15`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/util/SaveFileData.java#L15) maps keys to byte arrays containing further object streams, and its deserializer at line 338 uses `Class.forName`/`ObjectStreamClass` to handle serial-version mismatches. Moving bytes into browser storage alone would not solve codec compatibility. No persistence/account design is attempted here.

## Baseline build commands, results, and environment constraints

Commands ran from `C:\Users\Silent\Projects\forge-adventure-web` in PowerShell. The initial tree was clean. `git switch -c research/codex-runtime` succeeded after granting access to the repository's Git metadata. No global environment settings were changed.

| Actual command / check | Observed result |
| --- | --- |
| `git rev-parse HEAD` | `db893dcdb14fdd85a05437ad458dc70ef704ca2e` |
| `git --version` | `git version 2.54.0.windows.1` |
| `java -version` | Oracle Java **26**, build `26+35-2893`, through the Oracle `javapath` executable. |
| `& 'C:/Program Files/Java/jdk-17/bin/java.exe' -version` | Oracle JDK **17.0.12**, build `17.0.12+8-LTS-286`, already installed. |
| `Get-Command java,javac,mvn,git,gh -ErrorAction SilentlyContinue` | Java/javac/git available; Maven and GitHub CLI not found on PATH. |
| `mvn -version` | PowerShell command-not-found; exit 1. No Maven version obtained. |
| `mvn -o -B -T1 -pl forge-gui-mobile-dev -am -DskipTests compile` | PowerShell command-not-found; exit 1 **before Maven or javac started**. This is an environment blocker, not a Forge compilation failure. |
| Tracked-file search for `mvnw`, `mvnw.cmd`, `mvn.cmd` | No wrapper/distribution found in repository. Targeted standard Maven/IDE locations did not identify a runnable Maven installation; this is not an exhaustive machine inventory. |

### Bounded source compilation actually executed

The local Maven cache had gdx **1.13.5**, not the required **1.14.2**, at the path below. Two separate `javac` invocations compiled existing source without stubs or changes:

```powershell
$probeDir = Join-Path ([IO.Path]::GetTempPath()) ('forge-codex-runtime-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $probeDir | Out-Null
$probeGdx = Join-Path $env:USERPROFILE '.m2/repository/com/badlogicgames/gdx/gdx/1.13.5/gdx-1.13.5.jar'
& 'C:/Program Files/Java/jdk-17/bin/javac.exe' --release 17 -cp $probeGdx -d $probeDir forge-gui-mobile/src/forge/adventure/stage/OrthogonalTiledMapRendererBleeding.java
# Observed $LASTEXITCODE: 0
& 'C:/Program Files/Java/jdk-17/bin/javac.exe' --release 17 -cp $probeGdx -d $probeDir forge-gui-mobile/src/forge/adventure/stage/SpriteGroup.java
# Observed $LASTEXITCODE: 0
Get-FileHash -LiteralPath $probeGdx -Algorithm SHA256
```

Jar SHA-256: `045207DD3E582360B487C60EFA99E104E0ECDE64D3FDDCAB394757537D1620DB`.
Actual temporary output: `C:\Users\Silent\AppData\Local\Temp\forge-codex-runtime-802601ba1fb545a7986cab2b5de8de85`. Class sizes: renderer **5,968 bytes**, `SpriteGroup` **1,491 bytes**, comparator **821 bytes**. Generated files stay outside the checkout and are not committed.

**Limit:** `OrthogonalTiledMapRendererBleeding` was an easy isolated compile, but no call site was found in the inspected Adventure source. The active dungeon renderer is `PointOfInterestMapRenderer`, which depends on `MapStage`. The recommended probe therefore uses **`SpriteGroup`**, verified in active `GameStage` construction, rather than presenting the isolated renderer as an executed Adventure scene. Neither compiled class was executed.

### Reproducible baseline path once Maven is available — NOT RUN successfully

Use the existing JDK 17 explicitly and a known Maven 3.8.1+ executable. Prefer Maven 3.9.x as a conservative unverified choice, not a requirement inferred from CI. Set variables only for the command session, or invoke Maven with a process-specific environment. First repeat:

```powershell
# From repository root, with the selected Maven on PATH and JAVA_HOME pointing at the existing JDK 17:
mvn -version
mvn -o -B -T1 -pl forge-gui-mobile-dev -am -DskipTests compile
# After successful compilation, run the relevant tests:
mvn -o -B -T1 -pl forge-gui-mobile-dev -am test
```

`-pl ... -am` bounds compilation to the mobile-development dependency closure; `-T1` avoids parallel reactor load. `compile` avoids package-phase Launch4j/assembly output and game launch. `-DskipTests` does not establish test success. Offline resolution may fail on missing plugins, root Wagon extension, or newer dependencies; do not substitute cached versions. If it fails, record exact missing coordinates and request approval for a bounded tool/dependency acquisition before an online retry. Do not run a root `install`, `deploy`, release profile, or automatic asset updater merely to establish compilation. The tracked `.mvn/maven.config` selects `.mvn/local-settings.xml`; no credential changes are needed for these proposed phases.

## Ranked dependency/browser blocker matrix

Classification: **D** = source and platform evidence demonstrate an incompatibility in a direct unchanged route; **P** = plausible blocker requiring a compiler/runtime probe; **U** = unknown behavior/performance. **No TeaVM compiler errors were observed in this task.** A D row is not proof that a targeted adapter cannot solve it, nor that its entire containing jar is reachable.

| Rank | Boundary and source evidence | Classification and consequence | Smallest investigation / mitigation |
| --- | --- | --- | --- |
| 1 | Local resources: `Config` uses `new File(...).list`, `Files.exists`, and path-based `FileHandle`s; `CardStorageReader` uses `File`, `ZipFile`, pooled reads. | **D for unchanged host-filesystem semantics; P for selected TeaVM virtual-file support.** Browsers do not expose the installed Forge directory as a normal process filesystem. Startup reaches these operations. | Asset manifest, internal/preloaded reads and async loading; test directory lookup, locale/common overrides, ZIP/card data separately. Keep download/update logic out of the first probe. |
| 2 | Rules/AI reflection: [`TriggerType.java:174`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/trigger/TriggerType.java#L174), [`ReplacementType.java:78`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/replacement/ReplacementType.java#L78), [`Keyword.java:264`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-game/src/main/java/forge/game/keyword/Keyword.java#L264); [`SpellApiToAi.java:234`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-ai/src/main/java/forge/ai/SpellApiToAi.java#L234) calls `ReflectionUtil.makeDefaultInstanceOf`. | **D for ordinary GWT Java reflection; P for TeaVM metadata/metaprogramming coverage.** These operations are gameplay-essential, not optional desktop UI. Backend JSON reflection registration alone is not proof of general `java.lang.reflect` support. | Compile a tiny actual trigger/replacement/keyword/AI factory path. Verify selected constructors and instantiated types; investigate generated factories/TeaVM metadata only if needed. Preserve rule behavior. |
| 3 | Threads and blocking coordination: [`ThreadUtil.java:25`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-core/src/main/java/forge/util/ThreadUtil.java#L25), `FThreads`, `HostedMatch` action dispatch; [`WaitRunnable.java:12`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/util/WaitRunnable.java#L12) uses monitor wait/notify; [`MatchController.java:315`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/screens/match/MatchController.java#L315) waits on overlay latches. | **D for ordinary GWT multithreading; P for TeaVM executor/wait semantics; U for AI responsiveness.** TeaVM supports logical green threads, so “all threads are unsupported” would be incorrect. CPU-intensive AI need not yield automatically. | Test game action → UI prompt → resumed action without freezing frames. Profile scheduling before considering a worker/message boundary; do not assume shared JVM objects transfer to workers. |
| 4 | Saved objects: `WorldSave`, nested object streams in `SaveFileData`, runtime class lookup and version-descriptor override. | **P, high risk.** Browser storage APIs do not provide Java object-stream semantics. Exact TeaVM object serialization coverage was not compiled or audited to completion. | A later disposable fixture round-trip must verify the codec independently of storage. No save migration or account work in the first probe. |
| 5 | Native/platform launchers: LWJGL3/JNI, OSHI, AWT `Desktop`/`SplashScreen`/`ImageIO` in `Main.DesktopAdapter`. | **D if retained in the browser runtime.** This is primarily a separable launcher boundary, not evidence that Adventure requires Swing. | Add a separate browser launcher; leave `forge-gui-mobile-dev` out of its runtime. Implement only required browser device operations. `IDeviceAdapter` itself exposes jUPnP types, which needs reachability inspection. |
| 6 | [`FSkinFont.java:399`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/assets/FSkinFont.java#L399) generates FreeType font data; absolute font enumeration/cache writes. `MapStage` creates Box2D worlds; navigation uses fixtures/raycasting. | **D for carrying desktop native binaries unchanged; P for replacement web implementations.** gdx-TeaVM advertises a web FreeType module, not Forge's full font/cache behavior. Box2D must be checked separately. | First probe needs neither extension. Later test the exact FreeType API usage and a real navigation collision. Do not remove collision or text features to claim full compatibility. |
| 7 | XStream/Gson, Guava/Commons, Sentry/tinylog/JGraphT; controllers, TextraTypist and TenPatch. | **P.** JVM/library versions, reflection, resources, logging and native/transitive calls may require emulation or exclusion. A dependency declaration is not itself a demonstrated fatal runtime dependency. | Resolve the tree and capture TeaVM reachability diagnostics; test only paths actually used by the scene/duel. Do not upgrade unrelated libraries speculatively. |
| 8 | Netty/Jetty/jUPnP in shared GUI; asset/image fetchers and updater. | **D for native TCP/server/discovery operations in a browser; P for whether optional code is retained.** Single-player does not need a listening server. HTTP fetch and CORS need a separate browser-compatible path. | Exclude unused networking services; verify pruning rather than stubbing game logic. Do not start any server in this assignment. |
| 9 | [`TemplateTmxMapLoader.java:23`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/util/TemplateTmxMapLoader.java#L23) constructs local `File`s, loads textures synchronously, sets mipmaps, and shares `Forge.getAssets().tmxMap()`; templates are expanded at line 104. | **D for direct local-file loading; P for async/cache adaptation.** A stock TMX loader can omit Forge template behavior and is not an equivalent map test. | Explicit asset dependency closure; preserve the existing template expansion. Test genuine map objects/properties, not just an image of the map. |
| 10 | Shader/FBO/texture work: `forge/util/ShaderUtil`, `Shaders`, `adventure/util/ShaderDrawable`, `Graphics`; NPOT images such as the map's 2528×1024 tileset. | **P for exact WebGL shader/filter/format compatibility; U for memory and context loss.** Desktop rendering is not WebGL validation. | Compile actual shaders, inspect filter/wrap settings, constrain first texture upload, then record GL errors, context loss and restoration. |
| 11 | Safari viewport/touch/audio/lifecycle and asset scale. `Scene` switches intended width/height; common config starts at 480×270. | **U for Forge Safari behavior.** Source-level portrait paths exist, but no device run occurred. WasmGC availability alone cannot establish application compatibility. | Record device/iOS/Safari versions; portrait touch, rotation/resize, background/resume, audio after gesture, startup bytes/time, and frame latency. |

The [libGDX GWT specifics](https://libgdx.com/wiki/html5-backend-and-gwt-specifics), [file handling](https://libgdx.com/wiki/file-handling), and [networking documentation](https://libgdx.com/wiki/networking) substantiate platform restrictions; these do not describe gdx-TeaVM's entire implementation. [TeaVM's overview](https://teavm.org/docs/intro/overview.html) explicitly limits reflection/resources/class loaders/JNI and describes green threads. Its [metaprogramming API](https://teavm.org/docs/runtime/metaprogramming.html) offers compile-time generation, which is a possible adaptation mechanism, not an automatic Forge fix.

## Browser technologies comparison and recommended experiment

| Route | Evidence and fit | Remaining uncertainty |
| --- | --- | --- |
| **gdx-TeaVM JavaScript first** | Consumes Java bytecode; avoids requiring GWT-compatible Java sources for every library. The [pinned 1.6.1 README](https://github.com/xpenatan/gdx-teavm/blob/1.6.1/README.md) pairs libGDX **1.14.2** with TeaVM **0.15.0**, matching Forge's gdx version. It documents browser JS/Wasm builds and web FreeType/controllers modules. | Neither version alignment nor libGDX demos prove Forge support. Inspect exact reflection, JDK library, Box2D and third-party paths. |
| **gdx-TeaVM Wasm** | A follow-up output from the same backend family. WebKit documents WasmGC shipping in [Safari 18.2](https://webkit.org/blog/16301/webkit-features-in-safari-18-2/). | Choose the precise Wasm backend/features before setting minimum Safari version. Wasm does not restore JVM class loading, desktop APIs, or shared-memory executor semantics. No performance advantage measured here. |
| **libGDX GWT** | Established browser backend and documented reflection emulation/asset preload workflow. [Starter configuration](https://libgdx.com/wiki/app/starter-classes-and-configuration) requires source/module metadata for dependencies. | Forge has no GWT modules and uses direct Java reflection/threads. Expect more adaptation of its source/dependency graph. Use as a focused comparison if TeaVM fails on a specific component, not as an assumed turnkey fallback. |
| **Java runtime + native browser client** | A possible fallback preserving Forge rules/AI on the JVM while the browser owns rendering and input. | Requires a deliberate state/event/choice protocol; `HostedMatch` and `IGuiGame` are coupled to controller/view behavior. This report does not design hosting, accounts, deployment, or UI. Streaming a desktop is not this architecture. |

### Smallest practical browser experiment — proposed, not implemented

**Question:** can a real, actively used Adventure scene component and genuine asset render and respond to touch through the selected backend, with upstream source unchanged?

Use [`forge-gui-mobile/src/forge/adventure/stage/SpriteGroup.java`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/stage/SpriteGroup.java#L15). It orders actors by Y in `drawChildren`, `addActor`, and `UpdateActorZ`. [`GameStage.java:333`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-mobile/src/forge/adventure/stage/GameStage.java#L333) instantiates it; `CharacterSprite` updates its sorting on movement. Its imports require only libGDX and `java.util.Comparator`, so the first experiment need not load `Forge`, `Config`, or the rules database.

**Proposed files/components:** one standalone `experiments/adventure-teavm/` Gradle project with settings/build configuration, two small harness/launcher classes, a static page, and an explicit asset manifest. Do not add it to the root Maven reactor. Select the single existing `SpriteGroup.java` source into a dedicated source set via an include filter; do not include the entire mobile source tree, duplicate the class, or rewrite its logic. Use Java release 17, gdx **1.14.2**, gdx-TeaVM plugin **1.6.1** / TeaVM **0.15.0**, with dependency locking. The [tagged dependency catalog](https://github.com/xpenatan/gdx-teavm/blob/1.6.1/gradle/libs.versions.toml) independently confirms those library versions. Gradle/plugin host-JDK requirements remain unverified; source release 17 does not establish the required compiler-host JDK. A Gradle distribution/cache and these newer artifacts are not established locally: obtain approval for required acquisition before executing this future task.

Asset closure: [`common/sprites/treasure.atlas`](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/res/adventure/common/sprites/treasure.atlas) (**416 bytes**) and its sole page `treasure.png` (**18,694 bytes**), total **19,110 bytes** on disk before packaging. The atlas has Idle/Death regions and is used by reward objects (`MapStage` defaults to it). Preserve paths and attribution; do not copy the entire content directory.

Harness behavior: a libGDX `Stage` with a portrait viewport containing the unchanged `SpriteGroup` and two overlapping `Image` actors using atlas regions. Drag one actor across the other's Y coordinate; record touch coordinates, child order and draw order so correct sorting is observable. Harness input/optional frame animation is test scaffolding, **not reuse of CharacterSprite movement, collisions, reward logic, or an Adventure scene**. Use `WebApplication` as the browser launcher. Its [pinned implementation](https://github.com/xpenatan/gdx-teavm/blob/1.6.1/backends/backend-web/src/main/java/com/github/xpenatan/gdx/teavm/backends/web/WebApplication.java#L83) initializes browser graphics/input/files and handles resize/visibility events; that is backend evidence only.

Future commands, **valid only after those proposed files and approved tool dependencies exist**:

```powershell
# Standalone project; cap build parallelism and JVM heap for Silent.
gradle -p experiments/adventure-teavm --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m' gdx_teavm_web_js_build
# Optional later comparison, not the first requirement:
gradle -p experiments/adventure-teavm --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m' gdx_teavm_web_wasm_build
```

The [1.6.1 usage guide](https://github.com/xpenatan/gdx-teavm/blob/1.6.1/docs/usage.md#web-targets) documents these build tasks. Configure output and dependency/cache paths inside the experiment as appropriate; record the Gradle/JDK versions and lockfiles. Do not run the plugin's `*_run` tasks on Silent: they start a server, and binding/network exposure needs explicit approval. A Safari device test requires a separately approved way to serve the static artifact to that device. Desktop emulation or a desktop WebKit run does not count as iPhone verification.

**Pass:** transpilation succeeds without Forge stubs or `SpriteGroup` edits; both original assets load; two visible overlapping sprites change order through actual `SpriteGroup.drawChildren`; pointer dragging works; resize preserves input mapping; no runtime/GL/load errors. Record generated size, resource requests, cold-start time and frame timing. Then repeat on an actual iPhone Safari in portrait, record OS/browser/device and evidence. Suggested exploratory budget: usable first interaction within 5 seconds and 95th-percentile frame intervals below 33 ms during a 60-second drag session; these are proposed gates, not measured results.

**Fail:** missing APIs/metadata, asset preloading failure, empty canvas, unchanged ordering, touch coordinates not mapping to actors, sustained stalls, or a need to replace upstream component behavior. Capture exact first failure and reachability chain. Lack of an approved serving path is **blocked**, not a rendering failure.

**Interpretation:** success proves one component/asset/input boundary, not a working Adventure port. Failure falsifies the proposed unchanged slice on that pinned configuration, not every native browser approach.

### Next authentic scene gate

After that component gate, scope a separate experiment around `TemplateTmxMapLoader` and `PointOfInterestMapRenderer`/`MapStage`. A concrete tiny content fixture is `common/maps/map/cave/cave_treasure.tmx` (**1,619 bytes**, 30×17 cells): its closure includes `maps/tileset/main.tsx`, `main.png`, `buildings.tsx`, `buildings.png`, and `maps/obj/entry_up.tx`/`treasure.tx`. Preserve relative references and recursively enumerate any further dependencies. It uses base64/zlib layer data and Forge object templates. `main.png` is declared 2528×1024, about 9.9 MiB decoded RGBA before GPU/mipmap overhead, so small TMX text does not imply small runtime memory.

This second gate must load templates with their properties and render stage actors between map layers; a stock map loader or screenshot would omit genuine behavior. It is not as small as the first probe because `MapStage` reaches physics, current world/player, HUD and Forge assets. Compile diagnostics should identify required boundaries before choosing narrowly scoped adapters. Do not call this gate complete without a real scene, input, and assets demonstrated.

## Go / no-go criteria, risks, and next task

1. **Go now:** review this report, then commission only the component experiment above and arrange approved Maven/dependency access. Record the missing baseline explicitly until the exact reactor closure compiles and relevant tests pass.
2. **Go to a scene:** unchanged component passes on desktop and actual portrait Safari; resource contracts and extension requirements are bounded. Keep upstream game modules unchanged during the initial experiment.
3. **Go toward browser-only gameplay:** separately demonstrate a real rules/AI fixture that exercises trigger/replacement/keyword construction and AI factory selection, then a complete Adventure encounter → genuine Forge AI match → return/reward loop. Verify prompts resume the game without blocking rendering. A visual demo alone cannot justify this decision.
4. **No-go for a broad direct port yet:** no working baseline, no TeaVM compilation of the actual dependency closure, and no Safari measurements. Stop and reassess if engine adaptation spreads into broad rules rewrites, blocking execution cannot remain responsive, or asset/memory costs fail measured mobile budgets. Prefer investigating a JVM rules boundary over recreating the engine.

Principal maintenance risk: web emulation/adapters diverging from upstream while new cards, keywords and AI classes continue to arrive. Prefer generated registries/manifests and a separate target when evidence justifies them, but do not implement these speculatively. No multiplayer, accounts, durable-save system, or hosting work belongs in the next task.

## External versions, sources, and attribution

Consulted official project documentation/source on the research date; links using documentation branches may change. Version tags below are research pins, not an assertion that they are the newest releases or that artifacts resolved on Silent.

- [gdx-TeaVM 1.6.1 README/version matrix](https://github.com/xpenatan/gdx-teavm/blob/1.6.1/README.md), [usage](https://github.com/xpenatan/gdx-teavm/blob/1.6.1/docs/usage.md), [plugin properties](https://github.com/xpenatan/gdx-teavm/blob/1.6.1/docs/plugin-properties.md): backend/plugin setup, reflection configuration and JS/Wasm tasks. Its README identifies Apache-2.0 licensing. No external implementation code was imported.
- [TeaVM 0.15.0 source](https://github.com/konsoletyper/teavm/tree/0.15.0), [license](https://github.com/konsoletyper/teavm/blob/0.15.0/LICENSE), [overview](https://teavm.org/docs/intro/overview.html), [metaprogramming](https://teavm.org/docs/runtime/metaprogramming.html): Apache-2.0; restricted JVM facilities and potential compile-time adapters.
- [libGDX 1.14.2 source/license](https://github.com/libgdx/libgdx/blob/1.14.2/LICENSE): Apache-2.0. [GWT guide](https://libgdx.com/wiki/html5-backend-and-gwt-specifics), [threading](https://libgdx.com/wiki/app/threading), [files](https://libgdx.com/wiki/file-handling), [starter classes](https://libgdx.com/wiki/app/starter-classes-and-configuration), [FreeType](https://libgdx.com/wiki/extensions/gdx-freetype), [networking](https://libgdx.com/wiki/networking): platform constraints, not application compatibility tests.
- [WebKit Safari 18.2 announcement](https://webkit.org/blog/16301/webkit-features-in-safari-18-2/): WasmGC platform availability; no Forge evidence.
- Forge's [root license](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/LICENSE) is GPL-3.0, and its POM declares GPL 3. Preserve upstream notices and provide corresponding source for any eventual distributed build as required by its license. Asset provenance must be checked individually rather than inferred from the code license: the repository includes separate asset terms, for example `common/particle_effects/Particle Park License.txt`, and `CONTRIBUTING.md` requests art attribution. Third-party native/web extension transitive licenses remain to be checked before importing or distributing them; no broad legal clearance is claimed by this audit.
