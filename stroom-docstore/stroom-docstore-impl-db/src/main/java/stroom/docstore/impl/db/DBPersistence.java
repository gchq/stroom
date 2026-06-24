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
import stroom.docstore.impl.Persistence;
import stroom.docstore.impl.db.jooq.tables.records.DocDataSnapshotRecord;
import stroom.docstore.shared.AuditAction;
import stroom.docstore.shared.DocAuditEntry;
import stroom.docstore.shared.DocAuditUser;
import stroom.docstore.shared.DocDataType;
import stroom.importexport.api.ByteArrayImportExportAsset;
import stroom.importexport.api.ImportExportAsset;
import stroom.importexport.api.ImportExportDocument;
import stroom.util.exception.DataChangedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.util.string.PatternUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import net.openhft.hashing.LongHashFunction;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.InsertSetMoreStep;
import org.jooq.JSON;
import org.jooq.Record5;
import org.jooq.Result;
import org.jooq.impl.DSL;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static stroom.docstore.impl.db.jooq.tables.Doc.DOC;
import static stroom.docstore.impl.db.jooq.tables.DocAudit.DOC_AUDIT;
import static stroom.docstore.impl.db.jooq.tables.DocAuditDataSnapshot.DOC_AUDIT_DATA_SNAPSHOT;
import static stroom.docstore.impl.db.jooq.tables.DocData.DOC_DATA;
import static stroom.docstore.impl.db.jooq.tables.DocDataSnapshot.DOC_DATA_SNAPSHOT;

