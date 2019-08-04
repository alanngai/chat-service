package com.box.prototype.chatservice.rest.handler;

import com.box.prototype.chatservice.akka.AkkaComponents;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class ChatSessionHandler {
    @Autowired
    private AkkaComponents akkaComponents;

    public Mono<ServerResponse> startChatSession(ServerRequest request) {
        return ServerResponse.ok().build();
    }
}
