package com.chat.client.models;

import javafx.scene.Node;

public class DisplayMessage {
    private final Node content;
    private final boolean isSelf;

    public DisplayMessage(Node content, boolean isSelf) {
        this.content = content;
        this.isSelf = isSelf;
    }

    public Node getContent() {
        return content;
    }

    public boolean isSelf() {
        return isSelf;
    }
}
