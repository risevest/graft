# Graft

Self-hosted over-the-air web bundle updates for Capacitor apps. The active bundle is selected
natively before the WebView is constructed, so a swap costs exactly one page load and no flash, and
the web origin never changes — `localStorage`, IndexedDB and cookies survive it.

A fork of [`@capawesome/capacitor-live-update`](https://github.com/capawesome-team/capacitor-plugins/tree/main/packages/live-update).
See `NOTICE` and `UPSTREAM.md`.

## Status

Pre-release. The lifecycle, the native pointer and the self-hosted protocol below are implemented on
both platforms. Binary deltas are half-built: the patch archive format, its generator and a
reference applier ship in `tools/`, but no native apply path exists yet, so a release still
transfers whole files, reusing every file the running bundle already has at the same digest.
`requires`/`provides` contract gating is not implemented either — `minNativeBuild` is the only
compatibility gate today.

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
      "manifest": "/v1/releases/r-1042/graft-manifest.json",
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

**Serve the manifest as `graft-manifest.json`.** Because files are siblings of it, a release
containing a file of the same name resolves to the same URL and one overwrites the other — and
`manifest.json` is a file almost every web app ships. `graft-manifest.json` is safe because the
generator excludes that name from the file set by construction. A manifest whose file list would
collide is rejected, so this cannot ship silently, but the error is easier to avoid than to read.

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

After the signature verifies, the manifest is rejected unless `id`, `counter` and `minNativeBuild`
match the channel entry, `channel` matches the channel it was fetched from,
`minNativeBuild <= versionCode`, `counter > highestInstalledCounter`, and
`notBefore <= now < expiresAt`. `href` must be a relative path with no `.` or `..` segment.

`channel`, `counter`, `notBefore` and `expiresAt` are optional to parse and required to verify. The
same shape describes a release and the bundle sitting on disk, and the manifest the native build
generates for the embedded bundle has no channel or replay window to name — it is never fetched, so
it is never verified. A manifest served on a channel that omits any of them is rejected.

Each installed file is verified against its `sha256` — whether downloaded or copied from the running
bundle — and the manifest is written into the bundle directory only after every file has passed, so
an installed bundle is exactly the signed file set. The manifest is written as
`graft-manifest.json`; ship one at `public/graft-manifest.json` in the native build so the first OTA
can reuse the embedded files instead of downloading them.

### Patch archive

A release may also be transferred as a patch against a bundle the device already has. One archive
per version pair: `tar` containing a `plan.json` and the per-file payloads, compressed with
zstd-19.

```jsonc
{
  "schema": 1,
  "from": "r-1041",
  "to": "r-1042",
  "ops": [
    { "op": "keep", "href": "assets/app-Ba9.js", "from": "assets/app-Ba9.js" },
    {
      "op": "patch",
      "href": "assets/index-D6T.js",
      "from": "assets/index-4ws.js",
      "patch": "p/0.patch",
    },
    { "op": "add", "href": "assets/new-Qz1.js", "data": "a/0.bin" },
    { "op": "delete", "href": "assets/gone-Xy2.js" },
  ],
}
```

Each `p/*.patch` is the output of `zstd -19 --long=27 --patch-from <base>`, computed over
**uncompressed** bytes — diffing two already-compressed files is near-useless, because deflate
divergence cascades.

**The plan is not trusted.** It says how to reconstruct a file, never whether the result is
acceptable. Reconstruction is driven by the signed manifest's file list, and every output file is
verified against its `sha256` from that manifest before the bundle is eligible to run. A manifest
entry with no corresponding op is an error, and any failure — a missing base, a patch that will not
apply, a digest mismatch — falls back to a full download rather than installing something unverified.

Bases are paired per file, not by name: content-hashed chunk names change every release, so the
generator picks the base that yields the smallest patch among same-extension candidates and stores
that choice in the op. If no base beats simply shipping the file, it becomes an `add`.

```sh
node tools/make-patch.mjs  --old <old bundle> --new <new bundle> --out patch.tar.zst
node tools/apply-patch.mjs --base <old bundle> --patch patch.tar.zst \
                           --manifest <new manifest> --out <dir>
```

Both read `graft-manifest.json` from the bundle directories. `apply-patch.mjs` is a reference
implementation and a conformance check for the native apply paths — it is not used on device.
Measured on a real 98-file bundle, a one-word copy change produces a **5,456-byte archive** against
**1,622 kB** for the same release transferred file-by-file.

### Requirements on the consuming app

**The build number must be an integer, and must mean the same thing on both platforms.**
`minNativeBuild` is compared against Android's `versionCode` and iOS's `CFBundleVersion`, so a
release published for build 480 must be the same release for build 480 on either platform. Android's
`versionCode` is always an integer; `CFBundleVersion` is not — Apple also accepts `1.0.3`, and graft
cannot order those against a `versionCode`. A non-integer `CFBundleVersion` makes `sync()` fail with
`CFBundleVersion must be an integer…` and leaves a staged bundle unreconciled after a native release,
so set it to the same integer you set `versionCode` to.

**Releases and native builds must be counted on one scale, or the embedded bundle must not claim a
counter.** On a native release graft compares the embedded bundle's `counter` against the staged
bundle's to decide which web bundle is newer. That only means something if the two numbers come from
the same sequence. The simplest way to satisfy it is to derive both from one monotonic source — a
timestamp ordinal such as `Math.round(Date.now() / 100000)` works, and needs no registry.

If your OTA releases are counted independently of your native builds, **omit `counter` from the
embedded manifest**. Graft then skips the comparison and keeps a staged bundle whenever
`minNativeBuild` allows it. Supplying a number that does not compare is the one genuinely dangerous
option: an embedded counter that always outranks your release counters makes every store release
discard every staged bundle, which is the silent downgrade this rule exists to prevent.

### Nothing that changes per release may live inside a hashed chunk

Bundlers name chunks after a hash of their contents, and an update only reuses a file when its name
already exists on the device. So a value that changes every release — a crash reporter's release
identifier is the usual one — renames the chunk holding it, renames every chunk that references that
one, and cascades. Measured on a real app, a one-word source change touching the entry chunk left
**19 of 98 files different, 5.74 MB of 9.62 MB** (~1.3 MB gzipped). An identifier compiled into that
chunk makes you pay the same cost on every release, including ones that changed no code at all.

Graft already knows which release it is serving, so read it from here instead of compiling one in:

```ts
import { releaseIdentity } from '@risemaxi/graft';

const identity = releaseIdentity(); // { releaseId, nativeBuild } | null
Sentry.init({ release: identity?.releaseId ?? 'unknown' /* … */ });
```

It is published to the page before any app code runs and resolves synchronously, so it can be passed
straight to an `init` call. It returns `null` on the web and on a WebView too old to run a
document-start script, so handle that case rather than dereferencing it — on Android the capability
is feature-detected, and when it is missing the plugin logs why.

If you must stamp something at build time, put it where the bundler does not hash it — `index.html`
is regenerated every release anyway and costs a few kilobytes.

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
