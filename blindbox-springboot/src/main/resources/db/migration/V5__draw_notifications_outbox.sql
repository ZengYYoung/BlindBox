CREATE TABLE outbox_event (
    event_id CHAR(36) PRIMARY KEY,
    order_id INT NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    lease_token CHAR(36),
    lease_until DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    sent_at DATETIME(3),
    UNIQUE KEY uq_outbox_order (order_id),
    KEY ix_outbox_pending (status, next_attempt_at, created_at),
    CONSTRAINT fk_outbox_order FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB;

CREATE TABLE notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id CHAR(36) NOT NULL,
    user_id INT NOT NULL,
    order_id INT NOT NULL,
    content VARCHAR(300) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uq_notification_event (event_id),
    KEY ix_notification_user (user_id, id),
    CONSTRAINT fk_notification_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES user(id)
) ENGINE=InnoDB;
