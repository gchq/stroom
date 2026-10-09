/*
 * Copyright 2026 Crown Copyright
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

package stroom.sqlstore.impl.db;

import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.JooqUtil;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionUtil;
import stroom.sqlstore.api.UpdatableTemporalStore;
import stroom.sqlstore.impl.UpdatableTemporalStoreDao;
import stroom.sqlstore.shared.ChangeOperation;
import stroom.sqlstore.shared.TemporalStoreTimeRange;
import stroom.util.date.DateUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TemporalEntry;
import stroom.util.shared.TemporalEntryId;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.BatchBindStep;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.Record3;
import org.jooq.SelectHavingStep;
import org.jooq.Table;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * jOOQ implementation of {@link UpdatableTemporalStoreDao}.
 *
 * <h3>Scoping is always by document UUID</h3>
 * <p>Every method takes a {@code docUuid} and every predicate, {@code GROUP BY} and join
 * uses the {@code doc_uuid} column. The {@code map_name} column is a denormalised label
 * written for the benefit of the Data tab; it is never used to scope a read or a write, and
 * after the owning document is renamed the labels on existing rows are stale. Terms on
 * {@link UpdatableTemporalStore#MAP_FIELD} are therefore stripped from incoming expressions
 * before they reach SQL — honouring them would make a rename look like data loss.</p>
 *
 * <p>The store previously keyed on {@code map_name}, which meant renaming the owning document
 * orphaned every row and two same-named documents shared one dataset.</p>
 */
@Singleton
class UpdatableTemporalStoreDaoImpl implements UpdatableTemporalStoreDao {

    private static final stroom.sqlstore.impl.db.jooq.tables.UpdatableTemporalStore UPDATABLE_TEMPORAL_STORE =
            stroom.sqlstore.impl.db.jooq.tables.UpdatableTemporalStore.UPDATABLE_TEMPORAL_STORE;

    private final SqlStoreDbConnProvider sqlStoreDbConnProvider;
    private final ExpressionMapperFactory expressionMapperFactory;
    private final ExpressionMapper expressionMapper;

    @Inject
    UpdatableTemporalStoreDaoImpl(final SqlStoreDbConnProvider sqlStoreDbConnProvider,
                                  final ExpressionMapperFactory expressionMapperFactory) {
        this.sqlStoreDbConnProvider = sqlStoreDbConnProvider;
        this.expressionMapperFactory = expressionMapperFactory;
        this.expressionMapper = expressionMapperFactory.create();
        // Deliberately no mapping for MAP_FIELD - see the class javadoc. Map terms are stripped.
        expressionMapper.map(UpdatableTemporalStore.KEY_FIELD,
                UPDATABLE_TEMPORAL_STORE.MAP_KEY,
                String::valueOf);
        expressionMapper.map(UpdatableTemporalStore.TIME_FIELD,
                UPDATABLE_TEMPORAL_STORE.EFFECTIVE_TIME,
                DateUtil::parseUnknownString);
        expressionMapper.map(UpdatableTemporalStore.VALUE_FIELD,
                UPDATABLE_TEMPORAL_STORE.MAP_VALUE,
                String::valueOf);
    }

    @Override
    public TemporalEntry create(final String docUuid, final TemporalEntry entry) {
        validateDocUuid(docUuid);
        validateTemporalEntry(entry);
        JooqUtil.context(sqlStoreDbConnProvider, context -> context
                .insertInto(UPDATABLE_TEMPORAL_STORE)
                .set(UPDATABLE_TEMPORAL_STORE.DOC_UUID, docUuid)
                .set(UPDATABLE_TEMPORAL_STORE.MAP_NAME, entry.getMap())
                .set(UPDATABLE_TEMPORAL_STORE.MAP_KEY, entry.getKey())
                .set(UPDATABLE_TEMPORAL_STORE.EFFECTIVE_TIME, entry.getEffectiveTimeMs())
                .set(UPDATABLE_TEMPORAL_STORE.MAP_VALUE, entry.getValue())
                .onDuplicateKeyUpdate()
                // Refresh the label too, so a row rewritten after a rename is corrected.
                .set(UPDATABLE_TEMPORAL_STORE.MAP_NAME, entry.getMap())
                .set(UPDATABLE_TEMPORAL_STORE.MAP_VALUE, entry.getValue())
                .execute());
        return entry;
    }

