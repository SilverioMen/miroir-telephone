const SCHEMA = 'miroir://pair?';
const EMPREINTE = /^[0-9a-f]{64}$/;

export function buildPairingUrl({ addresses, port, token, fingerprint }) {
  const params = new URLSearchParams({ a: addresses.join(','), p: String(port), t: token, f: fingerprint });
  return SCHEMA + params.toString().replaceAll('%2C', ',');
}

export function parsePairingUrl(url) {
  if (typeof url !== 'string' || !url.startsWith(SCHEMA)) return null;
  const params = new URLSearchParams(url.slice(SCHEMA.length));
  const addresses = (params.get('a') ?? '').split(',').filter(Boolean);
  const port = Number(params.get('p'));
  const token = params.get('t') ?? '';
  const fingerprint = params.get('f') ?? '';
  if (addresses.length === 0 || !token) return null;
  if (!Number.isInteger(port) || port < 1 || port > 65535) return null;
  if (!EMPREINTE.test(fingerprint)) return null;
  return { addresses, port, token, fingerprint };
}
