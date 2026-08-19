import { createUnplugin } from 'unplugin';
import { writeManifest } from './manifest.mjs';

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

export const vite = graftManifest.vite;
export const rollup = graftManifest.rollup;
export const rolldown = graftManifest.rolldown;
export const webpack = graftManifest.webpack;
export const rspack = graftManifest.rspack;
export const esbuild = graftManifest.esbuild;
export const farm = graftManifest.farm;
