package com.box.prototype.chatservice.rest.handler;

import akka.actor.ActorRef;
import akka.japi.Pair;
import akka.stream.*;
import akka.stream.javadsl.*;
import com.box.prototype.chatservice.akka.AkkaComponents;
import com.box.prototype.chatservice.domain.entities.ChatRoomEntityProtocol;
import com.box.prototype.chatservice.domain.models.ChatMessage;
import com.box.prototype.chatservice.domain.models.ChatMessageEnvelope;
import com.box.prototype.chatservice.domain.models.SessionInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static akka.pattern.Patterns.*;

@EnableWebFlux
public class ChatSessionHandler implements WebSocketHandler {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Duration REQUEST_TIMEOUT;

    private AkkaComponents akkaComponents;
    private final ObjectMapper mapper = new ObjectMapper();

    public ChatSessionHandler(AkkaComponents components) {
        this.akkaComponents = components;
        REQUEST_TIMEOUT = components.getConfig().getDuration("server.request-timeout");
    }

    /** web socket handler */
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        SessionInfo sessionInfo = new SessionInfo(session);
        logger.info("establishing new chat session: " + sessionInfo);
        if (sessionInfo.getChatRoom() == null || sessionInfo.getUserId() == null) {
            logger.error("terminating due missing chatroom ({}) or userid ({})", sessionInfo.getChatRoom(), sessionInfo.getUserId());
            session.close();
            return session.send(Flux.empty());
        }

        // construct a sinkref to pass to chatroom
        Pair<CompletionStage<SinkRef<ChatMessageEnvelope>>, Publisher<WebSocketMessage>> pair = StreamRefs.<ChatMessageEnvelope>sinkRef()
            .map(msg -> session.textMessage(this.mapper.writeValueAsString(msg)))
            .toMat(Sink.asPublisher(AsPublisher.WITHOUT_FANOUT), Keep.both())
            .run(this.akkaComponents.getMaterializer());
        final Publisher<WebSocketMessage> outFlux = pair.second();

        pair.first()
            // join chat room
            .thenCompose(sinkRef -> joinChat(sessionInfo, sinkRef))
            // handle incoming messages
            .thenAccept(unused -> handleMessageReceive(session, sessionInfo))
            // handle errors
            .exceptionally(error -> {
                logger.error(String.format("terminating websocket due to error: [%s], [%s])", error, sessionInfo.getSessionId()), error);
                terminateSession(session, sessionInfo, this.akkaComponents.getChatRoomRegion());
                return null;
            });

        return session.send(outFlux);
    }

    /** terminate session helper */
    protected void terminateSession(WebSocketSession session, SessionInfo sessionInfo, ActorRef chatRoomRegion) {
        chatRoomRegion.tell(new ChatRoomEntityProtocol.LeaveChat(System.currentTimeMillis(), sessionInfo), ActorRef.noSender());
        session.close();
    }

    /** join chat helper */
    protected CompletionStage<Boolean> joinChat(SessionInfo sessionInfo, SinkRef<ChatMessageEnvelope> outboundSink) {
        ChatRoomEntityProtocol.ChatRoomCommand command = sessionInfo.isRejoin() ?
            new ChatRoomEntityProtocol.RejoinChat(System.currentTimeMillis(), sessionInfo, outboundSink) :
            new ChatRoomEntityProtocol.JoinChat(System.currentTimeMillis(), sessionInfo, outboundSink);

        return ask(this.akkaComponents.getChatRoomRegion(), command, REQUEST_TIMEOUT)
            .thenApply(response -> {
                if (!(response instanceof ChatRoomEntityProtocol.Committed)) {
                    throw new RuntimeException("received unexpected response from chatroom: " + response);
                }
                return true;
            });
    }

    /** handle incoming messages helper */
    protected CompletionStage<Void> handleMessageReceive(WebSocketSession session, SessionInfo sessionInfo) {
        ActorRef chatRoomRegion = this.akkaComponents.getChatRoomRegion();
        session.receive()
            // deserialize incoming payload
            .flatMap(inMsg -> {
                String payload = "";
                try {
                    payload = inMsg.getPayloadAsText();
                    ChatMessage chatMessage = this.mapper.readValue(payload, ChatMessage.class);

                    // forward chat message to chatroom and check for commit response.  If bad response or
                    // timeout, terminate the session and rely on client to re-establish new session
                    ask(chatRoomRegion, new ChatRoomEntityProtocol.AddMessage(chatMessage, sessionInfo), REQUEST_TIMEOUT)
                        .thenApply(response -> {
                            if (!(response instanceof ChatRoomEntityProtocol.Committed)) {
                                throw new RuntimeException("received unexpected response from chatroom: " + response);
                            }
                            return true;
                        })
                        .exceptionally(error -> {
                            logger.error(String.format("terminating websocket due to chatroom (%s) error: %s)", sessionInfo.getChatRoom(), error), error);
                            terminateSession(session, sessionInfo, chatRoomRegion);
                            return null;
                        });
                } catch (IOException ex) {
                    logger.error(String.format("malformed incoming chat message: %s", payload), ex);
                }

                return Flux.empty();
            })
            // listen for connection termination and close session
            .doFinally(signal -> {
                logger.info(String.format("terminating websocket session (client side) sig: [%s], [%s]", signal.name(), sessionInfo.getSessionId()));
                terminateSession(session, sessionInfo, chatRoomRegion);
            })
            // handle errors
            .doOnError(error -> {
                logger.error(String.format("terminating websocket due to error: [%s], [%s])", error, sessionInfo.getSessionId()), error);
                terminateSession(session, sessionInfo, chatRoomRegion);
            })
            .subscribe();

        return CompletableFuture.completedFuture(null);
    }
}
