# Standalone original Forge lifecycle probe — D3a

One disposable JVM initializes Forge, arranges the accepted human Bolt/Mountain
fixture through the original start hook, and stops at authentic human priority.
It is **not connected to the static battlefield**, and is not a headless service.
There is no TestNG invocation, browser, transport, listener or production DTO.

## Reproduce with the existing offline environment

From the repository root in PowerShell 7:

```powershell
./experiments/standalone-bolt/Run-Standalone.ps1 -Label fresh-unique-label
```

Use a new label. The launcher refuses to overwrite a previous run. It requires:

- The already-installed `C:/Program Files/Java/jdk-17`.
- Existing compiled reactor outputs and dependencies from the accepted baseline
  and scoped tests; the existing desktop Surefire XML supplies only the classpath.
  The launcher does **not execute that XML or TestNG**. All 99 retained entries
  must already exist inside this repository; the TestNG JAR is excluded.
- The root POM's existing JVM `argLine` opens. No POM/plugin changes or Maven
  invocation are involved.

It invokes the installed `javac` with UTF-8, source/target 17, an empty sourcepath,
the existing classpath and a fresh output directory. Compilation inputs are only
`StandaloneBolt.java` and the **unchanged** original `TestUtils.java`,
`HeadlessGuiDesktop.java`, `HeadlessNetworkGuiGame.java`. Those helpers are rebuilt
locally so this experiment does not silently reuse their old compiled versions.
The resulting classes precede the existing classpath. The original engine's
already-built classes remain prerequisites, not a newly rebuilt Forge distribution.

The Java command uses the POM opens, that classpath, main class
`experiment.StandaloneBolt`, and an absolute result-file argument. Working
directory: `forge-gui-desktop`, as in the accepted tests. Arguments are passed
through `ProcessStartInfo.ArgumentList`, not shell interpolation.

The launcher creates only `.local-tools/standalone-bolt/<label>/` for compilation,
logs and diagnostic profiles, redirecting `JAVA_HOME`, `JAVA_TOOL_OPTIONS`,
`APPDATA`, `LOCALAPPDATA`, `TEMP`, `TMP`. Java options set the isolated `user.home`
and `java.io.tmpdir`, headless AWT and a 1536 MiB heap ceiling. It checks exact
environment restoration, including an absent/present-variable preflight. Missing
inputs cause a stop; there is no download, install or rebuild fallback.

## Lifecycle and termination

The only scripted decisions are explicitly offered Play and Keep during original
bootstrap. Fixture arrangement uses the accepted original start-game hook and
original `GameAction` zone moves, not replacement gameplay. Once arranged, no
controller action is submitted. Every synchronous required question fails closed
and records the asynchronous failure before throwing.

The controller-binding override calls the original implementation. The await
override publishes immutable **private Java lifecycle references** and calls the
original `IGuiGame.awaitInput`. Comparison uses `==`; printed identity hash codes
are diagnostic labels, not collision-free IDs or transport handles. Reading the
atomic input pointer, concurrent input queue, latch count and thread stacks does
not traverse the game/view graph.

This is deliberately a disposable process, with no campaign/save. On success or
failure it flushes logs, writes a result and calls `System.exit(status)`. It never
concedes, stops the match, releases original input, passes priority or mutates the
graph to clean up. An experiment-owned daemon watchdog halts **this JVM only**
after 150 seconds if normal exit/shutdown hangs. The launcher also bounds compile
to 60 seconds and the standalone process to 180 seconds, with a final 10-second
exit observation after killing only its retained child Process handle. Expiration
is failure and never retried automatically. The normal path and process exit were
observed; watchdog/forced-termination branches were not fault-injected.

This process-disposal policy is not a reusable match shutdown, save, disconnect or
reconnect policy. The observed Swing EDT is non-daemon, so simply returning from
`main` would not establish bounded termination. No TestNG or Surefire exit policy
is used here.

## Evidence

[Actual results and source boundaries](../../docs/experiments/standalone-bolt-results.md).
Private originals remain under the ignored run directory. To create a new,
sanitized evidence directory after manually reviewing a successful run:

```powershell
./experiments/standalone-bolt/Publish-Evidence.ps1 -Label fresh-unique-label
```

This copies logs, JSON results, classpath/source inventory and integrity hashes;
it excludes compiled files, JARs, POM downloads and profiles. Repository absolute
paths and generated lambda addresses are normalized, with original/public hashes
and sizes retained in the manifest. Inspect new evidence before committing it.

Production capture ownership remains **unresolved**. An EDT check or parked latch
does not make the game graph exclusively owned. Do not attach a projector here.
