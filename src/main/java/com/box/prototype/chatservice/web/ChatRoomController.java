package com.box.prototype.chatservice.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ChatRoomController {
    @Value("Chat Service")
    private String appName;

    @RequestMapping("/chatapp")
    public String chatRoom(Model model) {
        model.addAttribute("appName", this.appName);
        return "chatapp";
    }
}
