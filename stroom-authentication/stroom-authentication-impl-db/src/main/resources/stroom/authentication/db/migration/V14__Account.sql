-- ------------------------------------------------------------------------
-- Copyright 2020 Crown Copyright
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
-- ------------------------------------------------------------------------

-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

--
-- Rename the old USERS table
--
DROP PROCEDURE IF EXISTS rename_users;
DELIMITER //
CREATE PROCEDURE rename_users ()
BEGIN
    IF NOT EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'OLD_users') THEN

        IF EXISTS (
                SELECT NULL
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_NAME = 'users') THEN

            RENAME TABLE users TO OLD_users;
        END IF;
    END IF;
END//
DELIMITER ;
CALL rename_users();
DROP PROCEDURE rename_users;

--
-- Create the account table
--
CREATE TABLE IF NOT EXISTS account (
  id                        int(11) NOT NULL AUTO_INCREMENT,
  version                   int(11) NOT NULL,
  create_time_ms            bigint(20) NOT NULL,
  create_user               varchar(255) NOT NULL,
  update_time_ms            bigint(20) NOT NULL,
  update_user               varchar(255) NOT NULL,
  email                     varchar(255) NOT NULL,
  password_hash             varchar(255) NOT NULL,
  password_last_changed_ms  bigint(20) DEFAULT NULL,
  first_name                varchar(255) DEFAULT NULL,
  last_name                 varchar(255) DEFAULT NULL,
  comments                  longtext,
  login_count               int(11) NOT NULL DEFAULT '0',
  login_failures            int(11) NOT NULL DEFAULT '0',
  last_login_ms             bigint(20) DEFAULT NULL,
  reactivated_ms            bigint(20) DEFAULT NULL,
  force_password_change     tinyint(1) NOT NULL DEFAULT '0',
  never_expires             tinyint(1) NOT NULL DEFAULT '0',
  enabled                   tinyint(1) NOT NULL DEFAULT '0',
  inactive                  tinyint(1) NOT NULL DEFAULT '0',
  locked                    tinyint(1) NOT NULL DEFAULT '0',
  processing_account        tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY               (id),
  UNIQUE KEY                email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the account table
--
DROP PROCEDURE IF EXISTS copy_users;
DELIMITER //
CREATE PROCEDURE copy_users ()
BEGIN
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'OLD_users') THEN

        INSERT INTO account (
            id,
            version,
            create_time_ms,
            create_user,
            update_time_ms,
            update_user,
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
            UNIX_TIMESTAMP(created_on) * 1000,
            created_by_user,
            IFNULL(UNIX_TIMESTAMP(updated_on) * 1000, UNIX_TIMESTAMP(created_on) * 1000),
            IFNULL(updated_by_user, created_by_user),
            email,
            password_hash,
            UNIX_TIMESTAMP(password_last_changed) * 1000,
            first_name,
            last_name,
            comments,
            login_count,
            login_failures,
            UNIX_TIMESTAMP(last_login) * 1000,
            UNIX_TIMESTAMP(reactivated_date) * 1000,
            force_password_change,
            never_expires,
            state = "enabled",
            state = "inactive",
            state = "locked",
            false
        FROM OLD_users
        WHERE id > (SELECT COALESCE(MAX(id), 0) FROM account)
        ORDER BY id;

        -- Work out what to set our auto_increment start value to
        SELECT CONCAT('ALTER TABLE account AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM account;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
    END IF;
END//
DELIMITER ;
CALL copy_users();
DROP PROCEDURE copy_users;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
