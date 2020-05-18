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
-- Rename the old token_types table
--
DROP PROCEDURE IF EXISTS rename_token_types;
DELIMITER //
CREATE PROCEDURE rename_token_types ()
BEGIN
    IF NOT EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'OLD_token_types') THEN

        IF EXISTS (
                SELECT NULL
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_NAME = 'token_types') THEN

            RENAME TABLE token_types TO OLD_token_types;
        END IF;
    END IF;
END//
DELIMITER ;
CALL rename_token_types();
DROP PROCEDURE rename_token_types;

--
-- Create the token_type table
--
CREATE TABLE IF NOT EXISTS token_type (
  id                        int(11) NOT NULL AUTO_INCREMENT,
  type                      varchar(255) NOT NULL,
  PRIMARY KEY               (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the token_type table
--
DROP PROCEDURE IF EXISTS copy_token_types;
DELIMITER //
CREATE PROCEDURE copy_token_types ()
BEGIN
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'OLD_token_types') THEN

        INSERT INTO token_type (
            id,
            type)
        SELECT
            id,
            token_type
        FROM OLD_token_types
        WHERE id > (SELECT COALESCE(MAX(id), 0) FROM token_type)
        ORDER BY id;

        -- Work out what to set our auto_increment start value to
        SELECT CONCAT('ALTER TABLE token_type AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM token_type;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
    END IF;
END//
DELIMITER ;
CALL copy_token_types();
DROP PROCEDURE copy_token_types;


















--
-- Rename the old tokens table
--
DROP PROCEDURE IF EXISTS rename_tokens;
DELIMITER //
CREATE PROCEDURE rename_tokens ()
BEGIN
    IF NOT EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'OLD_tokens') THEN

        IF EXISTS (
                SELECT NULL
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_NAME = 'tokens') THEN

            RENAME TABLE tokens TO OLD_tokens;
        END IF;
    END IF;
END//
DELIMITER ;
CALL rename_tokens();
DROP PROCEDURE rename_tokens;

--
-- Create the token table
--
CREATE TABLE IF NOT EXISTS token (
  id                        int(11) NOT NULL AUTO_INCREMENT,
  version                   int(11) NOT NULL,
  create_time_ms            bigint(20) NOT NULL,
  create_user               varchar(255) NOT NULL,
  update_time_ms            bigint(20) NOT NULL,
  update_user               varchar(255) NOT NULL,
  fk_account_id             int(11) NOT NULL,
  fk_token_type_id          int(11) NOT NULL,
  data                      longtext,
  expires_on_ms             bigint(20) DEFAULT NULL,
  comments                  longtext,
  enabled                   bit(1) NOT NULL,
  PRIMARY KEY               (id),
  KEY                       token_fk_account_id (fk_account_id),
  KEY                       token_fk_token_type_id (fk_token_type_id),
  CONSTRAINT token_fk_account_id FOREIGN KEY (fk_account_id) REFERENCES account (id),
  CONSTRAINT token_fk_token_type_id FOREIGN KEY (fk_token_type_id) REFERENCES token_type (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the token table
--
DROP PROCEDURE IF EXISTS copy_tokens;
DELIMITER //
CREATE PROCEDURE copy_tokens ()
BEGIN
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'OLD_tokens') THEN

        INSERT INTO token (
            id,
            version,
            create_time_ms,
            create_user,
            update_time_ms,
            update_user,
            fk_account_id,
            fk_token_type_id,
            data,
            expires_on_ms,
            comments,
            enabled)
        SELECT
            id,
            1,
            UNIX_TIMESTAMP(issued_on) * 1000,
            issued_by_user,
            IFNULL(UNIX_TIMESTAMP(updated_on) * 1000, UNIX_TIMESTAMP(issued_on) * 1000),
            IFNULL(updated_by_user, issued_by_user),
            user_id,
            token_type_id,
            token,
            UNIX_TIMESTAMP(expires_on) * 1000,
            comments,
            enabled
        FROM OLD_tokens
        WHERE id > (SELECT COALESCE(MAX(id), 0) FROM token)
        ORDER BY id;

        -- Work out what to set our auto_increment start value to
        SELECT CONCAT('ALTER TABLE token AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM token;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
    END IF;
END//
DELIMITER ;
CALL copy_tokens();
DROP PROCEDURE copy_tokens;




























--
-- Rename the old json_web_key table
--
DROP PROCEDURE IF EXISTS rename_json_web_key;
DELIMITER //
CREATE PROCEDURE rename_json_web_key ()
BEGIN
    IF NOT EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'OLD_json_web_key') THEN

        IF EXISTS (
                SELECT NULL
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_NAME = 'json_web_key') THEN

            RENAME TABLE json_web_key TO OLD_json_web_key;
        END IF;
    END IF;
END//
DELIMITER ;
CALL rename_json_web_key();
DROP PROCEDURE rename_json_web_key;

--
-- Create the json_web_key table
--
CREATE TABLE IF NOT EXISTS json_web_key (
  id                        int(11) NOT NULL AUTO_INCREMENT,
  version                   int(11) NOT NULL,
  create_time_ms            bigint(20) NOT NULL,
  create_user               varchar(255) NOT NULL,
  update_time_ms            bigint(20) NOT NULL,
  update_user               varchar(255) NOT NULL,
  fk_token_type_id          int(11) NOT NULL,
  key_id                    varchar(255) NOT NULL,
  json                      longtext,
  expires_on_ms             bigint(20) DEFAULT NULL,
  comments                  longtext,
  enabled                   bit(1) NOT NULL,
  PRIMARY KEY               (id),
  CONSTRAINT json_web_key_fk_token_type_id FOREIGN KEY (fk_token_type_id) REFERENCES token_type (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the json_web_key table
--
DROP PROCEDURE IF EXISTS copy_json_web_key;
DELIMITER //
CREATE PROCEDURE copy_json_web_key ()
BEGIN
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'OLD_json_web_key') THEN

        INSERT INTO json_web_key (
            id,
            version,
            create_time_ms,
            create_user,
            update_time_ms,
            update_user,
            fk_token_type_id,
            key_id,
            json,
            enabled)
        SELECT
            id,
            1,
            create_time_ms,
            "admin",
            create_time_ms,
            "admin",
            1,
            keyId,
            json,
            true
        FROM OLD_json_web_key
        WHERE id > (SELECT COALESCE(MAX(id), 0) FROM json_web_key)
        ORDER BY id;

        -- Work out what to set our auto_increment start value to
        SELECT CONCAT('ALTER TABLE json_web_key AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM json_web_key;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
    END IF;
END//
DELIMITER ;
CALL copy_json_web_key();
DROP PROCEDURE copy_json_web_key;











SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
