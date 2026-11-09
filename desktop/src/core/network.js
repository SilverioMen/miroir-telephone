import os from 'node:os';

export function listLocalAddresses(interfaces = os.networkInterfaces()) {
  const adresses = [];
  for (const nom of Object.keys(interfaces).sort()) {
    for (const entree of interfaces[nom] ?? []) {
      if (entree.family !== 'IPv4' || entree.internal) continue;
      if (entree.address.startsWith('169.254.')) continue;
      adresses.push(entree.address);
    }
  }
  return adresses;
}
