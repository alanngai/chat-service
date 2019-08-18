package com.box.prototype.chatservice.rest.handler;

import akka.NotUsed;
import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Pair;
import akka.stream.DelayOverflowStrategy;
import akka.stream.KillSwitches;
import akka.stream.UniqueKillSwitch;
import akka.stream.javadsl.AsPublisher;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.box.prototype.chatservice.akka.AkkaComponents;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.adapter.ReactorNettyWebSocketSession;
import org.springframework.web.util.UriTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.box.prototype.chatservice.WebSocketConfig.*;

@EnableWebFlux
public class ChatSessionHandler implements WebSocketHandler {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private AkkaComponents akkaComponents;
    public ChatSessionHandler(AkkaComponents components) {
        this.akkaComponents = components;
    }

    private ConcurrentHashMap<String, ActorRef> sessions = new ConcurrentHashMap<>();

    // TODO: remove this temp actor that generates periodic messages for test
    private static class FakeChatRoom extends AbstractActorWithTimers {
        private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

        private static final Object TICK_KEY = "tick-key";
        private static final class Tick {}
        private static final class KillSession {}

        /** constructor */
        public FakeChatRoom() {
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

    /** get connection uri */
    private URI getConnectionURI(WebSocketSession session) {
        ReactorNettyWebSocketSession nettySession = (ReactorNettyWebSocketSession)session;
        return nettySession.getHandshakeInfo().getUri();
    }

    /** get uri path parameters */
    private Map<String, String> uriPathParameters(String template, URI uri) {
        UriTemplate uriTemplate = new UriTemplate(template);
        return uriTemplate.match(uri.getPath());
    }

    /** web socket handler */
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        final String sessionId = session.getId();
        if (!this.sessions.containsKey(sessionId)) {
            Map<String, String> pathParams = uriPathParameters(CHAT_SESSION_ROUTE, getConnectionURI(session));
            final String chatRoom = pathParams.get(ID_PARAM_KEY);
            logger.info("starting chatroom [{}] websocket session [{}]", chatRoom, sessionId);

            // create chat session actor
//            ActorRef chatSession = this.akkaComponents.getSystem().actorOf(Props.create(FakeChatSession.class), "chat-session-" + sessionId);
//            this.sessions.put(sessionId, chatSession);

//            // echo inbound messages outbound
//            final Flux<WebSocketMessage> outFlux = session.receive()
//                .map(session::textMessage)
//                .map(inMsg -> {
//                    String text = inMsg.getPayloadAsText();
//                    logger.info("received inbound message from client [{}]: {}", sessionId, text);
//                    return String.format("{\"session\":\"%s\"} echoing: %s", sessionId, text);
//                })
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

            final Pair<UniqueKillSwitch, Publisher<WebSocketMessage>> fluxPair = Source
                .repeat(NotUsed.getInstance())
                .delay(Duration.ofSeconds(1), DelayOverflowStrategy.backpressure())
                .map(e -> "foo")
                .map(session::textMessage)
                .watchTermination((notUsed, done) -> {
                    done.thenAccept(doneFlag -> {
                        logger.info("stream terminated");
                    });
                    return NotUsed.getInstance();
                })
                .viaMat(KillSwitches.single(), Keep.right())
                .toMat(Sink.asPublisher(AsPublisher.WITHOUT_FANOUT), Keep.both())
                .run(this.akkaComponents.getMaterializer());
            final UniqueKillSwitch killSwitch = fluxPair.first();
            final Publisher<WebSocketMessage> outFlux = fluxPair.second();

            // TODO: construct input stream from incoming session
            session.receive()
                .map(inMsg -> {
                    // parse and route message

                        //
                    String text = inMsg.getPayloadAsText();
                    logger.info("received inbound message from client [{}]: {}", sessionId, text);
                    return String.format("{\"session\":\"%s\"} echoing: %s", sessionId, text);
                })
                .doFinally(signal -> {
                    logger.info("terminating websocket session (client side) sig: [{}], [{}]", signal.name(), sessionId);
                    killSwitch.shutdown();
                    session.close();
                })
                .subscribe();




            // TODO: connect to chatroom

            return session.send(outFlux);
        } else {
            logger.error("terminating due to duplicate session id: " + sessionId);
            session.close();
            return session.send(Flux.empty());
        }
    }
}
