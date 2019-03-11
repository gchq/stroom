CREATE TABLE IF NOT EXISTS processor_filter_task (
  id                        bigint(20) NOT NULL AUTO_INCREMENT,
  version                   int(11) NOT NULL,
  fk_processor_filter_id    int(11) NOT NULL,
  fk_processor_node_id      int(11) NOT NULL,
  create_time_ms            bigint(20) DEFAULT NULL,
  start_time_ms             bigint(20) DEFAULT NULL,
  end_time_ms               bigint(20) DEFAULT NULL,
  status                    tinyint(4) NOT NULL,
  status_time_ms            bigint(20) DEFAULT NULL,
  meta_id                   bigint(20) NOT NULL,
  data                      longtext,
  PRIMARY KEY               (id),
  KEY processor_filter_task_fk_processor_filter_id (fk_processor_filter_id),
  KEY processor_filter_task_fk_processor_node_id (fk_processor_node_id),
  KEY processor_filter_task_status_idx (status),
  KEY processor_filter_task_meta_id_idx (meta_id),
  CONSTRAINT processor_filter_task_fk_processor_filter_id FOREIGN KEY (fk_processor_filter_id) REFERENCES processor_filter (id),
  CONSTRAINT processor_filter_task_fk_processor_node_id FOREIGN KEY (fk_processor_node_id) REFERENCES processor_node (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

DROP PROCEDURE IF EXISTS copy_processor_filter_task;
DELIMITER //
CREATE PROCEDURE copy_processor_filter_task ()
BEGIN
    -- If table exists (it may not if this migration runs before core stroom's) then migrate its data,
    -- if it doesn't exist then it won't ever have data to migrate
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'STRM_TASK' > 0) THEN
        -- Copy data into the table, use ID predicate to make it re-runnable
        INSERT
        INTO processor_filter_task (id, version, fk_processor_filter_id, fk_processor_node_id, create_time_ms, start_time_ms, end_time_ms, status, status_time_ms, meta_id, data)
        SELECT ID, VER, FK_STRM_PROC_FILT_ID, FK_ND_ID, CRT_MS, START_TIME_MS, END_TIME_MS, STAT, STAT_MS, FK_STRM_ID, DAT
        FROM STRM_TASK
        WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM processor_filter_task)
        ORDER BY ID;

        -- Work out what to set our auto_increment start value to
        SELECT CONCAT('ALTER TABLE processor_filter_task AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM processor_filter_task;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
    END IF;
END//
DELIMITER ;
CALL copy_processor_filter_task();
DROP PROCEDURE copy_processor_filter_task;

