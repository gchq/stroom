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
    s3_region                 varchar(255) NOT NULL,
    s3_bucket                 varchar(255) NOT NULL,
    s3_key                    varchar(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY `fs_meta_s3_location_meta_region_bucket_key_idx` (
        meta_id,
        s3_region,
        s3_bucket,
        s3_key)
    ) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
