import { test, before } from 'node:test';
import assert from 'node:assert/strict';
import { WebSocket } from 'ws';
import { createSessionStore } from '../src/core/session.js';
import { generateCert } from '../src/core/cert.js';
import { startServer } from '../src/core/server.js';
import { parsePairingUrl } from '../src/core/pairing.js';

let cert;
before(async () => { cert = await generateCert(); });

function ouvre(srv, chemin) {
  const ws = new WebSocket(`wss://127.0.0.1:${srv.port}${chemin}`, { rejectUnauthorized: false });
  const recus = [];
  const attente = [];
  ws.on('message', (d) => {
    const m = JSON.parse(d.toString());
    const w = attente.shift();
    if (w) w(m); else recus.push(m);
  });
  ws.suivant = () => new Promise((ok, ko) => {
    if (recus.length) return ok(recus.shift());
    const t = setTimeout(() => ko(new Error('timeout')), 2000);
    attente.push((m) => { clearTimeout(t); ok(m); });
  });
  ws.envoie = (o) => ws.send(JSON.stringify(o));
  ws.pret = new Promise((ok, ko) => { ws.once('open', ok); ws.once('error', ko); });
  return ws;
}

test('resume: coupure puis reprise, le récepteur reçoit bye puis la nouvelle offre', async (t) => {
  const h = { t: 1000 };
  const sessions = createSessionStore({ now: () => h.t });
  const srv = await startServer({ sessions, cert, addresses: () => ['192.168.1.20'] });
  t.after(() => srv.close());

  const rec = ouvre(srv, '/ws/receiver'); await rec.pret;
  const tel = ouvre(srv, '/ws/phone'); await tel.pret;
  tel.envoie({ type: 'hello', token: parsePairingUrl(srv.pairingUrl()).token });
  const { resumeKey } = await tel.suivant();
  tel.envoie({ type: 'offer', sdp: 'offre-1' });
  assert.deepEqual(await rec.suivant(), { type: 'offer', sdp: 'offre-1' });

  tel.close();
  assert.deepEqual(await rec.suivant(), { type: 'bye' });

  h.t += 30000;
  const tel2 = ouvre(srv, '/ws/phone'); await tel2.pret;
  tel2.envoie({ type: 'resume', resumeKey });
  assert.equal((await tel2.suivant()).type, 'ready');
  tel2.envoie({ type: 'offer', sdp: 'offre-2' });
  assert.deepEqual(await rec.suivant(), { type: 'offer', sdp: 'offre-2' });
  tel2.close();
});

test('resume: le récepteur peut arrêter le partage, le téléphone reçoit bye', async (t) => {
  const sessions = createSessionStore();
  const srv = await startServer({ sessions, cert, addresses: () => ['192.168.1.20'] });
  t.after(() => srv.close());
  const rec = ouvre(srv, '/ws/receiver'); await rec.pret;
  const tel = ouvre(srv, '/ws/phone'); await tel.pret;
  tel.envoie({ type: 'hello', token: parsePairingUrl(srv.pairingUrl()).token });
  await tel.suivant();
  rec.envoie({ type: 'bye' });
  assert.deepEqual(await tel.suivant(), { type: 'bye' });
  tel.close();
});
