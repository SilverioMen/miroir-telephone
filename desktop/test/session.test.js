import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createSessionStore } from '../src/core/session.js';

function horloge(start = 1000) {
  let t = start;
  return { now: () => t, avance: (ms) => { t += ms; } };
}

test('session: un jeton neuf est accepté une seule fois', () => {
  const h = horloge();
  const s = createSessionStore({ now: h.now });
  const jeton = s.issueToken();
  const premier = s.redeemToken(jeton);
  assert.equal(premier.ok, true);
  assert.equal(typeof premier.resumeKey, 'string');
  assert.deepEqual(s.redeemToken(jeton), { ok: false, reason: 'invalid_token' });
});

test('session: un jeton inconnu est refusé', () => {
  const s = createSessionStore({ now: () => 0 });
  assert.deepEqual(s.redeemToken('nimporte-quoi'), { ok: false, reason: 'invalid_token' });
});

test('session: un jeton expire après 120000 ms', () => {
  const h = horloge();
  const s = createSessionStore({ now: h.now });
  const jeton = s.issueToken();
  h.avance(120001);
  assert.deepEqual(s.redeemToken(jeton), { ok: false, reason: 'expired' });
});

test('session: resume accepté pendant 120000 ms après markDisconnected, refusé après', () => {
  const h = horloge();
  const s = createSessionStore({ now: h.now });
  const { resumeKey } = s.redeemToken(s.issueToken());
  s.markDisconnected();
  h.avance(119999);
  assert.equal(s.resume(resumeKey), true);
  s.markDisconnected();
  h.avance(120001);
  assert.equal(s.resume(resumeKey), false);
});

test('session: resume refuse une clé inconnue', () => {
  const s = createSessionStore({ now: () => 0 });
  assert.equal(s.resume('inconnue'), false);
});

test('session: un seul téléphone actif à la fois', () => {
  const s = createSessionStore({ now: () => 0 });
  assert.equal(s.hasActivePhone(), false);
  s.setActivePhone(true);
  assert.equal(s.hasActivePhone(), true);
  s.setActivePhone(false);
  assert.equal(s.hasActivePhone(), false);
});
