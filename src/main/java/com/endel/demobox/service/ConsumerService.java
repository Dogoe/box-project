package com.endel.demobox.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import static resource.Const.PAYMENT_TOPIC;

@Service
public class ConsumerService {
    public ConsumerService(){

    }

    @KafkaListener(topics = PAYMENT_TOPIC, groupId = "codeDecode_group")
    public void listenToTopic(String receivedMessage){
        System.out.println("The Received Message is: " + receivedMessage);
    }
}
