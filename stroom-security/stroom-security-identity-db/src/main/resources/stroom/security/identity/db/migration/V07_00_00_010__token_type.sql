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
-- Create the token_type table
--
CREATE TABLE IF NOT EXISTS token_type (
  id    int NOT NULL AUTO_INCREMENT,
  type  varchar(255) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

DROP PROCEDURE IF EXISTS identity_copy_old_auth_token_types;

DELIMITER //

--
-- Copy data into the token_type table
-- This relies on a pre-migration script renaming v6 auth tbls
-- to OLD_AUTH_
--
CREATE PROCEDURE identity_copy_old_auth_token_types ()
BEGIN
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = database()
            AND TABLE_NAME = 'OLD_AUTH_token_types') THEN

        INSERT INTO token_type (
            id,
            type)
        SELECT
            id,
            token_type
        FROM OLD_AUTH_token_types
        WHERE id > (SELECT COALESCE(MAX(id), 0) FROM token_type)
        ORDER BY id;

        -- Work out what to set our auto_increment start value to
        SELECT
        CONCAT(
            'ALTER TABLE token_type AUTO_INCREMENT = ',
            COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM token_type;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
    END IF;
END//

DELIMITER ;

-- idempotent
CALL identity_copy_old_auth_token_types();

DROP PROCEDURE IF EXISTS identity_copy_old_auth_token_types;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=2 tabstop=2 expandtab:
