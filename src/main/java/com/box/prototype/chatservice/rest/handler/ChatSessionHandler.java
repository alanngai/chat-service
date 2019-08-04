package com.box.prototype.chatservice.rest.handler;

import com.box.prototype.chatservice.akka.AkkaComponents;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

//@Component
public class ChatSessionHandler implements WebSocketHandler {
//    @Autowired
//    private AkkaComponents akkaComponents;

    public Mono<ServerResponse> startChatSession(ServerRequest request) {
        return ServerResponse.ok().build();
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        return session.send(session.receive()
            .map(msg -> "RECEIVED ON SERVER :: " + msg.getPayloadAsText())
            .map(session::textMessage)
        );
    }
}
