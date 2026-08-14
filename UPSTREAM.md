# Upstream

Graft is a fork of [`@capawesome/capacitor-live-update`](https://github.com/capawesome-team/capacitor-plugins/tree/main/packages/live-update)
by Robin Genz, MIT licensed. See `NOTICE`.

## Fork point

| | |
|---|---|
| Repository | `capawesome-team/capacitor-plugins` |
| Path | `packages/live-update` |
| Version | `8.4.0` |
| Commit | `a81ee3d5e7b8fdddc7d56e3a0de524a31f7a0f99` |

## Why fork rather than depend

Upstream implements file-level manifest diffing. Measured against the Rise Treasury bundle, a
one-word copy change transfers **1.28 MB** that way versus **1.8 kB** as a per-file binary delta —
the 4.64 MB entry chunk changes on any edit, so file-level diffing has a hard floor. Adding a delta
artifact type touches the artifact enum, four dispatch sites and options parsing, and nothing in
install, pointer, rollback, watchdog or retention. That lifecycle is the part worth keeping.

Upstreaming was assessed and rejected: substantial external PRs have a 0/3 merge rate, and PR #800 —
an outside contributor's transfer-size optimisation, the same category as this work — was declined
with the contributor redirected to Capawesome Cloud.

## Rename map

The initial commit is a pure mechanical rename of the vendored source, applied longest-identifier
first so shorter names do not shadow longer ones:

| Upstream | Graft |
|---|---|
| `io.capawesome.capacitorjs.plugins.liveupdate` | `com.risemaxi.graft` |
| `CapawesomeCapacitorLiveUpdate` | `RisemaxiGraft` |
| `CapawesomeLiveUpdate` (SharedPreferences name) | `RisemaxiGraft` |
| `capawesome_live_update_default_channel` | `graft_default_channel` |
| `LiveUpdatePlugin` | `GraftPlugin` |
| `LiveUpdateConfig` | `GraftConfig` |
| `LiveUpdatePreferences` | `GraftPreferences` |
| `LiveUpdateHttpClient` | `GraftHttpClient` |
| `LiveUpdateWeb` | `GraftWeb` |
| `capacitorLiveUpdate` | `risemaxiGraft` |
| `LiveUpdate` | `Graft` |

Layout changes: `ios/Plugin/` became `ios/Sources/RisemaxiGraft/` for SwiftPM, and the Capacitor
plugin identifier is `Graft`.

The SwiftPM target and product are both named `RisemaxiGraft`, and they have to match. `cap sync`
adds the *product* to the app's generated `CapApp-SPM/Package.swift`, but Swift imports the *target*
name — and a target whose name differs from its product is not resolvable from the app target, which
fails as `Unable to resolve module dependency`. So consumers write `import RisemaxiGraft`. This only
matters for plugins the app touches from its own Swift code; the other Capacitor plugins are reached
from JS and never imported.

Not vendored: `example/`, `ios/Plugin.xcodeproj` (SwiftPM covers the build), and the upstream test
scaffolding, which does not compile.

## Divergence

What upstream still recognises: the bundle store and its directory layout, `ready()`, the rollback
watchdog, the blocklist, retention, and the reload path.

What is ours, and shares no code with upstream:

| | Upstream | Graft |
|---|---|---|
| Update source | Capawesome Cloud (`appId`, `serverDomain`, `customId`, device headers) | The channel document in `README.md` (`serverUrl`) |
| Which release | Whatever the server hands back for this device | Chosen on-device from `minNativeBuild`, `counter` and a local rollout bucket |
| Signing | Optional, per file, signature supplied by response headers | Required, once over the manifest, verified before any asset is fetched |
| File integrity | `X-Checksum`/`X-Signature` headers; manifest checksums used only for diffing | Every installed file checked against the signed manifest |
| Downgrades | Not prevented | Monotonic `counter` floor, recorded at install |
| Binary change | Pointer always discarded | Kept when `minNativeBuild` allows and the embedded counter is not higher |

Removed from the API: `fetchChannels`, `fetchLatestBundle`, `getConfig`, `setConfig`, `resetConfig`,
`getCustomId`, `setCustomId`, `getBundles`, `getDeviceId` (replaced by `getInstallId`), and the
`manifest` artifact type of `downloadBundle`, which is now a ZIP-only manual staging path.

## Rebasing onto a newer upstream

1. Fetch the upstream range: `git log --oneline <pinned>..<target> -- packages/live-update`.
2. Ignore changes confined to the cloud client, `example/`, and the docs site — and note that this
   now also covers `sync`, `downloadBundle`'s manifest path, and everything under verification,
   since none of that is shared code any more.
3. Apply the rest by hand against the rename map above. The god classes — `Graft.java` and
   `Graft.swift` — are where conflicts land, and only their lifecycle halves are still comparable.
4. Re-run `bun run verify`, then re-run the device matrix in the consuming app.
5. Update the fork point table above.

Upstream ships roughly one small release a month on a 6k-line native codebase, and `BREAKING.md`
runs 49 lines across two majors, so budget a few hours per quarter.
