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

package stroom.processor.impl;

import stroom.node.api.NodeService;
import stroom.processor.api.JobNames;
import stroom.processor.shared.ProcessorClaimStatus;
import stroom.processor.shared.ProcessorTaskResource;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * gh-5699. Restores the one view of "what is this cluster about to process?" that decentralised
 * claiming otherwise destroys. See PROCESSOR_WORKER_TASK_QUEUEING_DESIGN.md §3.6.
 * <p>
 * When nodes claim for themselves no node holds the answer, and this deliberately does not appoint
 * one: <b>the aggregation happens at request time, on whichever node is asked</b>. That node fans
 * out to the enabled node set, merges, and returns. So aggregation is a property of the request
 * rather than a role a node holds, no node has standing state to keep in sync, every node answers
 * identically, and a node that cannot be reached costs one entry in the result rather than the
 * whole view.
 * <p>
 * Running with the master queue instead ({@code stroom.processor.claimTasksOnWorker} false) this
 * reports little of interest - each node's {@code claimTasksOnWorker} tells you which mode it is
 * in - and {@code ProcessorTaskQueueManagerImpl}'s own system info shows the queue.
 */
@Singleton
public class ProcessorClaimSystemInfo implements HasSystemInfo {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorClaimSystemInfo.class);

    private static final String SYSTEM_INFO_NAME = "ProcessorTaskClaiming";

    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<ProcessorTaskResource> processorTaskResourceProvider;
    private final Provider<ProcessorTaskDao> processorTaskDaoProvider;
    private final Provider<ProcessorConfig> processorConfigProvider;

    @Inject
    public ProcessorClaimSystemInfo(final Provider<NodeService> nodeServiceProvider,
                                    final Provider<ProcessorTaskResource> processorTaskResourceProvider,
                                    final Provider<ProcessorTaskDao> processorTaskDaoProvider,
                                    final Provider<ProcessorConfig> processorConfigProvider) {
        this.nodeServiceProvider = nodeServiceProvider;
        this.processorTaskResourceProvider = processorTaskResourceProvider;
        this.processorTaskDaoProvider = processorTaskDaoProvider;
        this.processorConfigProvider = processorConfigProvider;
    }

    @Override
    public String getSystemInfoName() {
        return SYSTEM_INFO_NAME;
    }

    @Override
    public SystemInfoResult getSystemInfo() {
        final List<ProcessorClaimStatus> statuses = new ArrayList<>();
        for (final String nodeName : nodeServiceProvider.get().getEnabledNodesByPriority()) {
            try {
                statuses.add(processorTaskResourceProvider.get().getClaimStatus(nodeName));
            } catch (final RuntimeException e) {
                // One unreachable node must degrade the view, not remove it.
                LOGGER.debug(() -> "Unable to get processor claim status from node " + nodeName, e);
                statuses.add(ProcessorClaimStatus.error(nodeName, e.getMessage()));
            }
        }

        final Map<String, Object> nodes = new LinkedHashMap<>();
        long tasksClaimed = 0;
        long claimAttempts = 0;
        long emptyClaims = 0;
        int tasksInFlight = 0;
        int unreachable = 0;
        for (final ProcessorClaimStatus status : statuses) {
            nodes.put(status.getNodeName(), toDetail(status));
            if (status.getError() != null) {
                unreachable++;
            } else {
                tasksClaimed += status.getTasksClaimed();
                claimAttempts += status.getClaimAttempts();
                emptyClaims += status.getEmptyClaims();
                tasksInFlight += status.getTasksInFlight();
            }
        }

        // Asked once by whichever node is serving this request, not once per node: it is a
        // cluster wide number and N identical queries would say nothing extra.
        final int deadTaskCount = countDeadTasks();

        return SystemInfoResult.builder(this)
                .description("What each node is doing about finding and claiming its own processor tasks")
                .addDetail("nodes", nodes)
                .addDetail("deadTaskCount", deadTaskCount)
                .addDetail("deadTaskWarning", deadTaskCount == 0
                        ? null
                        : deadTaskCount + " tasks have not had their heartbeat renewed within "
                          + "stroom.processor.taskLeaseTimeout and have not been recovered. Check that the '"
                          + JobNames.PROCESSOR_TASK_REAPER + "' job is enabled and running; while it is not, "
                          + "the tasks of any node that dies are never picked up by another node.")
                .addDetail("totalTasksInFlight", tasksInFlight)
                .addDetail("totalTasksClaimed", tasksClaimed)
                .addDetail("totalClaimAttempts", claimAttempts)
                .addDetail("totalEmptyClaims", emptyClaims)
                .addDetail("unreachableNodeCount", unreachable)
                .build();
    }

    /**
     * How many tasks the reaper should have taken but has not. Non zero for longer than a job
     * interval means dead task recovery is not happening.
     */
    private int countDeadTasks() {
        try {
            return processorTaskDaoProvider.get().countDeadTasks(
                    Instant.now().minus(processorConfigProvider.get().getTaskLeaseTimeout()));
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            return 0;
        }
    }

    private Map<String, Object> toDetail(final ProcessorClaimStatus status) {
        final Map<String, Object> detail = new LinkedHashMap<>();
        if (status.getError() != null) {
            detail.put("error", status.getError());
            return detail;
        }
        detail.put("claimTasksOnWorker", status.isClaimTasksOnWorker());
        detail.put("eligibleFilterCount", status.getEligibleFilterCount());
        detail.put("filtersWithWorkCount", status.getFiltersWithWorkCount());
        detail.put("summaryAgeMs", status.getSummaryAgeMs());
        detail.put("backedOffFilterCount", status.getBackedOffFilterCount());
        detail.put("claimAttempts", status.getClaimAttempts());
        detail.put("tasksClaimed", status.getTasksClaimed());
        detail.put("emptyClaims", status.getEmptyClaims());
        detail.put("lockedMetaReleases", status.getLockedMetaReleases());
        detail.put("tasksInFlight", status.getTasksInFlight());
        detail.put("lastHeartbeatAgeMs", status.getLastHeartbeatAgeMs());
        return detail;
    }
}
