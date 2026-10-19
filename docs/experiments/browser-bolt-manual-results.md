# Manual browser Bolt: implementation and offline verification

This report preserves the original offline verification at `bc0d5257`.
The subsequently authorized **single** browser session is recorded separately
in [loopback results](browser-bolt-loopback-results.md); original logs below
are unchanged. Statements about no listener execution describe this historical
offline phase.

Waypoint's bounded integration direction is implemented on
`codex/browser-bolt-manual`, based on PR #24 at
`55164bf309e1a0e38997c206762dc3a1c2635d81`. Existing drafts stay unmerged.
PR #27 and its launcher/safety gate are not a base or dependency and remain
unchanged/on execution HOLD.

**No listener, browser session or real iPhone test has run for this work.**
This report separates compiled implementation from listener-free verification;
the first connected Bolt sequence remains subject to separate owner approval.

## Changes and original integration points

- `forge-gui-desktop/src/test/java/forge/net/embedded/ProtocolGuiGameInProcessTest.java`:
  distinct `manualBrowserBoltSession` / `MANUAL_BROWSER` path beside the accepted
  scripted peer. Original `DtoPort` implementation, fixture checkpoint, C1
  replies, admission/dispatch, scripted tests and PR #23 `finishCleanly` policy
  are preserved. Only the DTO record visibility becomes package-local for the
  test-only transport. No main-source, POM, rules, AI or card change.
- `ManualBrowserBoltHost.java` in that package: JDK HTTP host with an injectable
  safe DTO facade, strict codec/auth/origin/route policy, cached-frame reads,
  typed submission/receipt polling and separate harness abort/finish signals.
  Constructor and policy handlers cannot listen. Only explicit `start`, gated
  by a separate approval property and isolated output path, can bind.
- `ManualBrowserBoltHostTest.java`: direct policy/codec tests, not a simulated
  Forge match. Synthetic DTOs/futures verify auth, input rejection and receipt
  mechanics without sockets or FModel. Their synthetic acceptance is **not**
  claimed as gameplay evidence.
- `experiments/battlefield-static/live.html`, `live-client.js`, shared renderer
  and scoped CSS: minimal polling peer using only the established DTO shape.
  The original static page/snapshots and preview path are preserved. No
  optimistic life/tap/stack changes, remote assets, storage, cookies or framework.
- `experiments/browser-bolt/Run-Manual.ps1`: explicit verification versus
  separately approved listener modes; offline reactor, isolated child paths,
  owned-driver supervision, hashes, fresh reports and parent-environment checks.
  `verify-live.cjs` is a syntax-checked future approved-session helper only.

The HTTP facade binds authorization on the server. Client-supplied match/session
values cannot authenticate. Every API needs the freshly generated 256-bit
capability; it is held in an ignored private file and browser memory and sent
only as a Bearer header. No access logging, query strings or secret echo. Exact
Host/Origin checks, no CORS, bounded strict JSON, allowlisted static routes and
defensive response headers limit the private test's exposure. No production
security or slow-client hardening claim is made.

## Human delay, capture and cleanup policy

The owner polls failures every 100 ms while waiting for a person. Interaction
and terminal-inspection budgets are ten and two minutes; TestNG has fifteen
minutes and the driver twenty. EDT work retains a fifteen-second dispatch
budget and is never blocked waiting for HTTP or a human. A timeout records abort
before any queued EDT work can pass its original failure recheck.

Incoming prompt callbacks trigger the original fixture checkpoint, not polling
graph traversal. Refreshes of a published original input do not create new
frames/capabilities; stale callbacks and consumed payment inputs hit the original
pre-read refusal. Cached reads and receipt queries never traverse the graph.
This does not generalize capture ownership to production Forge or other decks.

The terminal original-game oracle must verify opposing-player targeting,
Mountain's one red payment/tapped state, stack and resolution, opponent 20→17
and human Bolt in graveyard. Terminal choices are revoked before browser
publication. The terminal frame stays visible until inspection finishes or
expires. Finish is a separate harness acknowledgement. Transport closes before
the unchanged success-only cleanup concession; failure/abort/expiry never
concedes, replies, auto-passes or releases an original latch.

