package com.box.prototype.chatservice.domain.models;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessage implements Serializable {
    private long timestamp;
    private String userId;
    private String message;

    public ChatMessage() {}
    public ChatMessage(long timestamp, String userId, String message) {
        this.timestamp = timestamp;
        this.userId = userId;
        this.message = message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
            "timestamp=" + timestamp +
            ", userId='" + userId + '\'' +
            ", message='" + message + '\'' +
            '}';
    }
}
