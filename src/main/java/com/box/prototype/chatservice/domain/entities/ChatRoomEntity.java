package com.box.prototype.chatservice.domain.entities;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Pair;
import akka.japi.pf.FI;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.SnapshotOffer;
import akka.stream.ActorMaterializer;
import akka.stream.OverflowStrategy;
import akka.stream.QueueOfferResult;
import akka.stream.SinkRef;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceQueueWithComplete;
import com.box.prototype.chatservice.domain.models.ChatMessage;
import com.box.prototype.chatservice.domain.models.ChatMessageEnvelope;
import com.box.prototype.chatservice.domain.models.SessionInfo;

import java.util.HashMap;
import java.util.Map;

import static com.box.prototype.chatservice.domain.entities.ChatRoomEntityProtocol.*;

public class ChatRoomEntity extends AbstractPersistentActor {
    protected final LoggingAdapter logger = Logging.getLogger(getContext().getSystem(), this);

    protected ChatRoomEntityState state = new ChatRoomEntityState(getSelf().path().name());

    // these member variables hold transient state (such as current sessions) that won't be persisted.
    // should this entity get failed over, the chat log will be persisted via akka-persistence, while no sessions
    // will be recovered.  On subsequent commands for which there are no sessions, the chatroom will return error,
    // which will signal to the client session the need to re-establish the session via a JoinChat or RejoinChat
    // command
    protected Map<String, SourceQueueWithComplete<ChatMessageEnvelope>> clientSessions = new HashMap<>();

    // track lastEventId for each session for backfill purposes
    protected Map<String, String> sessionCursors = new HashMap<>();

    protected final int OUT_STREAM_BUFFER;
    protected final ActorMaterializer materializer;

    public ChatRoomEntity() {
        OUT_STREAM_BUFFER = getContext().getSystem().settings().config().getInt("chat-sessions.out-stream-buffer");

        this.materializer = ActorMaterializer.create(getContext());
    }

    @Override
    public String persistenceId() {
        return getSelf().path().name();
    }

    @Override
    public Receive createReceiveRecover() {
        return receiveBuilder()
            .match(ChatRoomEvent.class, this.state::update)
            .match(SnapshotOffer.class, snapshot -> this.state = (ChatRoomEntityState)snapshot.snapshot())
            .build();
    }

    /** persists event, updates state, and calls event handler */
    protected <C extends ChatRoomCommand, E extends ChatRoomEvent> void persistAndHandle(C command, E event, FI.UnitApply2<C, E> handler) {
        persist(event, evt -> {
            this.state.update(evt);
            handler.apply(command, evt);
        });
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(JoinChat.class, cmd -> persist(mapMemberJoined(cmd), event -> handleMemberJoined(cmd, event)))
            .match(RejoinChat.class, this::handleMemberRejoined)
            .match(LeaveChat.class, cmd -> persist(mapMemberLeft(cmd), event -> handleMemberLeft(cmd, event)))
            .match(AddMessage.class, cmd -> persist(new MessageAdded(cmd.message), event -> handleMessageAdded(cmd, event)))
            .match(StopSession.class, this::handleStopSession)
            .build();
    }

    /** helper join chat message */
    public MemberJoined mapMemberJoined(JoinChat joinChat) {
        return new MemberJoined(new ChatMessage(
            joinChat.timestamp,
            joinChat.sessionInfo.getUserId(),
            String.format("[%s] has joined", joinChat.sessionInfo.getUserId())
        ));
    }

    /** helper left chat message */
    public MemberLeft mapMemberLeft(LeaveChat leaveChat) {
        return new MemberLeft(new ChatMessage(
            leaveChat.timestamp,
            leaveChat.sessionInfo.getUserId(),
            String.format("[%s] has left", leaveChat.sessionInfo.getUserId())
        ));
    }

    /** helper join chat message */
    public ChatMessage membershipMessage(String action, JoinChat joinChat, SessionInfo sessionInfo) {
        return new ChatMessage(
            joinChat.timestamp,
            joinChat.sessionInfo.getUserId(),
            String.format("[%s] has %s", joinChat.sessionInfo.getUserId(), action)
        );
    }

