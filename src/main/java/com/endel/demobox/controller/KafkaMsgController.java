package com.endel.demobox.controller;


import com.endel.demobox.service.ProducerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/kafka/api")
public class KafkaMsgController {
    final ProducerService producerService;
    public KafkaMsgController(ProducerService producerService){
        this.producerService = producerService;
    }
    @GetMapping("/producerMsg")
    public void getMessageFromClient(@RequestParam("message") String message){
        producerService.sendMessageToTopic(message);
    }

}
