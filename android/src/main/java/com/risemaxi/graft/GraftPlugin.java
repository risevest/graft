package com.risemaxi.graft;

import android.webkit.WebView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.WebViewListener;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.risemaxi.graft.classes.events.DownloadBundleProgressEvent;
import com.risemaxi.graft.classes.events.NextBundleSetEvent;
import com.risemaxi.graft.classes.options.DeleteBundleOptions;
import com.risemaxi.graft.classes.options.DownloadBundleOptions;
import com.risemaxi.graft.classes.options.SetChannelOptions;
import com.risemaxi.graft.classes.options.SetNextBundleOptions;
import com.risemaxi.graft.classes.options.SyncOptions;
import com.risemaxi.graft.interfaces.EmptyCallback;
import com.risemaxi.graft.interfaces.NonEmptyCallback;
import com.risemaxi.graft.interfaces.Result;
import java.net.SocketTimeoutException;
import java.util.Collections;

@CapacitorPlugin(name = "Graft")
public class GraftPlugin extends Plugin {

    public static final String TAG = "Graft";
    public static final String SHARED_PREFERENCES_NAME = "RisemaxiGraft"; // DO NOT CHANGE
    public static final String RELEASE_IDENTITY_KEY = "__graft__"; // must match src/identity.ts

    public static final String ERROR_BUNDLE_EXISTS = "bundle already exists.";
    public static final String ERROR_BUNDLE_ID_MISSING = "bundleId must be provided.";
    public static final String ERROR_BUNDLE_INDEX_HTML_MISSING = "The bundle does not contain an index.html file.";
    public static final String ERROR_BUNDLE_NOT_FOUND = "bundle not found.";
    public static final String ERROR_CHANNEL_MISSING = "channel must be configured.";
    public static final String ERROR_CHECKSUM_MISSING = "checksum must be provided.";
    public static final String ERROR_CHECKSUM_MISMATCH = "Checksum mismatch.";
    public static final String ERROR_DOWNLOAD_FAILED = "Bundle could not be downloaded.";
    public static final String ERROR_HTTP_TIMEOUT = "Request timed out.";
    public static final String ERROR_INSTALL_FAILED = "Bundle could not be installed.";
    public static final String ERROR_MANIFEST_EXPIRED = "The manifest is not valid at the current time.";
    public static final String ERROR_MANIFEST_MISMATCH = "The manifest does not describe an acceptable release.";
    public static final String ERROR_MANIFEST_NAME_COLLISION =
        "A file in the release has the same name as the manifest, so one overwrites the other. Serve the manifest as graft-manifest.json.";
    public static final String ERROR_MANIFEST_URL_INVALID = "The manifest URL is not on the configured server.";
    public static final String ERROR_NOT_INITIALIZED = "Graft failed to initialize.";
    public static final String ERROR_PATCH_CHECKSUM_MISMATCH = "A patched file did not match the manifest.";
    public static final String ERROR_PATCH_FAILED = "The patch could not be applied.";
    public static final String ERROR_PUBLIC_KEY_INVALID = "Invalid public key.";
    public static final String ERROR_PUBLIC_KEY_MISSING = "publicKey must be configured.";
    public static final String ERROR_SERVER_URL_INVALID = "Invalid serverUrl.";
    public static final String ERROR_SERVER_URL_MISSING = "serverUrl must be configured.";
    public static final String ERROR_SIGNATURE_VERIFICATION_FAILED = "Signature verification failed.";
    public static final String ERROR_SYNC_IN_PROGRESS = "Sync is already in progress.";
    public static final String ERROR_UNKNOWN_ERROR = "An unknown error has occurred.";
    public static final String ERROR_URL_MISSING = "url must be provided.";

    public static final String EVENT_DOWNLOAD_BUNDLE_PROGRESS = "downloadBundleProgress";
    public static final String EVENT_NEXT_BUNDLE_SET = "nextBundleSet";
    public static final String EVENT_RELOADED = "reloaded";

    @Nullable
    private Graft implementation;

    private boolean webViewListenerRegistered = false;

    public void load() {
        try {
            implementation = new Graft(getGraftConfig(), this);
            publishReleaseIdentity(implementation);
        } catch (Exception exception) {
            Logger.error(TAG, exception.getMessage(), exception);
        }
    }

