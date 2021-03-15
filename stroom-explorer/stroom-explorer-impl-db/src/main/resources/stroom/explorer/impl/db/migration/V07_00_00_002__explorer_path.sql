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
-- Create the explorer_path table
--
CREATE TABLE IF NOT EXISTS `explorer_path` (
  `ancestor` int NOT NULL,
  `descendant` int NOT NULL,
  `depth` int NOT NULL,
  `order_index` int NOT NULL,
  PRIMARY KEY (`ancestor`,`descendant`),
  KEY `explorer_path_descendant` (`descendant`),
  KEY `explorer_path_descendant_depth` (`descendant`,`depth`),
  KEY `explorer_path_ancestor_depth_order_index` (`ancestor`,`depth`,`order_index`),
  KEY `explorer_path_depth` (`depth`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set tabstop=4 shiftwidth=4 expandtab:
