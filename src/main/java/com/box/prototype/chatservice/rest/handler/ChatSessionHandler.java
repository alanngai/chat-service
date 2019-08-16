package com.box.prototype.chatservice.rest.handler;

import com.box.prototype.chatservice.akka.AkkaComponents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;

@EnableWebFlux
public class ChatSessionHandler implements WebSocketHandler {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private AkkaComponents akkaComponents;
    public ChatSessionHandler(AkkaComponents components) {
        this.akkaComponents = components;
    }

    private HashSet<String> sessionIds = new HashSet<>();
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        final String sessionId = session.getId();
        if (this.sessionIds.add(sessionId)) {
            logger.info("starting websocket session [{}]", sessionId);

            // echo inbound messages outbound
            final Flux<WebSocketMessage> outFlux = session.receive()
                .map(inMsg -> {
                    String text = inMsg.getPayloadAsText();
                    logger.info("received inbound message from client [{}]: {}", sessionId, text);
                    return String.format("{\"session\":\"%s\"} echoing: %s", sessionId, text);
                })
                .map(session::textMessage)
                .doFinally(signal -> {
                    logger.info("terminating websocket session (client side) sig: [{}], [{}]", signal.name(), sessionId);
                    session.close();
                    sessionIds.remove(sessionId);
                });

            return session.send(outFlux);
        } else {
            logger.error("terminating due to duplicate session id: " + sessionId);
            session.close();
            return session.send(Flux.empty());
        }
    }
}