    @Override
    public TemporalEntry update(final String docUuid, final TemporalEntry entry) {
        return create(docUuid, entry);
    }

    @Override
    public Optional<TemporalEntry> fetch(final String docUuid, final TemporalEntryId id) {
        validateDocUuid(docUuid);
        validateTemporalEntryId(id);
        return JooqUtil.contextResult(sqlStoreDbConnProvider, context -> context
                .selectFrom(UPDATABLE_TEMPORAL_STORE)
                .where(UPDATABLE_TEMPORAL_STORE.DOC_UUID.eq(docUuid))
                .and(UPDATABLE_TEMPORAL_STORE.MAP_KEY.eq(id.getKey()))
                .and(UPDATABLE_TEMPORAL_STORE.EFFECTIVE_TIME.eq(id.getEffectiveTimeMs()))
                .fetchOptional()
                .map(record -> new TemporalEntry(
                        record.getMapName(),
                        record.getMapKey(),
                        record.getEffectiveTime(),
                        record.getMapValue())));
    }

    @Override
    public boolean delete(final String docUuid, final TemporalEntryId id) {
        validateDocUuid(docUuid);
        validateTemporalEntryId(id);
        return JooqUtil.contextResult(sqlStoreDbConnProvider, context -> context
                .deleteFrom(UPDATABLE_TEMPORAL_STORE)
                .where(UPDATABLE_TEMPORAL_STORE.DOC_UUID.eq(docUuid))
                .and(UPDATABLE_TEMPORAL_STORE.MAP_KEY.eq(id.getKey()))
                .and(UPDATABLE_TEMPORAL_STORE.EFFECTIVE_TIME.eq(id.getEffectiveTimeMs()))
                .execute() > 0);
    }

    @Override
    public ResultPage<TemporalEntry> find(final String docUuid, final ExpressionCriteria criteria) {
        validateDocUuid(docUuid);
        final Long queryTime = getQueryTime(criteria);
        if (queryTime != null) {
            final stroom.sqlstore.impl.db.jooq.tables.UpdatableTemporalStore t1 =
                    UPDATABLE_TEMPORAL_STORE.as("t1");
            final stroom.sqlstore.impl.db.jooq.tables.UpdatableTemporalStore t2 =
                    UPDATABLE_TEMPORAL_STORE.as("t2");

            final Condition condition = aliasedCondition(t2, criteria);

            final SelectHavingStep<Record3<String, String, Long>> subquery = DSL.select(
                            t2.DOC_UUID.as("sub_uuid"),
                            t2.MAP_KEY.as("sub_key"),
                            DSL.max(t2.EFFECTIVE_TIME).as("max_time"))
                    .from(t2)
                    .where(t2.DOC_UUID.eq(docUuid))
                    .and(condition)
                    .and(t2.EFFECTIVE_TIME.le(queryTime))
                    .groupBy(t2.DOC_UUID, t2.MAP_KEY);

            final Table<Record3<String, String, Long>> subTable = subquery.asTable("sub");
            final Field<String> subUuid = subTable.field("sub_uuid", String.class);
            final Field<String> subKey = subTable.field("sub_key", String.class);
            final Field<Long> maxTime = subTable.field("max_time", Long.class);

            final List<TemporalEntry> list = JooqUtil.contextResult(sqlStoreDbConnProvider, context -> context
                    .select(t1.MAP_NAME, t1.MAP_KEY, t1.EFFECTIVE_TIME, t1.MAP_VALUE)
                    .from(t1)
                    .innerJoin(subTable)
                    .on(t1.DOC_UUID.eq(subUuid))
                    .and(t1.MAP_KEY.eq(subKey))
                    .and(t1.EFFECTIVE_TIME.eq(maxTime))
                    .limit(JooqUtil.getLimit(criteria.getPageRequest(), true))
                    .offset(JooqUtil.getOffset(criteria.getPageRequest()))
                    .fetch()
                    .map(record -> new TemporalEntry(
                            record.get(t1.MAP_NAME),
                            record.get(t1.MAP_KEY),
                            record.get(t1.EFFECTIVE_TIME),
                            record.get(t1.MAP_VALUE))));
            return ResultPage.createPageLimitedList(list, criteria.getPageRequest());
        } else {
            final Condition condition = condition(criteria);
            final List<TemporalEntry> list = JooqUtil.contextResult(sqlStoreDbConnProvider, context -> context
                    .selectFrom(UPDATABLE_TEMPORAL_STORE)
                    .where(UPDATABLE_TEMPORAL_STORE.DOC_UUID.eq(docUuid))
                    .and(condition)
                    .limit(JooqUtil.getLimit(criteria.getPageRequest(), true))
                    .offset(JooqUtil.getOffset(criteria.getPageRequest()))
                    .fetch()
                    .map(record -> new TemporalEntry(
                            record.getMapName(),
                            record.getMapKey(),
                            record.getEffectiveTime(),
                            record.getMapValue())));
            return ResultPage.createPageLimitedList(list, criteria.getPageRequest());
        }
    }

