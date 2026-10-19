# Portrait battlefield · Stage D1

A standalone HTML/CSS/JavaScript view of **recorded Forge snapshots**, not a
connected game. No Java build, package install, server or Internet access is
needed to view it. Based on PR #23 at
`f23a1a5b66b2b2a692be9082511eb4b4d3a6433f`; existing PRs remain unmerged.

## View locally

Open `experiments/battlefield-static/index.html` directly in your desktop
browser using Open File, or drag it into an existing browser window. Keep the
repository directory structure: the CSS references original Forge skin images
under `forge-gui/res/skins/default/`. Use a 390×844 CSS-pixel portrait viewport
in browser device tools for the verified layout. There is no build step.

The top selector explicitly switches between five **static recorded views**.
Tap Lightning Bolt, the offered player target, Mountain, or the contextual
choice buttons to inspect a proposed typed action. The modal says **not sent**;
closing it leaves the same snapshot, life, hand, land and prompt. Only changing
the snapshot selector changes the displayed state. No engine response is
simulated. In the target view, both original player choices are present, with
the demonstrated opposing-player target emphasized.

The page uses classic local scripts and images, without `fetch`, modules,
storage, networking or a service worker. Its CSP prohibits connections.
Opening these Windows files directly from an iPhone is not a remote test
arrangement. Actual iPhone Safari validation needs separate approval for a
delivery arrangement; no listener was started for D1.

![Real Chrome capture, 390×844 CSS pixels](evidence/before-cast-390x844.png)

[Target](evidence/target-390x844.png) ·
[Manual payment](evidence/payment-390x844.png) ·
[Stack](evidence/stack-390x844.png) ·
[Resolution](evidence/resolved-390x844.png) ·
[Payload preview](evidence/proposal-390x844.png)

## Data and assets

`fixtures.js` transcribes revisions 3–7 of PR #22's published
[`dto-final-2` browser frames](../../docs/experiments/evidence/stage-c-dto-20261018/dto-final-2/console.log).
It preserves the real frame's fields, values, per-frame handles and offered
choices. Source lines and the LF-normalized source SHA-256 are included.
`scripts/extract-fixtures.cjs --check` verifies the transcription offline.
The extractor is deliberately limited to this published record format; it is
not a runtime Forge parser or a Java/JSON bridge.

Only Frame/Seat/Face/StackFace/Payment/Decision/Choice data from the approved
DTO boundary is rendered. No opponent hand/library identities, hidden-card
counts, raw GameView/DeltaPacket/events or Java objects are present. During
target/payment, a card face has zone `Stack` but the actual stack list is empty:
the UI distinguishes **casting** from a completed stack entry. No extra cost,
card type or rules text is invented. Red-mana status comes from Payment only.

[Asset provenance and hashes](assets.json) identify four unchanged local Forge
skin files referenced in place, including the original red-mana sprite crop
from `FSkinProp.IMG_MANA_R`. Like PR #8's original-asset experiment, D1 reuses
the repository assets; treasure chests and the TeaVM renderer are unnecessary
for this DOM battlefield. The card silhouettes are decorative UI marks, not
downloaded official card illustrations. Original assets retain upstream
copyright/licensing; see the repository [GPL license](../../LICENSE) and
[Forge attribution](../../README.md). No dependency or third-party code was imported.

## Optional checks with existing tools

Use an **already installed** Node, Playwright and Chromium. These scripts never
install/download packages or start a listener. Substitute your existing paths:

```powershell
$nodeExe = '<existing node.exe>'
$modules = '<existing node_modules containing playwright>'
$chromeExe = '<existing Chromium executable>'
& $nodeExe experiments/battlefield-static/scripts/extract-fixtures.cjs --check
& $nodeExe --check experiments/battlefield-static/battlefield.js
& $nodeExe experiments/battlefield-static/scripts/verify-browser.cjs --modules $modules --browser $chromeExe --output .local-tools/battlefield-static/review-unique
```

The browser check uses a fresh repository-local Chrome profile, file URLs and
Playwright's pipe transport. It closes its context and preserves screenshots
and JSON in a fresh output directory. Missing tools are a stop condition,
not permission to install them. See [actual results](../../docs/experiments/portrait-battlefield-results.md).

## Still needed for a JVM connection

- Replace historical snapshots with trusted, viewer-specific JVM projections,
  preserving the allowlist and an independently reviewed coherent capture
  boundary. The current Stage C checkpoint proves only its fixture.
- Submit prompt-scoped actions to the original controller, with live handle
  lookup/EDT recheck, C1 ability authorization, manual mana provenance and
  authoritative receipts/revisions. A displayed preview is not authorization.
- Separately review transport, session identity, duplicates/stale actions and
  interruption policy against the [Stage C contract](../../docs/architecture/browser-jvm-interaction-contract.md).
  No endpoint, authentication service, reconnect handling or listener exists here.
- Test actual portrait Safari/touch/safe-area behavior under an approved private
  arrangement. This PR supplies Chromium evidence, not iPhone verification.
