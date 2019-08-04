package com.box.prototype.chatservice.domain.entities;

import akka.actor.ActorRef;
import com.box.prototype.chatservice.domain.models.ChatMessage;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;

public class ChatRoomEntityProtocol {
    // commands
    public interface ChatRoomCommand {}
    public static class JoinChat implements Serializable, ChatRoomCommand {
        public final long timestamp;
        public final String userId;
        public final ActorRef chatSession;

        public JoinChat(long timestamp, String userId, ActorRef chatSession) {
            this.timestamp = timestamp;
            this.userId = userId;
            this.chatSession = chatSession;
        }
    }
    public static class RejoinChat implements Serializable, ChatRoomCommand {
        public final long timestamp;
        public final String userId;
        public final ActorRef chatSession;

        public RejoinChat(long timestamp, String userId, ActorRef chatSession) {
            this.timestamp = timestamp;
            this.userId = userId;
            this.chatSession = chatSession;
        }
    }
    public static class LeaveChat implements Serializable, ChatRoomCommand {
        public final long timestamp;
        public final String userId;

        public LeaveChat(long timestamp, String userId) {
            this.timestamp = timestamp;
            this.userId = userId;
        }
    }
    public static class AddMessage implements Serializable, ChatRoomCommand {
        public final ChatMessage message;

        public AddMessage(ChatMessage message) {
            this.message = message;
        }
    }
    public static class GetMessageLog implements Serializable, ChatRoomCommand {
        public final int numMessages;
        public final int lastIndex;

        public GetMessageLog(int numMessages) {
            this(numMessages, -1);
        }
        public GetMessageLog(int numMessages, int lastIndex) {
            this.numMessages = numMessages;
            this.lastIndex = lastIndex;
        }
    }

    // command responses
    public static class MessageLog implements Serializable {
        public final ArrayList<ChatMessage> chatLog;
        public final int lastIndex;

        public MessageLog(ArrayList<ChatMessage> chatLog, int lastIndex) {
            this.chatLog = chatLog;
            this.lastIndex = lastIndex;
        }
    }

    // events
    public interface ChatRoomEvent {}
    public static class MemberJoined implements Serializable, ChatRoomEvent {
        public final long timestamp;
        public final String userId;

        public MemberJoined(long timestamp, String userId) {
            this.timestamp = timestamp;
            this.userId = userId;
        }
    }
    public static class MemberLeft implements Serializable, ChatRoomEvent {
        public final long timestamp;
        public final String userId;

        public MemberLeft(long timestamp, String userId) {
            this.timestamp = timestamp;
            this.userId = userId;
        }
    }
    public static class MessageAdded implements Serializable, ChatRoomEvent {
        public final ChatMessage message;

        public MessageAdded(ChatMessage message) {
            this.message = message;
        }
    }

    // state
    protected static class ChatRoomEntityState implements Serializable {
        public final HashSet<String> members = new HashSet<>();
        public final ArrayList<ChatMessage> chatLog = new ArrayList<>();

        public ChatRoomEntityState() {

        }

        /** update state given event */
        public void update(ChatRoomEvent event) {
            if (event instanceof MemberJoined) {
                MemberJoined joined = (MemberJoined)event;
                this.members.add(joined.userId);
                this.chatLog.add(new ChatMessage(joined.timestamp, joined.userId, String.format("[%s] joined chat", joined.userId)));
            } else if (event instanceof MemberLeft) {
                MemberLeft left = (MemberLeft) event;
                this.members.remove(left.userId);
                this.chatLog.add(new ChatMessage(left.timestamp, left.userId, String.format("[%s] left chat", left.userId)));
            } else if (event instanceof MessageAdded) {
                this.chatLog.add(((MessageAdded)event).message);
            } else {
                throw new RuntimeException("unknown ChatRoom event type: " + event.getClass());
            }
        }
    }
}
