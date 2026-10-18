# Temporary actual-iPhone Safari test — approval proposal

Prepared 2026-10-18 on `codex/safari-test-plan` before listener approval. The
owner subsequently approved one coordinated session, which is now completed
and stopped; see [actual results](safari-session-results.md). The original
proposal below is retained for reproduction, not standing startup permission.
This is an access/test plan, not a game change.

## Verified preparation

- PR #8 remains draft/unmerged at
  `57ed01d137e45ea2a304b64ef979402347cb0843`.
- PR #11 remains draft/unmerged at
  `226bb0fb6109a2a5f31b5dd931e8e1db3ac379c5`.
- Existing Tailscale 1.102.2 and Python 3.12.10 are installed. Silent has a
  preferred IPv4 address on its `Tailscale` interface. Actual private addresses
  and Serve configuration stay out of this public repository document.
- Read-only `tailscale serve status --json` shows an existing HTTPS 443 service.
  TCP 8765 is currently unused. Both checks must be repeated before startup.
- Retained built artifacts exist. SpriteGroup entry point, JavaScript, source
  map, license and treasure hashes match PR #8's recorded manifest. Factory
  artifact files match PR #11's recorded manifest. No rebuild is needed.
- Offline staging is complete: six recorded SpriteGroup hashes and all 526
  recorded factory hashes matched; all 924 copied files are byte-identical to
  their source artifact files. All three command blocks parse as PowerShell.
  Only the preparation block ran; startup/shutdown blocks were not executed.

## Proposed boundary

One foreground Python static-file server, TCP **8765**, bound to **only Silent's
Tailscale IPv4 address**. No wildcard, LAN, loopback or IPv6 listener is proposed.
One isolated directory contains copies of the built artifacts under
`/spritegroup/` and `/factories/{default,explicit,missing-amount}/`.
Never serve the repository root, caches, evidence directory, saves or other services.
Built debug sources/maps and original license files are included in these artifacts.

**No Tailscale Serve/Funnel mutation**, certificate installation, firewall rule,
ACL/grant, route, DNS, port forwarding, system dependency or persistent service.
Existing SillyTavern HTTPS 443 and its backend remain untouched.
Existing tailnet policy governs who can connect: this is not restricted to just
one iPhone. There is no application login or upload endpoint. Python provides
only temporary static GET/HEAD serving; directory listings are possible inside
this artifact root. All staged paths must be ordinary copies, with no symlinks
or junctions.

