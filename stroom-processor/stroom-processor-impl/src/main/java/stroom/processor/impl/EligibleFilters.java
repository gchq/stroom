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
import stroom.processor.impl.ProcessorProfileCache.ProfileResult;
import stroom.processor.shared.ProcessorFilter;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;

/**
 * gh-5699. The filters <em>this</em> node is currently allowed to process, in priority order.
 * See PROCESSOR_WORKER_TASK_QUEUEING_DESIGN.md §3.1.
 * <p>
 * Today the master node decides what to queue on behalf of nodes it cannot identify, so it has to
 * ask whether <em>any</em> enabled node could process a filter
 * ({@code ProcessorTaskQueueManagerImpl.getMaxConcurrentTasks}, a sweep over every enabled node).
 * A node asking on its own behalf needs no sweep and no guess: it asks the profile cache one
 * question about itself. That is the actual win of decentralised claiming, and it is why this is
 * computed locally rather than served by anything.
 * <p>
 * Both inputs are already node-agnostic and DB backed - {@link PrioritisedFilters} refreshes
 * asynchronously every 10s and {@link ProcessorProfileCache} sits on {@code NodeGroupCache} - so
 * this adds no queries of its own and needs no cache of its own. A node that becomes ineligible
 * simply stops looking; nothing needs releasing, because under this design nothing was ever owned.
 */
@Singleton
public class EligibleFilters {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EligibleFilters.class);

    private final PrioritisedFilters prioritisedFilters;
    private final ProcessorProfileCache processorProfileCache;
    private final NodeInfo nodeInfo;

    @Inject
    public EligibleFilters(final PrioritisedFilters prioritisedFilters,
                           final ProcessorProfileCache processorProfileCache,
                           final NodeInfo nodeInfo) {
        this.prioritisedFilters = prioritisedFilters;
        this.processorProfileCache = processorProfileCache;
        this.nodeInfo = nodeInfo;
    }

    /**
     * @return The enabled filters this node may currently process tasks for, highest priority
     * first. Empty if a processing profile currently excludes this node from all of them.
     */
    public List<ProcessorFilter> getEligibleFilters() {
        return getEligibleFilters(Instant.now());
    }

    /**
     * Factored with "now" as a parameter for testing - profile periods are time of day based and
     * there is no injectable clock in stroom-util (same approach as #5683).
     */
    public List<ProcessorFilter> getEligibleFilters(final Instant now) {
        final String nodeName = nodeInfo.getThisNodeName();
        final List<ProcessorFilter> filters = prioritisedFilters.get();
        final List<ProcessorFilter> eligible = filters
                .stream()
                .filter(filter -> isEligible(filter, nodeName, now))
                .toList();
        LOGGER.debug(() -> "getEligibleFilters() - " + eligible.size() + "/" + filters.size()
                           + " filters are eligible on " + nodeName);
        return eligible;
    }

    /**
     * Whether this node may currently process tasks for the filter, which is a question only
     * about processing profiles. A filter's own maximum processing task count is not eligibility -
     * it bounds how many of its tasks may run at once, so it belongs to claiming, not to finding
     * work. A filter at its limit still has work this node is allowed to do later.
     */
    private boolean isEligible(final ProcessorFilter filter, final String nodeName, final Instant now) {
        final String profileName = filter.getProfileName();
        if (profileName == null) {
            // No profile means every node may process it, at any time.
            return true;
        }

        try {
            final ProfileResult profileResult = processorProfileCache.getProfile(nodeName, profileName, now);
            // Zero on either count means this node may process nothing for this filter right now,
            // whether because the profile's node group excludes it, the group is disabled, or the
            // current time falls outside the profile's periods.
            final boolean eligible = profileResult.maxNodeThreads() > 0
                                     && profileResult.maxClusterThreads() > 0;
            LOGGER.trace(() -> "isEligible() - " + filter.getFilterInfo() + " on " + nodeName
                               + ": " + eligible + " (" + profileResult + ")");
            return eligible;

        } catch (final RuntimeException e) {
            // A filter with a profile is governed by that profile alone, so an unresolvable
            // profile means no tasks for this filter rather than a fall back to the unprofiled
            // behaviour - the same call this node's assignment path already makes. It is almost
            // always a configuration error, so say so rather than silently processing nothing.
            LOGGER.error(() -> "Error getting processing profile for filter (filter=" + filter
                               + ", profileName=" + profileName + "), treating it as not eligible "
                               + "for processing on this node", e);
            return false;
        }
    }
}
