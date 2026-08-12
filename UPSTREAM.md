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

Layout changes: `ios/Plugin/` became `ios/Sources/Graft/` for SwiftPM, and the Capacitor plugin
identifier is `Graft`.

Not vendored: the Capawesome Cloud client and its API surface, `example/`, `ios/Plugin.xcodeproj`
(SwiftPM covers the build), and the upstream test scaffolding, which does not compile.

## Rebasing onto a newer upstream

1. Fetch the upstream range: `git log --oneline <pinned>..<target> -- packages/live-update`.
2. Ignore changes confined to the cloud client, `example/`, and the docs site.
3. Apply the rest by hand against the rename map above. The god classes — `Graft.java` and
   `Graft.swift` — are where conflicts land.
4. Re-run `bun run verify`, then re-run the device matrix in the consuming app.
5. Update the fork point table above.

Upstream ships roughly one small release a month on a 6k-line native codebase, and `BREAKING.md`
runs 49 lines across two majors, so budget a few hours per quarter.
