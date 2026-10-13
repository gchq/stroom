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

package stroom.processor.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * gh-5699. What one node is doing about finding and claiming its own processor tasks.
 * <p>
 * Decentralised claiming has no single place that knows what the cluster is about to process -
 * that is the point of it - so this exists to be collected from every node at the moment somebody
 * asks, standing in for the {@code filterQueues} view that only the master could offer.
 * See PROCESSOR_WORKER_TASK_QUEUEING_DESIGN.md §3.6.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class ProcessorClaimStatus {

    @JsonProperty
    private final String nodeName;
    /**
     * Which mode this node is running in. Every node must agree; a node reporting a different
     * value from its peers is misconfigured and will not process the same work they do.
     */
    @JsonProperty
    private final boolean claimTasksOnWorker;
    @JsonProperty
    private final int eligibleFilterCount;
    @JsonProperty
    private final int filtersWithWorkCount;
    /**
     * How old the availability summary this node is working from is, or null if it has not taken
     * one yet. Much older than the configured interval means nothing is asking for work.
     */
    @JsonProperty
    private final Long summaryAgeMs;
    @JsonProperty
    private final int backedOffFilterCount;
    @JsonProperty
    private final long claimAttempts;
    @JsonProperty
    private final long tasksClaimed;
    /**
     * Claims that returned nothing. Should be rare: SKIP LOCKED gives concurrent nodes distinct
     * rows, so anything other than rare means an assumption in §3.3 of the design is wrong.
     */
    @JsonProperty
    private final long emptyClaims;
    @JsonProperty
    private final long lockedMetaReleases;
    @JsonProperty
    private final int tasksInFlight;
    @JsonProperty
    private final long lastHeartbeatAgeMs;
    /**
     * Why this node could not be asked, if it could not. Populated by whichever node is
     * aggregating, so that one unreachable node gives a partial view rather than no view.
     */
    @JsonProperty
    private final String error;

    @JsonCreator
    public ProcessorClaimStatus(@JsonProperty("nodeName") final String nodeName,
                                @JsonProperty("claimTasksOnWorker") final Boolean claimTasksOnWorker,
                                @JsonProperty("eligibleFilterCount") final Integer eligibleFilterCount,
                                @JsonProperty("filtersWithWorkCount") final Integer filtersWithWorkCount,
                                @JsonProperty("summaryAgeMs") final Long summaryAgeMs,
                                @JsonProperty("backedOffFilterCount") final Integer backedOffFilterCount,
                                @JsonProperty("claimAttempts") final Long claimAttempts,
                                @JsonProperty("tasksClaimed") final Long tasksClaimed,
                                @JsonProperty("emptyClaims") final Long emptyClaims,
                                @JsonProperty("lockedMetaReleases") final Long lockedMetaReleases,
                                @JsonProperty("tasksInFlight") final Integer tasksInFlight,
                                @JsonProperty("lastHeartbeatAgeMs") final Long lastHeartbeatAgeMs,
                                @JsonProperty("error") final String error) {
        this.nodeName = nodeName;
        this.claimTasksOnWorker = Objects.requireNonNullElse(claimTasksOnWorker, false);
        this.eligibleFilterCount = Objects.requireNonNullElse(eligibleFilterCount, 0);
        this.filtersWithWorkCount = Objects.requireNonNullElse(filtersWithWorkCount, 0);
        this.summaryAgeMs = summaryAgeMs;
        this.backedOffFilterCount = Objects.requireNonNullElse(backedOffFilterCount, 0);
        this.claimAttempts = Objects.requireNonNullElse(claimAttempts, 0L);
        this.tasksClaimed = Objects.requireNonNullElse(tasksClaimed, 0L);
        this.emptyClaims = Objects.requireNonNullElse(emptyClaims, 0L);
        this.lockedMetaReleases = Objects.requireNonNullElse(lockedMetaReleases, 0L);
        this.tasksInFlight = Objects.requireNonNullElse(tasksInFlight, 0);
        this.lastHeartbeatAgeMs = Objects.requireNonNullElse(lastHeartbeatAgeMs, 0L);
        this.error = error;
    }

    public static ProcessorClaimStatus error(final String nodeName, final String error) {
        return new ProcessorClaimStatus(nodeName, false, 0, 0, null, 0, 0L, 0L, 0L, 0L, 0, 0L, error);
    }

    public String getNodeName() {
        return nodeName;
    }

    public boolean isClaimTasksOnWorker() {
        return claimTasksOnWorker;
    }

    public int getEligibleFilterCount() {
        return eligibleFilterCount;
    }

    public int getFiltersWithWorkCount() {
        return filtersWithWorkCount;
    }

    public Long getSummaryAgeMs() {
        return summaryAgeMs;
    }

    public int getBackedOffFilterCount() {
        return backedOffFilterCount;
    }

    public long getClaimAttempts() {
        return claimAttempts;
    }

    public long getTasksClaimed() {
        return tasksClaimed;
    }

    public long getEmptyClaims() {
        return emptyClaims;
    }

    public long getLockedMetaReleases() {
        return lockedMetaReleases;
    }

    public int getTasksInFlight() {
        return tasksInFlight;
    }

    public long getLastHeartbeatAgeMs() {
        return lastHeartbeatAgeMs;
    }

    public String getError() {
        return error;
    }

    @Override
    public String toString() {
        return "ProcessorClaimStatus{" +
               "nodeName='" + nodeName + '\'' +
               ", claimTasksOnWorker=" + claimTasksOnWorker +
               ", eligibleFilterCount=" + eligibleFilterCount +
               ", filtersWithWorkCount=" + filtersWithWorkCount +
               ", summaryAgeMs=" + summaryAgeMs +
               ", backedOffFilterCount=" + backedOffFilterCount +
               ", claimAttempts=" + claimAttempts +
               ", tasksClaimed=" + tasksClaimed +
               ", emptyClaims=" + emptyClaims +
               ", lockedMetaReleases=" + lockedMetaReleases +
               ", tasksInFlight=" + tasksInFlight +
               ", lastHeartbeatAgeMs=" + lastHeartbeatAgeMs +
               ", error='" + error + '\'' +
               '}';
    }
}
