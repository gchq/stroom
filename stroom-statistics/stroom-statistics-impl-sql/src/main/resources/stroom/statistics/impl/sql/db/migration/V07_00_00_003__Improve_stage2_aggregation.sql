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

CALL statistics_create_non_unique_index_v1(
    'SQL_STAT_KEY',
    'SQL_STAT_KEY_NAME_ID',
    'NAME, ID');

-- Idempotent
-- We need an index that starts with the FK for constraint checks and most
-- search queries will be FK_SQL_STAT_KEY_ID = X AND TIME_MS >= Y AND TIME_MS < Z
CALL statistics_create_non_unique_index_v1(
    'SQL_STAT_VAL',
    'SQL_STAT_VAL_FK_SQL_STAT_KEY_ID_TIME_MS',
    'FK_SQL_STAT_KEY_ID, TIME_MS');

-- Idempotent
-- Typical access pattern for aggregation is
-- VAL_TP = X AND PRES = Y AND TIME_MS < Z
-- so we need the ones that use equality at the front of the index so mysql can jump to the
-- (VAL_TP = X AND PRES = Y) branch of the index then range scan on TIME_MS
alter table SQL_STAT_VAL
    drop primary key,
    add primary key(
        PRES,
        VAL_TP,
        TIME_MS,
        FK_SQL_STAT_KEY_ID);

-- Idempotent
-- Don't need this one now the PK is re-ordered
CALL statistics_drop_index_v1(
    'SQL_STAT_VAL',
    'SQL_STAT_VAL_TIME_MS');

SET SQL_NOTES=@OLD_SQL_NOTES;
