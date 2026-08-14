package com.risemaxi.graft.classes;

import androidx.annotation.NonNull;
import org.json.JSONException;
import org.json.JSONObject;

public class ManifestFile {

    @NonNull
    private final String href;

    @NonNull
    private final String sha256;

    private final long size;

    public ManifestFile(@NonNull JSONObject json) throws JSONException {
        this.href = requireRelativePath(json.getString("href"));
        this.sha256 = json.getString("sha256");
        this.size = json.getLong("size");
        if (sha256.length() != 64) {
            throw new JSONException("sha256 must be a hex-encoded SHA-256 digest: " + sha256);
        }
        if (size < 0) {
            throw new JSONException("size must not be negative: " + size);
        }
    }

    @NonNull
    public String getHref() {
        return href;
    }

    @NonNull
    public String getSha256() {
        return sha256;
    }

    public long getSize() {
        return size;
    }

    @NonNull
    private static String requireRelativePath(@NonNull String href) throws JSONException {
        if (href.isEmpty() || href.startsWith("/") || href.contains("\\") || href.contains("//")) {
            throw new JSONException("href must be a relative path: " + href);
        }
        for (String segment : href.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                throw new JSONException("href must not traverse directories: " + href);
            }
        }
        return href;
    }
}
