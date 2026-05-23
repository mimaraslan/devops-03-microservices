package com.mimaraslan.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

//    http://localhost:9594/notification

@RequiredArgsConstructor
@RestController
@RequestMapping("/notification")
public class NotificationController {

    //    http://localhost/notification
    @GetMapping
    public String hi (){
        return "NotificationService Hi" ;
    }

    //    http://localhost/notification/nfo
    @GetMapping("/info")
    public String info (){
        return "INFO: NotificationService";
    }

}
