package stroom.node.server;

import com.google.common.base.Preconditions;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.statistics.common.StatisticEvent;
import stroom.statistics.common.StatisticTag;
import stroom.statistics.common.StatisticsFactory;
import stroom.util.logging.StroomLogger;
import stroom.util.spring.StroomScope;
import stroom.util.spring.StroomSimpleCronSchedule;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
@Component
@Scope(value = StroomScope.TASK)
public class HeapHistogramStatisticsExecutor {

    private static final StroomLogger LOGGER = StroomLogger.getLogger(HeapHistogramStatisticsExecutor.class);

    private static final String HEAP_HISTOGRAM_BYTES_STAT_NAME_BASE = "Heap Histogram ";
    static final String HEAP_HISTOGRAM_BYTES_STAT_NAME = HEAP_HISTOGRAM_BYTES_STAT_NAME_BASE + "Bytes";
    static final String HEAP_HISTOGRAM_INSTANCES_STAT_NAME = HEAP_HISTOGRAM_BYTES_STAT_NAME_BASE + "Instances";
    static final String NODE_TAG_NAME = "Node";
    static final String CLASS_NAME_TAG_NAME = "Class Name";

    private final HeapHistogramService heapHistogramService;
    private final StatisticsFactory statisticsFactory;
    private final NodeCache nodeCache;


    @Inject
    public HeapHistogramStatisticsExecutor(final HeapHistogramService heapHistogramService,
                                           final StatisticsFactory statisticsFactory,
                                           final NodeCache nodeCache) {
        this.heapHistogramService = heapHistogramService;
        this.statisticsFactory = statisticsFactory;
        this.nodeCache = nodeCache;
    }


    //hourly  and disabled by default
    @StroomSimpleCronSchedule(cron = "0 * *")
    @JobTrackedSchedule(
            jobName = "Heap Histogram Statistics",
            advanced = false,
            description = "Job to record statistic events for a Java heap histogram",
            enabled = false)
    public void exec() {
        Instant startTme = Instant.now();
        LOGGER.info("Heap histogram job started");
//        heapHistogramService.buildHeapHistogram(this::consumeHistogramEntries);
        List<HeapHistogramService.HeapHistogramEntry> heapHistogramEntries = heapHistogramService.buildHeapHistogram();
        consumeHistogramEntries(heapHistogramEntries);
//        LOGGER.debug("Heap histogram job completed, results will be processed asynchronously");
        LOGGER.info("Heap histogram job completed in %s", Duration.between(startTme, Instant.now()).toString());
    }

    private void consumeHistogramEntries(List<HeapHistogramService.HeapHistogramEntry> heapHistogramEntries) {
        Preconditions.checkNotNull(heapHistogramService);

        final long statTimeMs = Instant.now().toEpochMilli();
        final String nodeName = nodeCache.getDefaultNode().getName();
        //immutable so can be reused for all events
        final StatisticTag nodeTag = new StatisticTag(NODE_TAG_NAME, nodeName);

        mapAndSend(heapHistogramEntries,
                entry -> buildBytesEvent(statTimeMs, nodeTag, entry),
                "Bytes");

        mapAndSend(heapHistogramEntries,
                entry -> buildInstancesEvent(statTimeMs, nodeTag, entry),
                "Instances");
    }

    private void mapAndSend(final List<HeapHistogramService.HeapHistogramEntry> heapHistogramEntries,
                            final Function<HeapHistogramService.HeapHistogramEntry, StatisticEvent> mapper,
                            final String type) {
        List<StatisticEvent> statisticEvents = heapHistogramEntries.stream()
                .map(mapper)
                .collect(Collectors.toList());

        LOGGER.info("Sending %s %s histogram stat events", statisticEvents.size(), type);

        statisticsFactory.instance().putEvents(statisticEvents);

    }

    private static StatisticEvent buildBytesEvent(final long statTimeMs,
                                                  final StatisticTag nodeTag,
                                                  final HeapHistogramService.HeapHistogramEntry heapHistogramEntry) {
        return StatisticEvent.createValue(
                statTimeMs,
                HEAP_HISTOGRAM_BYTES_STAT_NAME,
                buildTags(nodeTag, heapHistogramEntry),
                (double) heapHistogramEntry.getBytes());
    }

    private static StatisticEvent buildInstancesEvent(final long statTimeMs,
                                                      final StatisticTag nodeTag,
                                                      final HeapHistogramService.HeapHistogramEntry heapHistogramEntry) {
        return StatisticEvent.createValue(
                statTimeMs,
                HEAP_HISTOGRAM_INSTANCES_STAT_NAME,
                buildTags(nodeTag, heapHistogramEntry),
                (double) heapHistogramEntry.getInstances());
    }

    private static List<StatisticTag> buildTags(StatisticTag nodeTag, HeapHistogramService.HeapHistogramEntry heapHistogramEntry) {
        return Arrays.asList(
                nodeTag,
                new StatisticTag(CLASS_NAME_TAG_NAME, heapHistogramEntry.getClassName()));
    }
}
