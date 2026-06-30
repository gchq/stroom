/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.docstore.impl.db;

import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.docstore.shared.AuditAction;
import stroom.docstore.shared.DocAuditEntry;
import stroom.docstore.shared.DocDataType;
import stroom.importexport.api.ByteArrayImportExportAsset;
import stroom.importexport.api.ImportExportDocument;
import stroom.test.common.util.db.DbTestUtil;
import stroom.util.exception.DataChangedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.Guice;
import jakarta.inject.Inject;
import org.jooq.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static stroom.docstore.impl.db.jooq.tables.Doc.DOC;
import static stroom.docstore.impl.db.jooq.tables.DocAudit.DOC_AUDIT;
import static stroom.docstore.impl.db.jooq.tables.DocAuditDataSnapshot.DOC_AUDIT_DATA_SNAPSHOT;
import static stroom.docstore.impl.db.jooq.tables.DocData.DOC_DATA;
import static stroom.docstore.impl.db.jooq.tables.DocDataSnapshot.DOC_DATA_SNAPSHOT;

/**
 * Module-level unit tests for {@link DBPersistence}.
 * <p>
 * Follows the standard DAO test pattern (TestNodeDaoImpl, TestAnnotationDaoImpl, etc.)
 * — lightweight Guice injector with just the docstore DB module + test datasource.
 * No full application bootstrap needed.
 * <p>
 * Tests prefer exercising {@link DBPersistence} methods directly. Direct DB
 * queries are only used where no public API exists (snapshot dedup counts,
 * audit-snapshot links) or where we need to verify internal state that the API
 * intentionally hides (e.g. data surviving a soft-delete, physical row removal).
 */
