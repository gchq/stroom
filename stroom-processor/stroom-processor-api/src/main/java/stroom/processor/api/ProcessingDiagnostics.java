/*
 * Copyright 2026 Crown Copyright
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

package stroom.processor.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Read-only views of the state that decides whether processor filters get processed: the processing settings,
/// processor profiles as they apply now, and what each node is doing about claiming work. For explaining why
/// processing isn't happening; nothing here changes any state.
public interface ProcessingDiagnostics {

    /// @return The settings that decide how tasks are created, queued and claimed.
    Settings getSettings();

    /// A processor profile as it applies at a time: its node group and periods, and the thread limits each node
    /// gets from it, worked out as task assignment works them out.
    ///
    /// @param name The profile's name, as a processor filter names it.
    /// @param now  The time to work the limits out for.
    /// @return The profile, or empty if there is none of that name.
    /// @throws stroom.util.shared.PermissionException without the Manage Processors permission.
    Optional<Profile> getProfile(String name, Instant now);

    /// What each enabled node says it is doing about claiming tasks, asked of every node in parallel.
    ///
    /// @param timeout How long to wait for the nodes; a node that hasn't answered by then is reported with an error.
    /// @return One entry per enabled node, in priority order.
    /// @throws stroom.util.shared.PermissionException without the View System Info permission.
    List<NodeClaim> getClaimStatus(Duration timeout);

    /// @return How many processing tasks have gone longer than the task lease timeout without a heartbeat and have
    /// not been reaped: non-zero for longer than a minute means dead task recovery isn't happening.
    /// @throws stroom.util.shared.PermissionException without the Manage Processors permission.
    int getDeadTaskCount();

    /// The processing settings (`stroom.processor.*`).
    ///
    /// @param claimTasksOnWorker            Whether nodes claim their own tasks (`claimTasksOnWorker`), rather than
    ///                                      the master queueing and assigning them.
    /// @param assignTasks                   Whether the master assigns tasks (`assignTasks`).
    /// @param fillTaskQueue                 Whether the master fills its task queue (`fillTaskQueue`).
    /// @param createTasksBeyondProcessLimit Whether task creation ignores profile and filter limits.
    /// @param useMaxMetaIdFromPreviousPoll  Whether a filter's first poll only records where to start.
    /// @param tasksToCreate                 The task creation budget for each profile per run.
    /// @param queueSize                     The master's task queue size.
    /// @param taskLeaseTimeout              How long a processing task may go without a heartbeat.
    /// @param skipNonProducingMax           The longest a filter that found nothing waits before polling again.
    public record Settings(boolean claimTasksOnWorker,
                           boolean assignTasks,
                           boolean fillTaskQueue,
                           boolean createTasksBeyondProcessLimit,
                           boolean useMaxMetaIdFromPreviousPoll,
                           int tasksToCreate,
                           int queueSize,
                           Duration taskLeaseTimeout,
                           Duration skipNonProducingMax) {

    }

    /// A processor profile as it applies at a time.
    ///
    /// @param name             The profile's name.
    /// @param nodeGroupName    The node group it runs on.
    /// @param nodeGroupFound   Whether that node group exists (if not, filters on the profile get no tasks).
    /// @param nodeGroupEnabled Whether the node group is enabled (if not, its nodes get no threads).
    /// @param timeZone         The time zone its periods are in, as configured.
    /// @param periods          Its periods.
    /// @param nodeLimits       The thread limits each enabled node gets from it now, by node name; empty when the
    ///                         node group wasn't found.
    public record Profile(String name,
                          String nodeGroupName,
                          boolean nodeGroupFound,
                          boolean nodeGroupEnabled,
                          String timeZone,
                          List<Period> periods,
                          Map<String, Limits> nodeLimits) {

    }

    /// A profile period: days and a time window, and the thread limits that apply within it.
    ///
    /// @param days              The days it applies on (Monday ... Sunday).
    /// @param startTime         Its start time, `HH:mm:ss`.
    /// @param endTime           Its end time, `HH:mm:ss`.
    /// @param overnight         Whether it ends at or before its start time, running past midnight.
    /// @param maxNodeThreads    The most threads on each node, or null when not limited.
    /// @param maxClusterThreads The most threads across the cluster, or null when not limited.
    public record Period(List<String> days,
                         String startTime,
                         String endTime,
                         boolean overnight,
                         Integer maxNodeThreads,
                         Integer maxClusterThreads) {

    }

    /// Thread limits; [Integer#MAX_VALUE] when not limited, 0 when the node gets none.
    ///
    /// @param maxNodeThreads    The most threads on the node.
    /// @param maxClusterThreads The most threads across the cluster.
    public record Limits(int maxNodeThreads, int maxClusterThreads) {

    }

    /// What a node says it is doing about claiming tasks.
    ///
    /// @param node                The node.
    /// @param claimTasksOnWorker  Whether it claims its own tasks; null if it couldn't be asked.
    /// @param eligibleFilters     Filters it may claim for (worker claiming only).
    /// @param filtersWithWork     Filters it last saw with tasks waiting.
    /// @param backedOffFilters    Filters it is backing off from after finding nothing.
    /// @param tasksInFlight       Tasks it is processing.
    /// @param lockedMetaReleases  Tasks it gave back because their stream was still locked.
    /// @param lastHeartbeatAgeMs  How long since it last renewed its tasks' heartbeats, or null.
    /// @param error               Why it couldn't be asked, or null.
    public record NodeClaim(String node,
                            Boolean claimTasksOnWorker,
                            int eligibleFilters,
                            int filtersWithWork,
                            int backedOffFilters,
                            int tasksInFlight,
                            long lockedMetaReleases,
                            Long lastHeartbeatAgeMs,
                            String error) {

    }
}
