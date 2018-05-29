/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.entity.cluster.ClearServiceClusterTask;
import stroom.entity.shared.Period;
import stroom.jobsystem.JobTrackedSchedule;
import stroom.jobsystem.shared.JobManager;
import stroom.node.NodeService;
import stroom.node.shared.FindNodeCriteria;
import stroom.node.shared.Node;
import stroom.pipeline.PipelineStore;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.statistics.sql.StatisticEvent;
import stroom.statistics.sql.StatisticTag;
import stroom.statistics.sql.Statistics;
import stroom.streamstore.FeedService;
import stroom.streamstore.StreamAttributeMapService;
import stroom.streamstore.StreamStore;
import stroom.streamstore.shared.FindStreamAttributeMapCriteria;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.QueryData;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamAttributeConstants;
import stroom.streamstore.shared.StreamAttributeMap;
import stroom.streamstore.shared.StreamDataSource;
import stroom.streamstore.shared.StreamStatus;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.StreamProcessorFilterService;
import stroom.streamtask.StreamProcessorService;
import stroom.streamtask.StreamProcessorTask;
import stroom.streamtask.shared.FindStreamProcessorCriteria;
import stroom.streamtask.shared.FindStreamProcessorFilterCriteria;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.task.AsyncTaskHelper;
import stroom.task.GenericServerTask;
import stroom.task.TaskContext;
import stroom.task.TaskManager;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.task.cluster.TargetNodeSetFactory.TargetType;
import stroom.util.date.DateUtil;
import stroom.util.lifecycle.StroomSimpleCronSchedule;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.Task;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

public class BenchmarkClusterExecutor extends AbstractBenchmark {
    // 20 min timeout
    private static final int TIME_OUT = 1000 * 60 * 20;

    private static final Logger LOGGER = LoggerFactory.getLogger(BenchmarkClusterExecutor.class);
    private static final String ROOT_TEST_NAME = "Benchmark-Cluster Test";
    private static final String EPS = "EPS";
    private static final String ERROR = "Error";
    private static final String BENCHMARK_REFERENCE = "BENCHMARK-REFERENCE";
    private static final String BENCHMARK_EVENTS = "BENCHMARK-EVENTS";

    private final FeedService feedService;
    private final PipelineStore pipelineStore;
    private final StreamProcessorFilterService streamProcessorFilterService;
    private final StreamProcessorService streamProcessorService;
    private final ClusterDispatchAsyncHelper dispatchHelper;
    private final StreamAttributeMapService streamAttributeMapService;
    private final StreamStore streamStore;
    private final JobManager jobManager;
    private final NodeService nodeService;
    private final TaskContext taskContext;
    private final TaskManager taskManager;
    private final Set<Node> nodeSet = new HashSet<>();
    private final Statistics statistics;
    private final BenchmarkClusterConfig benchmarkClusterConfig;

    private final ReentrantLock rangeLock = new ReentrantLock();
    private volatile Long minStreamId = null;
    private volatile Long maxStreamId = null;

    private Task<?> task;

    @Inject
    BenchmarkClusterExecutor(final FeedService feedService,
                             final PipelineStore pipelineStore,
                             final StreamProcessorFilterService streamProcessorFilterService,
                             final StreamProcessorService streamProcessorService,
                             final ClusterDispatchAsyncHelper dispatchHelper,
                             final StreamAttributeMapService streamAttributeMapService,
                             final StreamStore streamStore,
                             final JobManager jobManager,
                             final NodeService nodeService,
                             final TaskContext taskContext,
                             final TaskManager taskManager,
                             final Statistics statistics,
                             final BenchmarkClusterConfig benchmarkClusterConfig) {
        this.feedService = feedService;
        this.pipelineStore = pipelineStore;
        this.streamProcessorFilterService = streamProcessorFilterService;
        this.streamProcessorService = streamProcessorService;
        this.dispatchHelper = dispatchHelper;
        this.streamAttributeMapService = streamAttributeMapService;
        this.streamStore = streamStore;
        this.jobManager = jobManager;
        this.nodeService = nodeService;
        this.taskContext = taskContext;
        this.taskManager = taskManager;
        this.statistics = statistics;
        this.benchmarkClusterConfig = benchmarkClusterConfig;
    }

