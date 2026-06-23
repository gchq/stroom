# Split `doc` Table into `doc` + `doc_data` + `doc_audit` + `doc_data_snapshot` + `doc_audit_data_snapshot`

## Background

The `doc` table currently stores multiple rows per logical document, differentiated by the `ext` column. A single document (identified by `type` + `uuid`) typically has:

- An `ext = 'meta'` row containing JSON metadata (type, uuid, name, version, audit fields, plus type-specific configuration)
- One or more content rows with other `ext` values (e.g., `xsl`, `js`, `txt`, `json`) containing the document's content data

All data is stored in a single `longblob` column regardless of actual content type. The goal is to normalise this into separate tables: a `doc` identity table, a typed `doc_data` table for content, a `doc_audit` table for operation tracking, a `doc_data_snapshot` table for deduplicated data snapshots, and a `doc_audit_data_snapshot` link table connecting audit entries to the data snapshots that were current after each operation.

### Current Schema

```sql
CREATE TABLE doc (
  id       bigint NOT NULL AUTO_INCREMENT,
  type     varchar(255) NOT NULL,
  uuid     varchar(255) NOT NULL,
  name     varchar(255) NOT NULL,
  data     longblob,               -- JSON metadata OR content data, depending on ext
  ext      varchar(255) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY doc_type_uuid_ext_idx (type, uuid, ext),
  KEY doc_type_uuid_idx (type, uuid),
  UNIQUE KEY doc_uuid_idx (uuid),
  KEY doc_type_name_uuid_idx (type, name, uuid)
);
```

### Scope of Change

