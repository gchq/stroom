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


DROP PROCEDURE IF EXISTS V07_12_00_002_node_group;

DELIMITER $$

CREATE PROCEDURE V07_12_00_002_node_group ()
BEGIN
    DECLARE object_count integer;

    -- Add volume type
SELECT COUNT(1)
INTO object_count
FROM information_schema.columns
WHERE table_schema = database()
  AND table_name = 'node_group'
  AND column_name = 'invert_selection';

IF object_count = 0 THEN
ALTER TABLE `node_group` ADD COLUMN `invert_selection` tinyint(1) NOT NULL DEFAULT 0;
END IF;

END $$

DELIMITER ;

CALL V07_12_00_002_node_group;

DROP PROCEDURE IF EXISTS V07_12_00_002_node_group;
SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set tabstop=4 shiftwidth=4 expandtab:
