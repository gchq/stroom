# Split `doc` Table into `doc` + `doc_data` + `doc_audit` + `doc_data_history`

## Background

The `doc` table currently stores multiple rows per logical document, differentiated by the `ext` column. A single document (identified by `type` + `uuid`) typically has:

- An `ext = 'meta'` row containing JSON metadata (type, uuid, name, version, audit fields, plus type-specific configuration)
- One or more content rows with other `ext` values (e.g., `xsl`, `js`, `txt`, `json`) containing the document's content data

All data is stored in a single `longblob` column regardless of actual content type. The goal is to normalise this into separate tables: a `doc` identity table, a typed `doc_data` table for content, a `doc_audit` table for operation tracking, and a `doc_data_history` table for change tracking.

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
  KEY doc_uuid_idx (uuid),
  KEY doc_type_name_uuid_idx (type, name, uuid)
);
```

### Scope of Change

Changes required:
- [Persistence.java](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl/src/main/java/stroom/docstore/impl/Persistence.java) — `write()` signature changes from `boolean update` to `AuditAction`
- [StoreImpl.java](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl/src/main/java/stroom/docstore/impl/StoreImpl.java) — update all `persistence.write()` call sites to pass the correct `AuditAction`
- [DBPersistence.java](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl-db/src/main/java/stroom/docstore/impl/db/DBPersistence.java) — heavy refactoring of all SQL and CRUD methods
- [FSPersistence.java](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl-fs/src/main/java/stroom/docstore/impl/fs/FSPersistence.java) — update `write()` signature (no audit logic needed)
- [MemoryPersistence.java](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl-memory/src/main/java/stroom/docstore/impl/memory/MemoryPersistence.java) — update `write()` signature (no audit logic needed)
- [AuditAction](file:///home/stroomdev66/work/stroom-7.10/stroom-docref/src/main/java/stroom/docref/DocAuditEntry.java#L80-L95) enum — add new action values for import/export/copy/move/rename
- Flyway migration scripts — new tables + data migration

---

## Resolved Design Decisions

1. **Single sparse table**: Use one `doc_data` table with typed columns (`json_data`, `text_data`, `bin_data`) instead of 3 separate tables. Each row populates exactly one data column based on the extension type. This dramatically simplifies reads, writes, and history tracking.
2. **Meta JSON**: Moves OUT of the `doc` table into `doc_data`. The `doc` table becomes a pure identity table (`id`, `type`, `uuid`, `name`, `deleted`).
3. **Each row has an `ext` column** to distinguish sub-types (e.g., `meta`, `xsl`, `json`, `txt`).
4. **Unknown extensions**: Default to the `bin_data` column.
5. **History on CREATE, UPDATE, and IMPORT**: Full data snapshots are captured for operations that introduce or change data. All data entries are copied to `doc_data_history`, even unchanged ones, making any version trivially reconstructable.
6. **`Persistence.write()` accepts `AuditAction`** instead of `boolean update` — gives precise audit logging at the persistence layer.
7. **Logical delete**: Documents are soft-deleted by setting a `deleted` timestamp column on `doc`. All data, audit, and history survive indefinitely. Physical cleanup of old soft-deleted items can be added later.

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
  UNIQUE KEY doc_type_uuid_idx (type, uuid),
  KEY doc_uuid_idx (uuid),
  KEY doc_type_name_uuid_idx (type, name, uuid),
  KEY doc_deleted_idx (deleted)
);
```

> [!NOTE]
> The `deleted` column enables **logical (soft) delete**. When a document is deleted, `deleted` is set to the current epoch millis. All read/list/find queries filter on `deleted IS NULL` to exclude soft-deleted documents. Soft-deleted documents can be undeleted by setting `deleted = NULL`. A future scheduled job can physically `DELETE FROM doc WHERE deleted < (NOW() - X days)` to reclaim storage for items deleted longer than X days — CASCADE will clean up `doc_data`, `doc_audit`, and `doc_data_history` automatically.

### Doc Data Table

