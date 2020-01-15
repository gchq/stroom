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
-- Create the explorer tables
--
CREATE TABLE IF NOT EXISTS explorer_node (
  id                    int(11) NOT NULL AUTO_INCREMENT,
  type                  varchar(255) NOT NULL,
  uuid                  varchar(255) NOT NULL,
  name                  varchar(255) NOT NULL,
  tags                  varchar(255) DEFAULT NULL,
  PRIMARY KEY           (id),
  UNIQUE KEY            type (type,uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS explorer_path (
  ancestor              int(11) NOT NULL,
  descendant            int(11) NOT NULL,
  depth                 int(11) NOT NULL,
  order_index           int(11) NOT NULL,
  PRIMARY KEY           (ancestor,descendant)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the explorer table
--
DROP PROCEDURE IF EXISTS copy_explorer;
DELIMITER //
CREATE PROCEDURE copy_explorer ()
BEGIN
  -- TODO get rid of OLD_ table logic, see V7_0_0_1__config.sql as an example
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'explorerTreeNode' > 0) THEN
        INSERT INTO explorer_node (id, type, uuid, name, tags)
        SELECT id, type, uuid, name, tags
        FROM explorerTreeNode;
    END IF;
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'OLD_explorerTreeNode' > 0) THEN
        INSERT INTO explorer_node (id, type, uuid, name, tags)
        SELECT id, type, uuid, name, tags
        FROM OLD_explorerTreeNode;
    END IF;

    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'explorerTreePath' > 0) THEN
        INSERT INTO explorer_path (ancestor, descendant, depth, order_index)
        SELECT ancestor, descendant, depth, orderIndex
        FROM explorerTreePath;
    END IF;
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'OLD_explorerTreePath' > 0) THEN
        INSERT INTO explorer_path (ancestor, descendant, depth, order_index)
        SELECT ancestor, descendant, depth, orderIndex
        FROM OLD_explorerTreePath;
    END IF;
END//
DELIMITER ;
CALL copy_explorer();
DROP PROCEDURE copy_explorer;

SET SQL_NOTES=@OLD_SQL_NOTES;
