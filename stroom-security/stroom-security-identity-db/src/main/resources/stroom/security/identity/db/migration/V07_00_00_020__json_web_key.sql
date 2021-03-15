-- ------------------------------------------------------------------------
-- Copyright 2021 Crown Copyright
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
-- Create the json_web_key table
--
CREATE TABLE IF NOT EXISTS json_web_key (
    id                int NOT NULL AUTO_INCREMENT,
    version           int NOT NULL,
    create_time_ms    bigint NOT NULL,
    create_user       varchar(255) NOT NULL,
    update_time_ms    bigint NOT NULL,
    update_user       varchar(255) NOT NULL,
    fk_token_type_id  int NOT NULL,
    key_id            varchar(255) NOT NULL,
    json              longtext,
    expires_on_ms     bigint DEFAULT NULL,
    comments          longtext,
    enabled           tinyint(1) NOT NULL DEFAULT '0',
    PRIMARY KEY (id),
    KEY json_web_key_fk_token_type_id (fk_token_type_id),
    CONSTRAINT json_web_key_fk_token_type_id
        FOREIGN KEY (fk_token_type_id)
        REFERENCES token_type (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP PROCEDURE IF EXISTS identity_copy_old_auth_json_web_key;

DELIMITER //

--
-- Copy data into the json_web_key table from legacy v6 tables
-- This relies on a pre-migration script renaming v6 auth tbls
-- to OLD_AUTH_
--
CREATE PROCEDURE identity_copy_old_auth_json_web_key ()
BEGIN
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = database()
            AND TABLE_NAME = 'OLD_AUTH_json_web_key') THEN

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
            UNIX_TIMESTAMP() * 1000,
            "Flyway migration",
            UNIX_TIMESTAMP() * 1000,
            "Flyway migration",
            1,
            keyId,
            json,
            true
        FROM OLD_AUTH_json_web_key
        WHERE id > (SELECT COALESCE(MAX(id), 0) FROM json_web_key)
        ORDER BY id;

        -- Work out what to set our auto_increment start value to
        SELECT
            CONCAT(
                'ALTER TABLE json_web_key AUTO_INCREMENT = ',
                COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM json_web_key;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
    END IF;
END//

DELIMITER ;

-- idempotent
CALL identity_copy_old_auth_json_web_key();

DROP PROCEDURE IF EXISTS identity_copy_old_auth_json_web_key;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
