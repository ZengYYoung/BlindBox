package com.blindbox;

import com.blindbox.core.DrawTransactionService;
import com.blindbox.messaging.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:mysql://127.0.0.1:33319/blindbox_mq_test?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai",
    "spring.datasource.username=root","spring.datasource.password=", "app.jwt-secret=01234567890123456789012345678901",
    "app.rate-limit-enabled=false","app.messaging.enabled=true", "spring.rabbitmq.host=127.0.0.1",
    "spring.rabbitmq.username=guest","spring.rabbitmq.password=guest", "spring.rabbitmq.virtual-host=/",
    "spring.rabbitmq.publisher-confirm-type=correlated","spring.rabbitmq.publisher-returns=true",
    "app.messaging.poll-delay-ms=200"
})
@EnabledIfEnvironmentVariable(named="RUN_MQ_DB_TEST",matches="YES")
class RabbitDatabaseIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired DrawTransactionService draws;
    @Autowired RabbitTemplate rabbit;
    @Autowired ObjectMapper json;
    @Test void rollbackAndEndToEndAndDuplicate() throws Exception {
        db.update("INSERT INTO user(account,password,balance) VALUES ('mq-isolated-user','test',10000)");
        int user=db.queryForObject("SELECT id FROM user WHERE account='mq-isolated-user'",Integer.class);
        db.update("INSERT INTO blind_box(name,series,price,stock,status) VALUES ('mq-box','sea',100,2,'ON_SALE')");
        int box=db.queryForObject("SELECT id FROM blind_box WHERE name='mq-box'",Integer.class);
        db.update("INSERT INTO prize(blind_box_id,prize_name,rarity,value,stock) VALUES (?,'test-prize','common',0,2)",box);
        db.execute("CREATE TRIGGER mq_test_fail BEFORE INSERT ON outbox_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test rollback'");
        try { assertThrows(Exception.class,()->draws.draw(user,box,"mq-test-rollback")); }
        finally { db.execute("DROP TRIGGER mq_test_fail"); }
        assertEquals(10000L,db.queryForObject("SELECT balance FROM user WHERE id=?",Long.class,user));
        assertEquals(2,db.queryForObject("SELECT stock FROM blind_box WHERE id=?",Integer.class,box));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class));
        var result=draws.draw(user,box,"mq-test-success");
        var order=result.order();
        String id=db.queryForObject("SELECT event_id FROM outbox_event WHERE order_id=?",String.class,order.getId());
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(System.nanoTime()<until && db.queryForObject("SELECT COUNT(*) FROM notification WHERE event_id=?",Integer.class,id)==0) Thread.sleep(100);
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM notification WHERE event_id=?",Integer.class,id));
        assertEquals(9900L,db.queryForObject("SELECT balance FROM user WHERE id=?",Long.class,user));
        assertEquals(1,db.queryForObject("SELECT stock FROM blind_box WHERE id=?",Integer.class,box));
        assertTrue(draws.draw(user,box,"mq-test-success").replayed());
        MessageProperties props=new MessageProperties();props.setMessageId(id);props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbit.send(MessageConfiguration.EXCHANGE,MessageConfiguration.KEY,new Message(json.writeValueAsBytes(new OutboxPublisher.DrawEvent(id,order.getId())),props));
        Thread.sleep(1000);
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM notification WHERE event_id=?",Integer.class,id));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE order_id=?",Integer.class,order.getId()));
    }
}
