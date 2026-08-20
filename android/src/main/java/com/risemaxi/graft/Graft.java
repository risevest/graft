package com.risemaxi.graft;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.pm.PackageInfoCompat;
import com.getcapacitor.Bridge;
import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;
import com.risemaxi.graft.classes.ChannelDocument;
import com.risemaxi.graft.classes.ChannelRelease;
import com.risemaxi.graft.classes.Manifest;
import com.risemaxi.graft.classes.ManifestFile;
import com.risemaxi.graft.classes.events.DownloadBundleProgressEvent;
import com.risemaxi.graft.classes.events.NextBundleSetEvent;
import com.risemaxi.graft.classes.options.DeleteBundleOptions;
import com.risemaxi.graft.classes.options.DownloadBundleOptions;
import com.risemaxi.graft.classes.options.SetChannelOptions;
import com.risemaxi.graft.classes.options.SetNextBundleOptions;
import com.risemaxi.graft.classes.options.SyncOptions;
import com.risemaxi.graft.classes.results.GetBlockedBundlesResult;
import com.risemaxi.graft.classes.results.GetChannelResult;
import com.risemaxi.graft.classes.results.GetCurrentBundleResult;
import com.risemaxi.graft.classes.results.GetDownloadedBundlesResult;
import com.risemaxi.graft.classes.results.GetInstallIdResult;
import com.risemaxi.graft.classes.results.GetNextBundleResult;
import com.risemaxi.graft.classes.results.GetVersionCodeResult;
import com.risemaxi.graft.classes.results.GetVersionNameResult;
import com.risemaxi.graft.classes.results.IsSyncingResult;
import com.risemaxi.graft.classes.results.ReadyResult;
import com.risemaxi.graft.classes.results.SyncResult;
import com.risemaxi.graft.interfaces.DownloadProgressCallback;
import com.risemaxi.graft.interfaces.EmptyCallback;
import com.risemaxi.graft.interfaces.NonEmptyCallback;
import com.risemaxi.graft.interfaces.Result;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.Response;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;
import org.json.JSONObject;

public class Graft {

    private final long autoUpdateIntervalMs = 15 * 60 * 1000; // 15 minutes

    @NonNull
    private final GraftConfig config;

    private final String defaultWebAssetDir = Bridge.DEFAULT_WEB_ASSET_DIR;

    @NonNull
    private final GraftHttpClient httpClient;

    @NonNull
    private final GraftPlugin plugin;

    @NonNull
    private final GraftPreferences preferences;

    private final Handler rollbackHandler = new Handler(Looper.getMainLooper());

    private boolean initialPageLoaded = false;
    private long lastAutoUpdateCheckTimestamp = 0;
    private boolean rollbackPerformed = false;
    private boolean syncInProgress = false;

    public Graft(@NonNull GraftConfig config, @NonNull GraftPlugin plugin) {
        this.config = config;
        this.httpClient = new GraftHttpClient(config);
        this.plugin = plugin;
        this.preferences = new GraftPreferences(plugin.getContext());

        // Start the rollback timer to rollback to the last known good bundle
        // if the app is not ready after a certain time
        startRollbackTimer();
    }

    public void clearBlockedBundles() {
        preferences.setBlockedBundleIds(null);
    }

    public void deleteBundle(@NonNull DeleteBundleOptions options, @NonNull EmptyCallback callback) {
        String bundleId = options.getBundleId();

        if (!hasBundleById(bundleId)) {
            callback.error(new Exception(GraftPlugin.ERROR_BUNDLE_NOT_FOUND));
            return;
        }
        deleteBundleById(bundleId);

        callback.success();
    }

    public void downloadBundle(@NonNull DownloadBundleOptions options, @NonNull EmptyCallback callback) {
        String bundleId = options.getBundleId();

        if (hasBundleById(bundleId)) {
            callback.error(new Exception(GraftPlugin.ERROR_BUNDLE_EXISTS));
            return;
        }

        File file = new File(plugin.getContext().getCacheDir(), UUID.randomUUID().toString() + ".zip");
        downloadFile(
            options.getUrl(),
            file,
            (downloadedBytes, totalBytes) -> notifyDownloadBundleProgressListeners(bundleId, downloadedBytes, totalBytes),
            new EmptyCallback() {
                @Override
                public void success() {
                    try {
                        verifyChecksum(file, options.getChecksum());
                        File directory = unzipFile(file);
                        File indexHtmlFile = searchIndexHtmlFile(directory);
                        if (indexHtmlFile == null) {
                            throw new Exception(GraftPlugin.ERROR_BUNDLE_INDEX_HTML_MISSING);
                        }
                        moveBundleIntoPlace(indexHtmlFile.getParentFile(), bundleId);
                        callback.success();
                    } catch (Exception exception) {
                        callback.error(exception);
                    } finally {
                        file.delete();
                    }
                }

                @Override
                public void error(@NonNull Exception exception) {
                    file.delete();
                    callback.error(exception);
                }
            }
        );
    }

    public void getBlockedBundles(@NonNull NonEmptyCallback<GetBlockedBundlesResult> callback) {
        Set<String> bundleIds = getBlockedBundleIds();
        callback.success(new GetBlockedBundlesResult(bundleIds.toArray(new String[0])));
    }

    public void getChannel(@NonNull NonEmptyCallback<GetChannelResult> callback) {
        callback.success(new GetChannelResult(getChannel()));
    }

    public void getCurrentBundle(@NonNull NonEmptyCallback<GetCurrentBundleResult> callback) {
        callback.success(new GetCurrentBundleResult(getCurrentBundleId()));
    }

