import { test, before } from 'node:test';
import assert from 'node:assert/strict';
import { WebSocket } from 'ws';
import { createSessionStore } from '../src/core/session.js';
import { generateCert } from '../src/core/cert.js';
import { startServer } from '../src/core/server.js';
import { parsePairingUrl } from '../src/core/pairing.js';

let cert;
before(async () => { cert = await generateCert(); });

async function demarre(options = {}) {
  const sessions = createSessionStore({ now: () => 1000 });
  return startServer({ sessions, cert, port: 0, addresses: () => ['192.168.1.20'], ...options });
}

function ouvre(srv, chemin, opts = {}) {
  const ws = new WebSocket(`wss://127.0.0.1:${srv.port}${chemin}`, { rejectUnauthorized: false, ...opts });
  const recus = [];
  const attente = [];
  ws.on('message', (d) => { const m = JSON.parse(d.toString()); const w = attente.shift(); if (w) w(m); else recus.push(m); });
  ws.suivant = () => new Promise((ok, ko) => {
    if (recus.length) return ok(recus.shift());
    const t = setTimeout(() => ko(new Error('timeout')), 2000);
    attente.push((m) => { clearTimeout(t); ok(m); });
  });
  ws.ferme = new Promise((ok) => ws.once('close', ok));
  ws.pret = new Promise((ok, ko) => { ws.once('open', ok); ws.once('error', ko); });
  return ws;
}

async function appaire(srv, opts) {
  const tel = ouvre(srv, '/ws/phone', opts);
  await tel.pret;
  tel.send(JSON.stringify({ type: 'hello', token: parsePairingUrl(srv.pairingUrl()).token }));
  assert.equal((await tel.suivant()).type, 'ready');
  return tel;
}

test('fin: close() du serveur envoie bye au telephone', async () => {
  const srv = await demarre();
  const tel = await appaire(srv);
  await srv.close();
  assert.deepEqual(await tel.suivant(), { type: 'bye' });
});

test('fin: bye du recepteur ferme la socket du telephone', async () => {
  const srv = await demarre();
  const tel = await appaire(srv);
  const rec = ouvre(srv, '/ws/receiver'); await rec.pret;
  rec.send(JSON.stringify({ type: 'bye' }));
  assert.deepEqual(await tel.suivant(), { type: 'bye' });
  await Promise.race([tel.ferme, new Promise((_, ko) => setTimeout(() => ko(new Error('socket telephone toujours ouverte')), 1000))]);
  rec.close(); await srv.close();
});

test('fin: telephone muet (sans pong) est coupe par le ping serveur', async () => {
  const srv = await demarre({ pingMs: 50 });
  const tel = await appaire(srv, { autoPong: false });
  await Promise.race([tel.ferme, new Promise((_, ko) => setTimeout(() => ko(new Error('pas de coupure')), 1500))]);
  await srv.close();
});

test('fin: un telephone qui envoie ses propres ping reste en vie meme sans repondre aux notres', async () => {
  const srv = await demarre({ pingMs: 50 });
  const tel = await appaire(srv, { autoPong: false });
  const rythme = setInterval(() => { try { tel.ping(); } catch { /* socket fermee */ } }, 20);
  let ferme = false;
  tel.ferme.then(() => { ferme = true; });
  await new Promise((r) => setTimeout(r, 500));
  clearInterval(rythme);
  assert.equal(ferme, false, 'le telephone actif a ete coupe');
  tel.close(); await srv.close();
});
