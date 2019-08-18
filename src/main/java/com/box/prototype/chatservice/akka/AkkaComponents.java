package com.box.prototype.chatservice.akka;

import akka.stream.ActorMaterializer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.springframework.stereotype.Component;
import akka.actor.ActorSystem;

@Component
public class AkkaComponents {
    private final Config config;
    private final ActorSystem system;
    private final ActorMaterializer materializer;

    public AkkaComponents() {
        this.config = ConfigFactory.load();
        this.system = ActorSystem.create("chat-service", this.config);
        this.materializer = ActorMaterializer.create(this.system);

        // TODO: create chat room shard region
    }

    public Config getConfig() {
        return this.config;
    }

    public ActorSystem getSystem() {
        return this.system;
    }

    public ActorMaterializer getMaterializer() { return this.materializer; }
}
