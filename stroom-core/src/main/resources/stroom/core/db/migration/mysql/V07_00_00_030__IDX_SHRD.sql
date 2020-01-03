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

ALTER TABLE IDX_SHRD ADD COLUMN IDX_UUID varchar(255) default NULL;
UPDATE IDX_SHRD shard SET shard.IDX_UUID = (SELECT ind.UUID FROM IDX ind WHERE ind.ID = shard.FK_IDX_ID);
ALTER TABLE IDX_SHRD DROP FOREIGN KEY IDX_SHRD_FK_IDX_ID;
ALTER TABLE IDX_SHRD CHANGE FK_IDX_ID OLD_IDX_ID int(11) default NULL;