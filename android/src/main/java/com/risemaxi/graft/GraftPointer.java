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
     * Set when a host asks where to serve from, which is the only moment the pointer can take effect.
     * Graft's own reads deliberately bypass this, so it records the host wiring and nothing else.
     */
    private static boolean wasAskedForBundleDirectory = false;

    static boolean wasAskedForBundleDirectory() {
        return wasAskedForBundleDirectory;
    }

    /**
     * @return The directory to serve, or `null` to serve the bundle embedded in the binary.
     */
    @Nullable
    public static File resolveActiveBundleDirectory(@NonNull Context context) {
        wasAskedForBundleDirectory = true;
        return activeBundleDirectory(context);
    }

    @Nullable
    private static File activeBundleDirectory(@NonNull Context context) {
        discardBundlesThisBinaryCannotServe(context);
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
        File directory = activeBundleDirectory(context);
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
     * downgrade a device to whatever the store build embeds. We keep our own pointer instead and drop
     * only the bundles this binary genuinely cannot serve.
     *
     * Runs on every launch rather than only when the binary changed. The comparison it makes is
     * against the bundle on disk, so it needs no memory of previous launches, and reaching the same
     * answer costs two manifest reads — hundredths of a millisecond against a file already in page
     * cache. Remembering instead would mean storing a fact whose source of truth is that same file.
     */
    private static void discardBundlesThisBinaryCannotServe(@NonNull Context context) {
        Manifest embeddedManifest = readEmbeddedManifest(context);
        String fingerprint = embeddedManifest == null ? null : embeddedManifest.getNativeFingerprint();
        if (fingerprint == null) {
            Logger.error(GraftPlugin.TAG, "The embedded manifest carries no native fingerprint, so a staged bundle cannot be reconciled.", null);
            return;
        }
        SharedPreferences preferences = GraftPreferences.getSharedPreferences(context);
        String activeBundleId = getActiveBundleId(context);
        if (activeBundleId != null && !isBundleRunnable(context, activeBundleId, fingerprint, embeddedManifest)) {
            Logger.debug(GraftPlugin.TAG, "Discarding bundle " + activeBundleId + ": it was built against different native code.");
            clearActiveBundleId(context);
        }
        String lastKnownGoodBundleId = preferences.getString(GraftPreferences.LAST_KNOWN_GOOD_BUNDLE_ID_KEY, null);
        if (lastKnownGoodBundleId != null && !isBundleRunnable(context, lastKnownGoodBundleId, fingerprint, embeddedManifest)) {
            preferences.edit().remove(GraftPreferences.LAST_KNOWN_GOOD_BUNDLE_ID_KEY).apply();
        }

        SharedPreferences.Editor editor = preferences.edit();
        Long embeddedCounter = embeddedManifest == null ? null : embeddedManifest.getCounter();
        if (embeddedCounter != null && embeddedCounter > preferences.getLong(GraftPreferences.HIGHEST_INSTALLED_COUNTER_KEY, 0)) {
            editor.putLong(GraftPreferences.HIGHEST_INSTALLED_COUNTER_KEY, embeddedCounter);
        }
        editor.commit();
    }

    private static boolean isBundleRunnable(
        @NonNull Context context,
        @NonNull String bundleId,
        @NonNull String nativeFingerprint,
        @Nullable Manifest embeddedManifest
    ) {
        Manifest manifest = readManifest(new File(buildBundleDirectory(context, bundleId), MANIFEST_FILE_NAME));
        if (manifest == null) {
            return false;
        }
        if (!manifest.getNativeFingerprint().equals(nativeFingerprint)) {
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