Changes required:
- [Persistence.java](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl/src/main/java/stroom/docstore/impl/Persistence.java) — `write()` signature changes from `boolean update` to `AuditAction`
- [StoreImpl.java](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl/src/main/java/stroom/docstore/impl/StoreImpl.java) — update all `persistence.write()` call sites to pass the correct `AuditAction`
- [DBPersistence.java](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl-db/src/main/java/stroom/docstore/impl/db/DBPersistence.java) — rewrite to use jOOQ DSL instead of raw JDBC
- [FSPersistence.java](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl-fs/src/main/java/stroom/docstore/impl/fs/FSPersistence.java) — update `write()` signature (no audit logic needed)
- [MemoryPersistence.java](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl-memory/src/main/java/stroom/docstore/impl/memory/MemoryPersistence.java) — update `write()` signature (no audit logic needed)
- [AuditAction](file:///home/stroomdev66/work/stroom-7.10/stroom-docref/src/main/java/stroom/docref/DocAuditEntry.java#L80-L95) enum — add new action values for import/export/copy/move/rename
- **[NEW]** `stroom-docstore-impl-db-jooq` module — generated jOOQ table classes for `doc`, `doc_data`, `doc_audit`, `doc_data_snapshot`, `doc_audit_data_snapshot`
- [build.gradle](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl-db/build.gradle) — add jOOQ module dependency
- [settings.gradle](file:///home/stroomdev66/work/stroom-7.10/settings.gradle) — register new jOOQ module
- Flyway migration scripts — new tables + data migration

---

## Resolved Design Decisions

1. **Single sparse table**: Use one `doc_data` table with typed columns (`json_data`, `text_data`, `bin_data`) instead of 3 separate tables. Each row populates exactly one data column based on the `DocDataType` provided by the serialiser. This dramatically simplifies reads, writes, and history tracking.
2. **Meta JSON**: Moves OUT of the `doc` table into `doc_data`. The `doc` table becomes a pure identity table (`id`, `type`, `uuid`, `name`, `deleted`).
3. **Each row has an `ext` column** to distinguish sub-types (e.g., `meta`, `xsl`, `json`, `txt`).
4. **Data type is caller-provided**: The `DocDataType` (JSON, TEXT, BINARY) is set by the serialiser on each `ImportExportAsset`, not inferred by the persistence layer from the extension. `DBPersistence` simply reads `asset.getDocDataType()` and writes to the correct column.
5. **Deduplicated snapshot tracking**: Data snapshots after each CREATE, UPDATE, and IMPORT are stored in `doc_data_snapshot` (one row per unique data version). A link table `doc_audit_data_snapshot` connects audit entries to the data snapshots that were current after that operation. Multiple audit entries can reference the same snapshot row if the data didn't change, avoiding unnecessary duplication.
6. **`Persistence.write()` accepts `AuditAction`** instead of `boolean update` — gives precise audit logging at the persistence layer.
7. **Logical delete**: Documents are soft-deleted by setting a `deleted` timestamp (epoch millis) on `doc`. All data, audit, and snapshots survive indefinitely. Physical cleanup of old soft-deleted items can be added later.
8. **jOOQ for type-safe queries**: `DBPersistence` will use jOOQ DSL via `JooqUtil` helpers instead of hand-crafted JDBC SQL strings, consistent with all other Stroom DAO implementations.

---

## New Schema Design

### Doc Identity Table

```sql
-- doc table: pure identity table, one row per logical document
CREATE TABLE doc (
  id       bigint NOT NULL AUTO_INCREMENT,
  type     varchar(255) NOT NULL,
  uuid     varchar(255) NOT NULL,
  name     varchar(255) NOT NULL,
  deleted  bigint DEFAULT NULL,      -- epoch millis when soft-deleted, NULL = active
  PRIMARY KEY (id),
  UNIQUE KEY doc_uuid_idx (uuid),
  KEY doc_type_name_uuid_idx (type, name, uuid),
  KEY doc_deleted_idx (deleted)
);
```

> [!NOTE]
> The `deleted` column enables **logical (soft) delete**. When a document is deleted, `deleted` is set to the current epoch millis. All read/list/find queries filter on `deleted IS NULL` to exclude soft-deleted documents. Soft-deleted documents can be undeleted by setting `deleted = NULL`. A future scheduled job can physically delete old soft-deleted documents where `deleted < (now - retention)` by deleting in a transaction in defined order: `doc_audit_data_snapshot` → `doc_data_snapshot` → `doc_audit` → `doc_data` → `doc`.

### Doc Data Table

```sql
-- doc_data: single sparse table with typed columns for all document content
CREATE TABLE doc_data (
  id        bigint NOT NULL AUTO_INCREMENT,
  fk_doc_id bigint NOT NULL,
  ext       varchar(255) NOT NULL,
  data_type tinyint NOT NULL,          -- 1=JSON, 2=TEXT, 3=BINARY — indicates which data column is populated
  json_data json,                     -- populated when data_type = 1
  text_data longtext,                 -- populated when data_type = 2
  bin_data  longblob,                 -- populated when data_type = 3
  PRIMARY KEY (id),
  UNIQUE KEY doc_data_fk_doc_id_ext_idx (fk_doc_id, ext),
  CONSTRAINT doc_data_fk_doc_id FOREIGN KEY (fk_doc_id) REFERENCES doc (id)
);
```

A `DocDataType` enum (following the `AnnotationTagType` pattern) implements both `HasDisplayValue` and `HasPrimitiveValue`:

```java
public enum DocDataType implements HasDisplayValue, HasPrimitiveValue {
    JSON("JSON", 1),
    TEXT("Text", 2),
    BINARY("Binary", 3);

    public static final PrimitiveValueConverter<DocDataType> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(DocDataType.class, DocDataType.values());
    private final String displayValue;
    private final byte primitiveValue;

    DocDataType(final String displayValue, final int primitiveValue) {
        this.displayValue = displayValue;
        this.primitiveValue = (byte) primitiveValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }
}
```

The `data_type` column makes the table self-describing — on read, use `DocDataType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(value)` to know which column to read. On write, `DBPersistence` reads `asset.getDocDataType()` directly — no extension-to-type mapping is needed at the persistence layer.

### Audit Table

```sql
-- Audit trail: one row per CRUD/import/export operation on a document
CREATE TABLE doc_audit (
  id           bigint NOT NULL AUTO_INCREMENT,
  fk_doc_id    bigint NOT NULL,
  action       tinyint NOT NULL,                  -- AuditAction primitive value (see AuditAction.getPrimitiveValue())
  action_time  bigint NOT NULL,                   -- epoch millis
  user_uuid    varchar(255) DEFAULT NULL,         -- stroom_user UUID
  user_name    varchar(255) DEFAULT NULL,         -- user display name
  PRIMARY KEY (id),
  KEY doc_audit_fk_doc_id_idx (fk_doc_id),
  KEY doc_audit_action_time_idx (action_time),
  CONSTRAINT doc_audit_fk_doc_id FOREIGN KEY (fk_doc_id) REFERENCES doc (id)
);
```

### Data Snapshot and Audit Link Tables

```sql
-- doc_data_snapshot: deduplicated data snapshots, each row is a unique snapshot of a (doc, ext) pair
CREATE TABLE doc_data_snapshot (
  id        bigint NOT NULL AUTO_INCREMENT,
  fk_doc_id bigint NOT NULL,
  ext       varchar(255) NOT NULL,
  data_type tinyint NOT NULL,          -- 1=JSON, 2=TEXT, 3=BINARY
  data_hash varchar(64) NOT NULL,     -- SHA-256 hex of the data content, for deduplication
  json_data json,
  text_data longtext,
  bin_data  longblob,
  PRIMARY KEY (id),
  KEY doc_data_snapshot_dedup_idx (fk_doc_id, ext, data_hash),
  KEY doc_data_snapshot_fk_doc_id_idx (fk_doc_id),
  CONSTRAINT doc_data_snapshot_fk_doc_id FOREIGN KEY (fk_doc_id) REFERENCES doc (id)
);
```

```sql
-- doc_audit_data_snapshot: link table connecting audit entries to the data snapshots current after that operation
CREATE TABLE doc_audit_data_snapshot (
  id                    bigint NOT NULL AUTO_INCREMENT,
  fk_doc_audit_id       bigint NOT NULL,
  fk_doc_data_snapshot_id  bigint NOT NULL,
  PRIMARY KEY (id),
  KEY doc_audit_data_snapshot_fk_doc_audit_id_idx (fk_doc_audit_id),
  KEY doc_audit_data_snapshot_fk_doc_data_snapshot_id_idx (fk_doc_data_snapshot_id),
  CONSTRAINT doc_audit_data_snapshot_fk_doc_audit_id FOREIGN KEY (fk_doc_audit_id) REFERENCES doc_audit (id),
  CONSTRAINT doc_audit_data_snapshot_fk_doc_data_snapshot_id FOREIGN KEY (fk_doc_data_snapshot_id) REFERENCES doc_data_snapshot (id)
);
```

The `doc_data_snapshot` table stores each **unique data snapshot** after an operation. The `data_hash` column (SHA-256 of the data content) combined with `(fk_doc_id, ext)` forms a non-unique index for fast candidate lookup during deduplication. On write, the code looks up candidates by hash, then compares actual data content to confirm a true match before reusing a row. This avoids any risk of hash collision causing incorrect deduplication or insert failure.

The `doc_audit_data_snapshot` link table connects each audit entry to the set of `doc_data_snapshot` rows that represent the document's snapshot **after** that operation. This means:
- If only XSL changes on an update, the new audit entry links to a NEW snapshot row for `xsl` but the SAME snapshot row for `meta` as the previous audit
- Multiple consecutive read-only operations or updates that don't change a particular ext all point to the same snapshot row
- Reconstruct the snapshot at any audit point: `SELECT * FROM doc_data_snapshot s JOIN doc_audit_data_snapshot l ON l.fk_doc_data_snapshot_id = s.id WHERE l.fk_doc_audit_id = ?`

### Design Points

**Why a single sparse table instead of 3 separate tables:**

| Aspect | 3 Separate Tables | 1 Sparse Table (chosen) |
|--------|-------------------|------------------------|
| **Read** | 3 queries or UNION | 1 simple `SELECT WHERE fk_doc_id = ?` |
| **Write** | Route each ext to correct table | 1 INSERT/UPDATE, populate the right column |
| **Snapshot tracking** | 3 snapshot tables + 3 link tables | 1 snapshot table + 1 link table |
| **Total tables** | 9+ data-related | 4 data-related |
| **DBPersistence complexity** | Extension routing, per-table SQL | Minimal — column selection only |
| **NULL overhead** | None | ~2 NULL columns per row (negligible in MySQL) |
| **Type safety** | Each column matches its table's type | Same — each ext only populates the matching column |
| **JSON functions** | Work on `doc_data_json.data` | Work on `doc_data.json_data` (same) |
| **Extension category change** | Migrate data between tables | Just populate a different column |

**Other design points:**
- **No cascading deletes** — all FKs use `RESTRICT` (default). Physical cleanup of old soft-deleted documents is done explicitly in a transaction, deleting in order: `doc_audit_data_snapshot` → `doc_data_snapshot` → `doc_audit` → `doc_data` → `doc`
- Normal delete operations are **logical** — `UPDATE doc SET deleted = ? WHERE type = ? AND uuid = ?` (sets epoch millis)
- `ext` column preserves the key used by `ImportExportDocument` and serialisers
- MySQL's `JSON` data type provides automatic validation and enables JSON functions (`JSON_EXTRACT`, `JSON_VALUE`)
- `doc` unique key changes from `(type, uuid, ext)` to `(type, uuid)` — one row per doc

**Data snapshot behaviour (snapshots are after the action):**
- Each `doc_data_snapshot` row represents a unique data version for a specific `(doc, ext)` combination
- `doc_audit_data_snapshot` links record the complete document snapshot **after** each operation
- On **CREATE**: compute hash of each data entry, insert new snapshot rows, link all to the CREATE audit entry
- On **UPDATE**: for each ext, compute hash — if a snapshot with this `(fk_doc_id, ext, data_hash)` already exists, reuse it in the link; otherwise insert a new snapshot row. This deduplicates unchanged data entries
- On **IMPORT**: same as CREATE/UPDATE — snapshot rows are reused or created as needed
- DELETE and EXPORT operations do NOT create snapshot or link entries (no data change)
- FK constraints enforce referential integrity without cascading deletes

> [!IMPORTANT]
> **Storage growth**: Snapshot rows are deduplicated, so unchanged data entries are not duplicated across audit events. However, audit entries and link rows do grow over time. The scheduled `Doc Store - Physical Delete` job handles (1) by physically deleting old soft-deleted documents past the retention period, in a transaction deleting child tables first: `doc_audit_data_snapshot` → `doc_data_snapshot` → `doc_audit` → `doc_data` → `doc`. Optional pruning of old audit entries and orphaned snapshot rows for active documents remains a future concern.

> [!NOTE]
> **JSON column handling in `DBPersistence`**: The serialisers write JSON as `byte[]` via Jackson's `writeValueAsBytes()`. When writing to the `json_data` column, `DBPersistence` will convert `byte[]` → `String` (UTF-8) before calling `setString()` on the prepared statement. When reading, `getString()` returns the JSON string which is then converted back to `byte[]` (UTF-8) for the `ImportExportDocument`. This conversion is lightweight since Jackson already produces UTF-8 bytes.

---

## Extension-to-Column Mapping

Based on analysis of all serialisers in the codebase:

| Extension | Serialiser | Content Format | Target Column |
|-----------|-----------|---------------|--------------|
| `meta` | [JsonSerialiser2](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl/src/main/java/stroom/docstore/impl/JsonSerialiser2.java) (all docs) | JSON bytes | `json_data` |
| `json` | [PipelineSerialiser](file:///home/stroomdev66/work/stroom-7.10/stroom-pipeline/src/main/java/stroom/pipeline/PipelineSerialiser.java), [DashboardSerialiser](file:///home/stroomdev66/work/stroom-7.10/stroom-dashboard/stroom-dashboard-impl/src/main/java/stroom/dashboard/impl/DashboardSerialiser.java), [VisualisationSerialiser](file:///home/stroomdev66/work/stroom-7.10/stroom-dashboard/stroom-dashboard-impl/src/main/java/stroom/dashboard/impl/visualisation/VisualisationSerialiser.java) | JSON bytes | `json_data` |
| `xsl` | [XsltSerialiser](file:///home/stroomdev66/work/stroom-7.10/stroom-pipeline/src/main/java/stroom/pipeline/xslt/XsltSerialiser.java) | Text (XSLT) | `text_data` |
| `xsd` | [XmlSchemaSerialiser](file:///home/stroomdev66/work/stroom-7.10/stroom-pipeline/src/main/java/stroom/pipeline/xmlschema/XmlSchemaSerialiser.java) | Text (XSD) | `text_data` |
| `xml` | [TextConverterSerialiser](file:///home/stroomdev66/work/stroom-7.10/stroom-pipeline/src/main/java/stroom/pipeline/textconverter/TextConverterSerialiser.java) | Text (XML) | `text_data` |
| `js` | [ScriptSerialiser](file:///home/stroomdev66/work/stroom-7.10/stroom-dashboard/stroom-dashboard-impl/src/main/java/stroom/dashboard/impl/script/ScriptSerialiser.java) | Text (JS) | `text_data` |
| `txt` | [DictionarySerialiser](file:///home/stroomdev66/work/stroom-7.10/stroom-dictionary/stroom-dictionary-impl/src/main/java/stroom/dictionary/impl/DictionarySerialiser.java), [DocumentationSerialiser](file:///home/stroomdev66/work/stroom-7.10/stroom-documentation/stroom-documentation-impl/src/main/java/stroom/documentation/impl/DocumentationSerialiser.java) | Text | `text_data` |
| *(unknown)* | — | Binary | `bin_data` |

Each serialiser sets the appropriate `DocDataType` on its `ImportExportAsset` instances. `DBPersistence` reads `asset.getDocDataType()` directly.

---

## AuditAction Enum Changes

#### [MODIFY] [DocAuditEntry.java](file:///home/stroomdev66/work/stroom-7.10/stroom-docref/src/main/java/stroom/docref/DocAuditEntry.java#L80-L95)

Expand the `AuditAction` enum to cover all document lifecycle operations:

```java
public enum AuditAction implements HasDisplayValue, HasPrimitiveValue {
    CREATE("Created", 1),
    UPDATE("Updated", 2),
    DELETE("Deleted", 3),
    IMPORT("Imported", 4),
    EXPORT("Exported", 5),
    COPY("Copied", 6),
    MOVE("Moved", 7),
    RENAME("Renamed", 8);

    public static final PrimitiveValueConverter<AuditAction> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(AuditAction.class, AuditAction.values());
    private final String displayValue;
    private final byte primitiveValue;

    AuditAction(final String displayValue, final int primitiveValue) {
        this.displayValue = displayValue;
        this.primitiveValue = (byte) primitiveValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }
}
```

Currently only `CREATE`, `UPDATE`, `DELETE` exist. The new values map to operations in [StoreImpl](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl/src/main/java/stroom/docstore/impl/StoreImpl.java):

| AuditAction | Triggered by | StoreImpl method |
|-------------|-------------|------------------|
| `CREATE` | New document creation | `create()` → `persistence.write(docRef, false, ...)` |
| `UPDATE` | Document content/name change | `update()` → `persistence.write(docRef, true, ...)` |
| `DELETE` | Document deletion | `deleteDocument()` → `persistence.delete(docRef)` |
| `IMPORT` | Import of document | `importDocument()` → `persistence.write(docRef, ...)` |
| `EXPORT` | Export of document | `exportDocument()` — read-only, logged for audit trail |
| `COPY` | Copy document | `copyDocument()` → `persistence.write(docRef, false, ...)` |
| `MOVE` | Move document in explorer | Rename with different parent |
| `RENAME` | Rename document | `renameDocument()` → `persistence.write(docRef, true, ...)` |

> [!NOTE]
> Since we chose option (a) — `Persistence.write()` accepts `AuditAction` — each call site in `StoreImpl` will pass the precise action. The `boolean update` parameter is replaced by `AuditAction`, and whether it's a create or update can be inferred from the action (e.g., `CREATE`, `COPY` → insert; `UPDATE`, `RENAME`, `IMPORT` → upsert). See Persistence Interface Changes below.

---

## Proposed Changes

### Migration Script

#### [NEW] [V07_14_00_001__split_doc_table.sql](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl-db/src/main/resources/stroom/docstore/impl/db/migration/V07_14_00_001__split_doc_table.sql)

A Flyway versioned migration that:

1. **Creates `doc_data`** table with sparse typed columns
2. **Migrates meta JSON** (`ext = 'meta'`) into `json_data` column — validates with `JSON_VALID()` first; invalid rows are diverted to `text_data` for manual review
3. **Migrates content JSON** (`ext = 'json'`) into `json_data` column — same validation guard; invalid rows diverted to `text_data`
4. **Migrates text content** (`ext IN ('xsl', 'xsd', 'xml', 'js', 'txt')`) into `text_data` column
5. **Migrates remaining content** (unknown extensions) into `bin_data` column
6. **Removes all non-meta rows from `doc`**
7. **Drops `data` and `ext` columns from `doc`**, adds `deleted` column, updates unique key
8. **Creates `doc_audit` table**
9. **Creates `doc_data_snapshot` table**
10. **Creates `doc_audit_data_snapshot` link table**
11. **Seeds initial audit entries** from existing meta JSON

```sql
-- Step 1: Create doc_data table
CREATE TABLE IF NOT EXISTS doc_data (
  id        bigint NOT NULL AUTO_INCREMENT,
  fk_doc_id bigint NOT NULL,
  ext       varchar(255) NOT NULL,
  data_type tinyint NOT NULL,
  json_data json,
  text_data longtext,
  bin_data  longblob,
  PRIMARY KEY (id),
  UNIQUE KEY doc_data_fk_doc_id_ext_idx (fk_doc_id, ext),
  CONSTRAINT doc_data_fk_doc_id FOREIGN KEY (fk_doc_id) REFERENCES doc (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Step 1.5: Pre-migration JSON validation guard
-- Abort early if any meta/json rows have corrupt UTF-8 bytes (CONVERT returns NULL)
-- or invalid JSON content. These rows would cause CAST(... AS JSON) to fail.
-- Instead of failing, invalid rows are diverted to text_data for manual review.

-- Step 2a: Migrate VALID meta JSON → doc_data.json_data (meta row references itself)
INSERT INTO doc_data (fk_doc_id, ext, data_type, json_data)
SELECT id, ext, 1, CAST(CONVERT(data USING utf8mb4) AS JSON)
FROM doc
WHERE ext = 'meta'
  AND data IS NOT NULL
  AND CONVERT(data USING utf8mb4) IS NOT NULL
  AND JSON_VALID(CONVERT(data USING utf8mb4)) = 1;

-- Step 2b: Migrate INVALID meta rows → doc_data.text_data as fallback
-- These rows had data that could not be parsed as JSON.
-- They are stored as text for manual inspection and correction.
INSERT INTO doc_data (fk_doc_id, ext, data_type, text_data)
SELECT id, ext, 2, CONVERT(data USING utf8mb4)
FROM doc
WHERE ext = 'meta'
  AND data IS NOT NULL
  AND (CONVERT(data USING utf8mb4) IS NULL
       OR JSON_VALID(CONVERT(data USING utf8mb4)) = 0);

-- Step 3a: Migrate VALID content JSON → doc_data.json_data (references the meta row)
INSERT INTO doc_data (fk_doc_id, ext, data_type, json_data)
SELECT dm.id, d.ext, 1, CAST(CONVERT(d.data USING utf8mb4) AS JSON)
FROM doc d
JOIN doc dm ON dm.type = d.type AND dm.uuid = d.uuid AND dm.ext = 'meta'
WHERE d.ext = 'json'
  AND d.data IS NOT NULL
  AND CONVERT(d.data USING utf8mb4) IS NOT NULL
  AND JSON_VALID(CONVERT(d.data USING utf8mb4)) = 1;

-- Step 3b: Migrate INVALID content JSON rows → doc_data.text_data as fallback
INSERT INTO doc_data (fk_doc_id, ext, data_type, text_data)
SELECT dm.id, d.ext, 2, CONVERT(d.data USING utf8mb4)
FROM doc d
JOIN doc dm ON dm.type = d.type AND dm.uuid = d.uuid AND dm.ext = 'meta'
WHERE d.ext = 'json'
  AND d.data IS NOT NULL
  AND (CONVERT(d.data USING utf8mb4) IS NULL
       OR JSON_VALID(CONVERT(d.data USING utf8mb4)) = 0);

-- Step 4: Migrate text content → doc_data.text_data
INSERT INTO doc_data (fk_doc_id, ext, data_type, text_data)
SELECT dm.id, d.ext, 2, CONVERT(d.data USING utf8mb4)
FROM doc d
JOIN doc dm ON dm.type = d.type AND dm.uuid = d.uuid AND dm.ext = 'meta'
WHERE d.ext IN ('xsl', 'xsd', 'xml', 'js', 'txt');

-- Step 5: Migrate remaining → doc_data.bin_data
INSERT INTO doc_data (fk_doc_id, ext, data_type, bin_data)
SELECT dm.id, d.ext, 3, d.data
FROM doc d
JOIN doc dm ON dm.type = d.type AND dm.uuid = d.uuid AND dm.ext = 'meta'
WHERE d.ext NOT IN ('meta', 'json', 'xsl', 'xsd', 'xml', 'js', 'txt')
AND d.ext IS NOT NULL;

-- Step 6: Delete all non-meta rows from doc
DELETE FROM doc WHERE ext != 'meta' OR ext IS NULL;

-- Step 7: Drop data and ext columns, update indexes
ALTER TABLE doc DROP KEY doc_type_uuid_ext_idx;
 ALTER TABLE doc DROP KEY doc_type_uuid_idx;
ALTER TABLE doc DROP KEY doc_uuid_idx;
ALTER TABLE doc ADD UNIQUE KEY doc_uuid_idx (uuid);
ALTER TABLE doc DROP COLUMN data;
ALTER TABLE doc DROP COLUMN ext;
ALTER TABLE doc ADD COLUMN deleted bigint DEFAULT NULL;
ALTER TABLE doc ADD KEY doc_deleted_idx (deleted);

-- Step 8: Create doc_audit table
CREATE TABLE IF NOT EXISTS doc_audit (...);

-- Step 9: Create doc_data_snapshot table
CREATE TABLE IF NOT EXISTS doc_data_snapshot (...);

-- Step 10: Create doc_audit_data_snapshot link table
CREATE TABLE IF NOT EXISTS doc_audit_data_snapshot (...);

-- Step 10: Seed initial audit entries from existing meta JSON
INSERT INTO doc_audit (fk_doc_id, action, action_time, user_uuid, user_name)
SELECT d.id, 'CREATE',
       COALESCE(JSON_VALUE(dd.json_data, '$.createTimeMs' RETURNING SIGNED), 0),
       NULL,
       JSON_VALUE(dd.json_data, '$.createUser' RETURNING CHAR(255))
FROM doc d
JOIN doc_data dd ON dd.fk_doc_id = d.id AND dd.ext = 'meta';

INSERT INTO doc_audit (fk_doc_id, action, action_time, user_uuid, user_name)
SELECT d.id, 'UPDATE',
       COALESCE(JSON_VALUE(dd.json_data, '$.updateTimeMs' RETURNING SIGNED), 0),
       NULL,
       JSON_VALUE(dd.json_data, '$.updateUser' RETURNING CHAR(255))
FROM doc d
JOIN doc_data dd ON dd.fk_doc_id = d.id AND dd.ext = 'meta'
WHERE JSON_VALUE(dd.json_data, '$.updateTimeMs' RETURNING SIGNED) IS NOT NULL;
```

> [!NOTE]
> The initial audit entries seeded from meta JSON will have `user_uuid = NULL` because the current meta JSON only stores the user's display name string (e.g., `"admin"`), not their UUID. Future audit entries created by `DBPersistence` will populate both `user_uuid` and `user_name` from `SecurityContext`.

#### [MODIFY] [R__docstore_views.sql](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl-db/src/main/resources/stroom/docstore/impl/db/migration/R__docstore_views.sql)

Update the `v_doc` and `v_feed_doc` views to use the new schema:
- `v_doc`: Join `doc` with `doc_data` (where `ext = 'meta'`) for metadata, and `LEFT JOIN` with `doc_data` for content
- `v_feed_doc`: Same approach, extracting JSON fields from `doc_data.json_data` where `ext = 'meta'`

---

### Persistence Interface Changes

#### [MODIFY] [Persistence.java](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl/src/main/java/stroom/docstore/impl/Persistence.java)

Change the `write()` signature to accept `AuditAction` instead of `boolean update`:

```diff
-void write(DocRef docRef, boolean update, ImportExportDocument importExportDocument) throws IOException;
+void write(DocRef docRef, AuditAction auditAction, ImportExportDocument importExportDocument) throws IOException;
```

The `AuditAction` replaces the boolean flag. Whether the operation is a create or update can be inferred:
- **Creates a new doc**: `CREATE`, `COPY`
- **Updates existing doc**: `UPDATE`, `RENAME`, `IMPORT` (when doc exists)
- **IMPORT can also create**: when importing a doc that doesn't exist yet

---

### StoreImpl Changes

#### [MODIFY] [StoreImpl.java](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl/src/main/java/stroom/docstore/impl/StoreImpl.java)

Update all 4 `persistence.write()` call sites:

| Line | Current | New |
|------|---------|-----|
| [530](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl/src/main/java/stroom/docstore/impl/StoreImpl.java#L530) `create()` | `write(docRef, false, ...)` | `write(docRef, AuditAction.CREATE, ...)` |
| [665](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl/src/main/java/stroom/docstore/impl/StoreImpl.java#L665) `update()` | `write(docRef, true, ...)` | `write(docRef, AuditAction.UPDATE, ...)` |
| [411](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl/src/main/java/stroom/docstore/impl/StoreImpl.java#L411) `importDocument()` | `write(docRef, existingDocument != null, ...)` | `write(docRef, AuditAction.IMPORT, ...)` |
| [755](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl/src/main/java/stroom/docstore/impl/StoreImpl.java#L755) `migratePipelines()` | `write(docRef, true, ...)` | `write(docRef, AuditAction.UPDATE, ...)` |

Note: `renameDocument()` calls `update()` internally, so it inherits `AuditAction.UPDATE`. If you want `RENAME` to be a distinct audit action, `renameDocument()` could be modified to call `persistence.write()` directly with `AuditAction.RENAME` instead of going through `update()`. This can be refined later.

---

### FSPersistence and MemoryPersistence Changes

#### [MODIFY] [FSPersistence.java](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl-fs/src/main/java/stroom/docstore/impl/fs/FSPersistence.java)
#### [MODIFY] [MemoryPersistence.java](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl-memory/src/main/java/stroom/docstore/impl/memory/MemoryPersistence.java)

Both implementations need their `write()` method signature updated to match the new interface:

```diff
-public void write(DocRef docRef, boolean update, ImportExportDocument importExportDocument) throws IOException {
+public void write(DocRef docRef, AuditAction auditAction, ImportExportDocument importExportDocument) throws IOException {
```

Internally, where they currently check `if (update)`, they should instead check:
```java
boolean update = auditAction.isUpdate(); // or check against a set of update actions
```

These implementations do **not** need audit or history logic — they are filesystem/in-memory stores used for testing and legacy purposes.

### DBPersistence Changes

#### [MODIFY] [DBPersistence.java](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl-db/src/main/java/stroom/docstore/impl/db/DBPersistence.java)

Rewrite from raw JDBC to jOOQ DSL. All raw SQL string constants and `PreparedStatement`/`ResultSet` code is replaced with type-safe jOOQ queries using generated table references (`DOC`, `DOC_DATA`, `DOC_AUDIT`, `DOC_DATA_SNAPSHOT`, `DOC_AUDIT_DATA_SNAPSHOT`) and `JooqUtil` helpers.

**1. Constructor** — Inject `DocStoreDbConnProvider` (used with `JooqUtil`) and `SecurityContext`:
```java
@Inject
DBPersistence(final DocStoreDbConnProvider connProvider,
              final SecurityContext securityContext) {
    this.connProvider = connProvider;
    this.securityContext = securityContext;
}
```

**2. No extension-to-type mapping** — `DBPersistence` does not map extensions to data types. It reads `asset.getDocDataType()` directly from each `ImportExportAsset`, which is set by the serialiser.

**3. `read(DocRef)` method** — jOOQ rewrite:
```java
return JooqUtil.contextResult(connProvider, ctx -> {
    // Look up doc.id
    final Long docId = ctx.select(DOC.ID)
            .from(DOC)
            .where(DOC.TYPE.eq(docRef.getType()))
            .and(DOC.UUID.eq(docRef.getUuid()))
            .and(DOC.DELETED.isNull())
            .fetchOne(DOC.ID);
    if (docId == null) return null;

    // Fetch all data entries
    final var records = ctx.select(DOC_DATA.EXT, DOC_DATA.DATA_TYPE,
                    DOC_DATA.JSON_DATA, DOC_DATA.TEXT_DATA, DOC_DATA.BIN_DATA)
            .from(DOC_DATA)
            .where(DOC_DATA.FK_DOC_ID.eq(docId))
            .fetch();

    // Build ImportExportDocument from records using DocDataType.PRIMITIVE_VALUE_CONVERTER
    // to determine which column to read, and set DocDataType on each asset
    ...
});
```

**4. `write(DocRef, AuditAction, ImportExportDocument)` method** — jOOQ rewrite using transactions:
```java
JooqUtil.transaction(connProvider, ctx -> {
    final boolean isCreate = auditAction == AuditAction.CREATE || auditAction == AuditAction.COPY;
    Long docId;

    if (isCreate) {
        // Insert doc row
        docId = ctx.insertInto(DOC, DOC.TYPE, DOC.UUID, DOC.NAME)
                .values(docRef.getType(), docRef.getUuid(), docRef.getName())
                .returning(DOC.ID)
                .fetchOne()
                .getId();
    } else if (auditAction == AuditAction.IMPORT) {
        // Import: check if a soft-deleted doc with this UUID exists and undelete it
        docId = ctx.select(DOC.ID).from(DOC)
                .where(DOC.UUID.eq(docRef.getUuid()))
                .fetchOne(DOC.ID);
        if (docId != null) {
            // Undelete and update
            ctx.update(DOC)
                    .set(DOC.NAME, docRef.getName())
                    .set(DOC.DELETED, (Long) null)
                    .where(DOC.ID.eq(docId)).execute();
        } else {
            // Brand new import
            docId = ctx.insertInto(DOC, DOC.TYPE, DOC.UUID, DOC.NAME)
                    .values(docRef.getType(), docRef.getUuid(), docRef.getName())
                    .returning(DOC.ID)
                    .fetchOne()
                    .getId();
        }
    } else {
        // Update doc name, get id
        docId = ctx.select(DOC.ID).from(DOC)
                .where(DOC.TYPE.eq(docRef.getType()))
                .and(DOC.UUID.eq(docRef.getUuid()))
                .and(DOC.DELETED.isNull())
                .fetchOne(DOC.ID);
        ctx.update(DOC).set(DOC.NAME, docRef.getName())
                .where(DOC.ID.eq(docId)).execute();
    }

    // Upsert each data entry using ON DUPLICATE KEY UPDATE
    for (var asset : document.getAssets()) {
        final String dataType = resolveDataType(asset.getExt());
        ctx.insertInto(DOC_DATA, DOC_DATA.FK_DOC_ID, DOC_DATA.EXT,
                        DOC_DATA.DATA_TYPE, DOC_DATA.JSON_DATA, ...)
                .values(...)
                .onDuplicateKeyUpdate()
                .set(DOC_DATA.DATA_TYPE, dataType)
                .set(DOC_DATA.JSON_DATA, ...)
                .execute();
    }

    // Clean up removed extensions
    ctx.deleteFrom(DOC_DATA)
            .where(DOC_DATA.FK_DOC_ID.eq(docId))
            .and(DOC_DATA.EXT.notIn(currentExts))
            .execute();

    // Insert audit entry
    final long auditId = ctx.insertInto(DOC_AUDIT, ...)
            .values(docId, auditAction.name(), System.currentTimeMillis(), ...)
            .returning(DOC_AUDIT.ID)
            .fetchOne()
            .getId();

    // Insert history snapshots for all data entries
    for (var asset : document.getAssets()) {
        ctx.insertInto(DOC_DATA_HISTORY, ...)
                .values(auditId, docId, ...)
                .execute();
    }
});
```

**5. `delete(DocRef)` method** — Logical delete with jOOQ:
```java
JooqUtil.transaction(connProvider, ctx -> {
    final long now = System.currentTimeMillis();
    final Long docId = ctx.select(DOC.ID).from(DOC)
            .where(DOC.TYPE.eq(docRef.getType()))
            .and(DOC.UUID.eq(docRef.getUuid()))
            .and(DOC.DELETED.isNull())
            .fetchOne(DOC.ID);

    // Logical delete — set timestamp
    ctx.update(DOC).set(DOC.DELETED, now)
            .where(DOC.ID.eq(docId)).execute();

    // Audit entry
    ctx.insertInto(DOC_AUDIT, DOC_AUDIT.FK_DOC_ID, DOC_AUDIT.ACTION,
                    DOC_AUDIT.ACTION_TIME, DOC_AUDIT.USER_UUID, DOC_AUDIT.USER_NAME)
            .values(docId, AuditAction.DELETE.getPrimitiveValue(), now, ...)
            .execute();
});
```

**6. Simplified methods** — All use jOOQ DSL with `DOC.DELETED.isNull()` condition:
```java
// exists
JooqUtil.contextResult(connProvider, ctx ->
    ctx.fetchExists(ctx.selectFrom(DOC)
        .where(DOC.TYPE.eq(type)).and(DOC.UUID.eq(uuid)).and(DOC.DELETED.isNull())));

// list
JooqUtil.contextResult(connProvider, ctx ->
    ctx.select(DOC.UUID, DOC.NAME).from(DOC)
        .where(DOC.TYPE.eq(type)).and(DOC.DELETED.isNull())
        .fetch(r -> new DocRef(type, r.get(DOC.UUID), r.get(DOC.NAME))));

// find with wildcards — jOOQ Condition building replaces dynamic SQL string concatenation
Condition condition = DOC.TYPE.eq(type).and(DOC.DELETED.isNull());
if (nameFilter != null) {
    condition = condition.and(DOC.NAME.like(nameFilter));
}
```

**7. `getAuditInfo(DocRef)` method** — jOOQ query against `doc_audit`:
```java
JooqUtil.contextResult(connProvider, ctx ->
    ctx.select(DOC_AUDIT.ACTION, DOC_AUDIT.ACTION_TIME,
                    DOC_AUDIT.USER_UUID, DOC_AUDIT.USER_NAME)
            .from(DOC_AUDIT)
            .where(DOC_AUDIT.FK_DOC_ID.eq(
                    ctx.select(DOC.ID).from(DOC)
                            .where(DOC.TYPE.eq(type).and(DOC.UUID.eq(uuid)))))
            .orderBy(DOC_AUDIT.ACTION_TIME.desc())
            .fetch(r -> new DocAuditEntry(...)));
```
Note: `getAuditInfo()` intentionally omits the `DELETED.isNull()` filter — this allows viewing audit history for soft-deleted documents too.

**8. `findDocRefsEmbeddedIn(DocRef)`** — The JSON_TABLE query is the one case where jOOQ's DSL may not fully cover MySQL's `JSON_TABLE` syntax. Use `ctx.resultQuery(sql, bindings)` for this specific query — a type-safe raw SQL fallback that jOOQ supports.

**9. Audit and snapshot recording** — Integrated into `write()` and `delete()` (see items 4 and 5 above). Since delete is logical, the DELETE audit entry survives alongside the soft-deleted doc row.

**10. Deduplicated snapshots** — On CREATE, UPDATE, and IMPORT, each data entry is hashed (SHA-256) and checked against existing `doc_data_snapshot` rows for this `(fk_doc_id, ext, data_hash)`. If a matching snapshot already exists, it is reused; otherwise a new snapshot row is inserted. Link rows in `doc_audit_data_snapshot` connect the audit entry to the set of snapshot rows representing the document after the operation. To reconstruct the snapshot at any audit point: `SELECT * FROM doc_data_snapshot s JOIN doc_audit_data_snapshot l ON l.fk_doc_data_snapshot_id = s.id WHERE l.fk_doc_audit_id = ?`.

Write logic (within the same transaction):
```java
for (var asset : document.getAssets()) {
    final String hash = sha256(asset.getData());
    // Look up candidate snapshot rows by hash
    final var candidates = ctx.selectFrom(DOC_DATA_SNAPSHOT)
            .where(DOC_DATA_SNAPSHOT.FK_DOC_ID.eq(docId))
            .and(DOC_DATA_SNAPSHOT.EXT.eq(asset.getExt()))
            .and(DOC_DATA_SNAPSHOT.DATA_HASH.eq(hash))
            .fetch();

    Long snapshotId = null;
    for (var candidate : candidates) {
        // Verify actual data matches (guards against hash collision)
        if (dataEquals(candidate, asset)) {
            snapshotId = candidate.getId();
            break;
        }
    }

    if (snapshotId == null) {
        // No matching snapshot — insert new row
        snapshotId = ctx.insertInto(DOC_DATA_SNAPSHOT, ...)
                .values(docId, asset.getExt(), dataType, hash, ...)
                .returning(DOC_DATA_SNAPSHOT.ID)
                .fetchOne().getId();
    }
    // Link audit to snapshot
    ctx.insertInto(DOC_AUDIT_DATA_SNAPSHOT,
                    DOC_AUDIT_DATA_SNAPSHOT.FK_DOC_AUDIT_ID, DOC_AUDIT_DATA_SNAPSHOT.FK_DOC_DATA_SNAPSHOT_ID)
            .values(auditId, snapshotId)
            .execute();
}
```

**11. `physicalDelete(Duration retentionPeriod)` method** — Hard cleanup of old soft-deleted documents:

Deletes all documents where `doc.deleted` is non-null and older than `retentionPeriod` ago. No audit table join needed — the `deleted` timestamp is self-contained. Deletes child rows first in a single transaction to respect FK constraints:

```java
public int physicalDelete(final Duration retentionPeriod) {
    final long cutoff = System.currentTimeMillis() - retentionPeriod.toMillis();

    return JooqUtil.transactionResult(connProvider, ctx -> {
        // Find doc IDs soft-deleted before the cutoff
        final List<Long> docIds = ctx.select(DOC.ID)
                .from(DOC)
                .where(DOC.DELETED.isNotNull())
                .and(DOC.DELETED.le(cutoff))
                .fetch(DOC.ID);

        if (docIds.isEmpty()) {
            return 0;
        }

        // Delete in FK-safe order: leaves first, root last
        // 1. doc_audit_data_snapshot (references doc_audit and doc_data_snapshot)
        ctx.deleteFrom(DOC_AUDIT_DATA_SNAPSHOT)
                .where(DOC_AUDIT_DATA_SNAPSHOT.FK_DOC_AUDIT_ID.in(
                        ctx.select(DOC_AUDIT.ID).from(DOC_AUDIT)
                                .where(DOC_AUDIT.FK_DOC_ID.in(docIds))))
                .execute();

        // 2. doc_data_snapshot (references doc)
        ctx.deleteFrom(DOC_DATA_SNAPSHOT)
                .where(DOC_DATA_SNAPSHOT.FK_DOC_ID.in(docIds))
                .execute();

        // 3. doc_audit (references doc)
        ctx.deleteFrom(DOC_AUDIT)
                .where(DOC_AUDIT.FK_DOC_ID.in(docIds))
                .execute();

        // 4. doc_data (references doc)
        ctx.deleteFrom(DOC_DATA)
                .where(DOC_DATA.FK_DOC_ID.in(docIds))
                .execute();

        // 5. doc (root)
        return ctx.deleteFrom(DOC)
                .where(DOC.ID.in(docIds))
                .execute();
    });
}
```

This method would typically be called by a scheduled job (e.g., daily) with a configurable retention period (e.g., 30 days). It is only relevant for `DBPersistence` — `FSPersistence` and `MemoryPersistence` can provide no-op implementations.



### jOOQ Module

#### [NEW] `stroom-docstore-impl-db-jooq`

New Gradle module following the established pattern (e.g., `stroom-activity-impl-db-jooq`). Contains jOOQ-generated table/record classes for the 5 tables:

```
stroom-docstore/stroom-docstore-impl-db-jooq/
  build.gradle
  src/main/java/stroom/docstore/impl/db/jooq/
    DefaultCatalog.java
    Keys.java
    Stroom.java
    tables/
      Doc.java              — DOC.ID, DOC.TYPE, DOC.UUID, DOC.NAME, DOC.DELETED
      DocData.java          — DOC_DATA.ID, DOC_DATA.FK_DOC_ID, DOC_DATA.EXT, DOC_DATA.DATA_TYPE, DOC_DATA.JSON_DATA, DOC_DATA.TEXT_DATA, DOC_DATA.BIN_DATA
      DocAudit.java         — DOC_AUDIT.ID, DOC_AUDIT.FK_DOC_ID, DOC_AUDIT.ACTION, DOC_AUDIT.ACTION_TIME, DOC_AUDIT.USER_UUID, DOC_AUDIT.USER_NAME
      DocDataSnapshot.java     — DOC_DATA_SNAPSHOT.ID, DOC_DATA_SNAPSHOT.FK_DOC_ID, DOC_DATA_SNAPSHOT.EXT, DOC_DATA_SNAPSHOT.DATA_TYPE, DOC_DATA_SNAPSHOT.DATA_HASH, ...
      DocAuditDataSnapshot.java     — DOC_AUDIT_DATA_SNAPSHOT.ID, DOC_AUDIT_DATA_SNAPSHOT.FK_DOC_AUDIT_ID, DOC_AUDIT_DATA_SNAPSHOT.FK_DOC_DATA_SNAPSHOT_ID
    records/
      DocRecord.java
      DocDataRecord.java
      DocAuditRecord.java
      DocDataSnapshotRecord.java
      DocAuditDataSnapshotRecord.java
```

These classes are generated by the `nu.studer.jooq` Gradle plugin from the migration-created schema, following the same approach used across all other Stroom modules.



#### [MODIFY] [settings.gradle](file:///home/stroomdev66/work/stroom-7.10/settings.gradle)

Register the new module:
```groovy
include 'stroom-docstore:stroom-docstore-impl-db-jooq'
```

#### [MODIFY] [build.gradle](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl-db/build.gradle)

Add jOOQ dependencies:
```diff
 dependencies {
+    implementation project(':stroom-docstore:stroom-docstore-impl-db-jooq')
     implementation project(':stroom-config:stroom-config-common')
     ...
+    implementation libs.jooq
+    implementation libs.jakarta.inject
 }
```

---

### ImportExportAsset Changes

#### [MODIFY] [ImportExportAsset.java](file:///home/stroomdev66/work/stroom-7.10/stroom-importexport/stroom-importexport-api/src/main/java/stroom/importexport/api/ImportExportAsset.java)

Add a `getDocDataType()` method so each asset carries its own data type:

```diff
+import stroom.docstore.shared.DocDataType;
+
 public interface ImportExportAsset {
     String getKey();
+    DocDataType getDocDataType();
     @Nullable InputStream getInputStream() throws IOException;
     byte @Nullable [] getInputData() throws IOException;
 }
```

#### [MODIFY] [ByteArrayImportExportAsset.java](file:///home/stroomdev66/work/stroom-7.10/stroom-importexport/stroom-importexport-api/src/main/java/stroom/importexport/api/ByteArrayImportExportAsset.java)

Add `DocDataType` field and constructor parameter:

```diff
-public ByteArrayImportExportAsset(final String key, final byte[] data) {
+public ByteArrayImportExportAsset(final String key, final DocDataType docDataType, final byte[] data) {
```

#### Serialiser Changes

Each `DocumentSerialiser2` implementation sets the correct `DocDataType` when creating assets:

| Serialiser | `DocDataType` | Rationale |
|-----------|--------------|----------|
| `JsonDocumentSerialiser` | `DocDataType.JSON` | Writes JSON via Jackson |
| Serialisers writing `meta` ext | `DocDataType.JSON` | Meta is always JSON |
| Serialisers writing `xsl`, `xsd`, `xml`, `js`, `txt` exts | `DocDataType.TEXT` | Known text content |
| Serialisers writing binary/unknown exts | `DocDataType.BINARY` | Default for binary content |

> [!NOTE]
> The `DocumentSerialiser2` implementations **do** now need modification (previously listed as "no changes required") to pass `DocDataType` when creating `ImportExportAsset` instances. This is a mechanical change — each serialiser already knows its content type.

---

### No Changes Required

| File | Reason |
|------|--------|
| [DocStoreDBPersistenceDbModule.java](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl-db/src/main/java/stroom/docstore/impl/db/DocStoreDBPersistenceDbModule.java) | Flyway config unchanged |
| [NoLockFactory.java](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl-db/src/main/java/stroom/docstore/impl/db/NoLockFactory.java) | Unrelated |

---

### Scheduled Physical Delete Job

#### [MODIFY] [DocStoreDbPersistenceModule.java](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl-db/src/main/java/stroom/docstore/impl/db/DocStoreDbPersistenceModule.java)

Register a scheduled job for physical deletion of old soft-deleted documents, following the `ContentIndexModule` / `ScheduledJobsBinder` pattern:

```java
import stroom.job.api.ScheduledJobsBinder;
import stroom.util.RunnableWrapper;

@Override
protected void configure() {
    // ... existing bindings ...

    ScheduledJobsBinder.create(binder())
            .bindJobTo(PhysicalDeleteOldDocs.class, builder -> builder
                    .name("Doc Store - Physical Delete")
                    .description("Physically deletes documents that have been soft-deleted " +
                                 "for longer than the configured retention period. " +
                                 "Removes all associated data, audit, and snapshot rows.")
                    .managed(true)
                    .enabledOnBootstrap(true)
                    .enabled(true)
                    .frequencySchedule("1d"));
}

private static class PhysicalDeleteOldDocs extends RunnableWrapper {

    @Inject
    PhysicalDeleteOldDocs(final DBPersistence dbPersistence,
                          final DocStoreConfig docStoreConfig) {
        super(() -> dbPersistence.physicalDelete(
                docStoreConfig.getDeletedDocRetentionPeriod().getDuration()));
    }
}
```

#### [MODIFY] [DocStoreConfig.java](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl-db/src/main/java/stroom/docstore/impl/db/DocStoreConfig.java)

Add a configurable retention period for the physical delete job:

```diff
+import stroom.util.time.StroomDuration;

 public class DocStoreConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

+    public static final StroomDuration DEFAULT_DELETED_DOC_RETENTION_PERIOD = StroomDuration.ofDays(30);
+
     private final DocStoreDbConfig dbConfig;
+    private final StroomDuration deletedDocRetentionPeriod;

     public DocStoreConfig() {
         dbConfig = new DocStoreDbConfig();
+        deletedDocRetentionPeriod = DEFAULT_DELETED_DOC_RETENTION_PERIOD;
     }

     @JsonCreator
     public DocStoreConfig(
-            @JsonProperty("db") final DocStoreDbConfig dbConfig) {
+            @JsonProperty("db") final DocStoreDbConfig dbConfig,
+            @JsonProperty("deletedDocRetentionPeriod") final StroomDuration deletedDocRetentionPeriod) {
         this.dbConfig = dbConfig;
+        this.deletedDocRetentionPeriod = deletedDocRetentionPeriod;
     }

+    @JsonProperty("deletedDocRetentionPeriod")
+    public StroomDuration getDeletedDocRetentionPeriod() {
+        return deletedDocRetentionPeriod;
+    }
 }
```

This produces a YAML config property at `stroom.docstore.deletedDocRetentionPeriod` with a default of `"30d"`. The scheduled job reads this value on each invocation.

---

### Import of Soft-Deleted Documents

When importing a document whose UUID matches an existing soft-deleted doc, the system **undeletes** the doc and performs the import as an update. This is necessary because:

1. `doc.uuid` has a UNIQUE constraint — inserting a new row with the same UUID would violate it
2. The user's intent is to restore/replace the document content, not to create a duplicate
3. The audit trail is preserved — the existing audit history (including the DELETE entry) remains, and a new IMPORT entry is added

The `write()` method handles this in the IMPORT branch (see item 4 in DBPersistence Changes):
- Look up by UUID (ignoring the `deleted` filter)
- If found and soft-deleted: set `deleted = NULL` (undelete) and update the name
- If found and active: normal update
- If not found: insert as new doc

This produces an audit trail like:
```
CREATE → UPDATE → DELETE → IMPORT (undelete + data replaced)
```



## Verification Plan

### Automated Tests

```bash
# Regenerate jOOQ classes (after migration is applied to dev DB)
./gradlew :stroom-docstore:stroom-docstore-impl-db-jooq:generateJooq

# Run the docstore integration test
./gradlew :stroom-app:test --tests "*TestDBPersistence*"

# Run the full docstore module tests
./gradlew :stroom-docstore:stroom-docstore-impl-db:test
```

### Manual Verification

1. **Migration verification** — Apply migration to a test database with existing doc data and verify:
   - `count(doc)` = original count of `ext='meta'` rows
   - `doc` table has no `data` or `ext` columns, has `deleted` column (all `NULL` for existing docs)
   - `count(doc_data WHERE json_data IS NOT NULL)` = original count of `ext IN ('meta', 'json')` rows
   - `count(doc_data WHERE text_data IS NOT NULL)` = original count of `ext IN ('xsl', 'xsd', 'xml', 'js', 'txt')` rows
   - `count(doc_data WHERE bin_data IS NOT NULL)` = original count of remaining non-null `ext` rows
   - All data content matches between old and new tables
   - `count(doc_audit)` = 2× `count(doc)` (one CREATE + one UPDATE per doc from seeded data)

2. **Logical delete test** — Delete a doc and verify:
   - `doc.deleted` is set to current epoch millis
   - `doc_data`, `doc_audit`, `doc_data_snapshot`, `doc_audit_data_snapshot` rows are all still present
   - `exists(docRef)` returns `false`
   - `list(type)` does not include the deleted doc
   - `getAuditInfo(docRef)` still returns the audit trail including the DELETE entry

3. **Undelete test** — Set `doc.deleted = NULL` and verify the doc is visible again in all queries

4. **Views test** — Verify `v_doc` and `v_feed_doc` views return correct data with new schema (filtering out soft-deleted docs)

5. **Round-trip test** — Create, read, update, and delete documents of various types (Pipeline, XSLT, Dictionary, Feed) to verify all serialisers still work correctly through `DBPersistence`

6. **Audit trail test** — Create a doc, update it twice, then call `getAuditInfo()` — verify 3 audit entries (CREATE + 2× UPDATE) with correct timestamps and users

7. **Snapshot deduplication test** — Create a doc (meta + xsl), update only the xsl, then verify:
   - 2 snapshot rows for `xsl` (one per version), but only 1 snapshot row for `meta` (reused)
   - Both audit entries link to the same meta snapshot row
   - Each audit entry links to its own xsl snapshot row
   - Reconstructing snapshot at audit_1 gives (meta_v1, xsl_v1), at audit_2 gives (meta_v1, xsl_v2)

8. **Import snapshot test** — Import a document, verify an IMPORT audit entry exists with corresponding snapshot links (distinguishable from CREATE)

9. **AuditAction precision test** — Verify that each StoreImpl operation produces the correct `AuditAction` in `doc_audit`: CREATE for `create()`, UPDATE for `update()`, IMPORT for `importDocument()`, DELETE for `deleteDocument()`

10. **Physical delete test** — Create a doc, update it, soft-delete it, then:
   - Call `physicalDelete(Duration.ZERO)` — verify all rows removed from `doc`, `doc_data`, `doc_audit`, `doc_data_snapshot`, `doc_audit_data_snapshot`
   - Verify a non-deleted doc is unaffected
   - Call `physicalDelete(Duration.ofDays(30))` on a recently soft-deleted doc — verify it is NOT physically deleted (within retention)
   - Backdate the `deleted` timestamp beyond the retention period — verify it IS now physically deleted

11. **Import of soft-deleted doc test** — Create a doc, soft-delete it, then import a doc with the same UUID:
   - Verify `doc.deleted` is set back to `NULL` (undeleted)
   - Verify the doc is visible again via `exists()`, `list()`, `find()`
   - Verify `doc_data` contains the imported content (not the old content)
   - Verify audit trail shows: CREATE → DELETE → IMPORT
   - Verify snapshot links exist for both the original CREATE and the IMPORT

12. **Scheduled job test** — Verify the `Doc Store - Physical Delete` job is registered and callable

---

## Future Work

Items identified during this design but deferred from the initial implementation:

1. **Audit and snapshot pruning for active documents** — The scheduled job only cleans up soft-deleted docs. For long-lived active documents, audit entries and snapshot rows grow unbounded. A future pruning mechanism could compact old audit history (e.g., keep only the last N snapshots, or snapshots younger than M days) for active documents.

2. **Undelete API / UI** — Soft-deleted documents can be undeleted by setting `deleted = NULL`, but there is no API endpoint or UI to do this. A future enhancement could expose an undelete operation.

3. **Remove redundant audit fields from doc meta** — The `createTimeMs`, `createUser`, `updateTimeMs`, and `updateUser` fields currently stored in the doc meta JSON are now redundant, as this information is captured in the `doc_audit` table. A future migration could strip these fields from the meta JSON to avoid duplication and reduce storage.

4. **Database-managed document dependencies** — Document dependencies (e.g., a Pipeline referencing an XSLT, a Dashboard referencing a Dictionary) are currently resolved at runtime by each store loading document content and parsing it to discover references. This is slow and does not scale for the dependencies UI, impact analysis, or a "safe delete" feature that warns users about downstream impacts before deleting a document. A future enhancement could maintain a `doc_dependency` table (e.g., `from_doc_id`, `to_doc_uuid`) that is updated on each write, enabling fast database-level dependency queries without loading or parsing content.
