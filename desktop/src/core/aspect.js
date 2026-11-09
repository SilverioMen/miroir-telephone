const PAR_DEFAUT = { width: 420, height: 640 };
const LARGEUR_MIN = 240;
const PART_ECRAN = 0.8;

const valide = (n) => Number.isFinite(n) && n > 0;

/** Taille de fenetre qui conserve le format de la video et tient dans 80 % de l'ecran. */
export function fitWindowToVideo(video, screen) {
  if (!valide(video?.width) || !valide(video?.height) || !valide(screen?.width) || !valide(screen?.height)) {
    return { ...PAR_DEFAUT };
  }
  const echelle = Math.min((PART_ECRAN * screen.width) / video.width, (PART_ECRAN * screen.height) / video.height);
  let width = Math.round(video.width * echelle);
  let height = Math.round(video.height * echelle);
  if (width < LARGEUR_MIN) {
    width = LARGEUR_MIN;
    height = Math.round((LARGEUR_MIN * video.height) / video.width);
  }
  return { width, height };
}

export const TAILLE_ATTENTE = PAR_DEFAUT;
