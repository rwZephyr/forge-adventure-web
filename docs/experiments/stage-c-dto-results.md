# Stage C: DTO interaction proof and failure-cleanup blocker

2026-10-18 (owner's local date). Stacked on PR #21 at
`3c3aa18df2f160dc2b6dce2279d73e2bb7fb1aa8`.
**The bounded DTO Bolt sequence passes in three fresh JVMs. Acceptance still
requires review of an observed failure-cleanup concurrency issue.** No browser,
transport, production runtime change or merge is included.

## Actual verification

[Run index](evidence/stage-c-dto-20261018/runs.json) and
[complete sanitized logs/reports](evidence/stage-c-dto-20261018/manifest.json):

| Run | Actual result |
| --- | --- |
| `capture-gate` | Test failed before capture fingerprint/projection on a consumed payment refresh; subsequent original cleanup produced overlapping Game-0/Game-1 graph callbacks. Both failures are preserved. |
| `capture-gate-2` | Capture gate passed the original human sequence after moving the hook behind the existing consumed-payment filter; no retrying a graph walk or changing synchronization. |
| `dto-development` | Earlier DTO version passed; excluded from the final three-run count. |
| `dto-final-1` | Compilation failed: Forge's target-player collection is Iterable, not a stream. No test executed. Stale prior Surefire reports copied by the initial launcher are excluded from publication/counts; the launcher now copies only fresh reports. |
| `dto-final-2`, `dto-final-3`, `dto-final-4` | **3/3 fresh positive JVMs**, each 1 pass, zero failures/skips and exit 0, all on final test-source SHA-256 `4a98d6d6dcdbed15f50206065ce54200cab92e530b8d9d1630b3e89f741daf16`. The latter two ran concurrently in independent processes/isolations using the same compiled classes. |
| `unanswered-final` | New DTO unanswered-required-question control passed by proving the owning TestNG invocation received the exact atomic failure artifact; no cast/payment credited. |
| `regressions-final` | **6/6 passed**, zero failures/errors/skips, exit 0: original human Bolt, AI Bolt, empty-hand, unanswered control, historical in-process baseline and unchanged SingleThreadTest. |

The positive oracle reads original Forge objects: one Bolt targets the opposing
player at life 20; `getPayingMana()` has one RED from the actual manually clicked,
tapped Mountain; original stack resolution produces life 17, graveyard Bolt and
empty stack. The peer also receives payment, stack and resolved DTO frames.
No damage application, prepaid mana, direct target insertion, resolver call,
AI replacement or fabricated SpellAbility is added.

## Capture checkpoint and the blocker

Implementation was ordered: checkpoint instrumentation and its standalone run
preceded DTO code. The passing fixture checkpoints require:

- Original Game loop is WAITING in CountDownLatch.await, with stored original
  latch count 1; current InputProxy and InputQueue refer to the expected input.
  Fixture readiness is published, and prompt display/Selection has returned
  before the driver's next EDT runnable executes.
- The original cached game executor has only that parked loop active, an empty
  queue, unchanged submitted/completed task counts across capture, all other
  live Game workers idle in the original executor, and no live Game AI Eval.
- Tracker is unfrozen; the existing graph-operation overlap guard also covers
  capture/projection. Repeated private engine/view fingerprints agree on phase,
  life, card zone/tap state, mana pool, stack count/targets/payment, and the
  active cost. The original input and latch remain parked afterward.

These are **closed-fixture checks**, not a general graph lock. EDT alone is not
used as the safety argument. Pool counters are approximate diagnostics, not a
replacement for ownership; the source-level argument also depends on the test
driver being the only input producer, the original loop being latched, known
worker completion and no active hosting/AFK producer. Original
[input publication/latch](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/input/InputSyncronizedBase.java#L38)
and [payment worker completion](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui/src/main/java/forge/gamemodes/match/input/InputPayMana.java#L332)
remain unchanged. Original input publication supplies the existing happens-before
edge; the guard does not make arbitrary mutators participate in a new lock.

**Unresolved failure path:** in the first gate run, the hook was incorrectly
placed before the already-submitted-payment refresh filter. Its quiescence
predicate failed while Game-1 payment was still in progress. No fingerprint or
DTO was read at that failing refresh. The existing test's `finally` then issued
its cleanup concession while that worker was still active. The log records
`updateGameView: concurrent original graph operations` between Game-0 and
Game-1 during this cleanup. The first-wins atomic artifact preserves the first
failure; the secondary graph failure is in the full log and must not be hidden
by the primary assertion or later successful runs.

This secondary issue was identified during full publication-log review, after
the final runs. **Implementation/testing stopped on discovery.** No production
synchronization, deferred cleanup, retry or thread adapter was introduced.
The initial placement correction only discarded consumed callbacks before
capture, as the existing script already did before a second payment click.
It does not resolve failure cleanup. Review this observed overlap before
accepting the slice or advancing to Safari. The smallest proposed follow-up is
a separately reviewed test-only failure policy that records owner receipt
without issuing an in-game concession while a worker is active; no implementation
of that policy is included here.

## DTO and original-controller boundary

Only the [existing embedded test](../../forge-gui-desktop/src/test/java/forge/net/embedded/ProtocolGuiGameInProcessTest.java)
changes among Java sources. Test-only nested records contain viewer-specific
players/life, visible card faces, turn/phase, actual stack entries/targets,
pending spell source/selected targets, remaining mana and original legal player
choices. Lists are copied and recursively checked immutable; the allowed type
graph contains only the declared DTO records, primitives/strings and lists.
The static peer receives a Frame and returns typed Action values; it has no
Forge object, controller, projector or private-oracle reference.

Raw GameView, DeltaPacket, GameEvents, CardView/SpellAbilityView objects, library
contents, opponent hand and hidden face IDs stay inside Java. Projection uses a
fixed non-null human viewer, explicit zones and both original visibility checks;
unsupported face-down cases fail closed. New random handles are issued each
frame and resolve only through the current EDT-confined registry. Both players
are public; own hand and public battlefield/graveyard/stack faces are copied.
No hidden placeholders, counts, raw prompts, descriptions or image keys are sent.

The contract starts at the prepared MAIN1 fixture. The original explicit
Play/Keep bootstrap remains test setup outside this peer; this is not general
match-start GUI support. Only the fixture's cast, player target, manual Mountain
activation and ordinary priority-pass capabilities are supported. The pending
cast's card can already have upstream zone Stack during costs; the separate
Frame.stack list remains empty until Forge actually adds the spell to its stack.

C2–C6 are enforced in this test boundary:

- Immutable authorization is published in an AtomicReference before queuing
  dispatch. On EDT, Game-0 and Game-1, required singleton replies must be the
  exact C1-authorized views and live controller-cache values by reference.
  A separate atomic counter consumes exactly the three audited questions.
- Admission reads immutable metadata only. Final live handle lookup, visibility,
  input/generation checks and original controller call run in one EDT runnable,
  with no suspension/queue hop between them. Unexpected questions or dispatch
  errors record failure rather than returning null/defaults.
- Typed Payload compares `type, version, match, session, revision, prompt, choice`;
  the trusted server-side session context owns the action-ID ledger. Exact pending
  and terminal duplicates share the original future; a changed payload for the
  same ID is `actionIdConflict`, distinct from `staleDecision`. Acceptance means
  controller dispatch, not successful casting; original state supplies that proof.
- Mountain is selected through the original human controller, never Auto/OK
  during payment. Source/color/tap assertions use original payment records.

## Negative controls and limits

The private malicious injector, separate from the peer, checks wrong trusted
context/session, invalid operation, stale revision/prompt, unknown handles,
hidden opponent-card ID submitted as a choice, and a public display handle
submitted without a choice capability. Hidden and unknown choices get the same
generic rejection. Each rejection makes zero controller calls and preserves
the private state fingerprint and original parked input/latch.

Held dispatch tasks test real queue boundaries: revoke the live entity handle
on EDT before dispatch, or republish a newer decision while the original input
stays parked; the later EDT runnable rejects without advancing Forge. Resync is
an authorized cached DTO read with no graph traversal, prompt replacement or
latch release. Pending and terminal duplicate submissions do not dispatch twice.
The separate unanswered control checks atomic recording before propagation and
exact owner receipt, not a swallowed background exception.

This is a typed Java DTO test, **not** hostile JSON parser validation, network
authentication, reconnect, a production service, creature targeting, Adventure,
or an actual Safari test. It deliberately does not provide every legal action
of a general Forge GUI. Historical observer modes/source were untouched; C1's
line-pinned JDI mode was not rerun against this extended test source. Successful
checkpoint observations do not establish safe arbitrary graph traversal or
safe failure cleanup.

## Reproduction, integrity and publication

The committed [scoped launcher](../../experiments/embedded-dto/Run-Scoped.ps1)
uses existing JDK 17 and portable Maven with `-o`, the repository-local cache,
fresh home/temp/AppData/report paths and explicit test selection. Before launching
a JVM it proves restoration of absent/present process variables, then verifies
presence and exact value equality for all six real environment variables after
the run. It logs only names/presence/equality, not their original values.
**All nine launcher invocations passed this restoration check.** PR #21's
historical `environmentRestored:false` record/hash is unchanged.

Actual commands from repository root:

```powershell
./experiments/embedded-dto/Run-Scoped.ps1 -Case capture -Label capture-gate -Maven
# Placement correction; preserve the failed run.
./experiments/embedded-dto/Run-Scoped.ps1 -Case capture -Label capture-gate-2 -Maven
./experiments/embedded-dto/Run-Scoped.ps1 -Case dto -Label dto-development -Maven
./experiments/embedded-dto/Run-Scoped.ps1 -Case dto -Label dto-final-1 -Maven
# Compile correction; preserve the compilation failure.
./experiments/embedded-dto/Run-Scoped.ps1 -Case dto -Label dto-final-2 -Maven
./experiments/embedded-dto/Run-Scoped.ps1 -Case dto -Label dto-final-3
./experiments/embedded-dto/Run-Scoped.ps1 -Case dto -Label dto-final-4
./experiments/embedded-dto/Run-Scoped.ps1 -Case unanswered -Label unanswered-final
./experiments/embedded-dto/Run-Scoped.ps1 -Case regressions -Label regressions-final -Maven
```

The launcher rejects reused directories. These commands document completed
execution, not authorization to rerun during review. The non-Maven launches
reuse the classpath from the normal successful reactor build. TestNG retains
its 180-second test timeout. No extra historical test or observer mode was run.

[Integrity](evidence/stage-c-dto-20261018/integrity.json): all 2,393 cache files
are byte-identical to the C1 snapshot; only the approved test differs among
the checked original source/config/card inputs. Git confirms production sources
and POMs unchanged; SingleThreadTest and JdiWitness retain their prior hashes.
No Java process remains. Generated outputs/private profiles are not committed.
The manifest records original/published hashes; full logs/test XML are sanitized
for absolute paths, hostnames, VM lambda addresses and Surefire system properties.
Next step is independent review of this proof **and its cleanup blocker**.
Minimal actual Safari connectivity follows acceptance and separate server approval.
