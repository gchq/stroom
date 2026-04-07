-- ------------------------------------------------------------------------
-- Copyright 2016-2026 Crown Copyright
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

DROP PROCEDURE IF EXISTS V07_12_00_001_annotation;

DELIMITER $$

CREATE PROCEDURE V07_12_00_001_annotation ()
BEGIN
    DECLARE object_count integer;

    --
    -- Add tag_text column to annotation_tag
    --
    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'annotation_tag'
    AND column_name = 'tag_text';

    IF object_count = 0 THEN
        ALTER TABLE `annotation_tag` ADD COLUMN `tag_text` longtext DEFAULT NULL;
    END IF;

END $$

DELIMITER ;

CALL V07_12_00_001_annotation;

DROP PROCEDURE IF EXISTS V07_12_00_001_annotation;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
