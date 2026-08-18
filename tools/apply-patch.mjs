#!/usr/bin/env node
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import {
  copyFileSync,
  mkdtempSync,
  mkdirSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

const MANIFEST_FILE_NAME = 'graft-manifest.json';

function usage() {
  console.error(
    'usage: apply-patch.mjs --base <bundle dir> --patch <patch.tar.zst> --manifest <manifest.json> --out <dir>',
  );
  process.exit(2);
}

function parseArgs(argv) {
  const args = {};
  for (let i = 0; i < argv.length; i += 2) {
    const key = argv[i];
    if (!key?.startsWith('--')) usage();
    args[key.slice(2)] = argv[i + 1];
  }
  if (!args.base || !args.patch || !args.manifest || !args.out) usage();
  return args;
}

function safeJoin(root, href) {
  if (path.isAbsolute(href)) throw new Error(`absolute href rejected: ${href}`);
  const segments = href.split('/');
  if (segments.some(s => s === '.' || s === '..'))
    throw new Error(`unsafe href: ${href}`);
  return path.join(root, ...segments);
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  const manifest = JSON.parse(readFileSync(args.manifest, 'utf8'));
  if (!Array.isArray(manifest.files))
    throw new Error('manifest has no file list');

  const stage = mkdtempSync(path.join(tmpdir(), 'graft-apply-'));
  execFileSync('sh', [
    '-c',
    `zstd -d --long=27 -q -c ${JSON.stringify(args.patch)} | tar -xf - -C ${JSON.stringify(stage)}`,
  ]);
  const plan = JSON.parse(readFileSync(path.join(stage, 'plan.json'), 'utf8'));
  const byHref = new Map(
    plan.ops.filter(o => o.op !== 'delete').map(o => [o.href, o]),
  );

  rmSync(args.out, { recursive: true, force: true });
  mkdirSync(args.out, { recursive: true });

  let patched = 0;
  let kept = 0;
  let added = 0;

  for (const file of manifest.files) {
    const op = byHref.get(file.href);
    if (!op) throw new Error(`no op for manifest file: ${file.href}`);
    const target = safeJoin(args.out, file.href);
    mkdirSync(path.dirname(target), { recursive: true });

    if (op.op === 'keep') {
      copyFileSync(safeJoin(args.base, op.from ?? file.href), target);
      kept += 1;
    } else if (op.op === 'patch') {
      execFileSync('zstd', [
        '-d',
        '--long=27',
        '-q',
        '-f',
        '--patch-from',
        safeJoin(args.base, op.from),
        path.join(stage, op.patch),
        '-o',
        target,
      ]);
      patched += 1;
    } else if (op.op === 'add') {
      copyFileSync(path.join(stage, op.data), target);
      added += 1;
    } else {
      throw new Error(`unknown op: ${op.op}`);
    }

    const digest = createHash('sha256')
      .update(readFileSync(target))
      .digest('hex');
    if (digest !== file.sha256) {
      throw new Error(
        `sha256 mismatch after ${op.op}: ${file.href}\n  want ${file.sha256}\n  got  ${digest}`,
      );
    }
  }

  writeFileSync(
    path.join(args.out, MANIFEST_FILE_NAME),
    readFileSync(args.manifest),
  );
  rmSync(stage, { recursive: true, force: true });
  console.log(
    `applied: ${patched} patched, ${kept} kept, ${added} added — all ${manifest.files.length} verified`,
  );
}

main();
