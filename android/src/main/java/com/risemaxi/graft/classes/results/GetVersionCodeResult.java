package com.risemaxi.graft.classes.results;

import androidx.annotation.Nullable;
import com.getcapacitor.JSObject;
import com.risemaxi.graft.interfaces.Result;
import org.json.JSONObject;

public class GetVersionCodeResult implements Result {

    private String versionCode;

    public GetVersionCodeResult(String versionCode) {
        this.versionCode = versionCode;
    }

    public JSObject toJSObject() {
        JSObject result = new JSObject();
        result.put("versionCode", versionCode);
        return result;
    }
}
