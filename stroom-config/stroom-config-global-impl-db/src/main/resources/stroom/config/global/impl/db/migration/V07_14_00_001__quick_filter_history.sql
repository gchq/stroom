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
-- The filters a user has recently typed into each quick filter.
--
-- One row per (user, context, data source, filter text); the unique key is what
-- de-duplicates. Under the table's default _ai_ci collation that de-duplication is
-- case-insensitive, matching the parser, which ignores case on qualifiers and on most
-- conditions.
--
-- data_source_uuid is NOT NULL DEFAULT '' rather than nullable because MySQL does not treat
-- NULLs as equal in a unique key, so a surface with no data source stores the empty string.
--
-- Column widths keep the unique key under InnoDB's 3072-byte index limit for utf8mb4:
-- (255 + 64 + 36 + 400) * 4 = 3020.
--
CREATE TABLE IF NOT EXISTS quick_filter_history (
    id                    int NOT NULL AUTO_INCREMENT,
    user_uuid             varchar(255) NOT NULL,
    context               varchar(64) NOT NULL,
    data_source_uuid      varchar(36) NOT NULL DEFAULT '',
    filter_text           varchar(400) NOT NULL,
    last_used_ms          bigint NOT NULL,
    use_count             int NOT NULL DEFAULT 1,
    PRIMARY KEY           (id),
    UNIQUE KEY            quick_filter_history_user_ctx_ds_text_idx (user_uuid, context, data_source_uuid, filter_text),
    KEY                   quick_filter_history_user_ctx_ds_used_idx (user_uuid, context, data_source_uuid, last_used_ms)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

SET SQL_NOTES=@OLD_SQL_NOTES;
