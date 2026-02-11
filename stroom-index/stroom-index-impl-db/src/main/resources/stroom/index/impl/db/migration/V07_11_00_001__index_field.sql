-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

ALTER TABLE index_field ADD COLUMN `dense_vector` json DEFAULT NULL;

SET SQL_NOTES=@OLD_SQL_NOTES;