    public void getDownloadedBundles(@NonNull NonEmptyCallback<GetDownloadedBundlesResult> callback) {
        callback.success(new GetDownloadedBundlesResult(getDownloadedBundleIds()));
    }

    /**
     * @return What the page is told about the bundle it is running, so an app never has to compile a
     *         release identifier into its own bundle.
     */
    @NonNull
    public JSObject getReleaseIdentity() {
        Manifest embeddedManifest = GraftPointer.readEmbeddedManifest(plugin.getContext());
        String releaseId = GraftPointer.resolveActiveBundleId(plugin.getContext());
        if (releaseId == null) {
            releaseId = embeddedManifest == null ? null : embeddedManifest.getId();
        }

        JSObject identity = new JSObject();
        identity.put("releaseId", releaseId == null ? JSONObject.NULL : releaseId);
        identity.put(
            "nativeFingerprint",
            embeddedManifest == null ? JSONObject.NULL : embeddedManifest.getNativeFingerprint()
        );
        try {
            identity.put("nativeBuild", getNativeBuild());
        } catch (PackageManager.NameNotFoundException exception) {
            identity.put("nativeBuild", JSONObject.NULL);
        }
        return identity;
    }

    public void getInstallId(@NonNull NonEmptyCallback<GetInstallIdResult> callback) {
        String installId = preferences.getInstallId();
        callback.success(new GetInstallIdResult(installId, ReleaseSelector.bucketFor(installId)));
    }

    public void getNextBundle(@NonNull NonEmptyCallback<GetNextBundleResult> callback) {
        callback.success(new GetNextBundleResult(getNextBundleId()));
    }

    public void getVersionCode(@NonNull NonEmptyCallback<GetVersionCodeResult> callback) throws PackageManager.NameNotFoundException {
        callback.success(new GetVersionCodeResult(String.valueOf(getNativeBuild())));
    }

    public void getVersionName(@NonNull NonEmptyCallback<GetVersionNameResult> callback) throws PackageManager.NameNotFoundException {
        callback.success(new GetVersionNameResult(getPackageInfo().versionName));
    }

    public void isSyncing(@NonNull NonEmptyCallback<IsSyncingResult> callback) {
        callback.success(new IsSyncingResult(syncInProgress));
    }

    public void handleOnPageLoaded() {
        // Wait for initial page load to perform auto update to make sure the WebViewLocalServer is initialized.
        // Otherwise, a NullPointerException may occur for `com.getcapacitor.WebViewLocalServer.getBasePath()`.
        if ("background".equals(config.getAutoUpdateStrategy()) && !initialPageLoaded) {
            initialPageLoaded = true;
            performAutoUpdate();
        }
    }

    public void handleOnResume() {
        if ("background".equals(config.getAutoUpdateStrategy()) && initialPageLoaded) {
            performAutoUpdate();
        }
    }

    public void ready(@NonNull NonEmptyCallback<ReadyResult> callback) {
        Logger.debug(GraftPlugin.TAG, "App is ready.");
        if (config.getReadyTimeout() <= 0) {
            Logger.warn(GraftPlugin.TAG, "Ready timeout is set to 0. Automatic rollback is disabled.");
        }
        // Stop the rollback timer
        stopRollbackTimer();
        // Delete unused bundles
        if (config.getAutoDeleteBundles()) {
            deleteUnusedBundles();
        }
        // Get the current and previous bundle IDs
        String currentBundleId = getCurrentBundleId();
        String previousBundleId = preferences.getPreviousBundleId();
        // Block the rolled back bundle if enabled
        if (config.getAutoBlockRolledBackBundles() && rollbackPerformed && previousBundleId != null) {
            recordFailure(previousBundleId);
        }
        // Return the result
        callback.success(new ReadyResult(currentBundleId, previousBundleId, rollbackPerformed));
        // Set the new previous bundle ID
        preferences.setPreviousBundleId(currentBundleId);
        // A bundle that reaches this point booted, so it is the one to roll back to next time
        if (!rollbackPerformed) {
            preferences.setLastKnownGoodBundleId(currentBundleId);
            if (currentBundleId != null && currentBundleId.equals(preferences.getLastFailedBundleId())) {
                preferences.setLastFailed(null, 0);
            }
        }
        // Reset the rollback flag
        rollbackPerformed = false;
    }

    public void reload() {
        setCurrentBundleById(getNextBundleId());
        startRollbackTimer();
    }

    public void reset() {
        setNextBundleById(null);
    }

    public void setChannel(@NonNull SetChannelOptions options, @NonNull EmptyCallback callback) {
        preferences.setChannel(options.getChannel());
        callback.success();
    }

    public void setNextBundle(@NonNull SetNextBundleOptions options, @NonNull EmptyCallback callback) {
        String bundleId = options.getBundleId();

        if (bundleId == null) {
            reset();
        } else if (hasBundleById(bundleId)) {
            setNextBundleById(bundleId);
        } else {
            callback.error(new Exception(GraftPlugin.ERROR_BUNDLE_NOT_FOUND));
            return;
        }
        callback.success();
    }

