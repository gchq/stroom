-- ------------------------------------------------------------------------
-- Copyright 2026 Crown Copyright
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

-- A session of an MCP client on Stroom's /mcp endpoint has a workspace, as a chat does.
-- It is held by a hidden chat whose mcp_session_id is the session's id: such chats are
-- never listed with the user's chats, and are deleted when their session ends.
-- See gh-5848.

-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

DROP PROCEDURE IF EXISTS V07_14_00_003__ai_add_column;
DROP PROCEDURE IF EXISTS V07_14_00_003__ai_add_index;

DELIMITER $$

CREATE PROCEDURE V07_14_00_003__ai_add_column (
    IN p_table_name varchar(64),
    IN p_column_name varchar(64),
    IN p_column_definition varchar(255))
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
      AND table_name = p_table_name
      AND column_name = p_column_name;

    IF object_count = 0 THEN
        SET @sql_str = CONCAT(
            'ALTER TABLE ', p_table_name, ' ADD COLUMN ', p_column_name, ' ', p_column_definition);
        PREPARE stmt FROM @sql_str;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

CREATE PROCEDURE V07_14_00_003__ai_add_index (
    IN p_table_name varchar(64),
    IN p_index_name varchar(64),
    IN p_index_columns varchar(255))
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.statistics
    WHERE table_schema = database()
      AND table_name = p_table_name
      AND index_name = p_index_name;

    IF object_count = 0 THEN
        SET @sql_str = CONCAT(
            'CREATE INDEX ', p_index_name, ' ON ', p_table_name, ' (', p_index_columns, ')');
        PREPARE stmt FROM @sql_str;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DELIMITER ;

CALL V07_14_00_003__ai_add_column('ai_chat', 'mcp_session_id', 'varchar(255) DEFAULT NULL');
CALL V07_14_00_003__ai_add_index('ai_chat', 'idx_ai_chat_mcp_session_id', 'mcp_session_id');

DROP PROCEDURE IF EXISTS V07_14_00_003__ai_add_column;
DROP PROCEDURE IF EXISTS V07_14_00_003__ai_add_index;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
