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

DROP PROCEDURE IF EXISTS V07_14_00_004__processor_filter_parent_filter_id;

DELIMITER $$

CREATE PROCEDURE V07_14_00_004__processor_filter_parent_filter_id ()
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'processor_filter'
    AND column_name = 'parent_filter_id';

    IF object_count = 0 THEN
        -- gh-5699. The filter this one was made from, where it was made by replacing an existing
        -- filter rather than being created outright, e.g. restoring a deleted filter so that its
        -- range is processed again. Reprocessing a range makes a new filter rather than resetting
        -- an existing filter's tracker, so that a filter id always means the same body of work;
        -- this column is what keeps the history visible once it does.
        --
        -- Deliberately not a foreign key. Old filters are physically deleted once their tasks have
        -- gone (see ProcessorFilterDaoImpl.physicalDeleteOldProcessorFilters), and a constraint
        -- here would keep every superseded ancestor alive for as long as any descendant existed.
        -- This is provenance, not referential integrity, so it is allowed to dangle.
        ALTER TABLE processor_filter ADD COLUMN parent_filter_id int DEFAULT NULL;
    END IF;
END $$

DELIMITER ;

CALL V07_14_00_004__processor_filter_parent_filter_id;

DROP PROCEDURE IF EXISTS V07_14_00_004__processor_filter_parent_filter_id;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
