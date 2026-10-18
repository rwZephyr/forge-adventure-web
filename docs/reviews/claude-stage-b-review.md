# Stage B independent technical review — TeaVM 0.15.0 vs Forge's real AI path

Reviewer: Claude (new account), read-only. Date: 2026-10-18. Scope: PR #13 baseline, `AiController`, `AITimeoutTracker`, `ApiType`, and what they pull in.

**Evidence labels.** **[V]** verified this session by reading the cited source. **[A]** author-reported in the PRs; I read it but did not reproduce it. **[P]** prediction, not a compiler or runtime result. **[R]** recalled from general knowledge; Codex should grep before relying on it.

**Limits of this review.** I compiled and ran nothing. Sources were read from the fork's `master` (documented as the unmerged `db893dc` baseline) via GitHub raw/web fetch, and raw text carries no line numbers, so I cite methods and rely on PR #13's pins for line numbers. TeaVM's GitHub directory listing was blocked, so "absent from classlib" rests on raw-path 404s plus PR #13's JAR hashes. I did not open `GameAction`, `SpellApiToAi`, `ReflectionUtil`, `Localizer` or PR #9.

---

## 1. Verified findings

**Forge side**

- **V1. Threaded path.** `AiController.chooseSpellAbilityToPlayFromList` does the following:
  - nests `AITimeoutTracker.begin`;
  - builds a `FutureTask` whose body arms the shared deadline with `honourInterrupt=true`;
  - starts a daemon `Thread` named "Game AI Eval";
  - calls `future.get(max(0, deadline − nanoTime), NANOSECONDS)`.
- **V2. Cleanup runs only on exception.** The `catch (InterruptedException | ExecutionException | TimeoutException)` branch does `printStackTrace`, a `getStackTrace` sampling loop, `cancel(true)`, `join(2000)`, then `stop()` guarded by `catch (UnsupportedOperationException | NoSuchMethodError)`. The happy-path Bolt run never executes any of it. The code does need these symbols to compile.
- **V3. Forge already tolerates "no stop".** Its own comment says `stop()` is gone on Android and Java 20+ and "the thread will keep running". A `stop()` that throws `UnsupportedOperationException` is therefore a path upstream Forge already supports.
- **V4. Deadline semantics are cooperative.** The eval thread self-aborts via `AITimeoutTracker.check()` (throws `Expired`), and the timed `get` uses the same absolute deadline. By the time `TimeoutException` fires, the next `check()` will already throw. `cancel(true)` interrupting the runner matters mainly on non-timeout exits (`InterruptedException`/`ExecutionException`).
- **V5. Code without `check()` cannot be aborted.** Forge's comment names mana payment, threat prediction and simulation.
- **V6. `AITimeoutTracker` reaches more than FutureTask.**
  - `begin()` calls `ThreadUtil.delay(...)`, so `ThreadUtil`'s static initializer is reachable. It builds `Executors.newCachedThreadPool`, `newScheduledThreadPool` and `newWorkStealingPool`, and uses `ScheduledFuture`, `ThreadFactory` and `Runtime.availableProcessors`.
  - `Expired` calls `super(msg, null, false, false)`, the protected 4-argument `RuntimeException` constructor.
  - The tracker also uses `ConcurrentHashMap`, `synchronized`, `ThreadLocal.withInitial` and `System.nanoTime`.
- **V7. In the fixture, the `ThreadUtil.delay` call is dead at runtime** (it needs a repaint hook and `timeout > 2 s`; the fixture sets 2 s). It is not dead in the real game: `Game.AI_TIMEOUT` defaults to 5 (seconds, via `TimeUnit.SECONDS`), and `HostedMatch` copies it from preferences.
- **V8. Sentry runs on the happy path.** `canPlaySa` calls `Sentry.addBreadcrumb/setExtra/removeExtra` for any ability with an API. No return value is consumed, so the calls are write-only observability.
- **V9. `ApiType`.** Every constant's constructor calls `ReflectionUtil.makeDefaultInstanceOf` (all use the default stateless path), so all effect constructors are needed, as PR #13 says.
- **V10. Real topology is two-level.** `HostedMatch` comments that the match must run in a different thread so GUI input can be invoked from inside the game, and starts it via `game.getAction().invoke(...)`. The fixture calls the brain directly, bypassing that thread.

