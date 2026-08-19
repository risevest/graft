#!/usr/bin/env node
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';
import { writeManifest } from './manifest.mjs';
import { signManifestToFile } from './sign.mjs';

const USAGE = `graft — release tooling for self-hosted OTA bundles

  graft manifest --dir <bundle> --id <release> --counter <n> --min-native-build <n>
                 [--channel <name>] [--not-before <epoch>] [--expires-at <epoch>] [--out <file>]
  graft sign     <manifest> <signature-out>          key from GRAFT_SIGNING_KEY
  graft patch    --old <bundle> --new <bundle> --out <patch.gpz> [--level 19]
  graft apply    --base <bundle> --patch <patch.gpz> --manifest <manifest> --out <dir>

A manifest describes what graft will install and verify; the signature is what makes it
authoritative. Patches are an optimisation the device can always do without.`;

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
          'min-native-build': { type: 'string' },
          'not-before': { type: 'string' },
          'expires-at': { type: 'string' },
        },
      });
      const { manifest, output } = writeManifest({
        dir: values.dir,
        out: values.out,
        id: values.id,
        channel: values.channel,
        counter: values.counter,
        minNativeBuild: values['min-native-build'],
        notBefore: values['not-before'],
        expiresAt: values['expires-at'],
      });
      console.log(
        `${output}: ${manifest.files.length} files, counter ${manifest.counter}`,
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