class TestDBPersistence {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestDBPersistence.class);
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    // Tables cleared before each test, in FK-safe (reverse) order
    private static final List<Table<?>> TABLES = List.of(
            DOC_AUDIT_DATA_SNAPSHOT,
            DOC_DATA_SNAPSHOT,
            DOC_AUDIT,
            DOC_DATA,
            DOC);

    @Inject
    private DBPersistence dbPersistence;
    @Inject
    private DocStoreDbConnProvider connProvider;

    @BeforeEach
    void setUp() throws SQLException {
        Guice.createInjector(new TestModule()).injectMembers(this);

        try (final Connection connection = connProvider.getConnection()) {
            for (final Table<?> table : TABLES) {
                LOGGER.debug("Clearing table {}", table.getName());
                DbTestUtil.clearTables(connection, List.of(table.getName()));
            }
        }
    }

    // --- Helper methods ---

    private DocRef randomDocRef(final String type) {
        return new DocRef(type, UUID.randomUUID().toString(), "test-" + type);
    }

    private ImportExportDocument createMetaDoc(final String metaJson) {
        final ImportExportDocument doc = new ImportExportDocument();
        doc.addExtAsset(new ByteArrayImportExportAsset(
                "meta", DocDataType.JSON, metaJson.getBytes(CHARSET)));
        return doc;
    }

    /**
     * Helper: write with CREATE action, generating a new version.
     * Returns the version that was set.
     */
    private String writeCreate(final DocRef docRef, final ImportExportDocument doc) {
        final String version = UUID.randomUUID().toString();
        dbPersistence.write(docRef, AuditAction.CREATE, null, doc, null, version);
        return version;
    }

    /**
     * Helper: write with UPDATE action using optimistic locking.
     * Returns the new version that was set.
     */
    private String writeUpdate(final DocRef docRef, final ImportExportDocument doc,
                               final String expectedVersion) {
        final String newVersion = UUID.randomUUID().toString();
        dbPersistence.write(docRef, AuditAction.UPDATE, null, doc, expectedVersion, newVersion);
        return newVersion;
    }

    /**
     * Helper: write with IMPORT action (no version check).
     * Returns the version that was set.
     */
    private String writeImport(final DocRef docRef, final ImportExportDocument doc) {
        final String version = UUID.randomUUID().toString();
        dbPersistence.write(docRef, AuditAction.IMPORT, null, doc, null, version);
        return version;
    }

    private ImportExportDocument createTextAndXslDoc(final String text, final String xsl) {
        final ImportExportDocument doc = new ImportExportDocument();
        doc.addExtAsset(new ByteArrayImportExportAsset(
                "txt", DocDataType.TEXT, text.getBytes(CHARSET)));
        doc.addExtAsset(new ByteArrayImportExportAsset(
                "xsl", DocDataType.TEXT, xsl.getBytes(CHARSET)));
        return doc;
    }

    private List<DocAuditEntry> getAuditEntries(final DocRef docRef) {
        return dbPersistence.getAuditInfo(docRef).getValues();
    }

    // --- Direct DB helpers — only used where no public API exists ---

    /**
     * Get the internal doc ID. Needed for snapshot/audit-link queries that have
     * no public API equivalent.
     */
    private long getDocId(final String uuid) {
        return JooqUtil.contextResult(connProvider, ctx -> ctx
                .select(DOC.ID).from(DOC)
                .where(DOC.UUID.eq(uuid))
                .fetchOne(DOC.ID));
    }

    /**
     * Check whether the doc row physically exists in the DB (regardless of
     * soft-delete status). Needed after physical delete to verify the row is gone.
     */
    private boolean docRowExists(final String uuid) {
        return JooqUtil.contextResult(connProvider, ctx -> ctx
                .fetchExists(ctx.selectOne().from(DOC).where(DOC.UUID.eq(uuid))));
    }

    /**
     * Get the raw DELETED timestamp from the doc row. Needed to verify soft-delete
     * sets the value, and import-undelete clears it.
     */
    private Long getDeletedTimestamp(final String uuid) {
        return JooqUtil.contextResult(connProvider, ctx -> ctx
                .select(DOC.DELETED).from(DOC)
                .where(DOC.UUID.eq(uuid))
                .fetchOne(DOC.DELETED));
    }

    /**
     * Backdate the deleted timestamp for retention testing.
     */
    private void backdateDeleted(final String uuid, final Duration age) {
        JooqUtil.context(connProvider, ctx ->
                ctx.update(DOC)
                        .set(DOC.DELETED, System.currentTimeMillis() - age.toMillis())
                        .where(DOC.UUID.eq(uuid))
                        .execute());
    }

    /**
     * Manually clear the DELETED timestamp to simulate an undelete.
     */
    private void clearDeleted(final String uuid) {
        JooqUtil.context(connProvider, ctx ->
                ctx.update(DOC)
                        .set(DOC.DELETED, (Long) null)
                        .where(DOC.UUID.eq(uuid))
                        .execute());
    }

    // Snapshot/audit-link counts — no public API for these internal structures

    private int countSnapshotRowsByExt(final long docId, final String ext) {
        return JooqUtil.contextResult(connProvider, ctx -> ctx
                .selectCount().from(DOC_DATA_SNAPSHOT)
                .where(DOC_DATA_SNAPSHOT.FK_DOC_ID.eq(docId))
                .and(DOC_DATA_SNAPSHOT.EXT.eq(ext))
                .fetchOne(0, int.class));
    }

    private int countAuditSnapshotLinks(final long auditId) {
        return JooqUtil.contextResult(connProvider, ctx -> ctx
                .selectCount().from(DOC_AUDIT_DATA_SNAPSHOT)
                .where(DOC_AUDIT_DATA_SNAPSHOT.FK_DOC_AUDIT_ID.eq(auditId))
                .fetchOne(0, int.class));
    }

    private List<Long> getAuditIds(final long docId) {
        return JooqUtil.contextResult(connProvider, ctx -> ctx
                .select(DOC_AUDIT.ID).from(DOC_AUDIT)
                .where(DOC_AUDIT.FK_DOC_ID.eq(docId))
                .orderBy(DOC_AUDIT.ID.asc())
                .fetch(DOC_AUDIT.ID));
    }

    private List<Long> getSnapshotIdsForAudit(final long auditId) {
        return JooqUtil.contextResult(connProvider, ctx -> ctx
                .select(DOC_AUDIT_DATA_SNAPSHOT.FK_DOC_DATA_SNAPSHOT_ID)
                .from(DOC_AUDIT_DATA_SNAPSHOT)
                .where(DOC_AUDIT_DATA_SNAPSHOT.FK_DOC_AUDIT_ID.eq(auditId))
                .orderBy(DOC_AUDIT_DATA_SNAPSHOT.FK_DOC_DATA_SNAPSHOT_ID.asc())
                .fetch(DOC_AUDIT_DATA_SNAPSHOT.FK_DOC_DATA_SNAPSHOT_ID));
    }

    /**
     * Count rows across all tables for a given doc ID. Used after physical delete
     * to verify complete removal.
     */
    private void assertAllRowsGone(final String uuid, final long docId) {
        assertThat(docRowExists(uuid)).isFalse();
        assertThat(countDocDataRows(docId)).isEqualTo(0);
        assertThat(countSnapshotRows(docId)).isEqualTo(0);
        assertThat(countAuditRows(docId)).isEqualTo(0);
    }

    private int countDocDataRows(final long docId) {
        return JooqUtil.contextResult(connProvider, ctx -> ctx
                .selectCount().from(DOC_DATA)
                .where(DOC_DATA.FK_DOC_ID.eq(docId))
                .fetchOne(0, int.class));
    }

    private int countSnapshotRows(final long docId) {
        return JooqUtil.contextResult(connProvider, ctx -> ctx
                .selectCount().from(DOC_DATA_SNAPSHOT)
                .where(DOC_DATA_SNAPSHOT.FK_DOC_ID.eq(docId))
                .fetchOne(0, int.class));
    }

    private int countAuditRows(final long docId) {
        return JooqUtil.contextResult(connProvider, ctx -> ctx
                .selectCount().from(DOC_AUDIT)
                .where(DOC_AUDIT.FK_DOC_ID.eq(docId))
                .fetchOne(0, int.class));
    }

    // --- Tests ---

    /**
     * Round-trip — CREATE → EXISTS → READ → UPDATE → READ → LIST → DELETE.
     */
    @Test
    void testRoundTrip() throws IOException {
        final String json1 = "{\"version\":\"" + UUID.randomUUID() + "\"}";
        final String json2 = "{\"version\":\"" + UUID.randomUUID() + "\"}";
        final DocRef docRef = randomDocRef("test-type");

        final String version1 = writeCreate(docRef, createMetaDoc(json1));
        assertThat(dbPersistence.exists(docRef)).isTrue();

        // Read — MySQL normalises JSON whitespace
        assertThat(new String(dbPersistence.read(docRef).getExtAssetData("meta"), CHARSET))
                .contains("version");

        writeUpdate(docRef, createMetaDoc(json2), version1);
        assertThat(new String(dbPersistence.read(docRef).getExtAssetData("meta"), CHARSET))
                .contains("version");

        assertThat(dbPersistence.list(docRef.getType())).contains(docRef);

        dbPersistence.delete(docRef, null);
        assertThat(dbPersistence.exists(docRef)).isFalse();
    }

    /**
     * findDocRefsEmbeddedIn — JSON_VALUE query for embedded doc refs.
     */
    @Test
    void findDocRefsEmbeddedIn() {
        final String uuid1 = UUID.randomUUID().toString();
        final String uuid2 = UUID.randomUUID().toString();
        final DocRef docRef1 = new DocRef("test-type1", uuid1, "test-name1");
        final DocRef docRef2 = new DocRef("test-type2", uuid2, "test-name2");

        final String metaJson1 = """
                {
                  "type": "XSLT",
                  "uuid": "%s",
                  "name": "EMBEDDED XSLT",
                  "version": "ba97a81f-c8e6-4d34-8ea8-a9808e89ced4",
                  "createTimeMs": 1769686495941,
                  "updateTimeMs": 1769686587789,
                  "createUser": "admin",
                  "updateUser": "admin",
                  "embeddedIn": {
                    "type": "Pipeline",
                    "uuid": "%s",
                    "name": "my pipeline"
                  }
                }""";

        final String metaJson2 = """
                {
                  "type": "Pipeline",
                  "uuid": "%s",
                  "name": "Pipeline",
                  "version": "ba97a81f-c8e6-4d34-8ea8-a9808e89ced4",
                  "createTimeMs": 1769686495941,
                  "updateTimeMs": 1769686587789,
                  "createUser": "admin",
                  "updateUser": "admin"
                }""";

        writeCreate(docRef1, createMetaDoc(String.format(metaJson1, uuid1, uuid2)));
        writeCreate(docRef2, createMetaDoc(String.format(metaJson2, uuid2)));

        final List<DocRef> docRefs = dbPersistence.findDocRefsEmbeddedIn(docRef2);
        assertThat(docRefs).hasSize(1);
        assertThat(docRefs.getFirst()).isEqualTo(docRef1);
    }

    /**
     * Logical delete — soft-delete preserves all child rows while hiding from API.
     * Direct DB queries verify data/snapshot survival (API can't see deleted docs).
     */
    @Test
    void testLogicalDelete() {
        final DocRef docRef = randomDocRef("test-del");
        writeCreate(docRef, createMetaDoc("{\"name\":\"test\"}"));

        // Pre-delete: verify via API
        assertThat(dbPersistence.exists(docRef)).isTrue();
        assertThat(dbPersistence.read(docRef).getExtAssets()).hasSize(1);
        assertThat(getAuditEntries(docRef)).hasSize(1);

        // Delete
        dbPersistence.delete(docRef, null);

        // API hides the doc
        assertThat(dbPersistence.exists(docRef)).isFalse();
        assertThat(dbPersistence.list(docRef.getType())).doesNotContain(docRef);

        // Direct DB: deleted timestamp is set
        assertThat(getDeletedTimestamp(docRef.getUuid())).isNotNull();

        // Direct DB: child rows survive (API can't read deleted docs, so we must
        // check the DB directly to verify soft-delete preserves data)
        final long docId = getDocId(docRef.getUuid());
        assertThat(countDocDataRows(docId)).isGreaterThan(0);
        assertThat(countSnapshotRows(docId)).isGreaterThan(0);

        // Audit trail still accessible via API (getAuditInfo works for deleted docs)
        final List<DocAuditEntry> entries = getAuditEntries(docRef);
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(entries.get(1).getAction()).isEqualTo(AuditAction.DELETE);
    }

    /**
     * Multi-type round-trip — JSON + TEXT sparse column routing.
     */
    @Test
    void testRoundTripMultiType() throws IOException {
        final DocRef docRef = randomDocRef("test-multi");
        final String metaJson = "{\"name\":\"multi-test\"}";
        final String xsl = "<xsl:stylesheet version=\"1.0\"/>";

        final ImportExportDocument writeDoc = new ImportExportDocument();
        writeDoc.addExtAsset(new ByteArrayImportExportAsset(
                "meta", DocDataType.JSON, metaJson.getBytes(CHARSET)));
        writeDoc.addExtAsset(new ByteArrayImportExportAsset(
                "xsl", DocDataType.TEXT, xsl.getBytes(CHARSET)));
        writeCreate(docRef, writeDoc);

        // Verify via read — both extensions present with correct content
        final ImportExportDocument readDoc = dbPersistence.read(docRef);
        assertThat(readDoc.getExtAssets()).hasSize(2);
        assertThat(new String(readDoc.getExtAssetData("meta"), CHARSET)).contains("multi-test");
        assertThat(new String(readDoc.getExtAssetData("xsl"), CHARSET)).isEqualTo(xsl);
    }

    /**
     * Audit trail — CREATE + 2×UPDATE → 3 entries, ascending timestamps.
     */
    @Test
    void testAuditTrail() {
        final DocRef docRef = randomDocRef("test-audit");
        final String v1 = writeCreate(docRef, createMetaDoc("{\"v\":1}"));
        final String v2 = writeUpdate(docRef, createMetaDoc("{\"v\":2}"), v1);
        writeUpdate(docRef, createMetaDoc("{\"v\":3}"), v2);

        final List<DocAuditEntry> entries = getAuditEntries(docRef);
        assertThat(entries).hasSize(3);
        assertThat(entries.get(0).getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(entries.get(1).getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(entries.get(2).getAction()).isEqualTo(AuditAction.UPDATE);

        assertThat(entries.get(0).getTime()).isLessThanOrEqualTo(entries.get(1).getTime());
        assertThat(entries.get(1).getTime()).isLessThanOrEqualTo(entries.get(2).getTime());
    }

    /**
     * Snapshot deduplication — unchanged TEXT asset reuses snapshot row.
     * Uses TEXT rather than JSON to avoid MySQL JSON normalisation breaking dedup.
     * Direct DB queries are necessary: no public API exposes snapshot internals.
     */
    @Test
    void testSnapshotDeduplication() {
        final DocRef docRef = randomDocRef("test-dedup");
        final String textV1 = "unchanged-content-for-dedup";
        final String xslV1 = "<xsl:stylesheet version=\"1.0\"/>";
        final String xslV2 = "<xsl:stylesheet version=\"2.0\"/>";

        final String v1 = writeCreate(docRef, createTextAndXslDoc(textV1, xslV1));
        writeUpdate(docRef, createTextAndXslDoc(textV1, xslV2), v1);

        // Direct DB: verify snapshot dedup — no public API for this
        final long docId = getDocId(docRef.getUuid());
        assertThat(countSnapshotRowsByExt(docId, "txt")).isEqualTo(1);  // Reused
        assertThat(countSnapshotRowsByExt(docId, "xsl")).isEqualTo(2);  // Changed

        // Both audit entries should link to 2 snapshots each (txt + xsl)
        final List<Long> auditIds = getAuditIds(docId);
        assertThat(auditIds).hasSize(2);
        assertThat(countAuditSnapshotLinks(auditIds.get(0))).isEqualTo(2);
        assertThat(countAuditSnapshotLinks(auditIds.get(1))).isEqualTo(2);

        // One snapshot ID should be shared (the txt one)
        final List<Long> audit1Snapshots = getSnapshotIdsForAudit(auditIds.get(0));
        final List<Long> audit2Snapshots = getSnapshotIdsForAudit(auditIds.get(1));
        final long commonCount = audit1Snapshots.stream()
                .filter(audit2Snapshots::contains)
                .count();
        assertThat(commonCount).isEqualTo(1);
    }

    /**
     * Import snapshot — IMPORT audit entry with snapshot links.
     */
    @Test
    void testImportSnapshot() {
        final DocRef docRef = randomDocRef("test-import");
        writeImport(docRef, createMetaDoc("{\"name\":\"imported\"}"));

        // Verify via API
        final List<DocAuditEntry> entries = getAuditEntries(docRef);
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getAction()).isEqualTo(AuditAction.IMPORT);

        // Direct DB: verify snapshot link exists — no public API
        final long docId = getDocId(docRef.getUuid());
        final List<Long> auditIds = getAuditIds(docId);
        assertThat(countAuditSnapshotLinks(auditIds.getFirst())).isEqualTo(1);
    }

    /**
     * AuditAction precision — each operation → correct action.
     */
    @Test
    void testAuditActionPrecision() {
        final DocRef docRef = randomDocRef("test-action");
        final String v1 = writeCreate(docRef, createMetaDoc("{\"v\":1}"));
        writeUpdate(docRef, createMetaDoc("{\"v\":2}"), v1);
        dbPersistence.delete(docRef, null);

        final List<AuditAction> actions = getAuditEntries(docRef).stream()
                .map(DocAuditEntry::getAction)
                .toList();
        assertThat(actions).containsExactly(
                AuditAction.CREATE, AuditAction.UPDATE, AuditAction.DELETE);

        // Separate test for IMPORT
        final DocRef importDocRef = randomDocRef("test-action-import");
        writeImport(importDocRef, createMetaDoc("{\"v\":1}"));
        assertThat(getAuditEntries(importDocRef).getFirst().getAction())
                .isEqualTo(AuditAction.IMPORT);
    }

    /**
     * Physical delete — retention-aware, preserves non-deleted docs.
     * Direct DB queries verify complete row removal from all tables.
     */
    @Test
    void testPhysicalDelete() {
        final DocRef docRef = randomDocRef("test-phys-del");
        final DocRef keepDocRef = randomDocRef("test-phys-keep");

        final String v1 = writeCreate(docRef, createMetaDoc("{\"v\":1}"));
        writeUpdate(docRef, createMetaDoc("{\"v\":2}"), v1);
        writeCreate(keepDocRef, createMetaDoc("{\"v\":1}"));

        dbPersistence.delete(docRef, null);
        final long docId = getDocId(docRef.getUuid());

        // 30d retention should NOT remove recently deleted doc
        assertThat(dbPersistence.physicalDelete(Duration.ofDays(30))).isEqualTo(0);
        assertThat(docRowExists(docRef.getUuid())).isTrue();

        // Backdate beyond retention, then physical delete
        backdateDeleted(docRef.getUuid(), Duration.ofDays(31));
        assertThat(dbPersistence.physicalDelete(Duration.ofDays(30))).isEqualTo(1);

        // Direct DB: all rows gone from every table
        assertAllRowsGone(docRef.getUuid(), docId);

        // Kept doc unaffected — verify via API
        assertThat(dbPersistence.exists(keepDocRef)).isTrue();
        assertThat(dbPersistence.read(keepDocRef).getExtAssets()).hasSize(1);
        assertThat(getAuditEntries(keepDocRef)).hasSize(1);
    }

    /**
     * Import of soft-deleted doc — undelete + data replacement.
     */
    @Test
    void testImportOfSoftDeletedDoc() throws IOException {
        final DocRef docRef = randomDocRef("test-undelete");
        writeCreate(docRef, createMetaDoc("{\"name\":\"original\"}"));
        dbPersistence.delete(docRef, null);
        assertThat(dbPersistence.exists(docRef)).isFalse();

        // Import same UUID with new content
        final String importVersion = writeImport(docRef, createMetaDoc("{\"name\":\"reimported\"}"));

        // Direct DB: deleted timestamp cleared
        assertThat(getDeletedTimestamp(docRef.getUuid())).isNull();

        // Direct DB: version column matches the imported version
        final String storedVersion = JooqUtil.contextResult(connProvider, context -> context
                .select(DOC.VERSION)
                .from(DOC)
                .where(DOC.UUID.eq(docRef.getUuid()))
                .fetchOne(DOC.VERSION));
        assertThat(storedVersion).isEqualTo(importVersion);

        // API: visible again with new content
        assertThat(dbPersistence.exists(docRef)).isTrue();
        assertThat(dbPersistence.list(docRef.getType())).contains(docRef);
        assertThat(new String(dbPersistence.read(docRef).getExtAssetData("meta"), CHARSET))
                .contains("reimported");

        // API: audit trail CREATE → DELETE → IMPORT
        final List<AuditAction> actions = getAuditEntries(docRef).stream()
                .map(DocAuditEntry::getAction)
                .toList();
        assertThat(actions).containsExactly(
                AuditAction.CREATE, AuditAction.DELETE, AuditAction.IMPORT);

        // Direct DB: snapshot links for CREATE and IMPORT, not DELETE
        final long docId = getDocId(docRef.getUuid());
        final List<Long> auditIds = getAuditIds(docId);
        assertThat(countAuditSnapshotLinks(auditIds.get(0))).isEqualTo(1); // CREATE
        assertThat(countAuditSnapshotLinks(auditIds.get(1))).isEqualTo(0); // DELETE
        assertThat(countAuditSnapshotLinks(auditIds.get(2))).isEqualTo(1); // IMPORT
    }

    /**
     * Undelete — manually set deleted=NULL, verify doc visible again.
     */
    @Test
    void testUndelete() throws IOException {
        final DocRef docRef = randomDocRef("test-undelete2");
        writeCreate(docRef, createMetaDoc("{\"name\":\"alive\"}"));
        dbPersistence.delete(docRef, null);
        assertThat(dbPersistence.exists(docRef)).isFalse();

        // Setup: manually undelete via direct DB
        clearDeleted(docRef.getUuid());

        // API: doc visible again
        assertThat(dbPersistence.exists(docRef)).isTrue();
        assertThat(dbPersistence.list(docRef.getType())).contains(docRef);
        assertThat(new String(dbPersistence.read(docRef).getExtAssetData("meta"), CHARSET))
                .contains("alive");
    }

    /**
     * physicalDelete is callable (scheduled job sanity check).
     */
    @Test
    void testPhysicalDeleteCallable() {
        assertThat(dbPersistence.physicalDelete(Duration.ofDays(365)))
                .isGreaterThanOrEqualTo(0);
    }

    // --- Optimistic Concurrency Control Tests ---

    /**
     * Version column populated on CREATE — doc.version matches the version passed to write().
     */
    @Test
    void testVersionColumnPopulatedOnCreate() {
        final DocRef docRef = randomDocRef("test-version-create");
        final String version = writeCreate(docRef, createMetaDoc("{\"v\":1}"));

        final String storedVersion = JooqUtil.contextResult(connProvider, context -> context
                .select(DOC.VERSION)
                .from(DOC)
                .where(DOC.UUID.eq(docRef.getUuid()))
                .fetchOne(DOC.VERSION));
        assertThat(storedVersion).isEqualTo(version);
    }

    /**
     * Version updated on UPDATE — doc.version changes to newVersion after successful update.
     */
    @Test
    void testVersionUpdatedOnUpdate() {
        final DocRef docRef = randomDocRef("test-version-update");
        final String v1 = writeCreate(docRef, createMetaDoc("{\"v\":1}"));
        final String v2 = writeUpdate(docRef, createMetaDoc("{\"v\":2}"), v1);

        final String storedVersion = JooqUtil.contextResult(connProvider, context -> context
                .select(DOC.VERSION)
                .from(DOC)
                .where(DOC.UUID.eq(docRef.getUuid()))
                .fetchOne(DOC.VERSION));
        assertThat(storedVersion).isEqualTo(v2);
        assertThat(storedVersion).isNotEqualTo(v1);
    }

    /**
     * Optimistic lock success — update with correct expectedVersion succeeds.
     */
    @Test
    void testOptimisticLockSuccess() throws IOException {
        final DocRef docRef = randomDocRef("test-opt-lock-ok");
        final String v1 = writeCreate(docRef, createMetaDoc("{\"v\":1}"));
        final String v2 = writeUpdate(docRef, createMetaDoc("{\"v\":2}"), v1);

        // Verify doc was updated
        assertThat(new String(dbPersistence.read(docRef).getExtAssetData("meta"), CHARSET))
                .contains("\"v\": 2");

        // Verify version column
        final String storedVersion = JooqUtil.contextResult(connProvider, context -> context
                .select(DOC.VERSION)
                .from(DOC)
                .where(DOC.UUID.eq(docRef.getUuid()))
                .fetchOne(DOC.VERSION));
        assertThat(storedVersion).isEqualTo(v2);
    }

    /**
     * Optimistic lock — stale version throws DataChangedException.
     */
    @Test
    void testOptimisticLockStaleVersion() {
        final DocRef docRef = randomDocRef("test-opt-lock-stale");
        final String v1 = writeCreate(docRef, createMetaDoc("{\"v\":1}"));
        // Update to V2
        writeUpdate(docRef, createMetaDoc("{\"v\":2}"), v1);

        // Attempt update with stale expectedVersion (V1) — should throw
        assertThatThrownBy(() -> writeUpdate(docRef, createMetaDoc("{\"v\":3}"), v1))
                .isInstanceOf(DataChangedException.class)
                .hasMessageContaining("modified by another user");
    }

    /**
     * Optimistic lock — non-existent doc throws DocumentNotFoundException.
     */
    @Test
    void testOptimisticLockDocNotFound() {
        final DocRef docRef = new DocRef("test-type", UUID.randomUUID().toString(), "ghost");

        assertThatThrownBy(() -> writeUpdate(docRef, createMetaDoc("{\"v\":1}"),
                UUID.randomUUID().toString()))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    /**
     * Optimistic lock — soft-deleted doc throws DocumentNotFoundException on update.
     */
    @Test
    void testOptimisticLockDeletedDoc() {
        final DocRef docRef = randomDocRef("test-opt-lock-deleted");
        final String v1 = writeCreate(docRef, createMetaDoc("{\"v\":1}"));
        dbPersistence.delete(docRef, null);

        assertThatThrownBy(() -> writeUpdate(docRef, createMetaDoc("{\"v\":2}"), v1))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    /**
     * Import ignores version — import succeeds regardless of current version.
     */
    @Test
    void testImportIgnoresVersion() throws IOException {
        final DocRef docRef = randomDocRef("test-import-noversion");
        writeCreate(docRef, createMetaDoc("{\"v\":1}"));

        // Import with no version check — should succeed
        final String importVersion = writeImport(docRef, createMetaDoc("{\"v\":\"imported\"}"));

        // Verify version column matches import version
        final String storedVersion = JooqUtil.contextResult(connProvider, context -> context
                .select(DOC.VERSION)
                .from(DOC)
                .where(DOC.UUID.eq(docRef.getUuid()))
                .fetchOne(DOC.VERSION));
        assertThat(storedVersion).isEqualTo(importVersion);

        // Verify content was replaced
        assertThat(new String(dbPersistence.read(docRef).getExtAssetData("meta"), CHARSET))
                .contains("imported");
    }

    /**
     * Version survives logical delete — doc.version is unchanged by soft-delete.
     */
    @Test
    void testVersionSurvivesDelete() {
        final DocRef docRef = randomDocRef("test-version-survives");
        final String v1 = writeCreate(docRef, createMetaDoc("{\"v\":1}"));

        dbPersistence.delete(docRef, null);

        // Version column should still be V1
        final String storedVersion = JooqUtil.contextResult(connProvider, context -> context
                .select(DOC.VERSION)
                .from(DOC)
                .where(DOC.UUID.eq(docRef.getUuid()))
                .fetchOne(DOC.VERSION));
        assertThat(storedVersion).isEqualTo(v1);
    }
}
