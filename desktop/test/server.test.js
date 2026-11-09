import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import tls from 'node:tls';
import { WebSocket } from 'ws';
import { createSessionStore } from '../src/core/session.js';
import { generateCert } from '../src/core/cert.js';
import { startServer } from '../src/core/server.js';
import { parsePairingUrl } from '../src/core/pairing.js';
import { listLocalAddresses } from '../src/core/network.js';

let cert;
before(async () => { cert = await generateCert(); });

async function demarre(options = {}) {
  const horloge = { t: 1000 };
  const sessions = createSessionStore({ now: () => horloge.t });
  const srv = await startServer({ sessions, cert, port: 0, addresses: () => ['192.168.1.20'], ...options });
  return { sessions, srv, horloge };
}

function ouvre(srv, chemin, hote = '127.0.0.1') {
  const ws = new WebSocket(`wss://${hote}:${srv.port}${chemin}`, { rejectUnauthorized: false });
  const recus = [];
  const attente = [];
  ws.on('message', (data) => {
    const msg = JSON.parse(data.toString());
    const w = attente.shift();
    if (w) w(msg); else recus.push(msg);
  });
  ws.suivant = () => new Promise((ok, ko) => {
    if (recus.length) return ok(recus.shift());
    const timer = setTimeout(() => ko(new Error('timeout en attente d\'un message')), 2000);
    attente.push((m) => { clearTimeout(timer); ok(m); });
  });
  ws.envoie = (obj) => ws.send(JSON.stringify(obj));
  ws.pret = new Promise((ok, ko) => { ws.once('open', ok); ws.once('error', ko); });
  return ws;
}

const jetonDe = (srv) => parsePairingUrl(srv.pairingUrl()).token;

test('server: hello valide renvoie ready avec resumeKey', async () => {
  const { srv } = await demarre();
  const tel = ouvre(srv, '/ws/phone');
  await tel.pret;
  tel.envoie({ type: 'hello', token: jetonDe(srv) });
  const rep = await tel.suivant();
  assert.equal(rep.type, 'ready');
  assert.equal(typeof rep.resumeKey, 'string');
  tel.close(); await srv.close();
});

test('server: jeton déjà utilisé renvoie error invalid_token', async () => {
  const { srv } = await demarre();
  const jeton = jetonDe(srv);
  const a = ouvre(srv, '/ws/phone'); await a.pret;
  a.envoie({ type: 'hello', token: jeton });
  assert.equal((await a.suivant()).type, 'ready');
  a.close();
  await new Promise((r) => setTimeout(r, 50));
  const b = ouvre(srv, '/ws/phone'); await b.pret;
  b.envoie({ type: 'hello', token: jeton });
  assert.deepEqual(await b.suivant(), { type: 'error', reason: 'invalid_token' });
  b.close(); await srv.close();
});

test('server: jeton expiré renvoie error expired', async () => {
  const { srv, horloge } = await demarre();
  const jeton = jetonDe(srv);
  horloge.t += 120001;
  const tel = ouvre(srv, '/ws/phone'); await tel.pret;
  tel.envoie({ type: 'hello', token: jeton });
  assert.deepEqual(await tel.suivant(), { type: 'error', reason: 'expired' });
  tel.close(); await srv.close();
});

test('server: un deuxième téléphone pendant une session renvoie error busy', async () => {
  const { srv } = await demarre();
  const a = ouvre(srv, '/ws/phone'); await a.pret;
  a.envoie({ type: 'hello', token: jetonDe(srv) });
  assert.equal((await a.suivant()).type, 'ready');
  const b = ouvre(srv, '/ws/phone'); await b.pret;
  b.envoie({ type: 'hello', token: jetonDe(srv) });
  assert.deepEqual(await b.suivant(), { type: 'error', reason: 'busy' });
  a.close(); b.close(); await srv.close();
});

test('server: resume accepté dans les 120000 ms, refusé ensuite', async () => {
  const { srv, horloge } = await demarre();
  const a = ouvre(srv, '/ws/phone'); await a.pret;
  a.envoie({ type: 'hello', token: jetonDe(srv) });
  const { resumeKey } = await a.suivant();
  a.close();
  await new Promise((r) => setTimeout(r, 50));
  horloge.t += 30000;
  const b = ouvre(srv, '/ws/phone'); await b.pret;
  b.envoie({ type: 'resume', resumeKey });
  assert.equal((await b.suivant()).type, 'ready');
  b.close();
  await new Promise((r) => setTimeout(r, 50));
  horloge.t += 120001;
  const c = ouvre(srv, '/ws/phone'); await c.pret;
  c.envoie({ type: 'resume', resumeKey });
  assert.deepEqual(await c.suivant(), { type: 'error', reason: 'expired' });
  c.close(); await srv.close();
});

test('server: offer du téléphone arrive au récepteur et answer revient', async () => {
  const { srv } = await demarre();
  const rec = ouvre(srv, '/ws/receiver'); await rec.pret;
  const tel = ouvre(srv, '/ws/phone'); await tel.pret;
  tel.envoie({ type: 'hello', token: jetonDe(srv) });
  await tel.suivant();
  tel.envoie({ type: 'offer', sdp: 'v=0 offre' });
  assert.deepEqual(await rec.suivant(), { type: 'offer', sdp: 'v=0 offre' });
  rec.envoie({ type: 'answer', sdp: 'v=0 reponse' });
  assert.deepEqual(await tel.suivant(), { type: 'answer', sdp: 'v=0 reponse' });
  tel.close(); rec.close(); await srv.close();
});

test('server: les messages avant hello ne sont pas relayés', async () => {
  const { srv } = await demarre();
  const rec = ouvre(srv, '/ws/receiver'); await rec.pret;
  const intrus = ouvre(srv, '/ws/phone'); await intrus.pret;
  intrus.envoie({ type: 'offer', sdp: 'pirate' });
  await assert.rejects(rec.suivant(), /timeout/);
  intrus.close(); rec.close(); await srv.close();
});

test('server: /ws/receiver est refusé depuis une adresse non loopback', async (t) => {
  const lan = listLocalAddresses()[0];
  if (!lan) return t.skip('aucune adresse réseau locale disponible');
  const { srv } = await demarre();
  const ws = ouvre(srv, '/ws/receiver', lan);
  await assert.rejects(ws.pret);
  await srv.close();
});

test('cert: l\'empreinte annoncée correspond au certificat servi', async () => {
  const { srv } = await demarre();
  const servie = await new Promise((ok, ko) => {
    const s = tls.connect({ host: '127.0.0.1', port: srv.port, rejectUnauthorized: false }, () => {
      const f = s.getPeerCertificate().fingerprint256.replaceAll(':', '').toLowerCase();
      s.end(); ok(f);
    });
    s.on('error', ko);
  });
  assert.equal(servie, cert.fingerprint);
  assert.equal(parsePairingUrl(srv.pairingUrl()).fingerprint, cert.fingerprint);
  await srv.close();
});
