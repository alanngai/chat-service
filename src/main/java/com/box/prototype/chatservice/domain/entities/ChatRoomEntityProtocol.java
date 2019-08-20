package com.box.prototype.chatservice.domain.entities;

import akka.actor.ActorRef;
import akka.cluster.sharding.ShardRegion;
import akka.stream.SinkRef;
import com.box.prototype.chatservice.domain.models.ChatMessage;
import com.box.prototype.chatservice.domain.models.ChatRoom;
import com.typesafe.config.Config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;

public class ChatRoomEntityProtocol {
    // commands
    public interface ChatRoomCommand {
        String getChatRoom();
    }
    public static class JoinChat implements Serializable, ChatRoomCommand {
        public final long timestamp;
        public final String chatRoom;
        public final String userId;
        public SinkRef<ChatMessage> sessionListener;

        public JoinChat(long timestamp, String userId, String chatRoom, SinkRef<ChatMessage> sessionListener) {
            this.timestamp = timestamp;
            this.chatRoom = chatRoom;
            this.userId = userId;
            this.sessionListener = sessionListener;
        }
        public String getChatRoom() { return this.chatRoom; }
    }
    public static class RejoinChat implements Serializable, ChatRoomCommand {
        public final long timestamp;
        public final String chatRoom;
        public final String userId;
        public SinkRef<ChatMessage> sessionListener;

        public RejoinChat(long timestamp, String userId, String chatRoom, SinkRef<ChatMessage> sessionListener) {
            this.timestamp = timestamp;
            this.chatRoom = chatRoom;
            this.userId = userId;
            this.sessionListener = sessionListener;
        }
        public String getChatRoom() { return this.chatRoom; }
    }
    public static class LeaveChat implements Serializable, ChatRoomCommand {
        public final long timestamp;
        public final String userId;
        public final String chatRoom;

        public LeaveChat(long timestamp, String userId, String chatRoom) {
            this.timestamp = timestamp;
            this.userId = userId;
            this.chatRoom = chatRoom;
        }
        public String getChatRoom() { return this.chatRoom; }
    }
    public static class AddMessage implements Serializable, ChatRoomCommand {
        public final ChatMessage message;
        public final String lastEventId;

        public AddMessage(ChatMessage message, String lastEventId) {
            this.message = message;
            this.lastEventId = lastEventId;
        }
        public String getChatRoom() { return this.message.getChatRoom(); }
    }

    // command responses
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
                this.chatLog.add(new ChatMessage(joined.timestamp, joined.userId, this.chatRoom, String.format("[%s] joined chat", joined.userId)));
            } else if (event instanceof MemberLeft) {
                MemberLeft left = (MemberLeft) event;
                this.members.remove(left.userId);
                this.chatLog.add(new ChatMessage(left.timestamp, left.userId, chatRoom, String.format("[%s] left chat", left.userId)));
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
                return ((ChatRoomCommand)message).getChatRoom();
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
