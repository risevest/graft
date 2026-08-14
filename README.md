# Graft

Self-hosted over-the-air web bundle updates for Capacitor apps. The active bundle is selected
natively before the WebView is constructed, so a swap costs exactly one page load and no flash, and
the web origin never changes — `localStorage`, IndexedDB and cookies survive it.

A fork of [`@capawesome/capacitor-live-update`](https://github.com/capawesome-team/capacitor-plugins/tree/main/packages/live-update).
See `NOTICE` and `UPSTREAM.md`.

## Status

Pre-release. The lifecycle, the native pointer and the self-hosted protocol below are implemented on
both platforms. Binary deltas are not: a release still transfers whole files, reusing every file the
running bundle already has at the same digest. `requires`/`provides` contract gating is not
implemented either — `minNativeBuild` is the only compatibility gate today.

## Install

```sh
bun add @risemaxi/graft
bun x cap sync
```

Then serve the staged bundle from the pre-WebView hook on each platform:

- **Android** — set the launcher activity to `com.risemaxi.graft.GraftActivity`, or call
  `GraftPointer.resolveActiveBundleDirectory(this)` from your own `BridgeActivity.load()`.
- **iOS** — use `GraftViewController` as the root view controller, or override
  `instanceDescriptor()` and assign `GraftPointer.resolveActiveBundleDirectory()` to `appLocation`.

The app must call `Graft.ready()` once it has booted. With the default `readyTimeout` of 10 s, a
bundle that never reports is rolled back to the last one that did.

## Configuration

```ts
{
  plugins: {
    Graft: {
      serverUrl: 'https://ota.example.com',
      defaultChannel: 'production',
      publicKey: '-----BEGIN PUBLIC KEY-----…-----END PUBLIC KEY-----',
    },
  },
}
```

`serverUrl` and `publicKey` are both required for `sync()`. There is no unsigned path: a missing key
is an error, not a skipped check.

## Protocol

### Channel document

`GET <serverUrl>/v1/channel/<channel>.json`, served unsigned and edge-cacheable. It is a hint about
which manifest to fetch and nothing more — every value in it is re-checked against the signed
manifest before a bundle is installed.

```jsonc
{
  "schema": 1,
  "killSwitch": false,
  "releases": [
    {
      "id": "r-1042",
      "counter": 1042,
      "rollout": 25,
      "minNativeBuild": 17862387,
      "manifest": "/v1/releases/r-1042/manifest.json",
      "sig": "<base64 RSA PKCS#1 v1.5 over SHA-256 of the manifest bytes>",
    },
  ],
}
```

A release is eligible when `minNativeBuild <= versionCode`, `counter > highestInstalledCounter`,
`rollout > bucket`, and its id is not blocked. The highest-counter eligible release wins; if none is,
the device stays put. `killSwitch` clears the pointer, so the next launch serves the embedded bundle.

`manifest` is resolved against the channel document's URL and must land on `serverUrl`'s origin.
Release files are fetched as siblings of the manifest — `<manifest dir>/<href>`.

### Rollout bucket

`bucket = FNV-1a-32(installId) % 100`, computed on the device from a random per-install id. It is
fixed for the life of the install, so a device keeps its position as a rollout widens. Read it with
`getInstallId()`.

### Manifest

The signed unit. Its raw bytes are what `sig` covers, so it is never re-serialised between signing
and upload, and never parsed before the signature verifies.

```jsonc
{
  "schema": 1,
  "id": "r-1042",
  "channel": "production",
  "counter": 1042,
  "minNativeBuild": 17862387,
  "notBefore": 1786238700,
  "expiresAt": 1793928700,
  "files": [{ "href": "index.html", "sha256": "<hex>", "size": 1234 }],
}
```

Every field is required. After the signature verifies, the manifest is rejected unless `id`,
`counter` and `minNativeBuild` match the channel entry, `channel` matches the channel it was fetched
from, `minNativeBuild <= versionCode`, `counter > highestInstalledCounter`, and
`notBefore <= now < expiresAt`. `href` must be a relative path with no `.` or `..` segment.

Each installed file is verified against its `sha256` — whether downloaded or copied from the running
bundle — and the manifest is written into the bundle directory only after every file has passed, so
an installed bundle is exactly the signed file set. The manifest is written as
`graft-manifest.json`; ship one at `public/graft-manifest.json` in the native build so the first OTA
can reuse the embedded files instead of downloading them.

### Downgrades and native releases

`counter` must increase. The device records the highest counter it has ever installed — at install
time, not at boot — so a bundle that fails to boot still raises the floor and the device can only
move forward. Rolling back a channel means publishing the old content under a higher counter; a KV
flip alone only helps devices that have not taken the release yet, which for a canary is the
population that matters.

Capacitor discards its own server path whenever the binary changes, which silently downgrades users
to whatever the store build embeds. Graft reads its own pointer and reconciles it deliberately, in
the pre-WebView hook: a staged bundle survives a native release when its `minNativeBuild` allows the
new build and the embedded bundle's `counter` is not higher. Bundles with no manifest — anything
staged by hand through `downloadBundle()` — are dropped on any binary change.

## Verify

```sh
bun run verify:web
bun run verify:android    # needs JDK 21 and ANDROID_HOME
bun run verify:ios        # needs Xcode
```

## Licence

MIT. Copyright (c) 2022 Robin Genz, Copyright (c) 2026 Rise.
