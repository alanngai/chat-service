package com.box.prototype.chatservice.domain;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.actor.dsl.Creators;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.stream.ActorMaterializer;
import akka.stream.OverflowStrategy;
import akka.stream.SinkRef;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceQueueWithComplete;
import com.box.prototype.chatservice.domain.entities.ChatRoomEntityProtocol;
import com.box.prototype.chatservice.domain.models.ChatMessage;

import java.util.Optional;

import static com.box.prototype.chatservice.domain.ChatSessionProtocol.*;

import static com.box.prototype.chatservice.domain.entities.ChatRoomEntityProtocol.*;

public class ChatSession extends AbstractActor {
    public static Props createProps(SinkRef<ChatMessage> chatSinkRef) {
        return Props.create(ChatSession.class, chatSinkRef);
    }

    private final LoggingAdapter logger = Logging.getLogger(getContext().getSystem(), this);
    private SourceQueueWithComplete<ChatMessage> sessionListener;
    private ActorMaterializer materializer = ActorMaterializer.create(getContext());

    /** constructor */
    public ChatSession(SinkRef<ChatMessage> chatSinkRef) {
        this.sessionListener = Source.<ChatMessage>queue(100, OverflowStrategy.backpressure())
            .to(chatSinkRef.getSink())
            .run(this.materializer);
    }

    /** message handler */
    @Override
    public Receive createReceive() {
        // TODO: think through fail and reconnect
        return receiveBuilder()
            .match(JoinChat.class, m -> System.out.println())
            .match(RejoinChat.class, m -> System.out.println())
            .match(AddMessage.class, m -> System.out.println())
            .match(LeaveChat.class, m -> System.out.println())
            .matchAny(msg -> logger.info("received unknown message: ", msg))
            .build();
    }
}
