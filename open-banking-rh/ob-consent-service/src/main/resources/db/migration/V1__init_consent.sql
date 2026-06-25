-- Flyway migration V1: initial schema for ob-consent-service
-- Replaces WSO2 FS Accelerator MySQL scripts for OB_CONSENT, OB_CONSENT_MAPPING, etc.

CREATE TABLE IF NOT EXISTS ob_consent (
    consent_id          VARCHAR(36)     NOT NULL,
    client_id           VARCHAR(255)    NOT NULL,
    user_id             VARCHAR(255),
    consent_type        VARCHAR(50)     NOT NULL,
    status              VARCHAR(30)     NOT NULL,
    receipt             TEXT            NOT NULL,
    created_timestamp   DATETIME(6)     NOT NULL,
    updated_timestamp   DATETIME(6)     NOT NULL,
    expiration_timestamp DATETIME(6),
    consent_attributes  TEXT,
    PRIMARY KEY (consent_id),
    INDEX idx_consent_client  (client_id),
    INDEX idx_consent_user    (user_id),
    INDEX idx_consent_status  (status),
    INDEX idx_consent_type    (consent_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