    @Override
    public void clear(final String docUuid) {
        validateDocUuid(docUuid);
        JooqUtil.context(sqlStoreDbConnProvider, context -> context
                .deleteFrom(UPDATABLE_TEMPORAL_STORE)
                .where(UPDATABLE_TEMPORAL_STORE.DOC_UUID.eq(docUuid))
                .execute());
    }

    @Override
    public long count(final String docUuid) {
        validateDocUuid(docUuid);
        return JooqUtil.contextResult(sqlStoreDbConnProvider, context -> context
                .selectCount()
                .from(UPDATABLE_TEMPORAL_STORE)
                .where(UPDATABLE_TEMPORAL_STORE.DOC_UUID.eq(docUuid))
                .fetchOne(0, Long.class));
    }

    /**
     * Returns the absolute latest version of every key in the specified store
     * with no upper-time constraint.
     *
     * <p>Uses a SQL subquery that computes {@code MAX(effective_time)} per key
     * and joins back to the main table to fetch the full row — the same
     * approach as {@link #find(String, ExpressionCriteria)}.
     * All deduplication happens in the database; no
     * historical rows are transferred across the wire.
     * The result is ordered by key ascending.</p>
     *
     * @param docUuid UUID of the owning store document; must not be {@code null} or blank
     * @return deduplicated list — one entry per key — sorted by key ascending;
     *         never {@code null}, may be empty
     */
    @Override
    public List<TemporalEntry> fetchAll(final String docUuid) {
        validateDocUuid(docUuid);

        // Outer table alias
        final stroom.sqlstore.impl.db.jooq.tables.UpdatableTemporalStore t1 =
                UPDATABLE_TEMPORAL_STORE.as("t1");
        // Inner table alias used in the subquery
        final stroom.sqlstore.impl.db.jooq.tables.UpdatableTemporalStore t2 =
                UPDATABLE_TEMPORAL_STORE.as("t2");

        // Subquery: for each key, find the greatest effective_time in this store.
        final SelectHavingStep<Record3<String, String, Long>> subquery = DSL.select(
                        t2.DOC_UUID.as("sub_uuid"),
                        t2.MAP_KEY.as("sub_key"),
                        DSL.max(t2.EFFECTIVE_TIME).as("max_time"))
                .from(t2)
                .where(t2.DOC_UUID.eq(docUuid))
                .groupBy(t2.DOC_UUID, t2.MAP_KEY);

        final Table<Record3<String, String, Long>> subTable = subquery.asTable("sub");
        final Field<String> subUuid = subTable.field("sub_uuid", String.class);
        final Field<String> subKey  = subTable.field("sub_key",  String.class);
        final Field<Long>   maxTime = subTable.field("max_time", Long.class);

        // Join the outer table to the subquery to retrieve the full rows.
        return JooqUtil.contextResult(sqlStoreDbConnProvider, context -> context
                .select(t1.MAP_NAME, t1.MAP_KEY, t1.EFFECTIVE_TIME, t1.MAP_VALUE)
                .from(t1)
                .innerJoin(subTable)
                .on(t1.DOC_UUID.eq(subUuid))
                .and(t1.MAP_KEY.eq(subKey))
                .and(t1.EFFECTIVE_TIME.eq(maxTime))
                .orderBy(t1.MAP_KEY.asc())
                .fetch()
                .map(record -> new TemporalEntry(
                        record.get(t1.MAP_NAME),
                        record.get(t1.MAP_KEY),
                        record.get(t1.EFFECTIVE_TIME),
                        record.get(t1.MAP_VALUE))));
    }

