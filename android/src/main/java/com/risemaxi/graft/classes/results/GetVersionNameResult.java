package com.risemaxi.graft.classes.results;

import androidx.annotation.NonNull;
import com.getcapacitor.JSObject;
import com.risemaxi.graft.interfaces.Result;

public class GetVersionNameResult implements Result {

    @NonNull
    private String versionName;

    public GetVersionNameResult(@NonNull String versionName) {
        this.versionName = versionName;
    }

    public JSObject toJSObject() {
        JSObject result = new JSObject();
        result.put("versionName", versionName);
        return result;
    }
}
