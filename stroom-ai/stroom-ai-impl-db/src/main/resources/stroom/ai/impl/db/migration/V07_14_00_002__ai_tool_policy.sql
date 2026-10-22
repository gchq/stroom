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

-- Tool policies: which of Stroom's tools each user may use, in the "Ask Stroom AI"
-- chat and over MCP, by category, read-only or read and write. A user gets the union
-- of the policies that name them, or a group they are in, or that cover all users.
-- See gh-5848.

-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

-- access: 0 = read-only, 1 = read and write (AiToolAccess).
CREATE TABLE IF NOT EXISTS ai_tool_policy (
    id                    int NOT NULL AUTO_INCREMENT,
    version               int NOT NULL,
    create_time_ms        bigint NOT NULL,
    create_user           varchar(255) NOT NULL,
    update_time_ms        bigint NOT NULL,
    update_user           varchar(255) NOT NULL,
    name                  varchar(255) NOT NULL,
    description           varchar(1000) DEFAULT NULL,
    enabled               tinyint(1) NOT NULL DEFAULT 1,
    all_users             tinyint(1) NOT NULL DEFAULT 0,
    access                tinyint NOT NULL,
    PRIMARY KEY           (id),
    UNIQUE KEY            idx_ai_tool_policy_name (name)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- The users and groups a policy covers. There is no foreign key: the DAO deletes these
-- rows along with their policy.
CREATE TABLE IF NOT EXISTS ai_tool_policy_member (
    id                    int NOT NULL AUTO_INCREMENT,
    fk_ai_tool_policy_id  int NOT NULL,
    user_uuid             varchar(255) NOT NULL,
    PRIMARY KEY           (id),
    UNIQUE KEY            idx_ai_tool_policy_member (fk_ai_tool_policy_id, user_uuid),
    KEY                   idx_ai_tool_policy_member_user (user_uuid)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- The categories a policy grants (AiToolCategory). Deleted with their policy by the DAO.
CREATE TABLE IF NOT EXISTS ai_tool_policy_category (
    id                    int NOT NULL AUTO_INCREMENT,
    fk_ai_tool_policy_id  int NOT NULL,
    category              tinyint NOT NULL,
    PRIMARY KEY           (id),
    UNIQUE KEY            idx_ai_tool_policy_category (fk_ai_tool_policy_id, category)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Policies govern the chat as well as MCP, so without one no chat user would have any
-- tools after the upgrade. Everyone keeps the tools that only read, in every category;
-- tools that change things need a policy an administrator writes.
INSERT INTO ai_tool_policy (
    version, create_time_ms, create_user, update_time_ms, update_user,
    name, description, enabled, all_users, access)
SELECT
    1,
    UNIX_TIMESTAMP() * 1000,
    'Flyway migration',
    UNIX_TIMESTAMP() * 1000,
    'Flyway migration',
    'Everyone: read-only tools',
    'Made by the upgrade so that every user keeps the tools that only read. Tools that change things need '
        'a policy that grants read and write.',
    1,
    1,
    0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_tool_policy);

INSERT INTO ai_tool_policy_category (fk_ai_tool_policy_id, category)
SELECT p.id, c.category
FROM ai_tool_policy p
CROSS JOIN (
    SELECT 0 AS category UNION ALL
    SELECT 1 UNION ALL
    SELECT 2 UNION ALL
    SELECT 3 UNION ALL
    SELECT 4 UNION ALL
    SELECT 5) c
WHERE p.name = 'Everyone: read-only tools'
AND NOT EXISTS (
    SELECT 1
    FROM ai_tool_policy_category pc
    WHERE pc.fk_ai_tool_policy_id = p.id);

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
