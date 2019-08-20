package com.box.prototype.chatservice.akka;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.cluster.sharding.ShardRegion;
import akka.stream.ActorMaterializer;
import com.box.prototype.chatservice.domain.entities.ChatRoomEntity;
import com.box.prototype.chatservice.domain.entities.ChatRoomEntityProtocol;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.springframework.stereotype.Component;
import akka.actor.ActorSystem;

@Component
public class AkkaComponents {
    private final Config config;
    private final ActorSystem system;
    private final ActorMaterializer materializer;
    private final ActorRef chatRoomRegion;

    public AkkaComponents() {
        this.config = ConfigFactory.load();
        this.system = ActorSystem.create("chat-service", this.config);
        this.materializer = ActorMaterializer.create(this.system);

        this.chatRoomRegion = ClusterSharding.get(this.system).start(
            "ChatRoomEntity",
            Props.create(ChatRoomEntity.class),
            ClusterShardingSettings.create(this.system),
            new ChatRoomEntityProtocol.ChatRoomEntityMessageExtractor(this.config)
        );
    }

    public Config getConfig() {
        return this.config;
    }

    public ActorSystem getSystem() {
        return this.system;
    }

    public ActorMaterializer getMaterializer() { return this.materializer; }

    public ActorRef getChatRoomRegion() { return this.chatRoomRegion; }
}
