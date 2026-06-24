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
import stroom.docstore.api.RWLockFactory;
import stroom.docstore.impl.Persistence;
import stroom.docstore.impl.db.jooq.tables.records.DocDataRecord;
import stroom.docstore.shared.AuditAction;
import stroom.docstore.shared.DocAuditEntry;
import stroom.docstore.shared.DocAuditUser;
import stroom.docstore.shared.DocDataType;
import stroom.importexport.api.ByteArrayImportExportAsset;
import stroom.importexport.api.ImportExportAsset;
import stroom.importexport.api.ImportExportDocument;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.string.PatternUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.JSON;
import org.jooq.UpdateSetMoreStep;
import org.jooq.impl.DSL;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static stroom.docstore.impl.db.jooq.tables.Doc.DOC;
import static stroom.docstore.impl.db.jooq.tables.DocAudit.DOC_AUDIT;
import static stroom.docstore.impl.db.jooq.tables.DocAuditDataSnapshot.DOC_AUDIT_DATA_SNAPSHOT;
import static stroom.docstore.impl.db.jooq.tables.DocData.DOC_DATA;
import static stroom.docstore.impl.db.jooq.tables.DocDataSnapshot.DOC_DATA_SNAPSHOT;

@Singleton
public class DBPersistence implements Persistence {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DBPersistence.class);

    private static final RWLockFactory LOCK_FACTORY = new NoLockFactory();
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
                        .where(DOC.UUID.eq(docRef.getUuid()))));
    }

    @Override
    public Optional<String> getName(final DocRef docRef) {
        return JooqUtil.contextResult(dataSource, context -> context
                .select(DOC.NAME)
                .from(DOC)
                .where(DOC.TYPE.eq(docRef.getType()))
                .and(DOC.UUID.eq(docRef.getUuid()))
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

                        final byte[] data;
                        if (dataType == DocDataType.JSON) {
                            final JSON json = record.get(DOC_DATA.JSON_DATA);
                            data = json != null
                                    ? json.data().getBytes(StandardCharsets.UTF_8)
                                    : null;
                        } else if (dataType == DocDataType.TEXT) {
                            final String text = record.get(DOC_DATA.TEXT_DATA);
                            data = text != null
                                    ? text.getBytes(StandardCharsets.UTF_8)
                                    : null;
                        } else {
                            data = record.get(DOC_DATA.BIN_DATA);
                        }

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
                      final ImportExportDocument importExportDocument) {
        JooqUtil.transaction(dataSource, context -> {
            // Get existing doc id
            final Long existingDocId = context
                    .select(DOC.ID)
                    .from(DOC)
                    .where(DOC.TYPE.eq(docRef.getType()))
                    .and(DOC.UUID.eq(docRef.getUuid()))
                    .fetchOne(DOC.ID);

            if (auditAction.isUpdate()) {
                if (existingDocId == null) {
                    throw new RuntimeException(
                            "Document does not exist with uuid=" + docRef.getUuid());
                }
            } else if (auditAction.isCreate() && existingDocId != null) {
                throw new RuntimeException(
                        "Document already exists with uuid=" + docRef.getUuid());
            }

            final long docId;
            if (existingDocId != null) {
                // Update existing doc name
                context
                        .update(DOC)
                        .set(DOC.NAME, docRef.getName())
                        .where(DOC.ID.eq(existingDocId))
                        .execute();
                docId = existingDocId;
            } else {
                // Insert new doc row
                docId = context
                        .insertInto(DOC)
                        .set(DOC.TYPE, docRef.getType())
                        .set(DOC.UUID, docRef.getUuid())
                        .set(DOC.NAME, docRef.getName())
                        .returning(DOC.ID)
                        .fetchOne()
                        .getId();
            }

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
                        UpdateSetMoreStep<DocDataRecord> updateStep = context.update(DOC_DATA)
                                .set(DOC_DATA.DATA_TYPE, dataType.getPrimitiveValue());

                        // Set the appropriate sparse column, null out others
                        if (dataType == DocDataType.JSON) {
                            updateStep = updateStep
                                    .set(DOC_DATA.JSON_DATA, rawData != null
                                            ? JSON.json(new String(rawData, StandardCharsets.UTF_8))
                                            : null)
                                    .setNull(DOC_DATA.TEXT_DATA)
                                    .setNull(DOC_DATA.BIN_DATA);
                        } else if (dataType == DocDataType.TEXT) {
                            updateStep = updateStep
                                    .setNull(DOC_DATA.JSON_DATA)
                                    .set(DOC_DATA.TEXT_DATA, rawData != null
                                            ? new String(rawData, StandardCharsets.UTF_8)
                                            : null)
                                    .setNull(DOC_DATA.BIN_DATA);
                        } else {
                            updateStep = updateStep
                                    .setNull(DOC_DATA.JSON_DATA)
                                    .setNull(DOC_DATA.TEXT_DATA)
                                    .set(DOC_DATA.BIN_DATA, rawData);
                        }

                        updateStep
                                .where(DOC_DATA.ID.eq(existingDataId))
                                .execute();
                    } else {
                        // Insert new row
                        var insertStep = context
                                .insertInto(DOC_DATA)
                                .set(DOC_DATA.FK_DOC_ID, docId)
                                .set(DOC_DATA.EXT, asset.getKey())
                                .set(DOC_DATA.DATA_TYPE, dataType.getPrimitiveValue());

                        if (dataType == DocDataType.JSON) {
                            insertStep = insertStep.set(DOC_DATA.JSON_DATA,
                                    rawData != null
                                            ? JSON.json(new String(rawData, StandardCharsets.UTF_8))
                                            : null);
                        } else if (dataType == DocDataType.TEXT) {
                            insertStep = insertStep.set(DOC_DATA.TEXT_DATA,
                                    rawData != null
                                            ? new String(rawData, StandardCharsets.UTF_8)
                                            : null);
                        } else {
                            insertStep = insertStep.set(DOC_DATA.BIN_DATA, rawData);
                        }

                        insertStep.execute();
                    }
                } catch (final IOException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }

            // Remove stale extensions that are no longer in the document
            if (existingDocId != null) {
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

            // Write audit entry
            context
                    .insertInto(DOC_AUDIT)
                    .set(DOC_AUDIT.FK_DOC_ID, docId)
                    .set(DOC_AUDIT.ACTION, auditAction.getPrimitiveValue())
                    .set(DOC_AUDIT.ACTION_TIME, System.currentTimeMillis())
                    .execute();
        });
    }

    @Override
    public void delete(final DocRef docRef) {
        JooqUtil.transaction(dataSource, context -> {
            // Get the doc id
            final Long docId = context
                    .select(DOC.ID)
                    .from(DOC)
                    .where(DOC.TYPE.eq(docRef.getType()))
                    .and(DOC.UUID.eq(docRef.getUuid()))
                    .fetchOne(DOC.ID);

            if (docId != null) {
                // Delete doc_data rows (FK cascading is not configured for safety)
                context
                        .deleteFrom(DOC_DATA)
                        .where(DOC_DATA.FK_DOC_ID.eq(docId))
                        .execute();

                // Write a DELETE audit entry before removing the doc
                context
                        .insertInto(DOC_AUDIT)
                        .set(DOC_AUDIT.FK_DOC_ID, docId)
                        .set(DOC_AUDIT.ACTION, AuditAction.DELETE.getPrimitiveValue())
                        .set(DOC_AUDIT.ACTION_TIME, System.currentTimeMillis())
                        .execute();

                // Soft-delete: set the deleted timestamp
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

    @Override
    public RWLockFactory getLockFactory() {
        return LOCK_FACTORY;
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
