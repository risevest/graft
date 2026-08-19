package com.risemaxi.graft.classes;

import androidx.annotation.NonNull;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * One entry of a channel document. Every value here is an unverified hint used to decide which
 * manifest to fetch; the manifest itself carries the signed copies that are enforced.
 */
public class ChannelRelease {

    @NonNull
    private final String id;

    private final long counter;
    private final int rollout;
    @NonNull
    private final String nativeFingerprint;

    @NonNull
    private final String manifest;

    @NonNull
    private final String signature;

    public ChannelRelease(@NonNull JSONObject json) throws JSONException {
        this.id = json.getString("id");
        this.counter = json.getLong("counter");
        this.rollout = json.getInt("rollout");
        this.nativeFingerprint = json.getString("nativeFingerprint");
        this.manifest = json.getString("manifest");
        this.signature = json.getString("sig");
        if (rollout < 0 || rollout > 100) {
            throw new JSONException("rollout must be between 0 and 100: " + rollout);
        }
    }

    @NonNull
    public String getId() {
        return id;
    }

    public long getCounter() {
        return counter;
    }

    public int getRollout() {
        return rollout;
    }

    @NonNull
    public String getNativeFingerprint() {
        return nativeFingerprint;
    }

    @NonNull
    public String getManifest() {
        return manifest;
    }

    @NonNull
    public String getSignature() {
        return signature;
    }
}
