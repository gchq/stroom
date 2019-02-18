--
-- Create the explorer tables
--
CREATE TABLE IF NOT EXISTS explorer_node (
  id                    int(11) NOT NULL AUTO_INCREMENT,
  type                  varchar(255) NOT NULL,
  uuid                  varchar(255) NOT NULL,
  name                  varchar(255) NOT NULL,
  tags                  varchar(255) DEFAULT NULL,
  PRIMARY KEY           (id),
  UNIQUE KEY            type (type,uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS explorer_path (
  ancestor              int(11) NOT NULL,
  descendant            int(11) NOT NULL,
  depth                 int(11) NOT NULL,
  order_index            int(11) NOT NULL,
  PRIMARY KEY           (ancestor,descendant)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


--
-- Copy data into the explorer table
--
DROP PROCEDURE IF EXISTS copy;
DELIMITER //
CREATE PROCEDURE copy ()
BEGIN
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'explorerTreeNode' > 0) THEN
        INSERT INTO explorer_node (id, type, uuid, name, tags)
        SELECT id, type, uuid, name, tags
        FROM explorerTreeNode;
    END IF;
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'OLD_explorerTreeNode' > 0) THEN
        INSERT INTO explorer_node (id, type, uuid, name, tags)
        SELECT id, type, uuid, name, tags
        FROM OLD_explorerTreeNode;
    END IF;

    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'explorerTreePath' > 0) THEN
        INSERT INTO explorer_path (ancestor, descendant, depth, order_index)
        SELECT ancestor, descendant, depth, orderIndex
        FROM explorerTreePath;
    END IF;
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'OLD_explorerTreePath' > 0) THEN
        INSERT INTO explorer_path (ancestor, descendant, depth, order_index)
        SELECT ancestor, descendant, depth, orderIndex
        FROM OLD_explorerTreePath;
    END IF;
END//
DELIMITER ;
CALL copy();
DROP PROCEDURE copy;