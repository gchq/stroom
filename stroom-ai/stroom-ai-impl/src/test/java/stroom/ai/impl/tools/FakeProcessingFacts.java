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
import stroom.job.api.JobStates.JobNodeState;
import stroom.job.api.JobStates.JobState;
import stroom.meta.shared.Meta;
import stroom.processor.api.JobNames;
import stroom.processor.api.ProcessingDiagnostics.NodeClaim;
import stroom.processor.api.ProcessingDiagnostics.Profile;
import stroom.processor.api.ProcessingDiagnostics.Settings;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.TaskStatus;
import stroom.util.shared.PermissionException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// [ProcessingFacts] as a test sets them: by default a healthy two-node cluster in master-queue mode with every job
/// processing needs enabled.
final class FakeProcessingFacts implements ProcessingFacts {

    Instant now = Instant.parse("2026-10-28T10:00:00Z");
    Settings settings = new Settings(false, true, true, true, true, 1000, 1000, Duration.ofMinutes(10),
            Duration.ofMinutes(1));
    Cluster cluster = new Cluster(List.of(new Node("node1", true, true), new Node("node2", true, true)), "node1");
    final Map<String, JobState> jobs = new LinkedHashMap<>();
    List<NodeClaim> claims = List.of();
    int deadTasks;
    final Map<String, List<ProcessorFilter>> pipelineFilters = new HashMap<>();
    final List<ProcessorFilter> filters = new ArrayList<>();
    final Map<Integer, Map<TaskStatus, Long>> backlogs = new HashMap<>();
    final Map<Long, List<ProcessorTask>> tasksOfStream = new HashMap<>();
    final Map<Integer, List<ProcessorTask>> createdTasks = new HashMap<>();
    final Map<Long, Meta> streams = new HashMap<>();
    final Map<String, Meta> newest = new HashMap<>();
    final Set<Long> locked = new HashSet<>();
    // Which filters select which streams, by filter id.
    final Map<Integer, Set<Long>> selected = new HashMap<>();
    boolean dependenciesReady = true;
    final Map<String, Profile> profiles = new HashMap<>();
    // The permissions the user lacks, as the facts' reads name them.
    final Set<String> lacking = new HashSet<>();

    FakeProcessingFacts() {
        for (final String name : List.of(ProcessingExplainer.TASK_CREATOR, JobNames.DATA_PROCESSOR,
                JobNames.PROCESSOR_TASK_REAPER, ProcessingExplainer.HEARTBEAT)) {
            jobs.put(name, job(name, true, true, 20, 0));
        }
    }

    /// @return A job enabled (or not) on both nodes, with a task limit and current task count on each.
    static JobState job(final String name,
                        final boolean enabled,
                        final boolean onNodes,
                        final int taskLimit,
                        final int current) {
        final boolean distributed = JobNames.DATA_PROCESSOR.equals(name);
        return new JobState(name, enabled, List.of("node1", "node2").stream()
                .map(node -> new JobNodeState(node, onNodes, distributed
                        ? "Distributed"
                        : "Frequency", distributed
                        ? null
                        : "10s", taskLimit, current, null, null, null))
                .toList());
    }

    @Override
    public Instant now() {
        return now;
    }

    @Override
    public Settings settings() {
        return settings;
    }

    @Override
    public Cluster cluster() {
        check("Manage Nodes");
        return cluster;
    }

    @Override
    public Optional<JobState> job(final String jobName) {
        check("Manage Jobs");
        return Optional.ofNullable(jobs.get(jobName));
    }

    @Override
    public List<NodeClaim> claims() {
        check("View System Info");
        return claims;
    }

    @Override
    public int deadTasks() {
        check("Manage Processors");
        return deadTasks;
    }

    @Override
    public List<ProcessorFilter> pipelineFilters(final DocRef pipeline) {
        check("Manage Processors");
        return pipelineFilters.getOrDefault(pipeline.getUuid(), List.of());
    }

    @Override
    public List<ProcessorFilter> filters(final boolean enabledOnly, final int limit) {
        check("Manage Processors");
        return filters.stream()
                .filter(filter -> !enabledOnly || filter.isEnabled())
                .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                .limit(limit)
                .toList();
    }

    @Override
    public Backlogs backlogs(final int minPriority, final int limit) {
        check("Manage Processors");
        return new Backlogs(backlogs, false);
    }

    @Override
    public List<ProcessorTask> tasks(final int filterId, final TaskStatus status, final int limit) {
        return createdTasks.getOrDefault(filterId, List.of());
    }

    @Override
    public List<ProcessorTask> tasksOfStream(final long metaId) {
        check("Manage Processors");
        return tasksOfStream.getOrDefault(metaId, List.of());
    }

    @Override
    public Optional<Meta> stream(final long metaId) {
        return Optional.ofNullable(streams.get(metaId));
    }

    @Override
    public Optional<Meta> newestStream(final String feed) {
        return Optional.ofNullable(newest.get(feed));
    }

    @Override
    public List<Long> locked(final List<Long> metaIds) {
        return metaIds.stream().filter(locked::contains).toList();
    }

    @Override
    public Optional<Boolean> selects(final ProcessorFilter filter, final long metaId) {
        return Optional.of(selected.getOrDefault(filter.getId(), Set.of()).contains(metaId));
    }

    @Override
    public boolean dependenciesReady(final ProcessorFilter filter) {
        return dependenciesReady;
    }

    @Override
    public Optional<Profile> profile(final String name) {
        check("Manage Processors");
        return Optional.ofNullable(profiles.get(name));
    }

    private void check(final String permission) {
        if (lacking.contains(permission)) {
            throw new PermissionException(null, permission);
        }
    }
}