```sql
-- doc_data: single sparse table with typed columns for all document content
CREATE TABLE doc_data (
  id        bigint NOT NULL AUTO_INCREMENT,
  fk_doc_id bigint NOT NULL,
  ext       varchar(255) NOT NULL,
  data_type varchar(8) NOT NULL,      -- 'JSON', 'TEXT', or 'BINARY' — indicates which data column is populated
  json_data json,                     -- populated when data_type = 'JSON'
  text_data longtext,                 -- populated when data_type = 'TEXT'
  bin_data  longblob,                 -- populated when data_type = 'BINARY'
  PRIMARY KEY (id),
  UNIQUE KEY doc_data_fk_doc_id_ext_idx (fk_doc_id, ext),
  CONSTRAINT doc_data_fk_doc_id FOREIGN KEY (fk_doc_id) REFERENCES doc (id) ON DELETE CASCADE
);
```

The `data_type` column makes the table self-describing — on read, you check `data_type` to know which column to read, rather than needing the extension-to-type mapping. The mapping is only needed on write to determine the correct `data_type` for a given `ext`.

### Audit Table

```sql
-- Audit trail: one row per CRUD/import/export operation on a document
CREATE TABLE doc_audit (
  id           bigint NOT NULL AUTO_INCREMENT,
  fk_doc_id    bigint NOT NULL,
  action       varchar(32) NOT NULL,              -- AuditAction enum value
  action_time  bigint NOT NULL,                   -- epoch millis
  user_uuid    varchar(255) DEFAULT NULL,         -- stroom_user UUID
  user_name    varchar(255) DEFAULT NULL,         -- user display name
  PRIMARY KEY (id),
  KEY doc_audit_fk_doc_id_idx (fk_doc_id),
  KEY doc_audit_action_time_idx (action_time),
  CONSTRAINT doc_audit_fk_doc_id FOREIGN KEY (fk_doc_id) REFERENCES doc (id) ON DELETE CASCADE
);
```

### History Table

```sql
-- doc_data_history: snapshots of data at each create/update/import event
CREATE TABLE doc_data_history (
  id              bigint NOT NULL AUTO_INCREMENT,
  fk_doc_audit_id bigint NOT NULL,
  fk_doc_id       bigint NOT NULL,
  ext             varchar(255) NOT NULL,
  data_type       varchar(8) NOT NULL,    -- 'JSON', 'TEXT', or 'BINARY'
  json_data       json,
  text_data       longtext,
  bin_data        longblob,
  PRIMARY KEY (id),
  KEY doc_data_history_fk_doc_audit_id_idx (fk_doc_audit_id),
  KEY doc_data_history_fk_doc_id_idx (fk_doc_id),
  CONSTRAINT doc_data_history_fk_doc_audit_id FOREIGN KEY (fk_doc_audit_id) REFERENCES doc_audit (id) ON DELETE CASCADE,
  CONSTRAINT doc_data_history_fk_doc_id FOREIGN KEY (fk_doc_id) REFERENCES doc (id) ON DELETE CASCADE
);
```

### Design Points

**Why a single sparse table instead of 3 separate tables:**

| Aspect | 3 Separate Tables | 1 Sparse Table (chosen) |
|--------|-------------------|------------------------|
| **Read** | 3 queries or UNION | 1 simple `SELECT WHERE fk_doc_id = ?` |
| **Write** | Route each ext to correct table | 1 INSERT/UPDATE, populate the right column |
| **History** | 3 history tables | 1 history table |
| **Total tables** | 6 data-related | 2 data-related |
| **DBPersistence complexity** | Extension routing, per-table SQL | Minimal — column selection only |
| **NULL overhead** | None | ~2 NULL columns per row (negligible in MySQL) |
| **Type safety** | Each column matches its table's type | Same — each ext only populates the matching column |
| **JSON functions** | Work on `doc_data_json.data` | Work on `doc_data.json_data` (same) |
| **Extension category change** | Migrate data between tables | Just populate a different column |

**Other design points:**
- `ON DELETE CASCADE` on all child tables — physical deletion of a `doc` row (future cleanup job) automatically removes all `doc_data`, `doc_audit`, and `doc_data_history` rows
- Normal delete operations are **logical** — `UPDATE doc SET deleted = ? WHERE type = ? AND uuid = ?`
- `ext` column preserves the key used by `ImportExportDocument` and serialisers
- MySQL's `JSON` data type provides automatic validation and enables JSON functions (`JSON_EXTRACT`, `JSON_VALUE`)
- `doc` unique key changes from `(type, uuid, ext)` to `(type, uuid)` — one row per doc

