/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

DROP PROCEDURE IF EXISTS V07_14_00_020;

DELIMITER $$

CREATE PROCEDURE V07_14_00_020 ()
BEGIN
    DECLARE object_count integer;

    -- Relax the 'not null' constraint on the path column
    ALTER TABLE fs_volume MODIFY path varchar(255);

    -- Check for existing unique constraint on 'path' and drop it
    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.statistics
    WHERE table_schema = database()
    AND table_name = 'fs_volume'
    AND index_name = 'path'
    AND non_unique = 0;

    IF object_count > 0 THEN
        ALTER TABLE fs_volume DROP INDEX path;
    END IF;

    -- Check for the new composite unique constraint and add it if missing
    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.statistics
    WHERE table_schema = database()
    AND table_name = 'fs_volume'
    AND index_name = 'fs_volume_type_path_idx'
    AND non_unique = 0;

    IF object_count = 0 THEN
        -- Paths should only be unique within a volume type
        ALTER TABLE fs_volume ADD CONSTRAINT fs_volume_type_path_idx UNIQUE (volume_type, path);
    END IF;

END $$

DELIMITER ;

CALL V07_14_00_020;

DROP PROCEDURE IF EXISTS V07_14_00_020;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