    @StroomSimpleCronSchedule(cron = "* * *")
    @JobTrackedSchedule(jobName = "XX Benchmark System XX", description = "Job to generate data in the system in order to benchmark it's performance (do not run in live!!)", enabled = false)
    public void exec(final Task<?> task) {
        this.task = task;
        info("Starting benchmark");

        // Find out what translation jobs are enabled and how many tasks are
        // possible across the cluster. If execution of no tasks are possible
        // then we should skip this benchmark as we won't be able to process
        // anything.
        LOGGER.info("Using benchmark stream count of {}", benchmarkClusterConfig.getStreamCount());
        LOGGER.info("Using benchmark record count of {}", benchmarkClusterConfig.getRecordCount());
        LOGGER.info("Using benchmark concurrent writers of {}", benchmarkClusterConfig.getConcurrentWriters());

        nodeSet.addAll(nodeService.find(new FindNodeCriteria()));

        // // Remove all old benchmark data.
        // removeOldData(folder, null);

        final boolean wasProcessing = jobManager.isJobEnabled(StreamProcessorTask.JOB_NAME);

        // Stop all translations and indexing so that data isn't translated
        // or indexed as soon as we
        // add it to the cluster.
        jobManager.setJobEnabled(StreamProcessorTask.JOB_NAME, false);

        // FIXME : MAKE SURE ALL TASKS HAVE STOPPED BEFORE WE EXECUTE THE
        // BENCHMARK.
        // try {
        // // Wait for all job instances to stop.
        // int instances = jobManager.getRunningInstances(
        // TranslationTask.JOB_NAME, true)
        // + jobManager.getRunningInstances(IndexingTask.JOB_NAME,
        // true);
        // while (!isStopping() && instances > 0) {
        // // Wait five seconds.
        // ThreadUtil.sleep(5000);
        // instances = jobManager.getRunningInstances(
        // TranslationTask.JOB_NAME, true)
        // + jobManager.getRunningInstances(
        // IndexingTask.JOB_NAME, true);
        // }

        // Create the benchmark.
        createBenchmark();

        // // Don't delete data if we were asked to stop.
        // if (!isStopping()) {
        // // Remove all old benchmark data.
        // removeOldData(folder, null);
        // }

        // Go back to the original job state.
        jobManager.setJobEnabled(StreamProcessorTask.JOB_NAME, wasProcessing);
    }

    private void createBenchmark() {
        try {
            if (!isTerminated()) {
                LOGGER.info("Starting cluster benchmark");

                dispatchHelper.execAsync(new ClearServiceClusterTask(task, null), TargetType.ACTIVE);

                final DocRef referencePipeline = pipelineStore.findByName(BENCHMARK_REFERENCE).get(0);
                final DocRef eventsPipeline = pipelineStore.findByName(BENCHMARK_EVENTS).get(0);

                // Not setup to run benchmark
                if (referencePipeline == null || eventsPipeline == null) {
                    return;
                }

                final StreamProcessor referenceProcessor = initProcessor(referencePipeline);
                final StreamProcessor eventsProcessor = initProcessor(eventsPipeline);

                // Create some data.
                LOGGER.info("Creating data");
                final String referenceData = createReferenceData(benchmarkClusterConfig.getRecordCount());
                final String eventData = createEventData(benchmarkClusterConfig.getRecordCount());

                final Period refPeriod = writeData(BENCHMARK_REFERENCE, StreamType.RAW_REFERENCE.getName(), referenceData, benchmarkClusterConfig.getStreamCount());

                processData(BENCHMARK_REFERENCE, StreamType.RAW_REFERENCE, StreamType.REFERENCE, referenceProcessor,
                        refPeriod);

                final Period evtPeriod = writeData(BENCHMARK_EVENTS, StreamType.RAW_EVENTS.getName(), eventData, benchmarkClusterConfig.getStreamCount());

                processData(BENCHMARK_EVENTS, StreamType.RAW_EVENTS, StreamType.EVENTS, eventsProcessor, evtPeriod);

                // Probe.setPrefix("EVENTS");
                // writeData(eventFeed, StreamType.RAW_EVENTS, eventData, node);
                // translateData(eventFeed, StreamType.RAW_EVENTS, eventData,
                // node);
                // Probe.setPrefix("INDEX");
                // indexData(eventFeed, StreamType.RAW_EVENTS, eventData, node);
                // Probe.setRecording(false);

                // while (true) {
                // final FindIndexStreamTaskCriteria criteria = new
                // FindIndexStreamTaskCriteria();
                // criteria.setFeed(eventFeed);
                // criteria.setStreamTaskStatus(TaskStatus.COMPLETE);
                // criteria.setReceivedPeriod(new Period(startTimeMs, null));
                // indexStreamTaskService.reprocess(criteria);
                //
                // indexData(eventFeed, StreamType.RAW_EVENTS, eventData, node);
                // }
                //
                LOGGER.info("Cluster benchmark complete");
            }
        } catch (final RuntimeException e) {
            LOGGER.error("Unable to create benchmark!", e);
        }
    }

