import { randomBytes } from 'node:crypto';

const nouveauSecret = () => randomBytes(16).toString('base64url');

export function createSessionStore({ now = Date.now, ttlMs = 120000, resumeMs = 120000 } = {}) {
  const jetons = new Map(); // jeton -> instant d'émission
  let resumeKey = null;
  let deconnecteA = null;
  let actif = false;

  return {
    issueToken() {
      const jeton = nouveauSecret();
      jetons.set(jeton, now());
      return jeton;
    },
    redeemToken(jeton) {
      const emisA = jetons.get(jeton);
      if (emisA === undefined) return { ok: false, reason: 'invalid_token' };
      jetons.delete(jeton);
      if (now() - emisA > ttlMs) return { ok: false, reason: 'expired' };
      resumeKey = nouveauSecret();
      deconnecteA = null;
      return { ok: true, resumeKey };
    },
    resume(cle) {
      if (resumeKey === null || cle !== resumeKey) return false;
      if (deconnecteA !== null && now() - deconnecteA > resumeMs) return false;
      deconnecteA = null;
      return true;
    },
    markDisconnected() {
      deconnecteA = now();
    },
    hasActivePhone: () => actif,
    setActivePhone(valeur) {
      actif = Boolean(valeur);
    },
  };
}
