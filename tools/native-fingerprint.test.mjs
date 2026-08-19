import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { after, test } from 'node:test';
import {
  describeChange,
  digest,
  fingerprint,
  isNativePackage,
  nativePackages,
} from './native-fingerprint.mjs';

const roots = [];

function makeRoot({ dependencies = {}, packages = {}, sources = {} } = {}) {
  const root = mkdtempSync(join(tmpdir(), 'graft-fp-'));
  roots.push(root);
  writeFileSync(join(root, 'package.json'), JSON.stringify({ dependencies }));
  for (const [name, spec] of Object.entries(packages)) {
    const directory = join(root, 'node_modules', ...name.split('/'));
    mkdirSync(directory, { recursive: true });
    writeFileSync(
      join(directory, 'package.json'),
      JSON.stringify({ name, version: spec.version, ...spec.extra }),
    );
    for (const marker of spec.markers ?? []) {
      if (marker.endsWith('/')) mkdirSync(join(directory, marker), { recursive: true });
      else writeFileSync(join(directory, marker), '');
    }
  }
  for (const [path, content] of Object.entries(sources)) {
    const absolute = join(root, path);
    mkdirSync(join(absolute, '..'), { recursive: true });
    writeFileSync(absolute, content);
  }
  return root;
}

after(() => roots.forEach(root => rmSync(root, { recursive: true, force: true })));

test('a package carrying an ios or android directory counts as native', () => {
  const root = makeRoot({
    packages: {
      withIos: { version: '1.0.0', markers: ['ios/'] },
      withAndroid: { version: '1.0.0', markers: ['android/'] },
      plain: { version: '1.0.0' },
    },
  });
  assert.equal(isNativePackage(join(root, 'node_modules', 'withIos')), true);
  assert.equal(isNativePackage(join(root, 'node_modules', 'withAndroid')), true);
  assert.equal(isNativePackage(join(root, 'node_modules', 'plain')), false);
});

test('a podspec or a capacitor field also counts, so no plugin is missed', () => {
  const root = makeRoot({
    packages: {
      pods: { version: '1.0.0', markers: ['Thing.podspec'] },
      declared: { version: '1.0.0', extra: { capacitor: { ios: {} } } },
    },
  });
  assert.equal(isNativePackage(join(root, 'node_modules', 'pods')), true);
  assert.equal(isNativePackage(join(root, 'node_modules', 'declared')), true);
});

test('only declared dependencies are fingerprinted, and they carry their versions', () => {
  const root = makeRoot({
    dependencies: { '@capacitor/app': '^8.0.0', lodash: '^4.0.0' },
    packages: {
      '@capacitor/app': { version: '8.1.0', markers: ['ios/'] },
      lodash: { version: '4.17.21' },
      '@capacitor/stray': { version: '9.0.0', markers: ['ios/'] },
    },
  });
  assert.deepEqual(nativePackages(root), ['@capacitor/app@8.1.0']);
});

test('a JS-only dependency change does not move the hash', () => {
  const shape = version => ({
    dependencies: { '@capacitor/app': '^8.0.0', lodash: '^4.0.0' },
    packages: {
      '@capacitor/app': { version: '8.1.0', markers: ['ios/'] },
      lodash: { version },
    },
  });
  assert.equal(
    fingerprint(makeRoot(shape('4.17.21'))).hash,
    fingerprint(makeRoot(shape('4.99.0'))).hash,
  );
});

test('bumping a native plugin moves the hash and names it', () => {
  const shape = version => ({
    dependencies: { '@capacitor/app': '^8.0.0' },
    packages: { '@capacitor/app': { version, markers: ['ios/'] } },
  });
  const before = fingerprint(makeRoot(shape('8.1.0')));
  const after = fingerprint(makeRoot(shape('8.2.0')));
  assert.notEqual(before.hash, after.hash);
  assert.deepEqual(describeChange(before, after), [
    '+ @capacitor/app@8.2.0',
    '- @capacitor/app@8.1.0',
  ]);
});

test('adding a native plugin moves the hash', () => {
  const base = {
    dependencies: { '@capacitor/app': '^8.0.0' },
    packages: { '@capacitor/app': { version: '8.1.0', markers: ['ios/'] } },
  };
  const before = fingerprint(makeRoot(base));
  const after = fingerprint(
    makeRoot({
      dependencies: { ...base.dependencies, '@capacitor/camera': '^8.0.0' },
      packages: {
        ...base.packages,
        '@capacitor/camera': { version: '8.0.1', markers: ['android/'] },
      },
    }),
  );
  assert.notEqual(before.hash, after.hash);
  assert.deepEqual(describeChange(before, after), ['+ @capacitor/camera@8.0.1']);
});

test('editing a native source moves the hash and names the file', () => {
  const before = fingerprint(
    makeRoot({ sources: { 'native/ios/SceneDelegate.swift': 'GraftViewController()' } }),
  );
  const after = fingerprint(
    makeRoot({ sources: { 'native/ios/SceneDelegate.swift': 'CAPBridgeViewController()' } }),
  );
  assert.notEqual(before.hash, after.hash);
  assert.deepEqual(describeChange(before, after), ['~ native/ios/SceneDelegate.swift']);
});

test('an unchanged tree fingerprints identically, so a web-only release still matches', () => {
  const shape = {
    dependencies: { '@capacitor/app': '^8.0.0' },
    packages: { '@capacitor/app': { version: '8.1.0', markers: ['ios/'] } },
    sources: {
      'capacitor.config.ts': 'export default {}',
      'native/ios/SceneDelegate.swift': 'x',
    },
  };
  assert.equal(fingerprint(makeRoot(shape)).hash, fingerprint(makeRoot(shape)).hash);
  assert.deepEqual(
    describeChange(fingerprint(makeRoot(shape)), fingerprint(makeRoot(shape))),
    [],
  );
});

test('extra sources are hashed when named, and ignored when not', () => {
  const shape = { sources: { 'trapeze.yaml': 'platforms: {}' } };
  const without = fingerprint(makeRoot(shape));
  const with_ = fingerprint(makeRoot(shape), { sources: ['native', 'trapeze.yaml'] });
  assert.deepEqual(without.sources, []);
  assert.deepEqual(
    with_.sources.map(entry => entry.path),
    ['trapeze.yaml'],
  );
  assert.notEqual(without.hash, with_.hash);
});

test('the digest depends on both halves, not just one', () => {
  const packages = ['a@1'];
  const sources = [{ path: 'native/x', sha256: 'aa' }];
  assert.notEqual(digest({ packages, sources }), digest({ packages: ['a@2'], sources }));
  assert.notEqual(
    digest({ packages, sources }),
    digest({ packages, sources: [{ path: 'native/x', sha256: 'bb' }] }),
  );
});

test('a missing prior fingerprint reports everything as added rather than throwing', () => {
  const after = fingerprint(
    makeRoot({
      dependencies: { '@capacitor/app': '^8.0.0' },
      packages: { '@capacitor/app': { version: '8.1.0', markers: ['ios/'] } },
    }),
  );
  assert.deepEqual(describeChange(undefined, after), ['+ @capacitor/app@8.1.0']);
});