    /**
     * Publishes the running bundle's identity to the page before any of its code runs, so an app can
     * read it synchronously instead of compiling a release identifier into its own bundle.
     */
    private void publishReleaseIdentity(@NonNull Graft graft) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            Logger.warn(TAG, "This WebView cannot run a document-start script, so releaseIdentity() will return null.");
            return;
        }
        try {
            String script = "globalThis." + RELEASE_IDENTITY_KEY + " = " + graft.getReleaseIdentity() + ";";
            WebViewCompat.addDocumentStartJavaScript(getBridge().getWebView(), script, Collections.singleton(getBridge().getLocalUrl()));
        } catch (Exception exception) {
            Logger.error(TAG, "Failed to publish the release identity to the page.", exception);
        }
    }

    @Override
    protected void handleOnResume() {
        super.handleOnResume();
        try {
            if (implementation != null) {
                implementation.handleOnResume();
            }
            // Important: For some reason, the listener CANNOT be registered in the load() method
            // or constructor, it MUST be done here in onResume().
            if (!webViewListenerRegistered) {
                webViewListenerRegistered = true;
                getBridge().addWebViewListener(
                    new WebViewListener() {
                        @Override
                        public void onPageLoaded(WebView webView) {
                            if (implementation != null) {
                                implementation.handleOnPageLoaded();
                            }
                        }
                    }
                );
            }
        } catch (Exception exception) {
            Logger.error(TAG, exception.getMessage(), exception);
        }
    }

    @PluginMethod
    public void clearBlockedBundles(PluginCall call) {
        Graft graft = requireImplementation(call);
        if (graft == null) {
            return;
        }
        try {
            graft.clearBlockedBundles();
            call.resolve();
        } catch (Exception exception) {
            rejectCall(call, exception);
        }
    }

    @PluginMethod
    public void deleteBundle(PluginCall call) {
        Graft graft = requireImplementation(call);
        if (graft == null) {
            return;
        }
        try {
            String bundleId = call.getString("bundleId");
            if (bundleId == null) {
                call.reject(ERROR_BUNDLE_ID_MISSING);
                return;
            }
            graft.deleteBundle(new DeleteBundleOptions(bundleId), emptyCallback(call));
        } catch (Exception exception) {
            rejectCall(call, exception);
        }
    }

    @PluginMethod
    public void downloadBundle(PluginCall call) {
        Graft graft = requireImplementation(call);
        if (graft == null) {
            return;
        }
        try {
            String bundleId = call.getString("bundleId");
            if (bundleId == null) {
                call.reject(ERROR_BUNDLE_ID_MISSING);
                return;
            }
            String checksum = call.getString("checksum");
            if (checksum == null) {
                call.reject(ERROR_CHECKSUM_MISSING);
                return;
            }
            String url = call.getString("url");
            if (url == null) {
                call.reject(ERROR_URL_MISSING);
                return;
            }
            graft.downloadBundle(new DownloadBundleOptions(bundleId, checksum, url), emptyCallback(call));
        } catch (Exception exception) {
            rejectCall(call, exception);
        }
    }

    @PluginMethod
    public void getBlockedBundles(PluginCall call) {
        Graft graft = requireImplementation(call);
        if (graft == null) {
            return;
        }
        try {
            graft.getBlockedBundles(resultCallback(call));
        } catch (Exception exception) {
            rejectCall(call, exception);
        }
    }

    @PluginMethod
    public void getChannel(PluginCall call) {
        Graft graft = requireImplementation(call);
        if (graft == null) {
            return;
        }
        try {
            graft.getChannel(resultCallback(call));
        } catch (Exception exception) {
            rejectCall(call, exception);
        }
    }

    @PluginMethod
    public void getCurrentBundle(PluginCall call) {
        Graft graft = requireImplementation(call);
        if (graft == null) {
            return;
        }
        try {
            graft.getCurrentBundle(resultCallback(call));
        } catch (Exception exception) {
            rejectCall(call, exception);
        }
    }

    @PluginMethod
    public void getDownloadedBundles(PluginCall call) {
        Graft graft = requireImplementation(call);
        if (graft == null) {
            return;
        }
        try {
            graft.getDownloadedBundles(resultCallback(call));
        } catch (Exception exception) {
            rejectCall(call, exception);
        }
    }

    @PluginMethod
    public void getInstallId(PluginCall call) {
        Graft graft = requireImplementation(call);
        if (graft == null) {
            return;
        }
        try {
            graft.getInstallId(resultCallback(call));
        } catch (Exception exception) {
            rejectCall(call, exception);
        }
    }

    @PluginMethod
    public void getNextBundle(PluginCall call) {
        Graft graft = requireImplementation(call);
        if (graft == null) {
            return;
        }
        try {
            graft.getNextBundle(resultCallback(call));
        } catch (Exception exception) {
            rejectCall(call, exception);
        }
    }

    @PluginMethod
    public void getVersionCode(PluginCall call) {
        Graft graft = requireImplementation(call);
        if (graft == null) {
            return;
        }
        try {
            graft.getVersionCode(resultCallback(call));
        } catch (Exception exception) {
            rejectCall(call, exception);
        }
    }

    @PluginMethod
    public void getVersionName(PluginCall call) {
        Graft graft = requireImplementation(call);
        if (graft == null) {
            return;
        }
        try {
            graft.getVersionName(resultCallback(call));
        } catch (Exception exception) {
            rejectCall(call, exception);
        }
    }

    @PluginMethod
    public void isSyncing(PluginCall call) {
        Graft graft = requireImplementation(call);
        if (graft == null) {
            return;
        }
        try {
            graft.isSyncing(resultCallback(call));
        } catch (Exception exception) {
            rejectCall(call, exception);
        }
    }

    @PluginMethod
    public void ready(PluginCall call) {
        Graft graft = requireImplementation(call);
        if (graft == null) {
            return;
        }
        try {
            graft.ready(resultCallback(call));
        } catch (Exception exception) {
            rejectCall(call, exception);
        }
    }

    @PluginMethod
    public void reload(PluginCall call) {
        Graft graft = requireImplementation(call);
        if (graft == null) {
            return;
        }
        try {
            graft.reload();
            call.resolve();
        } catch (Exception exception) {
            rejectCall(call, exception);
        }
    }

    @PluginMethod
    public void reset(PluginCall call) {
        Graft graft = requireImplementation(call);
        if (graft == null) {
            return;
        }
        try {
            graft.reset();
            call.resolve();
        } catch (Exception exception) {
            rejectCall(call, exception);
        }
    }

    @PluginMethod
    public void setChannel(PluginCall call) {
        Graft graft = requireImplementation(call);
        if (graft == null) {
            return;
        }
        try {
            graft.setChannel(new SetChannelOptions(call.getString("channel")), emptyCallback(call));
        } catch (Exception exception) {
            rejectCall(call, exception);
        }
    }

    @PluginMethod
    public void setNextBundle(PluginCall call) {
        Graft graft = requireImplementation(call);
        if (graft == null) {
            return;
        }
        try {
            graft.setNextBundle(new SetNextBundleOptions(call), emptyCallback(call));
        } catch (Exception exception) {
            rejectCall(call, exception);
        }
    }

    @PluginMethod
    public void sync(PluginCall call) {
        Graft graft = requireImplementation(call);
        if (graft == null) {
            return;
        }
        try {
            graft.sync(new SyncOptions(call), resultCallback(call));
        } catch (Exception exception) {
            rejectCall(call, exception);
        }
    }

    public void notifyDownloadBundleProgressListeners(@NonNull DownloadBundleProgressEvent event) {
        notifyListeners(EVENT_DOWNLOAD_BUNDLE_PROGRESS, event.toJSObject(), false);
    }

    public void notifyNextBundleSetListeners(@NonNull NextBundleSetEvent event) {
        notifyListeners(EVENT_NEXT_BUNDLE_SET, event.toJSObject(), false);
    }

    public void notifyReloadedListeners() {
        notifyListeners(EVENT_RELOADED, new JSObject(), true);
    }

    @Nullable
    private Graft requireImplementation(@NonNull PluginCall call) {
        if (implementation == null) {
            call.reject(ERROR_NOT_INITIALIZED);
        }
        return implementation;
    }

    @NonNull
    private EmptyCallback emptyCallback(@NonNull PluginCall call) {
        return new EmptyCallback() {
            @Override
            public void success() {
                call.resolve();
            }

            @Override
            public void error(Exception exception) {
                rejectCall(call, exception);
            }
        };
    }

    @NonNull
    private <T extends Result> NonEmptyCallback<T> resultCallback(@NonNull PluginCall call) {
        return new NonEmptyCallback<>() {
            @Override
            public void success(@NonNull T result) {
                call.resolve(result.toJSObject());
            }

            @Override
            public void error(Exception exception) {
                rejectCall(call, exception);
            }
        };
    }

    @NonNull
    private GraftConfig getGraftConfig() {
        GraftConfig config = new GraftConfig();

        config.setAutoBlockRolledBackBundles(getConfig().getBoolean("autoBlockRolledBackBundles", config.getAutoBlockRolledBackBundles()));
        config.setAutoDeleteBundles(getConfig().getBoolean("autoDeleteBundles", config.getAutoDeleteBundles()));
        config.setAutoUpdateStrategy(getConfig().getString("autoUpdateStrategy", config.getAutoUpdateStrategy()));
        config.setDefaultChannel(getConfig().getString("defaultChannel", config.getDefaultChannel()));
        config.setHttpTimeout(getConfig().getInt("httpTimeout", config.getHttpTimeout()));
        config.setPublicKey(getConfig().getString("publicKey", config.getPublicKey()));
        config.setReadyTimeout(getConfig().getInt("readyTimeout", config.getReadyTimeout()));
        config.setServerUrl(getConfig().getString("serverUrl", config.getServerUrl()));

        return config;
    }

    private void rejectCall(@NonNull PluginCall call, @NonNull Exception exception) {
        String message = exception.getMessage();
        if (exception instanceof SocketTimeoutException) {
            message = ERROR_HTTP_TIMEOUT;
        } else if (message == null) {
            message = ERROR_UNKNOWN_ERROR;
        }
        Logger.error(TAG, message, exception);
        call.reject(message);
    }
}
