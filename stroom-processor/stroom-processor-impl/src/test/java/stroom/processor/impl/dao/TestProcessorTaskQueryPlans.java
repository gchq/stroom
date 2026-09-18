/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.processor.impl.dao;

import stroom.db.util.JooqUtil;
import stroom.processor.impl.db.jooq.tables.records.ProcessorFilterRecord;
import stroom.processor.impl.db.jooq.tables.records.ProcessorFilterTrackerRecord;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.TaskStatus;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.jooq.DSLContext;
import org.jooq.InsertValuesStepN;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.conf.ParamType;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilter.PROCESSOR_FILTER;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilterTracker.PROCESSOR_FILTER_TRACKER;
import static stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK;

/**
 * gh-5699 Phase 1/0 gate: proves (or falsifies) the query plans that the decentralised
 * task-claiming design depends on, against a realistically populated {@code processor_task}
 * table — a large retained COMPLETE backlog, a CREATED backlog spread over many filters
 * (the degenerate no-profile case where every filter is eligible on every node), plus
 * PROCESSING and stale QUEUED rows.
 * <p>
 * The gates, from PROCESSOR_WORKER_TASK_QUEUEING_DESIGN.md:
 * <ul>
 *     <li>§3.2 availability summary must be one index descent per filter on the existing
 *     {@code processor_task_filter_id_status_id_meta_id} index ("Using index for group-by",
 *     i.e. a loose index scan) with no materialise-and-sort. This is the design's
 *     falsifiable condition; if it fails the fallback is Option B or abandonment.</li>
 *     <li>§3.4 reap must use {@code processor_task_status_create_time_ms_idx} so its cost is
 *     bounded by the PROCESSING row count, not by the retained terminal backlog. Same for
 *     the switchover sweep of stale QUEUED/ASSIGNED rows.</li>
 *     <li>§3.3 claim must read in index order (no filesort) so FIFO comes free.</li>
 * </ul>
 */
