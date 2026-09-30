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

import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.meta.api.MetaService;
import stroom.node.api.NodeInfo;
import stroom.processor.impl.EligibleFilters;
import stroom.processor.impl.FilterFetchBackoff;
import stroom.processor.impl.PrioritisedFilters;
import stroom.processor.impl.ProcessorConfig;
import stroom.processor.impl.ProcessorProfileCache;
import stroom.processor.impl.ProcessorTaskAvailability;
import stroom.processor.impl.ProcessorTaskClaimer;
import stroom.processor.impl.ProcessorTaskDao;
import stroom.processor.impl.ProcessorTaskHeartbeat;
import stroom.processor.impl.ProcessorTaskQueueManager;
import stroom.processor.impl.ProcessorTaskQueueManagerTestFactory;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskList;
import stroom.processor.shared.TaskStatus;
import stroom.security.api.SecurityContext;
import stroom.statistics.api.InternalStatisticsReceiver;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.SimpleTaskContext;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskManager;
import stroom.task.api.TerminateHandlerFactory;
import stroom.task.shared.TaskId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.Mockito;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * gh-5699 Phase 3 (§8). The local scaled-down many-node harness: the master queue path and worker
 * node claiming, driven over the <em>same</em> seeded data in the same database, so the numbers can
 * be compared rather than merely reported.
 * <p>
 * Run with {@code -DrunProcessorBenchmark=true}; it is off by default because it takes minutes and
 * measures rather than asserts. Scale it with {@code -DbenchFilterCount},
 * {@code -DbenchTasksPerFilter}, {@code -DbenchNodeCount} and {@code -DbenchBatchSize}.
 * <p>
 * <b>What it measures, and why those things.</b>
 * <ul>
 *     <li><b>Tasks/sec</b> cluster-wide - the headline.</li>
 *     <li><b>Statements against {@code processor_task}</b>, counted at the DAO. This is the
 *     comparison that matters for the design's central trade: the queue path does O(filters)
 *     discovery once for the cluster, claiming does O(eligible filters) per node continuously.</li>
 *     <li><b>p99 fetch latency</b> - a mean hides the tail that a worker actually waits on.</li>
 *     <li><b>Observed batch size per fetch</b> - the direct evidence for §3.7's claim that the
 *     fetcher self-clocks. If it sits at 1 under load, the coalescing was misread and the
 *     per-fetch overhead question reopens.</li>
 * </ul>
 * <b>Accepted limitations, stated rather than buried:</b> tasks here do no work, so this measures
 * dispatch and nothing else; a thread is not a node, so it shares a JVM, a connection pool and a
 * machine with its peers; and the filter counts reachable locally are far below the ones this
 * design exists for. It can falsify the dispatch claims. It cannot confirm the scaling claim.
 */
@EnabledIfSystemProperty(named = "runProcessorBenchmark", matches = "true")
class TestProcessorTaskDispatchBenchmark extends AbstractProcessorTest {

    private static final LambdaLogger LOGGER =
            LambdaLoggerFactory.getLogger(TestProcessorTaskDispatchBenchmark.class);

    private static final int FILTER_COUNT = Integer.getInteger("benchFilterCount", 200);
    private static final int TASKS_PER_FILTER = Integer.getInteger("benchTasksPerFilter", 50);
    private static final int NODE_COUNT = Integer.getInteger("benchNodeCount", 8);
    private static final int BATCH_SIZE = Integer.getInteger("benchBatchSize", 20);
    private static final int TOTAL_TASKS = FILTER_COUNT * TASKS_PER_FILTER;
    /** So a path that stalls reports what it managed rather than hanging the build. */
    private static final Duration TIME_LIMIT = Duration.ofSeconds(Integer.getInteger("benchTimeLimitSec", 300));

