package com.box.prototype.chatservice.rest.handler;

import com.box.prototype.chatservice.persistence.model.ChatRoom;
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
    public Mono<ServerResponse> listChatRooms(ServerRequest request) {
        ArrayList<ChatRoom> chatRooms = new ArrayList<>(Arrays.asList(new ChatRoom("room1")));
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(Mono.fromFuture(CompletableFuture.completedFuture(chatRooms)), ArrayList.class);
    }
}
