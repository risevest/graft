#!/usr/bin/env node
// Derives malformed patch archives from a good one, for exercising the apply path's rejection
// behaviour. Every variant must be rejected, and none may leave a file whose digest is not the
// signed manifest's. Run from a directory holding `patch.gpz` and the two bundle directories.
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';

const MAGIC = Buffer.from('GRAFTP1\n', 'ascii');
const u32 = n => {
  const b = Buffer.allocUnsafe(4);
  b.writeUInt32BE(n, 0);
  return b;
};

function read(file) {
  const raw = execFileSync('zstd', ['-d', '--long=27', '-q', '-c', file], {
    maxBuffer: 1 << 30,
  });
  let o = MAGIC.length;
  const planLen = raw.readUInt32BE(o);
  o += 4;
  const plan = JSON.parse(raw.subarray(o, o + planLen).toString('utf8'));
  o += planLen;
  const count = raw.readUInt32BE(o);
  o += 4;
  const payloads = [];
  for (let i = 0; i < count; i++) {
    const n = raw.readUInt32BE(o);
    o += 4;
    payloads.push(Buffer.from(raw.subarray(o, o + n)));
    o += n;
  }
  return { plan, payloads };
}

function write(out, plan, payloads) {
  const planBuf = Buffer.from(JSON.stringify(plan), 'utf8');
  const container = Buffer.concat([
    MAGIC,
    u32(planBuf.length),
    planBuf,
    u32(payloads.length),
    ...payloads.flatMap(p => [u32(p.length), p]),
  ]);
  writeFileSync('/tmp/c.bin', container);
  execFileSync('zstd', [
    '-19',
    '--long=27',
    '-q',
    '-f',
    '/tmp/c.bin',
    '-o',
    out,
  ]);
}

const { plan, payloads } = read('patch.gpz');
const clone = () => JSON.parse(JSON.stringify(plan));

// 1. truncated archive
const good = readFileSync('patch.gpz');
writeFileSync(
  'bad-truncated.gpz',
  good.subarray(0, Math.floor(good.length * 0.6)),
);

// 2. a patch payload with a flipped byte -> reconstructs to the wrong bytes
{
  const p = payloads.map(b => Buffer.from(b));
  const target = plan.ops.findIndex(o => o.op === 'patch');
  const idx = plan.ops[target].payload;
  p[idx][Math.floor(p[idx].length / 2)] ^= 0xff;
  write('bad-payload.gpz', plan, p);
}

// 3. plan omits an op for a file the manifest lists
{
  const p = clone();
  const i = p.ops.findIndex(o => o.op === 'patch');
  const dropped = p.ops[i].href;
  p.ops.splice(i, 1);
  write('bad-missing-op.gpz', p, payloads);
  console.log('missing-op drops:', dropped);
}

// 4. base href that does not exist in the old bundle
{
  const p = clone();
  const i = p.ops.findIndex(o => o.op === 'patch');
  p.ops[i].from = 'assets/does-not-exist-XXXXXXXX.js';
  write('bad-base.gpz', p, payloads);
}

// 5. base href that climbs out of the bundle directory
{
  const p = clone();
  const i = p.ops.findIndex(o => o.op === 'keep');
  p.ops[i].from = '../../../../../../etc/passwd';
  write('bad-traversal.gpz', p, payloads);
}

console.log('variants written');
