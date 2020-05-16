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
-- Create the meta_val table
--
CREATE TABLE IF NOT EXISTS `meta_val` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `create_time` bigint(20) NOT NULL,
  `meta_id` bigint(20) NOT NULL,
  `meta_key_id` int(11) NOT NULL,
  `val` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `meta_val_create_time` (`create_time`),
  KEY `meta_val_meta_id` (`meta_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set tabstop=4 shiftwidth=4 expandtab:
