CREATE TABLE IF NOT EXISTS state.owner
(
    owner_id UUID,
    address TEXT,
    name    TEXT,
    PRIMARY KEY (owner_id)
);

CREATE TABLE IF NOT EXISTS state.pet
(
    owner_id UUID,
    pet_id   UUID,
    chip_id  TEXT,
    species  TEXT,
    breed    TEXT,
    color    TEXT,
    gender   TEXT,
    age      INT,
    weight   FLOAT,
    address  TEXT,
    name     TEXT,
    PRIMARY KEY (owner_id, pet_id)
);

CREATE TABLE IF NOT EXISTS state.sensor
(
    pet_id UUID,
    sensor_id UUID,
    type TEXT,
    PRIMARY KEY (pet_id, sensor_id)
);

CREATE TABLE IF NOT EXISTS state.measurement
(
    sensor_id UUID,
    ts    TIMESTAMP,
    value FLOAT,
    PRIMARY KEY (sensor_id, ts)
) WITH compaction = { 'class' : 'TimeWindowCompactionStrategy' };

CREATE TABLE IF NOT EXISTS state.sensor_avg
(
    sensor_id UUID,
    date  DATE,
    hour  INT,
    value FLOAT,
    PRIMARY KEY (sensor_id, date, hour)
) WITH compaction = { 'class' : 'TimeWindowCompactionStrategy' };
