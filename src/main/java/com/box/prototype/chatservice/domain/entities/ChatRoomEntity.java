package com.box.prototype.chatservice.domain.entities;

import akka.japi.pf.FI;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.SnapshotOffer;
import akka.stream.SinkRef;
import com.box.prototype.chatservice.domain.models.ChatMessage;

import java.util.HashMap;
import java.util.Map;

import static com.box.prototype.chatservice.domain.entities.ChatRoomEntityProtocol.*;

public class ChatRoomEntity extends AbstractPersistentActor {
    private ChatRoomEntityState state = new ChatRoomEntityState(getSelf().path().name());

    // these member variables hold transient state (such as current sessions) that won't be persisted.
    // should this entity get failed over, the chat log will be persisted via akka-persistence, while no sessions
    // will be recovered.  On subsequent commands for which there are no sessions, the chatroom will return error,
    // which will signal to the client session the need to re-establish the session via a JoinChat or RejoinChat
    // command
    protected Map<String, SinkRef<ChatMessage>> clientSessions = new HashMap<>();
    // userId -> sessionId
    protected Map<String, String> existingSessions = new HashMap<>();

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
        // persist event
        this.state.update(event);
        try {
            // invoke event handler on successful event persist
            handler.apply(command, event);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(JoinChat.class, cmd -> persistAndHandle(cmd, new MemberJoined(cmd.timestamp, cmd.sessionInfo.getUserId()), this::handleMemberJoined))
            .match(RejoinChat.class, this::handleMemberRejoined)
            .match(LeaveChat.class, cmd -> persistAndHandle(cmd, new MemberLeft(cmd.timestamp, cmd.sessionInfo.getUserId()), this::handleMemberLeft))
            .match(AddMessage.class, cmd -> persistAndHandle(cmd, new MessageAdded(cmd.message), this::handleMessageAdded))
            .build();
    }

    protected void handleMemberJoined(JoinChat command, MemberJoined event) {
        // TODO: next steps
        // - figure out data structure to track chat sessions
        // - figure out what happens to sessions on recovery (how to detect and handle terminated session on both ends?)
        // - publish chat events to sessions
    }

    protected void handleMemberRejoined(RejoinChat command) {
        // no event generated or persisted on rejoin, only need to update session information and resend messages since lastEventid
    }

    protected void handleMemberLeft(LeaveChat command, MemberLeft event) {

    }

    protected void handleMessageAdded(AddMessage command, MessageAdded event) {

    }
}
