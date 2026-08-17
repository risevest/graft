package com.risemaxi.graft.classes.options;

import androidx.annotation.NonNull;

public class DownloadBundleOptions {

    @NonNull
    private final String bundleId;

    @NonNull
    private final String checksum;

    @NonNull
    private final String url;

    public DownloadBundleOptions(@NonNull String bundleId, @NonNull String checksum, @NonNull String url) {
        this.bundleId = bundleId;
        this.checksum = checksum;
        this.url = url;
    }

    @NonNull
    public String getBundleId() {
        return bundleId;
    }

    @NonNull
    public String getChecksum() {
        return checksum;
    }

    @NonNull
    public String getUrl() {
        return url;
    }
}
