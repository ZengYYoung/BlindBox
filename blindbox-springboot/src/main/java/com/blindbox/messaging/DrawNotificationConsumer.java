package com.blindbox.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@ConditionalOnProperty(name="app.messaging.enabled", havingValue="true")
public class DrawNotificationConsumer {
    private final NotificationService notifications;
    private final ObjectMapper json;
    public DrawNotificationConsumer(NotificationService notifications,ObjectMapper json) {this.notifications=notifications;this.json=json;}
    @RabbitListener(queues=MessageConfiguration.QUEUE)
    public void receive(Message message) throws Exception {
        OutboxPublisher.DrawEvent event=json.readValue(message.getBody(),OutboxPublisher.DrawEvent.class);
        UUID.fromString(event.eventId());
        if(event.orderId()<=0 || !event.eventId().equals(message.getMessageProperties().getMessageId()))
            throw new IllegalArgumentException("invalid draw event");
        notifications.create(event.eventId(),event.orderId());
    }
}
