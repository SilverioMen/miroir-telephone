import { X509Certificate } from 'node:crypto';
import { generate } from 'selfsigned';

export async function generateCert() {
  const pems = await generate([{ name: 'commonName', value: 'miroir' }], {
    days: 30,
    keySize: 2048,
    algorithm: 'sha256',
  });
  const fingerprint = new X509Certificate(pems.cert).fingerprint256.replaceAll(':', '').toLowerCase();
  return { key: pems.private, cert: pems.cert, fingerprint };
}
