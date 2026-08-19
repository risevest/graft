import { createHash } from 'node:crypto';
import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join, relative, sep } from 'node:path';

export const MANIFEST_FILE_NAME = 'graft-manifest.json';
const INDEX_FILE_NAME = 'index.html';
// Finder writes these into any directory it displays. Signed into a release they ship to every
// device, and they differ per machine, so they also break the byte-identical builds that deltas
// depend on.
const EXCLUDED_FILE_NAMES = new Set(['.DS_Store', 'Thumbs.db']);

function* walk(from) {
  for (const entry of readdirSync(from, { withFileTypes: true })) {
    const path = join(from, entry.name);
    if (entry.isDirectory()) yield* walk(path);
    else if (entry.isFile() && !EXCLUDED_FILE_NAMES.has(entry.name)) yield path;
  }
}

function ordinal(name, value) {
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < 0) {
    throw new Error(`${name} must be a non-negative integer, got '${value}'`);
  }
  return parsed;
}

// Sorted keys keep the bytes reproducible, and the bytes are what the signature covers.
function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.keys(value)
        .sort()
        .map(key => [key, canonical(value[key])]),
    );
  }
  return value;
}

export function buildManifest({
  dir,
  id,
  counter,
  minNativeBuild,
  channel,
  notBefore,
  expiresAt,
}) {
  for (const [name, value] of [
    ['dir', dir],
    ['id', id],
    ['counter', counter],
    ['minNativeBuild', minNativeBuild],
  ]) {
    if (value === undefined || value === null || value === '')
      throw new Error(`${name} is required`);
  }

  const files = [];
  for (const path of walk(dir)) {
    const href = relative(dir, path).split(sep).join('/');
    // Graft writes this into every bundle it installs; one already present would disagree with what was signed.
    if (href === MANIFEST_FILE_NAME) continue;
    const content = readFileSync(path);
    files.push({
      href,
      sha256: createHash('sha256').update(content).digest('hex'),
      size: content.length,
    });
  }
  files.sort((a, b) => (a.href < b.href ? -1 : a.href > b.href ? 1 : 0));

  if (!files.some(file => file.href === INDEX_FILE_NAME)) {
    throw new Error(
      `${dir} has no ${INDEX_FILE_NAME} at its root, so it is not a bundle`,
    );
  }

  const manifest = {
    schema: 1,
    id: String(id),
    counter: ordinal('counter', counter),
    minNativeBuild: ordinal('minNativeBuild', minNativeBuild),
    files,
  };
  if (channel) manifest.channel = channel;
  if (notBefore !== undefined)
    manifest.notBefore = ordinal('notBefore', notBefore);
  if (expiresAt !== undefined)
    manifest.expiresAt = ordinal('expiresAt', expiresAt);

  return {
    manifest,
    json: `${JSON.stringify(canonical(manifest), null, 2)}\n`,
  };
}

export function writeManifest(options) {
  const { manifest, json } = buildManifest(options);
  const output = options.out ?? join(options.dir, MANIFEST_FILE_NAME);
  writeFileSync(output, json);
  return { manifest, output };
}
