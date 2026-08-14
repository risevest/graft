import type { ReleaseIdentity } from './definitions';

const GLOBAL_KEY = '__graft__';

/**
 * The identity of the bundle currently being served, or `null` when the plugin did not publish one
 * — on the web, or on a WebView that cannot run a document-start script.
 *
 * This exists so an app never has to compile a release identifier into its own bundle. Doing that
 * makes the bundle's bytes depend on which release produced it, and because bundlers name chunks
 * after a hash of their contents, changing that one value renames the chunk, renames every chunk
 * that references it, and defeats the file reuse an update relies on. Reading the identity here
 * instead keeps a release that changed no code byte-identical to the one before it.
 *
 * It is resolved synchronously, so it can be passed straight to a crash reporter's `init`.
 */
export function releaseIdentity(): ReleaseIdentity | null {
  const value = (globalThis as Record<string, unknown>)[GLOBAL_KEY];
  if (typeof value !== 'object' || value === null) {
    return null;
  }
  const { nativeBuild, releaseId } = value as Record<string, unknown>;
  if (releaseId !== null && typeof releaseId !== 'string') {
    return null;
  }
  if (nativeBuild !== null && typeof nativeBuild !== 'number') {
    return null;
  }
  return { nativeBuild, releaseId };
}
