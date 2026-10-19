# Safari trial preparation — listener OFF

Prepared under [Waypoint's conditional owner authorization](https://github.com/rwZephyr/forge-adventure-web/pull/28#issuecomment-6279227053)
on `codex/browser-bolt-manual`, starting at PR #28 head
`87a063749d67c2585ebd3431f0187f60b273cdd0`. This is preparation, **not an executed
Safari session**. START has not been received. No listener or Safari countdown
was started; the old loopback authorization remains spent.

## Verified preparation and minimal changes

Preflight found a clean working tree, the existing JDK **17.0.12**, portable
Maven 3.9.16/cache, **100.122.229.121 assigned to the Up Tailscale adapter**,
port 8765 free, and `.local-tools/browser-bolt/safari-1` unused. These must be
rechecked immediately before any launch. Local address presence does not prove
phone reachability; no listener-based reachability test was performed.

Only manual timing changes were made:

| Budget | Previous | Prepared value |
| --- | --- | --- |
| Manual interaction | 10 minutes | **25 minutes** |
| Terminal inspection | 2 minutes | **5 minutes** |
| Manual TestNG method | 15 minutes | **35 minutes** |
| Owned launcher, listener mode | 20 minutes | **40 minutes** |
| Owned launcher, listener-free verification | 20 minutes | **20 minutes, unchanged** |

The Java test has three numeric substitutions; the PowerShell launcher selects
40 minutes only for the existing approved-listener mode. Failure polling,
15-second EDT dispatch budgets, original checkpoint/C1/authorization, graph
checks, input handling, cleanup and security policies are unchanged. Scripted
tests, host/policy-test source, client/static UI, dependencies, POMs, original
Forge main source and PR #27 are unchanged. The known status-label defect is
not corrected under this timing-only scope.

Actual focused verification, offline and listener-free:

```powershell
./experiments/browser-bolt/Run-Manual.ps1 -Label safari-prep-policy-1 -VerifyOnly -Check policy
```

Compilation succeeded; **16 policy cases PASS**, zero failures/errors/skips;
Maven/owned driver exit **0**. All six parent environment values and the run's
**2,086 protected hashes / 2,393 cache files** stayed unchanged. The authorized
timing edits happened before the before-inventory. PowerShell AST parse passed;
an exact source comparison verifies only the expected timing substitutions and
unchanged transport/browser/historical test files. Normal scripted gameplay
was not rerun. No 25-minute endurance, real Safari behavior, runtime expiry or
new listener shutdown is claimed.

[Sanitized logs, fresh reports, source comparison and inventory delta](../../experiments/browser-bolt/evidence/safari-preparation/manifest.json)
preserve the focused check. Private complete inventories/originals remain local;
the published inventory is reconstructed from the previous full inventory and
the two explicitly changed source hashes. Postflight found no Java process or
port-8765 listener, and `safari-1` remains unused. No installations, downloads,
Tailscale Serve/firewall/router/service modifications or merges.

## READY → START → LIVE

**READY TO LAUNCH means listener OFF.** There is no time pressure while waiting
for the owner. The owner confirmed that they can read Silent's screen and
manually type the **43-character** session capability into Safari. Remote
clipboard is not relied upon or claimed tested. No actual Safari capability
exists yet; it will be generated only after START.

Owner preparation before sending START:

1. Have the iPhone available, **Tailscale enabled** and Safari ready, preferably
   a fresh Private tab. Keep access to Silent's screen for the private code.
2. Read the code from Silent after LIVE and type it **only** into Safari's
   **Private session capability** field. It is case-sensitive and can contain
   letters, digits, hyphen and underscore; type all 43 characters with no spaces.
   No chat/GitHub transfer, URL token, Notes copy, log or code screenshot.
3. Send **START** when available to perform the test. Do not open the target
   expecting a response before the LIVE notification.

After explicit START, Codex rechecks address/port/label/source, then executes
**once**, with no substituted label or automatic retry:

```powershell
./experiments/browser-bolt/Run-Manual.ps1 -Label safari-1 -ApproveListener -BindAddress 100.122.229.121 -Port 8765
```

Only `100.122.229.121:8765` may listen. No wildcard, LAN/public address or
Tailscale Serve/other service reconfiguration is permitted. If the address is
absent, port occupied, or a firewall/network change appears necessary, stop.

Codex keeps the exact launcher running in its retained execution session and
reads the existing private console and OS listener table concurrently. Readiness
requires the original `listener=started` diagnostic, the exact bound address,
the first original browser frame and capability-file existence (contents never
printed). This is the same operational communication method demonstrated in the
loopback session; no new listener, watcher service or timing instrumentation is
introduced. Codex sends **LIVE** in chat while continuing to supervise the run.
If this communication/observation path fails, stop rather than leave an
unsupervised listener.

LIVE supplies this URL and the private local file path:

`http://100.122.229.121:8765/experiments/battlefield-static/live.html`

`.local-tools/browser-bolt/safari-1/capability.txt`

The code stays in that ignored file and the private Safari entry path; Connect
clears the field, and the original page uses its in-memory Authorization header.
Close or cover the code viewer before capturing any evidence. No agent should
display or log the file contents.

## Timing and the owner's actions

Initial Maven/FModel bootstrap happens **after START** and may take a few
minutes. It precedes the 25-minute interaction clock. That clock starts inside
the manual test **immediately after `match.startMatch(...)` returns**, after
the HTTP host starts, shortly before the first original prompt/frame. It does
not wait for Safari to open or Connect. LIVE reports approximate remaining time
when readiness is observed. A separate five-minute inspection clock starts
when the owner loop recognizes Forge's resolved terminal result.

The owner personally performs:

1. Open the URL after LIVE, enter the private code, tap **Connect**.
2. Tap **Cast Lightning Bolt**, wait for the target decision.
3. Tap **Target opponent**, wait for the red payment decision.
4. Tap **Activate Mountain** manually, then inspect tapped Mountain/Bolt on stack.
5. Tap **Pass priority**, inspect opponent **17**, player **20**, empty stack
   and Bolt in graveyard. After that result is recorded, tap **Finish test
   inspection** within five minutes.

Codex guides one action at a time and observes the original JVM evidence; it
does not click gameplay actions for the owner. The current heading, enabled
buttons and revision identify the current decision; the known intermediate
“Connecting/Waiting” status text can remain stale. Do not choose Auto/OK,
reload/reconnect to recover an unknown submission, or interpret closing Safari
as a gameplay answer. Report trouble; do not improvise a workaround.

Terminal inspection and success-only harness concession remain distinct from
gameplay. On failure/expiry/abort, no concession, pass, answer or latch release
is supplied for cleanup. Preserve the failed run and stop; no second session is
authorized. Existing operational timeout limitations remain: native/HTTP/CIM
operations are not given an unconditional wall-clock guarantee.

After completion, collect the original oracle, available Safari/user evidence,
exact outcome/shutdown, fresh TestNG reports, process/port observations and
integrity/environment results. Verify listener and owned JVMs have exited.
Keep #28 draft/unmerged and #27 on HOLD, then return for review.
