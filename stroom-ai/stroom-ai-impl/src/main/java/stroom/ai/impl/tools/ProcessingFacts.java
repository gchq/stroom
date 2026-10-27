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

package stroom.ai.impl.tools;

import stroom.docref.DocRef;
import stroom.job.api.JobStates.JobState;
import stroom.meta.shared.Meta;
import stroom.processor.api.ProcessingDiagnostics.NodeClaim;
import stroom.processor.api.ProcessingDiagnostics.Profile;
import stroom.processor.api.ProcessingDiagnostics.Settings;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.TaskStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// What [ProcessingExplainer] reads of Stroom, as the user. Each read either answers or throws
/// [stroom.util.shared.PermissionException] when the user lacks the permission it needs, which the explainer reports
/// as a check it couldn't make.
interface ProcessingFacts {

    /// @return The time now.
    Instant now();

    /// @return The processing settings.
    Settings settings();

    /// @return The cluster's nodes as the cluster state has them. Needs Manage Nodes.
    Cluster cluster();

    /// @param jobName A job's name.
    /// @return The job across the cluster, or empty if there is none. Needs Manage Jobs.
    Optional<JobState> job(String jobName);

    /// @return What each enabled node says it is claiming. Needs View System Info.
    List<NodeClaim> claims();

    /// @return Processing tasks that have outlived their lease without being reaped. Needs Manage Processors.
    int deadTasks();

    /// @param pipeline A pipeline.
    /// @return Its processor filters (not deleted). Needs Manage Processors and View on the pipeline.
    List<ProcessorFilter> pipelineFilters(DocRef pipeline);

    /// @param enabledOnly Only enabled filters whose processor is enabled (as task creation takes them), or every
    ///                    filter that isn't deleted.
    /// @param limit       The most to return.
    /// @return The processor filters the user can see, highest priority first. Needs Manage Processors.
    List<ProcessorFilter> filters(boolean enabledOnly, int limit);

    /// @param minPriority The lowest filter priority to count.
    /// @param limit       The most task rows to read.
    /// @return Created, Queued and Processing task counts for each filter of at least that priority, by filter id.
    /// Needs Manage Processors.
    Backlogs backlogs(int minPriority, int limit);

    /// @param filterId A processor filter's id.
    /// @param status   A task status.
    /// @param limit    The most to return.
    /// @return The filter's tasks in that status, oldest first. Needs Manage Processors.
    List<ProcessorTask> tasks(int filterId, TaskStatus status, int limit);

    /// @param metaId A stream's id.
    /// @return The processing tasks made for the stream. Needs Manage Processors.
    List<ProcessorTask> tasksOfStream(long metaId);

    /// @param metaId A stream's id.
    /// @return The stream, if the user can see it.
    Optional<Meta> stream(long metaId);

    /// @param feed A feed's name.
    /// @return The newest stream of the feed the user can see, if any.
    Optional<Meta> newestStream(String feed);

    /// @param metaIds Streams' ids.
    /// @return Those still locked (being written).
    List<Long> locked(List<Long> metaIds);

    /// @param filter A processor filter.
    /// @param metaId A stream's id.
    /// @return Whether the filter's expression selects the stream, as task creation would (status aside); empty if
    /// it can't be told (a reprocess or search filter).
    Optional<Boolean> selects(ProcessorFilter filter, long metaId);

    /// @param filter A processor filter with feed dependencies.
    /// @return Whether every feed it depends on has an Unlocked stream (if not, the filter waits indefinitely).
    boolean dependenciesReady(ProcessorFilter filter);

    /// @param name A processor profile's name.
    /// @return The profile as it applies now, or empty if there is none of that name. Needs Manage Processors.
    Optional<Profile> profile(String name);

    /// The cluster's nodes.
    ///
    /// @param nodes  Each node, highest priority first.
    /// @param master The master node, or null if there is none (or it isn't known yet).
    record Cluster(List<Node> nodes, String master) {

    }

    /// A node.
    ///
    /// @param name    Its name.
    /// @param enabled Whether it is enabled.
    /// @param active  Whether it answered the cluster's last ping.
    record Node(String name, boolean enabled, boolean active) {

    }

    /// Task counts by filter.
    ///
    /// @param byFilter Created, Queued and Processing counts, by filter id.
    /// @param capped   Whether more tasks exist than were read.
    record Backlogs(Map<Integer, Map<TaskStatus, Long>> byFilter, boolean capped) {

    }
}
