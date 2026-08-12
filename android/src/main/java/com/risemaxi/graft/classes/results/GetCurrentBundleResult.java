package com.risemaxi.graft.classes.results;

import androidx.annotation.Nullable;
import com.getcapacitor.JSObject;
import com.risemaxi.graft.interfaces.Result;
import org.json.JSONObject;

public class GetCurrentBundleResult implements Result {

    @Nullable
    private String bundleId;

    public GetCurrentBundleResult(@Nullable String bundleId) {
        this.bundleId = bundleId;
    }

    public JSObject toJSObject() {
        JSObject result = new JSObject();
        result.put("bundleId", bundleId == null ? JSONObject.NULL : bundleId);
        return result;
    }
}
