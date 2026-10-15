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

-- Ask Stroom AI: the agent mode of each chat (Agent, Ask or Plan), so that a run carried on after the user
-- answers an approval or a form, or is resumed, works in the mode the chat is in. See gh-5848.

-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

DROP PROCEDURE IF EXISTS V07_14_00_002__ai_add_column;

DELIMITER $$

CREATE PROCEDURE V07_14_00_002__ai_add_column (
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

-- 0 = Agent (the default, as chats were before), 1 = Ask, 2 = Plan.
CALL V07_14_00_002__ai_add_column('ai_chat', 'agent_mode', 'tinyint NOT NULL DEFAULT 0');

DROP PROCEDURE IF EXISTS V07_14_00_002__ai_add_column;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
