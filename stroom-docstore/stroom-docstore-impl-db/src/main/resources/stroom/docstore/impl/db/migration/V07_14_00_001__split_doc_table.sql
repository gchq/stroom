--
-- Copyright 2016-2025 Crown Copyright
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
--

-- ============================================================================
-- Split doc table into doc (identity) + doc_data (typed content) +
-- doc_audit (operation trail) + doc_data_snapshot (deduplicated snapshots) +
-- doc_audit_data_snapshot (audit-to-snapshot links)
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Step 1: Create doc_data table with sparse typed columns
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_data (
    id        bigint NOT NULL AUTO_INCREMENT,
    fk_doc_id bigint NOT NULL,
    ext       varchar(255) NOT NULL,
    data_type tinyint NOT NULL,
    json_data json,
    text_data longtext,
    bin_data  longblob,
    PRIMARY KEY (id),
    UNIQUE KEY doc_data_fk_doc_id_ext_idx (fk_doc_id, ext),
    CONSTRAINT doc_data_fk_doc_id FOREIGN KEY (fk_doc_id) REFERENCES doc (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- Step 2a: Migrate VALID meta JSON -> doc_data.json_data
-- The meta row references itself (fk_doc_id = doc.id where ext = 'meta')
-- ---------------------------------------------------------------------------
INSERT INTO doc_data (fk_doc_id, ext, data_type, json_data)
SELECT id, ext, 1, CAST(CONVERT(data USING utf8mb4) AS JSON)
FROM doc
WHERE ext = 'meta'
  AND data IS NOT NULL
  AND CONVERT(data USING utf8mb4) IS NOT NULL
  AND JSON_VALID(CONVERT(data USING utf8mb4)) = 1;

-- ---------------------------------------------------------------------------
-- Step 2b: Migrate INVALID meta rows -> doc_data.text_data as fallback
-- These rows had data that could not be parsed as JSON.
-- Stored as text for manual inspection and correction.
-- ---------------------------------------------------------------------------
INSERT INTO doc_data (fk_doc_id, ext, data_type, text_data)
SELECT id, ext, 2, CONVERT(data USING utf8mb4)
FROM doc
WHERE ext = 'meta'
  AND data IS NOT NULL
  AND (CONVERT(data USING utf8mb4) IS NULL
       OR JSON_VALID(CONVERT(data USING utf8mb4)) = 0);

-- ---------------------------------------------------------------------------
-- Step 3a: Migrate VALID content JSON -> doc_data.json_data
-- These are non-meta rows with ext = 'json', joined to the meta row
-- ---------------------------------------------------------------------------
INSERT INTO doc_data (fk_doc_id, ext, data_type, json_data)
SELECT dm.id, d.ext, 1, CAST(CONVERT(d.data USING utf8mb4) AS JSON)
FROM doc d
JOIN doc dm ON dm.type = d.type AND dm.uuid = d.uuid AND dm.ext = 'meta'
WHERE d.ext = 'json'
  AND d.data IS NOT NULL
  AND CONVERT(d.data USING utf8mb4) IS NOT NULL
  AND JSON_VALID(CONVERT(d.data USING utf8mb4)) = 1;

-- ---------------------------------------------------------------------------
-- Step 3b: Migrate INVALID content JSON rows -> doc_data.text_data as fallback
-- ---------------------------------------------------------------------------
INSERT INTO doc_data (fk_doc_id, ext, data_type, text_data)
SELECT dm.id, d.ext, 2, CONVERT(d.data USING utf8mb4)
FROM doc d
JOIN doc dm ON dm.type = d.type AND dm.uuid = d.uuid AND dm.ext = 'meta'
WHERE d.ext = 'json'
  AND d.data IS NOT NULL
  AND (CONVERT(d.data USING utf8mb4) IS NULL
       OR JSON_VALID(CONVERT(d.data USING utf8mb4)) = 0);

-- ---------------------------------------------------------------------------
-- Step 4: Migrate text content -> doc_data.text_data
-- ---------------------------------------------------------------------------
INSERT INTO doc_data (fk_doc_id, ext, data_type, text_data)
SELECT dm.id, d.ext, 2, CONVERT(d.data USING utf8mb4)
FROM doc d
JOIN doc dm ON dm.type = d.type AND dm.uuid = d.uuid AND dm.ext = 'meta'
WHERE d.ext IN ('xsl', 'xsd', 'xml', 'js', 'txt');

-- ---------------------------------------------------------------------------
-- Step 5: Migrate remaining content -> doc_data.bin_data
-- ---------------------------------------------------------------------------
INSERT INTO doc_data (fk_doc_id, ext, data_type, bin_data)
SELECT dm.id, d.ext, 3, d.data
FROM doc d
JOIN doc dm ON dm.type = d.type AND dm.uuid = d.uuid AND dm.ext = 'meta'
WHERE d.ext NOT IN ('meta', 'json', 'xsl', 'xsd', 'xml', 'js', 'txt')
  AND d.ext IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Step 6: Delete all non-meta rows from doc
-- ---------------------------------------------------------------------------
DELETE FROM doc WHERE ext != 'meta' OR ext IS NULL;

-- ---------------------------------------------------------------------------
-- Step 7: Restructure doc table - drop data/ext, add deleted, update indexes
-- ---------------------------------------------------------------------------
ALTER TABLE doc DROP KEY doc_type_uuid_ext_idx;
ALTER TABLE doc DROP KEY doc_type_uuid_idx;
ALTER TABLE doc DROP KEY doc_uuid_idx;
ALTER TABLE doc DROP KEY doc_type_name_uuid_idx;
ALTER TABLE doc DROP COLUMN data;
ALTER TABLE doc DROP COLUMN ext;
ALTER TABLE doc ADD COLUMN deleted bigint DEFAULT NULL;
ALTER TABLE doc ADD UNIQUE KEY doc_uuid_idx (uuid);
ALTER TABLE doc ADD KEY doc_type_name_uuid_idx (type, name, uuid);
ALTER TABLE doc ADD KEY doc_deleted_idx (deleted);

-- ---------------------------------------------------------------------------
-- Step 8: Create doc_audit table
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_audit (
    id          bigint NOT NULL AUTO_INCREMENT,
    fk_doc_id   bigint NOT NULL,
    action      tinyint NOT NULL,
    action_time bigint NOT NULL,
    user_uuid   varchar(255) DEFAULT NULL,
    user_name   varchar(255) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY doc_audit_fk_doc_id_idx (fk_doc_id),
    KEY doc_audit_action_time_idx (action_time),
    CONSTRAINT doc_audit_fk_doc_id FOREIGN KEY (fk_doc_id) REFERENCES doc (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- Step 9: Create doc_data_snapshot table
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_data_snapshot (
    id        bigint NOT NULL AUTO_INCREMENT,
    fk_doc_id bigint NOT NULL,
    ext       varchar(255) NOT NULL,
    data_type tinyint NOT NULL,
    data_hash bigint NOT NULL,
    json_data json,
    text_data longtext,
    bin_data  longblob,
    PRIMARY KEY (id),
    KEY doc_data_snapshot_dedup_idx (fk_doc_id, ext, data_hash),
    KEY doc_data_snapshot_fk_doc_id_idx (fk_doc_id),
    CONSTRAINT doc_data_snapshot_fk_doc_id FOREIGN KEY (fk_doc_id) REFERENCES doc (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- Step 10: Create doc_audit_data_snapshot link table
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_audit_data_snapshot (
    id                      bigint NOT NULL AUTO_INCREMENT,
    fk_doc_audit_id         bigint NOT NULL,
    fk_doc_data_snapshot_id bigint NOT NULL,
    PRIMARY KEY (id),
    KEY doc_audit_data_snapshot_fk_doc_audit_id_idx (fk_doc_audit_id),
    KEY doc_audit_data_snapshot_fk_doc_data_snapshot_id_idx (fk_doc_data_snapshot_id),
    CONSTRAINT doc_audit_data_snapshot_fk_doc_audit_id
        FOREIGN KEY (fk_doc_audit_id) REFERENCES doc_audit (id),
    CONSTRAINT doc_audit_data_snapshot_fk_doc_data_snapshot_id
        FOREIGN KEY (fk_doc_data_snapshot_id) REFERENCES doc_data_snapshot (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- Step 11: Seed initial audit entries from existing meta JSON
-- The user_uuid is NULL because the current meta JSON only stores user
-- display names, not their UUIDs.
-- ---------------------------------------------------------------------------

-- CREATE entry from createTimeMs / createUser
INSERT INTO doc_audit (fk_doc_id, action, action_time, user_uuid, user_name)
SELECT d.id, 1,
       COALESCE(JSON_VALUE(dd.json_data, '$.createTimeMs' RETURNING SIGNED), 0),
       NULL,
       JSON_VALUE(dd.json_data, '$.createUser' RETURNING CHAR(255))
FROM doc d
JOIN doc_data dd ON dd.fk_doc_id = d.id AND dd.ext = 'meta'
WHERE dd.json_data IS NOT NULL;

-- UPDATE entry from updateTimeMs / updateUser (only if updateTimeMs is set)
INSERT INTO doc_audit (fk_doc_id, action, action_time, user_uuid, user_name)
SELECT d.id, 2,
       COALESCE(JSON_VALUE(dd.json_data, '$.updateTimeMs' RETURNING SIGNED), 0),
       NULL,
       JSON_VALUE(dd.json_data, '$.updateUser' RETURNING CHAR(255))
FROM doc d
JOIN doc_data dd ON dd.fk_doc_id = d.id AND dd.ext = 'meta'
WHERE dd.json_data IS NOT NULL
  AND JSON_VALUE(dd.json_data, '$.updateTimeMs' RETURNING SIGNED) IS NOT NULL;
