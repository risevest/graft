package com.risemaxi.graft.classes.results;

import androidx.annotation.NonNull;
import com.getcapacitor.JSObject;
import com.risemaxi.graft.interfaces.Result;

public class GetInstallIdResult implements Result {

    private final int bucket;

    @NonNull
    private final String installId;

    public GetInstallIdResult(@NonNull String installId, int bucket) {
        this.installId = installId;
        this.bucket = bucket;
    }

    public JSObject toJSObject() {
        JSObject result = new JSObject();
        result.put("bucket", bucket);
        result.put("installId", installId);
        return result;
    }
}
