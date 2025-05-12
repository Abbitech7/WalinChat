package com.chat.client.models;

public class DisplayMessage {
    private final String content;
    private final boolean isSelf;

    public DisplayMessage(String content, boolean isSelf) {
        this.content = content;
        this.isSelf = isSelf;
    }

    public String getContent() {
        return content;
    }

    public boolean isSelf() {
        return isSelf;
    }
}
