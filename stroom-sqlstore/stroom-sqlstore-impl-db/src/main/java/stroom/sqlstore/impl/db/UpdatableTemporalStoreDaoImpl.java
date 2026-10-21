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
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionUtil;
import stroom.sqlstore.api.TemporalEntryValidator;
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
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.Record3;
import org.jooq.Record4;
import org.jooq.SelectHavingStep;
import org.jooq.Table;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    /**
     * The most rows an unpaged read ({@link #fetchAll}, or {@link #find} without a page request)
     * will return. Exceeding it is an error rather than a silent truncation, because every caller
     * treats the list as the whole store - a Fact List missing entries would look complete. A caller
     * that genuinely needs more must page.
     */
    static final int MAX_UNPAGED_ROWS = 100_000;

    /// The longest page [#find] will return. A longer page request is refused rather than
    /// shortened, because the caller would take a short page to mean it had reached the end.
    static final int MAX_PAGE_LENGTH = MAX_UNPAGED_ROWS;

    /// The fetch size that tells MySQL Connector/J to stream a result set a row at a time instead of
    /// reading all of it into memory before returning the first row.
    private static final int MYSQL_STREAMING_FETCH_SIZE = Integer.MIN_VALUE;

    /** Rows read from the cursor at a time when streaming {@link #search} results. */
    private static final int SEARCH_FETCH_BATCH_SIZE = 1_000;

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

    /**
     * Finds entries matching the criteria, a page at a time.
     *
     * <p>If the expression has a time term usable as a snapshot instant (see
     * {@link #findSnapshotTerm}) the result is the latest version of each key at that instant, with
     * {@code <} honoured as strictly before. Every other term - including further time terms,
     * whether lower bounds or inside {@code OR}/{@code NOT} - filters rows as written. Without a
     * snapshot term, every matching version is returned.</p>
     *
     * <p>Results are ordered by key then effective time so that paging is stable. Without a page
     * request at most {@link #MAX_UNPAGED_ROWS} rows are returned, and more than that is an error; a
     * page request may ask for at most {@link #MAX_PAGE_LENGTH}.</p>
     */
    @Override
    public ResultPage<TemporalEntry> find(final String docUuid, final ExpressionCriteria criteria) {
        validateDocUuid(docUuid);
        final ExpressionTerm snapshotTerm = findSnapshotTerm(criteria);
        final boolean paged = criteria.getPageRequest() != null && criteria.getPageRequest().getLength() != null;
        if (paged && criteria.getPageRequest().getLength() > MAX_PAGE_LENGTH) {
            throw new IllegalArgumentException("A page of at most " + MAX_PAGE_LENGTH
                                               + " entries may be requested, not "
                                               + criteria.getPageRequest().getLength() + ".");
        }
        // One more than wanted either way: for a page, so the result can say whether more follow; for
        // an unpaged read, so that exceeding the cap is detected rather than silently truncated.
        final int limit = paged
                ? JooqUtil.getLimit(criteria.getPageRequest(), true)
                : MAX_UNPAGED_ROWS + 1;
        final int offset = JooqUtil.getOffset(criteria.getPageRequest());

        final List<TemporalEntry> list;
        if (snapshotTerm != null) {
            final stroom.sqlstore.impl.db.jooq.tables.UpdatableTemporalStore t1 =
                    UPDATABLE_TEMPORAL_STORE.as("t1");
            final Table<Record3<String, String, Long>> subTable = snapshotSubquery(
                    docUuid, criteria, snapshotTerm, true, false);
            list = JooqUtil.contextResult(sqlStoreDbConnProvider, context ->
                    selectSnapshotRows(context, t1, subTable, true)
                            .orderBy(t1.MAP_KEY, t1.EFFECTIVE_TIME)
                            .limit(limit)
                            .offset(offset)
                            .fetch()
                            .map(record -> toEntry(record, t1)));
        } else {
            final Condition condition = condition(criteria);
            list = JooqUtil.contextResult(sqlStoreDbConnProvider, context -> context
                    .selectFrom(UPDATABLE_TEMPORAL_STORE)
                    .where(UPDATABLE_TEMPORAL_STORE.DOC_UUID.eq(docUuid))
                    .and(condition)
                    .orderBy(UPDATABLE_TEMPORAL_STORE.MAP_KEY, UPDATABLE_TEMPORAL_STORE.EFFECTIVE_TIME)
                    .limit(limit)
                    .offset(offset)
                    .fetch()
                    .map(record -> new TemporalEntry(
                            record.getMapName(),
                            record.getMapKey(),
                            record.getEffectiveTime(),
                            record.getMapValue())));
        }
        if (!paged) {
            checkUnpagedSize(list.size());
        }
        // Not createPageLimitedList: that takes the full list and applies the offset itself, and the
        // offset has already been applied by the query, so every page after the first came back wrong.
        return ResultPage.createCriterialBasedList(list, criteria);
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
        final List<TemporalEntry> list = JooqUtil.contextResult(sqlStoreDbConnProvider, context -> context
                .select(t1.MAP_NAME, t1.MAP_KEY, t1.EFFECTIVE_TIME, t1.MAP_VALUE)
                .from(t1)
                .innerJoin(subTable)
                .on(t1.DOC_UUID.eq(subUuid))
                .and(t1.MAP_KEY.eq(subKey))
                .and(t1.EFFECTIVE_TIME.eq(maxTime))
                .orderBy(t1.MAP_KEY.asc())
                .limit(MAX_UNPAGED_ROWS + 1)
                .fetch()
                .map(record -> new TemporalEntry(
                        record.get(t1.MAP_NAME),
                        record.get(t1.MAP_KEY),
                        record.get(t1.EFFECTIVE_TIME),
                        record.get(t1.MAP_VALUE))));
        checkUnpagedSize(list.size());
        return list;
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
                                validateTemporalEntry(entry);
                                flushDeletes(trx, docUuid, deletes);
                                upserts.add(entry);
                            }
                            case DELETE -> {
                                final TemporalEntryId id = op.getId();
                                validateTemporalEntryId(id);
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
     * The method distinguishes between two query paths based on the presence of a snapshot time term:
     *
     * <p><b>1. Snapshot path (temporal lookup):</b>
     * If the expression has a time term usable as a snapshot instant (see {@link #findSnapshotTerm}),
     * the search retrieves the single most recent entry for each key that is valid at or before that
     * instant. Unlike {@link #find}, a {@code <} term is read as at-or-before: the framework turns a
     * query's time range into {@code >= from AND < to}, and "the state as at {@code to}" must include
     * an entry effective at exactly {@code to}. For the same reason a lower bound at or after the
     * instant is the zero-width range the framework builds for "as at T", not a window, and is
     * ignored. Every other term, including an earlier lower bound, filters the candidate rows.</p>
     *
     * <p><b>2. Standard path (bulk/unbounded search):</b>
     * Without a snapshot term every matching row across all historical time points is returned.</p>
     *
     * <p>Rows are streamed from the database a row at a time rather than read into memory first, so
     * memory use does not grow with the size of the store, and the consumer may stop the read
     * part-way - for example on search termination - by throwing. While streaming, the connection can
     * run nothing else, so the consumer must not use the database.</p>
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
        final ExpressionTerm snapshotTerm = findSnapshotTerm(criteria);
        if (snapshotTerm != null) {
            final stroom.sqlstore.impl.db.jooq.tables.UpdatableTemporalStore t1 =
                    UPDATABLE_TEMPORAL_STORE.as("t1");
            final Table<Record3<String, String, Long>> subTable = snapshotSubquery(
                    docUuid, criteria, snapshotTerm, false, true);

            JooqUtil.context(sqlStoreDbConnProvider, context -> {
                try (final Cursor<Record4<String, String, Long, String>> cursor =
                        selectSnapshotRows(context, t1, subTable, includeValue)
                                .fetchSize(MYSQL_STREAMING_FETCH_SIZE)
                                .fetchLazy()) {
                    streamTo(cursor, record -> toEntry(record, t1), consumer);
                }
            });
        } else {
            final Condition condition = condition(criteria);

            JooqUtil.context(sqlStoreDbConnProvider, context -> {
                try (final Cursor<Record4<String, String, Long, String>> cursor = context
                        .select(UPDATABLE_TEMPORAL_STORE.MAP_NAME,
                                UPDATABLE_TEMPORAL_STORE.MAP_KEY,
                                UPDATABLE_TEMPORAL_STORE.EFFECTIVE_TIME,
                                includeValue
                                        ? UPDATABLE_TEMPORAL_STORE.MAP_VALUE
                                        : DSL.inline((String) null).as(UPDATABLE_TEMPORAL_STORE.MAP_VALUE))
                        .from(UPDATABLE_TEMPORAL_STORE)
                        .where(UPDATABLE_TEMPORAL_STORE.DOC_UUID.eq(docUuid))
                        .and(condition)
                        .fetchSize(MYSQL_STREAMING_FETCH_SIZE)
                        .fetchLazy()) {
                    streamTo(cursor, record -> toEntry(record, UPDATABLE_TEMPORAL_STORE), consumer);
                }
            });
        }
    }

    /**
     * Builds the grouped subquery that finds, for each key in the document, the greatest effective
     * time that satisfies the criteria and the snapshot bound.
     *
     * @param exactBounds          {@code true} to honour {@code <} as strictly before; {@code false}
     *                             to read it as at-or-before, as {@link #search} does
     * @param ignoreZeroWidthRange {@code true} to drop lower-bound conjuncts at or after the instant,
     *                             as {@link #search} does
     */
    private Table<Record3<String, String, Long>> snapshotSubquery(final String docUuid,
                                                                  final ExpressionCriteria criteria,
                                                                  final ExpressionTerm snapshotTerm,
                                                                  final boolean exactBounds,
                                                                  final boolean ignoreZeroWidthRange) {
        final stroom.sqlstore.impl.db.jooq.tables.UpdatableTemporalStore t2 =
                UPDATABLE_TEMPORAL_STORE.as("t2");
        final long instant = DateUtil.parseUnknownString(snapshotTerm.getValue());

        final Set<ExpressionTerm> consumed = Collections.newSetFromMap(new IdentityHashMap<>());
        consumed.add(snapshotTerm);
        if (ignoreZeroWidthRange) {
            consumed.addAll(zeroWidthLowerBounds(criteria, instant));
        }
        final Condition upperBound = exactBounds && snapshotTerm.getCondition() == ExpressionTerm.Condition.LESS_THAN
                ? t2.EFFECTIVE_TIME.lt(instant)
                : t2.EFFECTIVE_TIME.le(instant);

        return DSL.select(
                        t2.DOC_UUID.as("sub_uuid"),
                        t2.MAP_KEY.as("sub_key"),
                        DSL.max(t2.EFFECTIVE_TIME).as("max_time"))
                .from(t2)
                .where(t2.DOC_UUID.eq(docUuid))
                .and(aliasedCondition(t2, criteria, consumed))
                .and(upperBound)
                .groupBy(t2.DOC_UUID, t2.MAP_KEY)
                .asTable("sub");
    }

    /** Joins the snapshot subquery back to the table for the full row of each key's latest version. */
    private static org.jooq.SelectOnConditionStep<Record4<String, String, Long, String>> selectSnapshotRows(
            final DSLContext context,
            final stroom.sqlstore.impl.db.jooq.tables.UpdatableTemporalStore t1,
            final Table<Record3<String, String, Long>> subTable,
            final boolean includeValue) {
        return context
                .select(t1.MAP_NAME, t1.MAP_KEY, t1.EFFECTIVE_TIME,
                        includeValue
                                ? t1.MAP_VALUE
                                : DSL.inline((String) null).as(t1.MAP_VALUE))
                .from(t1)
                .innerJoin(subTable)
                .on(t1.DOC_UUID.eq(subTable.field("sub_uuid", String.class)))
                .and(t1.MAP_KEY.eq(subTable.field("sub_key", String.class)))
                .and(t1.EFFECTIVE_TIME.eq(subTable.field("max_time", Long.class)));
    }

    private static TemporalEntry toEntry(final org.jooq.Record record,
                                         final stroom.sqlstore.impl.db.jooq.tables.UpdatableTemporalStore t) {
        return new TemporalEntry(
                record.get(t.MAP_NAME),
                record.get(t.MAP_KEY),
                record.get(t.EFFECTIVE_TIME),
                record.get(t.MAP_VALUE));
    }

    /** Reads the cursor a batch at a time, passing each row to the consumer. */
    private static <R extends org.jooq.Record> void streamTo(final Cursor<R> cursor,
                                                             final java.util.function.Function<R, TemporalEntry> mapper,
                                                             final Consumer<TemporalEntry> consumer) {
        while (cursor.hasNext()) {
            for (final R record : cursor.fetchNext(SEARCH_FETCH_BATCH_SIZE)) {
                consumer.accept(mapper.apply(record));
            }
        }
    }

    private static void checkUnpagedSize(final int rowCount) {
        if (rowCount > MAX_UNPAGED_ROWS) {
            throw new IllegalStateException("The request matches more than " + MAX_UNPAGED_ROWS
                                            + " entries. Narrow it, or request it a page at a time.");
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
     * leaving out the given terms, which the caller has already accounted for - the snapshot term it
     * applies itself as the temporal upper bound, and any zero-width lower bound. Terms are matched
     * by identity, so an identical term written elsewhere in the expression is kept.
     */
    private Condition aliasedCondition(final stroom.sqlstore.impl.db.jooq.tables.UpdatableTemporalStore t,
                                       final ExpressionCriteria criteria,
                                       final Set<ExpressionTerm> consumedTerms) {
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

        if (criteria == null || criteria.getExpression() == null) {
            return DSL.noCondition();
        }
        final ExpressionOperator expression = ExpressionUtil.copyOperator(
                criteria.getExpression(),
                item -> {
                    if (item instanceof final ExpressionTerm term) {
                        return term.getField() != null
                               && !consumedTerms.contains(term)
                               && !UpdatableTemporalStore.MAP_FIELD.getFldName().equals(term.getField());
                    }
                    return true;
                });
        return expression == null
                ? DSL.noCondition()
                : localExpressionMapper.apply(expression);
    }

    /**
     * The time term that makes this a snapshot read, or {@code null} for an ordinary one.
     *
     * <p>A snapshot term is an {@code EffectiveTime} term with {@code =}, {@code <=} or {@code <} and
     * a parseable value, that is a conjunct of the whole expression: reachable from the root through
     * enabled {@code AND} operators only. A time term inside {@code OR} or {@code NOT} does not bound
     * the whole result, so treating it as the snapshot instant would change what the query means; it
     * is left in place to filter rows like any other term. Where there are several candidates the
     * first in document order wins, and the rest also stay in place as filters.</p>
     */
    static ExpressionTerm findSnapshotTerm(final ExpressionCriteria criteria) {
        for (final ExpressionTerm term : conjunctTimeTerms(criteria)) {
            final ExpressionTerm.Condition condition = term.getCondition();
            if (condition == ExpressionTerm.Condition.EQUALS
                || condition == ExpressionTerm.Condition.LESS_THAN_OR_EQUAL_TO
                || condition == ExpressionTerm.Condition.LESS_THAN) {
                if (parseTime(term) != null) {
                    return term;
                }
            }
        }
        return null;
    }

    /**
     * Lower-bound time conjuncts at or after the snapshot instant. Together with the upper bound
     * these form the zero-width {@code >= T AND < T} range the query framework builds for
     * "as at T", which {@link #search} must not read literally.
     */
    private static List<ExpressionTerm> zeroWidthLowerBounds(final ExpressionCriteria criteria,
                                                             final long instant) {
        return conjunctTimeTerms(criteria).stream()
                .filter(term -> term.getCondition() == ExpressionTerm.Condition.GREATER_THAN_OR_EQUAL_TO
                                || term.getCondition() == ExpressionTerm.Condition.GREATER_THAN)
                .filter(term -> {
                    final Long time = parseTime(term);
                    return time != null && time >= instant;
                })
                .toList();
    }

    /** The enabled {@code EffectiveTime} terms reachable from the root through enabled ANDs only. */
    private static List<ExpressionTerm> conjunctTimeTerms(final ExpressionCriteria criteria) {
        final List<ExpressionTerm> terms = new ArrayList<>();
        if (criteria != null) {
            addConjunctTimeTerms(criteria.getExpression(), terms);
        }
        return terms;
    }

    private static void addConjunctTimeTerms(final ExpressionOperator operator,
                                             final List<ExpressionTerm> terms) {
        if (operator == null
            || !operator.enabled()
            || operator.op() != ExpressionOperator.Op.AND
            || !operator.hasChildren()) {
            return;
        }
        for (final ExpressionItem item : operator.getChildren()) {
            if (item.enabled()) {
                if (item instanceof final ExpressionTerm term) {
                    if (UpdatableTemporalStore.TIME_FIELD.getFldName().equals(term.getField())) {
                        terms.add(term);
                    }
                } else if (item instanceof final ExpressionOperator childOperator) {
                    addConjunctTimeTerms(childOperator, terms);
                }
            }
        }
    }

    private static Long parseTime(final ExpressionTerm term) {
        if (term.getValue() == null || term.getValue().isBlank()) {
            return null;
        }
        try {
            return DateUtil.parseUnknownString(term.getValue());
        } catch (final RuntimeException e) {
            return null;
        }
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

    // The rules are shared with every other write path - see TemporalEntryValidator.
    private void validateTemporalEntry(final TemporalEntry entry) {
        throwIfInvalid(TemporalEntryValidator.entryProblem(entry));
    }

    private void validateTemporalEntryId(final TemporalEntryId id) {
        throwIfInvalid(TemporalEntryValidator.idProblem(id));
    }

    private static void throwIfInvalid(final String problem) {
        if (problem != null) {
            throw new IllegalArgumentException("Invalid temporal entry: " + problem);
        }
    }
}
