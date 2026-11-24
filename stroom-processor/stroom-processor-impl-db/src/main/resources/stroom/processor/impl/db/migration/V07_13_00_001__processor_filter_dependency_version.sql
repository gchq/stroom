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

-- Records which version of the dependency extraction logic last wrote each
-- processor filter's doc_dependency edges. NULL means the edges were never
-- written by the current code (e.g. they came from
-- V07_13_00_005__populate_doc_dependency_processor_filters, which recorded
-- legacy stream store data sources and kept edges for deleted filters), so the
-- ProcessorFilterDependencyRebuildStartup task rebuilds them on the next start.
-- This mirrors doc.dependency_version for documents.

-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

DROP PROCEDURE IF EXISTS V07_13_00_001__processor_filter_dependency_version;

DELIMITER $$

CREATE PROCEDURE V07_13_00_001__processor_filter_dependency_version ()
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'processor_filter'
    AND column_name = 'dependency_version';

    IF object_count = 0 THEN
        ALTER TABLE processor_filter ADD COLUMN dependency_version int DEFAULT NULL;
    END IF;
END $$

DELIMITER ;

CALL V07_13_00_001__processor_filter_dependency_version;

DROP PROCEDURE IF EXISTS V07_13_00_001__processor_filter_dependency_version;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
