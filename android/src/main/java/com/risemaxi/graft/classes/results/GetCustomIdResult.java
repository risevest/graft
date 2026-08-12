package com.risemaxi.graft.classes.results;

import androidx.annotation.Nullable;
import com.getcapacitor.JSObject;
import com.risemaxi.graft.interfaces.Result;
import org.json.JSONObject;

public class GetCustomIdResult implements Result {

    @Nullable
    private String customId;

    public GetCustomIdResult(@Nullable String customId) {
        this.customId = customId;
    }

    public JSObject toJSObject() {
        JSObject result = new JSObject();
        result.put("customId", customId == null ? JSONObject.NULL : customId);
        return result;
    }
}
