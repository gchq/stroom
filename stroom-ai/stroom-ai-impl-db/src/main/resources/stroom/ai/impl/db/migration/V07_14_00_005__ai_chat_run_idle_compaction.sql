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

-- A chat left idle is compacted ahead of the user's return, in a run of its own.
-- Such a run is marked, so that any node can tell a question asked meanwhile to
-- wait for it rather than steer it, and so that the chat is not compacted again
-- while idle once one has been started since its last message (e.g. one the user
-- stopped). See gh-5848.

-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

DROP PROCEDURE IF EXISTS V07_14_00_005__ai_add_column;

DELIMITER $$

CREATE PROCEDURE V07_14_00_005__ai_add_column (
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

DELIMITER ;

-- 1 if the run compacts a chat that was left idle, rather than answering a question.
CALL V07_14_00_005__ai_add_column('ai_chat_run', 'idle_compaction', 'tinyint(1) NOT NULL DEFAULT 0');

DROP PROCEDURE IF EXISTS V07_14_00_005__ai_add_column;

SET SQL_NOTES=@OLD_SQL_NOTES;
