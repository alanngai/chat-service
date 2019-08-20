package com.box.prototype.chatservice;

import com.box.prototype.chatservice.akka.AkkaComponents;
import com.box.prototype.chatservice.rest.handler.ChatSessionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import org.springframework.web.reactive.socket.server.upgrade.ReactorNettyRequestUpgradeStrategy;

import java.util.HashMap;
import java.util.Map;

@Component
public class WebSocketConfig {
    public static final String ROOM_ID_PARAM_KEY = "roomid";
    public static final String USER_ID_PARAM_KEY = "userid";
    public static final String LAST_EVENT_ID_PARAM_KEY = "lasteventid";
    public static final String REJOIN_PARAM_KEY = "rejoin";
    public static final String CHAT_SESSION_ROUTE = String.format("/chatapp/chatrooms/{%s}/chatsessions/{%s}", ROOM_ID_PARAM_KEY, USER_ID_PARAM_KEY);

    @Autowired
    private AkkaComponents akkaComponents;

    @Bean
    public HandlerMapping handlerMapping() {
        Map<String, WebSocketHandler> map = new HashMap<>();
        map.put(CHAT_SESSION_ROUTE, new ChatSessionHandler(this.akkaComponents));

        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(map);
        mapping.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter(webSocketService());
    }

    @Bean
    public WebSocketService webSocketService() {
        return new HandshakeWebSocketService(new ReactorNettyRequestUpgradeStrategy());
    }
}
