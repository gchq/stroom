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

-- stop note level warnings about objects (not)? existing
set @old_sql_notes = @@sql_notes, sql_notes = 0;

CREATE TABLE IF NOT EXISTS `tab_session_doc_ref`
(
    `tab_session_id`           int          NOT NULL,
    `tab_index`                int          NOT NULL,
    `doc_ref_type`             varchar(255) NOT NULL,
    `doc_ref_id`               varchar(255) NOT NULL,
    PRIMARY KEY (`tab_session_id`, `doc_ref_type`, `doc_ref_id`),
    CONSTRAINT `tab_session_doc_ref_tab_session_id` FOREIGN KEY (`tab_session_id`)
       REFERENCES `tab_session` (`id`)
       ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Reset to the original value
SET SQL_NOTES = @OLD_SQL_NOTES;
