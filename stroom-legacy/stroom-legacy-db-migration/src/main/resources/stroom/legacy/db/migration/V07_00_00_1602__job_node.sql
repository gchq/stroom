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

--
-- Create the job node table
--
CREATE TABLE IF NOT EXISTS job_node (
    id                    int(11) NOT NULL AUTO_INCREMENT,
    version               int(11) NOT NULL,
    create_time_ms        bigint(20) NOT NULL,
    create_user           varchar(255) NOT NULL,
    update_time_ms        bigint(20) NOT NULL,
    update_user           varchar(255) NOT NULL,
    job_id                int(11) NOT NULL,
    job_type              tinyint(4) NOT NULL,
    node_name             varchar(255) NOT NULL,
    task_limit            int(11) NOT NULL,
    schedule              varchar(255) DEFAULT NULL,
    enabled               bit(1) NOT NULL,
    PRIMARY KEY           (id),
    CONSTRAINT job_id FOREIGN KEY (job_id) REFERENCES job (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the job node table
--
DROP PROCEDURE IF EXISTS copy_job_node;
DELIMITER //
CREATE PROCEDURE copy_job_node ()
BEGIN
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'JB_ND') THEN

        RENAME TABLE JB_ND TO OLD_JB_ND;
    END IF;

    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'ND') THEN

        RENAME TABLE ND TO OLD_ND;
    END IF;

    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'OLD_JB_ND') THEN

        INSERT INTO job_node (
            id,
            version,
            create_time_ms,
            create_user,
            update_time_ms,
            update_user,
            job_id,
            job_type,
            node_name,
            task_limit,
            schedule,
            enabled)
        SELECT
            j.ID,
            1,
            IFNULL(j.CRT_MS,  0),
            IFNULL(j.CRT_USER,  'UNKNOWN'),
            IFNULL(j.UPD_MS,  0),
            IFNULL(j.UPD_USER,  'UNKNOWN'),
            j.FK_JB_ID,
            j.JB_TP,
            n.NAME,
            j.TASK_LMT,
            j.SCHEDULE,
            j.ENBL
        FROM OLD_JB_ND j
        JOIN OLD_ND n ON (j.FK_ND_ID = n.ID)
        WHERE j.ID > (SELECT COALESCE(MAX(id), 0) FROM job_node)
        ORDER BY j.ID;

        -- Work out what to set our auto_increment start value to
        SELECT CONCAT('ALTER TABLE job_node AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM job_node;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
  END IF;
END//
DELIMITER ;
CALL copy_job_node();
DROP PROCEDURE copy_job_node;

SET SQL_NOTES=@OLD_SQL_NOTES;
-- vim: set tabstop=4 shiftwidth=4 expandtab:
