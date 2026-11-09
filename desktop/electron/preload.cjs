// Pont minimal et sans donnees : la page ne peut demander que ces trois actions.
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('miroir', {
  redimensionner: (largeur, hauteur) => {
    if (Number.isFinite(largeur) && Number.isFinite(hauteur)) ipcRenderer.send('redimensionner', largeur, hauteur);
  },
  reset: () => ipcRenderer.send('reinitialiser'),
  ouvrirReglagesPartage: () => ipcRenderer.send('ouvrir-reglages-partage'),
});
