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

package stroom.node.server;

import org.springframework.stereotype.Component;
import stroom.node.shared.RecordCountService;
import stroom.statistics.common.StatisticEvent;
import stroom.statistics.common.StatisticTag;
import stroom.util.io.StreamUtil;

import javax.inject.Inject;
import java.io.FileInputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to split out the query of the node status in a separate read
 * only transaction so it queries the slave node.
 */
@Component
public class NodeStatusServiceUtil {
    private final NodeCache nodeCache;
    private final RecordCountService recordCountService;

    private long time = System.currentTimeMillis();

    private CPUStats previousCPUStats;

    @Inject
    public NodeStatusServiceUtil(final NodeCache nodeCache, final RecordCountService recordCountService) {
        this.nodeCache = nodeCache;
        this.recordCountService = recordCountService;
    }

    /**
     * Read the stats from a line.
     */
    public CPUStats createLinuxStats(String lines) {
        if (lines == null) {
            return null;
        }
        Pattern pattern = Pattern.compile("[0-9]+");
        Matcher matcher = pattern.matcher(lines);
        CPUStats statLine = new CPUStats();
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

        int btimeStart = lines.indexOf("btime");
        matcher = pattern.matcher(lines.substring(btimeStart));
        matcher.find();

        return statLine;
    }

    /**
     * Linux dependent call.
     */
    protected String readSystemStatsInfo() {
        try {
            return StreamUtil.streamToString(new FileInputStream("/proc/stat"));
        } catch (Exception ex) {
            return null;
        }
    }

    private StatisticEvent buildStatisticEvent(String stat, long timeMs, StatisticTag nodeTag, String type, double value) {
        // These stat events are being generated every minute so use a precision
        // of 60s
        final StatisticTag typeTag = new StatisticTag("Type", type);
        return new StatisticEvent(timeMs, stat, Arrays.asList(nodeTag, typeTag), value);
    }

    public List<StatisticEvent> buildNodeStatus() {
        List<StatisticEvent> statisticEventList = new ArrayList<>();

        final StatisticTag nodeTag = new StatisticTag("Node", nodeCache.getDefaultNode().getName());

        final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        final MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        final MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();

        long now = System.currentTimeMillis();

        final long heapUsed = heapUsage.getUsed();
        final long heapComitted = heapUsage.getCommitted();
        final long heapMax = heapUsage.getMax();
        final long nonHeapUsed = nonHeapUsage.getUsed();
        final long nonHeapComitted = nonHeapUsage.getCommitted();
        final long nonHeapMax = nonHeapUsage.getMax();

        if (heapUsed > 0) {
            statisticEventList.add(buildStatisticEvent("Memory", now, nodeTag, "Heap Used", heapUsed));
        }
        if (heapComitted > 0) {
            statisticEventList.add(buildStatisticEvent("Memory", now, nodeTag, "Heap Committed", heapComitted));
        }
        if (heapMax > 0) {
            statisticEventList.add(buildStatisticEvent("Memory", now, nodeTag, "Heap Max", heapMax));
        }
        if (nonHeapUsed > 0) {
            statisticEventList.add(buildStatisticEvent("Memory", now, nodeTag, "Non Heap Used", nonHeapUsed));
        }
        if (nonHeapComitted > 0) {
            statisticEventList.add(buildStatisticEvent("Memory", now, nodeTag, "Non Heap Committed", nonHeapComitted));
        }
        if (nonHeapMax > 0) {
            statisticEventList.add(buildStatisticEvent("Memory", now, nodeTag, "Non Heap Max", nonHeapMax));
        }

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

                statisticEventList.add(buildStatisticEvent("CPU", now, nodeTag, "Idle (%)", ((diff.idle * inc)) / 10F));
                statisticEventList.add(buildStatisticEvent("CPU", now, nodeTag, "IO Wait (%)", ((diff.ioWait * inc)) / 10F));
                statisticEventList.add(buildStatisticEvent("CPU", now, nodeTag, "User (%)", ((diff.user * inc)) / 10F));
                statisticEventList.add(buildStatisticEvent("CPU", now, nodeTag, "Irq (%)", ((diff.irq * inc)) / 10F));
                statisticEventList.add(buildStatisticEvent("CPU", now, nodeTag, "Soft Irq (%)", ((diff.softirq * inc)) / 10F));
                statisticEventList.add(buildStatisticEvent("CPU", now, nodeTag, "System (%)", ((diff.system * inc)) / 10F));
                statisticEventList.add(buildStatisticEvent("CPU", now, nodeTag, "Nice (%)", ((diff.nice * inc)) / 10F));
                statisticEventList.add(buildStatisticEvent("CPU", now, nodeTag, "Total (%)", ((diff.getTotal() * inc)) / 10F));
            }

            // Calculate the eps values.
            if (recordCountService != null) {
                final double read = recordCountService.getAndResetRead();
                final double written = recordCountService.getAndResetWritten();
                final long readEps = (long) (read / duration);
                final long writeEps = (long) (written / duration);

                statisticEventList.add(buildStatisticEvent("EPS", now, nodeTag, "Read", readEps));
                statisticEventList.add(buildStatisticEvent("EPS", now, nodeTag, "Write", writeEps));
            }
        }

        previousCPUStats = cpuStats;

        return statisticEventList;
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
            CPUStats rtn = new CPUStats();
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
