import { createHash } from 'node:crypto';
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';

/**
 * Everything version-controlled that ends up compiled into the binary. A Capacitor app's `ios/` and
 * `android/` directories are generated from these and are conventionally gitignored, so they are
 * outputs rather than inputs; fingerprinting them instead would make every regeneration look like a
 * native change.
 */
export const DEFAULT_SOURCES = [
  'native',
  'capacitor.config.ts',
  'capacitor.config.json',
  'capacitor.config.js',
];

/**
 * Capacitor discovers plugins from the app's declared dependencies, so mirroring that is what makes
 * this the same set the build compiles.
 */
export function isNativePackage(directory) {
  if (existsSync(join(directory, 'ios')) || existsSync(join(directory, 'android')))
    return true;
  try {
    if (readdirSync(directory).some(entry => entry.endsWith('.podspec'))) return true;
    return Boolean(
      JSON.parse(readFileSync(join(directory, 'package.json'), 'utf8')).capacitor,
    );
  } catch {
    return false;
  }
}

export function nativePackages(root) {
  const manifest = JSON.parse(readFileSync(join(root, 'package.json'), 'utf8'));
  const names = Object.keys({
    ...manifest.dependencies,
    ...manifest.devDependencies,
  }).sort();
  const found = [];
  for (const name of names) {
    const directory = join(root, 'node_modules', ...name.split('/'));
    if (!existsSync(directory) || !isNativePackage(directory)) continue;
    const { version } = JSON.parse(
      readFileSync(join(directory, 'package.json'), 'utf8'),
    );
    found.push(`${name}@${version}`);
  }
  return found;
}

function* walk(from) {
  const entries = readdirSync(from, { withFileTypes: true }).sort((a, b) =>
    a.name < b.name ? -1 : 1,
  );
  for (const entry of entries) {
    const path = join(from, entry.name);
    if (entry.isDirectory()) yield* walk(path);
    else if (entry.isFile()) yield path;
  }
}

export function nativeSources(root, paths = DEFAULT_SOURCES) {
  const entries = [];
  for (const candidate of paths) {
    const absolute = join(root, candidate);
    if (!existsSync(absolute)) continue;
    const files = statSync(absolute).isDirectory() ? [...walk(absolute)] : [absolute];
    for (const file of files) {
      entries.push({
        path: relative(root, file).split(sep).join('/'),
        sha256: createHash('sha256').update(readFileSync(file)).digest('hex'),
      });
    }
  }
  return entries.sort((a, b) => (a.path < b.path ? -1 : 1));
}

export function digest({ packages, sources }) {
  const hash = createHash('sha256');
  for (const entry of packages) hash.update(`pkg ${entry}\n`);
  for (const entry of sources) hash.update(`src ${entry.path} ${entry.sha256}\n`);
  return hash.digest('hex').slice(0, 32);
}

export function fingerprint(root, { sources: paths = DEFAULT_SOURCES } = {}) {
  const packages = nativePackages(root);
  const sources = nativeSources(root, paths);
  return { hash: digest({ packages, sources }), packages, sources };
}

/**
 * @returns The differences between two fingerprints, so a mismatch can say what moved rather than
 *   only that something did.
 */
export function describeChange(before, after) {
  const changed = [];
  const packagesBefore = new Set(before?.packages ?? []);
  const packagesAfter = new Set(after?.packages ?? []);
  for (const entry of packagesAfter)
    if (!packagesBefore.has(entry)) changed.push(`+ ${entry}`);
  for (const entry of packagesBefore)
    if (!packagesAfter.has(entry)) changed.push(`- ${entry}`);

  const sourcesBefore = new Map(
    (before?.sources ?? []).map(entry => [entry.path, entry.sha256]),
  );
  const sourcesAfter = new Map(
    (after?.sources ?? []).map(entry => [entry.path, entry.sha256]),
  );
  for (const [path, sha256] of sourcesAfter) {
    if (!sourcesBefore.has(path)) changed.push(`+ ${path}`);
    else if (sourcesBefore.get(path) !== sha256) changed.push(`~ ${path}`);
  }
  for (const path of sourcesBefore.keys())
    if (!sourcesAfter.has(path)) changed.push(`- ${path}`);
  return changed.sort();
}
