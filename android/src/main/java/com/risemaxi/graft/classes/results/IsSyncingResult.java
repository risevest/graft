package com.risemaxi.graft.classes.results;

import androidx.annotation.NonNull;
import com.getcapacitor.JSObject;
import com.risemaxi.graft.interfaces.Result;

public class IsSyncingResult implements Result {

    private final boolean syncing;

    public IsSyncingResult(boolean syncing) {
        this.syncing = syncing;
    }

    public boolean getSyncing() {
        return syncing;
    }

    @Override
    @NonNull
    public JSObject toJSObject() {
        JSObject result = new JSObject();
        result.put("syncing", syncing);
        return result;
    }
}
