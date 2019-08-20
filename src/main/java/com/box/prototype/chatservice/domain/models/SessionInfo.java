package com.box.prototype.chatservice.domain.models;

import com.box.prototype.chatservice.util.URIParser;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.io.Serializable;

import static com.box.prototype.chatservice.WebSocketConfig.*;

public class SessionInfo implements Serializable {

    protected String sessionId = "";
    protected String userId = "";
    protected String chatRoom = "";
    protected String lastEventId = "";
    protected boolean isRejoin = false;

    public SessionInfo() {}
    public SessionInfo(WebSocketSession session) {
        URIParser uriParser = new URIParser(session.getHandshakeInfo().getUri(), CHAT_SESSION_ROUTE);
        this.sessionId = session.getId();
        this.userId = uriParser.getFirstQueryParamValue(USER_ID_PARAM_KEY);
        this.chatRoom = uriParser.getFirstQueryParamValue(ROOM_ID_PARAM_KEY);
        this.lastEventId = uriParser.getFirstQueryParamValue(LAST_EVENT_ID_PARAM_KEY);
        this.isRejoin = uriParser.queryParamExists(REJOIN_PARAM_KEY);
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getChatRoom() {
        return chatRoom;
    }

    public String getLastEventId() {
        return lastEventId;
    }

    public boolean isRejoin() {
        return isRejoin;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("SessionInfo{");
        sb.append("sessionId='").append(sessionId).append('\'');
        sb.append(", userId='").append(userId).append('\'');
        sb.append(", chatRoom='").append(chatRoom).append('\'');
        sb.append(", lastEventId='").append(lastEventId).append('\'');
        sb.append(", isRejoin=").append(isRejoin);
        sb.append('}');
        return sb.toString();
    }
}
