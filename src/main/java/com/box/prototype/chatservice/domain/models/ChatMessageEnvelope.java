package com.box.prototype.chatservice.domain.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessageEnvelope {
    private ChatMessage message;
    private String lastEventId;

    public ChatMessageEnvelope() {}
    public ChatMessageEnvelope(ChatMessage message, String lastEventId) {
        this.message = message;
        this.lastEventId = lastEventId;
    }

    public ChatMessage getMessage() {
        return message;
    }

    public void setMessage(ChatMessage message) {
        this.message = message;
    }

    public String getLastEventId() {
        return lastEventId;
    }

    public void setLastEventId(String lastEventId) {
        this.lastEventId = lastEventId;
    }
}
