import { app, BrowserWindow } from 'electron';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createSessionStore } from '../src/core/session.js';
import { generateCert } from '../src/core/cert.js';
import { startServer } from '../src/core/server.js';

const racine = path.dirname(fileURLToPath(import.meta.url));
let serveur = null;

app.whenReady().then(async () => {
  const cert = await generateCert();
  serveur = await startServer({
    sessions: createSessionStore(),
    cert,
    staticDir: path.join(racine, '..', 'src', 'receiver'),
  });
  console.log(`Miroir: serveur sur le port ${serveur.port}`);

  // Certificat auto-signé accepté uniquement pour notre propre serveur local.
  app.on('certificate-error', (event, _wc, url, _erreur, _certificat, rappel) => {
    const u = new URL(url);
    const nous = u.hostname === '127.0.0.1' && u.port === String(serveur.port);
    if (nous) event.preventDefault();
    rappel(nous);
  });

  const fenetre = new BrowserWindow({
    width: 420,
    height: 640,
    title: 'Miroir Téléphone',
    webPreferences: { contextIsolation: true, nodeIntegration: false },
  });
  fenetre.loadURL(`https://127.0.0.1:${serveur.port}/receiver`);
});

app.on('window-all-closed', () => {
  if (serveur) serveur.close();
  app.quit();
});
