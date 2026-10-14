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

-- Ask Stroom AI: agent runs, chat memory, workspace files, tool approvals and the
-- columns/indexes needed for turn grouping, context tracking and the session switcher.
-- See gh-5848.

-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

-- --------------------------------------------------
-- Helper procedures, dropped again at the end
-- --------------------------------------------------

DROP PROCEDURE IF EXISTS V07_14_00_001__ai_add_column;
DROP PROCEDURE IF EXISTS V07_14_00_001__ai_add_index;
DROP PROCEDURE IF EXISTS V07_14_00_001__ai_drop_index;

DELIMITER $$

CREATE PROCEDURE V07_14_00_001__ai_add_column (
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

CREATE PROCEDURE V07_14_00_001__ai_add_index (
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

CREATE PROCEDURE V07_14_00_001__ai_drop_index (
    IN p_table_name varchar(64),
    IN p_index_name varchar(64))
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.statistics
    WHERE table_schema = database()
      AND table_name = p_table_name
      AND index_name = p_index_name;

    IF object_count > 0 THEN
        SET @sql_str = CONCAT('DROP INDEX ', p_index_name, ' ON ', p_table_name);
        PREPARE stmt FROM @sql_str;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DELIMITER ;

-- --------------------------------------------------
-- ai_chat
-- --------------------------------------------------

-- The model doc last used by the chat, and its context window, so the context
-- indicator can be shown as soon as a chat is opened.
CALL V07_14_00_001__ai_add_column('ai_chat', 'model_doc_uuid', 'varchar(255) DEFAULT NULL');
CALL V07_14_00_001__ai_add_column('ai_chat', 'context_window_tokens', 'int DEFAULT NULL');
-- The input token count the provider reported for the last model call.
CALL V07_14_00_001__ai_add_column('ai_chat', 'last_input_tokens', 'int DEFAULT NULL');
-- Running totals of tokens used by the chat.
CALL V07_14_00_001__ai_add_column('ai_chat', 'total_input_tokens', 'bigint NOT NULL DEFAULT 0');
CALL V07_14_00_001__ai_add_column('ai_chat', 'total_output_tokens', 'bigint NOT NULL DEFAULT 0');
-- When the user last opened the chat, used to restore the panel on the most recent chat.
CALL V07_14_00_001__ai_add_column('ai_chat', 'last_opened_ms', 'bigint DEFAULT NULL');
-- The last message the user has seen, for unread badges in the session switcher.
CALL V07_14_00_001__ai_add_column('ai_chat', 'last_read_message_id', 'int DEFAULT NULL');
-- When a browser last polled the chat, used to tell whether the user is still present.
CALL V07_14_00_001__ai_add_column('ai_chat', 'last_poll_ms', 'bigint DEFAULT NULL');
-- Whether the user has turned on 'approve everything' for this chat (only possible
-- when an admin has enabled it).
CALL V07_14_00_001__ai_add_column('ai_chat', 'auto_approve', 'tinyint(1) NOT NULL DEFAULT 0');

-- Matches the list chats query, which filters by user and sorts by update time.
CALL V07_14_00_001__ai_add_index('ai_chat', 'idx_ai_chat_user_uuid_update_time', 'user_uuid, update_time_ms');
CALL V07_14_00_001__ai_drop_index('ai_chat', 'idx_ai_chat_user_uuid');

-- --------------------------------------------------
-- ai_chat_message
-- --------------------------------------------------

-- The id of the USER_MESSAGE row that started the turn the message belongs to,
-- or 0 if the message came before any question (e.g. an attachment).
CALL V07_14_00_001__ai_add_column('ai_chat_message', 'turn_id', 'int NOT NULL DEFAULT 0');
-- An estimate of the number of tokens in the message.
CALL V07_14_00_001__ai_add_column('ai_chat_message', 'token_count', 'int DEFAULT NULL');

-- Messages are read in id order, by chat, and by type within a chat. The new indexes are
-- created before the old one is dropped, as the foreign key needs an index on fk_ai_chat_id.
CALL V07_14_00_001__ai_add_index('ai_chat_message', 'idx_ai_chat_message_chat_id_id', 'fk_ai_chat_id, id');
CALL V07_14_00_001__ai_add_index(
    'ai_chat_message', 'idx_ai_chat_message_chat_id_type_id', 'fk_ai_chat_id, message_type, id');
CALL V07_14_00_001__ai_drop_index('ai_chat_message', 'idx_ai_chat_message_chat_id');

-- Give existing messages their turn. A temporary table is used because MySQL won't let an
-- UPDATE select from the table it is updating.
DROP TEMPORARY TABLE IF EXISTS tmp_ai_chat_message_turn;

CREATE TEMPORARY TABLE tmp_ai_chat_message_turn AS
SELECT
    m.id,
    (
        SELECT MAX(u.id)
        FROM ai_chat_message u
        WHERE u.fk_ai_chat_id = m.fk_ai_chat_id
          AND u.message_type = 0
          AND u.id <= m.id
    ) AS turn_id
FROM ai_chat_message m
WHERE m.turn_id = 0;

UPDATE ai_chat_message m
INNER JOIN tmp_ai_chat_message_turn t ON t.id = m.id
SET m.turn_id = COALESCE(t.turn_id, 0);

DROP TEMPORARY TABLE IF EXISTS tmp_ai_chat_message_turn;

-- --------------------------------------------------
-- ai_chat_attachment (also the index of workspace files)
-- --------------------------------------------------

-- The file's path within the chat workspace, e.g. /outputs/q1.csv.
CALL V07_14_00_001__ai_add_column('ai_chat_attachment', 'path', 'varchar(512) DEFAULT NULL');
-- The uncompressed size of the file.
CALL V07_14_00_001__ai_add_column('ai_chat_attachment', 'size_bytes', 'bigint DEFAULT NULL');
-- The tool call message that produced the file, if any.
CALL V07_14_00_001__ai_add_column('ai_chat_attachment', 'fk_source_message_id', 'int DEFAULT NULL');
-- The data source (dashboard, query or data source doc) the content came from, so the
-- user's access can be checked again before the file is used.
CALL V07_14_00_001__ai_add_column('ai_chat_attachment', 'source_doc_uuid', 'varchar(255) DEFAULT NULL');
-- When the file expires and is deleted.
CALL V07_14_00_001__ai_add_column('ai_chat_attachment', 'expire_time_ms', 'bigint DEFAULT NULL');

CALL V07_14_00_001__ai_add_index('ai_chat_attachment', 'idx_ai_chat_attachment_expire_time', 'expire_time_ms');

-- --------------------------------------------------
-- ai_chat_file_chunk
-- --------------------------------------------------

-- The content of workspace files, in zstd compressed chunks of about 1MB of text, so any
-- node can read any chat's files.
CREATE TABLE IF NOT EXISTS ai_chat_file_chunk (
    id                    int NOT NULL AUTO_INCREMENT,
    fk_attachment_id      int NOT NULL,
    seq                   int NOT NULL,
    first_line            bigint NOT NULL,
    line_count            int NOT NULL,
    raw_size              int NOT NULL,
    data                  mediumblob NOT NULL,
    PRIMARY KEY           (id),
    UNIQUE KEY            idx_ai_chat_file_chunk_attachment_seq (fk_attachment_id, seq),
    CONSTRAINT fk_ai_chat_file_chunk_attachment
    FOREIGN KEY (fk_attachment_id) REFERENCES ai_chat_attachment (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- --------------------------------------------------
-- ai_memory
-- --------------------------------------------------

-- Agent memory. USER scope is loaded into every chat, CHAT scope only into its chat
-- (scope_key is the chat id), and DOC scope is reserved for memory about a document.
-- There is no foreign key, the DAO deletes CHAT scope rows along with their chat.
CREATE TABLE IF NOT EXISTS ai_memory (
    id                    int NOT NULL AUTO_INCREMENT,
    version               int NOT NULL,
    create_time_ms        bigint NOT NULL,
    create_user           varchar(255) NOT NULL,
    update_time_ms        bigint NOT NULL,
    update_user           varchar(255) NOT NULL,
    user_uuid             varchar(255) NOT NULL,
    scope                 tinyint NOT NULL,
    scope_key             varchar(255) NOT NULL DEFAULT '',
    name                  varchar(255) NOT NULL,
    content               mediumtext NOT NULL,
    PRIMARY KEY           (id),
    UNIQUE KEY            idx_ai_memory_scope_name (user_uuid, scope, scope_key, name)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- --------------------------------------------------
-- ai_chat_run
-- --------------------------------------------------

-- One row per agent run. Status changes are made with compare-and-set updates.
CREATE TABLE IF NOT EXISTS ai_chat_run (
    id                    int NOT NULL AUTO_INCREMENT,
    version               int NOT NULL,
    create_time_ms        bigint NOT NULL,
    update_time_ms        bigint NOT NULL,
    fk_ai_chat_id         int NOT NULL,
    user_uuid             varchar(255) NOT NULL,
    status                tinyint NOT NULL,
    stop_reason           tinyint DEFAULT NULL,
    node_name             varchar(255) DEFAULT NULL,
    session_id_hash       varchar(64) DEFAULT NULL,
    unattended            tinyint(1) NOT NULL DEFAULT 0,
    heartbeat_ms          bigint DEFAULT NULL,
    start_time_ms         bigint DEFAULT NULL,
    end_time_ms           bigint DEFAULT NULL,
    input_tokens          bigint NOT NULL DEFAULT 0,
    output_tokens         bigint NOT NULL DEFAULT 0,
    tool_call_count       int NOT NULL DEFAULT 0,
    error_message         varchar(1024) DEFAULT NULL,
    PRIMARY KEY           (id),
    INDEX                 idx_ai_chat_run_chat_status (fk_ai_chat_id, status),
    INDEX                 idx_ai_chat_run_user_status (user_uuid, status),
    INDEX                 idx_ai_chat_run_status_heartbeat (status, heartbeat_ms),
    CONSTRAINT fk_ai_chat_run_chat
    FOREIGN KEY (fk_ai_chat_id) REFERENCES ai_chat (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- --------------------------------------------------
-- ai_tool_approval
-- --------------------------------------------------

-- Tool approvals that last for a chat ('allow in this chat'). tool_name is '*' for
-- 'allow this server's read-only tools in this chat'. tool_hash covers the tool's name
-- and description, so a changed tool is asked about again. There is no foreign key,
-- the DAO deletes these rows along with their chat.
CREATE TABLE IF NOT EXISTS ai_tool_approval (
    id                    int NOT NULL AUTO_INCREMENT,
    version               int NOT NULL,
    create_time_ms        bigint NOT NULL,
    create_user           varchar(255) NOT NULL,
    update_time_ms        bigint NOT NULL,
    update_user           varchar(255) NOT NULL,
    user_uuid             varchar(255) NOT NULL,
    fk_ai_chat_id         int NOT NULL,
    server_key            varchar(255) NOT NULL,
    tool_name             varchar(255) NOT NULL,
    tool_hash             varchar(64) NOT NULL,
    decision              tinyint NOT NULL,
    PRIMARY KEY           (id),
    UNIQUE KEY            idx_ai_tool_approval_chat_tool (fk_ai_chat_id, server_key, tool_name)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- --------------------------------------------------
-- Tidy up
-- --------------------------------------------------

DROP PROCEDURE IF EXISTS V07_14_00_001__ai_add_column;
DROP PROCEDURE IF EXISTS V07_14_00_001__ai_add_index;
DROP PROCEDURE IF EXISTS V07_14_00_001__ai_drop_index;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
