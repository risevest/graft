import { sign } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';

// The signature covers the file's raw bytes: never re-serialise the JSON between signing and upload.
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
