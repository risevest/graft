/// <reference types="@capacitor/cli" />

import type { PluginListenerHandle } from '@capacitor/core';

declare module '@capacitor/cli' {
  export interface PluginsConfig {
    Graft?: {
      /**
       * Whether or not to automatically block bundles that have been rolled back.
       *
       * A bundle is blocked once it has failed to report `ready()` twice running, not on the first
       * failure: a single miss is as likely to be a slow cold start as a broken bundle, and blocking
       * is permanent — a device that blocks a good release never receives it again. Up to 100
       * bundles are held; the oldest is unblocked when the limit is reached. Blocked bundles are
       * skipped by `sync()`.
       *
       * **Attention**: This option has no effect if `readyTimeout` is set to `0`.
       *
       * Only available on Android and iOS.
       *
       * @default true
       */
      autoBlockRolledBackBundles?: boolean;
      /**
       * Whether or not to automatically delete unused bundles.
       *
       * When enabled, the plugin will automatically delete unused bundles after calling `ready()`.
       * The active bundle, the next bundle and the last bundle that reached `ready()` are always kept.
       *
       * @default true
       */
      autoDeleteBundles?: boolean;
      /**
       * The auto-update strategy for live updates.
       *
       * - `none`: Live updates will not be applied automatically.
       * - `background`: Live updates will be automatically downloaded
       * and applied in the background at app startup and when the app resumes
       * (if the last check was more than 15 minutes ago).
       *
       * Only available on Android and iOS.
       *
       * @default 'none'
       * @example 'background'
       */
      autoUpdateStrategy?: 'none' | 'background';
      /**
       * The default channel of the app.
       *
       * This can be overridden by `setChannel()`, the `channel` parameter of `sync()`,
       * or the native channel configuration
       * (`RisemaxiGraftDefaultChannel` in `Info.plist` on iOS or `graft_default_channel`
       * in `strings.xml` on Android).
       *
       * @example 'production'
       */
      defaultChannel?: string;
      /**
       * The timeout in milliseconds for HTTP requests.
       *
       * @default 60000
       */
      httpTimeout?: number;
      /**
       * The public key that release manifests are verified against.
       *
       * The public key must be a PEM-encoded RSA public key. Manifests carry a detached
       * RSA PKCS#1 v1.5 signature over the SHA-256 of their raw bytes.
       *
       * **Attention**: `sync()` fails when this is not set. A bundle is never installed
       * without a verified manifest.
       *
       * @example '-----BEGIN PUBLIC KEY-----MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDDodf1SD0OOn6hIlDuKBza0Ed0OqtwyVJwiyjmE9BJaZ7y8ZUfcF+SKmd0l2cDPM45XIg2tAFux5n29uoKyHwSt+6tCi5CJA5Z1/1eZruRRqABLonV77KS3HUtvOgqRLDnKSV89dYZkM++NwmzOPgIF422mvc+VukcVOBfc8/AHQIDAQAB-----END PUBLIC KEY-----'
       */
      publicKey?: string;
      /**
       * The timeout in milliseconds to wait for the app to be ready
       * before rolling back.
       *
       * The plugin waits for the app to call `ready()`. If that does not happen in time,
       * it rolls back to the last bundle that did reach `ready()`, or to the bundle
       * embedded in the binary when there is no such bundle.
       *
       * Set to `0` to disable the timeout, which also disables automatic rollback —
       * a bundle that fails to boot cannot report itself, so nothing else will catch it.
       *
       * @default 10000
       */
      readyTimeout?: number;
      /**
       * The base URL of the update server, without a trailing slash.
       *
       * The channel document for the channel `production` is read from
       * `<serverUrl>/v1/channel/production.json`.
       *
       * @example 'https://ota.example.com'
       */
      serverUrl?: string;
    };
  }
}

