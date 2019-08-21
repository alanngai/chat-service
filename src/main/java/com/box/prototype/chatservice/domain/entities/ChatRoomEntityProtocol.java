package com.box.prototype.chatservice.domain.entities;

import akka.cluster.sharding.ShardRegion;
import akka.japi.Pair;
import akka.stream.SinkRef;
import com.box.prototype.chatservice.domain.models.ChatMessage;
import com.box.prototype.chatservice.domain.models.ChatMessageEnvelope;
import com.box.prototype.chatservice.domain.models.SessionInfo;
import com.typesafe.config.Config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.TreeMap;

public class ChatRoomEntityProtocol {
    // commands
    public static class ChatRoomCommand {
        public final SessionInfo sessionInfo;

        protected ChatRoomCommand(SessionInfo sessionInfo) {
            this.sessionInfo = sessionInfo;
        }
    }
    public static class JoinChat extends ChatRoomCommand implements Serializable{
        public final long timestamp;
        public SinkRef<ChatMessageEnvelope> sessionListener;

        public JoinChat(long timestamp, SessionInfo sessionInfo, SinkRef<ChatMessageEnvelope> sessionListener) {
            super(sessionInfo);
            this.timestamp = timestamp;
            this.sessionListener = sessionListener;
        }
        public String getChatRoom() { return this.sessionInfo.getChatRoom(); }
    }
    public static class RejoinChat extends  ChatRoomCommand implements Serializable {
        public final long timestamp;
        public SinkRef<ChatMessageEnvelope> sessionListener;

        public RejoinChat(long timestamp, SessionInfo sessionInfo, SinkRef<ChatMessageEnvelope> sessionListener) {
            super(sessionInfo);
            this.timestamp = timestamp;
            this.sessionListener = sessionListener;
        }
        public String getChatRoom() { return this.sessionInfo.getChatRoom(); }
    }
    public static class LeaveChat extends ChatRoomCommand implements Serializable{
        public final long timestamp;

        public LeaveChat(long timestamp, SessionInfo sessionInfo) {
            super(sessionInfo);
            this.timestamp = timestamp;
        }
        public String getChatRoom() { return this.sessionInfo.getChatRoom(); }
    }
    public static class AddMessage extends ChatRoomCommand implements Serializable {
        public final ChatMessage message;

        public AddMessage(ChatMessage message, SessionInfo sessionInfo) {
            super(sessionInfo);
            this.message = message;
        }
        public String getChatRoom() { return this.sessionInfo.getChatRoom(); }
    }
    public static class StopSession {
        public final String sessionId;

        public StopSession(String sessionId) {
            this.sessionId = sessionId;
        }
    }

    // command responses
    public static class Committed implements Serializable {}
    public static class NewChatMessage implements Serializable {
        public final ChatMessage chatMessage;
        public final String lastEventId;

        public NewChatMessage(ChatMessage chatMessage, String lastEventId) {
            this.chatMessage = chatMessage;
            this.lastEventId = lastEventId;
        }
    }

    // events
    public static class ChatRoomEvent {
        public final ChatMessage message;
        protected ChatRoomEvent(ChatMessage message) {
            this.message = message;
        }
    }
    public static class MemberJoined extends ChatRoomEvent implements Serializable {
        public MemberJoined(ChatMessage message) { super(message); }
    }
    public static class MemberLeft extends ChatRoomEvent implements Serializable {
        public MemberLeft(ChatMessage message) { super(message); }
    }
    public static class MessageAdded extends ChatRoomEvent implements Serializable {
        public MessageAdded(ChatMessage message) { super(message); }
    }

    // state
    protected static class ChatRoomEntityState implements Serializable {
        public final HashSet<String> members = new HashSet<>();
        public final TreeMap<Pair<Long, String>, ChatMessage> chatLog = new TreeMap<>(new Comparator<Pair<Long, String>>() {
            @Override
            public int compare(Pair<Long, String> left, Pair<Long, String> right) {
                int tsCompare = Long.compare(left.first(), right.first());
                return tsCompare != 0 ? tsCompare : left.second().compareTo(right.second());
            }
        });
        public String chatRoom = "";
        public ChatRoomEntityState() {
        }
        public ChatRoomEntityState(String chatRoom) {
            this.chatRoom = chatRoom;
        }

        /** update state given event */
        public void update(ChatRoomEvent event) {
            if (event instanceof MemberJoined) {
                MemberJoined joined = (MemberJoined)event;
                this.members.add(joined.message.getUserId());
                this.chatLog.put(Pair.create(joined.message.getTimestamp(), joined.message.getUserId()), joined.message);
            } else if (event instanceof MemberLeft) {
                MemberLeft left = (MemberLeft) event;
                this.members.remove(left.message.getUserId());
                this.chatLog.put(Pair.create(left.message.getTimestamp(), left.message.getUserId()), left.message);
            } else if (event instanceof MessageAdded) {
                ChatMessage chatMessage = ((MessageAdded)event).message;
                this.chatLog.put(Pair.create(chatMessage.getTimestamp(), chatMessage.getUserId()), chatMessage);
            } else {
                throw new RuntimeException("unknown ChatRoom event type: " + event.getClass());
            }
        }
    }

    // cluster sharding message extractor
    public static class ChatRoomEntityMessageExtractor implements ShardRegion.MessageExtractor {
        private final int numShards;

        public ChatRoomEntityMessageExtractor(Config config) {
            this.numShards = config.getInt("chat-rooms.num-shards");
        }

        @Override
        public String shardId(Object message) {
            return "" + (Math.abs(entityId(message).hashCode()) % this.numShards);
        }

        @Override
        public String entityId(Object message) {
            if (message instanceof ChatRoomCommand) {
                return ((ChatRoomCommand)message).sessionInfo.getChatRoom();
            } else {
                throw new RuntimeException("unknown message: " + message);
            }
        }

        @Override
        public Object entityMessage(Object message) {
            return message;
        }
    }
}
