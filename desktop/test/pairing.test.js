import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildPairingUrl, parsePairingUrl } from '../src/core/pairing.js';

const empreinte = 'a'.repeat(64);

test('pairing: build puis parse redonnent les mêmes valeurs', () => {
  const entree = { addresses: ['192.168.1.20', '10.8.0.2'], port: 51234, token: 'abcDEF_-123', fingerprint: empreinte };
  const url = buildPairingUrl(entree);
  assert.ok(url.startsWith('miroir://pair?'));
  assert.deepEqual(parsePairingUrl(url), entree);
});

test('pairing: parse renvoie null si l\'empreinte ne fait pas 64 caractères hexadécimaux', () => {
  const url = 'miroir://pair?a=192.168.1.20&p=1234&t=abc&f=' + 'z'.repeat(64);
  assert.equal(parsePairingUrl(url), null);
  assert.equal(parsePairingUrl('miroir://pair?a=192.168.1.20&p=1234&t=abc&f=abcd'), null);
});

test('pairing: parse renvoie null pour un autre schéma ou un port invalide', () => {
  assert.equal(parsePairingUrl('https://pair?a=1.2.3.4&p=1234&t=abc&f=' + empreinte), null);
  assert.equal(parsePairingUrl('miroir://pair?a=1.2.3.4&p=70000&t=abc&f=' + empreinte), null);
  assert.equal(parsePairingUrl('miroir://pair?a=&p=1234&t=abc&f=' + empreinte), null);
});