export interface GraftPlugin {
  /**
   * Clear all blocked bundles from the blocked list.
   *
   * This removes all bundle identifiers that were automatically blocked
   * due to rollbacks when `autoBlockRolledBackBundles` is enabled.
   *
   * Only available on Android and iOS.
   */
  clearBlockedBundles(): Promise<void>;
  /**
   * Delete a bundle from the app.
   *
   * Only available on Android and iOS.
   */
  deleteBundle(options: DeleteBundleOptions): Promise<void>;
  /**
   * Download a ZIP bundle and install it under the given identifier.
   *
   * This is a manual staging path for development and QA. It is not part of the
   * update protocol and the bundle it installs carries no release metadata, so it is
   * discarded whenever the native binary changes.
   *
   * Only available on Android and iOS.
   */
  downloadBundle(options: DownloadBundleOptions): Promise<void>;
  /**
   * Get all blocked bundle identifiers.
   *
   * Returns the list of bundle identifiers that were automatically blocked
   * due to rollbacks when `autoBlockRolledBackBundles` is enabled.
   *
   * Only available on Android and iOS.
   */
  getBlockedBundles(): Promise<GetBlockedBundlesResult>;
  /**
   * Get the channel that is used for the update.
   *
   * The channel is resolved in the following order (highest priority first):
   * 1. `setChannel()` (SharedPreferences on Android / UserDefaults on iOS)
   * 2. Native config (`RisemaxiGraftDefaultChannel` in `Info.plist` on iOS or
   *    `graft_default_channel` in `strings.xml` on Android)
   * 3. Capacitor config `defaultChannel`
   *
   * **Note**: The `channel` parameter of `sync()` takes the highest priority
   * but is not persisted and therefore not returned by this method.
   *
   * Only available on Android and iOS.
   */
  getChannel(): Promise<GetChannelResult>;
  /**
   * Get the bundle identifier of the current bundle.
   * The current bundle is the bundle that is currently used by the app.
   *
   * Only available on Android and iOS.
   */
  getCurrentBundle(): Promise<GetCurrentBundleResult>;
  /**
   * Get all identifiers of bundles that have been downloaded.
   *
   * Only available on Android and iOS.
   */
  getDownloadedBundles(): Promise<GetDownloadedBundlesResult>;
  /**
   * Get the install identifier and the staged-rollout bucket derived from it.
   *
   * A release is offered to this install when its `rollout` percentage is greater
   * than `bucket`, so the bucket explains why a device is or is not in a canary.
   *
   * Only available on Android and iOS.
   */
  getInstallId(): Promise<GetInstallIdResult>;
  /**
   * Get the bundle identifier of the next bundle.
   * The next bundle is the bundle that will be used after calling `reload()`
   * or restarting the app.
   *
   * Only available on Android and iOS.
   */
  getNextBundle(): Promise<GetNextBundleResult>;
  /**
   * Get the version code of the app.
   *
   * On **Android**, this is the `versionCode` from the `android/app/build.gradle` file.
   * On **iOS**, this is the `CFBundleVersion` from the `Info.plist` file.
   *
   * A release is only installed when its `nativeFingerprint` matches the one in
   * this value.
   *
   * Only available on Android and iOS.
   */
  getVersionCode(): Promise<GetVersionCodeResult>;
  /**
   * Get the version name of the app.
   *
   * On **Android**, this is the `versionName` from the `android/app/build.gradle` file.
   * On **iOS**, this is the `CFBundleShortVersionString` from the `Info.plist` file.
   *
   * Only available on Android and iOS.
   */
  getVersionName(): Promise<GetVersionNameResult>;
  /**
   * Check whether a sync operation is currently in progress.
   *
   * Only available on Android and iOS.
   */
  isSyncing(): Promise<IsSyncingResult>;
  /**
   * Notify the plugin that the app is ready to use and no rollback is needed.
   *
   * **Attention**: This method should be called as soon as the app is ready to use
   * to prevent the app from being reset to the default bundle.
   *
   * Only available on Android and iOS.
   */
  ready(): Promise<ReadyResult>;
  /**
   * Reload the app to apply the new bundle.
   *
   * Only available on Android and iOS.
   */
  reload(): Promise<void>;
  /**
   * Reset the app to the default bundle.
   *
   * Call `reload()` or restart the app to apply the changes.
   *
   * Only available on Android and iOS.
   */
  reset(): Promise<void>;
  /**
   * Set the channel to use for the update.
   *
   * Only available on Android and iOS.
   */
  setChannel(options: SetChannelOptions): Promise<void>;
  /**
   * Set the next bundle to use for the app.
   *
   * Call `reload()` or restart the app to apply the changes.
   *
   * Only available on Android and iOS.
   */
  setNextBundle(options: SetNextBundleOptions): Promise<void>;
  /**
   * Read the channel document, install the newest release this install is eligible for,
   * and stage it as the next bundle.
   *
   * A release is eligible when its `nativeFingerprint` matches this binary's, its
   * code, its `counter` is greater than the highest counter ever installed, and its
   * `rollout` covers this install's bucket. The chosen release's manifest is verified
   * against `publicKey` before any asset is downloaded, and every installed file is
   * verified against that manifest.
   *
   * Call `reload()` or restart the app to apply the changes.
   *
   * Only available on Android and iOS.
   */
  sync(options?: SyncOptions): Promise<SyncResult>;
  /**
   * Listen for the download progress of a bundle.
   *
   * Only available on Android and iOS.
   */
  addListener(
    eventName: 'downloadBundleProgress',
    listenerFunc: DownloadBundleProgressListener,
  ): Promise<PluginListenerHandle>;
  /**
   * Listen for when a bundle is set as the next bundle.
   *
   * This event is triggered whenever a bundle is set to be used on the next app restart,
   * either through automatic updates or manual calls to `setNextBundle()`.
   *
   * Only available on Android and iOS.
   */
  addListener(
    eventName: 'nextBundleSet',
    listenerFunc: NextBundleSetListener,
  ): Promise<PluginListenerHandle>;
  /**
   * Listen for when the app is reloaded.
   *
   * This event is triggered after the `reload()` method is called
   * and the app has been reloaded.
   *
   * **Note:** To verify whether an update was successfully applied after a reload,
   * use the `ready()` method instead. The `ready()` method provides detailed information
   * about the current bundle, previous bundle, and whether a rollback occurred.
   *
   * Only available on Android and iOS.
   */
  addListener(
    eventName: 'reloaded',
    listenerFunc: ReloadedListener,
  ): Promise<PluginListenerHandle>;
  /**
   * Remove all listeners for this plugin.
   */
  removeAllListeners(): Promise<void>;
}