    public void sync(@NonNull SyncOptions options, @NonNull NonEmptyCallback<Result> callback) {
        if (syncInProgress) {
            callback.error(new Exception(GraftPlugin.ERROR_SYNC_IN_PROGRESS));
            return;
        }
        syncInProgress = true;

        NonEmptyCallback<Result> completion = new NonEmptyCallback<>() {
            @Override
            public void success(@NonNull Result result) {
                syncInProgress = false;
                callback.success(result);
            }

            @Override
            public void error(@NonNull Exception exception) {
                syncInProgress = false;
                callback.error(exception);
            }
        };

        try {
            String channel = options.getChannel() == null ? getChannel() : options.getChannel();
            if (channel == null) {
                throw new Exception(GraftPlugin.ERROR_CHANNEL_MISSING);
            }
            PublicKey publicKey = loadPublicKey();
            HttpUrl channelUrl = buildChannelUrl(channel);
            Logger.debug(GraftPlugin.TAG, "Reading channel document: " + channelUrl);

            String fingerprint = nativeFingerprint();
            httpClient.enqueue(
                channelUrl.toString(),
                preferences.getChannelEtag(fingerprint),
                new NonEmptyCallback<Response>() {
                    @Override
                    public void success(@NonNull Response response) {
                        try {
                            if (response.code() == 304) {
                                Logger.debug(GraftPlugin.TAG, "Channel document is unchanged. No update available.");
                                completion.success(new SyncResult(null));
                                return;
                            }
                            String etag = response.header("ETag");
                            // Recorded only once the work this document implies has succeeded, so a
                            // failed install is retried on the next launch rather than skipped by a
                            // tag that outran it.
                            NonEmptyCallback<Result> completed = new NonEmptyCallback<Result>() {
                                @Override
                                public void success(@NonNull Result result) {
                                    preferences.setChannelEtag(etag, fingerprint);
                                    completion.success(result);
                                }

                                @Override
                                public void error(@NonNull Exception exception) {
                                    completion.error(exception);
                                }
                            };
                            String body = readBody(response);
                            ChannelDocument document = new ChannelDocument(new JSONObject(body));
                            if (document.isKillSwitchEnabled()) {
                                Logger.warn(GraftPlugin.TAG, "Kill switch is enabled. Reverting to the embedded bundle.");
                                setNextBundleById(null);
                                completed.success(new SyncResult(null));
                                return;
                            }
                            ChannelRelease release = ReleaseSelector.select(
                                document.getReleases(),
                                fingerprint,
                                preferences.getHighestInstalledCounter(),
                                ReleaseSelector.bucketFor(preferences.getInstallId()),
                                getBlockedBundleIds()
                            );
                            if (release == null) {
                                Logger.debug(GraftPlugin.TAG, "No update available.");
                                completed.success(new SyncResult(null));
                                return;
                            }
                            installRelease(channelUrl, release, channel, publicKey, completed);
                        } catch (Exception exception) {
                            completion.error(exception);
                        }
                    }

                    @Override
                    public void error(@NonNull Exception exception) {
                        completion.error(exception);
                    }
                }
            );
        } catch (Exception exception) {
            completion.error(exception);
        }
    }

    private void installRelease(
        @NonNull HttpUrl channelUrl,
        @NonNull ChannelRelease release,
        @NonNull String channel,
        @NonNull PublicKey publicKey,
        @NonNull NonEmptyCallback<Result> completion
    ) throws Exception {
        HttpUrl manifestUrl = resolveManifestUrl(channelUrl, release.getManifest());
        Logger.debug(GraftPlugin.TAG, "Reading manifest: " + manifestUrl);

        httpClient.enqueue(
            manifestUrl.toString(),
            new NonEmptyCallback<Response>() {
                @Override
                public void success(@NonNull Response response) {
                    try {
                        byte[] manifestBytes = readBodyAsBytes(response);
                        // The signature covers these exact bytes, so nothing is parsed before it is verified
                        verifySignature(manifestBytes, release.getSignature(), publicKey);
                        Manifest manifest = new Manifest(new JSONObject(new String(manifestBytes, StandardCharsets.UTF_8)));
                        verifyManifestIsAcceptable(manifest, release, channel);
                        // An interrupted install leaves a verified bundle on disk; reusing it skips
                        // the download only. The contract and the expiry describe this moment, not
                        // those files, and the binary can have been replaced since they landed.
                        if (hasBundleById(release.getId())) {
                            stageRelease(release.getId(), release.getCounter());
                            completion.success(new SyncResult(release.getId()));
                            return;
                        }
                        verifyNoFileCollidesWithManifest(manifestUrl, manifest);
                        installManifest(manifestUrl, manifest, manifestBytes, completion);
                    } catch (Exception exception) {
                        completion.error(exception);
                    }
                }

                @Override
                public void error(@NonNull Exception exception) {
                    completion.error(exception);
                }
            }
        );
    }

    private void installManifest(
        @NonNull HttpUrl manifestUrl,
        @NonNull Manifest manifest,
        @NonNull byte[] manifestBytes,
        @NonNull NonEmptyCallback<Result> completion
    ) throws Exception {
        File directory = new File(plugin.getContext().getCacheDir(), UUID.randomUUID().toString());
        if (!directory.mkdirs()) {
            throw new Exception(GraftPlugin.ERROR_INSTALL_FAILED);
        }

        String baseReleaseId = resolveBaseReleaseId();
        HttpUrl patchUrl = baseReleaseId == null ? null : buildPatchUrl(baseReleaseId, manifest.getId());
        if (patchUrl == null) {
            installByDownload(manifestUrl, manifest, manifestBytes, directory, completion);
            return;
        }

        File archive = new File(plugin.getContext().getCacheDir(), UUID.randomUUID().toString());
        downloadFile(
            patchUrl.toString(),
            archive,
            (downloadedBytes, totalBytes) -> notifyDownloadBundleProgressListeners(manifest.getId(), downloadedBytes, totalBytes),
            new EmptyCallback() {
                @Override
                public void success() {
                    try {
                        GraftPatch.apply(archive, buildBaseReader(), manifest, directory);
                        finishInstall(manifest, manifestBytes, directory, completion);
                    } catch (Exception exception) {
                        Logger.warn(GraftPlugin.TAG, "Patch could not be applied, downloading the full bundle: " + exception.getMessage());
                        fallBackToDownload(manifestUrl, manifest, manifestBytes, directory, completion);
                    } finally {
                        archive.delete();
                    }
                }

                @Override
                public void error(@NonNull Exception exception) {
                    archive.delete();
                    Logger.warn(GraftPlugin.TAG, "No usable patch, downloading the full bundle: " + exception.getMessage());
                    fallBackToDownload(manifestUrl, manifest, manifestBytes, directory, completion);
                }
            }
        );
    }

