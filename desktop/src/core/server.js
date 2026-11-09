import https from 'node:https';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import QRCode from 'qrcode';
import { WebSocketServer } from 'ws';
import { buildPairingUrl } from './pairing.js';
import { listLocalAddresses } from './network.js';

const LOOPBACK = new Set(['127.0.0.1', '::1', '::ffff:127.0.0.1']);
const DELAI_HELLO_MS = 5000;
const CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
  + "connect-src 'self' wss://127.0.0.1:*; media-src 'self' blob:";

const versTelephone = new Set(['answer', 'ice', 'bye']);
const versRecepteur = new Set(['offer', 'ice', 'bye']);

function lire(data) {
  try {
    const msg = JSON.parse(data.toString());
    return msg && typeof msg.type === 'string' ? msg : null;
  } catch {
    return null;
  }
}

function envoyer(ws, obj) {
  if (ws && ws.readyState === 1) ws.send(JSON.stringify(obj));
}

const FICHIERS = {
  '/receiver': ['index.html', 'text/html; charset=utf-8'],
  '/receiver.js': ['receiver.js', 'text/javascript; charset=utf-8'],
};

export async function startServer({ sessions, cert, port = 0, staticDir = null, addresses = listLocalAddresses, pingMs = 15000 }) {
  let portReel = 0;
  const pairingUrl = () => buildPairingUrl({
    addresses: addresses(),
    port: portReel,
    token: sessions.issueToken(),
    fingerprint: cert.fingerprint,
  });

  async function repondre(req, res) {
    const chemin = new URL(req.url, 'https://miroir.local').pathname;
    const route = FICHIERS[chemin];
    const connu = route || chemin === '/qr.svg';
    if (!connu) {
      res.statusCode = 404;
      return res.end();
    }
    if (!LOOPBACK.has(req.socket.remoteAddress)) {
      res.statusCode = 403;
      return res.end();
    }
    try {
      if (chemin === '/qr.svg') {
        const svg = await QRCode.toString(pairingUrl(), { type: 'svg', margin: 1 });
        res.writeHead(200, { 'Content-Type': 'image/svg+xml', 'Cache-Control': 'no-store' });
        return res.end(svg);
      }
      const [fichier, type] = route;
      const contenu = await readFile(path.join(staticDir, fichier));
      res.writeHead(200, { 'Content-Type': type, 'Cache-Control': 'no-store', 'Content-Security-Policy': CSP });
      res.end(contenu);
    } catch {
      res.statusCode = 500;
      res.end();
    }
  }

  const server = https.createServer({ key: cert.key, cert: cert.cert }, repondre);
  const wss = new WebSocketServer({ noServer: true });
  const abonnes = [];
  let telephone = null;
  let recepteur = null;
  let derniereOffre = null;

  // Detecte un telephone disparu sans fermeture TCP : pas de pong avant le ping suivant, on coupe.
  const battement = setInterval(() => {
    wss.clients.forEach((c) => {
      if (c.vivant === false) return c.terminate();
      c.vivant = false;
      c.ping();
    });
  }, pingMs);
  battement.unref();
  // wss.emit('connection') n'est pas appele avec handleUpgrade(..., cb) : on branche le suivi nous-memes.
  function suivreVie(c) {
    c.vivant = true;
    // Tout signe de vie compte : pong, ping du telephone (OkHttp en envoie toutes les 10 s) ou message.
    const vivant = () => { c.vivant = true; };
    c.on('pong', vivant);
    c.on('ping', vivant);
    c.on('message', vivant);
  }

  const notifier = (etat) => {
    if (process.env.MIROIR_DEBUG === '1') console.log(`[${new Date().toISOString()}] telephone ${etat}`);
    abonnes.forEach((cb) => cb(etat));
  };

  function accepter(ws, resumeKey) {
    if (telephone && telephone !== ws) {
      const ancien = telephone;
      telephone = null;
      ancien.terminate();
    }
    telephone = ws;
    sessions.setActivePhone(true);
    notifier('connected');
    envoyer(ws, { type: 'ready', resumeKey });
  }

  function attacherTelephone(ws) {
    let authentifie = false;
    const minuteur = setTimeout(() => { if (!authentifie) ws.close(); }, DELAI_HELLO_MS);

    ws.on('message', (data) => {
      const msg = lire(data);
      if (!msg) return;
      if (!authentifie) {
        if (msg.type === 'hello') {
          if (sessions.hasActivePhone()) {
            envoyer(ws, { type: 'error', reason: 'busy' });
            return ws.close();
          }
          const res = sessions.redeemToken(msg.token);
          if (!res.ok) {
            envoyer(ws, { type: 'error', reason: res.reason });
            return ws.close();
          }
          authentifie = true;
          accepter(ws, res.resumeKey);
        } else if (msg.type === 'resume') {
          if (!sessions.resume(msg.resumeKey)) {
            envoyer(ws, { type: 'error', reason: 'expired' });
            return ws.close();
          }
          authentifie = true;
          accepter(ws, msg.resumeKey);
        }
        return;
      }
      if (!versRecepteur.has(msg.type)) return;
      if (msg.type === 'offer') derniereOffre = msg;
      if (msg.type === 'bye') derniereOffre = null;
      envoyer(recepteur, msg);
    });

    ws.on('close', (code) => {
      if (process.env.MIROIR_DEBUG === '1') console.log(`[${new Date().toISOString()}] socket telephone fermee code=${code}`);
      clearTimeout(minuteur);
      if (telephone !== ws) return;
      telephone = null;
      derniereOffre = null;
      sessions.setActivePhone(false);
      sessions.markDisconnected();
      envoyer(recepteur, { type: 'bye' });
      notifier('disconnected');
    });
  }

  function attacherRecepteur(ws) {
    recepteur = ws;
    if (derniereOffre) envoyer(ws, derniereOffre);
    ws.on('message', (data) => {
      const msg = lire(data);
      if (!msg || !versTelephone.has(msg.type)) return;
      envoyer(telephone, msg);
      // Arret demande depuis le Mac : on libere aussi la place du telephone.
      if (msg.type === 'bye' && telephone) telephone.close();
    });
    ws.on('close', () => { if (recepteur === ws) recepteur = null; });
  }

  server.on('upgrade', (req, socket, head) => {
    const chemin = new URL(req.url, 'https://miroir.local').pathname;
    const local = LOOPBACK.has(req.socket.remoteAddress);
    if (chemin === '/ws/phone') {
      wss.handleUpgrade(req, socket, head, (ws) => { suivreVie(ws); attacherTelephone(ws); });
    } else if (chemin === '/ws/receiver' && local) {
      wss.handleUpgrade(req, socket, head, (ws) => { suivreVie(ws); attacherRecepteur(ws); });
    } else {
      socket.write('HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n');
      socket.destroy();
    }
  });

  await new Promise((ok, ko) => {
    server.once('error', ko);
    server.listen(port, '0.0.0.0', ok);
  });
  portReel = server.address().port;

  return {
    port: portReel,
    pairingUrl,
    onPhoneState(cb) { abonnes.push(cb); },
    close() {
      clearInterval(battement);
      envoyer(telephone, { type: 'bye' });
      wss.clients.forEach((c) => c.terminate());
      wss.close();
      server.closeAllConnections();
      return new Promise((ok) => server.close(() => ok()));
    },
  };
}