    @Test
    void compareDispatchPaths() {
        LOGGER.info("Benchmark: {} filters x {} tasks = {} tasks, {} nodes, batch {}",
                FILTER_COUNT, TASKS_PER_FILTER, TOTAL_TASKS, NODE_COUNT, BATCH_SIZE);

        final List<ProcessorFilter> filters = seed();
        final Result claiming = measureClaiming(filters);
        resetTasksToCreated();
        final Result queueing = measureQueueing(filters);

        report("worker node claiming", claiming);
        report("master queue fill + assign", queueing);
        LOGGER.info("""

                        SUMMARY ({} filters, {} tasks, {} nodes)
                          tasks/sec                  {}   vs   {}
                          processor_task statements  {}   vs   {}
                          statements per task        {}   vs   {}
                          fetch p50/p99 (ms)         {}/{}   vs   {}/{}
                          mean batch per fetch       {}   vs   {}
                          empty fetches              {}   vs   {}""",
                FILTER_COUNT, TOTAL_TASKS, NODE_COUNT,
                claiming.tasksPerSecond(), queueing.tasksPerSecond(),
                claiming.totalStatements(), queueing.totalStatements(),
                claiming.statementsPerTask(), queueing.statementsPerTask(),
                claiming.p50Ms(), claiming.p99Ms(), queueing.p50Ms(), queueing.p99Ms(),
                claiming.meanBatch(), queueing.meanBatch(),
                claiming.emptyFetches(), queueing.emptyFetches());
    }

    // --------------------------------------------------------------------------------
    // Master queue fill + assign
    // --------------------------------------------------------------------------------

    private Result measureQueueing(final List<ProcessorFilter> filters) {
        final CountingDao counting = new CountingDao(processorTaskDao);
        final ProcessorTaskDao dao = counting.proxy();
        final Samples samples = new Samples();
        final AtomicLong dispatched = new AtomicLong();
        final Instant deadline = Instant.now().plus(TIME_LIMIT);

        final ProcessorTaskQueueManager queueManager = queueManager(dao, filters);
        queueManager.startup();

        final List<Runnable> workers = new ArrayList<>();
        // The queue fill: one thread, because it only ever runs on the master.
        workers.add(() -> {
            while (!isFinished(dispatched, deadline)) {
                queueManager.exec();
            }
        });
        for (int i = 0; i < NODE_COUNT; i++) {
            final String nodeName = "bench" + i;
            workers.add(() -> {
                while (!isFinished(dispatched, deadline)) {
                    final Instant start = Instant.now();
                    final ProcessorTaskList assigned = queueManager.assignTasks(
                            TaskId.createTestTaskId(), nodeName, BATCH_SIZE);
                    final List<ProcessorTask> tasks = assigned == null
                            ? List.of()
                            : assigned.getList();
                    samples.record(start, tasks.size());
                    if (tasks.isEmpty()) {
                        // The fill runs behind us, so an empty answer does not mean the pool is
                        // drained.
                        pause();
                    } else {
                        dispatched.addAndGet(tasks.size());
                        complete(dao, tasks, nodeName);
                    }
                }
            });
        }

        final Duration elapsed = runConcurrently(workers);
        queueManager.shutdown();
        return new Result(dispatched.get(), elapsed, samples, counting.counts());
    }

    /**
     * Put every task back so the second path starts from the population the first one did.
     */
    private void resetTasksToCreated() {
        stroom.db.util.JooqUtil.context(processorDbConnProvider, context -> context
                .update(stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK)
                .set(stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK.STATUS,
                        TaskStatus.CREATED.getPrimitiveValue())
                .setNull(stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK.FK_PROCESSOR_NODE_ID)
                .setNull(stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK.START_TIME_MS)
                .setNull(stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK.END_TIME_MS)
                .execute());
    }

