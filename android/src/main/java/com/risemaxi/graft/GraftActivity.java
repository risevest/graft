package com.risemaxi.graft;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.ServerPath;
import java.io.File;

public class GraftActivity extends BridgeActivity {

    @Override
    protected void load() {
        // Bridge.Builder is the only pre-WebView hook; CAP_SERVER_PATH costs a second load
        File directory = GraftPointer.resolveActiveBundleDirectory(this);
        if (directory != null) {
            bridgeBuilder.setServerPath(new ServerPath(ServerPath.PathType.BASE_PATH, directory.getAbsolutePath()));
        }
        super.load();
    }
}