    /**
     * A patch is an optimisation, never a requirement: any failure — no patch published, a transfer
     * error, a patch that will not apply, a digest that does not match the signed manifest — discards
     * whatever it produced and installs the release the ordinary way.
     */
    private void fallBackToDownload(
        @NonNull HttpUrl manifestUrl,
        @NonNull Manifest manifest,
        @NonNull byte[] manifestBytes,
        @NonNull File directory,
        @NonNull NonEmptyCallback<Result> completion
    ) {
        try {
            deleteFileRecursively(directory);
            if (!directory.mkdirs()) {
                throw new Exception(GraftPlugin.ERROR_INSTALL_FAILED);
            }
            installByDownload(manifestUrl, manifest, manifestBytes, directory, completion);
        } catch (Exception exception) {
            deleteFileRecursively(directory);
            completion.error(exception);
        }
    }

    private void installByDownload(
        @NonNull HttpUrl manifestUrl,
        @NonNull Manifest manifest,
        @NonNull byte[] manifestBytes,
        @NonNull File directory,
        @NonNull NonEmptyCallback<Result> completion
    ) throws Exception {
        Map<String, String> currentHrefBySha256 = loadCurrentHrefBySha256();
        List<ManifestFile> filesToDownload = new ArrayList<>();
        for (ManifestFile file : manifest.getFiles()) {
            String currentHref = currentHrefBySha256.get(file.getSha256());
            if (currentHref == null || !copyCurrentBundleFile(currentHref, file, directory)) {
                filesToDownload.add(file);
            }
        }

        downloadBundleFiles(
            manifestUrl,
            filesToDownload,
            directory,
            (downloadedBytes, totalBytes) -> notifyDownloadBundleProgressListeners(manifest.getId(), downloadedBytes, totalBytes),
            new EmptyCallback() {
                @Override
                public void success() {
                    try {
                        finishInstall(manifest, manifestBytes, directory, completion);
                    } catch (Exception exception) {
                        deleteFileRecursively(directory);
                        completion.error(exception);
                    }
                }

                @Override
                public void error(@NonNull Exception exception) {
                    deleteFileRecursively(directory);
                    completion.error(exception);
                }
            }
        );
    }

    private void finishInstall(
        @NonNull Manifest manifest,
        @NonNull byte[] manifestBytes,
        @NonNull File directory,
        @NonNull NonEmptyCallback<Result> completion
    ) throws Exception {
        try {
            if (!new File(directory, GraftPointer.INDEX_FILE_NAME).exists()) {
                throw new Exception(GraftPlugin.ERROR_BUNDLE_INDEX_HTML_MISSING);
            }
            // Written last so the verified file set is exactly what the manifest describes
            writeFile(new File(directory, GraftPointer.MANIFEST_FILE_NAME), manifestBytes);
            moveBundleIntoPlace(directory, manifest.getId());
            stageRelease(manifest.getId(), manifest.getCounter());
            completion.success(new SyncResult(manifest.getId()));
        } catch (Exception exception) {
            deleteFileRecursively(directory);
            throw exception;
        }
    }

    /**
     * Which release the device can patch against — the staged bundle if there is one, otherwise the
     * bundle compiled into the binary.
     */
    @Nullable
    private String resolveBaseReleaseId() {
        String currentBundleId = getCurrentBundleId();
        if (currentBundleId != null) {
            return currentBundleId;
        }
        Manifest embedded = GraftPointer.readEmbeddedManifest(plugin.getContext());
        return embedded == null ? null : embedded.getId();
    }

    /**
     * A patch is addressed by path rather than by query, so every request this plugin makes can be
     * served by a static bucket with no compute in front of it. A server that wants to synthesise a
     * missing pair on demand can still intercept the path; one that does not simply answers 404 and
     * the device downloads the files it cannot reuse.
     */
    @Nullable
    private HttpUrl buildPatchUrl(@NonNull String from, @NonNull String to) {
        try {
            return parseServerUrl()
                .newBuilder()
                .addPathSegment("v1")
                .addPathSegment("patches")
                .addPathSegment(from + "__" + to + ".gpz")
                .build();
        } catch (Exception exception) {
            return null;
        }
    }

    @NonNull
    private GraftPatch.BaseReader buildBaseReader() {
        String currentBundleId = getCurrentBundleId();
        return href -> {
            if (currentBundleId == null) {
                try (
                    InputStream source = plugin
                        .getContext()
                        .getAssets()
                        .open(defaultWebAssetDir + "/" + href)
                ) {
                    return readAllBytes(source);
                }
            }
            try (FileInputStream source = new FileInputStream(new File(buildBundleDirectoryFor(currentBundleId), href))) {
                return readAllBytes(source);
            }
        };
    }

