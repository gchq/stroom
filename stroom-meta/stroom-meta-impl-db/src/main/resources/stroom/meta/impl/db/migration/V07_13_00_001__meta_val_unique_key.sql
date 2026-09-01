-- ------------------------------------------------------------------------
-- Copyright 2026 Crown Copyright
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
-- A meta only ever has one value for each key, but nothing has enforced that, so queries that join to
-- meta_val have had to de-duplicate their results in case it wasn't true. Enforce it instead so they don't
-- have to.
--

--
-- Remove any rows that break the rule before we impose it, keeping the highest id for each meta and key,
-- which is the most recently written value. The `having` restricts the delete to only those metas and keys
-- that actually have more than one row, which should be none.
--
DELETE mv
FROM meta_val mv
JOIN (
    SELECT
        meta_id,
        meta_key_id,
        MAX(id) AS keep_id
    FROM meta_val
    GROUP BY meta_id, meta_key_id
    HAVING COUNT(*) > 1) duplicates
ON mv.meta_id = duplicates.meta_id
    AND mv.meta_key_id = duplicates.meta_key_id
    AND mv.id <> duplicates.keep_id;

--
-- meta_id is the first column so this also does the job of the existing meta_val_meta_id index, which is
-- left in place as dropping it is not needed to enforce uniqueness.
--
CALL meta_create_unique_index_v1(
    'meta_val',
    'meta_val_meta_id_meta_key_id',
    'meta_id, meta_key_id');

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set tabstop=4 shiftwidth=4 expandtab:
