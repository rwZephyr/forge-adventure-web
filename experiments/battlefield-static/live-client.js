// SPDX-License-Identifier: GPL-3.0-or-later
(() => {
  'use strict';
  const view = window.FORGE_LIVE_VIEW, $ = id => document.getElementById(id);
  let capability = '', running = false, pending = null, waitingRevision = null, latest = null;
  const status = text => { $('live-status').textContent = text; };
  function stop(text) { running = false; capability = ''; view.busy(true); $('abort').disabled = true; $('finish').disabled = true; status(text); }
  async function request(path, body) {
    const controller = new AbortController(), timer = setTimeout(() => controller.abort(), 5000);
    try {
      const headers = { Authorization: `Bearer ${capability}` };
      if (body !== undefined) headers['Content-Type'] = 'application/json';
      const response = await fetch(path, { method: body === undefined ? 'GET' : 'POST', headers,
        body: body === undefined ? undefined : JSON.stringify(body), cache: 'no-store', credentials: 'omit', redirect: 'error', signal: controller.signal });
      const data = await response.json();
      if (!response.ok && !((path === '/api/action' || path === '/api/receipt') && response.status === 409 && data.status === 'rejected')) throw new Error('Request unavailable');
      return data;
    } finally { clearTimeout(timer); }
  }
  function receipt(value, id) {
    if (!pending || pending.id !== id) return;
    if (value.status === 'pending') return;
    if (value.status === 'accepted') {
      waitingRevision = pending.payload.revision;
      status('Controller dispatched. Waiting for the next Forge frame.');
    } else { status(`Action rejected: ${value.code || 'unavailable'}. No success assumed.`); }
    pending = null;
  }
  async function poll() {
    if (!running) return;
    try {
      const state = await request('/api/state');
      if (!running) return;
      if (state.frame) { latest = state.frame; view.frame(latest); }
      const action = pending;
      if (action) receipt(await request('/api/receipt', { id: action.id }), action.id);
      if (!running) return;
      if (latest && waitingRevision !== null && latest.revision > waitingRevision) waitingRevision = null;
      if (state.outcome === 'TERMINAL') {
        view.busy(true); $('finish').hidden = false;
        status('Forge verified the Bolt sequence. Inspect the final frame, then finish this test.');
      } else if (state.outcome !== 'ACTIVE') { stop(`Test outcome: ${state.outcome}. No further gameplay submissions.`); return; }
      else view.busy(!latest?.prompt || pending !== null || waitingRevision !== null);
      setTimeout(poll, 400);
    } catch (_) { stop('Connection unavailable. The last frame is retained; no action or result is assumed. Consult the owner-side test report.'); }
  }
  view.onAction(async action => {
    if (!running || pending || waitingRevision !== null) return;
    pending = action; view.busy(true);
    try { receipt(await request('/api/action', action), action.id); }
    catch (_) { stop('Submission outcome unknown. No automatic retry or pass. Consult the owner-side test report.'); }
  });
  $('connect').addEventListener('click', () => {
    const entered = $('capability').value; $('capability').value = '';
    if (!/^[A-Za-z0-9_-]{43}$/.test(entered)) { status('Paste the private capability from the local session file.'); return; }
    capability = entered; running = true; $('connect').disabled = true; $('abort').disabled = false; $('access').hidden = true;
    status('Connecting to the isolated fixture…'); poll();
  });
  $('abort').addEventListener('click', async () => {
    view.busy(true);
    try { await request('/api/abort', {}); stop('Abort requested. The owner-side outcome and shutdown report confirm completion; no gameplay decision was supplied.'); }
    catch (_) { stop('Abort delivery unknown. Consult the owner-side report; no gameplay decision was supplied.'); }
  });
  $('finish').addEventListener('click', async () => {
    $('finish').disabled = true;
    try { const result = await request('/api/finish', {}); if (result.status !== 'harnessFinishRequested') throw new Error('Not terminal');
      stop('Test finish requested. Final frame retained. Harness cleanup is separate from browser gameplay; check the owner-side PASS/shutdown report.'); }
    catch (_) { stop('Finish delivery unknown. Final frame retained; consult the owner-side report.'); }
  });
})();
