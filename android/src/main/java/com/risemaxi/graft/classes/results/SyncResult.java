package com.risemaxi.graft.classes.results;

import androidx.annotation.Nullable;
import com.getcapacitor.JSObject;
import com.risemaxi.graft.interfaces.Result;
import org.json.JSONObject;

public class SyncResult implements Result {

    @Nullable
    private final String nextBundleId;

    public SyncResult(@Nullable String nextBundleId) {
        this.nextBundleId = nextBundleId;
    }

    public JSObject toJSObject() {
        JSObject result = new JSObject();
        result.put("nextBundleId", nextBundleId == null ? JSONObject.NULL : nextBundleId);
        return result;
    }
}
