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
-- Tracks document-to-document dependency edges.
-- Each row means: the doc identified by (from_type, from_uuid) depends on
-- (to_type, to_uuid).
--
-- Uses bare UUIDs (no FK to doc table) because:
--   - Dependency targets may reference docs that don't exist yet (broken deps)
--   - Pseudo-refs (e.g. Annotations, SearchableIndex) are not in the doc table
--   - Non-doc entities (e.g. ProcessorFilter) are not in the doc table
-- ============================================================================

-- stop note level warnings about objects (not)? existing
SET @old_sql_notes=@@sql_notes, sql_notes=0;

CREATE TABLE IF NOT EXISTS doc_dependency (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    from_type   VARCHAR(255) NOT NULL,
    from_uuid   VARCHAR(255) NOT NULL,
    from_name   VARCHAR(255) NOT NULL DEFAULT '',
    to_type     VARCHAR(255) NOT NULL,
    to_uuid     VARCHAR(255) NOT NULL,
    to_name     VARCHAR(255) NOT NULL DEFAULT '',
    PRIMARY KEY (id),
    -- A given (from, to) edge should be unique
    UNIQUE KEY  doc_dependency_from_to (from_uuid, to_uuid),
    -- Query pattern: "what does doc X depend on?"
    KEY         doc_dependency_from_uuid (from_uuid),
    -- Query pattern: "what depends on doc X?" (for safe-delete, dependants view)
    KEY         doc_dependency_to_uuid (to_uuid)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Reset to the original value
SET sql_notes=@old_sql_notes;
