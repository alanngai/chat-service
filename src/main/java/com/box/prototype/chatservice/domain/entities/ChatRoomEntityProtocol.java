package com.box.prototype.chatservice.domain.entities;

import akka.cluster.sharding.ShardRegion;
import akka.stream.SinkRef;
import com.box.prototype.chatservice.domain.models.ChatMessage;
import com.box.prototype.chatservice.domain.models.SessionInfo;
import com.typesafe.config.Config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;

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
        public SinkRef<ChatMessage> sessionListener;

        public JoinChat(long timestamp, SessionInfo sessionInfo, SinkRef<ChatMessage> sessionListener) {
            super(sessionInfo);
            this.timestamp = timestamp;
            this.sessionListener = sessionListener;
        }
        public String getChatRoom() { return this.sessionInfo.getChatRoom(); }
    }
    public static class RejoinChat extends  ChatRoomCommand implements Serializable {
        public final long timestamp;
        public SinkRef<ChatMessage> sessionListener;

        public RejoinChat(long timestamp, SessionInfo sessionInfo, SinkRef<ChatMessage> sessionListener) {
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
