# Actual iPhone Safari session — completed

Date: 2026-10-18 (America/Vancouver). Branch: `codex/safari-test-plan`.
The owner explicitly approved one temporary listener on the exact Tailscale
IPv4 address and TCP 8765 specified in the private test plan.

## Outcome

**Bounded SpriteGroup checks pass on the actual phone.** Original treasures
rendered; BA/AB draw and child orders matched; dragging/release, rotation/input
and exact reset values passed with screenshots and owner feedback. The fixed
portrait world and text overlap are layout limitations, not a responsive
Adventure layout. Phone performance feedback is qualitative.

**Stage A's expected factory behavior is reproduced on Safari:**

| Variant | First exported phone result | Actual comparison | Two further owner-reported loads |
| --- | --- | --- | --- |
| Explicit metadata | 15/15 | All 15 rows match JVM | 15/15, no console errors reported |
| Missing-amount control | 14/15, only K2 fails | Expected fallback detected; other rows match JVM | Same sole K2 failure and empty console capture |
| Default metadata | 5/15 | JVM rejects; semantic case fields match Chromium default | Same 5/15 |

Only the first load of each variant has exported full JSON. The repeat results
are owner reports; do not label them three independently exported comparisons.
Default's Safari TypeError strings differ from Chromium for T1, T2 and
invalid.trigger; actual strings are retained. Expected failures demonstrate the
need for reflection metadata and class assertions, not broad rules compatibility.

**Access and cleanup:** fresh retrieval succeeded over cellular with Tailscale
on. With phone Tailscale off, SpriteGroup and the representative explicit factory
URL did not retrieve successfully, with zero fresh query requests logged while
the listener was running. Safari's timeout and Private Relay warning are
recorded; privacy protections were not bypassed. Default/missing-amount off-mode
URLs were not separately attempted. The server is now stopped, TCP 8765 has no
listener, and saved before/after Serve JSON snapshots are byte-identical.

Reviewed source/artifacts remain PR #8 at
`57ed01d137e45ea2a304b64ef979402347cb0843` and PR #11 at
`226bb0fb6109a2a5f31b5dd931e8e1db3ac379c5`. Both PRs were rechecked after cleanup:
open, draft, unmerged. No Forge/game/runtime code, dependencies, network
configuration, firewall, existing services, saves or accounts changed. Stage B
remains unimplemented and needs its separate review.

## Verified on Silent

- Preflight passed: approved address preferred on the Tailscale interface;
  TCP 8765 unused; no staged symlinks/junctions; six recorded SpriteGroup and
  526 recorded factory hashes matched; all 924 staged files matched the original
  artifact copies, with no extra files. Existing tools only; no rebuild.
- Existing Serve configuration was saved privately outside the web root.
- Foreground Python 3.12.10 static server started with the documented `--bind`
  and `--directory` arguments. Exactly one TCP 8765 listener was verified,
  on the specifically approved address, owned by the expected Python process.
- Immediately after startup, existing Serve configuration matched the preflight
  snapshot exactly. No Serve/firewall/network/service setting was changed.
- PRs #8 and #11 were rechecked: both open, draft and unmerged. Stage B remains
  unimplemented.

Private session metadata, listener PID/identity and server request logs are
retained under the ignored `.local-tools/safari-test-20261018/session-20261018-061655/`.
Do not publish raw private network configuration or addresses from those files.

## Phone observations

### Observation 1 — rendering and initial dragging

- **Owner report:** iPhone 14, OS version `26.6.1`; “Works with dragging.”
  This is the owner's actual-phone observation during the requested Safari test;
  browser UA/version and OS version have not been independently extracted.
- **Screenshot observed:** portrait display, both mint and amber original treasure
  sprites rendered; footer `Draw: BA`, `children: BA`, `A.y 43.3`, `B.y 203.5`,
  `pointer events 244`. No visible page error. This is one captured draw order,
  not independent evidence of both order reversals or touch release.
- **Server evidence:** `/spritegroup/`, app JavaScript, backend scripts, original
  treasure atlas/PNG and other required assets returned HTTP 200 to a tailnet
  client at approximately 06:19:48–49 local time. Server requests prove retrieval,
  not JavaScript execution or input behavior.
- Screenshot retained byte-for-byte in private session evidence as
  `iphone-spritegroup-01.jpg`; its SHA-256 and observation metadata are recorded
  in `observation-01.json` outside the served directory.
