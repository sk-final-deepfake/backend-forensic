package com.example.demo.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RabbitMqAnalysisQueuePublisher implements AnalysisQueuePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;
    private final String queueName;

    public RabbitMqAnalysisQueuePublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${forenshield.rabbitmq.analysis-exchange:}") String exchange,
            @Value("${forenshield.rabbitmq.analysis-routing-key:analysis.requested}") String routingKey,
            @Value("${forenshield.rabbitmq.analysis-queue:forenshield.analysis.requests}") String queueName
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.queueName = queueName;
    }

    @Override
    public void publish(AnalysisQueueMessage message) {
        if (exchange == null || exchange.isBlank()) {
            rabbitTemplate.convertAndSend(queueName, message);
            return;
        }
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
    }

    @Override
    public String queueName() {
        return queueName;
    }
}
