package stroom.node.server;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.statistics.internal.InternalStatisticEvent;
import stroom.statistics.internal.InternalStatisticsReceiver;
import stroom.util.spring.StroomScope;
import stroom.util.spring.StroomSimpleCronSchedule;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Class for running scheduled jobs to execute a jmap heap histogram and load the results into
 * the {@link InternalStatisticsReceiver}. This is for use in identifying memory issues at run time
 * by capturing a regular snapshot of both the number of instances of classes and the bytes in use.
 *
 * As with all internal statistics it is reliant on the stat key being configured in stroom properties
 * (i.e. stroomCoreServerPropertyContext
 */
@SuppressWarnings("unused")
@Component
@Scope(StroomScope.TASK)
public class HeapHistogramStatisticsExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(HeapHistogramStatisticsExecutor.class);

    private static final String INTERNAL_STAT_KEY_HEAP_HISTOGRAM_INSTANCES = "heapHistogramInstances";
    private static final String INTERNAL_STAT_KEY_HEAP_HISTOGRAM_BYTES = "heapHistogramBytes";
    private static final String TAG_NAME_NODE = "Node";
    static final String TAG_NAME_CLASS_NAME = "Class Name";

    private final HeapHistogramService heapHistogramService;
    private final InternalStatisticsReceiver internalStatisticsReceiver;
    private final NodeCache nodeCache;


    @Inject
    HeapHistogramStatisticsExecutor(final HeapHistogramService heapHistogramService,
                                    final InternalStatisticsReceiver internalStatisticsReceiver,
                                    final NodeCache nodeCache) {
        this.heapHistogramService = heapHistogramService;
        this.internalStatisticsReceiver = internalStatisticsReceiver;
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
        final Map.Entry<String, String> nodeTag = Maps.immutableEntry(TAG_NAME_NODE, nodeName);

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
                                       final Function<HeapHistogramService.HeapHistogramEntry, InternalStatisticEvent> mapper,
                                       final String type) {

        List<InternalStatisticEvent> statisticEvents = heapHistogramEntries.stream()
                .map(mapper)
                .collect(Collectors.toList());

        LOGGER.info("Sending %s '%s' histogram stat events", statisticEvents.size(), type);

        internalStatisticsReceiver.putEvents(statisticEvents);
    }

    private static InternalStatisticEvent buildBytesEvent(final long statTimeMs,
                                                          final Map.Entry<String, String> nodeTag,
                                                          final HeapHistogramService.HeapHistogramEntry heapHistogramEntry) {
        return InternalStatisticEvent.createValueStat(
                INTERNAL_STAT_KEY_HEAP_HISTOGRAM_BYTES,
                statTimeMs,
                buildTags(nodeTag, heapHistogramEntry),
                (double) heapHistogramEntry.getBytes());
    }

    private static InternalStatisticEvent buildInstancesEvent(final long statTimeMs,
                                                              final Map.Entry<String, String> nodeTag,
                                                              final HeapHistogramService.HeapHistogramEntry heapHistogramEntry) {
        return InternalStatisticEvent.createValueStat(
                INTERNAL_STAT_KEY_HEAP_HISTOGRAM_INSTANCES,
                statTimeMs,
                buildTags(nodeTag, heapHistogramEntry),
                (double) heapHistogramEntry.getInstances());
    }

    private static Map<String, String> buildTags(final Map.Entry<String, String> nodeTag,
                                                final HeapHistogramService.HeapHistogramEntry heapHistogramEntry) {

      return ImmutableMap.<String, String>builder()
                .put(nodeTag)
                .put(TAG_NAME_CLASS_NAME, heapHistogramEntry.getClassName())
                .build();
    }
}
