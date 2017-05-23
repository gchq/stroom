/*
 * Copyright 2016 Crown Copyright
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

-- Drop the ENGINE_NAME column from STAT_DAT_SRC as this table is exclusive to SQL stats
-- making the column redundant. Need to drop/recreate the NAME index as ENGINE_NAME was used in it

SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE STAT_DAT_SRC DROP INDEX NAME;

ALTER TABLE STAT_DAT_SRC ADD CONSTRAINT UNIQUE INDEX NAME (FK_FOLDER_ID, NAME);

ALTER TABLE STAT_DAT_SRC DROP COLUMN ENGINE_NAME;

SET FOREIGN_KEY_CHECKS = 1;