    /** create sourcequeue from sinkref */
    protected SourceQueueWithComplete<ChatMessageEnvelope> createSourceQueue(SinkRef<ChatMessageEnvelope> sinkRef) {
        return Source.<ChatMessageEnvelope>queue(OUT_STREAM_BUFFER, OverflowStrategy.dropNew())
            .to(sinkRef.getSink())
            .run(this.materializer);
    }

    protected void handleMemberJoined(JoinChat command, MemberJoined event) {
        this.state.update(event);

        // update session state
        SourceQueueWithComplete<ChatMessageEnvelope> sourceQueue = createSourceQueue(command.sessionListener);
        this.clientSessions.put(command.sessionInfo.getSessionId(), sourceQueue);

        publishMessageToAll(new ChatMessageEnvelope(event.message, eventId(event.message)));
        getSender().tell(new Committed(), getSelf());
    }

    protected void handleMemberRejoined(RejoinChat command) {
        // no event generated or persisted on rejoin, only need to update session information and resend messages since lastEventid

        // update session state
        SourceQueueWithComplete<ChatMessageEnvelope> sourceQueue = createSourceQueue(command.sessionListener);
        this.clientSessions.put(command.sessionInfo.getSessionId(), sourceQueue);

        // publish messages from lastEventId
        this.state.chatLog
            .tailMap(parseEventId(command.sessionInfo.getLastEventId()))
            .forEach((key, message) -> {
                publishMessage(command.sessionInfo.getSessionId(), sourceQueue, new ChatMessageEnvelope(message, eventId(message)));
            });

        getSender().tell(new Committed(), getSelf());
    }

    protected void handleMemberLeft(LeaveChat command, MemberLeft event) {
        this.state.update(event);

        // update session state
        SourceQueueWithComplete<ChatMessageEnvelope> sourceQueue = this.clientSessions.remove(command.sessionInfo.getSessionId());
        if (sourceQueue != null) {
            sourceQueue.complete();
        }

        publishMessageToAll(new ChatMessageEnvelope(event.message, eventId(event.message)));
        getSender().tell(new Committed(), getSelf());
    }

    protected void handleMessageAdded(AddMessage command, MessageAdded event) {
        this.state.update(event);

        publishMessageToAll(new ChatMessageEnvelope(event.message, eventId(event.message)));
        getSender().tell(new Committed(), getSelf());
    }

    public void handleStopSession(StopSession command) {
        if (this.clientSessions.containsKey(command.sessionId)) {
            logger.info("terminating session: " + command.sessionId);
            SourceQueueWithComplete<ChatMessageEnvelope> sourceQueue = this.clientSessions.remove(command.sessionId);
            sourceQueue.complete();
        }
        if (this.sessionCursors.containsKey(command.sessionId)) {
            this.sessionCursors.remove(command.sessionId);
        }
    }

    /** helper to publish message to listeners */
    protected void publishMessageToAll(ChatMessageEnvelope message) {
        this.clientSessions.forEach( (sessionId, listener) -> publishMessage(sessionId, listener, message));
    }

    /** helper to publish message to listeners */
    protected void publishMessage(String sessionId, SourceQueueWithComplete<ChatMessageEnvelope> listener, ChatMessageEnvelope message) {
        final ActorRef self = getSelf();
        listener.offer(message)
            .thenAccept(result -> {
                if (!(result instanceof QueueOfferResult.Enqueued$)) {
                    throw new RuntimeException(String.format("bad SourceQueue.offer() result: %s", result));
                }
            })
            .exceptionally(error -> {
                logger.error(error, "unexpected error publishing to session ({}): {}", sessionId, error);

                // signal to self to terminate session
                getSelf().tell(new StopSession(sessionId), ActorRef.noSender());
                return null;
            });
    }

    /** extracts event id from chat message */
    protected String eventId(ChatMessage message) {
        return "" + message.getTimestamp() + "/" + message.getUserId();
    }

    /** parse chat log ke from event id */
    protected Pair<Long, String> parseEventId(String eventId) {
        int splitIndex = eventId.indexOf("/");
        if (splitIndex > 0) {
            long timestamp = Long.parseLong(eventId.substring(0, splitIndex));
            String userId = eventId.substring(splitIndex + 1);

            return Pair.create(timestamp, userId);
        } else {
            throw new RuntimeException("unparseable event id: " + eventId);
        }
    }
}
