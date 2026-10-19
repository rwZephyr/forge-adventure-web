# Browser-controlled Bolt · disposable manual fixture

This opt-in test host connects the accepted Stage C DTO boundary to the portrait
battlefield. It uses the original Forge JVM, controller, targeting, manual
Mountain activation, priority and resolution. It is **not a production adapter,
complete encounter or Adventure port**. Based on PR #24, commit
`55164bf309e1a0e38997c206762dc3a1c2635d81`; PR #27 is untouched and on HOLD.

The existing static `experiments/battlefield-static/index.html`, its recorded
fixtures and the scripted Stage C peer remain available. The separate
`live.html` page contains no fixture snapshots and never advances optimistically.
It displays only server-supplied allowlisted DTO frames. Original assets and
their provenance remain in `experiments/battlefield-static/assets.json`.

## Listener-free verification

Use the existing JDK 17, portable Maven and repository-local cache. Nothing is
installed or downloaded. From the repository root, use **fresh labels**:

```powershell
./experiments/browser-bolt/Run-Manual.ps1 -Label policy-1 -VerifyOnly -Check policy
./experiments/browser-bolt/Run-Manual.ps1 -Label dto-1 -VerifyOnly -Check dto
./experiments/browser-bolt/Run-Manual.ps1 -Label unanswered-1 -VerifyOnly -Check unanswered
./experiments/browser-bolt/Run-Manual.ps1 -Label regressions-1 -VerifyOnly -Check regressions
```

The policy tests call the host's request/codec methods directly; constructor
and policy tests never bind a socket. DTO and unanswered checks use separate
Surefire JVMs: the accepted unanswered control leaves its original input
parked until that fork exits. The regressions retain their accepted selector.
The manual TestNG method skips before FModel initialization unless explicitly
enabled. Maven is always offline (`-o`), with `-pl forge-gui-desktop -am test`,
an explicit test selector and `.local-tools/m2`.

Each run keeps complete logs, fresh reports, source/cache inventories and
`result.json` in `.local-tools/browser-bolt/<label>`. Child-only environment
overrides isolate home, temp and app-data paths; six parent variables are
compared afterward. The launcher retains its directly started PowerShell
driver process and uses its owned process tree only for forced termination.
It records checked exit waits and secondary finalization failures independently.
It does not use PR #27's launcher or CIM validation.

## First loopback session — proposed, requires separate owner approval

**Do not execute this command without the listener/session approval.**

```powershell
./experiments/browser-bolt/Run-Manual.ps1 -Label loopback-1 -ApproveListener -BindAddress 127.0.0.1 -Port 8765
```

Preflight requires that literal address to exist locally, port 8765 to be free,
fresh evidence paths and the already approved tools. An occupied port is a stop
condition; it never stops an existing service. Only `127.0.0.1:8765` listens in
this stage. No LAN, Tailscale Serve, firewall or network configuration changes.

After startup, open `http://127.0.0.1:8765/experiments/battlefield-static/live.html`.
Privately read `.local-tools/browser-bolt/loopback-1/capability.txt` and paste its
contents into the page's password field. **Never put the capability in a URL,
chat, screenshot, command argument, log or commit.** That ignored file inherits
the workspace's local access permissions; this is not a multi-user secret store.
The page clears the input, retains the capability only in memory and sends it
in the Authorization header. It uses no storage, cookies or service worker.

Cast Lightning Bolt → target the opposing player → activate Mountain → pass
priority. Wait for each new Forge prompt. Only original offered choices are
admitted. A receipt means controller dispatch, not successful spell resolution.
Forge's original oracle must confirm the target, one red mana from the tapped
Mountain, stack, opposing life 20→17 and Bolt in the human graveyard.

The terminal frame has no actionable prompt and remains visible for up to two
minutes. **Finish test inspection** is a harness acknowledgement, not gameplay.
The host closes before the unchanged PR #23 success-only cleanup concession.
That concession is never counted as a browser decision or damage evidence.

**Abort test**, expiry or a recorded failure supplies no gameplay answer,
concession or latch release. The owner continues observing failures while the
human thinks; no EDT wait is extended to a human-sized timeout. The interaction
budget is ten minutes, terminal inspection two minutes, TestNG fifteen minutes
and launcher twenty minutes. These are operational budgets, not unconditional
OS/native-I/O deadlines. Request body reads and platform process operations
can block; this disposable private host is not hardened against slow clients.

The owner must inspect **all** of `result.json`, fresh TestNG reports and
`manual-host-result.json`: the latter separates outcome
`PASSED / ABORTED / EXPIRED / FAILED` from shutdown `CLOSED / FAILED` and marks
`harnessCleanupIsGameplay:false`. `TERMINAL` alone is not a complete test pass.
On abnormal launcher exit, do not retry or terminate unrelated processes;
retain the evidence and review any surviving owned process. The launcher
records whether forced tree termination was requested, whether the retained
driver exited and whether port 8765 remains occupied. It does **not** claim
independently observed exit of every descendant JVM.

## Transport boundary

- JDK `HttpServer` only, two handler threads, HTTP polling; no new dependency,
  WebSocket, general file server, framework, auth service or reconnect.
- Only the login shell/scripts/CSS and four known skin assets are public.
  No repository root, profiles, capability file or raw Forge object endpoint.
- Every API requires a fresh server-generated 256-bit capability bound to this
  one fixture. Client match/session strings remain routing validation, not auth.
  Exact Host/Origin policy, no CORS, cross-site rejection, strict bounded typed
  JSON and query-string rejection precede submission. POST requires same Origin.
- `GET /api/state` reads one cached immutable frame; `POST /api/action` submits
  a typed action; `POST /api/receipt` polls its saved result. The existing port
  retains canonical duplicate handling, C1 exact-reference authorization, live
  EDT input/handle/visibility checks and same-runnable original dispatch.
- All refreshes of an already published input are ignored. The original stale
  input / consumed-payment filter runs before the original checkpoint/projector.
  This reuses **only the fixture-specific** checkpoint, not general graph ownership.
- An unknown submission outcome stops the client without automatic retry or
  pass. Closing the page does not answer a question. Session resumption is out
  of scope; expiry is an observable failure, not an invented default decision.

`verify-live.cjs` is an optional future Chromium automation helper, guarded by
`--approved-session`. It requires an independently approved running session and
existing browser/tools, and cannot start a listener or install anything. Only
its syntax has been checked. Human desktop validation comes first; actual
iPhone Safari over the literal Tailscale address is a later, separately approved
session. HTTP is plaintext at the application layer; loopback is local, and
any later Tailscale session depends on tailnet access control/encryption.

See [actual offline results and remaining risks](../../docs/experiments/browser-bolt-manual-results.md).
