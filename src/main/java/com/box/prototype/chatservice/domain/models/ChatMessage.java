package com.box.prototype.chatservice.domain.models;

import java.io.Serializable;

public class ChatMessage implements Serializable {
    public final long timestamp;
    public final String userId;
    public final String message;

    public ChatMessage(long timestamp, String userId, String message) {
        this.timestamp = timestamp;
        this.userId = userId;
        this.message = message;
    }
}
