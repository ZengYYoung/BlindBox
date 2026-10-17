ALTER TABLE user ADD CONSTRAINT chk_user_balance CHECK(balance>=0 AND balance<=1000000000000),
 ADD CONSTRAINT chk_user_role CHECK(role IN ('USER','ADMIN'));
ALTER TABLE blind_box ADD CONSTRAINT chk_box_stock CHECK(stock>=0 AND stock<=10000000),
 ADD CONSTRAINT chk_box_price CHECK(price>0 AND price<=10000000),
 ADD CONSTRAINT chk_box_status CHECK(status IN ('DRAFT','ON_SALE','OFF_SALE','ARCHIVED'));
ALTER TABLE prize ADD CONSTRAINT chk_prize_stock CHECK(stock>=0 AND stock<=10000000),
 ADD CONSTRAINT chk_prize_rarity CHECK(rarity IN ('common','rare','secret'));
