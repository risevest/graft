# Graft

Self-hosted over-the-air web bundle updates for Capacitor apps. The active bundle is selected
natively before the WebView is constructed, so a swap costs exactly one page load and no flash, and
the web origin never changes — `localStorage`, IndexedDB and cookies survive it.

A fork of [`@capawesome/capacitor-live-update`](https://github.com/capawesome-team/capacitor-plugins/tree/main/packages/live-update).
See `NOTICE` and `UPSTREAM.md`.

## Status

Pre-release. The lifecycle, the native pointer, the self-hosted protocol and binary deltas are
implemented on both platforms; a release is fetched as a patch when one is published and falls back
to whole files otherwise, and that path has run end to end on a device.

Contract gating runs end to end: the bundle side derives a `requires` set into the signed manifest,
and both platforms refuse a release naming a plugin the running build does not have.

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

The app must call `Graft.ready()`. With the default `readyTimeout` of 10 s, a bundle that never
reports is rolled back to the last one that did.

**Call it at the end of module initialisation, not from a framework "app ready" hook.** The watchdog
asks one question — did this bundle's entry chunk parse and evaluate — and reaching the end of the
entry module is precisely that answer. A later signal cannot answer it: a bundle broken at module
init never reaches the hook either way, and a bundle whose lazily-loaded chunks are broken is already
past the hook and fails on interaction, where a rollback would not have helped. Signalling late only
widens the window in which a healthy bundle is reverted for being slow.

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

This is the one response a device asks for on every launch, so serve it with a strong `ETag`. Graft
records the tag once the work a document implies has succeeded, and sends it back as `If-None-Match`;
publishing is rare and launches are not, so the steady state is a 304 with no body. A server that
sends no `ETag` is fine — graft stores nothing and the exchange stays a plain 200. The tag is
recorded only on success, so an install that failed is retried on the next launch rather than
skipped by a tag that outran it.

The tag is stored against the fingerprint it was judged under. A tag says "I have already seen this
document"; the conclusion drawn from it says "and there was nothing here for me", and that second
half depends on which binary asked. A release published for the next store build sits in the
document unselectable, and if the tag survived the update the device would answer 304 and never
reconsider — stranded on its embedded bundle with an update waiting. Keying the two together makes a
tag recorded under a previous binary simply not match.

```jsonc
{
  "schema": 1,
  "killSwitch": false,
  "releases": [
    {
      "id": "r-1042",
      "counter": 1042,
      "rollout": 25,
      "nativeFingerprint": "9f2c1ab4e77d5306",
      "manifest": "/v1/releases/r-1042/graft-manifest.json",
      "sig": "<base64 RSA PKCS#1 v1.5 over SHA-256 of the manifest bytes>",
    },
  ],
}
```

A release is eligible when `nativeFingerprint` equals this binary's, `counter > highestInstalledCounter`,
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
  "nativeFingerprint": "9f2c1ab4e77d5306",
  "notBefore": 1786238700,
  "expiresAt": 1793928700,
  "files": [{ "href": "index.html", "sha256": "<hex>", "size": 1234 }],
}
```

After the signature verifies, the manifest is rejected unless `id`, `counter` and `nativeFingerprint`
match the channel entry, `channel` matches the channel it was fetched from,
`nativeFingerprint` equals this binary's, `counter > highestInstalledCounter`, and
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
per version pair: an 8-byte `GRAFTP1\n` magic, a length-prefixed plan, then the payloads as a
counted sequence of length-prefixed blocks, the whole thing compressed with zstd-19. Lengths are
unsigned 32-bit big-endian.

Payloads are referenced by index rather than by name, so the archive contains no paths at all — the
only paths anywhere are the manifest hrefs, which are already validated. That also keeps the reader
to a few dozen lines on each platform, where a tar reader would be a few hundred with pax and GNU
long-name handling to get wrong.

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
      "payload": 0,
    },
    { "op": "add", "href": "assets/new-Qz1.js", "payload": 1 },
    { "op": "delete", "href": "assets/gone-Xy2.js" },
  ],
}
```

Each payload is the output of `zstd -19 --long=27 --patch-from <base>`, computed over
**uncompressed** bytes — diffing two already-compressed files is near-useless, because deflate
divergence cascades.

The device fetches `GET <serverUrl>/v1/patches/<from>__<to>.gpz`. Addressing a patch by path rather
than by query is deliberate: it is what lets every request graft makes be answered by a static
bucket with no compute in front of it. A server that wants to synthesise a missing pair on demand
can still intercept that path; one that does not answers 404 and the device downloads the files it
cannot reuse.

**The plan is not trusted.** It says how to reconstruct a file, never whether the result is
acceptable. Reconstruction is driven by the signed manifest's file list, and every output file is
verified against its `sha256` from that manifest before the bundle is eligible to run. A manifest
entry with no corresponding op is an error, and any failure — a missing base, a patch that will not
apply, a digest mismatch — falls back to a full download rather than installing something unverified.

Bases are paired per file, not by name: content-hashed chunk names change every release, so the
generator picks the base that yields the smallest patch among same-extension candidates and stores
that choice in the op. If no base beats simply shipping the file, it becomes an `add`.

Measured on a real 98-file bundle, a one-word copy change produces a **4,253-byte archive** against
**1,622 kB** for the same release transferred file-by-file.

## Release tooling

Everything the device validates, graft also produces. The rules are subtle enough — canonical key
order, never re-serialising between signing and upload, pairing patch bases by content rather than
by name — that a consumer reimplementing them will get one of them wrong, and the failure surfaces
as a signature error on a manifest that was signed correctly.

