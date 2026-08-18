package com.risemaxi.graft;

import androidx.annotation.NonNull;
import com.github.luben.zstd.ZstdInputStream;
import com.risemaxi.graft.classes.Manifest;
import com.risemaxi.graft.classes.ManifestFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Applies a patch archive produced by `tools/make-patch.mjs`.
 *
 * <p>The plan inside the archive says how to reconstruct each file; it never says whether the result
 * is acceptable. Reconstruction is driven by the signed manifest's file list, every output file is
 * verified against its manifest digest, and a manifest entry the plan does not cover is an error.
 */
final class GraftPatch {

    interface BaseReader {
        @NonNull
        byte[] read(@NonNull String href) throws Exception;
    }

    private static final byte[] MAGIC = { 'G', 'R', 'A', 'F', 'T', 'P', '1', '\n' };
    private static final int SCHEMA = 1;
    private static final int WINDOW_LOG_MAX = 27;

    private GraftPatch() {}

    static void apply(@NonNull File archive, @NonNull BaseReader base, @NonNull Manifest manifest, @NonNull File targetDirectory)
        throws Exception {
        byte[] container = inflate(archive);
        ByteBuffer buffer = ByteBuffer.wrap(container);

        for (byte expected : MAGIC) {
            if (!buffer.hasRemaining() || buffer.get() != expected) {
                throw new Exception(GraftPlugin.ERROR_PATCH_FAILED);
            }
        }

        JSONObject plan = new JSONObject(new String(readBlock(buffer), "UTF-8"));
        if (plan.optInt("schema") != SCHEMA) {
            throw new Exception(GraftPlugin.ERROR_PATCH_FAILED);
        }

        int payloadCount = readLength(buffer);
        byte[][] payloads = new byte[payloadCount][];
        for (int index = 0; index < payloadCount; index += 1) {
            payloads[index] = readBlock(buffer);
        }
        if (buffer.hasRemaining()) {
            throw new Exception(GraftPlugin.ERROR_PATCH_FAILED);
        }

        Map<String, JSONObject> operationByHref = indexOperations(plan.getJSONArray("ops"));

        for (ManifestFile file : manifest.getFiles()) {
            JSONObject operation = operationByHref.get(file.getHref());
            if (operation == null) {
                throw new Exception(GraftPlugin.ERROR_PATCH_FAILED);
            }

            byte[] content;
            String kind = operation.getString("op");
            switch (kind) {
                case "keep":
                    content = base.read(safeHref(operation.getString("from")));
                    break;
                case "patch":
                    content = patch(
                        base.read(safeHref(operation.getString("from"))),
                        payloadAt(payloads, operation.getInt("payload")),
                        file.getSize()
                    );
                    break;
                case "add":
                    content = payloadAt(payloads, operation.getInt("payload"));
                    break;
                default:
                    throw new Exception(GraftPlugin.ERROR_PATCH_FAILED);
            }

            if (!digestOf(content).equals(file.getSha256())) {
                throw new Exception(GraftPlugin.ERROR_PATCH_CHECKSUM_MISMATCH);
            }

            File destination = new File(targetDirectory, file.getHref());
            File parent = destination.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new Exception(GraftPlugin.ERROR_PATCH_FAILED);
            }
            try (FileOutputStream out = new FileOutputStream(destination)) {
                out.write(content);
            }
        }
    }

    @NonNull
    private static Map<String, JSONObject> indexOperations(@NonNull JSONArray operations) throws Exception {
        Map<String, JSONObject> byHref = new HashMap<>();
        for (int index = 0; index < operations.length(); index += 1) {
            JSONObject operation = operations.getJSONObject(index);
            if ("delete".equals(operation.optString("op"))) {
                continue;
            }
            byHref.put(operation.getString("href"), operation);
        }
        return byHref;
    }

    /**
     * `--patch-from` emits a frame that references the base as a raw content dictionary, so applying
     * one is ordinary decompression with a prefix loaded — no patch library is involved.
     */
    @NonNull
    private static byte[] patch(@NonNull byte[] base, @NonNull byte[] delta, long size) throws Exception {
        if (size < 0 || size > Integer.MAX_VALUE) {
            throw new Exception(GraftPlugin.ERROR_PATCH_FAILED);
        }
        try (ZstdInputStream stream = new ZstdInputStream(new ByteArrayInputStream(delta))) {
            stream.setLongMax(WINDOW_LOG_MAX);
            stream.setDict(base);
            byte[] content = readFully(stream, (int) size);
            if (content.length != size) {
                throw new Exception(GraftPlugin.ERROR_PATCH_FAILED);
            }
            return content;
        }
    }

    @NonNull
    private static byte[] inflate(@NonNull File archive) throws Exception {
        try (FileInputStream file = new FileInputStream(archive); ZstdInputStream stream = new ZstdInputStream(file)) {
            stream.setLongMax(WINDOW_LOG_MAX);
            return readFully(stream);
        }
    }

    @NonNull
    private static byte[] readFully(@NonNull InputStream stream) throws Exception {
        return readFully(stream, 8192);
    }

    @NonNull
    private static byte[] readFully(@NonNull InputStream stream, int expectedSize) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(expectedSize, 8192));
        byte[] chunk = new byte[8192];
        int read;
        while ((read = stream.read(chunk)) != -1) {
            out.write(chunk, 0, read);
        }
        return out.toByteArray();
    }

    @NonNull
    private static byte[] payloadAt(@NonNull byte[][] payloads, int index) throws Exception {
        if (index < 0 || index >= payloads.length) {
            throw new Exception(GraftPlugin.ERROR_PATCH_FAILED);
        }
        return payloads[index];
    }

    private static int readLength(@NonNull ByteBuffer buffer) throws Exception {
        if (buffer.remaining() < 4) {
            throw new Exception(GraftPlugin.ERROR_PATCH_FAILED);
        }
        int length = buffer.getInt();
        if (length < 0 || length > buffer.remaining()) {
            throw new Exception(GraftPlugin.ERROR_PATCH_FAILED);
        }
        return length;
    }

    @NonNull
    private static byte[] readBlock(@NonNull ByteBuffer buffer) throws Exception {
        byte[] block = new byte[readLength(buffer)];
        buffer.get(block);
        return block;
    }

    /**
     * The plan is untrusted input, so a base href it names is held to the same rule as a manifest
     * href: relative, and no segment that could climb out of the bundle directory.
     */
    @NonNull
    private static String safeHref(@NonNull String href) throws Exception {
        if (href.isEmpty() || href.startsWith("/")) {
            throw new Exception(GraftPlugin.ERROR_PATCH_FAILED);
        }
        for (String segment : href.split("/")) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new Exception(GraftPlugin.ERROR_PATCH_FAILED);
            }
        }
        return href;
    }

    @NonNull
    private static String digestOf(@NonNull byte[] content) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(Character.forDigit((value >> 4) & 0xf, 16));
            hex.append(Character.forDigit(value & 0xf, 16));
        }
        return hex.toString();
    }
}
