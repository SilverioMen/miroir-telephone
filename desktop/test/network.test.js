import { test } from 'node:test';
import assert from 'node:assert/strict';
import { listLocalAddresses } from '../src/core/network.js';

const v4 = (address, internal = false) => ({ address, family: 'IPv4', internal });

test('network: ignore loopback, link-local et IPv6', () => {
  const res = listLocalAddresses({
    lo0: [v4('127.0.0.1', true), { address: '::1', family: 'IPv6', internal: true }],
    en0: [{ address: 'fe80::1', family: 'IPv6', internal: false }, v4('169.254.10.2'), v4('192.168.1.20')],
  });
  assert.deepEqual(res, ['192.168.1.20']);
});

test('network: conserve toutes les adresses IPv4 locales avec VPN et Wi-Fi', () => {
  const res = listLocalAddresses({
    utun3: [v4('10.8.0.2')],
    en0: [v4('192.168.1.20')],
  });
  assert.deepEqual(res, ['192.168.1.20', '10.8.0.2']);
});

test('network: aucune interface donne une liste vide', () => {
  assert.deepEqual(listLocalAddresses({}), []);
});
