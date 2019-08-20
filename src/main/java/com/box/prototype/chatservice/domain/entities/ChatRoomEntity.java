package com.box.prototype.chatservice.domain.entities;

import akka.japi.pf.FI;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.SnapshotOffer;
import com.box.prototype.chatservice.domain.models.ChatMessage;

import java.util.ArrayList;
import java.util.Collections;

import static com.box.prototype.chatservice.domain.entities.ChatRoomEntityProtocol.*;

public class ChatRoomEntity extends AbstractPersistentActor {
    private ChatRoomEntityState state = new ChatRoomEntityState(getSelf().path().name());

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
        this.state.update(event);
        try {
            handler.apply(command, event);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(JoinChat.class, cmd -> persistAndHandle(cmd, new MemberJoined(cmd.timestamp, cmd.userId), this::handleMemberJoined))
            .match(LeaveChat.class, cmd -> persistAndHandle(cmd, new MemberLeft(cmd.timestamp, cmd.userId), this::handleMemberLeft))
            .match(AddMessage.class, cmd -> persistAndHandle(cmd, new MessageAdded(cmd.message), this::handleMessageAdded))
            .match(GetMessageLog.class, this::handleGetMessageLog)
            .build();
    }

    protected void handleGetMessageLog(GetMessageLog cmd) {
        ArrayList<ChatMessage> log = new ArrayList<>(cmd.numMessages);
        int lastIndex = cmd.lastIndex >= 0 ? cmd.lastIndex : this.state.chatLog.size() - 1;
        int i = lastIndex;
        for (; (i >= 0) || (lastIndex - i < cmd.numMessages); --i) {
            log.add(this.state.chatLog.get(i));
        }
        Collections.reverse(log);
        getSender().tell(new MessageLog(log, i), getSelf());
    }

    protected void handleMemberJoined(JoinChat command, MemberJoined event) {
        // TODO: next steps
        // - figure out data structure to track chat sessions
        // - figure out what happens to sessions on recovery (how to detect and handle terminated session on both ends?)
        // - publish chat events to sessions
    }

    protected void handleMemberLeft(LeaveChat command, MemberLeft event) {

    }

    protected void handleMessageAdded(AddMessage command, MessageAdded event) {

    }
}
