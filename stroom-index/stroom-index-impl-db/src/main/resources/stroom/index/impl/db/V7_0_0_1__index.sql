--
-- Create the explorer tables
--
CREATE TABLE `rack` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `ver` tinyint(4) NOT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `created_at` bigint(20) DEFAULT NULL,
  `updated_by` varchar(255) DEFAULT NULL,
  `updated_at` bigint(20) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`ID`),
  UNIQUE KEY `NAME` (`NAME`)
) ENGINE=InnoDB AUTO_INCREMENT=311 DEFAULT CHARSET=latin1;

CREATE TABLE `node` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `ver` tinyint(4) NOT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `created_at` bigint(20) DEFAULT NULL,
  `updated_by` varchar(255) DEFAULT NULL,
  `updated_at` bigint(20) DEFAULT NULL,
  `cluster_url` varchar(255) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `prior` smallint(6) NOT NULL,
  `enable` bit(1) NOT NULL,
  `fk_rack_id` int(11) NOT NULL,
  PRIMARY KEY (`ID`),
  UNIQUE KEY `NAME` (`NAME`),
  KEY `node_fk_rack_id` (`fk_rack_id`),
  CONSTRAINT `node_fk_rack_id` FOREIGN KEY (`fk_rack_id`) REFERENCES `rack` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=311 DEFAULT CHARSET=latin1;

CREATE TABLE IF NOT EXISTS `index` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `version` tinyint(4) NOT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `created_at` bigint(20) DEFAULT NULL,
  `updated_by` varchar(255) DEFAULT NULL,
  `updated_at` bigint(20) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `description` longtext,
  `max_doc` int(11) NOT NULL,
  `max_shard` int(11) NOT NULL,
  `partition_by` tinyint(4) DEFAULT NULL,
  `partition_size` int(11) NOT NULL,
  `retention_day_age` int(11) DEFAULT NULL,
  `fields` longtext,
  `uuid` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uuid` (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE TABLE IF NOT EXISTS `index_shard` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `version` tinyint(4) NOT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `created_at` bigint(20) DEFAULT NULL,
  `updated_by` varchar(255) DEFAULT NULL,
  `updated_at` bigint(20) DEFAULT NULL,
  `commit_doc_count` int(11) DEFAULT NULL,
  `commit_duration_ms` bigint(20) DEFAULT NULL,
  `commit_ms` bigint(20) DEFAULT NULL,
  `doc_count` int(11) NOT NULL,
  `file_size` bigint(20) DEFAULT NULL,
  `status` tinyint(4) NOT NULL,
  `old_index_id` int(11) DEFAULT NULL,
  `fk_node_id` int(11) NOT NULL,
  `partition` varchar(255) NOT NULL,
  `fk_volume_id` int(11) NOT NULL,
  `index_vesrion` varchar(255) DEFAULT NULL,
  `partition_from_ms` bigint(20) DEFAULT NULL,
  `partition_to_ms` bigint(20) DEFAULT NULL,
  `index_uuid` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `index_shard_fk_volume_id` (`fk_volume_id`),
  KEY `index_shard_fk_index_id` (`old_index_id`),
  KEY `index_shard_fk_node_id` (`fk_node_id`),
  CONSTRAINT `index_shard_fk_volume_id` FOREIGN KEY (`fk_volume_id`) REFERENCES `volume` (`id`),
  CONSTRAINT `index_shard_fk_node_id` FOREIGN KEY (`fk_node_id`) REFERENCES `node` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `volume` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `version` tinyint(4) NOT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `created_at` bigint(20) DEFAULT NULL,
  `updated_by` varchar(255) DEFAULT NULL,
  `updated_at` bigint(20) DEFAULT NULL,
  `path` varchar(255) NOT NULL,
  `index_status` tinyint(4) NOT NULL,
  `volume_type` tinyint(4) NOT NULL,
  `bytes_limit` bigint(20) DEFAULT NULL,
  `bytes_used` bigint(20) DEFAULT NULL,
  `bytes_free` bigint(20) DEFAULT NULL,
  `bytes_total` bigint(20) DEFAULT NULL,
  `status_ms` bigint(20) DEFAULT NULL,
  `fk_node_id` int(11) NOT NULL,
  PRIMARY KEY (`ID`),
  UNIQUE KEY `fk_node_id` (`fk_node_id`,`PATH`),
  CONSTRAINT `volume_fk_node_id` FOREIGN KEY (`fk_node_id`) REFERENCES `node` (`id`),
  ) ENGINE=InnoDB AUTO_INCREMENT=621 DEFAULT CHARSET=latin1;

--
-- Copy data into the explorer table
--
DROP PROCEDURE IF EXISTS copy;
DELIMITER //
CREATE PROCEDURE copy ()
BEGIN

    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'RK' > 0) THEN
        INSERT INTO rack (id, ver, created_by, created_at, updated_by, updated_at, name)
        SELECT ID, VER, CRT_USER, CRT_MS, UPD_USER, UPD_MS
        FROM RK;
    END IF;

    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'ND' > 0) THEN
        INSERT INTO node (id, ver, created_by, created_at, updated_by, updated_at, cluster_url, name, prior, enable, fk_rack_id)
        SELECT ID, VER, CRT_USER, CRT_MS, UPD_USER, UPD_MS, CLSTR_URL, NAME, PRIOR, ENBL, FK_RK_ID
        FROM ND;
    END IF;

    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'IDX' > 0) THEN
        INSERT INTO index (id, ver, created_by, created_at, updated_by, updated_at, name, description, max_doc, max_shard, partition_by, partition_size, retention_day_age, fields, uuid)
        SELECT ID, VER, CRT_USER, CRT_MS, UPD_USER, UPD_MS, NAME, DESCRIP, MAX_DOC, MAX_SHRD, PART_BY, PART_SZ, RETEN_DAY_AGE, FLDS, UUID
        FROM IDX;
    END IF;

    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'VOL' > 0) THEN
        INSERT INTO volume (id, ver, created_by, created_at, updated_by, updated_at, path, stream_status, volume_type, bytes_limit, bytes_used, bytes_free, bytes_total, status_ms, fk_node_id)
        SELECT V.ID, V.VER, V.CRT_USER, V.CRT_MS, V.UPD_USER, V.UPD_MS, V.PATH, V.IDX_STAT, V.VOL_TP, V.BYTES_LMT, V.FK_ND_ID, S.BYTES_USED, S.BYTES_FREE, S.BYTES_TOTAL, S.STAT_MS
        FROM VOL V INNER JOIN VOL_STATE S ON (V.FK_VOL_STATE_ID = S.ID);
    END IF;

END//
DELIMITER ;
CALL copy();
DROP PROCEDURE copy;