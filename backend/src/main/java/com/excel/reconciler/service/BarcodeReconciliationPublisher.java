package com.excel.reconciler.service;

import com.excel.reconciler.config.RabbitMqConfig;
import com.excel.reconciler.model.BarcodeReconciliationRequest;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class BarcodeReconciliationPublisher {
    private final RabbitTemplate rabbitTemplate;

    public BarcodeReconciliationPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(BarcodeReconciliationRequest request) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, RabbitMqConfig.ROUTING_KEY, request);
    }
}
