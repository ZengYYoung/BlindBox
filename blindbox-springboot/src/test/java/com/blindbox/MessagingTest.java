package com.blindbox;
import com.blindbox.messaging.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.amqp.AmqpException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
class MessagingTest {
    @Test void unavailableBrokerLeavesEventPendingForRetry() throws Exception {
        JdbcTemplate db=mock(JdbcTemplate.class);
        RabbitTemplate rabbit=mock(RabbitTemplate.class);
        when(db.update(anyString(),any(Object[].class))).thenReturn(1);
        String id=UUID.randomUUID().toString();
        when(db.query(anyString(),any(RowMapper.class),any(Object[].class))).thenAnswer(inv -> {
            java.sql.ResultSet row=mock(java.sql.ResultSet.class);
            when(row.getString(1)).thenReturn(id);when(row.getInt(2)).thenReturn(7);when(row.getInt(3)).thenReturn(0);
            RowMapper<?> mapper=inv.getArgument(1);
            return java.util.List.of(mapper.mapRow(row,0));
        });
        doThrow(new AmqpException("offline")).when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
        new OutboxPublisher(db,rabbit,new ObjectMapper()).publishOne();
        verify(db).update(startsWith("UPDATE outbox_event SET attempts=attempts+1"),any(Object[].class));
        verify(db,never()).update(startsWith("UPDATE outbox_event SET status='SENT'"),any(Object[].class));
    }
    @Test void consumerValidatesIdentity() throws Exception {
        NotificationService service=mock(NotificationService.class);
        ObjectMapper json=new ObjectMapper();
        DrawNotificationConsumer consumer=new DrawNotificationConsumer(service,json);
        String id=UUID.randomUUID().toString();
        MessageProperties props=new MessageProperties();props.setMessageId(id);
        Message message=new Message(json.writeValueAsBytes(new OutboxPublisher.DrawEvent(id,7)),props);
        consumer.receive(message);verify(service).create(id,7);
        props.setMessageId(UUID.randomUUID().toString());
        assertThrows(IllegalArgumentException.class,()->consumer.receive(message));
        verifyNoMoreInteractions(service);
    }
    @Test void missingOrderDoesNotBecomeNotification() {
        JdbcTemplate db=mock(JdbcTemplate.class);
        when(db.update(anyString(),any(),any())).thenReturn(0);
        when(db.queryForObject(anyString(),eq(Integer.class),any(),any())).thenReturn(0);
        assertThrows(IllegalStateException.class,()->new NotificationService(db).create(UUID.randomUUID().toString(),8));
    }
    @Test void duplicateNotificationIsSafe() {
        JdbcTemplate db=mock(JdbcTemplate.class);
        when(db.update(anyString(),any(),any())).thenReturn(0);
        when(db.queryForObject(anyString(),eq(Integer.class),any(),any())).thenReturn(1);
        assertDoesNotThrow(()->new NotificationService(db).create(UUID.randomUUID().toString(),8));
    }
    @Test @EnabledIfEnvironmentVariable(named="RUN_RABBIT_TEST",matches="YES")
    void realRabbitConfirmAndUnroutableReturn() throws Exception {
        CachingConnectionFactory factory=new CachingConnectionFactory("127.0.0.1",5672);
        factory.setUsername("guest");factory.setPassword("guest");
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);factory.setPublisherReturns(true);
        RabbitAdmin admin=new RabbitAdmin(factory);
        RabbitTemplate template=new RabbitTemplate(factory);template.setMandatory(true);
        String name="blindbox.test."+UUID.randomUUID();Queue queue=new Queue(name,false,true,true);
        try {
            admin.declareQueue(queue);
            CorrelationData routed=new CorrelationData(UUID.randomUUID().toString());
            template.convertAndSend("",name,"test",routed);
            assertTrue(routed.getFuture().get(5,TimeUnit.SECONDS).isAck());assertNull(routed.getReturned());
            assertNotNull(template.receive(name,3000));
            CorrelationData missing=new CorrelationData(UUID.randomUUID().toString());
            template.convertAndSend("",name+".missing","test",missing);
            assertTrue(missing.getFuture().get(5,TimeUnit.SECONDS).isAck());
            assertNotNull(missing.getReturned(),"mandatory return must be correlated before confirm");
        } finally {admin.deleteQueue(name);factory.destroy();}
    }
}
