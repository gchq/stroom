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

-- About to drop one of the cols in the idx so remove it
CALL statistics_drop_index_v1(
    'SQL_STAT_VAL_SRC',
    'SQL_STAT_VAL_SRC_PROCESSING_TIME_MS');

-- We will now use the PK to control what data is being processed
CALL statistics_drop_column_v1(
    'SQL_STAT_VAL_SRC',
    'PROCESSING');

 -- Re-index the time_ms col
CALL statistics_create_non_unique_index_v1(
    'SQL_STAT_VAL_SRC',
    'SQL_STAT_VAL_SRC_TIME_MS',
    'TIME_MS');

SET SQL_NOTES=@OLD_SQL_NOTES;
