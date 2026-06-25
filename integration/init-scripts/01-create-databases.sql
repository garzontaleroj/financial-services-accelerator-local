-- ─────────────────────────────────────────────────────────────────────────────
-- WSO2 Financial Services Accelerator – MySQL initialization script
-- This script runs automatically when the MySQL container starts for the
-- first time (mounted under /docker-entrypoint-initdb.d/).
-- ─────────────────────────────────────────────────────────────────────────────

-- API Manager databases
CREATE DATABASE IF NOT EXISTS apimgtdb      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS am_configdb   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS userdb        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Identity Server databases
CREATE DATABASE IF NOT EXISTS identitydb    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Financial Services custom databases
CREATE DATABASE IF NOT EXISTS fs_consentdb  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS fs_eventsdb   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Grant privileges to the service user
GRANT ALL PRIVILEGES ON apimgtdb.*     TO 'wso2user'@'%';
GRANT ALL PRIVILEGES ON am_configdb.*  TO 'wso2user'@'%';
GRANT ALL PRIVILEGES ON userdb.*       TO 'wso2user'@'%';
GRANT ALL PRIVILEGES ON identitydb.*   TO 'wso2user'@'%';
GRANT ALL PRIVILEGES ON fs_consentdb.* TO 'wso2user'@'%';
GRANT ALL PRIVILEGES ON fs_eventsdb.*  TO 'wso2user'@'%';

FLUSH PRIVILEGES;
