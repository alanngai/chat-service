package com.box.prototype.chatservice.rest.handler;

import com.box.prototype.chatservice.akka.AkkaComponents;
import com.box.prototype.chatservice.domain.models.ChatRoom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

@Component
public class ChatRoomHandler {
    @Autowired
    private AkkaComponents akkaComponents;

    public Mono<ServerResponse> listChatRooms(ServerRequest request) {
        // TODO: real implementation
        ArrayList<ChatRoom> chatRooms = new ArrayList<>(Arrays.asList(new ChatRoom("room1")));
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(Mono.fromFuture(CompletableFuture.completedFuture(chatRooms)), ArrayList.class);
    }
}
