package com.box.prototype.chatservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

@ServletComponentScan
@SpringBootApplication(scanBasePackages = "com.box.prototype.chatservice")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

//import org.springframework.boot.SpringApplication;
//import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.boot.web.servlet.ServletComponentScan;
//
//@ServletComponentScan
//@SpringBootApplication(scanBasePackages = "com.box.prototype.chatservice")
//public class Application {
//    public static void main(String[] args) {
//        SpringApplication.run(Application.class, args);
//    }
//}
