/*
 * Copyright 2016 Crown Copyright
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
import stroom.pipeline.refdata.store.offheapstore.DelegatingRefDataOffHeapStore;
import stroom.pipeline.refdata.store.offheapstore.RefDataOffHeapStore;
import stroom.pipeline.state.RecordCountService;
import stroom.query.common.v2.DataStoreFactory;
import stroom.query.common.v2.DataStoreFactory.StoreSizeSummary;
import stroom.statistics.api.InternalStatisticEvent;
import stroom.statistics.api.InternalStatisticKey;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import com.google.common.collect.ImmutableSortedMap;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to split out the query of the node status in a separate read
 * only transaction so it queries the slave node.
 */
@Singleton
class NodeStatusServiceUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(NodeStatusServiceUtil.class);

    public static final String TAG_TYPE = "Type";
    public static final String TAG_NODE = "Node";
    public static final String TAG_FEED = "Feed";

    private final NodeInfo nodeInfo;
    private final RecordCountService recordCountService;
    private final DelegatingRefDataOffHeapStore delegatingRefDataOffHeapStore;
    private final DataStoreFactory dataStoreFactory;

    private long time = System.currentTimeMillis();

    private CPUStats previousCPUStats;

    @Inject
    NodeStatusServiceUtil(final NodeInfo nodeInfo,
                          final RecordCountService recordCountService,
                          final DelegatingRefDataOffHeapStore delegatingRefDataOffHeapStore,
                          final DataStoreFactory dataStoreFactory) {
        this.nodeInfo = nodeInfo;
        this.recordCountService = recordCountService;
        this.delegatingRefDataOffHeapStore = delegatingRefDataOffHeapStore;
        this.dataStoreFactory = dataStoreFactory;
    }

    /**
     * Read the stats from a line.
     */
    static CPUStats createLinuxStats(final String lines) {
        if (lines == null) {
            return null;
        }
        final Pattern pattern = Pattern.compile("[0-9]+");
        Matcher matcher = pattern.matcher(lines);
        final CPUStats statLine = new CPUStats();
        matcher.find();
        statLine.user = Long.valueOf(matcher.group());
        matcher.find();
        statLine.nice = Long.valueOf(matcher.group());
        matcher.find();
        statLine.system = Long.valueOf(matcher.group());
        matcher.find();
        statLine.idle = Long.valueOf(matcher.group());
        matcher.find();
        statLine.ioWait = Long.valueOf(matcher.group());
        matcher.find();
        statLine.irq = Long.valueOf(matcher.group());
        matcher.find();
        statLine.softirq = Long.valueOf(matcher.group());

        final int btimeStart = lines.indexOf("btime");
        matcher = pattern.matcher(lines.substring(btimeStart));
        matcher.find();

        return statLine;
    }

    /**
     * Linux dependent call.
     */
    protected String readSystemStatsInfo() {
        try {
            return StreamUtil.fileToString(Paths.get("/proc/stat"));
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private InternalStatisticEvent buildStatisticEvent(final InternalStatisticKey key,
                                                       final long timeMs,
                                                       final SortedMap<String, String> tags,
                                                       final String typeTagValue,
                                                       final double value) {
        // These stat events are being generated every minute so use a precision
        // of 60s
        final SortedMap<String, String> newTags = new TreeMap<>(tags);
        newTags.put(TAG_TYPE, typeTagValue);
        return InternalStatisticEvent.createValueStat(key, timeMs, newTags, value);
    }

    public List<InternalStatisticEvent> buildNodeStatus() {
        final List<InternalStatisticEvent> statisticEventList = new ArrayList<>();

        final SortedMap<String, String> tags = ImmutableSortedMap.of(
                TAG_NODE, nodeInfo.getThisNodeName());
        final long nowEpochMs = System.currentTimeMillis();

        // TODO we prob ought to do these concurrently as some are having to hit the file system
        //  to find out sizes. Also consider whether each should get the current time, in case
        //  there is any delay in them running.
        buildJavaMemoryStats(statisticEventList, tags, nowEpochMs);
        buildCpuStatEvents(statisticEventList, tags);
        buildRefDataStats(statisticEventList, tags, nowEpochMs);
        buildSearchResultStoresStats(statisticEventList, tags, nowEpochMs);

        return statisticEventList;
    }

    private void buildJavaMemoryStats(final List<InternalStatisticEvent> statisticEventList,
                                      final SortedMap<String, String> tags,
                                      final long nowEpochMs) {
        final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        final MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        final MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();

        final BiConsumer<String, Long> statAdder = (type, val) -> {
            if (val != null && val > 0) {
                statisticEventList.add(buildStatisticEvent(
                        InternalStatisticKey.MEMORY,
                        nowEpochMs,
                        tags,
                        type,
                        val));
            }
        };

        statAdder.accept("Heap Used", heapUsage.getUsed());
        statAdder.accept("Heap Committed", heapUsage.getCommitted());
        statAdder.accept("Heap Max", heapUsage.getMax());
        statAdder.accept("Non Heap Used", nonHeapUsage.getUsed());
        statAdder.accept("Non Heap Committed", nonHeapUsage.getCommitted());
        statAdder.accept("Non Heap Max", nonHeapUsage.getMax());

        final List<BufferPoolMXBean> platformMXBeans = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);

        // Total mem used for off-heap direct byte buffers
        platformMXBeans.stream()
                .filter(pool -> "direct".equalsIgnoreCase(pool.getName()))
                .findAny()
                .ifPresent(bufferPoolMXBean -> {
                    statAdder.accept("Direct Buffers Used", bufferPoolMXBean.getMemoryUsed());
                });

        // Total mem used for off-heap mapped byte buffers (e.g. mmapped files)
        platformMXBeans.stream()
                .filter(pool -> "mapped".equalsIgnoreCase(pool.getName()))
                .findAny()
                .ifPresent(bufferPoolMXBean -> {
                    statAdder.accept("Mapped Buffers Used", bufferPoolMXBean.getMemoryUsed());
                });

        if (ManagementFactory.getOperatingSystemMXBean() instanceof
                final com.sun.management.OperatingSystemMXBean operatingSystem) {
            statAdder.accept("OS Committed Virtual Memory", operatingSystem.getCommittedVirtualMemorySize());
            statAdder.accept("OS Free Memory Size", operatingSystem.getFreeMemorySize());
            statAdder.accept("OS Free Swap Space", operatingSystem.getFreeSwapSpaceSize());
        }
    }

    private void buildRefDataStats(final List<InternalStatisticEvent> statisticEventList,
                                   final SortedMap<String, String> tags,
                                   final long nowEpochMs) {

        final Map<String, RefDataOffHeapStore> feedNameToStoreMap = delegatingRefDataOffHeapStore
                .getFeedNameToStoreMap();

        if (NullSafe.hasEntries(feedNameToStoreMap)) {
            feedNameToStoreMap.forEach((feedName, feedSpecificStore) -> {
                final SortedMap<String, String> allTags = new TreeMap<>(tags);
                allTags.put(TAG_FEED, feedName);

                try {
                    LOGGER.debug("Capturing ref store stats for feed: {}", feedName);
                    buildRefDataStats(feedSpecificStore, statisticEventList, allTags, nowEpochMs);
                } catch (final Exception e) {
                    LOGGER.error("Error getting stats from ref store with feedName: {}", feedName, e);
                }
            });
        } else {
            LOGGER.debug("No feed specific stores");
        }
    }

    private void buildRefDataStats(final RefDataOffHeapStore feedSpecificStore,
                                   final List<InternalStatisticEvent> statisticEventList,
                                   final SortedMap<String, String> tags,
                                   final long nowEpochMs) {

        // No point in holding a load of zeros, e.g. nodes not running processing
        final long sizeOnDisk = feedSpecificStore.getSizeOnDisk();
        final long combinedEntryCount = feedSpecificStore.getKeyValueEntryCount()
                                        + feedSpecificStore.getRangeValueEntryCount();
        final long processingInfoEntryCount = feedSpecificStore.getProcessingInfoEntryCount();

        if (sizeOnDisk > 0) {
            statisticEventList.add(InternalStatisticEvent.createValueStat(
                    InternalStatisticKey.REF_DATA_STORE_SIZE,
                    nowEpochMs,
                    tags,
                    sizeOnDisk));
        }

        if (combinedEntryCount > 0) {
            statisticEventList.add(InternalStatisticEvent.createValueStat(
                    InternalStatisticKey.REF_DATA_STORE_ENTRY_COUNT,
                    nowEpochMs,
                    tags,
                    combinedEntryCount));
        }

        if (processingInfoEntryCount > 0) {
            statisticEventList.add(InternalStatisticEvent.createValueStat(
                    InternalStatisticKey.REF_DATA_STORE_STREAM_COUNT,
                    nowEpochMs,
                    tags,
                    processingInfoEntryCount));
        }
    }

    private void buildSearchResultStoresStats(final List<InternalStatisticEvent> statisticEventList,
                                              final SortedMap<String, String> tags,
                                              final long nowEpochMs) {
        final StoreSizeSummary storeSizeSummary = dataStoreFactory.getTotalSizeOnDisk();

        // No point in holding a load of zeros for the times when no searches are running
        if (storeSizeSummary.getStoreCount() > 0) {
            statisticEventList.add(InternalStatisticEvent.createValueStat(
                    InternalStatisticKey.SEARCH_RESULTS_STORE_SIZE,
                    nowEpochMs,
                    tags,
                    storeSizeSummary.getTotalSizeOnDisk()));
        }

        if (storeSizeSummary.getTotalSizeOnDisk() > 0) {
            statisticEventList.add(InternalStatisticEvent.createValueStat(
                    InternalStatisticKey.SEARCH_RESULTS_STORE_COUNT,
                    nowEpochMs,
                    tags,
                    storeSizeSummary.getStoreCount()));
        }
    }

    private void buildCpuStatEvents(final List<InternalStatisticEvent> statisticEventList,
                                    final SortedMap<String, String> tags) {
        final long now;
        // Get the current CPU stats.
        final CPUStats cpuStats = createLinuxStats(readSystemStatsInfo());

        // Get the elapsed time in seconds.
        now = System.currentTimeMillis();
        final double duration = (now - time) / 1000D;
        time = now;

        if (duration > 0) {
            // OS dependent code :(
            if (cpuStats != null && previousCPUStats != null) {
                final CPUStats diff = cpuStats.subtract(previousCPUStats);
                final double inc = 1000D / diff.getTotal();

                statisticEventList.add(buildStatisticEvent(InternalStatisticKey.CPU,
                        now,
                        tags,
                        "Idle (%)",
                        ((diff.idle * inc)) / 10F));
                statisticEventList.add(buildStatisticEvent(InternalStatisticKey.CPU,
                        now,
                        tags,
                        "IO Wait (%)",
                        ((diff.ioWait * inc)) / 10F));
                statisticEventList.add(buildStatisticEvent(InternalStatisticKey.CPU,
                        now,
                        tags,
                        "User (%)",
                        ((diff.user * inc)) / 10F));
                statisticEventList.add(buildStatisticEvent(InternalStatisticKey.CPU,
                        now,
                        tags,
                        "Irq (%)",
                        ((diff.irq * inc)) / 10F));
                statisticEventList.add(buildStatisticEvent(InternalStatisticKey.CPU,
                        now,
                        tags,
                        "Soft Irq (%)",
                        ((diff.softirq * inc)) / 10F));
                statisticEventList.add(buildStatisticEvent(InternalStatisticKey.CPU,
                        now,
                        tags,
                        "System (%)",
                        ((diff.system * inc)) / 10F));
                statisticEventList.add(buildStatisticEvent(InternalStatisticKey.CPU,
                        now,
                        tags,
                        "Nice (%)",
                        ((diff.nice * inc)) / 10F));
                statisticEventList.add(buildStatisticEvent(InternalStatisticKey.CPU,
                        now,
                        tags,
                        "Total (%)",
                        ((diff.getTotal() * inc)) / 10F));
            }

            // Calculate the eps values.
            if (recordCountService != null) {
                final double read = recordCountService.getAndResetRead();
                final double written = recordCountService.getAndResetWritten();
                final long readEps = (long) (read / duration);
                final long writeEps = (long) (written / duration);

                statisticEventList.add(buildStatisticEvent(InternalStatisticKey.EVENTS_PER_SECOND,
                        now,
                        tags,
                        "Read",
                        readEps));
                statisticEventList.add(buildStatisticEvent(InternalStatisticKey.EVENTS_PER_SECOND,
                        now,
                        tags,
                        "Write",
                        writeEps));
            }
        }

        previousCPUStats = cpuStats;
    }

    static class CPUStats {

        public Long user;
        public Long nice;
        public Long system;
        public Long idle;
        public Long ioWait;
        public Long irq;
        public Long softirq;

        CPUStats subtract(final CPUStats cpuStats) {
            final CPUStats rtn = new CPUStats();
            rtn.user = this.user - cpuStats.user;
            rtn.nice = this.nice - cpuStats.nice;
            rtn.system = this.system - cpuStats.system;
            rtn.idle = this.idle - cpuStats.idle;
            rtn.ioWait = this.ioWait - cpuStats.ioWait;
            rtn.irq = this.irq - cpuStats.irq;
            rtn.softirq = this.softirq - cpuStats.softirq;
            return rtn;
        }

        Long getTotal() {
            return user + nice + system + idle + ioWait + irq + softirq;
        }
    }
}
