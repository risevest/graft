import { createUnplugin } from 'unplugin';
import { writeManifest } from './manifest.mjs';

/**
 * Writes the release manifest once the build output is complete.
 *
 * `writeBundle` is the only hook this needs, and it is the one hook unplugin unifies across every
 * bundler it supports — it carries no arguments, which is exactly right here: the bundler is being
 * asked for the timing, not for an inventory. A bundler's own record of what it emitted is not the
 * shipped file set, because static assets are copied in without passing through it.
 *
 * The hook passes no output directory either, so `dir` must be supplied.
 */
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
