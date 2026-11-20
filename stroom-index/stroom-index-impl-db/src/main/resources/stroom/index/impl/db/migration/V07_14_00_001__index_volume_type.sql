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

DROP PROCEDURE IF EXISTS V07_14_00_001;

DELIMITER $$

CREATE PROCEDURE V07_14_00_001 ()
BEGIN
    DECLARE object_count integer;

    -- An index volume is either local (0), a disk that only its node can see, or shared (1), a mount
    -- that every node can see. Every existing volume is local, which keeps its present behaviour.
    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'index_volume'
    AND column_name = 'volume_type';

    IF object_count = 0 THEN
        ALTER TABLE index_volume ADD COLUMN volume_type tinyint NOT NULL DEFAULT 0;
    END IF;

END $$

DELIMITER ;

CALL V07_14_00_001;

DROP PROCEDURE IF EXISTS V07_14_00_001;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
