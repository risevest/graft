package com.risemaxi.graft.interfaces;

public interface DownloadProgressCallback {
    void onProgress(long downloadedBytes, long totalBytes);
}
