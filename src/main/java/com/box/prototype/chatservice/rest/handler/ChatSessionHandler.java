package com.box.prototype.chatservice.rest.handler;

import akka.actor.AbstractActor;
import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.stream.javadsl.AsPublisher;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.box.prototype.chatservice.akka.AkkaComponents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

@EnableWebFlux
public class ChatSessionHandler implements WebSocketHandler {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private AkkaComponents akkaComponents;
    public ChatSessionHandler(AkkaComponents components) {
        this.akkaComponents = components;
    }

    private ConcurrentHashMap<String, ActorRef> sessions = new ConcurrentHashMap<>();

    // TODO: remove this temp actor that generates periodic messages for test
    private static class FakeChatSession extends AbstractActorWithTimers {
        private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

        private static final Object TICK_KEY = "tick-key";
        private static final class Tick {}
        private static final class KillSession {}

        /** constructor */
        public FakeChatSession() {
            getTimers().startPeriodicTimer(TICK_KEY, new Tick(), Duration.ofSeconds(2));
        }

        /** handle actor cleanup */
        @Override
        public void postStop() {
            getTimers().cancel(TICK_KEY);
        }

        /** message handler */
        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .match(Tick.class, msg -> getSender().tell(String.format("%s: hello!", System.currentTimeMillis()), getSelf()))
                    .match(KillSession.class, msg -> getContext().stop(getSelf()))
                    .matchAny(msg -> log.info("received unknown message: ", msg))
                    .build();
        }
    }

    /** web socket handler */
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        final String sessionId = session.getId();
        if (!this.sessions.containsKey(sessionId)) {
            logger.info("starting websocket session [{}]", sessionId);

            // create chat session actor
            ActorRef chatSession = this.akkaComponents.getSystem().actorOf(Props.create(FakeChatSession.class), "chat-session-" + sessionId);
            this.sessions.put(sessionId, chatSession);

//            // echo inbound messages outbound
//            final Flux<WebSocketMessage> outFlux = session.receive()
//                .map(inMsg -> {
//                    String text = inMsg.getPayloadAsText();
//                    logger.info("received inbound message from client [{}]: {}", sessionId, text);
//                    return String.format("{\"session\":\"%s\"} echoing: %s", sessionId, text);
//                })
//                .map(session::textMessage)
//                .doFinally(signal -> {
//                    logger.info("terminating websocket session (client side) sig: [{}], [{}]", signal.name(), sessionId);
//                    session.close();
//
//                    // remove and terminate session
//                    if (this.sessions.containsKey(sessionId)) {
//                        ActorRef ses = this.sessions.remove(sessionId);
//                        ses.tell(new FakeChatSession.KillSession(), this.akkaComponents.getSystem().deadLetters());
//                    }
//                });
            Source.queue()
            final Flux<WebSocketMessage> outFlux =
                    Flux.concat(Source
                        .actorRef(Props.create(FakeChatSession.class))
                        .runWith(Sink.asPublisher(AsPublisher.WITHOUT_FANOUT), this.akkaComponents.getMaterializer())
                    )
                    .map(msg -> session.textMessage(msg.toString()));
            return session.send(outFlux);
        } else {
            logger.error("terminating due to duplicate session id: " + sessionId);
            session.close();
            return session.send(Flux.empty());
        }
    }
}