**TeaVM 0.15.0 side**

- **V11. `TThread`** [source]:
  - It has `start`, `join(long)`, `interrupt`, `isInterrupted`, `isAlive`, `setDaemon` and a `(Runnable, String)` constructor.
  - It has no `stop()`, and `getStackTrace()` returns an empty array. The sampling loop therefore becomes a silent no-op.
  - It has no preemption mechanism in this file. Switching happens at `sleep`, `wait`, monitor contention, or `yield()` (only after 30 calls and a 100 ms slice).
  - `interrupt()` sets a flag and invokes a handler only if the thread is in `sleep`/`wait`.
- **V12. `Object.wait`** [source]:
  - It is async-suspending, supports timeouts via `Platform.schedule`, and is interruptible. `Thread.join(timeout)` is built on it.
  - `wait(0, 0)` schedules no timer, so it waits forever.
  - A nanos-only timeout becomes a 0 ms timer.
  - `monitorEnterSync` throws `IllegalStateException` if another green thread owns the monitor.
- **V13. Smaller classlib facts:**
  - `System.nanoTime()` is `(long)(performance.now()*1e6)`.
  - `ThreadLocal.withInitial` exists, keyed per `TThread.key`.
  - `TRuntimeException` has only the 0/1/2-argument constructors, no 4-argument one.
  - The TeaVM docs describe coroutine-based Threads with `synchronized`/`wait`/`notify`, and say TeaVM refuses to pass a coroutine to a JS function.