Outcome and shutdown are independent owner-side fields. The UI never credits
cleanup as browser gameplay. Unknown network/submission outcomes stop its
controls without automatic retries or default actions. No reconnect feature.

## Verification

Actual commands, results and sanitized evidence are recorded below. All Maven
runs use the existing JDK 17, Maven 3.9.16 and offline `.local-tools/m2` through
the [scoped runner](../../experiments/browser-bolt/Run-Manual.ps1).

| Fresh label / check | Actual result | What it establishes |
| --- | --- | --- |
| `policy-1 / policy` | 16 PASS, 0 failures/errors/skips; Maven exit 0 | New Java compiles; direct auth/codec/receipt/route and lifecycle-state policy checks, no listener |
| `dto-1 / dto` | 1 PASS, exit 0 | Preserved scripted DTO Bolt, exact C1 replies, payment/stack/resolution and invalid/stale/hidden/revoked/duplicate controls |
| `unanswered-1 / unanswered` | 1 PASS, exit 0 | Deliberate required-question failure received by owner with exact artifact/cause assertions; no cast/payment and zero cleanup concessions |
| `regressions-1 / regressions` | 6 PASS, exit 0 | Existing human/AI Bolt, empty-hand/unanswered controls, historical in-process and single-thread cases |

The exact four commands are in the [runbook](../../experiments/browser-bolt/README.md).
[Complete sanitized console/output logs and fresh XML/text reports](../../experiments/browser-bolt/evidence/manifest.json)
preserve the actual outcomes. The [shared full inventory](../../experiments/browser-bolt/evidence/inventory.json)
contains **2,086 protected file hashes and 2,393 cache hashes**. All eight
before/after inventories are byte-identical; the manifest links their private
original hashes/sizes to this deduplicated publication. All four run results
record six exact parent-environment matches, no source/cache changes, driver
exit 0, no forced termination and no secondary finalization failures.

The [source comparison](../../experiments/browser-bolt/evidence/source-comparison.json)
checks original DTO port and fixture capture/failure-policy sections, historical
single-thread test, static index and snapshots against #24 after LF normalization.
The [postflight](../../experiments/browser-bolt/evidence/postflight.json) found
zero Java processes and zero listeners on port 8765 at that instant; it does not
claim continuous descendant supervision. PR #27's local head remains
`cdda588e449754352790f7bb02c065fe881a7bed`.

Syntax checks use the already available Node `--check` for `battlefield.js`,
`live-client.js` and `verify-live.cjs`; PowerShell AST parsing checks the scoped
runner without invoking it in listener mode. The original fixture extractor's
`--check` verifies all five historical snapshots. These are not rendering or
connected browser results.

Publication keeps the private originals. It removes complete Surefire system
property blocks, substitutes workspace/JDK/user paths and machine hostnames,
normalizes UTF-8/LF/trailing whitespace and VM lambda suffixes, and records
original/published hashes and sizes in the manifest. Published JSON/XML parses;
credential/private-path pattern checks pass. No downloaded JARs/POMs, compiled
outputs, generated homes, private configuration or capability files are committed.

## Remaining risks and next bounded step

The new manual owner loop, actual HTTP exchange/shutdown, a person's long think
time, connected renderer and Safari behavior are not exercised by these offline
checks. They must not be described as a successful browser duel. The capture
checkpoint remains fixture-specific and fails closed on original invariants;
this implementation must stop on a graph overlap, unsafe refresh or unexpected
synchronous question rather than weaken those checks.

The process driver reports retained root exit and forced-tree requests; it does
not independently observe every descendant's exit, and neither native process
operations nor blocking HTTP reads provide an unconditional wall-clock bound.
No PR #27 safety guarantees are reused or claimed. No general authentication,
multi-match isolation, campaign state, persistence or production adapter exists.

Next: review this implementation and evidence, then separately authorize one
desktop loopback session using the exact proposed command in the
[runbook](../../experiments/browser-bolt/README.md). Only after that passes should
actual iPhone Safari over Tailscale be considered, under another explicit
listener approval. No listener, browser, network/service change, deployment or
merge is authorized by this report.
