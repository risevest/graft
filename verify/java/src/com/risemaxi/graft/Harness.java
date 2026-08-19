package com.risemaxi.graft;

import com.risemaxi.graft.classes.Manifest;
import com.risemaxi.graft.classes.ManifestFile;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

public class Harness {

    static File fixtures;
    static File base;
    static Manifest manifest;
    static int failures = 0;

    public static void main(String[] args) throws Exception {
        fixtures = new File(System.getenv("FIXTURES"));
        base = new File(fixtures, "pt-a");
        String manifestJson = Files.readString(new File(fixtures, "pt-b/graft-manifest.json").toPath());
        manifest = new Manifest(new JSONObject(manifestJson));

        System.out.println("manifest: " + manifest.getFiles().size() + " files, release " + manifest.getId() + "\n");
        run("happy", "patch.gpz", true);
        run("truncated", "bad-truncated.gpz", false);
        run("payload", "bad-payload.gpz", false);
        run("missing-op", "bad-missing-op.gpz", false);
        run("bad-base", "bad-base.gpz", false);
        run("traversal", "bad-traversal.gpz", false);
        run("wrong-content", "bad-swapped.gpz", false);

        System.out.println("\n" + (failures == 0 ? "ALL CASES PASSED" : failures + " CASE(S) FAILED"));
        System.exit(failures == 0 ? 0 : 1);
    }

    static void run(String name, String archive, boolean expectSuccess) throws Exception {
        File out = new File(fixtures, "jout-" + name);
        deleteRecursively(out);
        out.mkdirs();

        Exception thrown = null;
        try {
            GraftPatch.apply(new File(fixtures, archive), href -> Files.readAllBytes(new File(base, href).toPath()), manifest, out);
        } catch (Exception exception) {
            thrown = exception;
        }

        int produced = countFiles(out);
        boolean escaped = new File(fixtures, "passwd").exists();

        List<String> mismatched = new ArrayList<>();
        for (ManifestFile file : manifest.getFiles()) {
            File written = new File(out, file.getHref());
            if (!written.exists()) continue;
            if (!digestOf(Files.readAllBytes(written.toPath())).equals(file.getSha256())) {
                mismatched.add(file.getHref());
            }
        }

        boolean ok;
        String detail;
        if (expectSuccess) {
            ok = thrown == null && mismatched.isEmpty() && produced == manifest.getFiles().size();
            detail = "applied " + produced + "/" + manifest.getFiles().size() + " files, all digests match: " + mismatched.isEmpty();
        } else {
            ok = thrown != null && mismatched.isEmpty() && !escaped;
            detail =
                "rejected: " +
                (thrown == null ? "DID NOT THROW" : thrown.getMessage()) +
                "; wrote " +
                produced +
                " partial file(s), none mismatched: " +
                mismatched.isEmpty();
        }
        System.out.printf("%s  %-16s %s%n", ok ? "PASS" : "FAIL", name, detail);
        if (!ok) failures += 1;
        deleteRecursively(out);
    }

    static int countFiles(File root) {
        File[] children = root.listFiles();
        if (children == null) return 0;
        int total = 0;
        for (File child : children) total += child.isDirectory() ? countFiles(child) : 1;
        return total;
    }

    static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }

    static String digestOf(byte[] content) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
        StringBuilder hex = new StringBuilder();
        for (byte value : digest) {
            hex.append(Character.forDigit((value >> 4) & 0xf, 16));
            hex.append(Character.forDigit(value & 0xf, 16));
        }
        return hex.toString();
    }
}
