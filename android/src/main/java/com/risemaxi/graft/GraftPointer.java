package com.risemaxi.graft;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.pm.PackageInfoCompat;
import com.getcapacitor.Logger;
import com.risemaxi.graft.classes.Manifest;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import okio.BufferedSource;
import okio.Okio;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Owns the on-disk bundle layout and the pointer at the bundle to serve. Everything here runs before
 * the WebView exists, so it must stay synchronous and cheap.
 */
public final class GraftPointer {

    private static final String ACTIVE_BUNDLE_ID_KEY = "activeBundleId";
    private static final String BUNDLES_DIRECTORY = "graft/bundles";

    static final String EMBEDDED_WEB_ASSET_DIR = "public";
    static final String INDEX_FILE_NAME = "index.html";
    static final String MANIFEST_FILE_NAME = "graft-manifest.json";

    private GraftPointer() {}

    /**
     * @return The directory to serve, or `null` to serve the bundle embedded in the binary.
     */
    @Nullable
    public static File resolveActiveBundleDirectory(@NonNull Context context) {
        applyBinaryChangeRetention(context);
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

    /**
     * @return The id of the bundle this launch resolved, or `null` for the embedded one. Unlike the
     *         bridge's server path this is available before the WebView exists.
     */
    @Nullable
    public static String resolveActiveBundleId(@NonNull Context context) {
        File directory = resolveActiveBundleDirectory(context);
        return directory == null ? null : directory.getName();
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

    /**
     * Capacitor discards its own server path whenever the binary changes, which would silently
     * downgrade a device to whatever the store build embeds. We read our own pointer instead, so the
     * same decision is made here — but only for bundles the new binary genuinely cannot serve.
     */
    private static void applyBinaryChangeRetention(@NonNull Context context) {
        long nativeBuild = readNativeBuild(context);
        if (nativeBuild < 0) {
            Logger.error(GraftPlugin.TAG, "Cannot read the native build number, so a staged bundle cannot be reconciled.", null);
            return;
        }
        SharedPreferences preferences = GraftPreferences.getSharedPreferences(context);
        if (preferences.getLong(GraftPreferences.LAST_NATIVE_BUILD_KEY, -1) == nativeBuild) {
            return;
        }

        Manifest embeddedManifest = readEmbeddedManifest(context);
        String activeBundleId = getActiveBundleId(context);
        if (activeBundleId != null && !isBundleRunnable(context, activeBundleId, nativeBuild, embeddedManifest)) {
            Logger.debug(GraftPlugin.TAG, "Discarding bundle " + activeBundleId + " on native build " + nativeBuild + ".");
            clearActiveBundleId(context);
        }
        String lastKnownGoodBundleId = preferences.getString(GraftPreferences.LAST_KNOWN_GOOD_BUNDLE_ID_KEY, null);
        if (lastKnownGoodBundleId != null && !isBundleRunnable(context, lastKnownGoodBundleId, nativeBuild, embeddedManifest)) {
            preferences.edit().remove(GraftPreferences.LAST_KNOWN_GOOD_BUNDLE_ID_KEY).apply();
        }

        SharedPreferences.Editor editor = preferences.edit().putLong(GraftPreferences.LAST_NATIVE_BUILD_KEY, nativeBuild);
        Long embeddedCounter = embeddedManifest == null ? null : embeddedManifest.getCounter();
        if (embeddedCounter != null && embeddedCounter > preferences.getLong(GraftPreferences.HIGHEST_INSTALLED_COUNTER_KEY, 0)) {
            editor.putLong(GraftPreferences.HIGHEST_INSTALLED_COUNTER_KEY, embeddedCounter);
        }
        editor.commit();
    }

    private static boolean isBundleRunnable(
        @NonNull Context context,
        @NonNull String bundleId,
        long nativeBuild,
        @Nullable Manifest embeddedManifest
    ) {
        Manifest manifest = readManifest(new File(buildBundleDirectory(context, bundleId), MANIFEST_FILE_NAME));
        if (manifest == null) {
            return false;
        }
        if (manifest.getMinNativeBuild() > nativeBuild) {
            return false;
        }
        Long embeddedCounter = embeddedManifest == null ? null : embeddedManifest.getCounter();
        Long counter = manifest.getCounter();
        return embeddedCounter == null || counter == null || embeddedCounter <= counter;
    }

    @Nullable
    static Manifest readManifest(@NonNull File file) {
        if (!file.exists()) {
            return null;
        }
        try (InputStream inputStream = new FileInputStream(file)) {
            return parseManifest(inputStream);
        } catch (Exception exception) {
            Logger.error(GraftPlugin.TAG, "Failed to read manifest at " + file.getPath() + ".", exception);
            return null;
        }
    }

    @Nullable
    static Manifest readEmbeddedManifest(@NonNull Context context) {
        try (InputStream inputStream = context.getAssets().open(EMBEDDED_WEB_ASSET_DIR + "/" + MANIFEST_FILE_NAME)) {
            return parseManifest(inputStream);
        } catch (FileNotFoundException exception) {
            return null;
        } catch (Exception exception) {
            Logger.error(GraftPlugin.TAG, "Failed to read the embedded manifest.", exception);
            return null;
        }
    }

    @NonNull
    private static Manifest parseManifest(@NonNull InputStream inputStream) throws IOException, JSONException {
        BufferedSource source = Okio.buffer(Okio.source(inputStream));
        return new Manifest(new JSONObject(source.readUtf8()));
    }

    private static long readNativeBuild(@NonNull Context context) {
        try {
            return PackageInfoCompat.getLongVersionCode(context.getPackageManager().getPackageInfo(context.getPackageName(), 0));
        } catch (PackageManager.NameNotFoundException exception) {
            Logger.error(GraftPlugin.TAG, "Failed to read the native build number.", exception);
            return -1;
        }
    }

    @NonNull
    private static SharedPreferences getPreferences(@NonNull Context context) {
        return GraftPreferences.getSharedPreferences(context);
    }
}
