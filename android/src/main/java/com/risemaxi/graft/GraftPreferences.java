package com.risemaxi.graft;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.UUID;

public class GraftPreferences {

    static final String BLOCKED_BUNDLE_IDS_KEY = "blockedBundleIds";
    static final String CHANNEL_KEY = "channel";
    static final String HIGHEST_INSTALLED_COUNTER_KEY = "highestInstalledCounter";
    static final String INSTALL_ID_KEY = "installId";
    static final String LAST_KNOWN_GOOD_BUNDLE_ID_KEY = "lastKnownGoodBundleId";
    static final String LAST_NATIVE_BUILD_KEY = "lastNativeBuild";
    static final String PREVIOUS_BUNDLE_ID_KEY = "previousBundleId";

    @NonNull
    private final SharedPreferences preferences;

    public GraftPreferences(@NonNull Context context) {
        this.preferences = getSharedPreferences(context);
    }

    @NonNull
    static SharedPreferences getSharedPreferences(@NonNull Context context) {
        return context.getSharedPreferences(GraftPlugin.SHARED_PREFERENCES_NAME, Activity.MODE_PRIVATE);
    }

    @Nullable
    public String getBlockedBundleIds() {
        return preferences.getString(BLOCKED_BUNDLE_IDS_KEY, null);
    }

    @Nullable
    public String getChannel() {
        return preferences.getString(CHANNEL_KEY, null);
    }

    public long getHighestInstalledCounter() {
        return preferences.getLong(HIGHEST_INSTALLED_COUNTER_KEY, 0);
    }

    /**
     * @return A random identifier, created on first use, that fixes this install's rollout bucket.
     */
    @NonNull
    public String getInstallId() {
        String installId = preferences.getString(INSTALL_ID_KEY, null);
        if (installId == null) {
            installId = UUID.randomUUID().toString().toLowerCase();
            preferences.edit().putString(INSTALL_ID_KEY, installId).apply();
        }
        return installId;
    }

    @Nullable
    public String getLastKnownGoodBundleId() {
        return preferences.getString(LAST_KNOWN_GOOD_BUNDLE_ID_KEY, null);
    }

    @Nullable
    public String getPreviousBundleId() {
        return preferences.getString(PREVIOUS_BUNDLE_ID_KEY, null);
    }

    public void setBlockedBundleIds(@Nullable String blockedBundleIds) {
        putString(BLOCKED_BUNDLE_IDS_KEY, blockedBundleIds);
    }

    public void setChannel(@Nullable String channel) {
        putString(CHANNEL_KEY, channel);
    }

    public void setHighestInstalledCounter(long counter) {
        preferences.edit().putLong(HIGHEST_INSTALLED_COUNTER_KEY, counter).apply();
    }

    public void setLastKnownGoodBundleId(@Nullable String bundleId) {
        putString(LAST_KNOWN_GOOD_BUNDLE_ID_KEY, bundleId);
    }

    public void setPreviousBundleId(@Nullable String bundleId) {
        putString(PREVIOUS_BUNDLE_ID_KEY, bundleId);
    }

    private void putString(@NonNull String key, @Nullable String value) {
        SharedPreferences.Editor editor = preferences.edit();
        if (value == null) {
            editor.remove(key);
        } else {
            editor.putString(key, value);
        }
        editor.apply();
    }
}