    private StreamProcessor initProcessor(final DocRef pipelineDoc) {
        // Clear off any old processors
        for (final StreamProcessorFilter streamProcessorFilter : streamProcessorFilterService
                .find(new FindStreamProcessorFilterCriteria(pipelineDoc))) {
            streamProcessorFilterService.delete(streamProcessorFilter);
        }
        StreamProcessor streamProcessor = streamProcessorService.find(new FindStreamProcessorCriteria(pipelineDoc))
                .getFirst();
        if (streamProcessor == null) {
            streamProcessor = new StreamProcessor(pipelineDoc);
            streamProcessor.setEnabled(true);
            streamProcessor = streamProcessorService.save(streamProcessor);
        }
        return streamProcessor;
    }

    private Period writeData(final String feedName, final String streamTypeName, final String data, final int streamCount) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        if (!isTerminated()) {
            LOGGER.info("Adding {} data streams to the cluster", streamCount);

            LOGGER.info("Writing data");
            final AsyncTaskHelper<VoidResult> asyncTaskHelper = new AsyncTaskHelper<>(
                    "Writing test streams\n", taskContext, taskManager, benchmarkClusterConfig.getConcurrentWriters());
            for (int i = 1; i <= streamCount && !isTerminated(); i++) {
                final int count = i;
                final GenericServerTask writerTask = GenericServerTask.create("WriteBenchmarkData", "Writing benchmark data");
                writerTask.setRunnable(() -> {
                    final Stream stream = writeData(feedName, streamTypeName, data);

                    rangeLock.lock();
                    try {
                        if (minStreamId == null || minStreamId > stream.getId()) {
                            minStreamId = stream.getId();
                        }
                        if (maxStreamId == null || maxStreamId < stream.getId()) {
                            maxStreamId = stream.getId();
                        }
                    } finally {
                        rangeLock.unlock();
                    }

                    infoInterval("Written Stream {}/{}", count, streamCount);
                });
                asyncTaskHelper.fork(writerTask);
            }
            asyncTaskHelper.join();

            // Probe.addDuration("Writing data", elapsed * 1000000);
        }
        LOGGER.info("Written data in range [{}..{}] within {}", minStreamId, maxStreamId, logExecutionTime);
        return new Period(logExecutionTime.getStartTime(), System.currentTimeMillis());
    }

    private long getTimeoutTimeMs() {
        return System.currentTimeMillis() + TIME_OUT;
    }

    private void processData(final String feedName,
                             final StreamType rawStreamType,
                             final StreamType processedStreamType,
                             final StreamProcessor streamProcessor,
                             final Period createPeriod) {
        try {
            if (!isTerminated()) {
                final long startTime = System.currentTimeMillis();

                // Translate the data across the cluster.
                LOGGER.info("Processing data {}", feedName);
                final LogExecutionTime logExecutionTime = new LogExecutionTime();

                final ExpressionOperator rawExpression = new ExpressionOperator.Builder(Op.AND)
                        .addTerm(StreamDataSource.CREATE_TIME, Condition.BETWEEN, DateUtil.createNormalDateTimeString(createPeriod.getFromMs()) + "," + DateUtil.createNormalDateTimeString(createPeriod.getToMs()))
                        .addTerm(StreamDataSource.FEED, Condition.EQUALS, feedName)
                        .addTerm(StreamDataSource.STREAM_TYPE, Condition.EQUALS, rawStreamType.getName())
                        .build();
                final QueryData rawCriteria = new QueryData();
                rawCriteria.setExpression(rawExpression);

                streamProcessorFilterService.addFindStreamCriteria(streamProcessor, 1, rawCriteria);

                jobManager.setJobEnabled(StreamProcessorTask.JOB_NAME, true);

                // Wait for the cluster to stop processing.
                final ExpressionOperator processedExpression = new ExpressionOperator.Builder(Op.AND)
                        .addTerm(StreamDataSource.CREATE_TIME, Condition.GREATER_THAN_OR_EQUAL_TO, DateUtil.createNormalDateTimeString(startTime))
                        .addTerm(StreamDataSource.FEED, Condition.EQUALS, feedName)
                        .addTerm(StreamDataSource.STREAM_TYPE, Condition.EQUALS, processedStreamType.getName())
                        .addTerm(StreamDataSource.STATUS, Condition.EQUALS, StreamStatus.UNLOCKED.getDisplayValue())
                        .build();
                final FindStreamCriteria processedCriteria = new FindStreamCriteria();
                processedCriteria.setExpression(processedExpression);

                boolean complete = false;

                long timeoutTime = getTimeoutTimeMs();

                // Monitor translations and wait for all processing to complete.
                int completedTaskCount = 0;
                while (!complete && !isTerminated()) {
                    Thread.sleep(10000);

                    // Find out how many tasks are complete.
                    final List<Stream> streams = streamStore.find(processedCriteria);

                    // Things moved on ?
                    if (streams.size() > completedTaskCount) {
                        // Move on the time out
                        timeoutTime = getTimeoutTimeMs();
                        completedTaskCount = streams.size();
                    }

                    info("Completed {}/{} translation tasks", completedTaskCount, benchmarkClusterConfig.getStreamCount());

                    if (completedTaskCount >= benchmarkClusterConfig.getStreamCount()) {
                        complete = true;
                    }
                    if (System.currentTimeMillis() > timeoutTime) {
                        LOGGER.info("Timeout !! Abort !!");
                        abortDueToTimeout();
                    }
                }

                // Record benchmark statistics if we weren't asked to stop.
                if (!isTerminated()) {
                    final Period processPeriod = new Period(startTime, System.currentTimeMillis());
                    LOGGER.info("Translated {} data in {}", feedName, logExecutionTime);
                    recordTranslationStats(feedName, processPeriod);
                }

                jobManager.setJobEnabled(StreamProcessorTask.JOB_NAME, false);
            }
        } catch (final InterruptedException e) {
            LOGGER.error(e.getMessage(), e);

            // Continue to interrupt this thread.
            Thread.currentThread().interrupt();
        }
    }

    //
    // private void indexData(final Feed feed, final StreamType streamType,
    // final String data, final Node node) {
    // if (!isStopping()) {
    // // Index the data across the cluster.
    // if (feed.isEventFeed()) {
    // LOGGER.info("Indexing event data");
    // } else {
    // LOGGER.info("Indexing reference data");
    // }
    // long time = System.currentTimeMillis();
    // jobManager.setJobEnabled(IndexingTask.JOB_NAME, true);
    // // Wait for the cluster to stop processing.
    // final FindIndexStreamTaskCriteria criteria = new
    // FindIndexStreamTaskCriteria();
    // criteria.setFeed(feed);
    // criteria.setStreamTaskStatus(TaskStatus.COMPLETE);
    // criteria.setReceivedPeriod(new Period(startTimeMs, null));
    // boolean complete = false;
    //
    // long timeoutTime = getTimeoutTimeMs();
    //
    // // Monitor indexing and wait for all processing to complete.
    // int completedTaskCount = 0;
    // while (!complete && !isStopping()) {
    // ThreadUtil.sleepTenSeconds();
    //
    // // Find out how many tasks are complete.
    // final List<IndexStreamTask> completedTasks = indexStreamTaskService
    // .find(criteria);
    // if (completedTasks.size() > completedTaskCount) {
    //
    // // Move on the time out
    // timeoutTime = getTimeoutTimeMs();
    // completedTaskCount = completedTasks.size();
    // }
    //
    // info("Completed {}/{} index tasks", completedTaskCount,
    // streamCount);
    //
    // if (completedTaskCount >= streamCount) {
    // complete = true;
    // }
    // if (System.currentTimeMillis() > timeoutTime) {
    // LOGGER.info("Timeout !! Abort !!");
    // abortDueToTimeout();
    // }
    // }
    //
    // // Record benchmark statistics if we weren't asked to stop.
    // if (!isStopping()) {
    // final long elapsed = System.currentTimeMillis() - time;
    // if (feed.isEventFeed()) {
    // LOGGER.info("Indexed event data in " + elapsed + "ms");
    // } else {
    // LOGGER.info("Indexed reference data in " + elapsed + "ms");
    // }
    // recordIndexingStats(feed, node);
    // }
    //
    // jobManager.setJobEnabled(IndexingTask.JOB_NAME, false);
    // }
    // }

    private void recordTranslationStats(final String feedName, final Period processPeriod) {
        // // Flush out the attributes as we need these for the stats
        // dispatchHelper.execAsync(new FlushServiceClusterTask(null, null,
        // "Flush stream attribute values",
        // StreamAttributeValueFlush.class));

        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                .addTerm(StreamDataSource.FEED, Condition.EQUALS, feedName)
                .addTerm(StreamDataSource.CREATE_TIME, Condition.BETWEEN, DateUtil.createNormalDateTimeString(processPeriod.getFromMs()) + "," + DateUtil.createNormalDateTimeString(processPeriod.getToMs()))
                .addOperator(new ExpressionOperator.Builder(Op.OR)
                        .addTerm(StreamDataSource.STREAM_TYPE, Condition.EQUALS, StreamType.EVENTS.getName())
                        .addTerm(StreamDataSource.STREAM_TYPE, Condition.EQUALS, StreamType.REFERENCE.getName())
                        .build())
                .build();

        final FindStreamAttributeMapCriteria criteria = new FindStreamAttributeMapCriteria();
        criteria.obtainFindStreamCriteria().setExpression(expression);

        final List<StreamAttributeMap> processedStreams = streamAttributeMapService.find(criteria);

        final long nowMs = System.currentTimeMillis();

        final List<StatisticEvent> statisticEventList = new ArrayList<>();

        for (final Node node : nodeSet) {
            long nodeWritten = 0;
            long nodeError = 0;
            final Period nodePeriod = new Period();

            for (final StreamAttributeMap processedStream : processedStreams) {
                if (node.getName().equals(processedStream.getAttributeValue(StreamAttributeConstants.NODE))) {
                    nodeWritten += getLong(processedStream, StreamAttributeConstants.REC_WRITE);
                    nodeError += getLong(processedStream, StreamAttributeConstants.REC_ERROR);

                    checkPeriod(nodePeriod, processedStream);
                }
            }

            statisticEventList.add(StatisticEvent.createValue(nowMs,
                    ROOT_TEST_NAME, Arrays.asList(new StatisticTag("Node", node.getName()),
                            new StatisticTag("Feed", feedName), new StatisticTag("Type", EPS)),
                    (double) toEPS(nodeWritten, nodePeriod)));

            statisticEventList.add(StatisticEvent.createValue(nowMs,
                    ROOT_TEST_NAME, Arrays.asList(new StatisticTag("Node", node.getName()),
                            new StatisticTag("Feed", feedName), new StatisticTag("Type", ERROR)),
                    (double) toEPS(nodeError, nodePeriod)));

        }
        final Period clusterPeriod = new Period();

        for (final StreamAttributeMap processedStream : processedStreams) {
            checkPeriod(clusterPeriod, processedStream);
        }

        statistics.putEvents(statisticEventList);
    }

    private void checkPeriod(final Period period, final StreamAttributeMap processedStream) {
        final long streamStartMs = processedStream.getStream().getCreateMs();
        final long streamDuration = getLong(processedStream, StreamAttributeConstants.DURATION);
        final long streamEndMs = streamStartMs + streamDuration;

        if (period.getFromMs() == null) {
            period.setFromMs(streamStartMs);
        } else {
            if (period.getFromMs() > streamStartMs) {
                period.setFromMs(streamStartMs);
            }
        }
        if (period.getToMs() == null) {
            period.setToMs(streamEndMs);
        } else {
            if (period.getToMs() < streamEndMs) {
                period.setToMs(streamEndMs);
            }
        }
    }

    private long getLong(final StreamAttributeMap streamAttributeMap, final String name) {
        final String value = streamAttributeMap.getAttributeValue(name);
        if (value != null) {
            return Long.parseLong(value);
        }
        return 0;
    }

    public long toEPS(final long count, final Period duration) {
        if (!duration.isBounded()) {
            return 0;
        }
        if (duration.duration().longValue() == 0) {
            return 0;
        }
        try {
            return (long) (count / (duration.duration() / 1000.0));
        } catch (final java.lang.ArithmeticException ex) {
            return 0;
        }
    }

    // final String epsTestName = ROOT_TEST_NAME + TRANSLATION + EPS + feedType;
    // final String mbpsTestName = ROOT_TEST_NAME + TRANSLATION + BPS +
    // feedType;
    //
    // final long now = System.currentTimeMillis();
    //
    // final FindStreamCriteria criteria = new FindStreamCriteria();
    // criteria.getFeedIdSet().add(feed);
    // //criteria.getPipelineSet().add(pipeline);
    // //criteria.setReceivedPeriod(new Period(startTimeMs, null));
    //
    // //final List<Stream> completedTasks =
    // translationStreamTaskService.find(criteria);
    //
    // if (completedTasks != null && completedTasks.size() > 0) {
    // // Calculate
    // // EPS for each node.
    // //final MapNode, NodeTranslationResults> results = new HashMap<Node,
    // NodeTranslationResults>();
    //
    // // Add together the EPS and BPS for each node. long totalReadEPS = 0;
    // long totalWrittenEPS = 0;
    // long totalWarningEPS = 0;
    // long totalErrorEPS = 0;
    //
    // long totalStreamReadBPS = 0;
    // long totalFileReadBPS = 0;
    // long totalStreamWriteBPS = 0;
    // long totalFileWriteBPS = 0;
    //
    // for (final Node resultNode : results.keySet()) {
    // final NodeTranslationResults nodeResults = results .get(resultNode);
    // final
    // * NodeTranslationStats stats = nodeResults.getStats(); final String
    // * nodeName = "/" + resultNode.getName();
    // *
    // * // Save results for each node. benchmarkService.save(new
    // Benchmark(node,
    // * now, epsTestName + READ + nodeName, stats.getReadEPS()));
    // * benchmarkService.save(new Benchmark(node, now, epsTestName + WRITTEN +
    // * nodeName, stats.getWrittenEPS())); benchmarkService.save(new
    // * Benchmark(node, now, epsTestName + WARNING + nodeName,
    // * stats.getWarningEPS())); benchmarkService.save(new Benchmark(node, now,
    // * epsTestName + ERROR + nodeName, stats.getErrorEPS()));
    // *
    // * benchmarkService.save(new Benchmark(node, now, mbpsTestName +
    // STREAM_READ
    // * + nodeName, stats.getStreamReadBPS())); benchmarkService.save(new
    // * Benchmark(node, now, mbpsTestName + FILE_READ + nodeName,
    // * stats.getFileReadBPS())); benchmarkService.save(new Benchmark(node,
    // now,
    // * mbpsTestName + STREAM_WRITE + nodeName, stats.getStreamWriteBPS()));
    //
    //
    //
    // benchmarkService.save(new Benchmark(node, now, mbpsTestName + FILE_WRITE
    // + nodeName, stats.getFileWriteBPS()));
    // *
    // * // Add node result to cluster total. totalReadEPS +=
    // stats.getReadEPS();
    // * totalWrittenEPS += stats.getWrittenEPS(); totalWarningEPS +=
    // * stats.getWarningEPS(); totalErrorEPS += stats.getErrorEPS();
    // *
    // * totalStreamReadBPS += stats.getStreamReadBPS(); totalFileReadBPS +=
    // * stats.getFileReadBPS(); totalStreamWriteBPS +=
    // stats.getStreamWriteBPS();
    // * totalFileWriteBPS += stats.getFileWriteBPS(); }
    // *
    // * // Save the total results. benchmarkService.save(new Benchmark(node,
    // now,
    // * epsTestName + READ, totalReadEPS)); benchmarkService.save(new
    // * Benchmark(node, now, epsTestName + WRITTEN, totalWrittenEPS));
    // * benchmarkService.save(new Benchmark(node, now, epsTestName + WARNING,
    // * totalWarningEPS)); benchmarkService.save(new Benchmark(node, now,
    // * epsTestName + ERROR, totalErrorEPS));
    // *
    // * benchmarkService.save(new Benchmark(node, now, mbpsTestName +
    // * STREAM_READ, totalStreamReadBPS)); benchmarkService.save(new
    // * Benchmark(node, now, mbpsTestName + FILE_READ, totalFileReadBPS));
    // * benchmarkService.save(new Benchmark(node, now, mbpsTestName +
    // * STREAM_WRITE, totalStreamWriteBPS)); benchmarkService.save(new
    // Benchmark(node, now, mbpsTestName + FILE_WRITE, totalFileWriteBPS)); } }
    //
    // private void recordIndexingStats(final Feed feed, final Node node) {
    // String feedType = REFERENCE_DATA;
    // if (feed.isEventFeed()) {
    // feedType = EVENT_DATA;
    // }
    // final String epsTestName = ROOT_TEST_NAME + INDEXING + EPS + feedType;
    // final String mbpsTestName = ROOT_TEST_NAME + INDEXING + BPS + feedType;
    // final long now = System.currentTimeMillis();
    //
    // final FindIndexStreamTaskCriteria criteria = new
    // FindIndexStreamTaskCriteria();
    // criteria.setFeed(feed);
    // criteria.setFetchNode(true);
    // criteria.setStreamTaskStatus(TaskStatus.COMPLETE);
    // criteria.setReceivedPeriod(new Period(startTimeMs, null));
    // final List<IndexStreamTask> completedTasks = indexStreamTaskService
    // .find(criteria);
    //
    // if (completedTasks != null && completedTasks.size() > 0) {
    // // Calculate EPS for each node.
    // final Map<Node, NodeIndexingResults> results = new HashMap<Node,
    // NodeIndexingResults>();
    // for (final IndexStreamTask task : completedTasks) {
    // NodeIndexingResults nodeResults = results.get(task.getNode());
    // if (nodeResults == null) {
    // nodeResults = new NodeIndexingResults();
    // results.put(task.getNode(), nodeResults);
    // }
    //
    // nodeResults.addTask(task);
    // }
    //
    // // Add together the EPS and BPS for each node.
    // long totalReadEPS = 0;
    // long totalStreamReadBPS = 0;
    // long totalFileReadBPS = 0;
    //
    // for (final Entry<Node, NodeIndexingResults> entry : results
    // .entrySet()) {
    // final NodeIndexingResults nodeResults = entry.getValue();
    // final NodeIndexingStats stats = nodeResults.getStats();
    // final String nodeName = "/" + entry.getValues().getName();
    //
    // // Save results for each node.
    // benchmarkService.save(new Benchmark(node, now, epsTestName
    // + READ + nodeName, stats.getReadEPS()));
    //
    // benchmarkService.save(new Benchmark(node, now, mbpsTestName
    // + STREAM_READ + nodeName, stats.getStreamReadBPS()));
    // benchmarkService.save(new Benchmark(node, now, mbpsTestName
    // + FILE_READ + nodeName, stats.getFileReadBPS()));
    //
    // // Add node result to cluster total.
    // totalReadEPS += stats.getReadEPS();
    //
    // totalStreamReadBPS += stats.getStreamReadBPS();
    // totalFileReadBPS += stats.getFileReadBPS();
    // }
    //
    // // Save the total results.
    // benchmarkService.save(new Benchmark(node, now, epsTestName + READ,
    // totalReadEPS));
    //
    // benchmarkService.save(new Benchmark(node, now, mbpsTestName
    // + STREAM_READ, totalStreamReadBPS));
    // benchmarkService.save(new Benchmark(node, now, mbpsTestName
    // + FILE_READ, totalFileReadBPS));
    // }
    // }
    //
    //
    // private class NodeTranslationResults {
    // private long startMs = Long.MAX_VALUE;
    // private long endMs = 0;
    // private long recRead = 0;
    // private long recWritten = 0;
    // private long recWarning = 0;
    // private long recError = 0;
    // private long streamBytesRead = 0;
    // private long streamBytesWritten = 0;
    // private long fileBytesRead = 0;
    // private long fileBytesWritten = 0;
    //
    // public void addTask(final TranslationStreamTask task) {
    // if (task.getStartTimeMs() < startMs) {
    // startMs = task.getStartTimeMs();
    // }
    // if (task.getEndTimeMs() > endMs) {
    // endMs = task.getEndTimeMs();
    // }
    // if (task.getRecordsRead() != null) {
    // recRead += task.getRecordsRead();
    // }
    // if (task.getRecordsWritten() != null) {
    // recWritten += task.getRecordsWritten();
    // }
    // if (task.getRecordsWarning() != null) {
    // recWarning += task.getRecordsWarning();
    // }
    // if (task.getRecordsError() != null) {
    // recError += task.getRecordsError();
    // }
    //
    // // Add input stream size stats.
    // final Stream sourceStream = task.getStream();
    // if (sourceStream != null) {
    // if (sourceStream.getStreamSize() != null) {
    // streamBytesRead += sourceStream.getStreamSize();
    // }
    // if (sourceStream.getFileSize() != null) {
    // fileBytesRead += sourceStream.getFileSize();
    // }
    // }
    //
    // // Add output stream size stats.
    // final Stream targetMD = task.getTargetStream();
    // if (targetMD != null) {
    // if (targetMD.getStreamSize() != null) {
    // streamBytesWritten += targetMD.getStreamSize();
    // }
    // if (targetMD.getFileSize() != null) {
    // fileBytesWritten += targetMD.getFileSize();
    // }
    // }
    // }
    //
    // public NodeTranslationStats getStats() {
    // final double elapsedSec = (endMs - startMs) / MS_PER_SEC;
    // return new NodeTranslationStats((long) (recRead / elapsedSec),
    // (long) (recWritten / elapsedSec),
    // (long) (recWarning / elapsedSec),
    // (long) (recError / elapsedSec),
    // (long) (streamBytesRead / elapsedSec),
    // (long) (fileBytesRead / elapsedSec),
    // (long) (streamBytesWritten / elapsedSec),
    // (long) (fileBytesWritten / elapsedSec));
    // }
    // }
    //
    // private class NodeTranslationStats {
    // private final long readEPS;
    // private final long writtenEPS;
    // private final long warningEPS;
    // private final long errorEPS;
    // private final long streamReadBPS;
    // private final long fileReadBPS;
    // private final long streamWriteBPS;
    // private final long fileWriteBPS;
    //
    // public NodeTranslationStats(final long readEPS, final long writtenEPS,
    // final long warningEPS, final long errorEPS,
    // final long streamReadBPS, final long fileReadBPS,
    // final long streamWriteBPS, final long fileWriteBPS) {
    // this.readEPS = readEPS;
    // this.writtenEPS = writtenEPS;
    // this.warningEPS = warningEPS;
    // this.errorEPS = errorEPS;
    // this.streamReadBPS = streamReadBPS;
    // this.fileReadBPS = fileReadBPS;
    // this.streamWriteBPS = streamWriteBPS;
    // this.fileWriteBPS = fileWriteBPS;
    // }
    //
    // public long getReadEPS() {
    // return readEPS;
    // }
    //
    // public long getWrittenEPS() {
    // return writtenEPS;
    // }
    //
    // public long getWarningEPS() {
    // return warningEPS;
    // }
    //
    // public long getErrorEPS() {
    // return errorEPS;
    // }
    //
    // public long getStreamReadBPS() {
    // return streamReadBPS;
    // }
    //
    // public long getFileReadBPS() {
    // return fileReadBPS;
    // }
    //
    // public long getStreamWriteBPS() {
    // return streamWriteBPS;
    // }
    //
    // public long getFileWriteBPS() {
    // return fileWriteBPS;
    // }
    // }
    //
    // private class NodeIndexingResults {
    // private long startMs = Long.MAX_VALUE;
    // private long endMs = 0;
    // private long recRead = 0;
    // private long streamBytesRead = 0;
    // private long fileBytesRead = 0;
    //
    // public void addTask(final IndexStreamTask task) {
    // if (task.getStartTimeMs() < startMs) {
    // startMs = task.getStartTimeMs();
    // }
    // if (task.getEndTimeMs() > endMs) {
    // endMs = task.getEndTimeMs();
    // }
    // if (task.getRecordsRead() != null) {
    // recRead += task.getRecordsRead();
    // }
    //
    // // Add input stream size stats.
    // final Stream sourceStream = task.getStream();
    // if (sourceStream != null) {
    // if (sourceStream.getStreamSize() != null) {
    // streamBytesRead += sourceStream.getStreamSize();
    // }
    // if (sourceStream.getFileSize() != null) {
    // fileBytesRead += sourceStream.getFileSize();
    // }
    // }
    // }
    //
    // public NodeIndexingStats getStats() {
    // final double elapsedSec = (endMs - startMs) / MS_PER_SEC;
    // return new NodeIndexingStats((long) (recRead / elapsedSec),
    // (long) (streamBytesRead / elapsedSec),
    // (long) (fileBytesRead / elapsedSec));
    // }
    // }
    //
    // private class NodeIndexingStats {
    // private final long readEPS;
    // private final long streamReadBPS;
    // private final long fileReadBPS;
    //
    // public NodeIndexingStats(final long readEPS, final long streamReadBPS,
    // final long fileReadBPS) {
    // this.readEPS = readEPS;
    // this.streamReadBPS = streamReadBPS;
    // this.fileReadBPS = fileReadBPS;
    // }
    //
    // public long getReadEPS() {
    // return readEPS;
    // }
    //
    // public long getStreamReadBPS() {
    // return streamReadBPS;
    // }
    //
    // public long getFileReadBPS() {
    // return fileReadBPS;
    // }
    // }
}
