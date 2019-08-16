package com.box.prototype.chatservice.rest;

import com.box.prototype.chatservice.rest.handler.ChatRoomHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class RestRouter {
    @Bean
    public RouterFunction<ServerResponse> route(ChatRoomHandler handler) {
        return RouterFunctions
            .route(RequestPredicates.GET("/api/chatrooms")
                .and(RequestPredicates.accept(MediaType.APPLICATION_JSON)), handler::listChatRooms);
    }
}
