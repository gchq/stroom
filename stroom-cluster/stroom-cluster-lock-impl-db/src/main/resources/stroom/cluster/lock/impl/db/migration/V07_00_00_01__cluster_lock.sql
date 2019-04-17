--
-- Create the cluster_lock table
--
CREATE TABLE IF NOT EXISTS cluster_lock (
  id                    int(11) NOT NULL AUTO_INCREMENT,
  version               int(11) NOT NULL,
  name                  varchar(255) NOT NULL,
  PRIMARY KEY           (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;