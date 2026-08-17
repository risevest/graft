package com.risemaxi.graft;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class GraftConfig {

    private boolean autoBlockRolledBackBundles = true;

    private boolean autoDeleteBundles = true;

    @NonNull
    private String autoUpdateStrategy = "none";

    @Nullable
    private String defaultChannel = null;

    private int httpTimeout = 60000;

    @Nullable
    private String publicKey = null;

    private int readyTimeout = 10000;

    @Nullable
    private String serverUrl = null;

    public boolean getAutoBlockRolledBackBundles() {
        return autoBlockRolledBackBundles;
    }

    public boolean getAutoDeleteBundles() {
        return autoDeleteBundles;
    }

    @NonNull
    public String getAutoUpdateStrategy() {
        return autoUpdateStrategy;
    }

    @Nullable
    public String getDefaultChannel() {
        return defaultChannel;
    }

    public int getHttpTimeout() {
        return httpTimeout;
    }

    @Nullable
    public String getPublicKey() {
        return publicKey;
    }

    public int getReadyTimeout() {
        return readyTimeout;
    }

    @Nullable
    public String getServerUrl() {
        return serverUrl;
    }

    public void setAutoBlockRolledBackBundles(boolean autoBlockRolledBackBundles) {
        this.autoBlockRolledBackBundles = autoBlockRolledBackBundles;
    }

    public void setAutoDeleteBundles(boolean autoDeleteBundles) {
        this.autoDeleteBundles = autoDeleteBundles;
    }

    public void setAutoUpdateStrategy(@NonNull String autoUpdateStrategy) {
        this.autoUpdateStrategy = autoUpdateStrategy;
    }

    public void setDefaultChannel(@Nullable String defaultChannel) {
        this.defaultChannel = defaultChannel;
    }

    public void setHttpTimeout(int httpTimeout) {
        this.httpTimeout = httpTimeout;
    }

    public void setPublicKey(@Nullable String publicKey) {
        this.publicKey = publicKey;
    }

    public void setReadyTimeout(int readyTimeout) {
        this.readyTimeout = readyTimeout;
    }

    public void setServerUrl(@Nullable String serverUrl) {
        this.serverUrl = serverUrl;
    }
}