- Wi-Fi status icon is visible in the screenshot. Cellular-only access has not
  been verified; do not infer it from tailnet server requests.

### Observation 2 — opposite draw order, release and LTE

- **Owner report:** release stops movement; response “feels very good”; Wi-Fi
  has now been disabled. Responsiveness is subjective feedback, not a measured
  latency or frame-rate result.
- **Screenshot observed:** both sprites visibly rendered with mint above amber;
  `Draw: AB`, `children: AB`, `A.y 337.8`, `B.y 40.3`, `pointer events 83`.
  Together with observation 1, both correct BA and AB draw/child orders have
  been captured on the actual phone. A continuous AB → BA return sequence has
  not been independently captured. Release behavior passes by owner report.
- LTE status icon is present and Wi-Fi icon absent. This supports the owner's
  network-state report; fresh server retrieval over cellular is still pending.
  An already running page can keep rendering without network access.
- Screenshot retained byte-for-byte privately as `iphone-spritegroup-02.jpg`;
  hash and observation metadata are in `observation-02.json` outside the web root.

### Observation 3 — rotation and landscape bounds

- **Owner report:** both chests remain visible and dragging follows the finger
  in landscape and portrait. Landscape movement stays inside the portrait world;
  the screenshot places mint at its left limit and amber at its right limit.
- **Screenshots observed:** portrait `AB` draw/child order, A.y 373.7, B.y 67.1,
  412 pointer events, LTE; landscape `BA` draw/child order, A.y 241.7, B.y 265.9,
  919 pointer events. Both sprites render after rotation. These also capture a
  later return to BA following earlier AB observations, without a continuous
  recording of the transition.
- **Source verified:** probe uses `FitViewport(320, 480)` with 96-unit sprites;
  its touch handler clamps x to 0..224 and y to 0..384. Rotation fits the same
  logical world rather than expanding it. Horizontal limits therefore match
  the harness design; this is not a limitation established for Forge Adventure.
- **Layout limitation observed:** fixed header/footer text overlaps the sprite
  play area, and landscape leaves unused horizontal space. Input/resize pass
  by owner report; a responsive full-screen Adventure layout is not implemented
  or validated by this probe. No runtime or layout code was changed for this test.
- Both screenshots retained privately as `iphone-spritegroup-03-portrait.jpg`
  and `iphone-spritegroup-03-landscape.jpg`; hashes in `observation-03.json`.
  Raw screenshots include browser UI and stay out of public repository evidence.

### Observation 4 — reset and fresh cellular retrieval

- **Owner report:** reset looks correct; Wi-Fi was disabled in observation 2.
- **Screenshot observed:** both original chests at their initial overlap,
  `Draw: BA`, `children: BA`, `A.y 200.0`, `B.y 220.0`, `pointer events 0`;
  LTE visible, no Wi-Fi icon and no visible page error. Reset values match the
  original probe exactly.
- **Server evidence:** fresh `/spritegroup/?safari-reset=4` requests returned
  HTTP 200 at 06:26:50 and 06:26:52 local time from the same tailnet client.
  Together with the owner's network-state report and LTE screenshot, this
  verifies successful fresh page retrieval over cellular through Tailscale.
- Screenshot preserved privately as `iphone-spritegroup-04-reset.jpg`, with
  SHA-256 and matching request lines in `observation-04.json` outside the web root.

**Gate 1 bounded actual-phone result:** SpriteGroup rendering, matching BA/AB
draw-child orders, dragging/release, rotation with subsequent input, and reset
pass in this owner-guided iPhone Safari session. The fixed portrait world and
text overlap remain recorded layout limitations. No precise latency/frame rate,
full Adventure gameplay, HTTPS secure-context feature or broad iOS compatibility
is claimed. Device/OS identification is owner-reported, with screenshots.

### Observation 5 — explicit factory metadata, exported phone JSON

- **Owner supplied:** page reports PASS and 15/15; all nine factories have the
  correct exact concrete classes, with enum/name cases passing. Full raw case
  JSON was pasted from the phone page and saved as
  `iphone-factory-explicit-01.json` in the private session directory.
