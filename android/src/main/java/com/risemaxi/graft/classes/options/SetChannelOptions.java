package com.risemaxi.graft.classes.options;

import androidx.annotation.Nullable;

public class SetChannelOptions {

    @Nullable
    private final String channel;

    public SetChannelOptions(@Nullable String channel) {
        this.channel = channel;
    }

    @Nullable
    public String getChannel() {
        return channel;
    }
}
