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

package stroom.index.impl.db;

import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.index.impl.IndexFieldDao;
import stroom.index.impl.db.jooq.tables.records.IndexFieldRecord;
import stroom.index.shared.AddField;
import stroom.index.shared.DeleteField;
import stroom.index.shared.IndexFieldImpl;
import stroom.index.shared.UpdateField;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.datasource.AnalyzerType;
import stroom.query.api.datasource.DenseVectorFieldConfig;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.IndexField;
import stroom.query.api.datasource.IndexFieldFields;
import stroom.query.common.v2.FieldProviderImpl;
import stroom.query.common.v2.SimpleStringExpressionParser;
import stroom.query.common.v2.SimpleStringExpressionParser.FieldProvider;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.StringUtil;

import jakarta.inject.Inject;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.InsertValuesStep9;
import org.jooq.JSON;
import org.jooq.OrderField;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import java.sql.SQLTransactionRollbackException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static stroom.index.impl.db.jooq.tables.IndexField.INDEX_FIELD;
import static stroom.index.impl.db.jooq.tables.IndexFieldSource.INDEX_FIELD_SOURCE;

public class IndexFieldDaoImpl implements IndexFieldDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexFieldDaoImpl.class);
    public static final int MAX_DEADLOCK_RETRY_ATTEMPTS = 20;

    private static final Map<String, Field<?>> FIELD_MAP = Map.of(
            IndexFieldFields.NAME, INDEX_FIELD.NAME,
            IndexFieldFields.TYPE, INDEX_FIELD.TYPE,
            IndexFieldFields.STORE, INDEX_FIELD.STORED,
            IndexFieldFields.INDEX, INDEX_FIELD.INDEXED,
            IndexFieldFields.POSITIONS, INDEX_FIELD.TERM_POSITIONS,
            IndexFieldFields.ANALYSER, INDEX_FIELD.ANALYZER,
            IndexFieldFields.CASE_SENSITIVE, INDEX_FIELD.CASE_SENSITIVE);

    private final IndexDbConnProvider queryDatasourceDbConnProvider;
    private final ExpressionMapper expressionMapper;

    @Inject
    IndexFieldDaoImpl(final IndexDbConnProvider queryDatasourceDbConnProvider,
                      final ExpressionMapperFactory expressionMapperFactory) {
        this.queryDatasourceDbConnProvider = queryDatasourceDbConnProvider;
        expressionMapper = expressionMapperFactory.create();
        expressionMapper.map(IndexFieldFields.NAME_FIELD, INDEX_FIELD.NAME, string -> string);
        expressionMapper.map(IndexFieldFields.TYPE_FIELD, INDEX_FIELD.TYPE, string ->
                NullSafe.get(FieldType.fromDisplayValue(string), FieldType::getPrimitiveValue));
        expressionMapper.map(IndexFieldFields.STORE_FIELD, INDEX_FIELD.STORED, Boolean::valueOf);
        expressionMapper.map(IndexFieldFields.INDEX_FIELD, INDEX_FIELD.INDEXED, Boolean::valueOf);
        expressionMapper.map(IndexFieldFields.POSITIONS_FIELD, INDEX_FIELD.TERM_POSITIONS, Boolean::valueOf);
        expressionMapper.map(IndexFieldFields.ANALYSER_FIELD, INDEX_FIELD.ANALYZER, string -> string);
        expressionMapper.map(IndexFieldFields.CASE_SENSITIVE_FIELD, INDEX_FIELD.CASE_SENSITIVE, Boolean::valueOf);
    }

    private void ensureFieldSource(final DocRef docRef) {
        JooqUtil.context(queryDatasourceDbConnProvider, context -> {
            final Optional<Integer> optional = getFieldSource(context, docRef, false);
            if (optional.isEmpty()) {
                createFieldSource(context, docRef);
            }
        });
    }

    private Optional<Integer> getFieldSource(final DocRef docRef,
                                             final boolean lockFieldSource) {

        return JooqUtil.contextResult(queryDatasourceDbConnProvider, context ->
                getFieldSource(context, docRef, lockFieldSource));
    }

    private Optional<Integer> getFieldSource(final DSLContext context,
                                             final DocRef docRef,
                                             final boolean lockFieldSource) {
        final SelectConditionStep<Record1<Integer>> c = context
                .select(INDEX_FIELD_SOURCE.ID)
                .from(INDEX_FIELD_SOURCE)
                .where(INDEX_FIELD_SOURCE.TYPE.eq(docRef.getType()))
                .and(INDEX_FIELD_SOURCE.UUID.eq(docRef.getUuid()));

        if (lockFieldSource) {
            return c.forUpdate()
                    .fetchOptional(INDEX_FIELD_SOURCE.ID);
        } else {
            return c.fetchOptional(INDEX_FIELD_SOURCE.ID);
        }
    }

    private void createFieldSource(final DSLContext context, final DocRef docRef) {
        context
                .insertInto(INDEX_FIELD_SOURCE)
                .set(INDEX_FIELD_SOURCE.TYPE, docRef.getType())
                .set(INDEX_FIELD_SOURCE.UUID, docRef.getUuid())
                .set(INDEX_FIELD_SOURCE.NAME, docRef.getName())
                .onDuplicateKeyUpdate()
                .set(INDEX_FIELD_SOURCE.NAME, docRef.getName())
                .execute();
    }

    @Override
    public void addFields(final DocRef docRef, final Collection<IndexField> fields) {
        if (NullSafe.hasItems(fields)) {
            // Do this outside the txn so other threads can see it asap
            ensureFieldSource(docRef);

            boolean success = false;
            final AtomicInteger attempt = new AtomicInteger(0);

            while (!success) {
                if (attempt.incrementAndGet() > MAX_DEADLOCK_RETRY_ATTEMPTS) {
                    throw new RuntimeException(LogUtil.message("Gave up retrying {} upsert after {} attempts",
                            INDEX_FIELD.getName(), attempt.get()));
                }

                try {
                    JooqUtil.transaction(queryDatasourceDbConnProvider, txnContext -> {
                        // Get a record lock on the field source, so we are the only thread
                        // that can mutate the index fields for that source, else we can get a deadlock.
                        final int fieldSourceId = getFieldSource(txnContext, docRef, true)
                                .orElseThrow(() -> new RuntimeException("No field source found for " + docRef));

                        // Establish which fields are already there, so we don't need to touch them.
                        // This will reduce the number of records/gaps locked so hopefully reduce the risk
                        // of deadlocks.
                        final Set<String> existingFieldNames = new HashSet<>(txnContext.select(INDEX_FIELD.NAME)
                                .from(INDEX_FIELD)
                                .where(INDEX_FIELD.FK_INDEX_FIELD_SOURCE_ID.eq(fieldSourceId))
                                .fetch(INDEX_FIELD.NAME));

                        // Insert any new fields under lock
                        InsertValuesStep9<
                                IndexFieldRecord,
                                Integer,
                                Byte,
                                String,
                                String,
                                Boolean,
                                Boolean,
                                Boolean,
                                Boolean,
                                JSON> c = txnContext.insertInto(INDEX_FIELD,
                                INDEX_FIELD.FK_INDEX_FIELD_SOURCE_ID,
                                INDEX_FIELD.TYPE,
                                INDEX_FIELD.NAME,
                                INDEX_FIELD.ANALYZER,
                                INDEX_FIELD.INDEXED,
                                INDEX_FIELD.STORED,
                                INDEX_FIELD.TERM_POSITIONS,
                                INDEX_FIELD.CASE_SENSITIVE,
                                INDEX_FIELD.DENSE_VECTOR);

                        int fieldCount = 0;
                        for (final IndexField field : fields) {
                            if (!existingFieldNames.contains(field.getFldName())) {
                                c = c.values(
                                        fieldSourceId,
                                        field.getFldType().getPrimitiveValue(),
                                        field.getFldName(),
                                        field.getAnalyzerType().getDisplayValue(),
                                        field.isIndexed(),
                                        field.isStored(),
                                        field.isTermPositions(),
                                        field.isCaseSensitive(),
                                        writeDenseVector(field.getDenseVectorFieldConfig()));
                                fieldCount++;
                            }
                        }
                        LOGGER.debug("{} fields to upsert on {}", fieldCount, docRef);
                        if (fieldCount > 0) {
                            // The update part doesn't update anything, intentionally
                            c.onDuplicateKeyUpdate()
                                    .set(INDEX_FIELD.FK_INDEX_FIELD_SOURCE_ID, fieldSourceId)
                                    .execute();
                        }
                    });
                    success = true;
                } catch (final Exception e) {
                    // Deadlocks are likely as the upsert will create gap locks in the ID idx which has
                    // fields from different indexes all mixed in together.
                    if (e instanceof DataAccessException
                        && e.getCause() instanceof final SQLTransactionRollbackException sqlTxnRollbackEx
                        && NullSafe.containsIgnoringCase(sqlTxnRollbackEx.getMessage(), "deadlock")) {
                        LOGGER.warn(() -> LogUtil.message(
                                "Deadlock trying to upsert {} {} into {}. Attempt: {}. Will retry. " +
                                "Enable DEBUG for full stacktrace.",
                                fields.size(),
                                StringUtil.plural("field", fields.size()),
                                INDEX_FIELD.getName(),
                                attempt.get()));
                        LOGGER.debug(e.getMessage(), e);
                    } else {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    @Override
    public ResultPage<IndexField> findFields(final FindFieldCriteria criteria) {
        final Optional<Integer> optional = getFieldSource(criteria.getDataSourceRef(), false);

        if (optional.isEmpty()) {
            return ResultPage.createCriterialBasedList(Collections.emptyList(), criteria);
        }

        final List<Condition> conditions = new ArrayList<>();
        conditions.add(INDEX_FIELD.FK_INDEX_FIELD_SOURCE_ID.eq(optional.get()));

        final FieldProvider fieldProvider = new FieldProviderImpl(
                List.of(IndexFieldFields.NAME),
                List.of(IndexFieldFields.NAME,
                        IndexFieldFields.TYPE,
                        IndexFieldFields.STORE,
                        IndexFieldFields.INDEX,
                        IndexFieldFields.POSITIONS,
                        IndexFieldFields.ANALYSER,
                        IndexFieldFields.CASE_SENSITIVE));
        try {
            final Optional<ExpressionOperator> optionalExpressionOperator = SimpleStringExpressionParser
                    .create(fieldProvider, criteria.getFilter());
            optionalExpressionOperator.ifPresent(expressionOperator ->
                    conditions.add(expressionMapper.apply(expressionOperator)));
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            return ResultPage.empty();
        }

        if (criteria.getQueryable() != null) {
            conditions.add(INDEX_FIELD.INDEXED.eq(criteria.getQueryable()));
        }

        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);
        final int offset = JooqUtil.getOffset(criteria.getPageRequest());
        final int limit = JooqUtil.getLimit(criteria.getPageRequest(), true);

        final List<IndexField> fieldInfoList = JooqUtil
                .contextResult(queryDatasourceDbConnProvider, context -> context
                        .select(INDEX_FIELD.TYPE,
                                INDEX_FIELD.NAME,
                                INDEX_FIELD.ANALYZER,
                                INDEX_FIELD.INDEXED,
                                INDEX_FIELD.STORED,
                                INDEX_FIELD.TERM_POSITIONS,
                                INDEX_FIELD.CASE_SENSITIVE,
                                INDEX_FIELD.DENSE_VECTOR)
                        .from(INDEX_FIELD)
                        .where(conditions)
                        .orderBy(orderFields)
                        .limit(offset, limit)
                        .fetch())
                .map(r -> {
                    final byte typeId = r.get(INDEX_FIELD.TYPE);
                    final String name = r.get(INDEX_FIELD.NAME);
                    final String analyzer = r.get(INDEX_FIELD.ANALYZER);
                    final boolean indexed = r.get(INDEX_FIELD.INDEXED);
                    final boolean stored = r.get(INDEX_FIELD.STORED);
                    final boolean termPositions = r.get(INDEX_FIELD.TERM_POSITIONS);
                    final boolean caseSensitive = r.get(INDEX_FIELD.CASE_SENSITIVE);
                    final JSON denseVector = r.get(INDEX_FIELD.DENSE_VECTOR);

                    final FieldType fieldType = FieldType.fromTypeId(typeId);
                    final AnalyzerType analyzerType = AnalyzerType.fromDisplayValue(analyzer);
                    return IndexFieldImpl
                            .builder()
                            .fldName(name)
                            .fldType(fieldType)
                            .analyzerType(analyzerType)
                            .indexed(indexed)
                            .stored(stored)
                            .termPositions(termPositions)
                            .caseSensitive(caseSensitive)
                            .denseVectorFieldConfig(readDenseVector(denseVector))
                            .build();
                });
        return ResultPage.createCriterialBasedList(fieldInfoList, criteria);
    }

    private DenseVectorFieldConfig readDenseVector(final JSON denseVector) {
        try {
            if (denseVector != null) {
                return JsonUtil.readValue(denseVector.data(),
                        DenseVectorFieldConfig.class);
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
        return null;
    }

    private JSON writeDenseVector(final DenseVectorFieldConfig denseVector) {
        try {
            if (denseVector != null) {
                final String json = JsonUtil.writeValueAsString(denseVector);
                return JSON.json(json);
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
        return null;
    }

    @Override
    public int getFieldCount(final DocRef docRef) {
        final Optional<Integer> optFieldSource = getFieldSource(docRef, false);
        if (optFieldSource.isPresent()) {
            return JooqUtil.contextResult(queryDatasourceDbConnProvider, context -> context
                    .selectCount()
                    .from(INDEX_FIELD)
                    .where(INDEX_FIELD.FK_INDEX_FIELD_SOURCE_ID.eq(optFieldSource.get()))
                    .fetchOne(DSL.count()));
        } else {
            return 0;
        }
    }

    @Override
    public void addField(final AddField addField) {
        final DocRef docRef = addField.getIndexDocRef();
        final IndexFieldImpl field = addField.getIndexField();

        // Do this outside the txn so other threads can see it asap
        ensureFieldSource(docRef);

        JooqUtil.transaction(queryDatasourceDbConnProvider, txnContext -> {
            // Get a record lock on the field source, so we are the only thread
            // that can mutate the index fields for that source, else we can get a deadlock.
            final int fieldSourceId = getFieldSource(txnContext, docRef, true)
                    .orElseThrow(() -> new RuntimeException("No field source found for " + docRef));
            txnContext
                    .insertInto(INDEX_FIELD,
                            INDEX_FIELD.FK_INDEX_FIELD_SOURCE_ID,
                            INDEX_FIELD.TYPE,
                            INDEX_FIELD.NAME,
                            INDEX_FIELD.ANALYZER,
                            INDEX_FIELD.INDEXED,
                            INDEX_FIELD.STORED,
                            INDEX_FIELD.TERM_POSITIONS,
                            INDEX_FIELD.CASE_SENSITIVE,
                            INDEX_FIELD.DENSE_VECTOR)
                    .values(fieldSourceId,
                            field.getFldType().getPrimitiveValue(),
                            field.getFldName(),
                            field.getAnalyzerType().getDisplayValue(),
                            field.isIndexed(),
                            field.isStored(),
                            field.isTermPositions(),
                            field.isCaseSensitive(),
                            writeDenseVector(field.getDenseVectorFieldConfig()))
                    .execute();
        });
    }

    @Override
    public void updateField(final UpdateField updateField) {
        final DocRef docRef = updateField.getIndexDocRef();
        final IndexFieldImpl field = updateField.getIndexField();

        // Do this outside the txn so other threads can see it asap
        ensureFieldSource(docRef);

        JooqUtil.transaction(queryDatasourceDbConnProvider, txnContext -> {
            // Get a record lock on the field source, so we are the only thread
            // that can mutate the index fields for that source, else we can get a deadlock.
            final int fieldSourceId = getFieldSource(txnContext, docRef, true)
                    .orElseThrow(() -> new RuntimeException("No field source found for " + docRef));
            txnContext
                    .update(INDEX_FIELD)
                    .set(INDEX_FIELD.TYPE, field.getFldType().getPrimitiveValue())
                    .set(INDEX_FIELD.NAME, field.getFldName())
                    .set(INDEX_FIELD.ANALYZER, field.getAnalyzerType().getDisplayValue())
                    .set(INDEX_FIELD.INDEXED, field.isIndexed())
                    .set(INDEX_FIELD.STORED, field.isStored())
                    .set(INDEX_FIELD.TERM_POSITIONS, field.isTermPositions())
                    .set(INDEX_FIELD.CASE_SENSITIVE, field.isCaseSensitive())
                    .set(INDEX_FIELD.DENSE_VECTOR, writeDenseVector(field.getDenseVectorFieldConfig()))
                    .where(INDEX_FIELD.FK_INDEX_FIELD_SOURCE_ID.eq(fieldSourceId))
                    .and(INDEX_FIELD.NAME.eq(updateField.getFieldName()))
                    .execute();
        });
    }

    @Override
    public void deleteField(final DeleteField deleteField) {
        final DocRef docRef = deleteField.getIndexDocRef();
        JooqUtil.transaction(queryDatasourceDbConnProvider, txnContext -> {
            // Get a record lock on the field source, so we are the only thread
            // that can mutate the index fields for that source, else we can get a deadlock.
            final int fieldSourceId = getFieldSource(txnContext, docRef, true)
                    .orElseThrow(() -> new RuntimeException("No field source found for " + docRef));
            txnContext
                    .deleteFrom(INDEX_FIELD)
                    .where(INDEX_FIELD.FK_INDEX_FIELD_SOURCE_ID.eq(fieldSourceId))
                    .and(INDEX_FIELD.NAME.eq(deleteField.getFieldName()))
                    .execute();
        });
    }

    @Override
    public void deleteAll(final DocRef docRef) {
        JooqUtil.transaction(queryDatasourceDbConnProvider, txnContext -> getFieldSource(txnContext, docRef, true)
                .ifPresent(fieldSourceId -> {
                    txnContext
                            .deleteFrom(INDEX_FIELD)
                            .where(INDEX_FIELD.FK_INDEX_FIELD_SOURCE_ID.eq(fieldSourceId))
                            .execute();
                    txnContext
                            .deleteFrom(INDEX_FIELD_SOURCE)
                            .where(INDEX_FIELD_SOURCE.TYPE.eq(docRef.getType()))
                            .and(INDEX_FIELD_SOURCE.UUID.eq(docRef.getUuid()))
                            .execute();
                }));
    }

    @Override
    public void copyAll(final DocRef source, final DocRef dest) {
        // Do this outside the txn so other threads can see it asap
        ensureFieldSource(dest);

        JooqUtil.transaction(queryDatasourceDbConnProvider, txnContext -> {
            // Get a record lock on the field source, so we are the only thread
            // that can mutate the index fields for that source, else we can get a deadlock.
            final int sourceId = getFieldSource(txnContext, source, true)
                    .orElseThrow(() -> new RuntimeException("No field source found for " + source));
            final int destId = getFieldSource(txnContext, dest, true)
                    .orElseThrow(() -> new RuntimeException("No field source found for " + dest));
            txnContext
                    .insertInto(INDEX_FIELD)
                    .columns(INDEX_FIELD.FK_INDEX_FIELD_SOURCE_ID,
                            INDEX_FIELD.TYPE,
                            INDEX_FIELD.NAME,
                            INDEX_FIELD.ANALYZER,
                            INDEX_FIELD.INDEXED,
                            INDEX_FIELD.STORED,
                            INDEX_FIELD.TERM_POSITIONS,
                            INDEX_FIELD.CASE_SENSITIVE,
                            INDEX_FIELD.DENSE_VECTOR)
                    .select(DSL.select(
                                    DSL.val(destId),
                                    INDEX_FIELD.TYPE,
                                    INDEX_FIELD.NAME,
                                    INDEX_FIELD.ANALYZER,
                                    INDEX_FIELD.INDEXED,
                                    INDEX_FIELD.STORED,
                                    INDEX_FIELD.TERM_POSITIONS,
                                    INDEX_FIELD.CASE_SENSITIVE,
                                    INDEX_FIELD.DENSE_VECTOR)
                            .from(INDEX_FIELD)
                            .where(INDEX_FIELD.FK_INDEX_FIELD_SOURCE_ID.eq(sourceId)))
                    .execute();
        });
    }
}

