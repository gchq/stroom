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
set @old_sql_notes=@@sql_notes, sql_notes=0;

--
-- CREATE NEW TABLES
--
CREATE TABLE IF NOT EXISTS `document` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `type` varchar(255) NOT NULL,
  `uuid` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `unique_name` varchar(255) NOT NULL,
  `version` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `document_uuid_idx` (`uuid`),
  UNIQUE KEY `document_unique_name_idx` (`unique_name`),
  KEY `doc_type_uuid_idx` (`type`,`uuid`)
) ENGINE=InnoDB AUTO_INCREMENT=446 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `document_entry` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `fk_document_id` bigint NOT NULL,
  `entry` varchar(255) NOT NULL,
  `data` longblob,
  PRIMARY KEY (`id`),
  UNIQUE KEY `document_fk_document_id_oath` (`fk_document_id`, `entry`),
  CONSTRAINT document_entry_fk_document_id FOREIGN KEY (fk_document_id) REFERENCES document (id)
) ENGINE=InnoDB AUTO_INCREMENT=446 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;


--
-- INSERT DATA
--
DROP PROCEDURE IF EXISTS V07_07_00_001__copy_docs;
DELIMITER $$
CREATE PROCEDURE V07_07_00_001__copy_docs ()
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(*)
    INTO object_count
    FROM document;

    IF object_count = 0 THEN

        INSERT INTO document (type, uuid, name, unique_name, version)
        select v2.type, v2.uuid, v2.name, concat(v2.un, if(rn = 1, '', concat('_', v2.rn))), uuid()
        from (
            select
              v.type,
              v.uuid,
              v.name,
              v.un,
              row_number() over (partition by v.un order by v.un) as rn
            from (
                select
                  type,
                  uuid,
                  name,
                  concat(
                  regexp_replace(regexp_replace(regexp_replace(regexp_replace(lower(type), '[^0-9a-z]', '-'), '-+', '-'), '^-', ''), '-$', ''),
                  ':',
                  regexp_replace(regexp_replace(regexp_replace(regexp_replace(lower(name), '[^0-9a-z]', '-'), '-+', '-'), '^-', ''), '-$', '')
                  ) as un
                from doc
                where ext = 'meta'
            ) v
            order by v.un
        ) v2;

    END IF;
END $$

DELIMITER ;
CALL V07_07_00_001__copy_docs;
DROP PROCEDURE IF EXISTS V07_07_00_001__copy_docs;





DROP PROCEDURE IF EXISTS V07_07_00_001__copy_doc_entries;
DELIMITER $$
CREATE PROCEDURE V07_07_00_001__copy_doc_entries ()
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(*)
    INTO object_count
    FROM document_entry;

    IF object_count = 0 THEN

        INSERT INTO document_entry (fk_document_id, entry, data)
        SELECT v2.id, concat('doc.', v1.ext), v1.data
        FROM doc v1
        JOIN document v2 ON (v2.uuid = v1.uuid);

    END IF;
END $$

DELIMITER ;
CALL V07_07_00_001__copy_doc_entries;
DROP PROCEDURE IF EXISTS V07_07_00_001__copy_doc_entries;

--
-- RENAME OLD TABLE
--
DROP PROCEDURE IF EXISTS V07_07_00_001__rename_doc;
DELIMITER $$
CREATE PROCEDURE V07_07_00_001__rename_doc ()
BEGIN

    -- Check again so it is idempotent
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = database()
            AND TABLE_NAME = 'DOC') THEN

        RENAME TABLE DOC TO OLD_DOC;
    END IF;
END $$

DELIMITER ;
CALL V07_07_00_001__rename_doc;
DROP PROCEDURE IF EXISTS V07_07_00_001__rename_doc;

-- Reset to the original value
SET SQL_NOTES=@OLD_SQL_NOTES;