- **V14. `java.util.concurrent` gaps** (corroborates PR #13's [A] JAR finding, but is a path probe, not a jar listing):
  - `TFutureTask`, `TFuture`, `TTimeoutException` and `TExecutors` return 404 at tag 0.15.0.
  - `TExecutionException` exists.
  - The TeaVM JCL report shows `java.util.concurrent` at 20% partial / 14% full implemented (package level only).

## 2. Predicted blockers (none yet a compiler result)

| # | Prediction | Basis |
|---|---|---|
| P1 | Unresolved `FutureTask`, `Future`, `TimeoutException` (multi-catch) | V14 |
| P2 | Unresolved `Thread.stop()` (compile-time only; not on the happy path) | V11 |
| P3 | Unresolved `RuntimeException(String,Throwable,boolean,boolean)` from `Expired` | V13 |
| P4 | Unresolved `Executors`, `ExecutorService`, `ScheduledFuture`, `ThreadFactory`, `ForkJoinPool` via `ThreadUtil` | V6, V14 |
| P5 | Sentry-related linking errors (Stage A already hit these per PR #13 [A]) | V8 |
| P6 | Eval thread cannot start until the caller suspends; a spinning `get` hangs until deadline | V11 |
| P7 | Uninstrumented AI code freezes the worker; `join`/`stop` fallback can never run | V5, V11 |
| P8 | Cold-start on iPhone may exhaust the deadline, giving a null "pass priority" that is not a threading failure | V4 |

## 3. Answers

**1. Flaws and blind spots in PR #13's baseline.** The design is sound: unchanged-first, stop at the first boundary, no direct calls, a worker watchdog, three runs plus a negative control. Gaps:
- **B1. `ThreadUtil`/executors are missing from the scope** (P4). `AITimeoutTracker` and the `Expired` constructor (P3) also go unmentioned. Expect more unresolved symbols than the doc lists.
- **B2. The fixture never exercises timeout, cancel, interrupt or `stop`** (V2). "Preserve cancellation semantics" is therefore untested by Stage B. A config-only timeout probe is worth designing, but the outer session may throw `Expired` before the `FutureTask` is reached, so confirm the branch with path witnesses. If config cannot force it, report it as unexercised rather than adding a hook.
- **B3. Define expected deviations up front.** Empty stack traces, no `stop`, cooperative-only abort. Otherwise they get reclassified after the fact.
- **B4. Distinguish "AI chose nothing" from "deadline hit".** `Expired` returning null is a legitimate Forge outcome, so record remaining nanoseconds at `get` plus per-phase timings (P8).
- **B5. Oracle weakness.** Two interchangeable Bolts and one candidate cannot detect ordering divergences (for example hash iteration). JVM-equals-TeaVM here does not generalize to Adventure AI [P].
- **B6. Topology (V10).** Stage B proves the nested eval thread, not game thread → brain → eval thread, nor the GUI handoff. Not a Stage B failure, but do not claim more.
- **B7. Check classpath contributors.** gdx-teavm 1.6.1 may ship extra classlib emulation, which PR #13 itself concedes ("other compiler inputs may affect resolution"). Record the resolved jar hashes actually used.
- **B8. Browser timers and visibility.** Throttling or suspension of hidden iOS pages can burn the deadline before the eval thread starts. Run foreground only and log `visibilityState` [R].

**2. Smallest credible way to preserve semantics.** Keep Forge's source unchanged where possible and add the minimum at the classlib seam, in this order:
1. Add a monitor-based `FutureTask` (plus `Future`, `RunnableFuture`, `TimeoutException`, `CancellationException`), about 100–150 lines, built on `synchronized` + `wait`/`notifyAll`. Requirements:
   - record the runner thread so `cancel(true)` interrupts it;
   - make `run()` a no-op if already cancelled;
   - never call `wait(0)`; if remaining ≤ 0 and not done, throw `TimeoutException` immediately;
   - use an absolute-deadline loop against `nanoTime`;
   - wrap task failures in the existing `TExecutionException`.
   Write it clean-room or from Apache-licensed sources; do not copy OpenJDK's without legal review (Forge is GPL).
2. Add `TThread.stop()` throwing `UnsupportedOperationException`. This is exactly what Forge handles on Android/JDK 20+ (V3).
3. Add the 4-argument `TRuntimeException` constructor (a no-op for `writableStackTrace`).
4. `ThreadUtil` is Forge's own platform seam. If P4 is confirmed and reachable, a browser `ThreadUtil` (`delay`, `invokeInGameThread` over `TThread`/`Platform.schedule`) is a separate boundary change. It does not touch AI decision semantics, but it needs its own review.
5. Sentry (V8) is observability-only. A write-only shim is defensible only if the invariant "no Sentry return value is consumed on the AI path" is asserted. This relaxes PR #13's "no wholesale stubs" rule and needs explicit owner sign-off.

How to ship these, to be verified with a ~20-line probe first: either an upstream TeaVM PR (a 2022 outside contributor added `TExecutionException`, so precedent exists) or an external jar like `m-m-m/tvm`, which describes itself as a temporary classlib-extension workaround (maintenance status unverified).

**3. Alternatives that silently break semantics.** Separate two tests: is the AI evaluation genuine, and are the thread semantics preserved?

| Shortcut | AI eval genuine? | Thread semantics kept? |
|---|---|---|
| `get()` calls `run()` inline | yes | no (thread bypassed) |
| `Thread.start()` runs the Runnable synchronously | yes | no: same `currentThread`, `TimeoutException` branch unreachable, `isAlive` false |
| Spin or poll loop in `get` without suspending | yes | **blocks the event loop**: eval thread cannot start (P6), so it ends in a deadline timeout or a hang |
| Timeout ignored or `wait(0)` (infinite) | yes | deadline semantics lost |
| `Thread.sleep(1)` polling | yes | acceptable but adds timing noise; not preferred |
| `useSimulation(FULL)` | **no** | skips the `FutureTask` entirely (V: `usesFullSimulation()` early return) |
| Preselected target, free cast, direct `DamageDealAi` | **no** | n/a (already banned by PR #13) |
| AI evaluation moved to a separate Worker via RPC | partly | changes the object graph (already rejected) |

**4. Evidence to collect before changing Forge or runtime.**
1. JVM control 3× plus the empty-hand negative, with path witnesses, as PR #13 specifies.
2. The unchanged TeaVM compile with full, unsuppressed diagnostics: each unresolved symbol with its reachability chain, scored against P1–P5 as hit or miss.
3. For each blocker, whether it is happy-path-reachable or compile-time only (P2 and P4 should be compile-time only for the fixture).
4. Resolved classpath jar hashes (B7).
5. Only after items 1–4: a small isolated harness reproducing AiController's exact pattern (`FutureTask` + `Thread` + timed `get`) with a trivial Callable. Cases: normal completion, exception propagation, cooperative abort at the deadline, `cancel(true)`, zero remaining time, `join` after cancel. Run in Chromium, then iPhone Safari, before touching Forge.
6. Browser timing: phase timings, remaining nanoseconds at `get`, cold vs warm run, device and iOS version, `visibilityState`, with observation that does not pause execution.

**5. Findings that could justify reconsidering all-browser.** None so far. The blockers look bounded (a small `FutureTask`, a `stop` stub, a constructor, a seam). Reconsider if any of these become objective facts:
- Preserving genuine semantics needs a broad concurrency port (thread pools, ForkJoin, AQS/Unsafe across the engine) rather than the five items above.
- Realistic mid-game decisions on target iPhones hit the default 5 s deadline (silently degrading AI versus desktop), or uninstrumented code (V5) freezes the page for seconds.
- The worker isolation needed for a trustworthy watchdog is incompatible with libGDX's main-thread rendering/input path (unverified [P]). That would push toward a split architecture (engine in a Worker or on a server, UI on the main thread), not necessarily away from the browser.
- Memory: `HostedMatch` already special-cases iOS to stay under the jetsam ceiling (V). Not a Stage B finding, but it is the more likely all-browser killer.

If a fallback is ever needed, the JVM control fixture from Stage B is already the seed of a server-backed original-Java engine.

## 4. Objective next-test criteria

- **Pass (Stage B baseline):** per PR #13 (genuine path witnesses, three runs, negative control, clean task cleanup), with any deviations from the B3 list labeled "adapted" and reported separately.
- **Inconclusive:** a decision with no reliable path evidence, or a null decision with no recorded deadline data.
- **Fail:** wrong class, no task entered, missing payment/stack/damage, deadlock, surviving task, or a state mismatch with the JVM control.
- **Harness gate:** do not modify Forge or TeaVM until the unchanged-compile diagnostics are archived. Any boundary patch is its own draft PR citing the exact failing symbol and chain it fixes.

## Sources

- PR #13 — https://github.com/rwZephyr/forge-adventure-web/pull/13 (scope doc: `docs/experiments/gate-2-stage-b-scope.md`, branch `codex/gate2-ai-scope`; evidence: `docs/experiments/evidence/stage-b-preflight-20261018.json`)
- PR #14 / `docs/COORDINATION.md` — https://github.com/rwZephyr/forge-adventure-web/pull/14
- Forge sources (fork master): `forge-ai/.../AiController.java`, `forge-core/.../AITimeoutTracker.java`, `forge-core/.../ThreadUtil.java`, `forge-game/.../ApiType.java`, `forge-game/.../Game.java`, `forge-gui/.../HostedMatch.java` under https://github.com/rwZephyr/forge-adventure-web
- TeaVM 0.15.0 classlib: `TThread`, `TObject`, `TSystem`, `TThreadLocal`, `TRuntimeException`, `TExecutionException` under https://github.com/konsoletyper/teavm/tree/0.15.0/classlib/src/main/java/org/teavm/classlib
- TeaVM docs: https://teavm.org/docs/runtime/coroutines.html , https://teavm.org/docs/runtime/java-classes.html , https://teavm.org/jcl-report/recent/jcl.html
- Precedent for external classlib extension: https://github.com/m-m-m/tvm
