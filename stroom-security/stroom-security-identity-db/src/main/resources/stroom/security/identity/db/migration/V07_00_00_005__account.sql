-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

--
-- Create the account table
--
-- idempotent
CREATE TABLE IF NOT EXISTS account (
    id                        int NOT NULL AUTO_INCREMENT,
    version                   int NOT NULL,
    create_time_ms            bigint NOT NULL,
    create_user               varchar(255) NOT NULL,
    update_time_ms            bigint NOT NULL,
    update_user               varchar(255) NOT NULL,
    user_id                   varchar(255) NOT NULL,
    email                     varchar(255) DEFAULT NULL,
    password_hash             varchar(255) DEFAULT NULL,
    password_last_changed_ms  bigint DEFAULT NULL,
    first_name                varchar(255) DEFAULT NULL,
    last_name                 varchar(255) DEFAULT NULL,
    comments                  longtext,
    login_count               int NOT NULL DEFAULT '0',
    login_failures            int NOT NULL DEFAULT '0',
    last_login_ms             bigint DEFAULT NULL,
    reactivated_ms            bigint DEFAULT NULL,
    force_password_change     tinyint NOT NULL DEFAULT '0',
    never_expires             tinyint NOT NULL DEFAULT '0',
    enabled                   tinyint NOT NULL DEFAULT '0',
    inactive                  tinyint NOT NULL DEFAULT '0',
    locked                    tinyint NOT NULL DEFAULT '0',
    processing_account        tinyint NOT NULL DEFAULT '0',
    PRIMARY KEY (id),
    UNIQUE KEY user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

DROP PROCEDURE IF EXISTS identity_copy_auth_old_users;

DELIMITER //

--
-- Copy data into the account table if OLD_AUTH_users exists
-- This relies on a pre-migration script renaming v6 auth tbls
-- to OLD_AUTH_
--
CREATE PROCEDURE identity_copy_auth_old_users ()
BEGIN
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = database()
            AND TABLE_NAME = 'OLD_AUTH_users') THEN

        INSERT INTO account (
            id,
            version,
            create_time_ms,
            create_user,
            update_time_ms,
            update_user,
            user_id,
            email,
            password_hash,
            password_last_changed_ms,
            first_name,
            last_name,
            comments,
            login_count,
            login_failures,
            last_login_ms,
            reactivated_ms,
            force_password_change,
            never_expires,
            enabled,
            inactive,
            locked,
            processing_account)
        SELECT
            id,
            1,
            CASE WHEN created_on IS NULL
                THEN UNIX_TIMESTAMP() * 1000
                ELSE UNIX_TIMESTAMP(created_on) * 1000 END,
            IFNULL(created_by_user, "Flyway Migration"),
            CASE WHEN COALESCE(updated_on, created_on) IS NULL
                THEN UNIX_TIMESTAMP() * 1000
                ELSE IFNULL(UNIX_TIMESTAMP(updated_on) * 1000, UNIX_TIMESTAMP(created_on) * 1000) END,
            IFNULL(updated_by_user, IFNULL(created_by_user, "Flyway Migration")),
            email,
            email,
            password_hash,
            UNIX_TIMESTAMP(password_last_changed) * 1000,
            first_name,
            last_name,
            comments,
            IFNULL(login_count, 0),
            IFNULL(login_failures, 0),
            UNIX_TIMESTAMP(last_login) * 1000,
            UNIX_TIMESTAMP(reactivated_date) * 1000,
            false,
            never_expires,
            state = "enabled",
            state = "inactive",
            state = "locked",
            false
        FROM OLD_AUTH_users
        WHERE id > (SELECT COALESCE(MAX(id), 0) FROM account)
        ORDER BY id;

        -- Work out what to set our auto_increment start value to
        SELECT
            CONCAT(
            'ALTER TABLE account AUTO_INCREMENT = ',
            COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM account;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
    END IF;
END//

DELIMITER ;

-- idempotent
CALL identity_copy_auth_old_users();

DROP PROCEDURE IF EXISTS identity_copy_auth_old_users;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
