package com.example.demo.messaging;

import com.example.demo.config.RabbitMqConfig;
import com.example.demo.dto.AnalysisJobMessage;
import com.example.demo.service.AnalysisJobEnqueuer;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${spring.rabbitmq.host:}'.length() > 0 && !'${analysis.worker.mode:local}'.equalsIgnoreCase('local')")
public class RabbitMqAnalysisJobEnqueuer implements AnalysisJobEnqueuer {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void enqueue(AnalysisJobMessage message) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.ANALYSIS_QUEUE, message);
    }
}
