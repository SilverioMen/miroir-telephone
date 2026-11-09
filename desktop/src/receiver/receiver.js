// Page de réception : reçoit la vidéo WebRTC du téléphone via la signalisation locale.
const attente = document.getElementById('attente');
const direct = document.getElementById('direct');
const video = document.getElementById('video');
const etat = document.getElementById('etat');
const qr = document.getElementById('qr');
const debug = document.getElementById('debug');
const modeDebug = new URLSearchParams(location.search).get('debug') === '1';

let pc = null;
let ws = null;
let statsTimer = null;

function afficher(connecte) {
  attente.classList.toggle('cache', connecte);
  direct.classList.toggle('cache', !connecte);
  if (!connecte) {
    etat.textContent = 'En attente du téléphone';
    qr.src = '/qr.svg?' + Date.now();
  }
}

function envoyer(obj) {
  if (ws && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(obj));
}

function fermerConnexion() {
  clearInterval(statsTimer);
  if (pc) { pc.close(); pc = null; }
  video.srcObject = null;
  debug.style.display = 'none';
  afficher(false);
}

function surveiller() {
  if (!modeDebug) return;
  debug.style.display = 'block';
  statsTimer = setInterval(async () => {
    if (!pc) return;
    const stats = await pc.getStats();
    let rtt = 0;
    let tampon = 0;
    stats.forEach((r) => {
      if (r.type === 'candidate-pair' && r.state === 'succeeded' && r.currentRoundTripTime) rtt = r.currentRoundTripTime * 1000;
      if (r.type === 'inbound-rtp' && r.kind === 'video' && r.jitterBufferEmittedCount) {
        tampon = (r.jitterBufferDelay / r.jitterBufferEmittedCount) * 1000;
      }
    });
    debug.textContent = `latence estimée ${Math.round(rtt / 2 + tampon)} ms (aller-retour ${Math.round(rtt)} ms, tampon ${Math.round(tampon)} ms)`;
  }, 1000);
}

async function surOffre(msg) {
  fermerConnexion();
  pc = new RTCPeerConnection({ iceServers: [] });
  pc.onicecandidate = (e) => { if (e.candidate) envoyer({ type: 'ice', candidate: e.candidate.toJSON() }); };
  pc.ontrack = (e) => {
    video.srcObject = e.streams[0];
    etat.textContent = 'Connecté';
    afficher(true);
  };
  await pc.setRemoteDescription({ type: 'offer', sdp: msg.sdp });
  const reponse = await pc.createAnswer();
  await pc.setLocalDescription(reponse);
  envoyer({ type: 'answer', sdp: reponse.sdp });
  surveiller();
}

function ouvrir() {
  ws = new WebSocket(`wss://${location.host}/ws/receiver`);
  ws.onmessage = async (e) => {
    const msg = JSON.parse(e.data);
    if (msg.type === 'offer') await surOffre(msg);
    else if (msg.type === 'ice' && pc) await pc.addIceCandidate(msg.candidate);
    else if (msg.type === 'bye') fermerConnexion();
  };
  ws.onclose = () => setTimeout(ouvrir, 1000);
}

// Le jeton du QR expire : on le renouvelle tant qu'aucun téléphone n'est connecté.
setInterval(() => { if (!pc) qr.src = '/qr.svg?' + Date.now(); }, 100000);
video.addEventListener('resize', () => {
  if (window.miroir && video.videoWidth) window.miroir.resize(video.videoWidth, video.videoHeight);
});
ouvrir();
