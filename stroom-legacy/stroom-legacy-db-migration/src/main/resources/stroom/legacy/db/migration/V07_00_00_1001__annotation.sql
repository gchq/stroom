-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

ALTER TABLE annotation              CONVERT TO CHARACTER SET utf8mb4;
ALTER TABLE annotation              CHANGE COLUMN id                id                  bigint NOT NULL AUTO_INCREMENT;
ALTER TABLE annotation              CHANGE COLUMN version           version             int NOT NULL;
ALTER TABLE annotation              CHANGE COLUMN create_time_ms    create_time_ms      bigint NOT NULL;
ALTER TABLE annotation              CHANGE COLUMN update_time_ms    update_time_ms      bigint NOT NULL;

ALTER TABLE annotation_entry        CONVERT TO CHARACTER SET utf8mb4;
ALTER TABLE annotation_entry        CHANGE COLUMN id                id                  bigint NOT NULL AUTO_INCREMENT;
ALTER TABLE annotation_entry        CHANGE COLUMN version           version             int NOT NULL;
ALTER TABLE annotation_entry        CHANGE COLUMN create_time_ms    create_time_ms      bigint NOT NULL;
ALTER TABLE annotation_entry        CHANGE COLUMN update_time_ms    update_time_ms      bigint NOT NULL;
ALTER TABLE annotation_entry        CHANGE COLUMN fk_annotation_id  fk_annotation_id    bigint NOT NULL;

ALTER TABLE annotation_data_link    CONVERT TO CHARACTER SET utf8mb4;
ALTER TABLE annotation_data_link    CHANGE COLUMN id                id                  bigint NOT NULL AUTO_INCREMENT;
ALTER TABLE annotation_data_link    CHANGE COLUMN fk_annotation_id  fk_annotation_id    bigint NOT NULL;
ALTER TABLE annotation_data_link    CHANGE COLUMN stream_id         stream_id           bigint NOT NULL;
ALTER TABLE annotation_data_link    CHANGE COLUMN event_id          event_id            bigint NOT NULL;

SET SQL_NOTES=@OLD_SQL_NOTES;
