package stroom.node.server;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.statistics.server.sql.StatisticEvent;
import stroom.statistics.server.sql.StatisticTag;
import stroom.statistics.server.sql.Statistics;
import stroom.util.spring.StroomScope;
import stroom.util.spring.StroomSimpleCronSchedule;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("unused")
@Component
@Scope(StroomScope.TASK)
public class HeapHistogramStatisticsExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(HeapHistogramStatisticsExecutor.class);

    private static final String HEAP_HISTOGRAM_BYTES_STAT_NAME_BASE = "Heap Histogram ";
    private static final String HEAP_HISTOGRAM_BYTES_STAT_NAME = HEAP_HISTOGRAM_BYTES_STAT_NAME_BASE + "Bytes";
    private static final String HEAP_HISTOGRAM_INSTANCES_STAT_NAME = HEAP_HISTOGRAM_BYTES_STAT_NAME_BASE + "Instances";
    private static final String NODE_TAG_NAME = "Node";
    static final String CLASS_NAME_TAG_NAME = "Class Name";

    private final HeapHistogramService heapHistogramService;
    private final Statistics statistics;
    private final NodeCache nodeCache;


    @Inject
    HeapHistogramStatisticsExecutor(final HeapHistogramService heapHistogramService,
                                    final Statistics statistics,
                                    final NodeCache nodeCache) {
        this.heapHistogramService = heapHistogramService;
        this.statistics = statistics;
        this.nodeCache = nodeCache;
    }


    //hourly and disabled by default
    @StroomSimpleCronSchedule(cron = "0 * *")
    @JobTrackedSchedule(
            jobName = "Java Heap Histogram Statistics",
            description = "Generate Java heap map histogram ('jmap -histo:live') and record statistic events " +
                    "for the entries. CAUTION: this will pause the JVM, only enable this if you understand the " +
                    "consequences!",
            enabled = false)
    public void exec() {
        try {
            Instant startTme = Instant.now();
            LOGGER.info("Java Heap Histogram Statistics job started");
            List<HeapHistogramService.HeapHistogramEntry> heapHistogramEntries = heapHistogramService.generateHeapHistogram();
            processHistogramEntries(heapHistogramEntries);
            LOGGER.info("Java Heap Histogram Statistics job completed in %s",
                    Duration.between(startTme, Instant.now()).toString());
        } catch (Exception e) {
            LOGGER.error("Error executing scheduled Heap Histogram job", e);
            throw e;
        }
    }

    private void processHistogramEntries(List<HeapHistogramService.HeapHistogramEntry> heapHistogramEntries) {
        Preconditions.checkNotNull(heapHistogramService);

        final long statTimeMs = Instant.now().toEpochMilli();
        final String nodeName = nodeCache.getDefaultNode().getName();
        //immutable so can be reused for all events
        final StatisticTag nodeTag = new StatisticTag(NODE_TAG_NAME, nodeName);

        mapToStatEventAndSend(
                heapHistogramEntries,
                entry -> buildBytesEvent(statTimeMs, nodeTag, entry),
                "Bytes");

        mapToStatEventAndSend(
                heapHistogramEntries,
                entry -> buildInstancesEvent(statTimeMs, nodeTag, entry),
                "Instances");
    }

    private void mapToStatEventAndSend(final List<HeapHistogramService.HeapHistogramEntry> heapHistogramEntries,
                                       final Function<HeapHistogramService.HeapHistogramEntry, StatisticEvent> mapper,
                                       final String type) {

        List<StatisticEvent> statisticEvents = heapHistogramEntries.stream()
                .map(mapper)
                .collect(Collectors.toList());

        LOGGER.info("Sending %s '%s' histogram stat events", statisticEvents.size(), type);

        statistics.putEvents(statisticEvents);

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

    private static List<StatisticTag> buildTags(final StatisticTag nodeTag,
                                                final HeapHistogramService.HeapHistogramEntry heapHistogramEntry) {

        //tags need to be in name order else we will get problems with rollups
        //We could manually order them here but if the names change in the static strings the order may then be wrong
        return Stream.of(nodeTag, new StatisticTag(CLASS_NAME_TAG_NAME, heapHistogramEntry.getClassName()))
                .sorted(Comparator.comparing(StatisticTag::getTag))
                .collect(Collectors.toList());
    }
}
