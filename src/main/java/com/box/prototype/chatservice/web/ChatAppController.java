package com.box.prototype.chatservice.web;

import com.box.prototype.chatservice.akka.AkkaComponents;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import java.net.InetAddress;

@Controller
public class ChatAppController implements EnvironmentAware {
    @Value("Chat Service App")
    private String appName;

    @Autowired
    private AkkaComponents akkaComponents;

    private String hostname;
    private String port;

    public ChatAppController() {
        try {
            this.hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void setEnvironment(Environment env) {
        this.port = env.getProperty("server.port");
    }

    @RequestMapping("/chatapp")
    public String chatRoom(Model model) {
        model.addAttribute("appName", this.appName);
        model.addAttribute("hostname", this.hostname);
        model.addAttribute("port", this.port);
        return "chatapp";
    }
}
