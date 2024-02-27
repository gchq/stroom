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

-- --------------------------------------------------

--
-- Create the table
--
CREATE TABLE IF NOT EXISTS execution_schedule (
   id int NOT NULL AUTO_INCREMENT,
   name varchar(255) NOT NULL,
   enabled tinyint NOT NULL DEFAULT '0',
   node_name varchar(255) NOT NULL,
   schedule_type varchar(255) NOT NULL,
   expression varchar(255) NOT NULL,
   contiguous tinyint NOT NULL DEFAULT '0',
   start_time_ms bigint DEFAULT NULL,
   end_time_ms bigint DEFAULT NULL,
   doc_type varchar(255) NOT NULL,
   doc_uuid varchar(255) NOT NULL,
   PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX execution_schedule_doc_idx ON execution_schedule (doc_type, doc_uuid);
CREATE INDEX execution_schedule_enabled_idx ON execution_schedule (doc_type, doc_uuid, enabled, node_name);

CREATE TABLE IF NOT EXISTS execution_history (
   id bigint(20) NOT NULL AUTO_INCREMENT,
   fk_execution_schedule_id int NOT NULL,
   execution_time_ms bigint NOT NULL,
   effective_execution_time_ms bigint NOT NULL,
   status varchar(255) NOT NULL,
   message longtext,
   PRIMARY KEY (id),
   CONSTRAINT execution_history_execution_schedule_id FOREIGN KEY (fk_execution_schedule_id) REFERENCES execution_schedule (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS execution_tracker (
   fk_execution_schedule_id int NOT NULL,
   actual_execution_time_ms bigint NOT NULL,
   last_effective_execution_time_ms bigint DEFAULT NULL,
   next_effective_execution_time_ms bigint NOT NULL,
   PRIMARY KEY (fk_execution_schedule_id),
   CONSTRAINT execution_tracker_execution_schedule_id FOREIGN KEY (fk_execution_schedule_id) REFERENCES execution_schedule (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- --------------------------------------------------

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=2 tabstop=2 expandtab:
