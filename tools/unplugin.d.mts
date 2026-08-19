import type { UnpluginInstance } from 'unplugin';

/**
 * Numbers may be given as strings: the CLI passes the raw argv values straight through, and every
 * ordinal is coerced and range-checked when the manifest is built.
 */
type Ordinal = number | string;

export interface GraftManifestOptions {
  id: Ordinal;
  counter: Ordinal;
  minNativeBuild: Ordinal;
  dir?: string;
  out?: string;
  channel?: string;
  notBefore?: Ordinal;
  expiresAt?: Ordinal;
  requires?: string[];
}

export interface GraftRequiresOptions {
  out?: string;
}

export declare const graftManifest: UnpluginInstance<GraftManifestOptions>;
export declare const graftRequires: UnpluginInstance<GraftRequiresOptions | void>;

export declare const vite: UnpluginInstance<GraftManifestOptions>['vite'];
export declare const rollup: UnpluginInstance<GraftManifestOptions>['rollup'];
export declare const rolldown: UnpluginInstance<GraftManifestOptions>['rolldown'];
export declare const webpack: UnpluginInstance<GraftManifestOptions>['webpack'];
export declare const rspack: UnpluginInstance<GraftManifestOptions>['rspack'];
export declare const esbuild: UnpluginInstance<GraftManifestOptions>['esbuild'];
export declare const farm: UnpluginInstance<GraftManifestOptions>['farm'];

export declare const requiresVite: UnpluginInstance<GraftRequiresOptions | void>['vite'];
export declare const requiresRollup: UnpluginInstance<GraftRequiresOptions | void>['rollup'];
export declare const requiresRolldown: UnpluginInstance<GraftRequiresOptions | void>['rolldown'];
export declare const requiresWebpack: UnpluginInstance<GraftRequiresOptions | void>['webpack'];
export declare const requiresRspack: UnpluginInstance<GraftRequiresOptions | void>['rspack'];
export declare const requiresEsbuild: UnpluginInstance<GraftRequiresOptions | void>['esbuild'];
export declare const requiresFarm: UnpluginInstance<GraftRequiresOptions | void>['farm'];
