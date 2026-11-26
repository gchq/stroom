/*
 * Copyright 2026 Crown Copyright
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

-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

-- Replaced by index_shard_task and index_shard_purge. V07_14_00_015 has already emptied it.
DROP TABLE IF EXISTS index_shard_pending_stream;

DROP PROCEDURE IF EXISTS V08_00_00_010;

DELIMITER $$

CREATE PROCEDURE V08_00_00_010 ()
BEGIN
    DECLARE object_count integer;

    -- Replaced by index_shard.writer_id, which a shard's rows in index_shard_task are compared with.
    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'index_shard'
    AND column_name = 'recover_pending_streams';

    IF object_count > 0 THEN
        ALTER TABLE index_shard DROP COLUMN recover_pending_streams;
    END IF;

END $$

DELIMITER ;

CALL V08_00_00_010;

DROP PROCEDURE IF EXISTS V08_00_00_010;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
