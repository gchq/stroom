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

-- The sample files upload_sample has given commands for, waiting to be posted to
-- /mcp/upload. Held here, not in a node's memory, so that any node takes the post
-- and a restart doesn't forget them. Expired sets, and those of deleted users, are
-- deleted by the 'AI Chat Retention' job.
-- See gh-5848.

-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

-- A set of upload commands: what they were given for. token is the id in the
-- commands' URL; headers are the receipt headers, as a JSON object.
CREATE TABLE IF NOT EXISTS ai_sample_upload (
    id                    int NOT NULL AUTO_INCREMENT,
    token                 varchar(32) NOT NULL,
    user_uuid             varchar(255) NOT NULL,
    feed_name             varchar(255) NOT NULL,
    feed_uuid             varchar(255) NOT NULL,
    stream_type           varchar(255) NOT NULL,
    headers               longtext NOT NULL,
    build                 varchar(255) DEFAULT NULL,
    create_time_ms        bigint NOT NULL,
    expiry_time_ms        bigint NOT NULL,
    PRIMARY KEY           (id),
    UNIQUE KEY            idx_ai_sample_upload_token (token),
    KEY                   idx_ai_sample_upload_user_uuid (user_uuid),
    KEY                   idx_ai_sample_upload_expiry_time_ms (expiry_time_ms)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- A file of a set, posted once. claim_node and claim_time_ms are set by the node
-- storing it while it does (a stale claim, of a node that stopped mid-upload, may be
-- taken again); stored_time_ms once it is stored, with its stream if it is visible.
-- There is no foreign key: the DAO deletes these rows along with their set.
CREATE TABLE IF NOT EXISTS ai_sample_upload_file (
    id                    int NOT NULL AUTO_INCREMENT,
    fk_ai_sample_upload_id int NOT NULL,
    name                  varchar(700) NOT NULL,
    claim_node            varchar(255) DEFAULT NULL,
    claim_time_ms         bigint DEFAULT NULL,
    stored_time_ms        bigint DEFAULT NULL,
    stored_stream_id      bigint DEFAULT NULL,
    PRIMARY KEY           (id),
    UNIQUE KEY            idx_ai_sample_upload_file_name (fk_ai_sample_upload_id, name)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
