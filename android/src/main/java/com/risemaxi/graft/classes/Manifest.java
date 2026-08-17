package com.risemaxi.graft.classes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

    /**
     * Set only on a manifest published to a channel, where it stops a release for one channel being
     * served on another. The manifest generated for the embedded bundle has no channel to name.
     */
    @Nullable
    private final String channel;

    /**
     * The release ordering. Optional on the manifest generated for the embedded bundle: it is only
     * comparable when the consuming app counts releases and native builds on one scale, and a
     * consumer that cannot do that omits it rather than supplying a number that does not compare.
     */
    @Nullable
    private final Long counter;

    private final long minNativeBuild;

    @Nullable
    private final Long notBefore;

    @Nullable
    private final Long expiresAt;

    @NonNull
    private final List<ManifestFile> files = new ArrayList<>();

    public Manifest(@NonNull JSONObject json) throws JSONException {
        int schema = json.getInt("schema");
        if (schema != SUPPORTED_SCHEMA) {
            throw new JSONException("Unsupported manifest schema: " + schema);
        }
        this.id = json.getString("id");
        this.channel = json.has("channel") ? json.getString("channel") : null;
        this.counter = json.has("counter") ? json.getLong("counter") : null;
        this.minNativeBuild = json.getLong("minNativeBuild");
        this.notBefore = json.has("notBefore") ? json.getLong("notBefore") : null;
        this.expiresAt = json.has("expiresAt") ? json.getLong("expiresAt") : null;
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

    @Nullable
    public String getChannel() {
        return channel;
    }

    @Nullable
    public Long getCounter() {
        return counter;
    }

    public long getMinNativeBuild() {
        return minNativeBuild;
    }

    @Nullable
    public Long getNotBefore() {
        return notBefore;
    }

    @Nullable
    public Long getExpiresAt() {
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