- **Actual JVM comparison:** existing `scripts/compare_results.py` ran against
  committed `evidence/jvm-results.json` with `--variant explicit`, exit 0;
  `comparisonPassed: true`, 15 cases. Classes, observations, enum inventories,
  pass flags and aggregate consistency all match. The comparison output is
  saved as `iphone-factory-explicit-01-comparison.json`. One exported phone
  observation has been compared; repeat-load results remain pending.
- **UA supplied by page:** `Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X)
  AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.6.1 Mobile/15E148 Safari/604.1`.
  Earlier owner-reported version `26.6.1` is retained as reported; the page uses
  it for Safari Version, while its OS compatibility token is `18_7`. Actual
  Settings OS version is not independently established. Do not equate these
  identifiers or infer compatibility with untested iOS versions.
- **Server evidence:** factory page and explicit `app.js` returned HTTP 200.
  `/favicon.ico` returned an incidental 404; no application resource failure
  was observed. Page console capture was not included in the owner's message.
- JSON hash, provenance, full UA and observation notes are in `observation-05.json`.

### Observation 6 — explicit repeat loads in private browsing

- **Owner report:** opened private browsing because normal browsing seemed to
  cache; repeat 1 and repeat 2 both showed 15/15 with no console errors.
- Explicit variant therefore has three passing reported phone observations,
  including the first full JSON export verified against JVM. The two repeat
  loads have no separate raw JSON exports/comparisons; they are owner-reported
  summary/console checks. Do not present them as three independently exported
  JVM comparisons.
- Suspected caching is reported without a confirmed cache diagnosis or change
  to served artifacts/headers. No code, server setting or browser configuration
  was changed by the agent. Observation recorded privately in `observation-06.json`.

### Observation 7 — silent-fallback negative control on Safari

- **Owner supplied full phone page/JSON:** completed with 14/15, only K2 fails
  its exact concrete-class assertion. Bushido:2 returned original Forge
  `SimpleKeyword`, BUSHIDO, amount 1 rather than `KeywordWithAmount`, amount 2.
  Console warnings/errors capture is `[]`; the displayed overall FAIL is expected.
- **Actual JVM comparison:** existing comparator with `--variant missing-amount`
  exited 0, `comparisonPassed: true`, 15 cases,
  `negativeControlDemonstrated: true`. All other 14 rows match JVM; K2 satisfies
  the deliberately withheld constructor/fallback criteria. Saved phone JSON and
  comparison are `iphone-factory-missing-amount-01*.json`; provenance/hash and
  console observation are in `observation-07.json` in the private session folder.
- This is one exported phone run. Negative repeat-load summaries remain pending;
  do not infer that original keyword instantiation is safe merely from completion.

### Observation 8 — negative-control repeat loads

- **Owner confirmation:** both retries remained 14/15, only K2 failing, and
  console warnings/errors `[]`, in response to those specific criteria.
- The negative variant has three reported expected phone outcomes. Only the
  first includes a full exported JSON/JVM comparison; two repeat summaries are
  owner reports, not separate raw exports. Private record: `observation-08.json`.

### Observation 9 — default metadata failure on Safari

- **Owner supplied full page/JSON:** completed FAIL, 5/15; trigger enum cannot
  find TriggerAbandoned's constructor, T1/T2 and invalid.trigger encounter the
  failed initialization, replacement constructors are missing, and K2–K5 silently
  fall back to SimpleKeyword. Console warnings/errors capture is `[]`; caught
  case errors appear in result JSON rather than that console capture.
- **Actual JVM comparison:** unchanged comparator rejects default, as expected;
  it does not match the passing JVM. The mismatched fields are preserved in
  `iphone-factory-default-01-comparison.json`. This is not a factory success.
