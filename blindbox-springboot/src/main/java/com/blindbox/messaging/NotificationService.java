package com.blindbox.messaging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {
    private final JdbcTemplate db;
    public NotificationService(JdbcTemplate db) { this.db=db; }
    @Transactional
    public void create(String eventId,int orderId) {
        // The unique event key makes redelivery harmless, including a crash after commit and before ACK.
        int rows=db.update("INSERT INTO notification(event_id,user_id,order_id,content) " +
            "SELECT ?,user_id,id,CONCAT('你抽中了 ',prize_name,'（',blind_box_name,'）') FROM orders WHERE id=? " +
            "ON DUPLICATE KEY UPDATE event_id=event_id",eventId,orderId);
        if(rows==0) {
            Integer exists=db.queryForObject("SELECT COUNT(*) FROM notification WHERE event_id=? AND order_id=?",Integer.class,eventId,orderId);
            if(exists==null || exists==0) throw new IllegalStateException("order not found for event");
        }
    }
    public List<Map<String,Object>> list(int userId) {
        return db.queryForList("SELECT id,order_id AS orderId,content,created_at AS createdAt FROM notification WHERE user_id=? ORDER BY id DESC LIMIT 30",userId);
    }
}
