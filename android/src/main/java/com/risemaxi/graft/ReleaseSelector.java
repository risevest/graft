package com.risemaxi.graft;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.risemaxi.graft.classes.ChannelRelease;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * Decides which release of a channel this install should move to. Kept free of platform state so it
 * stays identical to its iOS counterpart.
 */
public final class ReleaseSelector {

    private ReleaseSelector() {}

    /**
     * @return A value in 0..99 that stays fixed for the lifetime of the install, so a device keeps
     *         its position as a rollout widens.
     */
    public static int bucketFor(@NonNull String installId) {
        int hash = 0x811c9dc5;
        for (byte value : installId.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (value & 0xff);
            hash *= 0x01000193;
        }
        return (int) (Integer.toUnsignedLong(hash) % 100);
    }

    /**
     * @return The highest-counter release this install is eligible for, or `null` when it is already
     *         on the newest release it can run.
     */
    @Nullable
    public static ChannelRelease select(
        @NonNull List<ChannelRelease> releases,
        long versionCode,
        long highestInstalledCounter,
        int bucket,
        @NonNull Set<String> blockedBundleIds
    ) {
        ChannelRelease selected = null;
        for (ChannelRelease release : releases) {
            if (release.getMinNativeBuild() > versionCode) {
                continue;
            }
            if (release.getCounter() <= highestInstalledCounter) {
                continue;
            }
            if (release.getRollout() <= bucket) {
                continue;
            }
            if (blockedBundleIds.contains(release.getId())) {
                continue;
            }
            if (selected == null || release.getCounter() > selected.getCounter()) {
                selected = release;
            }
        }
        return selected;
    }
}