    /**
     * Returns the minimum and maximum {@code effective_time} values in the store
     * for the given document via a single aggregation query.
     *
     * <p>Both values in the returned {@link TemporalStoreTimeRange} are
     * {@code null} if the store contains no entries.</p>
     *
     * @param docUuid UUID of the owning store document; must not be {@code null} or blank
     * @return time range DTO; never {@code null}
     */
    @Override
    public TemporalStoreTimeRange getTimeRange(final String docUuid) {
        validateDocUuid(docUuid);

        // Single aggregation query: SELECT MIN(effective_time), MAX(effective_time)
        // WHERE doc_uuid = ?  No row-level deduplication needed.
        final Record2<Long, Long> record = JooqUtil.contextResult(
                sqlStoreDbConnProvider, context -> context
                        .select(
                                DSL.min(UPDATABLE_TEMPORAL_STORE.EFFECTIVE_TIME),
                                DSL.max(UPDATABLE_TEMPORAL_STORE.EFFECTIVE_TIME))
                        .from(UPDATABLE_TEMPORAL_STORE)
                        .where(UPDATABLE_TEMPORAL_STORE.DOC_UUID.eq(docUuid))
                        .fetchOne());

        if (record == null) {
            return new TemporalStoreTimeRange(null, null);
        }
        return new TemporalStoreTimeRange(record.value1(), record.value2());
    }

    /**
     * Applies a list of upsert and delete operations atomically within a
     * single JOOQ-managed database transaction.
     *
     * <p>Each operation is applied in list order inside
     * {@link DSLContext#transaction(org.jooq.TransactionalRunnable)}.
     * If any step throws, JOOQ rolls back the entire transaction automatically
     * and the exception propagates to the caller.</p>
     *
     * <h3>UPSERT semantics</h3>
     * <p>The upsert uses the same {@code INSERT ... ON DUPLICATE KEY UPDATE}
     * SQL as {@link #create(String, TemporalEntry)}: if a row with the same
     * {@code (doc_uuid, key, effective_time)} already exists its {@code value}
     * column is overwritten; otherwise a new row is inserted.</p>
     *
     * <p>Every operation is scoped to {@code docUuid}; the map name carried by an
     * operation's entry is written as a label only and cannot redirect the write to
     * another document's data.</p>
     *
     * @param docUuid    UUID of the owning store document; must not be {@code null} or blank
     * @param operations ordered list of operations; must not be {@code null}
     */
    @Override
    public void applyChanges(final String docUuid, final List<ChangeOperation> operations) {
        validateDocUuid(docUuid);
        if (operations == null || operations.isEmpty()) {
            return;
        }
        JooqUtil.context(sqlStoreDbConnProvider, outerContext ->
                outerContext.transaction(config -> {
                    final DSLContext trx = DSL.using(config);
                    // Consecutive operations of the same type go to the database as one JDBC batch.
                    // Order still holds: runs execute in sequence and a batch preserves the order
                    // it was bound in, so a later operation on the same key still wins. Only a run
                    // is accumulated at a time rather than the whole list, so a batch spanning both
                    // types is never reordered into one.
                    final List<TemporalEntry> upserts = new ArrayList<>();
                    final List<TemporalEntryId> deletes = new ArrayList<>();

                    for (final ChangeOperation op : operations) {
                        if (op == null || op.getType() == null) {
                            throw new IllegalArgumentException(
                                    "ChangeOperation and its type must not be null.");
                        }
                        switch (op.getType()) {
                            case UPSERT -> {
                                final TemporalEntry entry = op.getEntry();
                                if (entry == null) {
                                    throw new IllegalArgumentException(
                                            "UPSERT operation must have a non-null entry.");
                                }
                                flushDeletes(trx, docUuid, deletes);
                                upserts.add(entry);
                            }
                            case DELETE -> {
                                final TemporalEntryId id = op.getId();
                                if (id == null) {
                                    throw new IllegalArgumentException(
                                            "DELETE operation must have a non-null id.");
                                }
                                flushUpserts(trx, docUuid, upserts);
                                deletes.add(id);
                            }
                        }
                    }
                    flushUpserts(trx, docUuid, upserts);
                    flushDeletes(trx, docUuid, deletes);
                }));
    }

