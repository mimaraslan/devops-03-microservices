package com.mimaraslan.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

//    http://localhost:9594
@RestController
@RequestMapping
public class HelloController {

    //    http://localhost:9594
    @GetMapping("/")
    public String hello (){
        return "NotificationService Hello";
    }

}
