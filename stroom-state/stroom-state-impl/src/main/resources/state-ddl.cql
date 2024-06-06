CREATE TABLE state.state
(
    map_name        TEXT,
    key             TEXT,
    effective_time  TIMESTAMP,
    value           TEXT,
    PRIMARY KEY     (map_name, key, effective_time)
)  WITH CLUSTERING ORDER BY (effective_time DESC);

