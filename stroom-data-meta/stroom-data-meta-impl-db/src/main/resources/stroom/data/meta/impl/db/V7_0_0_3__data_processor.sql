-- Create the data_processor table
-- processor_id comes from the processor table in stroom-process but there is no FK
-- between them
CREATE TABLE IF NOT EXISTS data_processor (
  id 				    int(11) NOT NULL AUTO_INCREMENT,
  pipeline_uuid 	    varchar(255) NOT NULL,
  processor_id   	    int(11) NOT NULL,
  PRIMARY KEY           (id),
  UNIQUE KEY            processor_id (processor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Copy data into the data_processor table, use ID predicate to make it re-runnable
INSERT
INTO data_processor (id, pipeline_uuid, processor_id)
SELECT SP.ID, P.UUID, SP.ID
FROM STRM_PROC SP
INNER JOIN PIPE P ON (P.ID = SP.FK_PIPE_ID)
WHERE SP.ID > (SELECT COALESCE(MAX(dp.id), 0) FROM data_processor dp)
ORDER BY SP.ID;

-- Work out what to set our auto_increment start value to
SELECT COALESCE(MAX(id) + 1, 1)
INTO @next_id
FROM data_processor

ALTER TABLE data_processor AUTO_INCREMENT=@next_id;