@Singleton
public class DBPersistence implements Persistence {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DBPersistence.class);

    private static final String COLLATION = "utf8mb4_0900_as_cs";

    private final DocStoreDbConnProvider dataSource;

    @Inject
    DBPersistence(final DocStoreDbConnProvider dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean exists(final DocRef docRef) {
        return JooqUtil.contextResult(dataSource, context -> context
                .fetchExists(context
                        .selectOne()
                        .from(DOC)
                        .where(DOC.UUID.eq(docRef.getUuid()))
                        .and(DOC.DELETED.isNull())));
    }

    @Override
    public Optional<String> getName(final DocRef docRef) {
        return JooqUtil.contextResult(dataSource, context -> context
                .select(DOC.NAME)
                .from(DOC)
                .where(DOC.TYPE.eq(docRef.getType()))
                .and(DOC.UUID.eq(docRef.getUuid()))
                .and(DOC.DELETED.isNull())
                .fetchOptional(DOC.NAME));
    }

    @Override
    public ImportExportDocument read(final DocRef docRef) {
        final ImportExportDocument importExportDocument = new ImportExportDocument();

        JooqUtil.context(dataSource, context -> {
            // First get the doc id
            final Long docId = context
                    .select(DOC.ID)
                    .from(DOC)
                    .where(DOC.TYPE.eq(docRef.getType()))
                    .and(DOC.UUID.eq(docRef.getUuid()))
                    .and(DOC.DELETED.isNull())
                    .fetchOne(DOC.ID);

            if (docId == null) {
                return;
            }

            // Read all data rows for this doc
            context
                    .select(DOC_DATA.EXT,
                            DOC_DATA.DATA_TYPE,
                            DOC_DATA.JSON_DATA,
                            DOC_DATA.TEXT_DATA,
                            DOC_DATA.BIN_DATA)
                    .from(DOC_DATA)
                    .where(DOC_DATA.FK_DOC_ID.eq(docId))
                    .forEach(record -> {
                        final String ext = record.get(DOC_DATA.EXT);
                        final byte dataTypeByte = record.get(DOC_DATA.DATA_TYPE);
                        final DocDataType dataType =
                                DocDataType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(dataTypeByte);
                        final byte[] data = extractData(record,
                                DOC_DATA.JSON_DATA, DOC_DATA.TEXT_DATA, DOC_DATA.BIN_DATA, dataType);

                        importExportDocument.addExtAsset(
                                new ByteArrayImportExportAsset(ext, dataType, data));
                    });
        });

        if (importExportDocument.getExtAssets().isEmpty()) {
            throw new DocumentNotFoundException(docRef);
        }

        return importExportDocument;
    }

    @Override
    public void write(final DocRef docRef,
                      final AuditAction auditAction,
                      final UserRef userRef,
                      final ImportExportDocument importExportDocument,
                      final String expectedVersion,
                      final String newVersion) {
        JooqUtil.transaction(dataSource, context -> {
            Long docId;

            if (auditAction == AuditAction.IMPORT) {
                // IMPORT: look up by UUID ignoring deleted filter (find soft-deleted docs too)
                docId = context
                        .select(DOC.ID, DOC.DELETED)
                        .from(DOC)
                        .where(DOC.UUID.eq(docRef.getUuid()))
                        .fetchOne(DOC.ID);

                if (docId != null) {
                    // Undelete if soft-deleted, update name and version
                    context.update(DOC)
                            .set(DOC.NAME, docRef.getName())
                            .set(DOC.VERSION, newVersion)
                            .set(DOC.DELETED, (Long) null)
                            .where(DOC.ID.eq(docId))
                            .execute();
                } else {
                    // Brand new import — insert
                    docId = context
                            .insertInto(DOC)
                            .set(DOC.TYPE, docRef.getType())
                            .set(DOC.UUID, docRef.getUuid())
                            .set(DOC.NAME, docRef.getName())
                            .set(DOC.VERSION, newVersion)
                            .returning(DOC.ID)
                            .fetchOne(DOC.ID);
                }
            } else if (auditAction.isUpdate()) {
                // UPDATE/RENAME — atomic version-guarded update (optimistic lock)
                Objects.requireNonNull(expectedVersion, "expectedVersion required for UPDATE");
                Objects.requireNonNull(newVersion, "newVersion required for UPDATE");

                final int updateCount = context.update(DOC)
                        .set(DOC.NAME, docRef.getName())
                        .set(DOC.VERSION, newVersion)
                        .where(DOC.TYPE.eq(docRef.getType()))
                        .and(DOC.UUID.eq(docRef.getUuid()))
                        .and(DOC.DELETED.isNull())
                        .and(DOC.VERSION.eq(expectedVersion))
                        .execute();

                if (updateCount == 0) {
                    // Distinguish not-found vs version mismatch
                    final boolean exists = context.fetchExists(
                            context.selectOne().from(DOC)
                                    .where(DOC.UUID.eq(docRef.getUuid()))
                                    .and(DOC.DELETED.isNull()));
                    if (!exists) {
                        throw new DocumentNotFoundException(docRef);
                    }
                    throw new DataChangedException(
                            docRef + " has been modified by another user");
                }

                docId = context
                        .select(DOC.ID)
                        .from(DOC)
                        .where(DOC.UUID.eq(docRef.getUuid()))
                        .fetchOne(DOC.ID);
            } else {
                // CREATE/COPY — check doc doesn't already exist, then insert with version
                Objects.requireNonNull(newVersion, "newVersion required for CREATE");
                docId = context
                        .select(DOC.ID)
                        .from(DOC)
                        .where(DOC.TYPE.eq(docRef.getType()))
                        .and(DOC.UUID.eq(docRef.getUuid()))
                        .and(DOC.DELETED.isNull())
                        .fetchOne(DOC.ID);

                if (docId != null) {
                    throw new RuntimeException(
                            "Document already exists with uuid=" + docRef.getUuid());
                }

                docId = context
                        .insertInto(DOC)
                        .set(DOC.TYPE, docRef.getType())
                        .set(DOC.UUID, docRef.getUuid())
                        .set(DOC.NAME, docRef.getName())
                        .set(DOC.VERSION, newVersion)
                        .returning(DOC.ID)
                        .fetchOne(DOC.ID);
            }

            // Ensure doc id is not null.
            Objects.requireNonNull(docId, "Null doc id");

            // Upsert each asset into doc_data
            for (final ImportExportAsset asset : importExportDocument.getExtAssets()) {
                try {
                    final byte[] rawData = asset.getInputData();
                    final DocDataType dataType = asset.getDocDataType();

                    // Check if this ext already exists
                    final Long existingDataId = context
                            .select(DOC_DATA.ID)
                            .from(DOC_DATA)
                            .where(DOC_DATA.FK_DOC_ID.eq(docId))
                            .and(DOC_DATA.EXT.eq(asset.getKey()))
                            .fetchOne(DOC_DATA.ID);

                    if (existingDataId != null) {
                        // Update existing row
                        context.update(DOC_DATA)
                                .set(DOC_DATA.DATA_TYPE, dataType.getPrimitiveValue())
                                .set(toColumnValues(
                                        DOC_DATA.JSON_DATA,
                                        DOC_DATA.TEXT_DATA,
                                        DOC_DATA.BIN_DATA,
                                        dataType,
                                        rawData))
                                .where(DOC_DATA.ID.eq(existingDataId))
                                .execute();
                    } else {
                        // Insert new row
                        context.insertInto(DOC_DATA)
                                .set(DOC_DATA.FK_DOC_ID, docId)
                                .set(DOC_DATA.EXT, asset.getKey())
                                .set(DOC_DATA.DATA_TYPE, dataType.getPrimitiveValue())
                                .set(toColumnValues(
                                        DOC_DATA.JSON_DATA,
                                        DOC_DATA.TEXT_DATA,
                                        DOC_DATA.BIN_DATA,
                                        dataType,
                                        rawData))
                                .execute();
                    }
                } catch (final IOException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }

            // Remove stale extensions that are no longer in the document.
            // Skip for fresh CREATEs where no stale rows can exist.
            if (!auditAction.isCreate()) {
                final List<String> currentExts = importExportDocument.getExtAssets()
                        .stream()
                        .map(ImportExportAsset::getKey)
                        .toList();

                context
                        .deleteFrom(DOC_DATA)
                        .where(DOC_DATA.FK_DOC_ID.eq(docId))
                        .and(DOC_DATA.EXT.notIn(currentExts))
                        .execute();
            }

            // Write audit entry and get the audit ID for snapshot linking
            final Long auditId = context
                    .insertInto(DOC_AUDIT)
                    .set(DOC_AUDIT.FK_DOC_ID, docId)
                    .set(DOC_AUDIT.ACTION, auditAction.getPrimitiveValue())
                    .set(DOC_AUDIT.ACTION_TIME, System.currentTimeMillis())
                    .set(DOC_AUDIT.USER_UUID, NullSafe.get(userRef, UserRef::getUuid))
                    .set(DOC_AUDIT.USER_NAME, NullSafe.get(userRef, UserRef::getDisplayName))
                    .returning(DOC_AUDIT.ID)
                    .fetchOne(DOC_AUDIT.ID);

            Objects.requireNonNull(auditId, "Null audit id");

            // Write deduplicated data snapshots and link to audit entry
            writeSnapshots(context, docId, auditId, importExportDocument);
        });
    }

    /**
     * For each data asset in the document, find or create a deduplicated snapshot row
     * and link it to the given audit entry.
     */
    private void writeSnapshots(final DSLContext context,
                                final long docId,
                                final long auditId,
                                final ImportExportDocument importExportDocument) {
        for (final ImportExportAsset asset : importExportDocument.getExtAssets()) {
            try {
                final byte[] rawData = asset.getInputData();
                final DocDataType dataType = asset.getDocDataType();
                final long dataHash = LongHashFunction.xx3().hashBytes(
                        rawData != null
                                ? rawData
                                : new byte[0]);

                // Look up candidate snapshot rows by hash
                final Result<Record5<Long, Byte, JSON, String, byte[]>> candidates = context
                        .select(DOC_DATA_SNAPSHOT.ID,
                                DOC_DATA_SNAPSHOT.DATA_TYPE,
                                DOC_DATA_SNAPSHOT.JSON_DATA,
                                DOC_DATA_SNAPSHOT.TEXT_DATA,
                                DOC_DATA_SNAPSHOT.BIN_DATA)
                        .from(DOC_DATA_SNAPSHOT)
                        .where(DOC_DATA_SNAPSHOT.FK_DOC_ID.eq(docId))
                        .and(DOC_DATA_SNAPSHOT.EXT.eq(asset.getKey()))
                        .and(DOC_DATA_SNAPSHOT.DATA_HASH.eq(dataHash))
                        .fetch();

                Long snapshotId = null;
                for (final Record5<Long, Byte, JSON, String, byte[]> candidate : candidates) {
                    // Verify actual data content matches (guards against hash collision)
                    if (snapshotDataEquals(candidate, dataType, rawData)) {
                        snapshotId = candidate.get(DOC_DATA_SNAPSHOT.ID);
                        break;
                    }
                }

                if (snapshotId == null) {
                    // No matching snapshot — insert new row
                    InsertSetMoreStep<DocDataSnapshotRecord> insertStep = context
                            .insertInto(DOC_DATA_SNAPSHOT)
                            .set(DOC_DATA_SNAPSHOT.FK_DOC_ID, docId)
                            .set(DOC_DATA_SNAPSHOT.EXT, asset.getKey())
                            .set(DOC_DATA_SNAPSHOT.DATA_TYPE, dataType.getPrimitiveValue())
                            .set(DOC_DATA_SNAPSHOT.DATA_HASH, dataHash);

                    insertStep = insertStep.set(toColumnValues(
                            DOC_DATA_SNAPSHOT.JSON_DATA,
                            DOC_DATA_SNAPSHOT.TEXT_DATA,
                            DOC_DATA_SNAPSHOT.BIN_DATA,
                            dataType,
                            rawData));

                    snapshotId = insertStep
                            .returning(DOC_DATA_SNAPSHOT.ID)
                            .fetchOne(DOC_DATA_SNAPSHOT.ID);
                }

                // Link audit entry to snapshot
                context.insertInto(DOC_AUDIT_DATA_SNAPSHOT)
                        .set(DOC_AUDIT_DATA_SNAPSHOT.FK_DOC_AUDIT_ID, auditId)
                        .set(DOC_AUDIT_DATA_SNAPSHOT.FK_DOC_DATA_SNAPSHOT_ID, snapshotId)
                        .execute();

            } catch (final IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    /**
     * Extract raw bytes from a record's sparse data columns based on data type.
     * Used by both {@code doc_data} reads and {@code doc_data_snapshot} comparisons.
     */
    private static byte[] extractData(final org.jooq.Record record,
                                      final org.jooq.Field<JSON> jsonField,
                                      final org.jooq.Field<String> textField,
                                      final org.jooq.Field<byte[]> binField,
                                      final DocDataType dataType) {
        return switch (dataType) {
            case JSON -> {
                final JSON json = record.get(jsonField);
                if (json == null) {
                    yield null;
                }
                yield json.data().getBytes(StandardCharsets.UTF_8);
            }
            case TEXT -> {
                final String text = record.get(textField);
                if (text == null) {
                    yield null;
                }
                yield text.getBytes(StandardCharsets.UTF_8);
            }
            case BINARY -> record.get(binField);
        };
    }

    /**
     * Convert raw byte data into a column-value map suitable for jOOQ's {@code .set(Map)} method.
     * Populates the appropriate sparse column (json_data, text_data, or bin_data) and
     * explicitly nulls the others. Works for both {@code doc_data} and {@code doc_data_snapshot}
     * tables by accepting field references as parameters.
     */
    private static Map<org.jooq.Field<?>, Object> toColumnValues(
            final org.jooq.Field<JSON> jsonField,
            final org.jooq.Field<String> textField,
            final org.jooq.Field<byte[]> binField,
            final DocDataType dataType,
            final byte[] rawData) {
        final Map<org.jooq.Field<?>, Object> values = HashMap.newHashMap(3);
        switch (dataType) {
            case JSON -> {
                values.put(jsonField, toJson(rawData));
                values.put(textField, null);
                values.put(binField, null);
            }
            case TEXT -> {
                values.put(jsonField, null);
                values.put(textField, toText(rawData));
                values.put(binField, null);
            }
            case BINARY -> {
                values.put(jsonField, null);
                values.put(textField, null);
                values.put(binField, rawData);
            }
        }
        return values;
    }

    private static JSON toJson(final byte[] bytes) {
        try {
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            final String string = new String(bytes, StandardCharsets.UTF_8);
            if (NullSafe.isBlankString(string)) {
                return null;
            }
            return JSON.json(string);
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return null;
    }

    private static String toText(final byte[] bytes) {
        try {
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Compare a candidate snapshot record's data content with the given raw data,
     * using the appropriate column based on data type.
     */
    private boolean snapshotDataEquals(final org.jooq.Record candidate,
                                       final DocDataType dataType,
                                       final byte[] rawData) {
        final byte[] existing = extractData(candidate,
                DOC_DATA_SNAPSHOT.JSON_DATA, DOC_DATA_SNAPSHOT.TEXT_DATA,
                DOC_DATA_SNAPSHOT.BIN_DATA, dataType);
        return Arrays.equals(existing, rawData);
    }

    @Override
    public void delete(final DocRef docRef, final UserRef userRef) {
        JooqUtil.transaction(dataSource, context -> {
            // Get the doc id (active docs only)
            final Long docId = context
                    .select(DOC.ID)
                    .from(DOC)
                    .where(DOC.TYPE.eq(docRef.getType()))
                    .and(DOC.UUID.eq(docRef.getUuid()))
                    .and(DOC.DELETED.isNull())
                    .fetchOne(DOC.ID);

            if (docId != null) {
                // Write a DELETE audit entry
                context
                        .insertInto(DOC_AUDIT)
                        .set(DOC_AUDIT.FK_DOC_ID, docId)
                        .set(DOC_AUDIT.ACTION, AuditAction.DELETE.getPrimitiveValue())
                        .set(DOC_AUDIT.ACTION_TIME, System.currentTimeMillis())
                        .set(DOC_AUDIT.USER_UUID, NullSafe.get(userRef, UserRef::getUuid))
                        .set(DOC_AUDIT.USER_NAME, NullSafe.get(userRef, UserRef::getDisplayName))
                        .execute();

                // Soft-delete: set the deleted timestamp.
                // doc_data, doc_audit, and snapshot rows are intentionally left intact.
                context
                        .update(DOC)
                        .set(DOC.DELETED, System.currentTimeMillis())
                        .where(DOC.ID.eq(docId))
                        .execute();
            }
        });
    }

    @Override
    public List<DocRef> list(final String type) {
        return JooqUtil.contextResult(dataSource, context -> context
                .select(DOC.UUID, DOC.NAME)
                .from(DOC)
                .where(DOC.TYPE.eq(type))
                .and(DOC.DELETED.isNull())
                .orderBy(DOC.UUID)
                .fetch(record ->
                        new DocRef(type, record.get(DOC.UUID), record.get(DOC.NAME))));
    }

    @Override
    public List<DocRef> list(final Collection<String> types) {
        if (NullSafe.isEmptyCollection(types)) {
            return Collections.emptyList();
        }

        return JooqUtil.contextResult(dataSource, context -> context
                .select(DOC.TYPE, DOC.UUID, DOC.NAME)
                .from(DOC)
                .where(DOC.TYPE.in(types))
                .and(DOC.DELETED.isNull())
                .orderBy(DOC.UUID)
                .fetch(record ->
                        new DocRef(record.get(DOC.TYPE), record.get(DOC.UUID), record.get(DOC.NAME))));
    }

    @Override
    public List<DocRef> find(final String type,
                             final String nameFilter,
                             final boolean allowWildCards) {
        return JooqUtil.contextResult(dataSource, context -> {
            final Condition nameCondition;
            if (allowWildCards) {
                final String sqlLike = PatternUtil.createSqlLikeStringFromWildCardFilter(nameFilter);
                nameCondition = DOC.NAME.collate(COLLATION).like(sqlLike);
            } else {
                nameCondition = DOC.NAME.collate(COLLATION).eq(nameFilter);
            }

            return context
                    .select(DOC.UUID, DOC.NAME)
                    .from(DOC)
                    .where(DOC.TYPE.eq(type))
                    .and(nameCondition)
                    .and(DOC.DELETED.isNull())
                    .orderBy(DOC.UUID)
                    .fetch(record ->
                            new DocRef(type, record.get(DOC.UUID), record.get(DOC.NAME)));
        });
    }

    @Override
    public List<DocRef> find(final String type,
                             final List<String> nameFilters,
                             final boolean allowWildCards) {
        if (nameFilters == null || nameFilters.isEmpty()) {
            return Collections.emptyList();
        }

        return JooqUtil.contextResult(dataSource, context -> {
            final Condition nameCondition;
            if (allowWildCards) {
                final List<Condition> conditions = nameFilters
                        .stream()
                        .map(PatternUtil::createSqlLikeStringFromWildCardFilter)
                        .<Condition>map(sqlLike -> DOC.NAME.collate(COLLATION).like(sqlLike))
                        .toList();
                nameCondition = DSL.or(conditions);
            } else {
                nameCondition = DOC.NAME.collate(COLLATION).in(nameFilters);
            }

            return context
                    .select(DOC.UUID, DOC.NAME)
                    .from(DOC)
                    .where(DOC.TYPE.eq(type))
                    .and(nameCondition)
                    .and(DOC.DELETED.isNull())
                    .orderBy(DOC.UUID)
                    .fetch(record ->
                            new DocRef(type, record.get(DOC.UUID), record.get(DOC.NAME)));
        });
    }

    @Override
    public List<DocRef> find(final Collection<String> types,
                             final List<String> nameFilters,
                             final boolean allowWildCards) {
        if (nameFilters == null || nameFilters.isEmpty()) {
            return Collections.emptyList();
        }

        final boolean allTypes = NullSafe.isEmptyCollection(types);

        return JooqUtil.contextResult(dataSource, context -> {
            // Build name condition
            final Condition nameCondition;
            if (allowWildCards) {
                final List<Condition> conditions = nameFilters
                        .stream()
                        .map(PatternUtil::createSqlLikeStringFromWildCardFilter)
                        .map(sqlLike -> (Condition) DOC.NAME.collate(COLLATION).like(sqlLike))
                        .toList();
                nameCondition = DSL.or(conditions);
            } else {
                nameCondition = DOC.NAME.collate(COLLATION).in(nameFilters);
            }

            // Build full condition
            Condition condition = nameCondition.and(DOC.DELETED.isNull());
            if (!allTypes) {
                condition = DOC.TYPE.in(types).and(condition);
            }

            return context
                    .selectDistinct(DOC.TYPE, DOC.UUID, DOC.NAME)
                    .from(DOC)
                    .where(condition)
                    .orderBy(DOC.UUID)
                    .fetch(record ->
                            new DocRef(record.get(DOC.TYPE), record.get(DOC.UUID), record.get(DOC.NAME)));
        });
    }

    @Override
    public ResultPage<DocAuditEntry> getAuditInfo(final DocRef docRef) {
        return JooqUtil.contextResult(dataSource, context -> {
            final List<DocAuditEntry> list = context
                    .select(DOC_AUDIT.ACTION, DOC_AUDIT.ACTION_TIME,
                            DOC_AUDIT.USER_UUID, DOC_AUDIT.USER_NAME)
                    .from(DOC_AUDIT)
                    .join(DOC).on(DOC_AUDIT.FK_DOC_ID.eq(DOC.ID))
                    .where(DOC.UUID.eq(docRef.getUuid()))
                    .orderBy(DOC_AUDIT.ID.asc())
                    .fetch(record -> {
                        final byte actionByte = record.get(DOC_AUDIT.ACTION);
                        final AuditAction action = AuditAction.PRIMITIVE_VALUE_CONVERTER
                                .fromPrimitiveValue(actionByte);
                        return new DocAuditEntry(
                                record.get(DOC_AUDIT.ACTION_TIME),
                                new DocAuditUser(
                                        record.get(DOC_AUDIT.USER_UUID),
                                        record.get(DOC_AUDIT.USER_NAME)),
                                action);
                    });

            return ResultPage.createUnboundedList(list);
        });
    }

    @Override
    public List<DocRef> findDocRefsEmbeddedIn(final DocRef parent) {
        return JooqUtil.contextResult(dataSource, context ->
                context
                        .select(DOC.UUID, DOC.TYPE, DOC.NAME)
                        .from(DOC)
                        .join(DOC_DATA).on(DOC_DATA.FK_DOC_ID.eq(DOC.ID))
                        .where(DOC_DATA.EXT.eq("meta"))
                        .and(DOC.DELETED.isNull())
                        .and(DSL.field("JSON_VALUE({0}, '$.embeddedIn.uuid')",
                                String.class, DOC_DATA.JSON_DATA).eq(parent.getUuid()))
                        .fetch(record ->
                                new DocRef(record.get(DOC.TYPE), record.get(DOC.UUID), record.get(DOC.NAME))));
    }

    /**
     * Physically delete documents that have been soft-deleted for longer than the given retention period.
     * Deletes all associated child rows in FK-safe order:
     * doc_audit_data_snapshot → doc_data_snapshot → doc_audit → doc_data → doc.
     *
     * @param retentionPeriod minimum age of soft-delete before physical removal
     * @return the number of documents physically deleted
     */
    public int physicalDelete(final Duration retentionPeriod) {
        final long cutoff = System.currentTimeMillis() - retentionPeriod.toMillis();

        return JooqUtil.transactionResult(dataSource, context -> {
            // Find doc IDs soft-deleted before the cutoff
            final List<Long> docIds = context.select(DOC.ID)
                    .from(DOC)
                    .where(DOC.DELETED.isNotNull())
                    .and(DOC.DELETED.le(cutoff))
                    .fetch(DOC.ID);

            if (docIds.isEmpty()) {
                return 0;
            }

            LOGGER.info("Physically deleting {} soft-deleted doc(s) older than {}",
                    docIds.size(), retentionPeriod);

            // Delete in FK-safe order: leaves first, root last

            // 1. doc_audit_data_snapshot (references doc_audit and doc_data_snapshot)
            context.deleteFrom(DOC_AUDIT_DATA_SNAPSHOT)
                    .where(DOC_AUDIT_DATA_SNAPSHOT.FK_DOC_AUDIT_ID.in(
                            context.select(DOC_AUDIT.ID).from(DOC_AUDIT)
                                    .where(DOC_AUDIT.FK_DOC_ID.in(docIds))))
                    .execute();

            // 2. doc_data_snapshot (references doc)
            context.deleteFrom(DOC_DATA_SNAPSHOT)
                    .where(DOC_DATA_SNAPSHOT.FK_DOC_ID.in(docIds))
                    .execute();

            // 3. doc_audit (references doc)
            context.deleteFrom(DOC_AUDIT)
                    .where(DOC_AUDIT.FK_DOC_ID.in(docIds))
                    .execute();

            // 4. doc_data (references doc)
            context.deleteFrom(DOC_DATA)
                    .where(DOC_DATA.FK_DOC_ID.in(docIds))
                    .execute();

            // 5. doc (root)
            return context.deleteFrom(DOC)
                    .where(DOC.ID.in(docIds))
                    .execute();
        });
    }
}
