/*
 * Copyright 2018 Crown Copyright
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

import stroom.datasource.api.v2.AnalyzerType;
import stroom.datasource.api.v2.FieldType;
import stroom.datasource.api.v2.FindFieldCriteria;
import stroom.datasource.api.v2.IndexField;
import stroom.db.util.JooqUtil;
import stroom.db.util.StringMatchConditionUtil;
import stroom.docref.DocRef;
import stroom.index.impl.IndexFieldDao;
import stroom.index.shared.IndexFieldImpl;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.StringUtil;

import jakarta.inject.Inject;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;

import java.sql.SQLTransactionRollbackException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static stroom.index.impl.db.jooq.tables.IndexField.INDEX_FIELD;
import static stroom.index.impl.db.jooq.tables.IndexFieldSource.INDEX_FIELD_SOURCE;

public class IndexFieldDaoImpl implements IndexFieldDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexFieldDaoImpl.class);
    public static final int MAX_DEADLOCK_RETRY_ATTEMPTS = 20;

    private final IndexDbConnProvider queryDatasourceDbConnProvider;

    @Inject
    IndexFieldDaoImpl(final IndexDbConnProvider queryDatasourceDbConnProvider) {
        this.queryDatasourceDbConnProvider = queryDatasourceDbConnProvider;
    }

    private void ensureFieldSource(final DocRef docRef) {
        JooqUtil.context(queryDatasourceDbConnProvider, context -> {
            Optional<Integer> optional = getFieldSource(context, docRef, false);
            if (optional.isEmpty()) {
                createFieldSource(context, docRef);
            }
        });
    }

    private Optional<Integer> getFieldSource(final DocRef docRef) {

        return JooqUtil.contextResult(queryDatasourceDbConnProvider, context ->
                getFieldSource(context, docRef, false));
    }

    private Optional<Integer> getFieldSourceWithLock(final DSLContext context,
                                                     final DocRef docRef) {
        return getFieldSource(context, docRef, true);
    }

    private Optional<Integer> getFieldSource(final DSLContext context,
                                             final DocRef docRef,
                                             final boolean lockFieldSource) {
        var c = context
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
        // TODO Consider using JooqUtil.tryCreate() as onDuplicateKeyIgnore will
        //  ignore any error, not just dup keys
        context
                .insertInto(INDEX_FIELD_SOURCE)
                .set(INDEX_FIELD_SOURCE.TYPE, docRef.getType())
                .set(INDEX_FIELD_SOURCE.UUID, docRef.getUuid())
                .set(INDEX_FIELD_SOURCE.NAME, docRef.getName())
                .onDuplicateKeyIgnore()
                .returningResult(INDEX_FIELD_SOURCE.ID)
                .execute();
    }

    @Override
    public void addFields(final DocRef docRef, final Collection<IndexField> fields) {
        if (NullSafe.hasItems(fields)) {
            // Do this outside the txn so other threads can see it asap
            ensureFieldSource(docRef);

            boolean success = false;
            AtomicInteger attempt = new AtomicInteger(0);

            while (!success) {
                if (attempt.incrementAndGet() > MAX_DEADLOCK_RETRY_ATTEMPTS) {
                    throw new RuntimeException(LogUtil.message("Gave up retrying {} upsert after {} attempts",
                            INDEX_FIELD.getName(), attempt.get()));
                }

                try {
                    JooqUtil.transaction(queryDatasourceDbConnProvider, txnContext -> {
                        // Get a record lock on the field source, so we are the only thread
                        // that can mutate the index fields for that source, else we can get a deadlock.
                        final int fieldSourceId = getFieldSourceWithLock(txnContext, docRef)
                                .orElseThrow(() -> new RuntimeException("No field source found for " + docRef));

                        // Establish which fields are already there, so we don't need to touch them.
                        // This will reduce the number of records/gaps locked so hopefully reduce the risk
                        // of deadlocks.
                        final Set<String> existingFieldNames = new HashSet<>(txnContext.select(INDEX_FIELD.NAME)
                                .from(INDEX_FIELD)
                                .where(INDEX_FIELD.FK_INDEX_FIELD_SOURCE_ID.eq(fieldSourceId))
                                .fetch(INDEX_FIELD.NAME));

                        // Insert any new fields under lock
                        var c = txnContext.insertInto(INDEX_FIELD,
                                INDEX_FIELD.FK_INDEX_FIELD_SOURCE_ID,
                                INDEX_FIELD.TYPE,
                                INDEX_FIELD.NAME,
                                INDEX_FIELD.ANALYZER,
                                INDEX_FIELD.INDEXED,
                                INDEX_FIELD.STORED,
                                INDEX_FIELD.TERM_POSITIONS,
                                INDEX_FIELD.CASE_SENSITIVE);

                        int fieldCount = 0;
                        for (final IndexField field : fields) {
                            if (!existingFieldNames.contains(field.getFldName())) {
                                c = c.values(
                                        fieldSourceId,
                                        (byte) field.getFldType().getIndex(),
                                        field.getFldName(),
                                        field.getAnalyzerType().getDisplayValue(),
                                        field.isIndexed(),
                                        field.isStored(),
                                        field.isTermPositions(),
                                        field.isCaseSensitive());
                                fieldCount++;
                            }
                        }
                        LOGGER.debug("{} fields to upsert on {}", fieldCount, docRef);
                        if (fieldCount > 0) {
                            // The update part doesn't update anything, intentionally, as we are not changing
                            // records, just silently ignoring ones that are already there
                            c.onDuplicateKeyUpdate()
                                    .set(INDEX_FIELD.FK_INDEX_FIELD_SOURCE_ID, fieldSourceId)
                                    .execute();
                        }
                    });
                    success = true;
                } catch (Exception e) {
                    // Deadlocks are likely as the upsert will create gap locks in the ID idx which has
                    // fields from different indexes all mixed in together.
                    if (e instanceof DataAccessException
                            && e.getCause() instanceof SQLTransactionRollbackException sqlTxnRollbackEx
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
        final Optional<Integer> optional = getFieldSource(criteria.getDataSourceRef());

        if (optional.isEmpty()) {
            return ResultPage.createCriterialBasedList(Collections.emptyList(), criteria);
        }

        final List<Condition> conditions = new ArrayList<>();
        conditions.add(INDEX_FIELD.FK_INDEX_FIELD_SOURCE_ID.eq(optional.get()));
        conditions.add(StringMatchConditionUtil.getCondition(INDEX_FIELD.NAME, criteria.getStringMatch()));
        if (criteria.getQueryable() != null) {
            conditions.add(INDEX_FIELD.INDEXED.eq(criteria.getQueryable()));
        }

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
                                INDEX_FIELD.CASE_SENSITIVE)
                        .from(INDEX_FIELD)
                        .where(conditions)
                        .orderBy(INDEX_FIELD.NAME)
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

                    final FieldType fieldType = FieldType.get(typeId);
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
                            .build();
                });
        return ResultPage.createCriterialBasedList(fieldInfoList, criteria);
    }
}

