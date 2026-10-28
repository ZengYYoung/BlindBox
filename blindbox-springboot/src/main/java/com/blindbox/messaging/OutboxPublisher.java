package com.blindbox.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name="app.messaging.enabled", havingValue="true")
public class OutboxPublisher {
    private static final Logger log=LoggerFactory.getLogger(OutboxPublisher.class);
    private final JdbcTemplate db;
    private final RabbitTemplate rabbit;
    private final ObjectMapper json;
    public record DrawEvent(String eventId, int orderId) {}
    public OutboxPublisher(JdbcTemplate db,RabbitTemplate rabbit,ObjectMapper json) {
        this.db=db;this.rabbit=rabbit;this.json=json;
    }
    @Scheduled(fixedDelayString="${app.messaging.poll-delay-ms:500}")
    public void publishOne() {
        String token=UUID.randomUUID().toString();
        try {
            int claimed=db.update("UPDATE outbox_event SET lease_token=?, lease_until=DATE_ADD(NOW(3), INTERVAL 30 SECOND) " +
                "WHERE status='PENDING' AND next_attempt_at<=NOW(3) AND (lease_until IS NULL OR lease_until<NOW(3)) " +
                "ORDER BY created_at,event_id LIMIT 1",token);
            if(claimed==0) return;
            var rows=db.query("SELECT event_id,order_id,attempts FROM outbox_event WHERE lease_token=? AND status='PENDING'",
                (rs,n)->new Pending(rs.getString(1),rs.getInt(2),rs.getInt(3)),token);
            if(rows.isEmpty()) return;
            Pending event=rows.get(0);
            try {
                MessageProperties properties=new MessageProperties();
                properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                properties.setContentEncoding(StandardCharsets.UTF_8.name());
                properties.setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
                properties.setMessageId(event.id());
                Message message=new Message(json.writeValueAsBytes(new DrawEvent(event.id(),event.orderId())),properties);
                CorrelationData correlation=new CorrelationData(event.id());
                rabbit.send(MessageConfiguration.EXCHANGE,MessageConfiguration.KEY,message,correlation);
                CorrelationData.Confirm confirm=correlation.getFuture().get(5,TimeUnit.SECONDS);
                if(!confirm.isAck() || correlation.getReturned()!=null) throw new IllegalStateException("message not confirmed or unroutable");
                db.update("UPDATE outbox_event SET status='SENT',sent_at=NOW(3),lease_token=NULL,lease_until=NULL WHERE event_id=? AND lease_token=?",event.id(),token);
            } catch(Exception e) {
                int delay=Math.min(300,1 << Math.min(8,event.attempts()));
                db.update("UPDATE outbox_event SET attempts=attempts+1,next_attempt_at=DATE_ADD(NOW(3),INTERVAL ? SECOND),lease_token=NULL,lease_until=NULL WHERE event_id=? AND lease_token=?",delay,event.id(),token);
                log.warn("Draw event {} publish failed; retry in {} seconds: {}",event.id(),delay,e.toString());
            }
        } catch(Exception e) { log.warn("Outbox polling failed: {}",e.toString()); }
    }
    private record Pending(String id,int orderId,int attempts) {}
}
