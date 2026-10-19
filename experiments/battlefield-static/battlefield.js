// SPDX-License-Identifier: GPL-3.0-or-later
(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const freeze = value => { if (value && typeof value === 'object') { Object.values(value).forEach(freeze); Object.freeze(value); } return value; };
  const fixtures = freeze(window.FORGE_FIXTURES);
  let current = fixtures.snapshots[0];
  let actionSequence = 0;
  const node = (tag, className, text) => {
    const el = document.createElement(tag); if (className) el.className = className;
    if (text !== undefined) el.textContent = text; return el;
  };
  const seatLabel = handle => current.frame.players.find(p => p.handle === handle)?.self ? 'You' : 'Opponent';
  const cardName = handle => current.frame.cards.find(c => c.handle === handle)?.name || 'Visible card';
  const label = choice => ({ cast: `Cast ${cardName(choice.entity)}`, target: `Target ${seatLabel(choice.entity).toLowerCase()}`,
    mana: `Activate ${cardName(choice.entity)}`, pass: 'Pass priority' })[choice.operation];

  function propose(choice) {
    // No transport, reducer, simulated receipt or game mutation. Copy only the offered capability.
    const f = current.frame;
    if (!f.prompt?.choices.includes(choice)) return;
    const action = { id: `static-preview-${++actionSequence}`, payload: {
      type: 'action', version: 1, match: f.match, session: f.session,
      revision: f.revision, prompt: f.prompt.id, choice: choice.id
    } };
    $('proposal-title').textContent = label(choice);
    $('payload').textContent = JSON.stringify(action, null, 2);
    $('proposal').showModal();
  }
  function button(choice, className = '') {
    const el = node('button', className, label(choice)); el.type = 'button';
    el.dataset.choice = choice.id; el.addEventListener('click', () => propose(choice)); return el;
  }
  function seat(player) {
    const f = current.frame;
    const choice = f.prompt?.choices.find(c => c.operation === 'target' && c.entity === player.handle);
    const selected = f.prompt?.selected.includes(player.handle) || f.stack.some(s => s.targets.includes(player.handle));
    const el = choice ? button(choice, 'seat selectable') : node('div', 'seat');
    el.replaceChildren(); el.dataset.self = String(player.self);
    if (choice) el.setAttribute('aria-label', `${label(choice)} · ${player.life} life · preview only`);
    if (selected) el.classList.add('selected-target');
    el.append(node('span', `sigil ${player.self ? 'self' : ''}`, player.self ? 'Y' : 'O'));
    const name = node('span', 'seat-name', player.self ? 'You' : 'Opponent');
    name.append(node('small', '', selected ? 'SELECTED TARGET' : choice ? 'TAP TO PREVIEW TARGET' : player.self ? 'YOUR SIDE' : 'OPPOSING PLAYER'));
    el.append(name, node('span', 'life', String(player.life)), node('span', 'life-label', 'LIFE'));
    return el;
  }
  function card(face, size) {
    const choice = current.frame.prompt?.choices.find(c => c.entity === face.handle && ['cast', 'mana'].includes(c.operation));
    const el = choice ? button(choice, `card ${size}`) : node('article', `card ${size}`);
    el.replaceChildren(); el.dataset.card = face.name; el.dataset.tapped = String(face.tapped);
    el.setAttribute('aria-label', `${face.name}, ${face.zone}, ${face.tapped ? 'tapped' : 'untapped'}${choice ? `; ${label(choice)}, preview only` : ''}`);
    const visual = node('span', `card-face ${face.name === 'Mountain' ? 'land-face' : 'bolt-face'}`);
    visual.append(node('span', 'card-title', face.name));
    const art = node('span', 'card-art'); art.setAttribute('aria-hidden', 'true');
    // Decorative UI marks only, not downloaded card artwork or additional card rules.
    art.innerHTML = face.name === 'Mountain'
      ? '<svg viewBox="0 0 100 80"><path d="M4 70 37 13 56 45 69 27 98 70Z"/><path class="snow" d="m24 36 13-23 19 32-15-10-8 8Z"/></svg>'
      : '<svg viewBox="0 0 100 100"><path d="m57 3-36 52h26l-8 42 41-58H53Z"/></svg>';
    visual.append(art, node('span', 'card-zone', face.zone === 'Hand' ? 'IN YOUR HAND' : face.zone.toUpperCase()));
    el.append(visual); if (choice) el.classList.add('offered-card'); return el;
  }
  function renderStack() {
    const f = current.frame, area = $('stack'); area.replaceChildren();
    const heading = node('div', 'stack-heading');
    heading.append(node('span', 'zone-label', 'STACK'), node('span', 'stack-count', String(f.stack.length)));
    area.append(heading);
    for (const item of f.stack) {
      const row = node('p', 'spell-line', cardName(item.source));
      row.append(node('span', 'spell-target', `→ ${item.targets.map(seatLabel).join(', ')}`)); area.append(row);
    }
    if (!f.stack.length) area.append(node('p', 'empty-stack', f.prompt?.source ? `Casting ${cardName(f.prompt.source)} · not on the stack yet` : 'The stack is empty'));
    if (f.prompt?.selected.length) area.append(node('p', 'target-note', `Selected target: ${f.prompt.selected.map(seatLabel).join(', ')}`));
    if (f.payment) {
      const payment = node('p', 'payment-note');
      if (f.payment.remaining.includes('RED')) { const mana = node('span', 'red-mana'); mana.setAttribute('aria-hidden', 'true'); payment.append(mana); }
      const remaining = f.payment.remaining.map(shard => shard === 'RED' ? 'red mana' : shard.toLowerCase());
      payment.append(document.createTextNode(`Need ${remaining.join(', ')} · ${f.payment.redPool} red in pool`)); area.append(payment);
    }
  }
  function render() {
    const f = current.frame;
    $('board').dataset.revision = String(f.revision);
    $('phase').textContent = `Turn ${f.turn} · ${f.phase === 'MAIN1' ? 'Main phase 1' : f.phase}`;
    $('revision').textContent = `Snapshot r${f.revision}`;
    const self = f.players.find(p => p.self), opponent = f.players.find(p => !p.self);
    $('opponent').replaceChildren(seat(opponent)); $('player').replaceChildren(seat(self));
    const opposing = f.cards.filter(c => c.zone === 'Battlefield' && c.controller === opponent.handle);
    $('opponent-cards').replaceChildren(...(opposing.length ? opposing.map(c => card(c, 'land-card')) : [node('p', 'empty-field', 'No battlefield cards shown')]));
    const lands = f.cards.filter(c => c.zone === 'Battlefield' && c.controller === self.handle);
    $('lands').replaceChildren(...lands.map(c => {
      const well = node('div', 'land-well');
      well.append(card(c, 'land-card'), node('span', 'tap-label', `${c.name} · ${c.tapped ? 'Tapped' : 'Untapped'}`)); return well;
    }));
    const hand = f.cards.filter(c => c.zone === 'Hand' && c.controller === self.handle);
    $('hand-title').textContent = `YOUR HAND · ${hand.length} SHOWN`;
    $('hand').classList.toggle('empty', !hand.length);
    $('hand').replaceChildren(...(hand.length ? hand.map(c => card(c, 'hand-card')) : [node('p', 'empty-hand', 'No cards shown in hand')]));
    const graveyard = f.cards.filter(c => c.zone === 'Graveyard' && c.controller === self.handle);
    $('graveyard').textContent = graveyard.length ? `Graveyard: ${graveyard.map(c => c.name).join(', ')}` : '';
    renderStack();
    $('decision-title').textContent = ({ priority: 'Choose an action', target: 'Choose a player target', payment: 'Pay with Mountain' })[f.prompt?.kind] || 'No current decision';
    const choices = [...(f.prompt?.choices || [])];
    // Presentation order only: preserve both original player capabilities, prefer the demonstrated opponent target.
    if (f.prompt?.kind === 'target') choices.sort((a, b) => Number(a.entity === self.handle) - Number(b.entity === self.handle));
    $('choices').replaceChildren(...choices.map((c, i) => button(c, i === 0 ? 'primary-choice' : 'secondary-choice')));
  }
  fixtures.snapshots.forEach((s, i) => { const option = node('option', '', `${i + 1}. ${s.label}`); option.value = s.key; $('snapshot').append(option); });
  $('snapshot').addEventListener('change', event => {
    current = fixtures.snapshots.find(s => s.key === event.target.value);
    if ($('proposal').open) $('proposal').close(); $('payload').textContent = ''; render();
  });
  $('close-proposal').addEventListener('click', () => $('proposal').close());
  render();
})();
