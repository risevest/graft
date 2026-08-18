#!/usr/bin/env node
import { execFileSync } from 'node:child_process';
import {
  mkdtempSync,
  readFileSync,
  writeFileSync,
  rmSync,
  statSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

const MANIFEST_FILE_NAME = 'graft-manifest.json';
const MAGIC = Buffer.from('GRAFTP1\n', 'ascii');
const SCHEMA = 1;
const BASE_CANDIDATES = 3;

function usage() {
  console.error(
    'usage: make-patch.mjs --old <bundle dir> --new <bundle dir> --out <patch.gpz> [--level 19]',
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
  if (!args.old || !args.new || !args.out) usage();
  return args;
}

function readManifest(dir) {
  const file = path.join(dir, MANIFEST_FILE_NAME);
  const manifest = JSON.parse(readFileSync(file, 'utf8'));
  if (!Array.isArray(manifest.files)) throw new Error(`${file}: no file list`);
  return manifest;
}

const HASH_SUFFIX = /-[A-Za-z0-9_-]{8,}(?=\.[^.]+$)/;

function chunkIdentity(href) {
  return href.replace(HASH_SUFFIX, '');
}

function candidateBases(file, oldFiles, oldByIdentity) {
  if (oldFiles.has(file.href)) return [file.href];
  const pool = oldByIdentity.get(chunkIdentity(file.href)) ?? [];
  const extension = path.extname(file.href);
  return pool
    .filter(href => path.extname(href) === extension)
    .sort(
      (a, b) =>
        Math.abs(oldFiles.get(a).size - file.size) -
        Math.abs(oldFiles.get(b).size - file.size),
    )
    .slice(0, BASE_CANDIDATES);
}

function uint32(value) {
  const buffer = Buffer.allocUnsafe(4);
  buffer.writeUInt32BE(value, 0);
  return buffer;
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  const level = args.level ?? '19';
  const oldManifest = readManifest(args.old);
  const newManifest = readManifest(args.new);

  const oldFiles = new Map(oldManifest.files.map(f => [f.href, f]));
  const oldByDigest = new Map();
  const oldByIdentity = new Map();
  for (const f of oldManifest.files) {
    if (!oldByDigest.has(f.sha256)) oldByDigest.set(f.sha256, f.href);
    const identity = chunkIdentity(f.href);
    if (!oldByIdentity.has(identity)) oldByIdentity.set(identity, []);
    oldByIdentity.get(identity).push(f.href);
  }

  const scratch = mkdtempSync(path.join(tmpdir(), 'graft-patch-'));
  const probe = path.join(scratch, 'probe');
  const payloads = [];
  const ops = [];
  const totals = { keep: 0, patch: 0, add: 0, delete: 0 };

  for (const file of newManifest.files) {
    const reusable = oldByDigest.get(file.sha256);
    if (reusable) {
      ops.push({ op: 'keep', href: file.href, from: reusable });
      totals.keep += 1;
      continue;
    }

    const fullSize = file.size ?? statSync(path.join(args.new, file.href)).size;
    let best = null;
    for (const base of candidateBases(file, oldFiles, oldByIdentity)) {
      execFileSync('zstd', [
        `-${level}`,
        '--long=27',
        '-q',
        '-f',
        '--patch-from',
        path.join(args.old, base),
        path.join(args.new, file.href),
        '-o',
        probe,
      ]);
      const size = statSync(probe).size;
      if (best === null || size < best.bytes.length) {
        best = { base, bytes: readFileSync(probe) };
      }
    }

    if (best !== null && best.bytes.length < fullSize) {
      ops.push({
        op: 'patch',
        href: file.href,
        from: best.base,
        payload: payloads.length,
      });
      payloads.push(best.bytes);
      totals.patch += 1;
      continue;
    }

    ops.push({ op: 'add', href: file.href, payload: payloads.length });
    payloads.push(readFileSync(path.join(args.new, file.href)));
    totals.add += 1;
  }

  const newHrefs = new Set(newManifest.files.map(f => f.href));
  for (const f of oldManifest.files) {
    if (newHrefs.has(f.href)) continue;
    ops.push({ op: 'delete', href: f.href });
    totals.delete += 1;
  }

  const plan = Buffer.from(
    JSON.stringify({
      schema: SCHEMA,
      from: oldManifest.id,
      to: newManifest.id,
      ops,
    }),
    'utf8',
  );
  const container = Buffer.concat([
    MAGIC,
    uint32(plan.length),
    plan,
    uint32(payloads.length),
    ...payloads.flatMap(payload => [uint32(payload.length), payload]),
  ]);

  const raw = path.join(scratch, 'container');
  writeFileSync(raw, container);
  execFileSync('zstd', [
    `-${level}`,
    '--long=27',
    '-q',
    '-f',
    raw,
    '-o',
    args.out,
  ]);
  rmSync(scratch, { recursive: true, force: true });

  console.log(
    `${oldManifest.id} -> ${newManifest.id}: keep ${totals.keep}, patch ${totals.patch}, add ${totals.add}, delete ${totals.delete}`,
  );
  console.log(
    `patch archive: ${statSync(args.out).size} bytes (container ${container.length} bytes)`,
  );
}

main();
