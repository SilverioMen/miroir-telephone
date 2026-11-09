import { test, before } from 'node:test';
import assert from 'node:assert/strict';
import https from 'node:https';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createSessionStore } from '../src/core/session.js';
import { generateCert } from '../src/core/cert.js';
import { startServer } from '../src/core/server.js';
import { listLocalAddresses } from '../src/core/network.js';

const staticDir = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'src', 'receiver');
let cert;
before(async () => { cert = await generateCert(); });

const demarre = () => startServer({
  sessions: createSessionStore(), cert, port: 0, staticDir, addresses: () => ['192.168.1.20'],
});

function get(port, chemin, hote = '127.0.0.1') {
  return new Promise((ok, ko) => {
    https.get({ host: hote, port, path: chemin, rejectUnauthorized: false }, (res) => {
      let corps = '';
      res.on('data', (d) => { corps += d; });
      res.on('end', () => ok({ status: res.statusCode, type: res.headers['content-type'], csp: res.headers['content-security-policy'], corps }));
    }).on('error', ko);
  });
}

test('receiver-assets: GET /receiver renvoie du HTML contenant une balise video', async (t) => {
  const srv = await demarre();
  t.after(() => srv.close());
  const rep = await get(srv.port, '/receiver');
  assert.equal(rep.status, 200);
  assert.match(rep.type, /text\/html/);
  assert.match(rep.corps, /<video/);
});

test('receiver-assets: la page propose l\'onglet iPhone, le bouton Arrêter et la note sur le noir', async (t) => {
  const srv = await demarre();
  t.after(() => srv.close());
  const { corps } = await get(srv.port, '/receiver');
  assert.match(corps, /iPhone/);
  assert.match(corps, /Recopie de l'écran/);
  assert.match(corps, /Arrêter/);
  assert.match(corps, /apparaît en noir/);
});

test('receiver-assets: la page est servie avec une politique de sécurité de contenu stricte', async (t) => {
  const srv = await demarre();
  t.after(() => srv.close());
  const { csp } = await get(srv.port, '/receiver');
  assert.match(csp, /default-src 'self'/);
  assert.match(csp, /script-src 'self'/);
  assert.doesNotMatch(csp, /unsafe-eval/);
});

test('receiver-assets: GET /receiver.js renvoie du JavaScript', async (t) => {
  const srv = await demarre();
  t.after(() => srv.close());
  const rep = await get(srv.port, '/receiver.js');
  assert.equal(rep.status, 200);
  assert.match(rep.type, /javascript/);
});

test('receiver-assets: GET /qr.svg renvoie du SVG avec une adresse du Mac', async (t) => {
  const srv = await demarre();
  t.after(() => srv.close());
  const rep = await get(srv.port, '/qr.svg');
  assert.equal(rep.status, 200);
  assert.match(rep.type, /image\/svg\+xml/);
  assert.match(rep.corps, /<svg/);
});

test('receiver-assets: les routes sont refusées depuis une adresse non loopback', async (t) => {
  const lan = listLocalAddresses()[0];
  if (!lan) return t.skip('aucune adresse réseau locale disponible');
  const srv = await demarre();
  t.after(() => srv.close());
  assert.equal((await get(srv.port, '/receiver', lan)).status, 403);
  assert.equal((await get(srv.port, '/qr.svg', lan)).status, 403);
});

test('receiver-assets: une route inconnue renvoie 404', async (t) => {
  const srv = await demarre();
  t.after(() => srv.close());
  assert.equal((await get(srv.port, '/../package.json')).status, 404);
});
