-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id`          INT PRIMARY KEY AUTO_INCREMENT,
    `account`     VARCHAR(50)  NOT NULL UNIQUE,
    `password`    VARCHAR(255) NOT NULL COMMENT 'bcrypt加密后的密文',
    `balance`     INT          NOT NULL DEFAULT 0 COMMENT '余额,单位:分',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 盲盒表
CREATE TABLE IF NOT EXISTS `blind_box` (
    `id`          INT PRIMARY KEY AUTO_INCREMENT,
    `name`        VARCHAR(100) NOT NULL,
    `series`      VARCHAR(50)  NOT NULL COMMENT 'sea/forest/space/candy',
    `image`       VARCHAR(255) DEFAULT '',
    `description` VARCHAR(500) DEFAULT '',
    `price`       INT          NOT NULL COMMENT '价格,单位:分',
    `stock`       INT          NOT NULL DEFAULT 0,
    `version`     INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 奖品表
CREATE TABLE IF NOT EXISTS `prize` (
    `id`           INT PRIMARY KEY AUTO_INCREMENT,
    `blind_box_id` INT          NOT NULL,
    `prize_name`   VARCHAR(100) NOT NULL,
    `prize_img`    VARCHAR(255) DEFAULT '',
    `rarity`       VARCHAR(20)  NOT NULL COMMENT 'common/rare/secret',
    `value`        INT          NOT NULL DEFAULT 0,
    `probability`  DECIMAL(5,4) NOT NULL COMMENT '概率 0.0000~1.0000',
    FOREIGN KEY (`blind_box_id`) REFERENCES `blind_box`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 订单表
CREATE TABLE IF NOT EXISTS `orders` (
    `id`           INT PRIMARY KEY AUTO_INCREMENT,
    `user_id`      INT         NOT NULL,
    `blind_box_id` INT         NOT NULL,
    `prize_id`     INT         NOT NULL,
    `price`        INT         NOT NULL COMMENT '下单时价格,单位:分',
    `status`       VARCHAR(20) NOT NULL DEFAULT '已支付',
    `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`user_id`)      REFERENCES `user`(`id`),
    FOREIGN KEY (`blind_box_id`) REFERENCES `blind_box`(`id`),
    FOREIGN KEY (`prize_id`)     REFERENCES `prize`(`id`)
) ENGINE=InnoDB;

-- 晒单表
CREATE TABLE IF NOT EXISTS `player_show` (
    `id`         INT PRIMARY KEY AUTO_INCREMENT,
    `user_id`    INT           NOT NULL,
    `content`    TEXT,
    `images`     VARCHAR(1000) DEFAULT '[]' COMMENT 'JSON数组存储图片URL',
    `likes`      INT           NOT NULL DEFAULT 0,
    `created_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`)
) ENGINE=InnoDB;

-- 评论表
CREATE TABLE IF NOT EXISTS `comment` (
    `id`             INT PRIMARY KEY AUTO_INCREMENT,
    `player_show_id` INT  NOT NULL,
    `user_id`        INT  NOT NULL,
    `content`        TEXT,
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`player_show_id`) REFERENCES `player_show`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`user_id`)        REFERENCES `user`(`id`)
) ENGINE=InnoDB;
