--
-- Create the processor_feed table
--
CREATE TABLE IF NOT EXISTS processor_feed (
                                       id 				    int(11) NOT NULL AUTO_INCREMENT,
                                       name				    varchar(255) NOT NULL,
                                       PRIMARY KEY           (id),
                                       UNIQUE KEY            name (name)
                                     ) ENGINE=InnoDB DEFAULT CHARSET=utf8;