    /**
     * Writes the accumulated upserts as one batch and clears the list.
     *
     * <p>Same {@code INSERT ... ON DUPLICATE KEY UPDATE} as {@link #create(String, TemporalEntry)},
     * bound once per row against a single prepared statement rather than executed per row.</p>
     */
    private void flushUpserts(final DSLContext trx,
                              final String docUuid,
                              final List<TemporalEntry> upserts) {
        if (upserts.isEmpty()) {
            return;
        }
        BatchBindStep batch = trx.batch(
                trx.insertInto(UPDATABLE_TEMPORAL_STORE)
                        .set(UPDATABLE_TEMPORAL_STORE.DOC_UUID, (String) null)
                        .set(UPDATABLE_TEMPORAL_STORE.MAP_NAME, (String) null)
                        .set(UPDATABLE_TEMPORAL_STORE.MAP_KEY, (String) null)
                        .set(UPDATABLE_TEMPORAL_STORE.EFFECTIVE_TIME, (Long) null)
                        .set(UPDATABLE_TEMPORAL_STORE.MAP_VALUE, (String) null)
                        .onDuplicateKeyUpdate()
                        .set(UPDATABLE_TEMPORAL_STORE.MAP_NAME, (String) null)
                        .set(UPDATABLE_TEMPORAL_STORE.MAP_VALUE, (String) null));
        for (final TemporalEntry entry : upserts) {
            batch = batch.bind(
                    docUuid,
                    entry.getMap(),
                    entry.getKey(),
                    entry.getEffectiveTimeMs(),
                    entry.getValue(),
                    // The ON DUPLICATE KEY UPDATE clause binds the same two values again.
                    entry.getMap(),
                    entry.getValue());
        }
        batch.execute();
        upserts.clear();
    }

    /** Writes the accumulated deletes as one batch and clears the list. */
    private void flushDeletes(final DSLContext trx,
                              final String docUuid,
                              final List<TemporalEntryId> deletes) {
        if (deletes.isEmpty()) {
            return;
        }
        BatchBindStep batch = trx.batch(
                trx.deleteFrom(UPDATABLE_TEMPORAL_STORE)
                        .where(UPDATABLE_TEMPORAL_STORE.DOC_UUID.eq((String) null))
                        .and(UPDATABLE_TEMPORAL_STORE.MAP_KEY.eq((String) null))
                        .and(UPDATABLE_TEMPORAL_STORE.EFFECTIVE_TIME.eq((Long) null)));
        for (final TemporalEntryId id : deletes) {
            batch = batch.bind(docUuid, id.getKey(), id.getEffectiveTimeMs());
        }
        batch.execute();
        deletes.clear();
    }

