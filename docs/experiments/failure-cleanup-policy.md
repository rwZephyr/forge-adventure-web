# Test-only failure cleanup: review gate

2026-10-18. Stacked on [PR #22](https://github.com/rwZephyr/forge-adventure-web/pull/22)
at `a8c7e0dde0882b27770844500a50253ede39fd90`, following
[Marvin's review](https://github.com/rwZephyr/forge-adventure-web/pull/22#issuecomment-6258639071).
**Source proposal only: no compilation, tests or new runtime results.** The three
successful DTO runs, regressions and original failed gate remain unchanged.

Only `ProtocolGuiGameInProcessTest.java` changes among executable files. No
production synchronization, observer, launcher, dependency or fixture-content
change. The historical in-process test and `ProtocolGuiGameSingleThreadTest`
are unchanged.

## Exceptional control flow

Marvin proposed gating the existing `finally` on `failure.get() == null`.
This correction removes the mutating `finally` entirely. `BoltScript.run()`
catches `Exception | Error`, keeps that exact unwinding object in a local
`primary`, records an abort and rethrows **the same object**. It performs no
game lookup, assertion, concession, reply, input release or game-over wait in
that catch. Unexpected owner exceptions are covered, including start/setup,
queue waits, DTO work and success assertions. A secondary thread's earlier
artifact cannot replace an already-unwinding owner exception; its cause is
attached as suppressed evidence instead.

A single atomic immutable `CleanupState` publishes the first cause and optional
`FailureArtifact` together. The artifact read sites use a read-only supplier of
that state. This avoids a gap between publishing the cleanup veto and the
unanswered-control artifact. Unexpected EDT-action/DTO-dispatch exceptions
record their actual Throwable without manufacturing a replacement assertion.
`checkFailure()` delivers an asynchronous assertion with its original cause to
the owner; the unanswered control still checks the exact artifact/error
reference. Subsequent failures are retained separately and, when available at
owner catch, suppressed on the delivered primary.

Failure diagnostics use the **last observed input identity**, explicitly labeled
`cachedInput=true`; they do not rediscover the input by traversing an uncertain
game graph. This cached value is diagnostic context, not a new identity claim.
Queued action/projection entry points and synchronous replies check the abort
state before continuing. No failure path retries or fabricates an answer.

The outer preference/random restoration attempts all four original restorations.
Each secondary exception is suppressed on an unwinding primary. If the test
body was successful, restoration failure remains a test failure. No assertion
is removed or converted into a pass.

## Clean termination and queue arbitration

The former early concession in `act()` also moves behind all owner-side success
assertions. Only normal fall-through reaches `finishCleanly()`:

1. Check for failure before queueing a bounded EDT task, then again on EDT.
2. Require the completed scenario. A naturally finished AI game needs no
   concession. An active game must have the original terminal priority input
   and pass the **unchanged** `captureCheckpoint`: original latch parked,
   only that loop active, other workers idle, graph guard, tracker and repeated
   engine/view checks. EDT affiliation or an empty failure field is insufficient.
3. Resolve the original controller, then CAS the same atomic state from clean
   to committed immediately before its one `concede()` call. An abort that wins
   first revokes even an already-queued success task. No test lock is held over
   Forge, and failure publication never waits for a controller to return.
4. Check failure while awaiting game over, with a ten-second bound; no second
   concession or input release is attempted if termination fails.

The owner uses a ten-second non-interruptible polling envelope for the queued
task. An observed interrupt or deadline publishes its abort **before throwing**;
it does not first unwind from `Future.get()` with a live queued permit.
`get()` is used only once the task is completed, and its `ExecutionException`
is unwrapped to preserve the actual EDT exception.

| Interleaving | Proposed result |
| --- | --- |
| Assertion already recorded | No success task queued; original cause delivered. |
| Unexpected owner exception without artifact | Catch retains exact Throwable; no cleanup task. |
| Failure after queueing, before concession admission | Abort wins CAS; queued task cannot concede. |
| Checkpoint rejects unsettled workers | Task records failure; no concession, retry or fallback. |
| Concession itself throws | Preserve that exception; never attempt another game action. |
| Preference restoration throws during failure | Suppress on primary and attempt remaining restorations. |

Admission is the linearization point for the **start of** a clean termination
action. A later error cannot undo an already-admitted controller call. It
revokes subsequent work and remains observable; this policy does not promise
to interrupt or roll back Forge inside `concede()`. This distinction, including
the admission/call boundary, requires Marvin's review before execution.

## Bounded isolated-process exit

Failure deliberately leaves original waiters/inputs untouched. It does not
join, interrupt or shut down Forge workers, and does not call `System.exit`
from a test. An abandoned match must end with its **isolated test JVM**, not be
reused for another scenario in that JVM. Any future failure-path verification
must select one case in the existing isolated launcher, rather than the
multi-method regression selection. No run is authorized by this document.

Source/bytecode inspection, without executing tests, establishes:

- The existing scenario deadline remains 120 seconds; the TestNG annotation
  remains 180 seconds. Normal termination adds at most two ten-second owner
  waits; failure does not enter either wait.
- [ThreadUtil](../../forge-core/src/main/java/forge/util/ThreadUtil.java) creates
  the original Game/Delayed workers as daemon threads. Parked inputs need not
  be released to let the runner terminate its process.
- The cached TestNG 7.10.2 `org.testng.TestNG.main` calls `privateMain`, obtains
  `getStatus()` and calls `System.exit(status)` after reporting. Thus AWT EDT
  survival alone cannot keep that normally completed CLI process alive.
- The cached Surefire 3.1.2 `ForkedBooter` likewise uses `acknowledgedExit`, a
  last-ditch shutdown task and `System.exit` after provider completion. Its
  process-exit code is not a substitute for the TestNG result reported to Maven.

These existing runner exit paths preserve TestNG's owner failure/report and
end all threads in the child JVM. No new process is launched by this correction.
The launcher does **not** itself implement an external PID watchdog or an
automatic post-run process inventory; PR #22's no-Java-remains check was a
separate observation. No claim is made that Java deadlines cover an arbitrary
VM/native/reporting hang. If the approved runner cannot return/report within
its existing timeout lifecycle, stop for review of an owned-process supervisor;
do not release game inputs, kill unrelated Java processes or silently reuse
the failed JVM. No such supervisor is implemented here.

## Verification and next gate

Performed only source/diff review, `git diff --check`, unchanged-file comparison
and read-only `javap -c -p` inspection of the two **already cached** runner
classes. No Maven, javac, test invocation, download, listener or environment
change. PR #22's evidence and the historical observer reports are untouched.

**Unverified:** compilation and runtime behavior of this correction, including
the revised success-only termination checkpoint. Existing positive evidence
continues to describe PR #22's source, not this source. Next: Marvin's independent
exception/interleaving/exit-policy review; then owner approval of a bounded
failure-path verification if warranted. No positive-path cosmetic reruns or
Safari work are included.