The URL is **HTTP over Tailscale**, whose device traffic is end-to-end encrypted
([Tailscale encryption](https://tailscale.com/docs/concepts/tailscale-encryption)).
Safari can display “Not Secure”; this does not establish an HTTPS secure browser
context. Source inspection finds no secure-context-only API required by these
bounded cases. Actual Safari startup remains unverified. This test will not
establish HTTPS/PWA, clipboard, persistent storage or full-game compatibility.
If Safari enforces HTTPS or access is blocked by existing policy/firewall, stop
and report the blocker. Do not change settings or accept a firewall allowance.

## Exact preparation commands (no listener)

PowerShell, repository root. These already ran during offline preparation;
**do not rerun them over the existing session**. They document how to reproduce
a fresh ignored local staging copy without replacing a directory. No private
address is hard-coded.

```powershell
$ErrorActionPreference = 'Stop'
Set-Location 'C:\Users\Silent\Projects\forge-adventure-web'
$repo = (Get-Location).Path
$session = Join-Path $repo '.local-tools/safari-test-20261018'
$web = Join-Path $session 'web'
$sprite = Join-Path $repo 'experiments/adventure-teavm/build/dist/js/webapp'
$factories = Join-Path $repo 'experiments/rules-teavm/build/web'
if (Test-Path -LiteralPath $session) { throw 'Session already exists; inspect it, do not overwrite.' }
foreach ($source in @($sprite, $factories)) {
    $items = @((Get-Item -LiteralPath $source)) + @(Get-ChildItem -LiteralPath $source -Recurse -Force)
    if ($items | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }) {
        throw 'Artifact contains a link/junction; stop.'
    }
}
New-Item -ItemType Directory -Path $web | Out-Null
Copy-Item -LiteralPath $sprite -Destination (Join-Path $web 'spritegroup') -Recurse
Copy-Item -LiteralPath $factories -Destination (Join-Path $web 'factories') -Recurse
```

Before starting, verify the copies against the original artifact manifests;
save any local configuration snapshots/logs outside `$web`. Do not publish
tailnet addresses, hostnames, peer information or the Serve snapshot.

## Exact startup commands — ONLY after approval

Use the existing local Python, foreground, for one coordinated test session.
Read-only Tailscale/network checks may need execution outside the tool sandbox;
that is not approval for a Windows firewall or Tailscale change.

```powershell
$ts = 'C:\Program Files\Tailscale\tailscale.exe'
$python = 'C:\Users\Silent\AppData\Local\Programs\Python\Python312\python.exe'
$web = 'C:\Users\Silent\Projects\forge-adventure-web\.local-tools\safari-test-20261018\web'
$bind = (& $ts ip -4).Trim()
if ($LASTEXITCODE -ne 0 -or $bind -notmatch '^100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])\.\d{1,3}\.\d{1,3}$') {
    throw 'Could not verify Tailscale IPv4 address.'
}
$adapter = @(Get-NetIPAddress -InterfaceAlias 'Tailscale' -AddressFamily IPv4 -ErrorAction Stop |
    Where-Object { $_.IPAddress -eq $bind -and $_.AddressState -eq 'Preferred' })
if ($adapter.Count -ne 1) { throw 'Address is not the preferred Tailscale interface address.' }
$listeners = @(Get-NetTCPConnection -State Listen -ErrorAction Stop)
if ($listeners | Where-Object LocalPort -eq 8765) { throw 'Port occupied; do not stop its owner.' }
$serveBefore = (& $ts serve status --json | Out-String).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Could not snapshot existing Serve configuration.' }
if (-not (Test-Path -LiteralPath "$web/spritegroup/index.html")) { throw 'Missing SpriteGroup artifact.' }
foreach ($variant in @('default', 'explicit', 'missing-amount')) {
    if (-not (Test-Path -LiteralPath "$web/factories/$variant/index.html")) { throw "Missing $variant artifact." }
}
& $python -u -m http.server 8765 --bind $bind --directory $web
```

Python's documented [`--bind` and `--directory` options](https://docs.python.org/3.12/library/http.server.html#command-line-usage)
limit the listener and served directory. After startup, read-only
`Get-NetTCPConnection -LocalAddress $bind -LocalPort 8765 -State Listen` must show
exactly that address/port. Record its owning PID. If the listener differs, stop
only this process immediately. No automatic fallback to another interface/port.

## Phone actions and acceptance

Enable Tailscale on the actual iPhone and open **Safari**, using the private
base URL `http://<Silent Tailscale IPv4>:8765` (include `http://` explicitly).
First attempt over cellular with Wi-Fi off to distinguish tailnet reachability
from ordinary home Wi-Fi. Record model, iOS version, Safari version/UA, time,
orientation and any loading/errors. A cached page is not evidence of access.

1. `/spritegroup/`: two original treasure sprites must visibly render. Touch-drag
   each past the other vertically, demonstrating both draw orders and a return;
   input must release without sticking. Rotate portrait → landscape → portrait
   and drag again; reload must reset. Capture screenshots/screen recording,
   visible status/errors and diagnostic JSON if available. No fixed desktop
   pixel coordinates or desktop frame-time requirement applies to the phone.
2. `/factories/explicit/`: **15/15**, all concrete classes and parsed fields match
   the JVM record; no uncaught errors. Reload twice for three observations.
3. `/factories/missing-amount/`: **14/15** on three observations; only K2 fails,
   returning `SimpleKeyword` with amount 1. “FAIL” here is the required successful
   detection of silent fallback, not a request to repair original Forge classes.
4. `/factories/default/`: record the actual result on three observations;
   expected **5/15** with missing constructors and silent fallbacks. Compare the
   full case JSON against the JVM using the existing Stage A comparator, not
   merely the summary count. If phone evidence cannot provide raw JSON, record
   the limit and keep the exact-comparison result pending.

After disabling iPhone Tailscale while still on cellular, request each URL with
a fresh query suffix never previously loaded, such as `?tailnet-off=<timestamp>`:
the server must receive no new request and fresh retrieval must fail. A browser
cache display is inconclusive. Re-enable Tailscale for any remaining tests.
Connectivity failure is a hosting blocker; visible/JSON mismatches after loading
are Safari test failures. Report each gate separately and never infer an actual
Safari pass from Chromium, server logs or successful initialization alone.

## Shutdown and verification

Press **Ctrl+C in the foreground server session** immediately after the tests,
or if the coordinated session is abandoned. Retain logs/evidence locally.
Then, in the same PowerShell session:

```powershell
$remaining = @(Get-NetTCPConnection -State Listen -ErrorAction Stop |
    Where-Object LocalPort -eq 8765)
if ($remaining.Count -ne 0) { throw 'Listener remains; inspect its PID before stopping anything.' }
$serveAfter = (& $ts serve status --json | Out-String).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Could not verify Serve after shutdown.' }
if ($serveAfter -ne $serveBefore) { throw 'Serve differs; report it, do not reset or restore globally.' }
```

If Ctrl+C fails, stop only the recorded demo PID after verifying its executable
path is the exact Python above and its command line contains this exact `$web`
and `--bind $bind`. Never kill all Python processes or the owner of an unrelated
port. Do not run `tailscale serve reset`, global off, `up`, `down`, or service
restart. Verify zero demo listeners and unchanged Serve; no network rollback
should be necessary because this proposal makes no configuration changes.
The ignored staging copy can remain for evidence; deletion is not required.

Stage B is unimplemented and remains a separate future approval. PRs #8 and #11
stay draft and unmerged. No actual phone result is claimed in this plan.
