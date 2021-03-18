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

-- Change table encodings.
ALTER TABLE SQL_STAT_KEY              CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE SQL_STAT_VAL              CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE SQL_STAT_VAL_SRC          CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

DROP PROCEDURE IF EXISTS drop_empty_sql_stat_val_src;
DELIMITER //
CREATE PROCEDURE drop_empty_sql_stat_val_src ()
BEGIN
    IF EXISTS (
        SELECT NULL
        FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_SCHEMA = database()
        AND TABLE_NAME = 'SQL_STAT_VAL_SRC') THEN

        IF (SELECT COUNT(*) FROM SQL_STAT_VAL_SRC) = 0 THEN
            DROP TABLE SQL_STAT_VAL_SRC;
        END IF;

    END IF;
END//
DELIMITER ;
CALL drop_empty_sql_stat_val_src();
DROP PROCEDURE drop_empty_sql_stat_val_src;

DROP PROCEDURE IF EXISTS drop_empty_sql_stat_val;
DELIMITER //
CREATE PROCEDURE drop_empty_sql_stat_val ()
BEGIN
    IF EXISTS (
        SELECT NULL
        FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_SCHEMA = database()
        AND TABLE_NAME = 'SQL_STAT_VAL') THEN

        IF (SELECT COUNT(*) FROM SQL_STAT_VAL) = 0 THEN
            DROP TABLE SQL_STAT_VAL;
        END IF;

    END IF;
END//
DELIMITER ;
CALL drop_empty_sql_stat_val();
DROP PROCEDURE drop_empty_sql_stat_val;

DROP PROCEDURE IF EXISTS drop_empty_sql_stat_key;
DELIMITER //
CREATE PROCEDURE drop_empty_sql_stat_key ()
BEGIN
    IF EXISTS (
        SELECT NULL
        FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_SCHEMA = database()
        AND TABLE_NAME = 'SQL_STAT_KEY') THEN

        IF (SELECT COUNT(*) FROM SQL_STAT_KEY) = 0 THEN
            DROP TABLE SQL_STAT_KEY;
        END IF;

    END IF;
END//
DELIMITER ;
CALL drop_empty_sql_stat_key();
DROP PROCEDURE drop_empty_sql_stat_key;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