/**
 * What the plugin knows about the bundle it is serving, exposed to the page before any app code
 * runs. Read it with `releaseIdentity()`.
 */
export interface ReleaseIdentity {
  /**
   * The build number of the binary — `versionCode` on Android, `CFBundleVersion` on iOS — or
   * `null` when it is not an integer.
   */
  nativeBuild: number | null;
  /**
   * The fingerprint of the native code this binary was compiled from, taken from the manifest
   * embedded in it. A release names the fingerprint it was built against, and the device runs it
   * only when the two match. `null` only when the binary embeds no manifest.
   */
  nativeFingerprint: string | null;
  /**
   * The identifier of the bundle being served: the release id for a bundle installed by `sync()`,
   * the identifier it was staged under for one installed by `downloadBundle()`, or the `id` of the
   * embedded manifest for the bundle built into the binary. `null` only when the binary embeds no
   * manifest.
   */
  releaseId: string | null;
}

export interface DeleteBundleOptions {
  /**
   * The unique identifier of the bundle to delete.
   *
   * @example '1.0.0'
   */
  bundleId: string;
}

export interface DownloadBundleOptions {
  /**
   * The unique identifier of the bundle.
   *
   * **Attention**: The value `public` is reserved and cannot be used as a bundle identifier.
   *
   * @example '1.0.0'
   */
  bundleId: string;
  /**
   * The SHA-256 hash of the ZIP file in hex format.
   *
   * The download is discarded when the hash does not match.
   */
  checksum: string;
  /**
   * The URL of the ZIP file to download.
   *
   * @example 'https://example.com/bundle.zip'
   */
  url: string;
}

export interface GetBlockedBundlesResult {
  /**
   * The identifiers of all blocked bundles.
   */
  bundleIds: string[];
}

export interface GetChannelResult {
  /**
   * The channel that is used for the update.
   */
  channel: string | null;
}

export interface GetCurrentBundleResult {
  /**
   * The identifier of the current bundle, or `null` when the bundle
   * embedded in the binary is in use.
   */
  bundleId: string | null;
}

export interface GetDownloadedBundlesResult {
  /**
   * The identifiers of all downloaded bundles.
   */
  bundleIds: string[];
}

export interface GetInstallIdResult {
  /**
   * The staged-rollout bucket, in the range 0 to 99, derived from `installId`.
   *
   * A release is offered to this install when its `rollout` is greater than this value.
   */
  bucket: number;
  /**
   * The identifier of this install.
   */
  installId: string;
}

export interface GetNextBundleResult {
  /**
   * The identifier of the next bundle, or `null` when the bundle
   * embedded in the binary will be used.
   */
  bundleId: string | null;
}

export interface GetVersionCodeResult {
  /**
   * The version code of the app.
   */
  versionCode: string;
}

export interface GetVersionNameResult {
  /**
   * The version name of the app.
   */
  versionName: string;
}

export interface IsSyncingResult {
  /**
   * Whether or not a sync operation is currently in progress.
   */
  syncing: boolean;
}

export interface ReadyResult {
  /**
   * The identifier of the current bundle, or `null` when the bundle
   * embedded in the binary is in use.
   */
  currentBundleId: string | null;
  /**
   * The identifier of the bundle that was in use before the current one,
   * or `null` when there was none.
   */
  previousBundleId: string | null;
  /**
   * Whether or not a rollback was performed on this launch.
   */
  rollback: boolean;
}

export interface SetChannelOptions {
  /**
   * The channel to use for the update. Pass `null` to fall back to the
   * native or Capacitor config default.
   *
   * @example 'production'
   */
  channel: string | null;
}

export interface SetNextBundleOptions {
  /**
   * The identifier of the bundle to use after the next reload,
   * or `null` to use the bundle embedded in the binary.
   *
   * @example '1.0.0'
   */
  bundleId: string | null;
}

export interface SyncOptions {
  /**
   * The channel to read the channel document from. Overrides the configured
   * channel for this call only and is not persisted.
   *
   * @example 'production'
   */
  channel?: string;
}

export interface SyncResult {
  /**
   * The identifier of the bundle that was staged as the next bundle,
   * or `null` when no update was applied.
   */
  nextBundleId: string | null;
}

export type DownloadBundleProgressListener = (
  event: DownloadBundleProgressEvent,
) => void;

export interface DownloadBundleProgressEvent {
  /**
   * The identifier of the bundle being downloaded.
   */
  bundleId: string;
  /**
   * The number of bytes downloaded so far.
   */
  downloadedBytes: number;
  /**
   * The total number of bytes to download.
   */
  totalBytes: number;
}

export type NextBundleSetListener = (event: NextBundleSetEvent) => void;

export interface NextBundleSetEvent {
  /**
   * The identifier of the bundle that was set as the next bundle,
   * or `null` when the bundle embedded in the binary was set.
   */
  bundleId: string | null;
}

export type ReloadedListener = () => void;
