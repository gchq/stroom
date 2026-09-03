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

import stroom.node.api.NodeInfo;
import stroom.processor.impl.ProcessorTaskAvailability.SummaryInfo;
import stroom.processor.impl.ProcessorTaskClaimer.ClaimStats;
import stroom.processor.shared.ProcessorClaimStatus;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * gh-5699. Builds this node's answer to "what are you doing about finding work?".
 * <p>
 * Everything here is read from in-memory state that the claim path already keeps, so asking costs
 * nothing and, importantly, changes nothing: the availability summary is reported as it stands
 * rather than refreshed, because a diagnostic that perturbs what it measures is worse than no
 * diagnostic. A cluster-wide database query would be a cheaper way to get some of these numbers,
 * but it cannot see summary age or claim win rate, which are the ones that say whether this node
 * is working properly.
 */
@Singleton
public class ProcessorClaimStatusFactory {

    private final NodeInfo nodeInfo;
    private final EligibleFilters eligibleFilters;
    private final ProcessorTaskAvailability processorTaskAvailability;
    private final ProcessorTaskClaimer processorTaskClaimer;
    private final ProcessorTaskHeartbeat processorTaskHeartbeat;
    private final FilterFetchBackoff filterFetchBackoff;
    private final Provider<ProcessorConfig> processorConfigProvider;

    @Inject
    public ProcessorClaimStatusFactory(final NodeInfo nodeInfo,
                                       final EligibleFilters eligibleFilters,
                                       final ProcessorTaskAvailability processorTaskAvailability,
                                       final ProcessorTaskClaimer processorTaskClaimer,
                                       final ProcessorTaskHeartbeat processorTaskHeartbeat,
                                       final FilterFetchBackoff filterFetchBackoff,
                                       final Provider<ProcessorConfig> processorConfigProvider) {
        this.nodeInfo = nodeInfo;
        this.eligibleFilters = eligibleFilters;
        this.processorTaskAvailability = processorTaskAvailability;
        this.processorTaskClaimer = processorTaskClaimer;
        this.processorTaskHeartbeat = processorTaskHeartbeat;
        this.filterFetchBackoff = filterFetchBackoff;
        this.processorConfigProvider = processorConfigProvider;
    }

    public ProcessorClaimStatus getStatus() {
        final long nowMs = System.currentTimeMillis();
        final boolean claimTasksOnWorker = processorConfigProvider.get().isClaimTasksOnWorker();
        final Optional<SummaryInfo> summaryInfo = processorTaskAvailability.getSummaryInfo();
        final ClaimStats claimStats = processorTaskClaimer.getClaimStats();

        return new ProcessorClaimStatus(
                nodeInfo.getThisNodeName(),
                claimTasksOnWorker,
                // Only worth computing in the mode that uses it: resolving every filter's profile
                // is real work, and an unresolvable one is reported as an error by the code that
                // actually needs the answer. Reading a diagnostic should not do either.
                claimTasksOnWorker
                        ? eligibleFilters.getEligibleFilters().size()
                        : 0,
                summaryInfo.map(info -> info.availability().size()).orElse(0),
                summaryInfo.map(info -> nowMs - info.takenMs()).orElse(null),
                filterFetchBackoff.size(),
                claimStats.claimAttempts(),
                claimStats.tasksClaimed(),
                claimStats.emptyClaims(),
                claimStats.lockedMetaReleases(),
                claimStats.tasksInFlight(),
                nowMs - processorTaskHeartbeat.getLastRenewalSuccessMs(),
                null);
    }
}
