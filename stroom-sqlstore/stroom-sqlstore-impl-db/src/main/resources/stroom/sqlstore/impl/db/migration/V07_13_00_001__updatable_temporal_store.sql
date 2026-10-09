--
-- Create the updatable_temporal_store table
--
-- Scoping is by doc_uuid, never by map_name. Document names in stroom are mutable and not
-- unique, so keying on the name meant a rename orphaned every row and two same-named store
-- documents shared one dataset. map_name is retained only as a denormalised label for the
-- Data tab and the `Map` query field; it may be stale after a rename and must never be used
-- to scope a read or a write.
--
CREATE TABLE IF NOT EXISTS updatable_temporal_store (
  doc_uuid            varchar(255) NOT NULL,
  map_name            varchar(255) NOT NULL,
  map_key             varchar(255) NOT NULL,
  effective_time      bigint NOT NULL,
  map_value           longtext,
  PRIMARY KEY (doc_uuid, map_key, effective_time),
  KEY updatable_temporal_store_map_name_idx (map_name)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