class TestProcessorTaskQueryPlans extends AbstractProcessorTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestProcessorTaskQueryPlans.class);

    private static final String SUMMARY_INDEX = "processor_task_filter_id_status_id_meta_id";
    private static final String REAP_INDEX = "processor_task_status_create_time_ms_idx";

    private static final int FILTER_COUNT = Integer.getInteger("testFilterCount", 2_000);
    private static final int TERMINAL_PER_FILTER = Integer.getInteger("testTerminalPerFilter", 150);
    private static final int CREATED_PER_FILTER = Integer.getInteger("testCreatedPerFilter", 30);
    private static final int QUEUED_ROWS = Integer.getInteger("testQueuedRows", 500);

    /** Well clear of the auto-increment ids handed to the template rows. */
    private static final int CLONE_ID_BASE = 100_000;
    private static final int INSERT_CHUNK_SIZE = 1_000;

    @Test
    void explainOptionAQueryPlans() {
        final Instant now = Instant.now();
        final List<Integer> filterIds = seed(now);

        JooqUtil.context(processorDbConnProvider, context -> {
            // Fresh statistics so the optimiser sees the real population.
            context.fetch("ANALYZE TABLE processor_task");

            // --- §3.2 availability summary, no-profile case: every filter in the IN list ---
            final String summarySql = context
                    .select(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID, DSL.min(PROCESSOR_TASK.ID))
                    .from(PROCESSOR_TASK)
                    .where(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID.in(filterIds))
                    .and(PROCESSOR_TASK.STATUS.eq(TaskStatus.CREATED.getPrimitiveValue()))
                    .groupBy(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID)
                    .getSQL(ParamType.INLINED);

            final PlanLine summaryPlan = explain(context, "availability summary", summarySql);
            assertThat(summaryPlan.key())
                    .describedAs("summary must use the existing composite index")
                    .isEqualTo(SUMMARY_INDEX);
            assertThat(summaryPlan.extra())
                    .describedAs("summary must be a loose index scan — one descent per filter, "
                                 + "not a scan of the CREATED backlog")
                    .contains("Using index for group-by");
            assertThat(summaryPlan.extra())
                    .describedAs("summary must not materialise and sort (§3.2 gate)")
                    .doesNotContain("Using temporary")
                    .doesNotContain("Using filesort");

            // Timing: warm once, then measure. Informational, not a gate — the gate is the plan.
            context.fetch(summarySql);
            for (int i = 0; i < 5; i++) {
                final Instant start = Instant.now();
                final int groups = context.fetch(summarySql).size();
                LOGGER.info("Summary run {}: {} filters with CREATED work in {}",
                        i, groups, Duration.between(start, Instant.now()));
                assertThat(groups).isEqualTo(FILTER_COUNT);
            }

            // --- §3.4 reap: must be bounded by PROCESSING count, not table size ---
            final String reapSql = context
                    .update(PROCESSOR_TASK)
                    .set(PROCESSOR_TASK.STATUS, TaskStatus.CREATED.getPrimitiveValue())
                    .setNull(PROCESSOR_TASK.FK_PROCESSOR_NODE_ID)
                    .where(PROCESSOR_TASK.STATUS.eq(TaskStatus.PROCESSING.getPrimitiveValue()))
                    .and(PROCESSOR_TASK.STATUS_TIME_MS.lt(now.minus(Duration.ofMinutes(10)).toEpochMilli()))
                    .getSQL(ParamType.INLINED);

            final PlanLine reapPlan = explain(context, "reap", reapSql);
            assertThat(reapPlan.key())
                    .describedAs("reap must use (status, create_time_ms), not the "
                                 + "status_time_ms-leading index that scans the retention backlog (§3.4)")
                    .isEqualTo(REAP_INDEX);

            // --- §3.4/§6 item 19 switchover sweep of stale QUEUED/ASSIGNED ---
            final String sweepSql = context
                    .update(PROCESSOR_TASK)
                    .set(PROCESSOR_TASK.STATUS, TaskStatus.CREATED.getPrimitiveValue())
                    .setNull(PROCESSOR_TASK.FK_PROCESSOR_NODE_ID)
                    .where(PROCESSOR_TASK.STATUS.in(
                            TaskStatus.QUEUED.getPrimitiveValue(),
                            TaskStatus.ASSIGNED.getPrimitiveValue()))
                    .and(PROCESSOR_TASK.STATUS_TIME_MS.lt(now.minus(Duration.ofMinutes(10)).toEpochMilli()))
                    .getSQL(ParamType.INLINED);

            final PlanLine sweepPlan = explain(context, "queued/assigned sweep", sweepSql);
            assertThat(sweepPlan.key())
                    .describedAs("sweep must range over (status, create_time_ms) like the reap")
                    .isEqualTo(REAP_INDEX);

            // --- §3.3 claim: FIFO must come from index order, not a sort ---
            final String claimSql = context
                    .select(PROCESSOR_TASK.ID, PROCESSOR_TASK.META_ID)
                    .from(PROCESSOR_TASK)
                    .where(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID.eq(filterIds.get(0)))
                    .and(PROCESSOR_TASK.STATUS.eq(TaskStatus.CREATED.getPrimitiveValue()))
                    .orderBy(PROCESSOR_TASK.ID)
                    .limit(30)
                    .forUpdate()
                    .skipLocked()
                    .getSQL(ParamType.INLINED);

            final PlanLine claimPlan = explain(context, "claim", claimSql);
            assertThat(claimPlan.key())
                    .describedAs("claim must walk the composite index")
                    .isEqualTo(SUMMARY_INDEX);
            assertThat(claimPlan.extra())
                    .describedAs("claim must read in index order — a filesort breaks cheap FIFO (§3.3)")
                    .doesNotContain("Using filesort");
        });
    }

    /**
     * Seeds one processor, {@link #FILTER_COUNT} filters (template cloned with explicit ids so
     * every NOT NULL column tracks the live schema), and the task populations. Terminal rows are
     * inserted first so CREATED rows sit at high ids, as in production.
     */
    private List<Integer> seed(final Instant now) {
        final Processor processor = createProcessor();
        final ProcessorFilter templateFilter = createProcessorFilter(processor);
        final int node1 = processorNodeCache.getOrCreate(NODE1);
        final int node2 = processorNodeCache.getOrCreate(NODE2);
        final int feed = processorFeedCache.getOrCreate(FEED);

        final List<Integer> filterIds = new ArrayList<>(FILTER_COUNT);
        filterIds.add(templateFilter.getId());

        JooqUtil.context(processorDbConnProvider, context -> {
            final ProcessorFilterRecord filterTemplate = context.fetchOne(
                    PROCESSOR_FILTER, PROCESSOR_FILTER.ID.eq(templateFilter.getId()));
            final ProcessorFilterTrackerRecord trackerTemplate = context.fetchOne(
                    PROCESSOR_FILTER_TRACKER,
                    PROCESSOR_FILTER_TRACKER.ID.eq(filterTemplate.getFkProcessorFilterTrackerId()));

            final List<ProcessorFilterTrackerRecord> trackers = new ArrayList<>();
            final List<ProcessorFilterRecord> filters = new ArrayList<>();
            for (int i = 0; i < FILTER_COUNT - 1; i++) {
                final int cloneId = CLONE_ID_BASE + i;
                final ProcessorFilterTrackerRecord tracker = trackerTemplate.copy();
                tracker.setId(cloneId);
                trackers.add(tracker);

                final ProcessorFilterRecord filter = filterTemplate.copy();
                filter.setId(cloneId);
                filter.setUuid(UUID.randomUUID().toString());
                filter.setFkProcessorFilterTrackerId(cloneId);
                filters.add(filter);
                filterIds.add(cloneId);
            }
            context.batchInsert(trackers).execute();
            context.batchInsert(filters).execute();

            final Instant seedStart = Instant.now();
            long metaId = 1;

            // Retained terminal backlog — the population that makes the wrong reap index bad.
            final long terminalTime = now.minus(Duration.ofDays(2)).toEpochMilli();
            metaId = insertTasks(context, filterIds, TERMINAL_PER_FILTER,
                    TaskStatus.COMPLETE, node1, feed, terminalTime, metaId);

            // CREATED backlog: unowned, recent.
            final long createdTime = now.minus(Duration.ofMinutes(5)).toEpochMilli();
            metaId = insertTasks(context, filterIds, CREATED_PER_FILTER,
                    TaskStatus.CREATED, null, feed, createdTime, metaId);

            // In-flight work with fresh heartbeats, split across two nodes.
            metaId = insertTasks(context, filterIds.subList(0, FILTER_COUNT / 2), 1,
                    TaskStatus.PROCESSING, node1, feed, now.toEpochMilli(), metaId);
            metaId = insertTasks(context, filterIds.subList(FILTER_COUNT / 2, FILTER_COUNT), 1,
                    TaskStatus.PROCESSING, node2, feed, now.toEpochMilli(), metaId);

            // Stale QUEUED residue for the switchover sweep.
            final long staleTime = now.minus(Duration.ofHours(1)).toEpochMilli();
            insertTasks(context, filterIds.subList(0, Math.min(QUEUED_ROWS, filterIds.size())), 1,
                    TaskStatus.QUEUED, node1, feed, staleTime, metaId);

            LOGGER.info("Seeded {} filters and {} tasks in {}",
                    FILTER_COUNT,
                    context.fetchCount(PROCESSOR_TASK),
                    Duration.between(seedStart, Instant.now()));
        });
        return filterIds;
    }

    /**
     * Multi-row inserts, chunked, interleaved across filters so ids mix between filters the way
     * time-ordered creation mixes them in production.
     */
    private long insertTasks(final DSLContext context,
                             final List<Integer> filterIds,
                             final int perFilter,
                             final TaskStatus status,
                             final Integer nodeId,
                             final Integer feedId,
                             final long statusTimeMs,
                             final long firstMetaId) {
        long metaId = firstMetaId;
        final List<Object[]> rows = new ArrayList<>(filterIds.size() * perFilter);
        for (int i = 0; i < perFilter; i++) {
            for (final Integer filterId : filterIds) {
                rows.add(new Object[]{
                        1,                              // version
                        filterId,
                        nodeId,
                        feedId,
                        statusTimeMs,                   // create_time_ms
                        status.getPrimitiveValue(),
                        statusTimeMs,
                        metaId++});
            }
        }
        for (int from = 0; from < rows.size(); from += INSERT_CHUNK_SIZE) {
            final List<Object[]> chunk = rows.subList(from, Math.min(from + INSERT_CHUNK_SIZE, rows.size()));
            InsertValuesStepN<?> step = context.insertInto(PROCESSOR_TASK)
                    .columns(List.of(PROCESSOR_TASK.VERSION,
                            PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID,
                            PROCESSOR_TASK.FK_PROCESSOR_NODE_ID,
                            PROCESSOR_TASK.FK_PROCESSOR_FEED_ID,
                            PROCESSOR_TASK.CREATE_TIME_MS,
                            PROCESSOR_TASK.STATUS,
                            PROCESSOR_TASK.STATUS_TIME_MS,
                            PROCESSOR_TASK.META_ID));
            for (final Object[] row : chunk) {
                step = step.values(row);
            }
            step.execute();
        }
        return metaId;
    }

    private PlanLine explain(final DSLContext context, final String name, final String sql) {
        final Result<Record> result = context.fetch("EXPLAIN " + sql);
        LOGGER.info("EXPLAIN {}:\n{}\n{}", name, sql, result);
        assertThat(result).describedAs("single-table query should produce one plan line").hasSize(1);
        final Record line = result.get(0);
        return new PlanLine(
                line.get("key", String.class),
                String.valueOf(line.get("Extra", String.class)));
    }

    private record PlanLine(String key, String extra) {

    }
}