    /**
     * Executes a search query on the updatable temporal store and streams matching entries to the consumer.
     * The method distinguishes between two query paths based on the presence of a query/lookup time:
     *
     * <p><b>1. QueryTime Path (Temporal Lookup):</b>
     * If a temporal query time boundary is present (e.g., from a lookup request at a specific point in time),
     * the search retrieves the single most recent (effective) entry for each key that is valid at or
     * before the specified time.
     * <ul>
     *   <li>A subquery is constructed over the aliased table <code>t2</code> to find the maximum effective
     *       time (<code>max(effective_time)</code>) for each key in this document that is less than or
     *       equal to the query time (<code>t2.effective_time &lt;= queryTime</code>).</li>
     *   <li>The subquery is joined back onto the main table <code>t1</code> on matching document, keys and
     *       effective times to fetch the corresponding value.</li>
     *   <li>The returned {@link TemporalEntry} contains the actual <code>effective_time</code> matching the latest
     *       available point in time resolved by the lookup.</li>
     * </ul>
     *
     * <p><b>2. Standard Path (Bulk/Unbounded Search):</b>
     * If no query time is specified, a standard search is performed.
     * <ul>
     *   <li>It executes a direct SELECT query against the <code>UPDATABLE_TEMPORAL_STORE</code> table, filtering
     *       using the expression criteria.</li>
     *   <li>This returns all matching records across all historical time points.</li>
     * </ul>
     *
     * @param docUuid  UUID of the owning store document; must not be {@code null} or blank
     * @param criteria The criteria containing filters and optional query time.
     * @param consumer A consumer to receive the mapped {@link TemporalEntry} results.
     */
    @Override
    public void search(final String docUuid,
                       final ExpressionCriteria criteria,
                       final boolean includeValue,
                       final Consumer<TemporalEntry> consumer) {
        validateDocUuid(docUuid);
        final Long queryTime = getQueryTime(criteria);
        if (queryTime != null) {
            // A bound at or after queryTime is not a window but the zero-width TimeRange the
            // framework builds for "as at T" - `>= T AND < T` - which no row satisfies if read
            // literally. Mirrors the guard in TemporalStateDb.searchAsAt.
            final Long candidate = getNotBefore(criteria);
            final Long notBefore = candidate != null && candidate < queryTime
                    ? candidate
                    : null;
            final stroom.sqlstore.impl.db.jooq.tables.UpdatableTemporalStore t1 =
                    UPDATABLE_TEMPORAL_STORE.as("t1");
            final stroom.sqlstore.impl.db.jooq.tables.UpdatableTemporalStore t2 =
                    UPDATABLE_TEMPORAL_STORE.as("t2");

            final Condition condition = aliasedCondition(t2, criteria);
            // Narrowing the aggregate rather than filtering its result is the same answer and a
            // cheaper one: the latest row in [notBefore, queryTime] is the latest row at or before
            // queryTime whenever that row is in scope, and no row at all when it is not.
            final SelectHavingStep<Record3<String, String, Long>> subquery = DSL.select(
                            t2.DOC_UUID.as("sub_uuid"),
                            t2.MAP_KEY.as("sub_key"),
                            DSL.max(t2.EFFECTIVE_TIME).as("max_time"))
                    .from(t2)
                    .where(t2.DOC_UUID.eq(docUuid))
                    .and(condition)
                    .and(t2.EFFECTIVE_TIME.le(queryTime))
                    .and(notBefore == null
                            ? DSL.noCondition()
                            : t2.EFFECTIVE_TIME.ge(notBefore))
                    .groupBy(t2.DOC_UUID, t2.MAP_KEY);

            final Table<Record3<String, String, Long>> subTable = subquery.asTable("sub");
            final Field<String> subUuid = subTable.field("sub_uuid", String.class);
            final Field<String> subKey = subTable.field("sub_key", String.class);
            final Field<Long> maxTime = subTable.field("max_time", Long.class);

            //noinspection CodeBlock2Expr
            JooqUtil.context(sqlStoreDbConnProvider, context -> context
                    .select(t1.MAP_NAME, t1.MAP_KEY, t1.EFFECTIVE_TIME,
                            includeValue
                                    ? t1.MAP_VALUE
                                    : DSL.inline((String) null).as(t1.MAP_VALUE))
                    .from(t1)
                    .innerJoin(subTable)
                    .on(t1.DOC_UUID.eq(subUuid))
                    .and(t1.MAP_KEY.eq(subKey))
                    .and(t1.EFFECTIVE_TIME.eq(maxTime))
                    .fetch()
                    .forEach(record -> {
                        consumer.accept(new TemporalEntry(
                                record.get(t1.MAP_NAME),
                                record.get(t1.MAP_KEY),
                                record.get(t1.EFFECTIVE_TIME),
                                record.get(t1.MAP_VALUE)));
                    }));
        } else {
            final Condition condition = condition(criteria);

            //noinspection CodeBlock2Expr
            JooqUtil.context(sqlStoreDbConnProvider, context -> context
                    .select(UPDATABLE_TEMPORAL_STORE.MAP_NAME,
                            UPDATABLE_TEMPORAL_STORE.MAP_KEY,
                            UPDATABLE_TEMPORAL_STORE.EFFECTIVE_TIME,
                            includeValue
                                    ? UPDATABLE_TEMPORAL_STORE.MAP_VALUE
                                    : DSL.inline((String) null).as(UPDATABLE_TEMPORAL_STORE.MAP_VALUE))
                    .from(UPDATABLE_TEMPORAL_STORE)
                    .where(UPDATABLE_TEMPORAL_STORE.DOC_UUID.eq(docUuid))
                    .and(condition)
                    .fetch()
                    .forEach(record -> {
                        consumer.accept(new TemporalEntry(
                                record.get(UPDATABLE_TEMPORAL_STORE.MAP_NAME),
                                record.get(UPDATABLE_TEMPORAL_STORE.MAP_KEY),
                                record.get(UPDATABLE_TEMPORAL_STORE.EFFECTIVE_TIME),
                                record.get(UPDATABLE_TEMPORAL_STORE.MAP_VALUE)));
                    }));
        }
    }

