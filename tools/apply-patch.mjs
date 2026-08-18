#!/usr/bin/env node
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { copyFileSync, mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

const MANIFEST_FILE_NAME = "graft-manifest.json";
const MAGIC = Buffer.from("GRAFTP1\n", "ascii");

function usage() {
  console.error(
    "usage: apply-patch.mjs --base <bundle dir> --patch <patch.gpz> --manifest <manifest.json> --out <dir>",
  );
  process.exit(2);
}

function parseArgs(argv) {
  const args = {};
  for (let i = 0; i < argv.length; i += 2) {
    const key = argv[i];
    if (!key?.startsWith("--")) usage();
    args[key.slice(2)] = argv[i + 1];
  }
  if (!args.base || !args.patch || !args.manifest || !args.out) usage();
  return args;
}

function safeJoin(root, href) {
  if (path.isAbsolute(href)) throw new Error(`absolute href rejected: ${href}`);
  const segments = href.split("/");
  if (segments.some((s) => s === "" || s === "." || s === "..")) throw new Error(`unsafe href: ${href}`);
  return path.join(root, ...segments);
}

function readContainer(file) {
  const raw = execFileSync("zstd", ["-d", "--long=27", "-q", "-c", file], { maxBuffer: 1 << 30 });
  if (raw.length < MAGIC.length + 4 || !raw.subarray(0, MAGIC.length).equals(MAGIC)) {
    throw new Error("not a graft patch archive");
  }
  let offset = MAGIC.length;
  const planLength = raw.readUInt32BE(offset);
  offset += 4;
  const plan = JSON.parse(raw.subarray(offset, offset + planLength).toString("utf8"));
  offset += planLength;
  const count = raw.readUInt32BE(offset);
  offset += 4;
  const payloads = [];
  for (let i = 0; i < count; i += 1) {
    const length = raw.readUInt32BE(offset);
    offset += 4;
    payloads.push(raw.subarray(offset, offset + length));
    offset += length;
  }
  if (offset !== raw.length) throw new Error("trailing bytes in patch archive");
  return { plan, payloads };
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  const manifest = JSON.parse(readFileSync(args.manifest, "utf8"));
  if (!Array.isArray(manifest.files)) throw new Error("manifest has no file list");

  const { plan, payloads } = readContainer(args.patch);
  const byHref = new Map(plan.ops.filter((o) => o.op !== "delete").map((o) => [o.href, o]));

  const scratch = mkdtempSync(path.join(tmpdir(), "graft-apply-"));
  rmSync(args.out, { recursive: true, force: true });
  mkdirSync(args.out, { recursive: true });

  const totals = { patch: 0, keep: 0, add: 0 };

  for (const file of manifest.files) {
    const op = byHref.get(file.href);
    if (!op) throw new Error(`no op for manifest file: ${file.href}`);
    const target = safeJoin(args.out, file.href);
    mkdirSync(path.dirname(target), { recursive: true });

    if (op.op === "keep") {
      copyFileSync(safeJoin(args.base, op.from ?? file.href), target);
      totals.keep += 1;
    } else if (op.op === "patch") {
      const payload = path.join(scratch, "payload");
      writeFileSync(payload, payloads[op.payload]);
      execFileSync("zstd", [
        "-d",
        "--long=27",
        "-q",
        "-f",
        "--patch-from",
        safeJoin(args.base, op.from),
        payload,
        "-o",
        target,
      ]);
      totals.patch += 1;
    } else if (op.op === "add") {
      writeFileSync(target, payloads[op.payload]);
      totals.add += 1;
    } else {
      throw new Error(`unknown op: ${op.op}`);
    }

    const digest = createHash("sha256").update(readFileSync(target)).digest("hex");
    if (digest !== file.sha256) {
      throw new Error(`sha256 mismatch after ${op.op}: ${file.href}\n  want ${file.sha256}\n  got  ${digest}`);
    }
  }

  writeFileSync(path.join(args.out, MANIFEST_FILE_NAME), readFileSync(args.manifest));
  rmSync(scratch, { recursive: true, force: true });
  console.log(
    `applied: ${totals.patch} patched, ${totals.keep} kept, ${totals.add} added — all ${manifest.files.length} verified`,
  );
}

main();
