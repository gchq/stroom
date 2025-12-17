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

package stroom.node.impl;

import stroom.node.api.NodeInfo;
import stroom.statistics.api.InternalStatisticEvent;
import stroom.statistics.api.InternalStatisticKey;
import stroom.statistics.api.InternalStatisticsReceiver;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LogUtil;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Class for running scheduled jobs to execute a jmap heap histogram and load the results into
 * the {@link InternalStatisticsReceiver}. This is for use in identifying memory issues at run time
 * by capturing a regular snapshot of both the number of instances of classes and the bytes in use.
 * <p>
 * As with all internal statistics it is reliant on the stat key being configured in stroom properties
 * (i.e. stroomCoreServerPropertyContext
 */
@SuppressWarnings("unused")
class HeapHistogramStatisticsExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(HeapHistogramStatisticsExecutor.class);

    private static final String TAG_NAME_NODE = "Node";
    static final String TAG_NAME_CLASS_NAME = "Class Name";

    private final HeapHistogramService heapHistogramService;
    private final InternalStatisticsReceiver internalStatisticsReceiver;
    private final NodeInfo nodeInfo;
    private final TaskContextFactory taskContextFactory;

    @Inject
    HeapHistogramStatisticsExecutor(final HeapHistogramService heapHistogramService,
                                    final InternalStatisticsReceiver internalStatisticsReceiver,
                                    final NodeInfo nodeInfo,
                                    final TaskContextFactory taskContextFactory) {
        this.heapHistogramService = heapHistogramService;
        this.internalStatisticsReceiver = internalStatisticsReceiver;
        this.nodeInfo = nodeInfo;
        this.taskContextFactory = taskContextFactory;
    }

    public void exec() {
        try {
            final TaskContext taskContext = taskContextFactory.current();

            LOGGER.info("Java Heap Histogram Statistics job started");
            taskContext.info(() -> "Java Heap Histogram Statistics job started");
            final Instant startTme = Instant.now();
            final List<HeapHistogramService.HeapHistogramEntry> heapHistogramEntries =
                    heapHistogramService.generateHeapHistogram();
            processHistogramEntries(heapHistogramEntries);
            LOGGER.info("Java Heap Histogram Statistics job completed in {}",
                    Duration.between(startTme, Instant.now()).toString());
            taskContext.info(() -> LogUtil.message("Java Heap Histogram Statistics job completed in {}",
                    Duration.between(startTme, Instant.now()).toString()));
        } catch (final RuntimeException e) {
            LOGGER.error("Error executing scheduled Heap Histogram job", e);
            throw e;
        }
    }

    private void processHistogramEntries(final List<HeapHistogramService.HeapHistogramEntry> heapHistogramEntries) {
        Preconditions.checkNotNull(heapHistogramService);

        final long statTimeMs = Instant.now().toEpochMilli();
        final String nodeName = nodeInfo.getThisNodeName();
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

    private void mapToStatEventAndSend(
            final List<HeapHistogramService.HeapHistogramEntry> heapHistogramEntries,
            final Function<HeapHistogramService.HeapHistogramEntry, InternalStatisticEvent> mapper,
            final String type) {

        final List<InternalStatisticEvent> statisticEvents = heapHistogramEntries.stream()
                .map(mapper)
                .collect(Collectors.toList());

        LOGGER.info("Sending {} '{}' histogram stat events", statisticEvents.size(), type);

        internalStatisticsReceiver.putEvents(statisticEvents);
    }

    private static InternalStatisticEvent buildBytesEvent(
            final long statTimeMs,
            final Map.Entry<String, String> nodeTag,
            final HeapHistogramService.HeapHistogramEntry heapHistogramEntry) {

        return InternalStatisticEvent.createValueStat(
                InternalStatisticKey.HEAP_HISTOGRAM_BYTES,
                statTimeMs,
                buildTags(nodeTag, heapHistogramEntry),
                (double) heapHistogramEntry.getBytes());
    }

    private static InternalStatisticEvent buildInstancesEvent(
            final long statTimeMs,
            final Map.Entry<String, String> nodeTag,
            final HeapHistogramService.HeapHistogramEntry heapHistogramEntry) {

        return InternalStatisticEvent.createValueStat(
                InternalStatisticKey.HEAP_HISTOGRAM_INSTANCES,
                statTimeMs,
                buildTags(nodeTag, heapHistogramEntry),
                (double) heapHistogramEntry.getInstances());
    }

    private static SortedMap<String, String> buildTags(
            final Map.Entry<String, String> nodeTag,
            final HeapHistogramService.HeapHistogramEntry heapHistogramEntry) {

        return ImmutableSortedMap.<String, String>naturalOrder()
                .put(nodeTag)
                .put(TAG_NAME_CLASS_NAME, heapHistogramEntry.getClassName())
                .build();
    }
}
