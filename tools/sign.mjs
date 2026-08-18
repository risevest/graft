import { sign } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';

/**
 * The device verifies SHA256withRSA over the manifest's raw bytes, so the file as written is the
 * signed form. Never re-serialise the JSON between signing and upload — reordering a key or changing
 * whitespace produces a manifest the signature no longer covers.
 */
export function signManifest(manifestPath, privateKey) {
  if (!privateKey) throw new Error('a private key is required');
  return sign('sha256', readFileSync(manifestPath), privateKey).toString(
    'base64',
  );
}

export function signManifestToFile(manifestPath, signaturePath, privateKey) {
  const signature = signManifest(manifestPath, privateKey);
  writeFileSync(signaturePath, signature);
  return signature;
}