    @NonNull
    private static byte[] readAllBytes(@NonNull InputStream source) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = source.read(chunk)) != -1) {
            out.write(chunk, 0, read);
        }
        return out.toByteArray();
    }

    /**
     * Records the release as installed before it is staged, so a bundle that never boots still raises
     * the downgrade floor and the device can only ever move forward.
     */
    private void stageRelease(@NonNull String bundleId, long counter) {
        if (counter > preferences.getHighestInstalledCounter()) {
            preferences.setHighestInstalledCounter(counter);
        }
        if (!bundleId.equals(getCurrentBundleId())) {
            setNextBundleById(bundleId);
        }
    }

    /**
     * Release files are fetched as siblings of the manifest, so a file named like the manifest and the
     * manifest itself resolve to one URL and whichever is published last wins. Left undetected that
     * surfaces as a signature failure on a manifest the publisher signed correctly.
     */
    private void verifyNoFileCollidesWithManifest(@NonNull HttpUrl manifestUrl, @NonNull Manifest manifest) throws Exception {
        String manifestFileName = manifestUrl.pathSegments().get(manifestUrl.pathSize() - 1);
        for (ManifestFile file : manifest.getFiles()) {
            if (file.getHref().equals(manifestFileName)) {
                throw new Exception(GraftPlugin.ERROR_MANIFEST_NAME_COLLISION);
            }
        }
    }

    private void verifyManifestIsAcceptable(@NonNull Manifest manifest, @NonNull ChannelRelease release, @NonNull String channel)
        throws Exception {
        Long counter = manifest.getCounter();
        if (
            !manifest.getId().equals(release.getId()) ||
            counter == null ||
            counter != release.getCounter() ||
            !manifest.getNativeFingerprint().equals(release.getNativeFingerprint()) ||
            !channel.equals(manifest.getChannel())
        ) {
            throw new Exception(GraftPlugin.ERROR_MANIFEST_MISMATCH);
        }
        if (!manifest.getNativeFingerprint().equals(nativeFingerprint())) {
            throw new Exception(GraftPlugin.ERROR_MANIFEST_MISMATCH);
        }
        if (counter <= preferences.getHighestInstalledCounter()) {
            throw new Exception(GraftPlugin.ERROR_MANIFEST_MISMATCH);
        }
        Long notBefore = manifest.getNotBefore();
        Long expiresAt = manifest.getExpiresAt();
        if (notBefore == null || expiresAt == null) {
            throw new Exception(GraftPlugin.ERROR_MANIFEST_EXPIRED);
        }
        long now = System.currentTimeMillis() / 1000;
        if (now < notBefore || now >= expiresAt) {
            throw new Exception(GraftPlugin.ERROR_MANIFEST_EXPIRED);
        }
        verifyContractIsSatisfied(manifest);
    }

    /**
     * A bundle reaches native only through a registerPlugin proxy, so a plugin the binary does not
     * have is a bundle this build cannot run. Checked against the bridge rather than a recorded list,
     * because the binary is the only thing that knows what it actually shipped.
     */
    private void verifyContractIsSatisfied(@NonNull Manifest manifest) throws Exception {
        Bridge bridge = plugin.getBridge();
        if (bridge == null) {
            return;
        }
        for (String name : manifest.getRequires()) {
            if (bridge.getPlugin(name) == null) {
                Logger.error(GraftPlugin.TAG, "This build has no plugin named " + name, null);
                throw new Exception(GraftPlugin.ERROR_MANIFEST_CONTRACT_UNMET);
            }
        }
    }

    @NonNull
    private HttpUrl buildChannelUrl(@NonNull String channel) throws Exception {
        HttpUrl serverUrl = parseServerUrl();
        return serverUrl
            .newBuilder()
            .addPathSegment("v1")
            .addPathSegment("channel")
            .addPathSegment(channel + ".json")
            .build();
    }

    /**
     * Confines the manifest to the configured origin. The signature already decides what may be
     * installed; this keeps an edited channel document from pointing the device at another host.
     */
    @NonNull
    private HttpUrl resolveManifestUrl(@NonNull HttpUrl channelUrl, @NonNull String manifest) throws Exception {
        HttpUrl manifestUrl = channelUrl.resolve(manifest);
        HttpUrl serverUrl = parseServerUrl();
        if (
            manifestUrl == null ||
            !manifestUrl.scheme().equals(serverUrl.scheme()) ||
            !manifestUrl.host().equals(serverUrl.host()) ||
            manifestUrl.port() != serverUrl.port()
        ) {
            throw new Exception(GraftPlugin.ERROR_MANIFEST_URL_INVALID);
        }
        return manifestUrl;
    }

    @NonNull
    private HttpUrl parseServerUrl() throws Exception {
        String serverUrl = config.getServerUrl();
        if (serverUrl == null) {
            throw new Exception(GraftPlugin.ERROR_SERVER_URL_MISSING);
        }
        HttpUrl parsed = HttpUrl.parse(serverUrl);
        if (parsed == null) {
            throw new Exception(GraftPlugin.ERROR_SERVER_URL_INVALID);
        }
        return parsed;
    }

    @NonNull
    private HttpUrl buildFileUrl(@NonNull HttpUrl manifestUrl, @NonNull String href) {
        HttpUrl.Builder builder = manifestUrl.newBuilder().removePathSegment(manifestUrl.pathSize() - 1);
        for (String segment : href.split("/")) {
            builder.addPathSegment(segment);
        }
        return builder.build();
    }

    private void downloadBundleFiles(
        @NonNull HttpUrl manifestUrl,
        @NonNull List<ManifestFile> filesToDownload,
        @NonNull File directory,
        @NonNull DownloadProgressCallback progressCallback,
        @NonNull EmptyCallback completionCallback
    ) {
        if (filesToDownload.isEmpty()) {
            progressCallback.onProgress(0, 0);
            completionCallback.success();
            return;
        }

        long totalBytesToDownload = 0;
        for (ManifestFile file : filesToDownload) {
            totalBytesToDownload += file.getSize();
        }
        final long totalBytes = totalBytesToDownload;

        AtomicLong completedBytes = new AtomicLong(0);
        AtomicInteger remaining = new AtomicInteger(filesToDownload.size());
        AtomicReference<Exception> firstError = new AtomicReference<>();
        List<Call> calls = new ArrayList<>();

        for (ManifestFile file : filesToDownload) {
            File destination = new File(directory, file.getHref());
            destination.getParentFile().mkdirs();
            Call call = downloadFile(
                buildFileUrl(manifestUrl, file.getHref()).toString(),
                destination,
                (downloadedBytes, ignored) -> progressCallback.onProgress(completedBytes.get() + downloadedBytes, totalBytes),
                new EmptyCallback() {
                    @Override
                    public void success() {
                        try {
                            verifyChecksum(destination, file.getSha256());
                            progressCallback.onProgress(completedBytes.addAndGet(file.getSize()), totalBytes);
                            finish(null);
                        } catch (Exception exception) {
                            finish(exception);
                        }
                    }

                    @Override
                    public void error(@NonNull Exception exception) {
                        Logger.error(GraftPlugin.TAG, "Failed to download file: " + file.getHref(), exception);
                        finish(exception);
                    }

                    private void finish(@Nullable Exception exception) {
                        if (exception != null && firstError.compareAndSet(null, exception)) {
                            synchronized (calls) {
                                for (Call pending : calls) {
                                    pending.cancel();
                                }
                            }
                        }
                        if (remaining.decrementAndGet() > 0) {
                            return;
                        }
                        Exception error = firstError.get();
                        if (error == null) {
                            completionCallback.success();
                        } else {
                            completionCallback.error(error);
                        }
                    }
                }
            );
            synchronized (calls) {
                calls.add(call);
            }
        }
    }

    private Call downloadFile(
        @NonNull String url,
        @NonNull File file,
        @NonNull DownloadProgressCallback progressCallback,
        @NonNull EmptyCallback completionCallback
    ) {
        return httpClient.enqueue(
            url,
            new NonEmptyCallback<Response>() {
                @Override
                public void success(@NonNull Response response) {
                    try {
                        if (!response.isSuccessful()) {
                            Logger.error(GraftPlugin.TAG, "Request to " + url + " failed with status " + response.code() + ".", null);
                            response.close();
                            throw new Exception(GraftPlugin.ERROR_DOWNLOAD_FAILED);
                        }
                        GraftHttpClient.writeResponseBodyToFile(response.body(), file, progressCallback);
                        completionCallback.success();
                    } catch (Exception exception) {
                        completionCallback.error(exception);
                    }
                }

                @Override
                public void error(@NonNull Exception exception) {
                    completionCallback.error(exception);
                }
            }
        );
    }

    @NonNull
    private String readBody(@NonNull Response response) throws Exception {
        return new String(readBodyAsBytes(response), StandardCharsets.UTF_8);
    }

    @NonNull
    private byte[] readBodyAsBytes(@NonNull Response response) throws Exception {
        try (Response closeable = response) {
            if (!closeable.isSuccessful()) {
                Logger.error(GraftPlugin.TAG, "Request failed with status " + closeable.code() + ".", null);
                throw new Exception(GraftPlugin.ERROR_DOWNLOAD_FAILED);
            }
            return closeable.body().bytes();
        }
    }

    /**
     * @return Where each digest of the running bundle can be read from, so unchanged files are copied
     *         rather than downloaded.
     */
    @NonNull
    private Map<String, String> loadCurrentHrefBySha256() {
        String currentBundleId = getCurrentBundleId();
        Manifest manifest =
            currentBundleId == null
                ? GraftPointer.readEmbeddedManifest(plugin.getContext())
                : GraftPointer.readManifest(new File(buildBundleDirectoryFor(currentBundleId), GraftPointer.MANIFEST_FILE_NAME));
        return manifest == null ? new HashMap<>() : manifest.buildHrefBySha256();
    }

    private boolean copyCurrentBundleFile(@NonNull String currentHref, @NonNull ManifestFile file, @NonNull File directory) {
        String currentBundleId = getCurrentBundleId();
        File destination = new File(directory, file.getHref());
        try {
            destination.getParentFile().mkdirs();
            if (currentBundleId == null) {
                try (
                    InputStream source = plugin
                        .getContext()
                        .getAssets()
                        .open(defaultWebAssetDir + "/" + currentHref)
                ) {
                    writeFile(destination, source);
                }
            } else {
                try (FileInputStream source = new FileInputStream(new File(buildBundleDirectoryFor(currentBundleId), currentHref))) {
                    writeFile(destination, source);
                }
            }
            verifyChecksum(destination, file.getSha256());
            return true;
        } catch (Exception exception) {
            Logger.warn(GraftPlugin.TAG, "Failed to reuse file " + currentHref + ": " + exception.getMessage());
            destination.delete();
            return false;
        }
    }

    private void moveBundleIntoPlace(@NonNull File source, @NonNull String bundleId) throws Exception {
        File bundlesDirectory = GraftPointer.buildBundlesDirectory(plugin.getContext());
        if (!bundlesDirectory.exists() && !bundlesDirectory.mkdirs()) {
            throw new Exception(GraftPlugin.ERROR_INSTALL_FAILED);
        }
        if (!source.renameTo(buildBundleDirectoryFor(bundleId))) {
            throw new Exception(GraftPlugin.ERROR_INSTALL_FAILED);
        }
    }

    @NonNull
    private File buildBundleDirectoryFor(@NonNull String bundleId) {
        return GraftPointer.buildBundleDirectory(plugin.getContext(), bundleId);
    }

    private void writeFile(@NonNull File file, @NonNull byte[] content) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content);
        }
    }

    private void writeFile(@NonNull File file, @NonNull InputStream input) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
    }

    private void deleteBundleById(@NonNull String bundleId) {
        deleteFileRecursively(buildBundleDirectoryFor(bundleId));
        if (bundleId.equals(getNextBundleId())) {
            setNextBundleById(null);
        }
    }

    private void deleteFileRecursively(@NonNull File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteFileRecursively(child);
                }
            }
        }
        file.delete();
    }

    private void deleteUnusedBundles() {
        for (String bundleId : getDownloadedBundleIds()) {
            if (!isBundleInUse(bundleId)) {
                deleteBundleById(bundleId);
            }
        }
    }

    @NonNull
    private String[] getDownloadedBundleIds() {
        File[] bundles = GraftPointer.buildBundlesDirectory(plugin.getContext()).listFiles();
        if (bundles == null) {
            return new String[0];
        }
        String[] bundleIds = new String[bundles.length];
        for (int i = 0; i < bundles.length; i++) {
            bundleIds[i] = bundles[i].getName();
        }
        return bundleIds;
    }

    @Nullable
    private String getChannel() {
        String channel = config.getDefaultChannel();
        String nativeChannel = getNativeChannel();
        if (nativeChannel != null) {
            channel = nativeChannel;
        }
        if (preferences.getChannel() != null) {
            channel = preferences.getChannel();
        }
        return channel;
    }

    @Nullable
    private String getNativeChannel() {
        int resId = plugin
            .getContext()
            .getResources()
            .getIdentifier("graft_default_channel", "string", plugin.getContext().getPackageName());
        if (resId == 0) {
            return null;
        }
        return plugin.getContext().getResources().getString(resId);
    }

    /**
     * @return The current bundle ID or `null` if the bundle embedded in the binary is in use.
     */
    @Nullable
    private String getCurrentBundleId() {
        String currentPath = plugin.getBridge().getServerBasePath();
        if (currentPath.equals(defaultWebAssetDir)) {
            return null;
        }
        return new File(currentPath).getName();
    }

    /**
     * @return The next bundle ID or `null` if the bundle embedded in the binary will be used.
     */
    @Nullable
    private String getNextBundleId() {
        return GraftPointer.getActiveBundleId(plugin.getContext());
    }

    private long getNativeBuild() throws PackageManager.NameNotFoundException {
        return PackageInfoCompat.getLongVersionCode(getPackageInfo());
    }

    /**
     * The binary's own fingerprint, taken from the manifest that shipped inside it. A release naming
     * a different one was built against different native code, so this build cannot run it.
     */
    @NonNull
    private String nativeFingerprint() throws Exception {
        Manifest embeddedManifest = GraftPointer.readEmbeddedManifest(plugin.getContext());
        String fingerprint = embeddedManifest == null ? null : embeddedManifest.getNativeFingerprint();
        if (fingerprint == null) {
            throw new Exception(GraftPlugin.ERROR_NATIVE_FINGERPRINT_UNKNOWN);
        }
        return fingerprint;
    }

    private boolean hasBundleById(@NonNull String bundleId) {
        return buildBundleDirectoryFor(bundleId).exists();
    }

    private boolean isBundleInUse(@NonNull String bundleId) {
        return (
            bundleId.equals(getCurrentBundleId()) ||
            bundleId.equals(getNextBundleId()) ||
            bundleId.equals(preferences.getLastKnownGoodBundleId())
        );
    }

    private void notifyDownloadBundleProgressListeners(@NonNull String bundleId, long downloadedBytes, long totalBytes) {
        plugin.notifyDownloadBundleProgressListeners(new DownloadBundleProgressEvent(bundleId, downloadedBytes, totalBytes));
    }

    private void performAutoUpdate() {
        long now = System.currentTimeMillis();
        if (lastAutoUpdateCheckTimestamp > 0 && (now - lastAutoUpdateCheckTimestamp) < autoUpdateIntervalMs) {
            Logger.debug(GraftPlugin.TAG, "Auto-update skipped. Last check was less than 15 minutes ago.");
            return;
        }
        lastAutoUpdateCheckTimestamp = now;

        Logger.debug(GraftPlugin.TAG, "Auto-update started.");
        sync(
            new SyncOptions((String) null),
            new NonEmptyCallback<>() {
                @Override
                public void success(@NonNull Result result) {
                    Logger.debug(GraftPlugin.TAG, "Auto-update completed successfully.");
                }

                @Override
                public void error(@NonNull Exception exception) {
                    Logger.error(GraftPlugin.TAG, "Auto-update failed: " + exception.getMessage(), exception);
                }
            }
        );
    }

    private void rollback() {
        rollbackPerformed = true;
        String currentBundleId = getCurrentBundleId();
        preferences.setPreviousBundleId(currentBundleId);
        if (currentBundleId == null) {
            Logger.debug(GraftPlugin.TAG, "App is not ready. Embedded bundle is already in use.");
            return;
        }
        String targetBundleId = resolveRollbackTargetBundleId();
        Logger.debug(
            GraftPlugin.TAG,
            "App is not ready. Rolling back to " + (targetBundleId == null ? "the embedded bundle." : "bundle " + targetBundleId + ".")
        );
        setNextBundleById(targetBundleId);
        setCurrentBundleById(targetBundleId);
    }

    @Nullable
    private String resolveRollbackTargetBundleId() {
        String bundleId = preferences.getLastKnownGoodBundleId();
        if (bundleId == null || getBlockedBundleIds().contains(bundleId) || !hasBundleById(bundleId)) {
            return null;
        }
        return bundleId;
    }

    @Nullable
    private File searchIndexHtmlFile(@NonNull File directory) {
        File[] files = directory.listFiles();
        if (files == null) {
            return null;
        }
        for (File file : files) {
            if (file.getName().equals(GraftPointer.INDEX_FILE_NAME)) {
                return file;
            }
        }
        for (File file : files) {
            if (file.isDirectory()) {
                File indexHtmlFile = searchIndexHtmlFile(file);
                if (indexHtmlFile != null) {
                    return indexHtmlFile;
                }
            }
        }
        return null;
    }

    /**
     * @param bundleId The bundle ID to serve now. If `null`, the bundle embedded in the binary is served.
     */
    private void setCurrentBundleById(@Nullable String bundleId) {
        if (bundleId == null) {
            plugin.getBridge().setServerAssetPath(defaultWebAssetDir);
        } else {
            plugin.getBridge().setServerBasePath(buildBundleDirectoryFor(bundleId).getPath());
        }
        plugin.getBridge().reload();
        plugin.notifyReloadedListeners();
    }

    /**
     * @param bundleId The bundle ID to serve on the next launch. If `null`, the bundle embedded in the binary is served.
     */
    private void setNextBundleById(@Nullable String bundleId) {
        if (bundleId == null) {
            GraftPointer.clearActiveBundleId(plugin.getContext());
        } else {
            GraftPointer.setActiveBundleId(plugin.getContext(), bundleId);
        }
        plugin.notifyNextBundleSetListeners(new NextBundleSetEvent(bundleId));
    }

    @NonNull
    private Set<String> getBlockedBundleIds() {
        String blockedIds = preferences.getBlockedBundleIds();
        if (blockedIds == null || blockedIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(Arrays.asList(blockedIds.split(",")));
    }

    /**
     * Blocks a bundle only once it has failed to report ready twice running. One failure is as likely
     * to mean a slow cold start as a broken bundle, and blocking is permanent — a device that blocks a
     * good release never receives it again.
     */
    private void recordFailure(@NonNull String bundleId) {
        int failures = bundleId.equals(preferences.getLastFailedBundleId()) ? preferences.getLastFailedCount() + 1 : 1;
        if (failures >= 2) {
            preferences.setLastFailed(null, 0);
            addBlockedBundleId(bundleId);
            return;
        }
        preferences.setLastFailed(bundleId, failures);
        Logger.debug(GraftPlugin.TAG, "Bundle did not report ready: " + bundleId + ". It is blocked if it fails again.");
    }

    private void addBlockedBundleId(@NonNull String bundleId) {
        Set<String> blocked = getBlockedBundleIds();
        if (!blocked.add(bundleId)) {
            return;
        }
        List<String> blockedList = new ArrayList<>(blocked);
        while (blockedList.size() > 100) {
            blockedList.remove(0);
        }
        preferences.setBlockedBundleIds(String.join(",", blockedList));
        Logger.debug(GraftPlugin.TAG, "Bundle blocked: " + bundleId);
    }

    private void startRollbackTimer() {
        if (config.getReadyTimeout() <= 0) {
            return;
        }
        stopRollbackTimer();
        rollbackHandler.postDelayed(this::rollback, config.getReadyTimeout());
    }

    private void stopRollbackTimer() {
        rollbackHandler.removeCallbacksAndMessages(null);
    }

    private File unzipFile(@NonNull File zipFile) throws IOException {
        File destination = new File(plugin.getContext().getCacheDir(), UUID.randomUUID().toString());
        ZipFile zip = new ZipFile(zipFile);
        // Clear stored Unix permissions to prevent EACCES errors on newer Android versions
        // where restrictive directory permissions from the zip block file creation.
        for (FileHeader fileHeader : zip.getFileHeaders()) {
            fileHeader.setExternalFileAttributes(null);
        }
        zip.extractAll(destination.getPath());
        return destination;
    }

    private void verifyChecksum(@NonNull File file, @NonNull String expected) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (BufferedSource source = Okio.buffer(Okio.source(file))) {
            Buffer buffer = new Buffer();
            while (source.read(buffer, 8192) != -1) {
                digest.update(buffer.readByteArray());
            }
        }
        StringBuilder checksum = new StringBuilder();
        for (byte value : digest.digest()) {
            checksum.append(Integer.toString((value & 0xff) + 0x100, 16).substring(1));
        }
        if (!checksum.toString().equals(expected)) {
            throw new Exception(GraftPlugin.ERROR_CHECKSUM_MISMATCH);
        }
    }

    @NonNull
    private PublicKey loadPublicKey() throws Exception {
        String publicKey = config.getPublicKey();
        if (publicKey == null) {
            throw new Exception(GraftPlugin.ERROR_PUBLIC_KEY_MISSING);
        }
        try {
            String value = publicKey.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replace("\n", "");
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.decode(value, Base64.DEFAULT)));
        } catch (Exception exception) {
            Logger.error(GraftPlugin.TAG, exception.getMessage(), exception);
            throw new Exception(GraftPlugin.ERROR_PUBLIC_KEY_INVALID);
        }
    }

    private void verifySignature(@NonNull byte[] content, @NonNull String signature, @NonNull PublicKey publicKey) throws Exception {
        boolean verified;
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(content);
            verified = verifier.verify(Base64.decode(signature, Base64.DEFAULT));
        } catch (Exception exception) {
            Logger.error(GraftPlugin.TAG, exception.getMessage(), exception);
            throw new Exception(GraftPlugin.ERROR_SIGNATURE_VERIFICATION_FAILED);
        }
        if (!verified) {
            throw new Exception(GraftPlugin.ERROR_SIGNATURE_VERIFICATION_FAILED);
        }
    }

    private PackageInfo getPackageInfo() throws PackageManager.NameNotFoundException {
        String packageName = plugin.getContext().getPackageName();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return plugin.getContext().getPackageManager().getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0));
        }
        return plugin.getContext().getPackageManager().getPackageInfo(packageName, 0);
    }
}
