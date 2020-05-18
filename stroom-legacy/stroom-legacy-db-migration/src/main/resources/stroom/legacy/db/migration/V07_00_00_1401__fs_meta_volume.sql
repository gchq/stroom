-- ------------------------------------------------------------------------
-- Copyright 2020 Crown Copyright
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

--
-- Create the fs_meta_volume table
--
CREATE TABLE IF NOT EXISTS fs_meta_volume (
    meta_id           bigint(20) NOT NULL,
    fs_volume_id      int(11) NOT NULL,
    PRIMARY KEY       (meta_id, fs_volume_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the fs_meta_volume table
--
DROP PROCEDURE IF EXISTS copy_fs_meta_volume;
DELIMITER //
CREATE PROCEDURE copy_fs_meta_volume ()
BEGIN
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'STRM_VOL') THEN

        RENAME TABLE STRM_VOL TO OLD_STRM_VOL;
    END IF;

    -- Check again so it is idempotent
    IF EXISTS (
            SELECT TABLE_NAME
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'OLD_STRM_VOL') THEN

        INSERT INTO fs_meta_volume (
            meta_id,
            fs_volume_id)
        SELECT
            FK_STRM_ID,
            FK_VOL_ID
        FROM OLD_STRM_VOL
        WHERE FK_STRM_ID > (SELECT COALESCE(MAX(meta_id), 0) FROM fs_meta_volume)
        ORDER BY FK_STRM_ID;
    END IF;

END//
DELIMITER ;
CALL copy_fs_meta_volume();
DROP PROCEDURE copy_fs_meta_volume;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
