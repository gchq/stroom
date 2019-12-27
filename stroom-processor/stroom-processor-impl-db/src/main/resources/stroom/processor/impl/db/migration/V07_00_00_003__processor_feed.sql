-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

--
-- Create the processor_feed table
--
CREATE TABLE IF NOT EXISTS processor_feed (
                                       id 				    int(11) NOT NULL AUTO_INCREMENT,
                                       name				    varchar(255) NOT NULL,
                                       PRIMARY KEY           (id),
                                       UNIQUE KEY            name (name)
                                     ) ENGINE=InnoDB DEFAULT CHARSET=utf8;

SET SQL_NOTES=@OLD_SQL_NOTES;