```sh
graft manifest --dir dist --id r-1042 --counter 1042 --native-fingerprint "$(graft fingerprint)" \
               --channel production --not-before 1786238700 --expires-at 1793928700
GRAFT_SIGNING_KEY="$(cat private.pem)" graft sign dist/graft-manifest.json manifest.sig
graft patch --old <previous bundle> --new dist --out r-1041__r-1042.gpz
graft apply --base <previous bundle> --patch r-1041__r-1042.gpz \
            --manifest dist/graft-manifest.json --out <dir>
```

`graft apply` is a reference implementation and a conformance check for the native apply paths — it
is not what runs on device. What the release pipeline owns is the part graft cannot know: where the
files are hosted, which counter a release gets, and when a channel points at it.

### Deriving the plugin contract

A bundle can only reach native code through a `registerPlugin` proxy, so the set of names it passes
to `registerPlugin` _is_ its contract. `graftRequires` collects them while the bundle is built and
writes them out:

```ts
import { requiresVite as graftRequires } from '@risemaxi/graft/tools/unplugin.mjs';

graftRequires({ out: 'graft-requires.json' });
```

```sh
graft manifest --dir dist --requires graft-requires.json …
```

It must run inside the build. Minification rewrites the call sites — a built bundle contains zero
recognisable `registerPlugin(` calls — so there is nothing to scan afterwards.

Two things make a derived contract untrustworthy, and both fail the build rather than warn: a
`registerPlugin` call whose name is not a literal, and first-party code reaching plugins through
`Capacitor.Plugins`. A contract that is too small is worse than none, because the device then accepts
a bundle it cannot run.

Only calls in modules importing `registerPlugin` from `@capacitor/core` count. The name is not
Capacitor's alone — gsap exports one too — and matching on the call alone reports every
`gsap.registerPlugin(CSSPlugin)` as an underivable plugin. The `Capacitor.Plugins` check is likewise
scoped to first-party modules, because Capacitor's own bridge reaches plugins that way by design.

On device the contract is checked against the live bridge — `Bridge.getPlugin` on Android,
`bridge.plugin(withName:)` on iOS — so it answers from the plugins the running binary actually
registered rather than from anything the build recorded. A name the bridge does not know fails the
update with `The release needs a plugin this build does not have.` The check sits in manifest
verification, which every path to staging goes through — including the one that reuses a bundle an
interrupted install left on disk, where the binary may have been replaced since those files landed.
A rejected release neither downloads files nor raises the installed counter, so a later release at
the same counter still installs.

### As a bundler plugin

The manifest can also be written as part of the build, which removes the second command and with it
the chance of signing a stale directory:

```ts
import { vite as graftManifest } from '@risemaxi/graft/tools/unplugin.mjs';

graftManifest({ dir: 'dist', id, counter, nativeFingerprint });
```

`unplugin` is an optional peer, and the same factory exports `rollup`, `rolldown`, `webpack`,
`rspack`, `esbuild` and `farm` builds. It hooks `writeBundle` and nothing else — the one hook every
bundler unifies, and one that deliberately carries no arguments. That suits this exactly: the
bundler is being asked for the timing, not for an inventory. A bundler's record of what it emitted
is not the shipped file set, because static assets are copied in without passing through it, and
every file has to be read off disk to be digested anyway.

### Requirements on the consuming app

**Every native build must embed a manifest carrying its own fingerprint.** `graft fingerprint`
hashes the native inputs — the plugin packages the app declares, and the version-controlled native
sources — and the value goes into the manifest shipped inside the binary. That is how the device
knows its own identity; there is nothing to configure and no number to keep in step across
platforms. A release names the fingerprint it was built against, and a device runs it only when the
two are equal, so a bundle can never reach a binary compiled from different native code.

Regenerate the fingerprint whenever the native build is cut, not when the web bundle is built. The
two directories a Capacitor app generates, `ios/` and `android/`, are outputs of these inputs rather
than inputs themselves, so they are deliberately not hashed — fingerprinting them would make every
`cap sync` look like a native change.

**Releases and native builds must be counted on one scale, or the embedded bundle must not claim a
counter.** On a native release graft compares the embedded bundle's `counter` against the staged
bundle's to decide which web bundle is newer. That only means something if the two numbers come from
the same sequence. The simplest way to satisfy it is to derive both from one monotonic source — a
timestamp ordinal such as `Math.round(Date.now() / 100000)` works, and needs no registry.

If your OTA releases are counted independently of your native builds, **omit `counter` from the
embedded manifest**. Graft then skips the comparison and keeps a staged bundle whenever its
fingerprint still matches. Supplying a number that does not compare is the one genuinely dangerous
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
the pre-WebView hook: a staged bundle survives a native release when its `nativeFingerprint` still matches the
new build and the embedded bundle's `counter` is not higher. Bundles with no manifest — anything
staged by hand through `downloadBundle()` — are dropped on any binary change.

## Verify

```sh
bun run verify:web
bun run verify:android    # needs JDK 21 and ANDROID_HOME
bun run verify:ios        # needs Xcode
```

Those three only compile. To exercise the apply path on both platforms against a real bundle and a
set of deliberately malformed archives:

```sh
FIXTURES=<dir> verify/run.sh
```

The sources it builds are symlinks to the shipping ones, so this runs the code that runs on a device
rather than a copy of it. `verify/run.sh` explains what the fixture directory needs and how to
generate it; use a realistic bundle, because a one-file marker exercises none of the base pairing or
hash cascade that makes any of this worth testing.

Every malformed archive must be rejected, and none may leave behind a file whose digest is not the
signed manifest's. The case that matters most is `wrong-content` — an op pointed at valid bytes from
the wrong file. A corrupted zstd frame fails to decompress before any digest is computed, so it
never reaches the check the whole design rests on; only that case does.

## Licence

MIT. Copyright (c) 2022 Robin Genz, Copyright (c) 2026 Rise.
