# Graft

Self-hosted over-the-air web bundle updates for Capacitor apps. The active bundle is selected
natively before the WebView is constructed, so a swap costs exactly one page load and no flash, and
the web origin never changes — `localStorage`, IndexedDB and cookies survive it.

A fork of [`@capawesome/capacitor-live-update`](https://github.com/capawesome-team/capacitor-plugins/tree/main/packages/live-update).
See `NOTICE` and `UPSTREAM.md`.

## Status

Pre-release. The vendored lifecycle compiles on all three targets; the self-hosted protocol, the
native pointer rework and binary deltas are landing in sequence.

## Install

```sh
bun add @risemaxi/graft
bun x cap sync
```

## Verify

```sh
bun run verify:web
bun run verify:android    # needs JDK 21 and ANDROID_HOME
bun run verify:ios        # needs Xcode
```

## Licence

MIT. Copyright (c) 2022 Robin Genz, Copyright (c) 2026 Rise.
