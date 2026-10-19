// Future approved-session check only. Does not start a listener or install a browser.
'use strict';
const fs = require('node:fs'), path = require('node:path');
const args = process.argv.slice(2), option = name => args[args.indexOf(name) + 1];
if (!args.includes('--approved-session')) throw new Error('Separate listener/browser-session approval is required');
for (const name of ['--url', '--capability-file', '--modules', '--browser', '--output']) {
  if (!args.includes(name) || !option(name)) throw new Error(`Missing ${name}`);
}
const url = new URL(option('--url'));
if (!['http://127.0.0.1:8765', 'http://100.122.229.121:8765'].includes(url.origin) || url.search || url.hash || url.pathname !== '/experiments/battlefield-static/live.html') throw new Error('Unexpected approved-session address');
const output = path.resolve(option('--output'));
if (fs.existsSync(output)) throw new Error('Use a fresh evidence directory');
const capability = fs.readFileSync(option('--capability-file'), 'utf8').trim();
if (!/^[A-Za-z0-9_-]{43}$/.test(capability)) throw new Error('Private capability file is invalid');
const { chromium } = require(path.join(path.resolve(option('--modules')), 'playwright'));
(async () => {
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({ executablePath: option('--browser'), headless: true });
  try {
    const context = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1 });
    const page = await context.newPage();
    await page.goto(url.href);
    await page.getByLabel('Private session capability').fill(capability);
    await page.getByRole('button', { name: 'Connect', exact: true }).click();
    for (const [label, screenshot] of [['Cast Lightning Bolt', 'before'], ['Target opponent', 'target'], ['Activate Mountain', 'payment'], ['Pass priority', 'stack']]) {
      const button = page.getByRole('button', { name: label, exact: true });
      await button.waitFor(); await page.waitForFunction(label => [...document.querySelectorAll('#choices button')].some(b => b.textContent === label && !b.disabled), label);
      await page.screenshot({ path: path.join(output, `${screenshot}.png`), fullPage: true });
      await button.click();
    }
    await page.getByText('Forge verified the Bolt sequence.', { exact: false }).waitFor();
    if (await page.locator('#opponent .life').textContent() !== '17' || !(await page.locator('#graveyard').textContent()).includes('Lightning Bolt')) throw new Error('Expected authoritative terminal view missing');
    await page.screenshot({ path: path.join(output, 'terminal.png'), fullPage: true });
    await page.getByRole('button', { name: 'Finish test inspection', exact: true }).click();
    await page.waitForFunction(() => document.getElementById('live-status').textContent.startsWith('Test finish requested.'));
    fs.writeFileSync(path.join(output, 'browser-result.json'), JSON.stringify({ browser: 'Chromium automation', viewport: { width: 390, height: 844 }, terminalView: true, ownerJvmOutcomeStillRequired: true, capabilityPublished: false }, null, 2));
    await context.close();
  } finally { await browser.close(); }
})().catch(() => { process.stderr.write('Browser check failed; consult private owner evidence. No automatic retry.\n'); process.exitCode = 1; });