    /**
     * Builds the SQL condition for the unaliased table from the caller's criteria, with
     * {@link UpdatableTemporalStore#MAP_FIELD} terms removed. Returns
     * {@link DSL#noCondition()} when nothing is left to filter on, so the caller can
     * always {@code AND} the result onto its {@code doc_uuid} predicate.
     */
    private Condition condition(final ExpressionCriteria criteria) {
        final ExpressionOperator expression = stripMapTerms(criteria);
        return expression == null
                ? DSL.noCondition()
                : expressionMapper.apply(expression);
    }

    /**
     * As {@link #condition(ExpressionCriteria)} but against an aliased table, and additionally
     * stripping the time term, which the caller applies itself as the temporal upper bound.
     */
    private Condition aliasedCondition(final stroom.sqlstore.impl.db.jooq.tables.UpdatableTemporalStore t,
                                       final ExpressionCriteria criteria) {
        final ExpressionMapper localExpressionMapper = expressionMapperFactory.create();
        localExpressionMapper.map(UpdatableTemporalStore.KEY_FIELD,
                t.MAP_KEY,
                String::valueOf);
        localExpressionMapper.map(UpdatableTemporalStore.TIME_FIELD,
                t.EFFECTIVE_TIME,
                DateUtil::parseUnknownString);
        localExpressionMapper.map(UpdatableTemporalStore.VALUE_FIELD,
                t.MAP_VALUE,
                String::valueOf);

        final ExpressionOperator expression = getFilteredExpression(criteria);
        return expression == null
                ? DSL.noCondition()
                : localExpressionMapper.apply(expression);
    }

    /**
     * The "not before" instant, where the caller has bounded the snapshot below as well as above.
     *
     * <p>Narrows "the state of each key at T" to "the state of each key at T, considering only what
     * happened since". Without it the bound is not ignored but stripped by
     * {@link #getFilteredExpression}, so a caller asking for {@code >= X AND <= Y} could be handed a
     * row from before X — the defect {@code docs/temporal-store-parity-report.md} records.</p>
     *
     * <p>Mirrors {@code PlanBSearchHelper.getNotBefore} so the two stores stay interchangeable:
     * {@code >} is treated as {@code >=}, the first parseable term wins, and its position in the
     * expression tree is not considered.</p>
     */
    private Long getNotBefore(final ExpressionCriteria criteria) {
        if (criteria == null || criteria.getExpression() == null) {
            return null;
        }
        final List<ExpressionTerm> timeTerms = ExpressionUtil.terms(
                criteria.getExpression(),
                List.of(UpdatableTemporalStore.TIME_FIELD.getFldName()));

        for (final ExpressionTerm term : timeTerms) {
            if (UpdatableTemporalStore.TIME_FIELD.getFldName().equals(term.getField()) &&
                    (term.getCondition() == ExpressionTerm.Condition.GREATER_THAN ||
                    term.getCondition() == ExpressionTerm.Condition.GREATER_THAN_OR_EQUAL_TO)) {
                try {
                    return DateUtil.parseUnknownString(term.getValue());
                } catch (final RuntimeException e) {
                    // Ignore and keep checking
                }
            }
        }
        return null;
    }

