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

-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

--
-- Create the ai_chat table
--
CREATE TABLE IF NOT EXISTS ai_chat (
    id                    int NOT NULL AUTO_INCREMENT,
    version               int NOT NULL,
    create_time_ms        bigint NOT NULL,
    create_user           varchar(255) NOT NULL,
    update_time_ms        bigint NOT NULL,
    update_user           varchar(255) NOT NULL,
    user_uuid             varchar(255) NOT NULL,
    title                 varchar(255) NOT NULL,
    PRIMARY KEY           (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

--
-- Create the ai_chat_attachment table
--
CREATE TABLE IF NOT EXISTS ai_chat_attachment (
    id                    int NOT NULL AUTO_INCREMENT,
    fk_ai_chat_id         int NOT NULL,
    create_time_ms        bigint NOT NULL,
    update_time_ms        bigint NOT NULL,
    status                int NOT NULL,
    attachment_type       int NOT NULL,
    description           varchar(255),
    context_json          longtext,
    data_markdown         longtext,
    row_count             int,
    error_message         varchar(1024),
    PRIMARY KEY           (id),
    CONSTRAINT fk_ai_chat_attachment_chat
    FOREIGN KEY (fk_ai_chat_id) REFERENCES ai_chat (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

--
-- Create the ai_chat_message table
--
CREATE TABLE IF NOT EXISTS ai_chat_message (
    id                    int NOT NULL AUTO_INCREMENT,
    fk_ai_chat_id         int NOT NULL,
    create_time_ms        bigint NOT NULL,
    message_type          int NOT NULL,
    fk_attachment_id      int DEFAULT NULL,
    message               longtext NOT NULL,
    PRIMARY KEY           (id),
    CONSTRAINT fk_ai_chat_message_chat
    FOREIGN KEY (fk_ai_chat_id) REFERENCES ai_chat (id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_chat_message_attachment
    FOREIGN KEY (fk_attachment_id) REFERENCES ai_chat_attachment (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX idx_ai_chat_user_uuid ON ai_chat (user_uuid);
CREATE INDEX idx_ai_chat_attachment_chat_id ON ai_chat_attachment (fk_ai_chat_id);
CREATE INDEX idx_ai_chat_message_chat_id ON ai_chat_message (fk_ai_chat_id);


SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set tabstop=4 shiftwidth=4 expandtab:
