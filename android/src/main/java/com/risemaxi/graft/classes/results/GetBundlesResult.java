package com.risemaxi.graft.classes.results;

import androidx.annotation.NonNull;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.risemaxi.graft.interfaces.Result;

public class GetBundlesResult implements Result {

    @NonNull
    private String[] bundleIds;

    public GetBundlesResult(@NonNull String[] bundleIds) {
        this.bundleIds = bundleIds;
    }

    public JSObject toJSObject() {
        JSArray bundleIdsResult = new JSArray();
        for (String bundleId : bundleIds) {
            bundleIdsResult.put(bundleId);
        }

        JSObject result = new JSObject();
        result.put("bundleIds", bundleIdsResult);
        return result;
    }
}