    private Long getQueryTime(final ExpressionCriteria criteria) {
        if (criteria == null || criteria.getExpression() == null) {
            return null;
        }
        final List<ExpressionTerm> timeTerms = ExpressionUtil.terms(
                criteria.getExpression(),
                List.of(UpdatableTemporalStore.TIME_FIELD.getFldName()));

        for (final ExpressionTerm term : timeTerms) {
            if (UpdatableTemporalStore.TIME_FIELD.getFldName().equals(term.getField()) &&
                    (term.getCondition() == ExpressionTerm.Condition.EQUALS ||
                    term.getCondition() == ExpressionTerm.Condition.LESS_THAN_OR_EQUAL_TO ||
                    term.getCondition() == ExpressionTerm.Condition.LESS_THAN)) {
                try {
                    return DateUtil.parseUnknownString(term.getValue());
                } catch (final RuntimeException e) {
                    // Ignore and keep checking
                }
            }
        }
        return null;
    }

    /**
     * Copies the criteria expression with both the time term and the map term removed - the
     * time bound is applied separately by the caller, and the map name is not a scoping field.
     */
    private ExpressionOperator getFilteredExpression(final ExpressionCriteria criteria) {
        if (criteria == null || criteria.getExpression() == null) {
            return null;
        }
        return ExpressionUtil.copyOperator(
                criteria.getExpression(),
                item -> {
                    if (item instanceof final ExpressionTerm term) {
                        return term.getField() != null
                               && !UpdatableTemporalStore.TIME_FIELD.getFldName().equals(term.getField())
                               && !UpdatableTemporalStore.MAP_FIELD.getFldName().equals(term.getField());
                    }
                    return true;
                });
    }

    /**
     * Copies the criteria expression with the map term removed. The map name is a denormalised
     * label, not a scoping field, so honouring it would make a renamed document look empty.
     */
    private ExpressionOperator stripMapTerms(final ExpressionCriteria criteria) {
        if (criteria == null || criteria.getExpression() == null) {
            return null;
        }
        return ExpressionUtil.copyOperator(
                criteria.getExpression(),
                item -> {
                    if (item instanceof final ExpressionTerm term) {
                        return term.getField() != null
                               && !UpdatableTemporalStore.MAP_FIELD.getFldName().equals(term.getField());
                    }
                    return true;
                });
    }

    private void validateDocUuid(final String docUuid) {
        if (docUuid == null || docUuid.isBlank()) {
            throw new IllegalArgumentException("Store document UUID must be defined and not empty.");
        }
    }

    private void validateTemporalEntry(final TemporalEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Entry cannot be null.");
        }
        if (entry.getMap() == null || entry.getMap().isBlank()) {
            throw new IllegalArgumentException("Map name must be defined and not empty.");
        }
        if (entry.getKey() == null || entry.getKey().isBlank()) {
            throw new IllegalArgumentException("Key must be defined and not empty.");
        }
        if (entry.getEffectiveTimeMs() == null) {
            throw new IllegalArgumentException("Effective time must be defined.");
        }
    }

    private void validateTemporalEntryId(final TemporalEntryId id) {
        if (id == null) {
            throw new IllegalArgumentException("Id cannot be null.");
        }
        if (id.getKey() == null || id.getKey().isBlank()) {
            throw new IllegalArgumentException("Key must be defined and not empty.");
        }
        if (id.getEffectiveTimeMs() == null) {
            throw new IllegalArgumentException("Effective time must be defined.");
        }
    }
}
