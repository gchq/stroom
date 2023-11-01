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

CREATE TABLE IF NOT EXISTS fs_volume_group (
  id                    int NOT NULL AUTO_INCREMENT,
  version               int NOT NULL,
  create_time_ms        bigint NOT NULL,
  create_user           varchar(255) NOT NULL,
  update_time_ms        bigint NOT NULL,
  update_user           varchar(255) NOT NULL,
  name                  varchar(255) NOT NULL,
  -- 'name' needs to be unique because it is used as a reference
  UNIQUE (name),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;


DROP PROCEDURE IF EXISTS V07_03_00_001;

DELIMITER $$

CREATE PROCEDURE V07_03_00_001 ()
BEGIN
    DECLARE object_count integer;

    -- Add volume type
    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'fs_volume'
    AND column_name = 'volume_type';

    IF object_count = 0 THEN
        ALTER TABLE `fs_volume` ADD COLUMN `volume_type` int NOT NULL;
        ALTER TABLE `fs_volume` ADD COLUMN `data` longblob;
        UPDATE `fs_volume` set `volume_type` = 0;
    END IF;

    -- Add default group
    SELECT COUNT(*)
    INTO object_count
    FROM fs_volume_group
    WHERE name = "Default";

    IF object_count = 0 THEN
        INSERT INTO fs_volume_group (
          version,
          create_time_ms,
          create_user,
          update_time_ms,
          update_user,
          name)
        VALUES (
            1,
            UNIX_TIMESTAMP() * 1000,
            "Flyway migration",
            UNIX_TIMESTAMP() * 1000,
            "Flyway migration",
            "Default Volume Group");
    END IF;

    -- Add volume group
    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'fs_volume'
    AND column_name = 'fk_fs_volume_group_id';

    IF object_count = 0 THEN
        ALTER TABLE `fs_volume`
        ADD COLUMN `fk_fs_volume_group_id` int NOT NULL;
        UPDATE `fs_volume` SET `fk_fs_volume_group_id` = (SELECT `id` FROM `fs_volume_group` WHERE `name` = "Default Volume Group");
        ALTER TABLE fs_volume
            ADD CONSTRAINT fs_volume_group_fk_fs_volume_group_id
            FOREIGN KEY (fk_fs_volume_group_id)
            REFERENCES fs_volume_group (id);
    END IF;

END $$

DELIMITER ;

CALL V07_03_00_001;

DROP PROCEDURE IF EXISTS V07_03_00_001;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
