// Optional verification with already-installed Playwright + Chromium. Never installs or serves.
// SPDX-License-Identifier: GPL-3.0-or-later
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const assert = require('node:assert/strict');
const { createHash } = require('node:crypto');
const arg = name => { const i = process.argv.indexOf(name); return i < 0 ? undefined : process.argv[i + 1]; };
const root = path.resolve(__dirname, '../../..');
const output = path.resolve(root, arg('--output') || '.local-tools/battlefield-static/verification');
assert(!fs.existsSync(output), 'Use a fresh output directory; preserve earlier captures.');
fs.mkdirSync(output, { recursive: true });
const { chromium } = require(require.resolve('playwright', { paths: [arg('--modules') || root] }));
const file = pathToFileURL(path.join(root, 'experiments/battlefield-static/index.html')).href;
const evidence = { method: 'Actual local Chromium, file URL, Playwright pipe; no HTTP server',
  viewport: { width: 390, height: 844 }, deviceScaleFactor: 1, mobileEmulation: true, touchEmulation: true,
  actualIphoneSafariTested: false, errors: [], externalRequests: [], checks: [], screenshots: [] };
const hash = value => createHash('sha256').update(value).digest('hex');
let context;
(async () => {
  const scratch = path.join(root, '.local-tools/battlefield-static'); fs.mkdirSync(scratch, { recursive: true });
  const profile = fs.mkdtempSync(path.join(scratch, 'chrome-'));
  context = await chromium.launchPersistentContext(profile, {
    executablePath: arg('--browser'), headless: true, viewport: evidence.viewport,
    deviceScaleFactor: 1, isMobile: true, hasTouch: true,
    args: ['--disable-background-networking', '--disable-component-update', '--no-first-run']
  });
  await context.route(/^https?:/, route => { evidence.externalRequests.push(route.request().url()); return route.abort(); });
  const page = context.pages()[0];
  page.on('pageerror', e => evidence.errors.push(e.message));
  page.on('console', m => { if (m.type() === 'error') evidence.errors.push(m.text().replaceAll(pathToFileURL(root).href, '<WORKSPACE>')); });
  await page.goto(file);
  await page.waitForFunction(() => document.querySelector('#board').dataset.revision);
  evidence.browserVersion = context.browser().version();
  evidence.userAgent = await page.evaluate(() => navigator.userAgent);
  const fixtures = await page.evaluate(() => window.FORGE_FIXTURES);
  function keys(value, allowed) { assert.deepEqual(Object.keys(value).sort(), allowed.split(' ').sort()); }
  for (const { frame: f } of fixtures.snapshots) {
    keys(f, 'match session revision turn phase players cards stack payment prompt');
    f.players.forEach(p => keys(p, 'handle self life'));
    f.cards.forEach(c => keys(c, 'handle name zone controller tapped'));
    f.stack.forEach(s => keys(s, 'source targets'));
    if (f.payment) keys(f.payment, 'remaining redPool');
    keys(f.prompt, 'id kind source selected min max choices');
    f.prompt.choices.forEach(c => keys(c, 'id operation entity'));
    assert.equal(f.players.length, 2);
    assert(f.cards.every(c => c.controller === f.players.find(p => p.self).handle), 'No opponent card identities in fixture');
  }
  const ids = new Set();
  for (const snapshot of fixtures.snapshots) {
    await page.getByLabel('View snapshot', { exact: true }).selectOption(snapshot.key);
    const f = snapshot.frame, before = await page.locator('#board').innerText();
    const actualLife = await page.locator('.seat .life').allTextContents();
    assert.deepEqual(actualLife, [f.players.find(p => !p.self).life, f.players.find(p => p.self).life].map(String));
    assert.equal(await page.locator('#choices button').count(), f.prompt.choices.length);
    assert.equal(await page.locator('#stack .spell-line').count(), f.stack.length);
    const choiceIds = await page.locator('#choices button').evaluateAll(nodes => nodes.map(n => n.dataset.choice));
    for (let i = 0; i < f.prompt.choices.length; i++) {
      const choice = f.prompt.choices.find(c => c.id === choiceIds[i]); assert(choice);
      await page.locator('#choices button').nth(i).tap();
      assert(await page.locator('#proposal').isVisible());
      const action = JSON.parse(await page.locator('#payload').textContent());
      keys(action, 'id payload'); keys(action.payload, 'type version match session revision prompt choice');
      assert(!ids.has(action.id)); ids.add(action.id);
      assert.deepEqual(action.payload, { type: 'action', version: 1, match: f.match, session: f.session,
        revision: f.revision, prompt: f.prompt.id, choice: choice.id });
      assert.equal(await page.locator('#board').innerText(), before, 'Action must not advance or mutate the displayed board');
      assert.equal(await page.locator('#snapshot').inputValue(), snapshot.key);
      if (snapshot.key === 'before-cast' && i === 0) {
        await page.screenshot({ path: path.join(output, 'proposal-390x844.png') });
        evidence.screenshots.push('proposal-390x844.png');
      }
      await page.getByRole('button', { name: 'Close preview', exact: true }).tap();
    }
    const cardAction = page.locator('button.card');
    if (await cardAction.count()) { await cardAction.first().tap(); assert(await page.locator('#proposal').isVisible()); await page.getByRole('button', { name: 'Close preview', exact: true }).tap(); }
    if (snapshot.key === 'target') {
      await page.locator('#opponent button.seat').tap(); assert.match(await page.locator('#proposal-title').textContent(), /Target opponent/);
      await page.getByRole('button', { name: 'Close preview', exact: true }).tap();
    }
    if (snapshot.key === 'payment') {
      assert.match(await page.locator('#choices').innerText(), /Activate Mountain/);
      assert.doesNotMatch(await page.locator('#choices').innerText(), /Auto|OK|Pass/);
      assert.equal(await page.locator('.selected-target .seat-name').innerText(), 'Opponent\nSELECTED TARGET');
    }
    assert.equal(await page.locator('#board').innerText(), before);
    assert.equal(await page.locator('#lands .card').getAttribute('data-tapped'), String(f.cards.find(c => c.name === 'Mountain').tapped));
    await page.evaluate(() => scrollTo(0, 0));
    const geometry = await page.evaluate(() => ({
      width: innerWidth, height: innerHeight, scrollWidth: document.documentElement.scrollWidth,
      scrollY, documentHeight: document.documentElement.scrollHeight,
      actionsBottom: document.querySelector('.decision-zone').getBoundingClientRect().bottom,
      targets: [...document.querySelectorAll('button, select')].filter(e => e.getClientRects().length).map(e => ({
        text: e.getAttribute('aria-label') || e.textContent, width: e.getBoundingClientRect().width, height: e.getBoundingClientRect().height
      }))
    }));
    assert(geometry.scrollWidth <= 390, 'No horizontal overflow');
    assert.equal(geometry.scrollY, 0, 'Capture begins at the page top');
    assert(geometry.documentHeight <= 844, 'Entire battlefield fits the target viewport');
    assert(geometry.actionsBottom <= 844, 'Primary controls fit the target viewport');
    assert(geometry.targets.every(t => t.width >= 44 && t.height >= 44), 'Visible tap targets must be at least 44×44');
    const filename = `${snapshot.key}-390x844.png`;
    await page.screenshot({ path: path.join(output, filename) });
    evidence.screenshots.push(filename);
    evidence.checks.push({ snapshot: snapshot.key, revision: f.revision, offeredChoices: f.prompt.choices.length,
      payloadsMatchOffers: true, actionsDidNotAdvanceSnapshot: true, geometry });
  }
  for (const viewport of [{ width: 320, height: 720 }, { width: 390, height: 667 }, { width: 844, height: 390 }]) {
    await page.setViewportSize(viewport);
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    evidence.checks.push({ viewport, noHorizontalOverflow: true, verticalScrollingAllowed: true });
  }
  await page.setViewportSize(evidence.viewport);
  await page.getByLabel('View snapshot', { exact: true }).selectOption('before-cast');
  await page.locator('#choices button').first().focus(); await page.keyboard.press('Enter');
  assert(await page.locator('#proposal').isVisible()); await page.keyboard.press('Escape');
  assert(!(await page.locator('#proposal').isVisible()));
  evidence.keyboardEnterAndEscape = true;
  evidence.typedChoicePreviewsVerified = ids.size;
  assert(await page.evaluate(() => Object.isFrozen(window.FORGE_FIXTURES.snapshots[0].frame.cards[0])));
  assert.equal(evidence.externalRequests.length, 0); assert.deepEqual(evidence.errors, []);
  evidence.sourceHashes = Object.fromEntries(['index.html', 'battlefield.css', 'battlefield.js', 'fixtures.js', 'scripts/extract-fixtures.cjs', 'scripts/verify-browser.cjs']
    .map(p => [p, hash(fs.readFileSync(path.join(root, 'experiments/battlefield-static', p)))]));
  evidence.passed = true;
})().catch(error => { evidence.passed = false; evidence.failure = error.message; process.exitCode = 1; })
  .finally(async () => {
    if (context) await context.close();
    evidence.browserContextClosed = true;
    fs.writeFileSync(path.join(output, 'browser-checks.json'), JSON.stringify(evidence, null, 2) + '\n');
    console.log(JSON.stringify({ passed: evidence.passed, snapshots: evidence.checks.length, errors: evidence.errors, failure: evidence.failure }, null, 2));
  });