**History table behaviour:**
- `fk_doc_audit_id` links to the specific audit event, giving a complete timeline: "at this audit event, the data for this ext was X"
- On **CREATE**: all initial data entries are captured as history rows
- On **UPDATE**: all data entries (both changed and unchanged) are captured as history rows — this gives a complete snapshot at each version
- On **IMPORT**: all imported data entries are captured as history rows (distinguishable from CREATE by the audit action)
- DELETE and EXPORT operations do NOT create history entries (no data change)
- `ON DELETE CASCADE` on both FKs — cleanup is automatic

> [!IMPORTANT]
> **Storage growth**: History tables and audit entries are never deleted by normal operations (since delete is logical). For large deployments, consider: (1) a retention policy that physically deletes `doc` rows where `deleted` is older than X days (CASCADE handles cleanup), and (2) optional pruning of old history entries for active documents. Both are future concerns and not part of the initial implementation.

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

In `DBPersistence`, this will be defined as:
```java
private static final Set<String> JSON_EXTENSIONS = Set.of("meta", "json");
private static final Set<String> TEXT_EXTENSIONS = Set.of("xsl", "xsd", "xml", "js", "txt");
// Everything else → bin_data column
```

---

## AuditAction Enum Changes

#### [MODIFY] [DocAuditEntry.java](file:///home/stroomdev66/work/stroom-7.10/stroom-docref/src/main/java/stroom/docref/DocAuditEntry.java#L80-L95)

Expand the `AuditAction` enum to cover all document lifecycle operations:

```java
public enum AuditAction implements HasDisplayValue {
    CREATE("Created"),
    UPDATE("Updated"),
    DELETE("Deleted"),
    IMPORT("Imported"),
    EXPORT("Exported"),
    COPY("Copied"),
    MOVE("Moved"),
    RENAME("Renamed");
    // ...
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
2. **Migrates meta JSON** (`ext = 'meta'`) into `json_data` column
3. **Migrates content JSON** (`ext = 'json'`) into `json_data` column
4. **Migrates text content** (`ext IN ('xsl', 'xsd', 'xml', 'js', 'txt')`) into `text_data` column
5. **Migrates remaining content** (unknown extensions) into `bin_data` column
6. **Removes all non-meta rows from `doc`**
7. **Drops `data` and `ext` columns from `doc`**, adds `deleted` column, updates unique key
8. **Creates `doc_audit` table**
9. **Creates `doc_data_history` table**
10. **Seeds initial audit entries** from existing meta JSON

```sql
-- Step 1: Create doc_data table
CREATE TABLE IF NOT EXISTS doc_data (
  id        bigint NOT NULL AUTO_INCREMENT,
  fk_doc_id bigint NOT NULL,
  ext       varchar(255) NOT NULL,
  data_type varchar(8) NOT NULL,
  json_data json,
  text_data longtext,
  bin_data  longblob,
  PRIMARY KEY (id),
  UNIQUE KEY doc_data_fk_doc_id_ext_idx (fk_doc_id, ext),
  CONSTRAINT doc_data_fk_doc_id FOREIGN KEY (fk_doc_id) REFERENCES doc (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Step 2: Migrate meta JSON → doc_data.json_data (meta row references itself)
-- CAST converts LONGBLOB → JSON, which validates the content
INSERT INTO doc_data (fk_doc_id, ext, data_type, json_data)
SELECT id, ext, 'JSON', CAST(CONVERT(data USING utf8mb4) AS JSON)
FROM doc
WHERE ext = 'meta';

-- Step 3: Migrate content JSON → doc_data.json_data (references the meta row)
INSERT INTO doc_data (fk_doc_id, ext, data_type, json_data)
SELECT dm.id, d.ext, 'JSON', CAST(CONVERT(d.data USING utf8mb4) AS JSON)
FROM doc d
JOIN doc dm ON dm.type = d.type AND dm.uuid = d.uuid AND dm.ext = 'meta'
WHERE d.ext = 'json';

-- Step 4: Migrate text content → doc_data.text_data
INSERT INTO doc_data (fk_doc_id, ext, data_type, text_data)
SELECT dm.id, d.ext, 'TEXT', CONVERT(d.data USING utf8mb4)
FROM doc d
JOIN doc dm ON dm.type = d.type AND dm.uuid = d.uuid AND dm.ext = 'meta'
WHERE d.ext IN ('xsl', 'xsd', 'xml', 'js', 'txt');

-- Step 5: Migrate remaining → doc_data.bin_data
INSERT INTO doc_data (fk_doc_id, ext, data_type, bin_data)
SELECT dm.id, d.ext, 'BINARY', d.data
FROM doc d
JOIN doc dm ON dm.type = d.type AND dm.uuid = d.uuid AND dm.ext = 'meta'
WHERE d.ext NOT IN ('meta', 'json', 'xsl', 'xsd', 'xml', 'js', 'txt')
AND d.ext IS NOT NULL;

-- Step 6: Delete all non-meta rows from doc
DELETE FROM doc WHERE ext != 'meta' OR ext IS NULL;

-- Step 7: Drop data and ext columns, update indexes
ALTER TABLE doc DROP KEY doc_type_uuid_ext_idx;
ALTER TABLE doc DROP COLUMN data;
ALTER TABLE doc DROP COLUMN ext;
ALTER TABLE doc ADD COLUMN deleted bigint DEFAULT NULL;
ALTER TABLE doc ADD KEY doc_deleted_idx (deleted);
ALTER TABLE doc DROP KEY doc_type_uuid_idx;
ALTER TABLE doc ADD UNIQUE KEY doc_type_uuid_idx (type, uuid);

-- Step 8: Create doc_audit table
CREATE TABLE IF NOT EXISTS doc_audit (...);

-- Step 9: Create doc_data_history table
CREATE TABLE IF NOT EXISTS doc_data_history (...);

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

This is the most significant code change, but is substantially simpler with a single `doc_data` table.

**1. New constants** — Extension classification:
```java
private static final Set<String> JSON_EXTENSIONS = Set.of("meta", "json");
private static final Set<String> TEXT_EXTENSIONS = Set.of("xsl", "xsd", "xml", "js", "txt");
// Everything else → bin_data column
```

**2. SQL queries** — All need rewriting (but simpler with single table):

| Query | Current Behaviour | New Behaviour |
|-------|-------------------|---------------|
| `SELECT_BY_TYPE_UUID_SQL` (read) | `SELECT ext, data FROM doc WHERE type=? AND uuid=?` | `SELECT ext, data_type, json_data, text_data, bin_data FROM doc_data WHERE fk_doc_id = ?` (after looking up `doc.id` with `AND deleted IS NULL`) |
| `LIST_BY_TYPE_SQL` | `SELECT ... FROM doc WHERE type=? AND ext='meta'` | `SELECT ... FROM doc WHERE type=? AND deleted IS NULL` |
| `SELECT_BY_TYPE_NAME_*_SQL` | Filters on `ext='meta'` | Filter on `deleted IS NULL` instead |
| `SELECT_NAME_BY_TYPE_UUID_SQL` | Filters on `ext='meta'` | Query `doc` directly with `AND deleted IS NULL` |
| `SELECT_ID_BY_TYPE_UUID_SQL` | `LIMIT 1` (any row for the doc) | Query `doc` directly (one row) with `AND deleted IS NULL` |
| `SELECT_EXTENSIONS_BY_TYPE_UUID_SQL` | `SELECT id, ext FROM doc` | `SELECT id, ext FROM doc_data WHERE fk_doc_id = ?` |
| `INSERT_SQL` / `UPDATE_SQL` | Single table | `doc` INSERT + `doc_data` INSERT per ext |
| `DELETE_BY_UUID_SQL` | `DELETE FROM doc WHERE type=? AND uuid=?` | `UPDATE doc SET deleted = ? WHERE type=? AND uuid=?` (logical delete) |
| `FIND_BY_EMBEDDED_IN` | JSON_TABLE on `doc.data WHERE ext='meta'` | JSON_TABLE on `doc_data.json_data` where `ext='meta'` joined to `doc` with `deleted IS NULL` |
| `READ_INFO_BY_UUID_SQL` | `SELECT data FROM doc WHERE uuid=? AND ext='meta'` | No longer needed — replaced by `doc_audit` query |
| Dynamic SQL in `find(...)`, `list(...)` | Filter on `ext='meta'` | Filter on `deleted IS NULL` — query `doc` directly |

**3. `read(DocRef)` method** — Rewrite to:
1. Look up `doc.id` from `doc WHERE type=? AND uuid=? AND deleted IS NULL`
2. Query `doc_data WHERE fk_doc_id=?` — for each row, use `data_type` to determine which column to read:
   - `data_type = 'JSON'` → read `json_data` as `String` via `getString()`, convert to `byte[]` (UTF-8)
   - `data_type = 'TEXT'` → read `text_data` as `String` via `getString()`, convert to `byte[]` (UTF-8)
   - `data_type = 'BINARY'` → read `bin_data` as `byte[]` via `getBytes()`
3. Create `ByteArrayImportExportAsset(ext, data)` for each, add to `ImportExportDocument`
4. Return combined `ImportExportDocument`

**4. `write(DocRef, AuditAction, ImportExportDocument)` method** — Rewrite to:
1. Determine if this is a create or update from `AuditAction`: `CREATE`/`COPY` → insert new `doc` row; `UPDATE`/`RENAME`/`IMPORT` → check if doc exists and update
2. **Create**: INSERT into `doc` (type, uuid, name, deleted=NULL), get generated `id`
3. Determine `data_type` from `ext` using extension classification, then INSERT into `doc_data` with `data_type` and the appropriate column:
   - `ext ∈ JSON_EXTENSIONS` → `data_type = 'JSON'`, set `json_data` (convert `byte[]` → `String`, write via `setString()`)
   - `ext ∈ TEXT_EXTENSIONS` → `data_type = 'TEXT'`, set `text_data` (convert `byte[]` → `String`, write via `setString()`)
   - Otherwise → `data_type = 'BINARY'`, set `bin_data` (write `byte[]` directly via `setBytes()`)
4. **Update**: UPDATE `doc` row (name may change), then upsert each asset in `doc_data` (including `data_type`)
5. Clean up removed extensions: `DELETE FROM doc_data WHERE fk_doc_id = ? AND ext NOT IN (?...)`
6. INSERT `doc_audit` row with the `AuditAction` name, current time, and user from `SecurityContext`
7. For each data entry written, INSERT a `doc_data_history` row (including `data_type`) linked to the new `doc_audit.id`

**5. `delete(DocRef)` method** — Changes to logical delete:
```sql
UPDATE doc SET deleted = ? WHERE type = ? AND uuid = ? AND deleted IS NULL
```
Also INSERT a `doc_audit` row with `action = 'DELETE'` — this entry survives since the `doc` row isn't physically removed.

**6. `getExtensionIds(DocRef)` method** — Simplifies: query single `doc_data` table:
```sql
SELECT id, ext FROM doc_data WHERE fk_doc_id = ?
```
No need to track table provenance.

**7. Simplified methods** (no `ext='meta'` filter needed, but add `deleted IS NULL`):
- `exists(DocRef)` — `SELECT 1 FROM doc WHERE type=? AND uuid=? AND deleted IS NULL LIMIT 1`
- `getName(DocRef)` — `SELECT name FROM doc WHERE type=? AND uuid=? AND deleted IS NULL`
- `list(String type)` — `SELECT uuid, name FROM doc WHERE type=? AND deleted IS NULL`
- All `find(...)` methods — Replace `AND ext = 'meta'` with `AND deleted IS NULL`
- `list(Collection<String>)` — Replace `AND ext = 'meta'` with `AND deleted IS NULL`
- `findDocRefsEmbeddedIn(DocRef)` — JSON_TABLE on `doc_data.json_data` joined to `doc` with `deleted IS NULL`

**8. `getAuditInfo(DocRef)` method** — Complete rewrite:

Currently this method ([line 567-595](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl-db/src/main/java/stroom/docstore/impl/db/DBPersistence.java#L567-L595)) has a TODO comment: *"This is just a temporary way to create audit records for now until we add the doc_audit table."* It currently deserialises the meta JSON to extract `createTimeMs`/`createUser`/`updateTimeMs`/`updateUser` and constructs synthetic audit entries.

Rewrite to simply query `doc_audit`:
```sql
SELECT action, action_time, user_uuid, user_name
FROM doc_audit
WHERE fk_doc_id = (SELECT id FROM doc WHERE type = ? AND uuid = ?)
ORDER BY action_time DESC
```
Note: `getAuditInfo()` queries by UUID without the `deleted IS NULL` filter — this allows viewing audit history for soft-deleted documents too.
Map each row to a `DocAuditEntry` with the `AuditAction` enum and `DocAuditUser`.

**9. Audit logging** — Integrated into `write()` and `delete()` (see items 4 and 5 above). Since delete is logical, the DELETE audit entry survives alongside the soft-deleted doc row — no CASCADE concern.

**10. History recording** — Integrated into `write()` (see item 4 above). On CREATE, UPDATE, and IMPORT, full snapshots of all `doc_data` entries are copied to `doc_data_history` linked to the `doc_audit` entry. This means any historical version can be reconstructed by querying `doc_data_history WHERE fk_doc_audit_id = ?`.

**11. `SecurityContext` injection** — Add `SecurityContext` to `DBPersistence`'s constructor to capture the current user for audit entries:
```java
@Inject
DBPersistence(final DocStoreDbConnProvider dataSource,
              final SecurityContext securityContext) {
    this.dataSource = dataSource;
    this.securityContext = securityContext;
}
```

---

### No Changes Required

| File | Reason |
|------|--------|
| All `DocumentSerialiser2` implementations | Work with `ImportExportDocument`, unaffected |
| [DocStoreDBPersistenceDbModule.java](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl-db/src/main/java/stroom/docstore/impl/db/DocStoreDBPersistenceDbModule.java) | Flyway config unchanged |
| [DocStoreDbPersistenceModule.java](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl-db/src/main/java/stroom/docstore/impl/db/DocStoreDbPersistenceModule.java) | Guice bindings unchanged |
| [NoLockFactory.java](file:///home/stroomdev66/work/stroom-7.10/stroom-docstore/stroom-docstore-impl-db/src/main/java/stroom/docstore/impl/db/NoLockFactory.java) | Unrelated |


---

## Verification Plan

### Automated Tests

```bash
# Run the docstore integration test
./gradlew :stroom-app:test --tests "*TestDBPersistence*"

# Run the full docstore module tests
./gradlew :stroom-docstore:stroom-docstore-impl-db:test
```

### Manual Verification

1. **Migration verification** — Apply migration to a test database with existing doc data and verify:
   - `count(doc)` = original count of `ext='meta'` rows
   - `doc` table has no `data` or `ext` columns, has `deleted` column (all NULL for existing docs)
   - `count(doc_data WHERE json_data IS NOT NULL)` = original count of `ext IN ('meta', 'json')` rows
   - `count(doc_data WHERE text_data IS NOT NULL)` = original count of `ext IN ('xsl', 'xsd', 'xml', 'js', 'txt')` rows
   - `count(doc_data WHERE bin_data IS NOT NULL)` = original count of remaining non-null `ext` rows
   - All data content matches between old and new tables
   - `count(doc_audit)` = 2× `count(doc)` (one CREATE + one UPDATE per doc from seeded data)

2. **Logical delete test** — Delete a doc and verify:
   - `doc.deleted` is set to current epoch millis
   - `doc_data`, `doc_audit`, `doc_data_history` rows are all still present
   - `exists(docRef)` returns `false`
   - `list(type)` does not include the deleted doc
   - `getAuditInfo(docRef)` still returns the audit trail including the DELETE entry

3. **Undelete test** — Set `doc.deleted = NULL` and verify the doc is visible again in all queries

4. **Views test** — Verify `v_doc` and `v_feed_doc` views return correct data with new schema (filtering out soft-deleted docs)

5. **Round-trip test** — Create, read, update, and delete documents of various types (Pipeline, XSLT, Dictionary, Feed) to verify all serialisers still work correctly through `DBPersistence`

6. **Audit trail test** — Create a doc, update it twice, then call `getAuditInfo()` — verify 3 audit entries (CREATE + 2× UPDATE) with correct timestamps and users

7. **History test** — Create a doc, update it, then query `doc_data_history` — verify 2 full snapshots exist (initial create + update) linked to the correct audit entries

8. **Import history test** — Import a document, verify an IMPORT audit entry exists with corresponding history snapshots (distinguishable from CREATE)

9. **AuditAction precision test** — Verify that each StoreImpl operation produces the correct `AuditAction` in `doc_audit`: CREATE for `create()`, UPDATE for `update()`, IMPORT for `importDocument()`, DELETE for `deleteDocument()`