- **Separate data audit:** all 15 Safari rows match recorded Chromium default
  for pass flags, expected/actual classes, observations and error classes.
  Aggregate consistency also passes. T1, T2 and invalid.trigger have Safari's
  `null is not an object` wording in place of Chromium's `Cannot read properties
  of null`; exact strings from both are retained, without altering input JSON.
- Private `compare_default_phone.py` performs this audit and calls the existing
  JVM comparator, exit 0 for the expected rejection/matching failure pattern.
  Raw export, audit output and `observation-09.json` retain provenance/hashes.
  This is one exported default phone run; repeat observations remain pending.

### Observation 10 — default repeat loads

- **Owner report:** completed the two retries, “same 5/15”. No additional raw
  exports were supplied; the last full default export had empty console capture.
- Default has three reported 5/15 phone outcomes, with one exported result
  audited against JVM/Chromium. Repeats are owner-reported summaries.
  Private record: `observation-10.json`.
- Before the final access check, read-only inspection confirmed the original
  approved listener was still running on the exact address/PID and existing
  Serve still matched the preflight snapshot. Unused query markers were saved
  privately for server-log correlation in `access-check-before.json`.

### Observation 11 — SpriteGroup with iPhone Tailscale disconnected

- **Owner report:** iPhone Tailscale off, Wi-Fi off/cellular; fresh test URL
  appeared black and did not appear to time out.
- **Screenshots observed:** first explicitly says Safari could not open the
  page because the server stopped responding; second displays iCloud Private
  Relay's unable-to-hide-IP warning. Both show LTE. No demo rendered.
- **Machine verification after feedback:** approved listener remained active
  on its exact address/PID, existing Serve unchanged, zero logged requests for
  the fresh `tailnet-off=iphone-final-11` marker. This supports lack of successful
  retrieval while phone Tailscale was disconnected, with a Private Relay
  interstitial as an additional access-test limitation. It is not a successful
  unauthenticated public retrieval, nor a browser-rendering regression.
- No privacy protection, firewall or Tailscale setting on Silent was changed.
  Owner is advised to close the warning without bypassing it. Screenshots and
  hashes are retained privately in `observation-11.json`; network/log verification
  is `access-check-spritegroup.json`.

### Observation 12 — representative factory URL with Tailscale off

- **Owner report:** “Blank screen waiting for timeout.” No factory result
  rendered; a completed timeout is not claimed.
- Zero matching fresh-query requests reached the server for the explicit factory
  URL before shutdown. Only this representative factory URL was attempted with
  Tailscale off; default and missing-amount were not separately attempted.
  Private record: `observation-12.json`.

## Cleanup

Completed at 2026-10-18 13:50:39 UTC (06:50:39 America/Vancouver).

- Ctrl+C stopped only the foreground demo server. Its exec session exited 1
  after interruption; this was an intentional stop, not a factory/runtime failure.
- Read-only `Get-NetTCPConnection -State Listen` found **zero TCP 8765 listeners**.
  The original Python process no longer existed.
- Read-only `tailscale serve status --json` after shutdown matched the saved
  preflight JSON. Saved before/after snapshot SHA-256 values were equal; raw
  snapshots remain private. No reset, restore or configuration write was needed.
- All fresh Tailscale-off query markers had **zero HTTP requests** in the final
  server log. Evidence and the isolated staging copies are retained locally.
- PRs #8/#11 remained draft/unmerged in a final GitHub read. No listener remains,
  and the one approved test session has ended.

## Evidence and exact comparison reproduction

[Sanitized summary](evidence/safari-20261018/session-summary.json) records
provenance, version ambiguity, owner-reported repeats, access limitations and
verified cleanup. Raw phone JSON and comparator outputs are in the same folder.
[Manifest](evidence/safari-20261018/evidence-manifest.json) records artifact-evidence
hashes and hashes of the privately retained screenshots. Screenshots, raw
request logs and Serve snapshots stay outside public evidence because they
contain private addresses and unrelated browser UI.

From the repository root, using existing Python only:

```powershell
$python = 'C:\Users\Silent\AppData\Local\Programs\Python\Python312\python.exe'
$jvm = 'experiments/rules-teavm/evidence/jvm-results.json'
$evidence = 'docs/experiments/evidence/safari-20261018'
& $python experiments/rules-teavm/scripts/compare_results.py $jvm "$evidence/iphone-factory-explicit-01.json" --variant explicit
& $python experiments/rules-teavm/scripts/compare_results.py $jvm "$evidence/iphone-factory-missing-amount-01.json" --variant missing-amount
& $python "$evidence/compare_default_phone.py"
```

All three commands actually exited 0. The third audit records the unchanged
comparator's **expected rejection** of default against JVM, then checks default's
15 case IDs, aggregate consistency, pass flags, classes, observations and error
classes against committed Chromium default. It reports browser error-text
differences explicitly. It does not turn default into a passing factory variant.
Replaying these comparisons checks preserved phone evidence, not a new Safari run.

No unresolved failure prevents completion of this bounded session. A future
Stage B design must separately exercise genuine threaded AiController spell
evaluation; no AI bootstrap or implementation was attempted here.
