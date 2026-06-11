package com.example.demo.messaging;

public interface AnalysisQueuePublisher {

    void publish(AnalysisQueueMessage message);

    String queueName();
}
