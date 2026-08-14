package com.risemaxi.graft.classes;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The signed description of a release. Its raw bytes are what the detached signature covers, so it
 * is parsed only after that signature has been verified.
 */
public class Manifest {

    private static final int SUPPORTED_SCHEMA = 1;

    @NonNull
    private final String id;

    @NonNull
    private final String channel;

    private final long counter;
    private final long minNativeBuild;
    private final long notBefore;
    private final long expiresAt;

    @NonNull
    private final List<ManifestFile> files = new ArrayList<>();

    public Manifest(@NonNull JSONObject json) throws JSONException {
        int schema = json.getInt("schema");
        if (schema != SUPPORTED_SCHEMA) {
            throw new JSONException("Unsupported manifest schema: " + schema);
        }
        this.id = json.getString("id");
        this.channel = json.getString("channel");
        this.counter = json.getLong("counter");
        this.minNativeBuild = json.getLong("minNativeBuild");
        this.notBefore = json.getLong("notBefore");
        this.expiresAt = json.getLong("expiresAt");
        JSONArray filesJson = json.getJSONArray("files");
        for (int i = 0; i < filesJson.length(); i++) {
            files.add(new ManifestFile(filesJson.getJSONObject(i)));
        }
        if (files.isEmpty()) {
            throw new JSONException("Manifest lists no files.");
        }
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getChannel() {
        return channel;
    }

    public long getCounter() {
        return counter;
    }

    public long getMinNativeBuild() {
        return minNativeBuild;
    }

    public long getNotBefore() {
        return notBefore;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    @NonNull
    public List<ManifestFile> getFiles() {
        return files;
    }

    /**
     * @return The path each digest can be read from in a bundle described by this manifest.
     */
    @NonNull
    public Map<String, String> buildHrefBySha256() {
        Map<String, String> hrefBySha256 = new HashMap<>();
        for (ManifestFile file : files) {
            hrefBySha256.putIfAbsent(file.getSha256(), file.getHref());
        }
        return hrefBySha256;
    }
}
