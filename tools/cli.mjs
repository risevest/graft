#!/usr/bin/env node
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';
import { writeManifest } from './manifest.mjs';
import {
  DEFAULT_SOURCES,
  describeChange,
  fingerprint,
} from './native-fingerprint.mjs';
import { signManifestToFile } from './sign.mjs';

const USAGE = `graft — release tooling for self-hosted OTA bundles

  graft manifest    --dir <bundle> --id <release> --counter <n> --native-fingerprint <hash>
                    [--channel <name>] [--not-before <epoch>] [--expires-at <epoch>] [--out <file>]
                    [--requires <graft-requires.json>]
  graft fingerprint [--root <dir>] [--source <path>]... [--full] [--against <fingerprint.json>]
  graft sign        <manifest> <signature-out>       key from GRAFT_SIGNING_KEY
  graft patch       --old <bundle> --new <bundle> --out <patch.gpz> [--level 19]
  graft apply       --base <bundle> --patch <patch.gpz> --manifest <manifest> --out <dir>

A manifest describes what graft will install and verify; the signature is what makes it
authoritative. Patches are an optimisation the device can always do without.

The native fingerprint identifies the compiled binary a bundle was built against. A device runs a
release only when the fingerprint matches its own, so a bundle never reaches a binary built from
different native code.`;

function delegate(script, argv) {
  const result = spawnSync(
    process.execPath,
    [fileURLToPath(new URL(script, import.meta.url)), ...argv],
    {
      stdio: 'inherit',
    },
  );
  process.exit(result.status ?? 1);
}

const [command, ...argv] = process.argv.slice(2);

try {
  switch (command) {
    case 'manifest': {
      const { values } = parseArgs({
        args: argv,
        options: {
          'dir': { type: 'string' },
          'out': { type: 'string' },
          'id': { type: 'string' },
          'channel': { type: 'string' },
          'counter': { type: 'string' },
          'native-fingerprint': { type: 'string' },
          'not-before': { type: 'string' },
          'expires-at': { type: 'string' },
          'requires': { type: 'string' },
        },
      });
      const { manifest, output } = writeManifest({
        dir: values.dir,
        out: values.out,
        id: values.id,
        channel: values.channel,
        counter: values.counter,
        nativeFingerprint: values['native-fingerprint'],
        notBefore: values['not-before'],
        expiresAt: values['expires-at'],
        requires: values.requires
          ? JSON.parse(readFileSync(values.requires, 'utf8'))
          : undefined,
      });
      console.log(
        `${output}: ${manifest.files.length} files, counter ${manifest.counter}`,
      );
      break;
    }
    case 'fingerprint': {
      const { values } = parseArgs({
        args: argv,
        options: {
          root: { type: 'string' },
          full: { type: 'boolean' },
          against: { type: 'string' },
          source: { type: 'string', multiple: true },
        },
      });
      const result = fingerprint(values.root ?? process.cwd(), {
        sources: values.source?.length
          ? [...DEFAULT_SOURCES, ...values.source]
          : DEFAULT_SOURCES,
      });
      if (values.against) {
        const before = JSON.parse(readFileSync(values.against, 'utf8'));
        const changed = describeChange(before, result);
        if (changed.length > 0) {
          console.error(
            `native code changed since ${before.hash}:\n  ${changed.join('\n  ')}`,
          );
          process.exit(1);
        }
        console.log(`native code is unchanged (${result.hash})`);
        break;
      }
      process.stdout.write(
        values.full ? `${JSON.stringify(result, null, 2)}\n` : `${result.hash}\n`,
      );
      break;
    }
    case 'sign': {
      const [manifestPath, signaturePath] = argv;
      if (!manifestPath || !signaturePath)
        throw new Error('usage: graft sign <manifest> <signature-out>');
      signManifestToFile(
        manifestPath,
        signaturePath,
        process.env.GRAFT_SIGNING_KEY,
      );
      console.log(`${signaturePath}: signed ${manifestPath}`);
      break;
    }
    case 'patch':
      delegate('./make-patch.mjs', argv);
      break;
    case 'apply':
      delegate('./apply-patch.mjs', argv);
      break;
    default:
      console.error(USAGE);
      process.exit(
        command === undefined || command === '--help' || command === '-h'
          ? 0
          : 2,
      );
  }
} catch (error) {
  console.error(`error: ${error.message}`);
  process.exit(1);
}
