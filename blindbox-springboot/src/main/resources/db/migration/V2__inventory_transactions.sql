ALTER TABLE user MODIFY balance BIGINT NOT NULL DEFAULT 0, ADD role VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE blind_box MODIFY price BIGINT NOT NULL, ADD status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
 ADD legacy_stock INT NOT NULL DEFAULT 0;
UPDATE blind_box SET legacy_stock = stock, stock = 0;
ALTER TABLE prize ADD stock INT NOT NULL DEFAULT 0, DROP COLUMN probability;
ALTER TABLE orders MODIFY price BIGINT NOT NULL,
 ADD request_id VARCHAR(64) NULL,
 ADD blind_box_name VARCHAR(100) NOT NULL DEFAULT '',
 ADD prize_name VARCHAR(100) NOT NULL DEFAULT '',
 ADD prize_img VARCHAR(255) NOT NULL DEFAULT '',
 ADD rarity VARCHAR(20) NOT NULL DEFAULT '';
UPDATE orders o JOIN blind_box b ON b.id=o.blind_box_id JOIN prize p ON p.id=o.prize_id
 SET o.request_id=CONCAT('legacy-',o.id),o.blind_box_name=b.name,o.prize_name=p.prize_name,
 o.prize_img=COALESCE(p.prize_img,''),o.rarity=p.rarity;
ALTER TABLE orders MODIFY request_id VARCHAR(64) NOT NULL,
 ADD UNIQUE KEY uk_orders_request(user_id,request_id),
 ADD KEY idx_orders_user_id(user_id,id);
CREATE TABLE wallet_ledger (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id INT NOT NULL, request_id VARCHAR(64) NOT NULL,
 type VARCHAR(20) NOT NULL, amount BIGINT NOT NULL, balance_after BIGINT NOT NULL,
 order_id INT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_wallet_request(user_id,type,request_id),
 KEY idx_wallet_user(user_id,id),
 FOREIGN KEY(user_id) REFERENCES user(id), FOREIGN KEY(order_id) REFERENCES orders(id)
) ENGINE=InnoDB;
CREATE TABLE stock_ledger (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, blind_box_id INT NOT NULL, prize_id INT NOT NULL,
 actor_id INT NOT NULL, delta INT NOT NULL, stock_after INT NOT NULL, reason VARCHAR(20) NOT NULL,
 order_id INT NULL, request_id VARCHAR(64) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_stock_request(blind_box_id,prize_id,reason,request_id),
 KEY idx_stock_box(blind_box_id,id),
 FOREIGN KEY(blind_box_id) REFERENCES blind_box(id), FOREIGN KEY(prize_id) REFERENCES prize(id),
 FOREIGN KEY(actor_id) REFERENCES user(id), FOREIGN KEY(order_id) REFERENCES orders(id)
) ENGINE=InnoDB;
CREATE TABLE player_show_like (
 user_id INT NOT NULL, player_show_id INT NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(user_id,player_show_id),
 FOREIGN KEY(user_id) REFERENCES user(id),
 FOREIGN KEY(player_show_id) REFERENCES player_show(id)
) ENGINE=InnoDB;
CREATE INDEX idx_comment_show_id ON comment(player_show_id,id);
