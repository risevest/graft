package com.risemaxi.graft.classes;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The list of releases published to a channel. It is served unsigned and edge-cached, so it is only
 * ever used to choose a manifest to fetch.
 */
public class ChannelDocument {

    private static final int SUPPORTED_SCHEMA = 1;

    private final boolean killSwitch;

    @NonNull
    private final List<ChannelRelease> releases = new ArrayList<>();

    public ChannelDocument(@NonNull JSONObject json) throws JSONException {
        int schema = json.getInt("schema");
        if (schema != SUPPORTED_SCHEMA) {
            throw new JSONException("Unsupported channel document schema: " + schema);
        }
        this.killSwitch = json.optBoolean("killSwitch", false);
        JSONArray releasesJson = json.getJSONArray("releases");
        for (int i = 0; i < releasesJson.length(); i++) {
            releases.add(new ChannelRelease(releasesJson.getJSONObject(i)));
        }
    }

    public boolean isKillSwitchEnabled() {
        return killSwitch;
    }

    @NonNull
    public List<ChannelRelease> getReleases() {
        return releases;
    }
}
