package com.box.prototype.chatservice.rest.handler;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Pair;
import akka.stream.*;
import akka.stream.javadsl.*;
import com.box.prototype.chatservice.akka.AkkaComponents;
import com.box.prototype.chatservice.domain.ChatSession;
import com.box.prototype.chatservice.domain.entities.ChatRoomEntityProtocol;
import com.box.prototype.chatservice.domain.models.ChatMessage;
import com.box.prototype.chatservice.util.URIParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.adapter.ReactorNettyWebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import static com.box.prototype.chatservice.WebSocketConfig.*;

@EnableWebFlux
public class ChatSessionHandler implements WebSocketHandler {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private AkkaComponents akkaComponents;
    private final ObjectMapper mapper = new ObjectMapper();

    // TODO: remove this
    private ActorRef fakeChatRoom;

    public ChatSessionHandler(AkkaComponents components) {
        this.akkaComponents = components;

        // TODO: remove this
        this.fakeChatRoom = this.akkaComponents.getSystem().actorOf(Props.create(FakeChatRoom.class));
    }

    private ConcurrentHashMap<String, ActorRef> sessions = new ConcurrentHashMap<>();

    // TODO: convert this to a local session actor that will buffer communication to chat room
    // TODO: - have to keep track of last message index
    // TODO: - rejoin will need to provide index to retrieve messages since
    // TODO: - use userId-timestamp for id for idempotency
    // TODO: remove this fake chat room and use real on instead
    private static class FakeChatRoom extends AbstractActor {
        private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

        private static final class KillSession {}

        private ActorMaterializer materializer;
        private HashMap<String, SourceQueueWithComplete<ChatMessage>> sessions = new HashMap<>();

        /** constructor */
        public FakeChatRoom() {
            this.materializer = ActorMaterializer.create(getContext());
        }

        /** cleanup */
        @Override
        public void postStop() {
            new ArrayList<>(this.sessions.keySet()).stream().forEach(this::removeSession);
        }

        /** message handler */
        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .match(ChatRoomEntityProtocol.JoinChat.class, msg -> {
                        log.info("creating source queue for session({})", msg.sessionId);
                        SourceQueueWithComplete<ChatMessage> sourceQueue = Source.<ChatMessage>queue(100, OverflowStrategy.backpressure())
                            .to(msg.sessionListener.getSink())
                            .run(this.materializer);
                        this.sessions.put(msg.sessionId, sourceQueue);
                        publishToSessions(new ChatMessage(msg.timestamp, "chatroomadmin", "fakechatroom", String.format("[%s] has joined chatroom", msg.userId)));
                    })
                    .match(ChatRoomEntityProtocol.LeaveChat.class, msg -> {
                        log.info("terminating chat session: {}", msg.sessionId);
                        removeSession(msg.sessionId);
                        publishToSessions(new ChatMessage(msg.timestamp, "chatroomadmin", "fakechatroom", String.format("[%s] has left chatroom", msg.userId)));
                    })
                    .match(ChatMessage.class, msg -> {
                        log.info("fake chat room received: " + msg);
                        publishToSessions(new ChatMessage(msg.getTimestamp(), msg.getUserId(), "fakechatroom", msg.getMessage()));
                    })
                    .match(KillSession.class, msg -> getContext().stop(getSelf()))
                    .matchAny(msg -> log.info("received unknown message: ", msg))
                    .build();
        }

        /** remove session */
        protected void removeSession(String sessionId) {
            if (this.sessions.containsKey(sessionId)) {
                SourceQueueWithComplete<ChatMessage> queue = this.sessions.get(sessionId);
                queue.complete();
                this.sessions.remove(sessionId);
            }
        }

        /** publish message to listeners */
        protected void publishToSessions(ChatMessage message) {
            this.sessions.values().stream()
                .forEach(sourceQueue -> sourceQueue.offer(message));
        }
    }

    /** get connection uri */
    private URI getConnectionURI(WebSocketSession session) {
        ReactorNettyWebSocketSession nettySession = (ReactorNettyWebSocketSession)session;
        return nettySession.getHandshakeInfo().getUri();
    }

    /** get uri path parameters */
    private MultiValueMap<String, String> uriQueryParameters(String template, URI uri) {
        return UriComponentsBuilder.fromUriString(uri.toASCIIString()).build().getQueryParams();
    }

    /** get uri path parameters */
    private Map<String, String> uriPathParameters(String template, URI uri) {
        UriTemplate uriTemplate = new UriTemplate(template);
        return uriTemplate.match(uri.getPath());
    }

    /** web socket handler */
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        URIParser uriParser = new URIParser(getConnectionURI(session), CHAT_SESSION_ROUTE);
        final String chatRoom = uriParser.getPathParamValue(ROOM_ID_PARAM_KEY);
        final String userId = uriParser.getFirstQueryParamValue(USER_ID_PARAM_KEY);
        if (chatRoom == null || userId == null) {
            logger.error("terminating due missing chatroom ({}) or userid ({})", chatRoom, userId);
            session.close();
            return session.send(Flux.empty());
        }

        final String sessionId = session.getId();
        if (!this.sessions.containsKey(sessionId)) {
            // construct a sinkref to pass to chatroom
            Pair<CompletionStage<SinkRef<ChatMessage>>, Publisher<WebSocketMessage>> pair = StreamRefs.<ChatMessage>sinkRef()
                .map(msg -> session.textMessage(this.mapper.writeValueAsString(msg)))
                .toMat(Sink.asPublisher(AsPublisher.WITHOUT_FANOUT), Keep.both())
                .run(this.akkaComponents.getMaterializer());
            final Publisher<WebSocketMessage> outFlux = pair.second();

            pair.first()
                // create chat session for this connection
                .thenApply(sinkRef -> {
                    ActorRef chatSession = this.akkaComponents.getSystem().actorOf(ChatSession.createProps(sinkRef), "chat-session-" + sessionId);
                    ChatRoomEntityProtocol.ChatRoomCommand command = uriParser.queryParamExists("rejoin") ?
                        new ChatRoomEntityProtocol.RejoinChat(System.currentTimeMillis(), userId, sessionId, uriParser.getFirstQueryParamValue(LAST_EVENT_ID_PARAM_KEY), sinkRef) :
                        new ChatRoomEntityProtocol.JoinChat(System.currentTimeMillis(), userId, sessionId, sinkRef);
                    chatSession.tell(command, ActorRef.noSender());

                    return chatSession;
                })
                // handle incoming messages
                .thenAccept(chatSession -> {
                    session.receive()
                        // deserialize incoming payload
                        .flatMap(inMsg -> {
                            String payload = "";
                            try {
                                payload = inMsg.getPayloadAsText();
                                ChatMessage chatMessage = this.mapper.readValue(payload, ChatMessage.class);
                                logger.info("received inbound message from client [{}]: {}", sessionId, chatMessage);

                                chatSession.tell(chatMessage, ActorRef.noSender());
                            } catch (IOException ex) {
                                logger.error(String.format("malformed incoming chat message: %s", payload), ex);
                            }

                            return Flux.empty();
                        })
                        // listen for connection termination and close session
                        .doFinally(signal -> {
                            logger.info("terminating websocket session (client side) sig: [{}], [{}]", signal.name(), sessionId);
                            chatSession.tell(new ChatRoomEntityProtocol.LeaveChat(System.currentTimeMillis(), userId, sessionId), ActorRef.noSender());
                            session.close();
                        })
                        .subscribe();
                });

            return session.send(outFlux);
        } else {
            logger.error("terminating due to duplicate session id: " + sessionId);
            session.close();
            return session.send(Flux.empty());
        }
    }
}
