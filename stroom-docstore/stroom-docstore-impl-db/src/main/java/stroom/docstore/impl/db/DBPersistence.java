/*
 * Copyright 2024 Crown Copyright
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
import stroom.docstore.impl.DocumentData;
import stroom.docstore.impl.Persistence;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.string.PatternUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record6;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import static stroom.docstore.impl.db.jooq.tables.Document.DOCUMENT;
import static stroom.docstore.impl.db.jooq.tables.DocumentEntry.DOCUMENT_ENTRY;

@Singleton
public class DBPersistence implements Persistence {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DBPersistence.class);

    private static final RWLockFactory LOCK_FACTORY = new NoLockFactory();

    private final DataSource dataSource;

    @Inject
    DBPersistence(final DocStoreDbConnProvider dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean exists(final DocRef docRef) {
        try {
            return JooqUtil.contextResult(dataSource, context -> getDocumentId(context, docRef)).isPresent();
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public DocumentData create(final DocumentData documentData) throws IOException {
        try {
            validate(documentData);
            return JooqUtil.transactionResult(dataSource, context -> insert(context, documentData));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public Optional<DocumentData> read(final DocRef docRef) throws IOException {
        try {
            return JooqUtil.transactionResult(dataSource, context -> {
                final Optional<Record6<Long, String, String, String, String, String>> optionalRecord = context
                        .select(DOCUMENT.ID,
                                DOCUMENT.TYPE,
                                DOCUMENT.UUID,
                                DOCUMENT.NAME,
                                DOCUMENT.UNIQUE_NAME,
                                DOCUMENT.VERSION)
                        .from(DOCUMENT)
                        .where(DOCUMENT.UUID.eq(docRef.getUuid()))
                        .fetchOptional();

                return optionalRecord.map(record -> {
                    final long id = record.get(DOCUMENT.ID);
                    final String type = record.get(DOCUMENT.TYPE);
                    final String uuid = record.get(DOCUMENT.UUID);
                    final String name = record.get(DOCUMENT.NAME);
                    final String uniqueName = record.get(DOCUMENT.UNIQUE_NAME);
                    final String version = record.get(DOCUMENT.VERSION);
                    final Map<String, byte[]> data = context
                            .select(DOCUMENT_ENTRY.ENTRY, DOCUMENT_ENTRY.DATA)
                            .from(DOCUMENT_ENTRY)
                            .where(DOCUMENT_ENTRY.FK_DOCUMENT_ID.eq(id))
                            .fetch()
                            .collect(Collectors.toMap(
                                    r -> r.get(DOCUMENT_ENTRY.ENTRY),
                                    r -> r.get(DOCUMENT_ENTRY.DATA)));

                    final DocRef ref = DocRef.builder().type(type).uuid(uuid).name(name).build();
                    return DocumentData
                            .builder()
                            .docRef(ref)
                            .version(version)
                            .uniqueName(uniqueName)
                            .data(data)
                            .build();
                });
            });
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public DocumentData update(final String expectedVersion,
                               final DocumentData documentData) throws IOException {
        try {
            Objects.requireNonNull(expectedVersion, "Expected version is null");
            validate(documentData);
            return JooqUtil.transactionResult(dataSource, context -> update(context, expectedVersion, documentData));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }

    private void validate(final DocumentData documentData) {
        Objects.requireNonNull(documentData, "Document data is null");
        Objects.requireNonNull(documentData.getDocRef(), "DocRef is null: " + documentData);
        NullSafe.requireNonBlank(documentData.getDocRef().getType(), () ->
                "Type not set on document: " + documentData);
        NullSafe.requireNonBlank(documentData.getDocRef().getUuid(), () ->
                "UUID not set on document: " + documentData);
        NullSafe.requireNonBlank(documentData.getDocRef().getName(), () ->
                "Name not set on document: " + documentData);
        NullSafe.requireNonBlank(documentData.getVersion(), () ->
                "Version not set on document: " + documentData);
        NullSafe.requireNonBlank(documentData.getUniqueName(), () ->
                "Unique name not set on document: " + documentData);
    }

    @Override
    public void delete(final DocRef docRef) {
        try {
            JooqUtil.transaction(dataSource, context -> {
                final Optional<Long> optional = getDocumentId(context, docRef);
                optional.ifPresent(id -> {
                    context
                            .deleteFrom(DOCUMENT_ENTRY)
                            .where(DOCUMENT_ENTRY.FK_DOCUMENT_ID.eq(id))
                            .execute();
                    context
                            .deleteFrom(DOCUMENT)
                            .where(DOCUMENT.ID.eq(id))
                            .execute();
                });
            });
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public List<DocRef> list(final String type) {
        try {
            return JooqUtil.contextResult(dataSource, context -> context
                            .select(DOCUMENT.TYPE, DOCUMENT.UUID, DOCUMENT.NAME)
                            .from(DOCUMENT)
                            .where(DOCUMENT.TYPE.eq(type))
                            .orderBy(DOCUMENT.UUID)
                            .fetch())
                    .map(r -> new DocRef(
                            r.get(DOCUMENT.TYPE),
                            r.get(DOCUMENT.UUID),
                            r.get(DOCUMENT.NAME)));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public List<DocRef> find(final String type,
                             final String nameFilter,
                             final boolean allowWildCards) {
        final String nameFilterSqlValue = allowWildCards
                ? PatternUtil.createSqlLikeStringFromWildCardFilter(nameFilter)
                : nameFilter;

        final Condition condition;
        if (allowWildCards) {
            condition = DOCUMENT.TYPE.eq(type)
                    .and(DOCUMENT.NAME.like(nameFilterSqlValue));
        } else {
            condition = DOCUMENT.TYPE.eq(type)
                    .and(DOCUMENT.NAME.eq(nameFilterSqlValue));
        }

        try {
            return JooqUtil.contextResult(dataSource, context -> context
                            .select(DOCUMENT.TYPE, DOCUMENT.UUID, DOCUMENT.NAME)
                            .from(DOCUMENT)
                            .where(condition)
                            .orderBy(DOCUMENT.UUID)
                            .fetch())
                    .map(r -> new DocRef(
                            r.get(DOCUMENT.TYPE),
                            r.get(DOCUMENT.UUID),
                            r.get(DOCUMENT.NAME)));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public RWLockFactory getLockFactory() {
        return LOCK_FACTORY;
    }

    private Optional<Long> getDocumentId(final DSLContext context, final DocRef docRef) {
        try {
            return context
                    .select(DOCUMENT.ID)
                    .from(DOCUMENT)
                    .where(DOCUMENT.UUID.eq(docRef.getUuid()))
                    .fetchOptional(DOCUMENT.ID);
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }

    private DocumentData update(final DSLContext context,
                                final String expectedVersion,
                                final DocumentData documentData) {
        try {
            final Optional<Long> optionalDocumentId = getDocumentId(context, documentData.getDocRef());
            if (optionalDocumentId.isEmpty()) {
                throw new DocumentNotFoundException(documentData.getDocRef());
            }

            final long id = optionalDocumentId.get();
            final DocumentData updatedDocument = updateDocument(
                    context,
                    optionalDocumentId.get(),
                    expectedVersion,
                    documentData);
            // Delete existing entries.
            context.deleteFrom(DOCUMENT_ENTRY).where(DOCUMENT_ENTRY.FK_DOCUMENT_ID.eq(id)).execute();
            // Insert new entries.
            insertDocumentEntries(context, id, documentData);
            return updatedDocument;
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }

    private DocumentData updateDocument(final DSLContext context,
                                        final long id,
                                        final String expectedVersion,
                                        final DocumentData documentData) {
        try {
            final int updatedRows = context
                    .update(DOCUMENT)
                    .set(DOCUMENT.NAME, documentData.getDocRef().getName())
                    .set(DOCUMENT.UNIQUE_NAME, documentData.getUniqueName())
                    .set(DOCUMENT.VERSION, documentData.getVersion())
                    .where(DOCUMENT.ID.eq(id))
                    .and(DOCUMENT.VERSION.eq(expectedVersion))
                    .execute();

            if (updatedRows == 0) {
                throw new RuntimeException("Unable to update document: " + documentData);
            }

            return documentData;
        } catch (final RuntimeException e) {
            LOGGER.trace(e.getMessage(), e);
            throw e;
        }
    }

    private DocumentData insert(final DSLContext context,
                                final DocumentData documentData) {
        try {
            final Optional<Long> optional = insertDocument(context, documentData);
            optional.ifPresent(id -> insertDocumentEntries(context, id, documentData));
            return documentData;
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }

    private Optional<Long> insertDocument(final DSLContext context,
                                          final DocumentData documentData) {
        try {
            return context
                    .insertInto(DOCUMENT)
                    .columns(DOCUMENT.TYPE,
                            DOCUMENT.UUID,
                            DOCUMENT.NAME,
                            DOCUMENT.UNIQUE_NAME,
                            DOCUMENT.VERSION)
                    .values(documentData.getDocRef().getType(),
                            documentData.getDocRef().getUuid(),
                            documentData.getDocRef().getName(),
                            documentData.getUniqueName(),
                            documentData.getVersion())
                    .returning(DOCUMENT.ID)
                    .fetchOptional(DOCUMENT.ID);
        } catch (final RuntimeException e) {
            LOGGER.trace(e.getMessage(), e);
            throw e;
        }
    }

    private void insertDocumentEntries(final DSLContext context,
                                       final Long id,
                                       final DocumentData documentData) {
        try {
            documentData.getEntries().forEach(entry -> {
                final byte[] data = documentData.getData(entry);
                insertDocumentEntry(context, id, entry, data);
            });
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }

    private void insertDocumentEntry(final DSLContext context,
                                     final Long documentId,
                                     final String entry,
                                     final byte[] data) {
        try {
            context
                    .insertInto(DOCUMENT_ENTRY)
                    .columns(DOCUMENT_ENTRY.FK_DOCUMENT_ID, DOCUMENT_ENTRY.ENTRY, DOCUMENT_ENTRY.DATA)
                    .values(documentId, entry, data)
                    .onDuplicateKeyUpdate()
                    .set(DOCUMENT_ENTRY.DATA, data)
                    .execute();
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }
}
