/*
 * Copyright 2016-2026 Crown Copyright
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

-- Links one or more files stored on S3 to a meta record.
-- In theory, we could just hold a FK to fs_meta_vol and the s3_key, but the volume
-- client config is editable, so if someone changes that then we no longer know
-- where the files are on s3. Having it in here seems safer, despite the duplication.
CREATE TABLE IF NOT EXISTS fs_meta_s3_location (
    id                        bigint NOT NULL AUTO_INCREMENT,
    meta_id                   bigint NOT NULL,
    -- Region/Bucket are strictly ascii and case-sensitive so use ascii and ascii_bin collation.
    s3_region                 VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    s3_bucket                 VARCHAR(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,

    -- Use a case-sensitive binary collation as we are not always in control of the keys.
    s3_key                    VARCHAR(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,

    -- A deterministic hash of location for the unique key, except in the slim chance of a hash clash.
    s3_hash                   binary(32) GENERATED ALWAYS AS (
        unhex(sha2(
            concat(meta_id, '|', s3_region, '|', s3_bucket, '|', s3_key),
            256))
    ) STORED NOT NULL,

    PRIMARY KEY (id),

    -- Indexing the region+bucket+key results in an index key that is too big as it has to assume
    -- the F s3_key is 4 bytes/char. Thus, we index the hash instead, which is much smaller and almost
    -- unique.
    UNIQUE KEY `fs_meta_s3_location_meta_s3_hash_idx` (meta_id, s3_hash)
    ) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
