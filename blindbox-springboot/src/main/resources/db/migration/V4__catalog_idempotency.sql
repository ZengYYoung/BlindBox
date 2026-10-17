ALTER TABLE blind_box ADD creator_id INT NULL,
 ADD create_request_id VARCHAR(64) NULL, ADD create_payload_hash VARCHAR(64) NULL,
 ADD UNIQUE KEY uk_box_creation(creator_id,create_request_id),
 ADD CONSTRAINT fk_box_creator FOREIGN KEY(creator_id) REFERENCES user(id);
