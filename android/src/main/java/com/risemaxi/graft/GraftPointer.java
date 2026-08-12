package com.risemaxi.graft;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;

public final class GraftPointer {

    private static final String ACTIVE_BUNDLE_ID_KEY = "activeBundleId";
    private static final String BUNDLES_DIRECTORY = "graft/bundles";
    private static final String INDEX_FILE_NAME = "index.html";

    private GraftPointer() {}

    @Nullable
    public static File resolveActiveBundleDirectory(@NonNull Context context) {
        String bundleId = getActiveBundleId(context);
        if (bundleId == null) {
            return null;
        }
        File directory = buildBundleDirectory(context, bundleId);
        if (new File(directory, INDEX_FILE_NAME).exists()) {
            return directory;
        }
        return null;
    }

    @Nullable
    public static String getActiveBundleId(@NonNull Context context) {
        return getPreferences(context).getString(ACTIVE_BUNDLE_ID_KEY, null);
    }

    public static void setActiveBundleId(@NonNull Context context, @NonNull String bundleId) {
        getPreferences(context).edit().putString(ACTIVE_BUNDLE_ID_KEY, bundleId).commit();
    }

    public static void clearActiveBundleId(@NonNull Context context) {
        getPreferences(context).edit().remove(ACTIVE_BUNDLE_ID_KEY).commit();
    }

    @NonNull
    public static File buildBundlesDirectory(@NonNull Context context) {
        return new File(context.getFilesDir(), BUNDLES_DIRECTORY);
    }

    @NonNull
    public static File buildBundleDirectory(@NonNull Context context, @NonNull String bundleId) {
        return new File(buildBundlesDirectory(context), bundleId);
    }

    @NonNull
    private static SharedPreferences getPreferences(@NonNull Context context) {
        return context.getSharedPreferences(GraftPlugin.SHARED_PREFERENCES_NAME, Activity.MODE_PRIVATE);
    }
}
