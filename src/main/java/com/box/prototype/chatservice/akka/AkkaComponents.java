package com.box.prototype.chatservice.akka;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.springframework.stereotype.Component;
import akka.actor.ActorSystem;

@Component
public class AkkaComponents {
    private final Config config;
    private final ActorSystem system;

    public AkkaComponents() {
        this.config = ConfigFactory.load();
        this.system = ActorSystem.create("chat-service", this.config);
    }

    public Config getConfig() {
        return this.config;
    }

    public ActorSystem getSystem() {
        return this.system;
    }
}