    private ProcessorTaskQueueManager queueManager(final ProcessorTaskDao dao,
                                                   final List<ProcessorFilter> filters) {
        final ProcessorConfig processorConfig = benchConfig();
        Mockito.when(processorConfig.isAssignTasks()).thenReturn(true);
        Mockito.when(processorConfig.isFillTaskQueue()).thenReturn(true);
        Mockito.when(processorConfig.getQueueSize()).thenReturn(1_000);
        // A wait here would measure the wait rather than the path.
        Mockito.when(processorConfig.getWaitToQueueTasksDuration()).thenReturn(StroomDuration.ZERO);

        final PrioritisedFilters prioritisedFilters = Mockito.mock(PrioritisedFilters.class);
        Mockito.when(prioritisedFilters.get()).thenReturn(filters);
        final MetaService metaService = Mockito.mock(MetaService.class);
        Mockito.when(metaService.findLockedMeta(Mockito.any())).thenReturn(Set.of());
        final NodeInfo nodeInfo = Mockito.mock(NodeInfo.class);
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn("master");
        final TargetNodeSetFactory targetNodeSetFactory = Mockito.mock(TargetNodeSetFactory.class);
        try {
            Mockito.when(targetNodeSetFactory.getEnabledTargetNodeSet()).thenReturn(Set.of("master"));
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        final ExecutorProvider executorProvider = Mockito.mock(ExecutorProvider.class);
        // Fill synchronously, so what is measured is the fill itself rather than a thread pool.
        Mockito.when(executorProvider.get(Mockito.any())).thenReturn(Runnable::run);

        return ProcessorTaskQueueManagerTestFactory.create(
                dao,
                executorProvider,
                new BenchTaskContextFactory(),
                nodeInfo,
                () -> processorConfig,
                () -> Mockito.mock(InternalStatisticsReceiver.class),
                metaService,
                getInjector().getInstance(SecurityContext.class),
                targetNodeSetFactory,
                prioritisedFilters,
                Mockito.mock(ProcessorProfileCache.class),
                new FilterFetchBackoff());
    }

    // --------------------------------------------------------------------------------
    // Worker node claiming
    // --------------------------------------------------------------------------------

    private Result measureClaiming(final List<ProcessorFilter> filters) {
        final CountingDao counting = new CountingDao(processorTaskDao);
        final ProcessorTaskDao dao = counting.proxy();
        final Samples samples = new Samples();
        final AtomicLong dispatched = new AtomicLong();
        final Instant deadline = Instant.now().plus(TIME_LIMIT);

        // Each "node" gets its own claimer, as each node would - its own availability summary, its
        // own backoff, its own heartbeat registry. Sharing one would measure something else.
        final List<Runnable> nodes = new ArrayList<>();
        for (int i = 0; i < NODE_COUNT; i++) {
            final String nodeName = "bench" + i;
            final ProcessorTaskClaimer claimer = claimer(dao, filters, nodeName);
            nodes.add(() -> {
                while (!isFinished(dispatched, deadline)) {
                    final Instant start = Instant.now();
                    final List<ProcessorTask> claimed = claimer.claimTasks(BATCH_SIZE);
                    samples.record(start, claimed.size());
                    if (claimed.isEmpty()) {
                        // Not necessarily drained - another node may hold the rest momentarily -
                        // so keep going until the pool really is empty or we run out of time.
                        pause();
                    } else {
                        dispatched.addAndGet(claimed.size());
                        complete(dao, claimed, nodeName);
                    }
                }
            });
        }

        final Duration elapsed = runConcurrently(nodes);
        return new Result(dispatched.get(), elapsed, samples, counting.counts());
    }

    // --------------------------------------------------------------------------------

    /**
     * Finish a dispatched task. Tasks do no work here - §8 asks for short tasks, because they are
     * what maximise dispatch rate and so expose per-fetch overhead - but they must still be taken
     * out of the pool the way real ones are.
     */
    private void complete(final ProcessorTaskDao dao, final List<ProcessorTask> tasks, final String nodeName) {
        final long now = System.currentTimeMillis();
        tasks.forEach(task -> dao.changeTaskStatus(task, nodeName, TaskStatus.COMPLETE, now, now));
    }

    private static boolean isFinished(final AtomicLong dispatched, final Instant deadline) {
        return dispatched.get() >= TOTAL_TASKS || Instant.now().isAfter(deadline);
    }

    private static void pause() {
        try {
            Thread.sleep(5);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Duration runConcurrently(final List<Runnable> work) {
        final CountDownLatch ready = new CountDownLatch(work.size());
        final CountDownLatch go = new CountDownLatch(1);
        final List<Thread> threads = new ArrayList<>();
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
        work.forEach(runnable -> {
            final Thread thread = new Thread(() -> {
                try {
                    ready.countDown();
                    go.await();
                    runnable.run();
                } catch (final Throwable e) {
                    errors.add(e);
                }
            });
            threads.add(thread);
            thread.start();
        });
        try {
            ready.await();
            final Instant start = Instant.now();
            go.countDown();
            for (final Thread thread : threads) {
                thread.join();
            }
            final Duration elapsed = Duration.between(start, Instant.now());
            errors.forEach(e -> LOGGER.error(e::getMessage, e));
            return elapsed;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private List<ProcessorFilter> seed() {
        final Processor processor = createProcessor();
        final List<ProcessorFilter> filters = new ArrayList<>(FILTER_COUNT);
        final Instant start = Instant.now();
        for (int i = 0; i < FILTER_COUNT; i++) {
            // Enabled, because the queue fill skips disabled filters and PrioritisedFilters would
            // never have offered one. Without this the two paths would not see the same work.
            final ProcessorFilter filter = processorFilterDao.update(
                    createProcessorFilter(processor).copy().enabled(true).build());
            filters.add(filter);
            for (int t = 0; t < TASKS_PER_FILTER; t++) {
                createProcessorTask(filter, TaskStatus.CREATED, null, FEED);
            }
        }
        LOGGER.info("Seeded {} filters and {} tasks in {}",
                FILTER_COUNT, FILTER_COUNT * TASKS_PER_FILTER, Duration.between(start, Instant.now()));
        return filters;
    }

    private ProcessorTaskClaimer claimer(final ProcessorTaskDao dao,
                                         final List<ProcessorFilter> filters,
                                         final String nodeName) {
        final EligibleFilters eligibleFilters = Mockito.mock(EligibleFilters.class);
        Mockito.when(eligibleFilters.getEligibleFilters(Mockito.any(Instant.class))).thenReturn(filters);
        final ProcessorConfig processorConfig = benchConfig();
        final MetaService metaService = Mockito.mock(MetaService.class);
        Mockito.when(metaService.findLockedMeta(Mockito.anyList())).thenReturn(Set.of());
        final NodeInfo nodeInfo = Mockito.mock(NodeInfo.class);
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn(nodeName);

        return new ProcessorTaskClaimer(
                new ProcessorTaskAvailability(eligibleFilters, dao, () -> processorConfig),
                dao,
                Mockito.mock(ProcessorProfileCache.class),
                new ProcessorTaskHeartbeat(
                        dao, nodeInfo, Mockito.mock(TaskManager.class), () -> processorConfig),
                new FilterFetchBackoff(),
                metaService,
                nodeInfo,
                getInjector().getInstance(SecurityContext.class),
                () -> processorConfig);
    }

    private ProcessorConfig benchConfig() {
        final ProcessorConfig processorConfig = Mockito.mock(ProcessorConfig.class);
        Mockito.when(processorConfig.getTaskAvailabilityInterval()).thenReturn(StroomDuration.ofSeconds(5));
        Mockito.when(processorConfig.getSkipEmptyFilterFetchDuration()).thenReturn(StroomDuration.ofSeconds(10));
        Mockito.when(processorConfig.getTaskLeaseTimeout()).thenReturn(StroomDuration.ofMinutes(10));
        return processorConfig;
    }

    private void report(final String name, final Result result) {
        LOGGER.info("""

                        --- {} ---
                        dispatched      : {} tasks in {}
                        throughput      : {} tasks/sec
                        statements      : {} against processor_task ({} per task)
                        fetch latency   : p50 {}ms, p99 {}ms, max {}ms over {} fetches
                        batch per fetch : mean {}, max {}, {} empty
                        dao calls       : {}""",
                name,
                result.dispatched(), result.elapsed(),
                result.tasksPerSecond(),
                result.totalStatements(), result.statementsPerTask(),
                result.p50Ms(), result.p99Ms(), result.maxMs(), result.fetchCount(),
                result.meanBatch(), result.maxBatch(), result.emptyFetches(),
                result.daoCalls());
    }


    // --------------------------------------------------------------------------------


    /**
     * Counts calls per DAO method. Counting here rather than at the JDBC layer keeps the numbers
     * meaningful: one {@code claimTasks} is one round trip that does the work of a
     * find-then-queue pair on the old path, and it is that shape of comparison the design turns on.
     */
    private static final class CountingDao implements InvocationHandler {

        private final ProcessorTaskDao delegate;
        private final Map<String, AtomicLong> counts = new ConcurrentHashMap<>();

        private CountingDao(final ProcessorTaskDao delegate) {
            this.delegate = delegate;
        }

        private ProcessorTaskDao proxy() {
            return (ProcessorTaskDao) Proxy.newProxyInstance(
                    ProcessorTaskDao.class.getClassLoader(),
                    new Class<?>[]{ProcessorTaskDao.class},
                    this);
        }

        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
            counts.computeIfAbsent(method.getName(), name -> new AtomicLong()).incrementAndGet();
            try {
                return method.invoke(delegate, args);
            } catch (final java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        }

        private Map<String, Long> counts() {
            return counts.entrySet()
                    .stream()
                    .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().get(),
                            (a, b) -> a,
                            java.util.TreeMap::new));
        }
    }


    // --------------------------------------------------------------------------------


    private static final class Samples {

        private final List<long[]> samples = new CopyOnWriteArrayList<>();

        private void record(final Instant start, final int batchSize) {
            samples.add(new long[]{Duration.between(start, Instant.now()).toMillis(), batchSize});
        }

        private List<long[]> get() {
            return samples;
        }
    }


    // --------------------------------------------------------------------------------


    private record Result(long dispatched,
                          Duration elapsed,
                          Samples samples,
                          Map<String, Long> daoCalls) {

        private long tasksPerSecond() {
            final long millis = Math.max(1, elapsed.toMillis());
            return dispatched * 1_000 / millis;
        }

        private long totalStatements() {
            return daoCalls.values().stream().mapToLong(Long::longValue).sum();
        }

        private String statementsPerTask() {
            return dispatched == 0
                    ? "n/a"
                    : String.format("%.2f", (double) totalStatements() / dispatched);
        }

        private int fetchCount() {
            return samples.get().size();
        }

        private long p50Ms() {
            return percentile(0.50);
        }

        private long p99Ms() {
            return percentile(0.99);
        }

        private long maxMs() {
            return samples.get().stream().mapToLong(sample -> sample[0]).max().orElse(0);
        }

        private long percentile(final double fraction) {
            final long[] sorted = samples.get().stream().mapToLong(sample -> sample[0]).sorted().toArray();
            if (sorted.length == 0) {
                return 0;
            }
            return sorted[Math.min(sorted.length - 1, (int) (sorted.length * fraction))];
        }

        private String meanBatch() {
            return samples.get().isEmpty()
                    ? "n/a"
                    : String.format("%.1f", samples.get().stream()
                    .mapToLong(sample -> sample[1])
                    .average()
                    .orElse(0));
        }

        private long maxBatch() {
            return samples.get().stream().mapToLong(sample -> sample[1]).max().orElse(0);
        }

        private long emptyFetches() {
            return samples.get().stream().filter(sample -> sample[1] == 0).count();
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * {@code SimpleTaskContext.getTaskId()} returns null but assignment calls {@code setParentId}
     * on it, so a usable id is needed here.
     */
    private static final class BenchTaskContextFactory extends SimpleTaskContextFactory {

        @Override
        public <R> Supplier<R> contextResult(final String taskName,
                                             final TerminateHandlerFactory terminateHandlerFactory,
                                             final java.util.function.Function<TaskContext, R> function) {
            return () -> function.apply(new SimpleTaskContext() {
                @Override
                public TaskId getTaskId() {
                    return TaskId.createTestTaskId();
                }
            });
        }
    }
}
