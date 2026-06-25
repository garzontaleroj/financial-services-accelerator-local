-- V1: Event Notifications schema

CREATE TABLE IF NOT EXISTS ob_event_subscription (
    subscription_id   VARCHAR(36)   NOT NULL,
    client_id         VARCHAR(255)  NOT NULL,
    callback_url      VARCHAR(1024),
    event_types       TEXT,
    version           VARCHAR(20),
    created_timestamp DATETIME(6)   NOT NULL,
    PRIMARY KEY (subscription_id),
    INDEX idx_sub_client (client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ob_event_notification (
    notification_id   VARCHAR(36)  NOT NULL,
    client_id         VARCHAR(255) NOT NULL,
    set_payload       TEXT         NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    created_timestamp DATETIME(6)  NOT NULL,
    PRIMARY KEY (notification_id),
    INDEX idx_notif_client (client_id),
    INDEX idx_notif_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
