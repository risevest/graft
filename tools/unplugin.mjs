import { writeFileSync } from 'node:fs';
import { createUnplugin } from 'unplugin';
import { writeManifest } from './manifest.mjs';
import { collectRequires, describeContract } from './requires.mjs';

// writeBundle carries no output directory, so `dir` must be supplied.
export const graftManifest = createUnplugin((options = {}) => {
  const { dir = 'dist', ...rest } = options;
  return {
    name: 'graft-manifest',
    writeBundle() {
      const { manifest, output } = writeManifest({ dir, ...rest });
      this.warn?.(
        `graft: ${output} describes ${manifest.files.length} files, counter ${manifest.counter}`,
      );
    },
  };
});

export const graftRequires = createUnplugin((options = {}) => {
  const { out = 'graft-requires.json' } = options;
  const names = new Set();
  const problems = [];

  return {
    name: 'graft-requires',
    enforce: 'pre',
    transform(code, id) {
      const path = id.split('?')[0];
      if (!/\.(?:[cm]?[jt]sx?)$/.test(path)) return null;
      const found = collectRequires(code, {
        firstParty: !path.includes('node_modules'),
      });
      for (const name of found.names) names.add(name);
      if (found.unresolved > 0) {
        problems.push(
          `${id}: ${found.unresolved} registerPlugin call(s) without a literal name`,
        );
      }
      if (found.dynamic > 0) {
        problems.push(`${id}: ${found.dynamic} use(s) of Capacitor.Plugins`);
      }
      return null;
    },
    writeBundle() {
      if (problems.length > 0) {
        throw new Error(
          `graft: the plugin contract cannot be derived from this bundle.\n  ${problems.join('\n  ')}\n` +
            'Every plugin must be reached through registerPlugin("Name") with a literal name.',
        );
      }
      const requires = describeContract(names);
      writeFileSync(out, `${JSON.stringify(requires, null, 2)}\n`);
      this.warn?.(
        `graft: ${requires.length} plugins required — ${requires.join(', ')}`,
      );
    },
  };
});

export const vite = graftManifest.vite;
export const rollup = graftManifest.rollup;
export const rolldown = graftManifest.rolldown;
export const webpack = graftManifest.webpack;
export const rspack = graftManifest.rspack;
export const esbuild = graftManifest.esbuild;
export const farm = graftManifest.farm;

export const requiresVite = graftRequires.vite;
export const requiresRollup = graftRequires.rollup;
export const requiresRolldown = graftRequires.rolldown;
export const requiresWebpack = graftRequires.webpack;
export const requiresRspack = graftRequires.rspack;
export const requiresEsbuild = graftRequires.esbuild;
export const requiresFarm = graftRequires.farm;
