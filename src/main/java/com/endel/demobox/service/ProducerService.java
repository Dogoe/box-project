package com.endel.demobox.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import static resource.Const.PAYMENT_TOPIC;

@Service
public class ProducerService {
    final KafkaTemplate<String, String> kafkaTemplate;

    public ProducerService(KafkaTemplate<String, String> kafkaTemplate){
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendMessageToTopic(String message){
        kafkaTemplate.send(PAYMENT_TOPIC, message);

    }
}
