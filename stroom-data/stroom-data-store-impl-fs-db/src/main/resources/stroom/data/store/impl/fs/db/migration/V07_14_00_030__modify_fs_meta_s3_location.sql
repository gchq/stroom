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

DROP PROCEDURE IF EXISTS V07_14_00_030;

DELIMITER $$

CREATE PROCEDURE V07_14_00_030 ()
BEGIN
    DECLARE object_count integer;

    -- A deterministic hash of location for the unique key, except in the slim chance of a hash clash.
    ALTER TABLE fs_meta_s3_location MODIFY s3_hash binary(32) GENERATED ALWAYS AS (
        unhex(sha2(
            concat(s3_region, '|', s3_bucket, '|', s3_key),
            256))
    ) STORED NOT NULL;

    -- Keep the original unique constraint and add a unique constraint on the hash alone.
    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.statistics
    WHERE table_schema = database()
    AND table_name = 'fs_meta_s3_location'
    AND index_name = 'fs_meta_s3_location_hash_idx';

    IF object_count = 0 THEN
        -- Indexing the region+bucket+key results in an index key that is too big as it has to assume
        -- the s3_key is 4 bytes/char. Thus, we index the hash instead, which is much smaller and almost
        -- unique. We have to assume no hash clashes
        ALTER TABLE fs_meta_s3_location ADD CONSTRAINT UNIQUE INDEX fs_meta_s3_location_hash_idx (s3_hash);
    END IF;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.statistics
    WHERE table_schema = database()
      AND table_name = 'fs_meta_s3_location'
      AND index_name = 'fs_meta_s3_location_meta_s3_hash_idx';

    IF object_count = 0 THEN
        -- No longer needed if we have unique on s3_hash
        ALTER TABLE fs_meta_s3_location DROP INDEX fs_meta_s3_location_meta_s3_hash_idx;
    END IF;
END $$

DELIMITER ;

CALL V07_14_00_030;

DROP PROCEDURE IF EXISTS V07_14_00_030;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
