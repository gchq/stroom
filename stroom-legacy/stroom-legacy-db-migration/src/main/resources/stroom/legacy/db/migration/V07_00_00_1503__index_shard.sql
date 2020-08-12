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

CREATE TABLE IF NOT EXISTS index_shard (
    id                    bigint(20) NOT NULL AUTO_INCREMENT,
    node_name             varchar(255) NOT NULL,
    fk_volume_id          int(11) NOT NULL,
    index_uuid            varchar(255) NOT NULL,
    commit_document_count int(11) DEFAULT NULL,
    commit_duration_ms    bigint(20) DEFAULT NULL,
    commit_ms             bigint(20) DEFAULT NULL,
    document_count        int(11) DEFAULT 0,
    file_size             bigint(20) DEFAULT 0,
    status                tinyint(4) NOT NULL,
    index_version         varchar(255) DEFAULT NULL,
    partition_name        varchar(255) NOT NULL,
    partition_from_ms     bigint(20) DEFAULT NULL,
    partition_to_ms       bigint(20) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY index_shard_fk_volume_id (fk_volume_id),
    KEY index_shard_index_uuid (index_uuid),
    CONSTRAINT index_shard_fk_volume_id
        FOREIGN KEY (fk_volume_id)
        REFERENCES index_volume (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the index table
--
DROP PROCEDURE IF EXISTS copy_index_shard;
DELIMITER //
CREATE PROCEDURE copy_index_shard ()
BEGIN
    -- Doesn't matter if two scripts do this
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = database()
            AND TABLE_NAME = 'IDX_SHRD') THEN

        RENAME TABLE IDX_SHRD TO OLD_IDX_SHRD;
    END IF;

    -- Doesn't matter if two scripts do this
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = database()
            AND TABLE_NAME = 'ND') THEN

        RENAME TABLE ND TO OLD_ND;
    END IF;

    -- Check again so it is idempotent
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES 
            WHERE TABLE_SCHEMA = database()
            AND TABLE_NAME = 'OLD_IDX_SHRD') THEN
        --
        -- Copy data into the table, use ID predicate to make it re-runnable
        --
        INSERT
        INTO index_shard (
            id,
            node_name,
            fk_volume_id,
            index_uuid,
            commit_document_count,
            commit_duration_ms,
            commit_ms,
            document_count,
            file_size,
            status,
            index_version,
            partition_name,
            partition_from_ms,
            partition_to_ms)
        SELECT
            s.ID,
            n.NAME,
            s.FK_VOL_ID,
            s.IDX_UUID,
            s.CMT_DOC_CT,
            s.CMT_DUR_MS,
            s.CMT_MS,
            s.DOC_CT,
            s.FILE_SZ,
            s.STAT,
            s.IDX_VER,
            s.PART,
            s.PART_FROM_MS,
            s.PART_TO_MS
        FROM OLD_IDX_SHRD s
        INNER JOIN OLD_ND n ON (n.ID = s.FK_ND_ID)
        WHERE s.ID > (SELECT COALESCE(MAX(id), 0) FROM index_shard)
        ORDER BY s.ID;

        -- Work out what to set our auto_increment start value to
        SELECT CONCAT('ALTER TABLE index_shard AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM index_shard;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
    END IF;

END//
DELIMITER ;
CALL copy_index_shard();
DROP PROCEDURE copy_index_shard;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
