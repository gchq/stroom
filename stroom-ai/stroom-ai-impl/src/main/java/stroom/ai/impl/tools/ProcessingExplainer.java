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

import stroom.ai.impl.tools.ProcessingFacts.Backlogs;
import stroom.ai.impl.tools.ProcessingFacts.Cluster;
import stroom.ai.impl.tools.ProcessingFacts.Node;
import stroom.docref.DocRef;
import stroom.job.api.JobStates.JobNodeState;
import stroom.job.api.JobStates.JobState;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;
import stroom.processor.api.JobNames;
import stroom.processor.api.ProcessingDiagnostics.Limits;
import stroom.processor.api.ProcessingDiagnostics.NodeClaim;
import stroom.processor.api.ProcessingDiagnostics.Period;
import stroom.processor.api.ProcessingDiagnostics.Profile;
import stroom.processor.api.ProcessingDiagnostics.Settings;
import stroom.processor.shared.FeedDependencies;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorFilterTrackerStatus;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.QueryData;
import stroom.processor.shared.TaskStatus;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/// Works out why processing isn't happening for a pipeline, a feed, a stream or the whole cluster, by tracing the
/// chain from the cluster's nodes and jobs through each processor filter, its tracker and profile, to its tasks and
/// where they wait. Each link is `ok`, `blocking` (nothing gets past it), `slowing` (work gets through, late or
/// partly) or `not checked` (the user lacks the permission to see it). It reads state only: Stroom keeps its own
/// reasons for skipping a filter in its logs, which this never reads, so they are worked out from what the
/// database and the nodes say.
final class ProcessingExplainer {

    static final String OK = "ok";
    static final String BLOCKING = "blocking";
    static final String SLOWING = "slowing";
    static final String NOT_CHECKED = "not checked";

    static final String TASK_CREATOR = "Processor Task Creator";
    static final String HEARTBEAT = "Processor Task Heartbeat";

    /// The most filters looked at for a stream, a feed or the cluster's priorities.
    static final int MAX_FILTERS = 200;
    /// The most task rows read to count backlogs by filter.
    static final int MAX_BACKLOG_ROWS = 20_000;
    /// The most Created tasks whose streams are checked for locks.
    static final int LOCK_SAMPLE = 20;
    /// How late a scheduled job may be before it is called overdue.
    static final Duration OVERDUE = Duration.ofMinutes(5);
    /// The most filters listed ahead of another, or in the cluster's priorities.
    static final int MAX_LISTED = 15;
    /// The order filters get work in, as Stroom's `ProcessorFilter.HIGHEST_PRIORITY_FIRST_COMPARATOR` gives it:
    /// highest priority first, then, of the same priority, the filter whose tracker is furthest behind (the lowest
    /// stream id, then the lowest event id, it still has to reach). A filter not yet polled has no tracker, so it
    /// goes last of its priority. Ties go to the lowest filter id, so the order is the same each time.
    static final Comparator<ProcessorFilter> WORK_ORDER = Comparator
            .comparingInt(ProcessorFilter::getPriority).reversed()
            .thenComparingLong(filter -> NullSafe.getOrElse(filter.getProcessorFilterTracker(),
                    ProcessorFilterTracker::getMinMetaId, Long.MAX_VALUE))
            .thenComparingLong(filter -> NullSafe.getOrElse(filter.getProcessorFilterTracker(),
                    ProcessorFilterTracker::getMinEventId, Long.MAX_VALUE))
            .thenComparing(ProcessorFilter::getId);

    static final String PROCESSORS_TAB = "the pipeline's Processors tab";
    static final String FAILED_NOTE = "Stroom keeps no reason for a task that ran and failed: look in that node's "
                                      + "application log around the task's time (processor_tasks, where offered, "
                                      + "lists failed tasks by node and time).";

    private final ProcessingFacts facts;
    private final Report report = new Report();
    private Settings settings;

    ProcessingExplainer(final ProcessingFacts facts) {
        this.facts = facts;
    }

    /// @param pipeline A pipeline.
    /// @return Why its filters are or aren't processing.
    Map<String, Object> explainPipeline(final DocRef pipeline) {
        final Map<String, Object> subject = subject("pipeline", pipeline.getName(), pipeline.getUuid());
        clusterChecks();
        final List<ProcessorFilter> filters = read("filters", "Manage Processors and View on the pipeline",
                () -> facts.pipelineFilters(pipeline));
        if (filters != null) {
            if (filters.isEmpty()) {
                report.add("filters", BLOCKING, "The pipeline has no processor filters, so nothing selects streams "
                                                + "for it to process.",
                        null, "Add a processor filter on " + PROCESSORS_TAB + " (or create_processor_filter, where "
                              + "offered).");
            }
            final Backlogs backlogs = backlogs(filters);
            for (final ProcessorFilter filter : filters) {
                report.filters.add(filterChecks(filter, backlogs, null));
            }
        }
        return report.result(subject);
    }

    /// @param feed A feed's name.
    /// @return Why its newest stream is or isn't processing.
    Map<String, Object> explainFeed(final String feed) {
        final Map<String, Object> subject = subject("feed", feed, null);
        final Optional<Meta> newest = facts.newestStream(feed);
        if (newest.isEmpty()) {
            clusterChecks();
            report.add("streams", BLOCKING, "Feed '" + feed + "' holds no streams you can see, so there is nothing "
                                            + "to process.",
                    null, "Check data is reaching the feed (its receipt, and that it isn't rejected), and that you "
                          + "can view the feed.");
            return report.result(subject);
        }
        subject.put("explained_through", "its newest stream, " + newest.get().getId());
        streamChecks(newest.get());
        return report.result(subject);
    }

    /// @param metaId A stream's id.
    /// @return Why it is or isn't processing.
    Map<String, Object> explainStream(final long metaId) {
        final Map<String, Object> subject = subject("stream", null, null);
        subject.put("id", metaId);
        final Optional<Meta> stream = facts.stream(metaId);
        if (stream.isEmpty()) {
            report.add("stream", BLOCKING, "Stream " + metaId + " doesn't exist, or you can't view its feed.",
                    null, null);
            return report.result(subject);
        }
        streamChecks(stream.get());
        return report.result(subject);
    }

    /// @return The cluster's processing health, and the enabled filters in the order they get work.
    Map<String, Object> explainCluster() {
        final Map<String, Object> subject = subject("cluster", null, null);
        clusterChecks();
        final List<ProcessorFilter> filters = read("priorities", "Manage Processors",
                () -> facts.filters(true, MAX_FILTERS));
        if (filters != null) {
            final Backlogs backlogs = read("priorities", "Manage Processors",
                    () -> facts.backlogs(1, MAX_BACKLOG_ROWS));
            report.priorities = priorities(filters, backlogs);
        }
        return report.result(subject);
    }

    // --------------------------------------------------------------------------------
    // The cluster, its settings and jobs.

    private void clusterChecks() {
        settings = facts.settings();
        report.mode = settings.claimTasksOnWorker()
                ? "worker claiming: each node claims its own tasks"
                : "master queue: the master node queues tasks and hands them out";
        final Cluster cluster = read("nodes", "Manage Nodes", facts::cluster);
        if (cluster != null) {
            nodeChecks(cluster);
        }
        settingsChecks();
        jobChecks(cluster);
        if (settings.claimTasksOnWorker()) {
            claimChecks();
        }
        final Integer dead = read("dead tasks", "Manage Processors", facts::deadTasks);
        if (dead != null && dead > 0) {
            report.add("dead tasks", SLOWING, dead + " task(s) have gone longer than the task lease ("
                                              + duration(settings.taskLeaseTimeout()) + ") without a heartbeat and "
                                              + "haven't been recovered: their streams wait until they are.",
                    Map.of("dead_tasks", dead),
                    "Check the '" + JobNames.PROCESSOR_TASK_REAPER + "' job is enabled and running (Jobs screen).");
        }
    }

    private void nodeChecks(final Cluster cluster) {
        final List<String> enabled = cluster.nodes().stream().filter(Node::enabled).map(Node::name).toList();
        final List<String> answering = cluster.nodes().stream()
                .filter(node -> node.enabled() && node.active())
                .map(Node::name)
                .toList();
        final List<String> silent = enabled.stream().filter(name -> !answering.contains(name)).toList();
        final Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("enabled", enabled);
        evidence.put("answering", answering);
        evidence.put("master", cluster.master());
        if (answering.isEmpty()) {
            report.add("nodes", BLOCKING, enabled.isEmpty()
                            ? "No node is enabled, so none runs processing's jobs."
                            : "No enabled node is answering the cluster's pings, so none is processing.",
                    evidence, "Enable a node (Nodes screen) or bring the enabled nodes back.");
        } else if (!settings.claimTasksOnWorker() && cluster.master() == null) {
            report.add("nodes", BLOCKING, "There is no master node. In master-queue mode the master queues and "
                                          + "hands out every task, so none are handed out.",
                    evidence, "The master is the enabled, answering node of highest priority: check the nodes on "
                              + "the Nodes screen.");
        } else if (!silent.isEmpty()) {
            report.add("nodes", SLOWING, "Enabled node(s) " + String.join(", ", silent) + " aren't answering: "
                                         + "they process nothing, and tasks they held wait to be reaped.",
                    evidence, "Bring them back, or disable them on the Nodes screen.");
        } else {
            report.add("nodes", OK, answering.size() + " enabled node(s) answering" + (cluster.master() == null
                    ? ""
                    : "; the master is " + cluster.master()) + ".", evidence, null);
        }
    }

    private void settingsChecks() {
        final Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("claimTasksOnWorker", settings.claimTasksOnWorker());
        evidence.put("assignTasks", settings.assignTasks());
        evidence.put("fillTaskQueue", settings.fillTaskQueue());
        evidence.put("tasksToCreate", settings.tasksToCreate());
        evidence.put("queueSize", settings.queueSize());
        if (!settings.claimTasksOnWorker() && !settings.assignTasks()) {
            report.add("settings", BLOCKING, "stroom.processor.assignTasks is false: the master hands out no tasks.",
                    evidence, "Set stroom.processor.assignTasks to true (Properties screen).");
        } else if (!settings.claimTasksOnWorker() && !settings.fillTaskQueue()) {
            report.add("settings", BLOCKING, "stroom.processor.fillTaskQueue is false: the master queues no tasks "
                                             + "to hand out.",
                    evidence, "Set stroom.processor.fillTaskQueue to true (Properties screen).");
        } else {
            report.add("settings", OK, "Processing runs in " + report.mode + ".", evidence, null);
        }
    }

    private void jobChecks(final Cluster cluster) {
        final List<String> answering = cluster == null
                ? null
                : cluster.nodes().stream().filter(node -> node.enabled() && node.active()).map(Node::name).toList();
        boolean refused = false;
        for (final String name : List.of(TASK_CREATOR, JobNames.DATA_PROCESSOR, JobNames.PROCESSOR_TASK_REAPER,
                HEARTBEAT)) {
            if (refused) {
                break;
            }
            try {
                final Optional<JobState> job = facts.job(name);
                if (job.isEmpty()) {
                    report.add("job: " + name, JobNames.PROCESSOR_TASK_REAPER.equals(name) || HEARTBEAT.equals(name)
                                    ? SLOWING
                                    : BLOCKING,
                            "The '" + name + "' job doesn't exist on this Stroom.", null, null);
                } else {
                    jobCheck(job.get(), answering);
                }
            } catch (final PermissionException e) {
                report.notChecked("jobs", "Manage Jobs");
                refused = true;
            }
        }
    }

    private void jobCheck(final JobState job, final List<String> answering) {
        final String name = job.name();
        final String link = "job: " + name;
        // The nodes where it can run: enabled there, on a node that is enabled and answering.
        final List<JobNodeState> running = job.nodes().stream()
                .filter(JobNodeState::enabled)
                .filter(node -> answering == null || answering.contains(node.node()))
                .toList();
        final Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("enabled", job.enabled());
        evidence.put("nodes", job.nodes().stream().map(ProcessingExplainer::jobNode).toList());
        final boolean essential = TASK_CREATOR.equals(name) || JobNames.DATA_PROCESSOR.equals(name);
        final String what = switch (name) {
            case TASK_CREATOR -> "no new tasks are created for any filter";
            case JobNames.DATA_PROCESSOR -> "no node processes any task";
            case JobNames.PROCESSOR_TASK_REAPER -> "tasks held by a node that stops are never recovered";
            default -> "running tasks' heartbeats aren't renewed, so they look dead after the task lease";
        };
        final String state = essential
                ? BLOCKING
                : SLOWING;
        if (!job.enabled()) {
            report.add(link, state, "The '" + name + "' job is disabled, so " + what + "."
                                    + (TASK_CREATOR.equals(name)
                    ? " Stroom creates this job disabled."
                    : ""), evidence, "Enable the '" + name + "' job on the Jobs screen.");
            return;
        }
        if (running.isEmpty()) {
            report.add(link, state, "The '" + name + "' job isn't enabled on any enabled, answering node, so "
                                    + what + ".",
                    evidence, "Enable it on at least one node (Jobs screen, the job's node rows).");
            return;
        }
        if (JobNames.DATA_PROCESSOR.equals(name)) {
            dataProcessorCheck(link, running, evidence);
            return;
        }
        final List<JobNodeState> overdue = running.stream()
                .filter(node -> node.nextScheduledMs() != null
                                && node.nextScheduledMs() < facts.now().minus(OVERDUE).toEpochMilli())
                .toList();
        if (!overdue.isEmpty() && overdue.size() == running.size()) {
            report.add(link, SLOWING, "The '" + name + "' job is overdue on every node it is enabled on: it isn't "
                                      + "running on schedule.",
                    evidence, "Check the nodes' job threads (Server Tasks screen) and that the job system is "
                              + "enabled (stroom.job.enabled).");
            return;
        }
        report.add(link, OK, "The '" + name + "' job is enabled on " + names(running) + ".", evidence, null);
    }

    private void dataProcessorCheck(final String link,
                                    final List<JobNodeState> running,
                                    final Map<String, Object> evidence) {
        final List<JobNodeState> withSlots = running.stream().filter(node -> node.taskLimit() > 0).toList();
        if (withSlots.isEmpty()) {
            report.add(link, BLOCKING, "The 'Data Processor' job has a task limit of 0 on every node it is enabled "
                                       + "on, so no node takes any task.",
                    evidence, "Set its Max Tasks above 0 on the Jobs screen.");
            return;
        }
        final List<JobNodeState> known = withSlots.stream().filter(node -> node.currentTaskCount() != null).toList();
        final boolean allBusy = !known.isEmpty() && known.size() == withSlots.size()
                                && known.stream().allMatch(node -> node.currentTaskCount() >= node.taskLimit());
        if (allBusy) {
            report.add(link, SLOWING, "Every 'Data Processor' slot is busy ("
                                      + known.stream().mapToInt(JobNodeState::currentTaskCount).sum()
                                      + " tasks on " + names(known) + "): new work waits for a slot, by priority.",
                    evidence, "Raise Max Tasks on the Jobs screen, or add nodes, if the backlog keeps growing.");
            return;
        }
        report.add(link, OK, "The 'Data Processor' job is enabled on " + names(withSlots) + ", with "
                             + withSlots.stream().mapToInt(JobNodeState::taskLimit).sum() + " slot(s).",
                evidence, null);
    }

    private void claimChecks() {
        final List<NodeClaim> claims = read("claiming", "View System Info", facts::claims);
        if (claims == null) {
            return;
        }
        final List<String> unreachable = claims.stream()
                .filter(claim -> claim.error() != null)
                .map(claim -> claim.node() + " (" + claim.error() + ")")
                .toList();
        final long leaseMs = NullSafe.getOrElse(settings.taskLeaseTimeout(), Duration::toMillis, Long.MAX_VALUE);
        final List<String> heartbeatLate = claims.stream()
                .filter(claim -> claim.lastHeartbeatAgeMs() != null && claim.lastHeartbeatAgeMs() > leaseMs)
                .map(NodeClaim::node)
                .toList();
        final Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("nodes", claims.stream().map(ProcessingExplainer::claim).toList());
        if (!unreachable.isEmpty()) {
            report.add("claiming", SLOWING, "Node(s) didn't say what they are claiming: "
                                            + String.join("; ", unreachable) + ".",
                    evidence, null);
        } else if (!heartbeatLate.isEmpty()) {
            report.add("claiming", SLOWING, "Node(s) " + String.join(", ", heartbeatLate) + " haven't renewed "
                                            + "their tasks' heartbeats within the task lease: they will stop "
                                            + "themselves, and their tasks will be reaped.",
                    evidence, "Check the '" + HEARTBEAT + "' job and those nodes' database connections.");
        } else {
            report.add("claiming", OK, claims.size() + " node(s) claiming for themselves.", evidence, null);
        }
    }

    // --------------------------------------------------------------------------------
    // A stream.

    private void streamChecks(final Meta stream) {
        clusterChecks();
        final Map<String, Object> streamFacts = new LinkedHashMap<>();
        streamFacts.put("id", stream.getId());
        streamFacts.put("feed", stream.getFeedName());
        streamFacts.put("type", stream.getTypeName());
        streamFacts.put("status", NullSafe.get(stream.getStatus(), Status::getDisplayValue));
        streamFacts.put("created", time(stream.getCreateMs()));
        report.stream = streamFacts;
        if (Status.DELETED.equals(stream.getStatus())) {
            report.add("stream", BLOCKING, "Stream " + stream.getId() + " is deleted: deleted streams are never "
                                           + "processed.", null, null);
            return;
        }
        final List<ProcessorTask> tasks = read("tasks", "Manage Processors", () -> facts.tasksOfStream(
                stream.getId()));
        if (tasks == null) {
            return;
        }
        if (!tasks.isEmpty()) {
            tasksOfStream(stream, tasks);
            return;
        }
        // No task yet: which filters select it, and what stops them reaching it.
        final List<ProcessorFilter> filters = read("filters", "Manage Processors",
                () -> facts.filters(false, MAX_FILTERS));
        if (filters == null) {
            return;
        }
        final List<ProcessorFilter> selecting = new ArrayList<>();
        final List<String> untold = new ArrayList<>();
        for (final ProcessorFilter filter : filters) {
            final Optional<Boolean> selects = facts.selects(filter, stream.getId());
            if (selects.isEmpty()) {
                untold.add(describe(filter));
            } else if (selects.get()) {
                selecting.add(filter);
            }
        }
        if (selecting.isEmpty()) {
            report.add("selection", BLOCKING, "No processor filter selects stream " + stream.getId() + ", so no "
                                              + "task is made for it.",
                    untold.isEmpty()
                            ? null
                            : Map.of("not_tested", untold),
                    "Add a processor filter whose selection takes its feed and type ('" + stream.getFeedName()
                    + "', '" + stream.getTypeName() + "') on the pipeline that should process it.");
            return;
        }
        final Backlogs backlogs = backlogs(selecting);
        for (final ProcessorFilter filter : selecting) {
            report.filters.add(filterChecks(filter, backlogs, stream));
        }
    }

    private void tasksOfStream(final Meta stream, final List<ProcessorTask> tasks) {
        final List<Map<String, Object>> listed = tasks.stream().map(ProcessingExplainer::task).toList();
        final List<ProcessorTask> waiting = tasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.CREATED || task.getStatus() == TaskStatus.QUEUED
                                || task.getStatus() == TaskStatus.PROCESSING)
                .toList();
        final boolean failed = tasks.stream().anyMatch(task -> task.getStatus() == TaskStatus.FAILED);
        if (waiting.isEmpty()) {
            report.add("tasks", failed
                            ? BLOCKING
                            : OK, failed
                            ? "Stream " + stream.getId() + "'s task failed. " + FAILED_NOTE
                            : "Stream " + stream.getId() + " has been processed.",
                    Map.of("tasks", listed), failed
                            ? "Reprocess it once the cause is fixed (reprocess_streams, where offered)."
                            : null);
            return;
        }
        report.add("tasks", OK, "Stream " + stream.getId() + " has a task, waiting as " + waiting.stream()
                .map(task -> task.getStatus().getDisplayValue())
                .distinct()
                .toList() + ": see its filter's checks for why it waits.", Map.of("tasks", listed), null);
        final List<ProcessorFilter> filters = waiting.stream()
                .map(ProcessorTask::getProcessorFilter)
                .filter(Objects::nonNull)
                .toList();
        final Backlogs backlogs = backlogs(filters);
        for (final ProcessorFilter filter : filters) {
            report.filters.add(filterChecks(filter, backlogs, null));
        }
    }

    // --------------------------------------------------------------------------------
    // A filter.

    private Map<String, Object> filterChecks(final ProcessorFilter filter,
                                             final Backlogs backlogs,
                                             final Meta stream) {
        final Report checks = new Report();
        final Map<String, Long> counts = counts(backlogs, filter);
        stateCheck(checks, filter);
        trackerCheck(checks, filter, stream);
        windowCheck(checks, filter, stream);
        dependencyCheck(checks, filter);
        pollCheck(checks, filter);
        profileCheck(checks, filter);
        Map<String, Object> priority = null;
        if (backlogs == null) {
            // Without the task counts, neither where its tasks wait nor what is ahead of them can be told.
            checks.notChecked("tasks", "Manage Processors");
        } else {
            taskCheck(checks, filter, counts);
            priority = priorityCheck(checks, filter, counts);
        }
        final Map<String, Object> entry = new LinkedHashMap<>(describeMap(filter));
        entry.put("tasks", counts);
        entry.put("checks", checks.checks);
        if (priority != null) {
            entry.put("priority_context", priority);
        }
        report.merge(checks, "filter " + filter.getId());
        return entry;
    }

    private void stateCheck(final Report checks, final ProcessorFilter filter) {
        final Processor processor = filter.getProcessor();
        if (filter.isDeleted()) {
            checks.add("filter", BLOCKING, "The filter is deleted.", null, "Add a new processor filter on "
                                                                           + PROCESSORS_TAB + ".");
        } else if (!filter.isEnabled()) {
            checks.add("filter", BLOCKING, "The filter is disabled, so no tasks are made or handed out for it.", null,
                    "Enable it on " + PROCESSORS_TAB + " (or set_processor_filter_enabled filter_uuid="
                    + filter.getUuid() + " enabled=true, where offered).");
        } else if (processor != null && (processor.isDeleted() || !processor.isEnabled())) {
            checks.add("filter", BLOCKING, "The pipeline's processor (the parent row of its filters) is "
                                           + (processor.isDeleted()
                    ? "deleted"
                    : "disabled") + ", so none of its filters get tasks.", null,
                    "Enable the processor row on " + PROCESSORS_TAB + ".");
        } else {
            checks.add("filter", OK, "Enabled, priority " + filter.getPriority() + ".", null, null);
        }
    }

    private void trackerCheck(final Report checks, final ProcessorFilter filter, final Meta stream) {
        final ProcessorFilterTracker tracker = filter.getProcessorFilterTracker();
        if (tracker == null) {
            checks.add("tracker", SLOWING, "The filter has no tracker yet: it hasn't been polled.", null, null);
            return;
        }
        final Map<String, Object> evidence = trackerMap(tracker);
        if (tracker.getStatus() == ProcessorFilterTrackerStatus.ERROR) {
            checks.add("tracker", BLOCKING, "The filter's tracker is in Error"
                                            + (NullSafe.isNonBlankString(tracker.getMessage())
                    ? ": " + tracker.getMessage()
                    : " (Stroom kept no message)") + ". Stroom never retries a filter in Error, and nothing "
                                            + "resets it.",
                    evidence, "Fix the cause (often its selection), then replace the filter: delete it and add one "
                              + "with the same selection on " + PROCESSORS_TAB + " (or create_processor_filter, "
                              + "where offered).");
            return;
        }
        if (tracker.getStatus() == ProcessorFilterTrackerStatus.COMPLETE) {
            checks.add("tracker", BLOCKING, "The filter is Complete: " + (filter.getMaxMetaCreateTimeMs() != null
                    ? "its stream create time window ended, "
                    : "it reached its limits, ") + "so it selects no more streams.",
                    evidence, "Add a new filter (or a reprocess filter) for any streams still to process.");
            return;
        }
        if (stream != null && stream.getId() < tracker.getMinMetaId()) {
            checks.add("tracker", BLOCKING, "The filter has already moved past stream " + stream.getId()
                                            + " (it next looks from stream " + tracker.getMinMetaId() + "), so it "
                                            + "won't select it again. It didn't make a task for it when it passed: "
                                            + "the stream didn't match then, or the filter was added after it.",
                    evidence, "Reprocess the stream (reprocess_streams, or a reprocess filter) to process it now.");
            return;
        }
        checks.add("tracker", OK, NullSafe.getOrElse(tracker.getStatus(), ProcessorFilterTrackerStatus::getDisplayValue,
                "Created") + (Integer.valueOf(0).equals(tracker.getLastPollTaskCount())
                ? ", up to date: its last poll found nothing new."
                : "."), evidence, null);
    }

    private void windowCheck(final Report checks, final ProcessorFilter filter, final Meta stream) {
        final Long min = filter.getMinMetaCreateTimeMs();
        final Long max = filter.getMaxMetaCreateTimeMs();
        if (min == null && max == null) {
            return;
        }
        final Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("from", time(min));
        evidence.put("to", time(max));
        if (stream != null && ((min != null && stream.getCreateMs() < min) || (max != null
                                                                               && stream.getCreateMs() > max))) {
            checks.add("window", BLOCKING, "Stream " + stream.getId() + " was created outside the filter's stream "
                                           + "create time window.", evidence, null);
        } else if (max != null && max < facts.now().toEpochMilli()) {
            checks.add("window", SLOWING, "The filter's stream create time window ended " + time(max) + ": streams "
                                          + "created since aren't selected.", evidence, null);
        } else {
            checks.add("window", OK, "Within its stream create time window.", evidence, null);
        }
    }

    private void dependencyCheck(final Report checks, final ProcessorFilter filter) {
        final List<?> dependencies = NullSafe.list(NullSafe.get(filter.getQueryData(), QueryData::getFeedDependencies,
                FeedDependencies::getFeedDependencies));
        if (dependencies.isEmpty()) {
            return;
        }
        if (facts.dependenciesReady(filter)) {
            checks.add("dependencies", OK, "Each feed it depends on has an Unlocked stream.", null, null);
        } else {
            checks.add("dependencies", BLOCKING, "The filter waits for an Unlocked stream of each feed it depends "
                                                 + "on, and at least one has none, so it waits indefinitely.",
                    Map.of("feed_dependencies", dependencies.size()),
                    "Check the dependency feeds are receiving data, or remove the dependency.");
        }
    }

    private void pollCheck(final Report checks, final ProcessorFilter filter) {
        final ProcessorFilterTracker tracker = filter.getProcessorFilterTracker();
        if (tracker == null || tracker.getStatus() == ProcessorFilterTrackerStatus.COMPLETE
            || tracker.getStatus() == ProcessorFilterTrackerStatus.ERROR) {
            return;
        }
        final Instant now = facts.now();
        if (tracker.getLastPollMs() == null) {
            checks.add("polling", SLOWING, "The filter hasn't been polled yet: the '" + TASK_CREATOR + "' job polls "
                                           + "it" + (settings.useMaxMetaIdFromPreviousPoll()
                    ? ", and its first poll only records where to start: tasks come on the poll after"
                    : "") + ".", null, null);
        } else if (tracker.getPrevMaxMetaId() == null && settings.useMaxMetaIdFromPreviousPoll()) {
            checks.add("polling", SLOWING, "The filter's first poll only records where to start: tasks come on the "
                                           + "next poll.", null, null);
        } else if (tracker.getNextPollMs() != null && tracker.getNextPollMs() > now.toEpochMilli()) {
            checks.add("polling", OK, "Its last poll found nothing, so it is next polled at "
                                      + time(tracker.getNextPollMs()) + " (polls of a filter that finds nothing back "
                                      + "off, up to " + duration(settings.skipNonProducingMax())
                                      + "; editing the filter makes it due at once).", null, null);
        }
    }

    private void profileCheck(final Report checks, final ProcessorFilter filter) {
        final String name = filter.getProfileName();
        if (NullSafe.isBlankString(name)) {
            return;
        }
        final Optional<Profile> found;
        try {
            found = facts.profile(name);
        } catch (final PermissionException e) {
            checks.notChecked("profile", "Manage Processors");
            return;
        }
        if (found.isEmpty()) {
            checks.add("profile", BLOCKING, "The filter's processor profile '" + name + "' doesn't exist: Stroom "
                                            + "can't resolve it, so the filter gets no tasks.",
                    null, "Create the profile (Processor Profiles screen) or clear the filter's profile.");
            return;
        }
        final Profile profile = found.get();
        final Map<String, Object> evidence = profileMap(profile);
        final List<Period> overnight = profile.periods().stream().filter(Period::overnight).toList();
        if (!profile.nodeGroupFound()) {
            checks.add("profile", BLOCKING, "Profile '" + name + "' runs on node group '" + profile.nodeGroupName()
                                            + "', which doesn't exist, so the filter gets no tasks.",
                    evidence, "Create the node group, or point the profile at one that exists.");
        } else if (!profile.nodeGroupEnabled()) {
            checks.add("profile", BLOCKING, "Profile '" + name + "' runs on node group '" + profile.nodeGroupName()
                                            + "', which is disabled (node groups are created disabled), so no "
                                            + "node gets threads for the filter.",
                    evidence, "Enable the node group (Node Groups screen).");
        } else if (profile.periods().isEmpty()) {
            checks.add("profile", BLOCKING, "Profile '" + name + "' has no periods, so it gives no node any threads.",
                    evidence, "Add a period to the profile.");
        } else if (profile.nodeLimits().values().stream().noneMatch(ProcessingExplainer::hasThreads)) {
            checks.add("profile", BLOCKING, "Profile '" + name + "' gives no node any threads now ("
                                            + time(facts.now().toEpochMilli()) + "): it is outside all its "
                                            + "periods" + (overnight.isEmpty()
                    ? ""
                    : ", or past midnight in an overnight period (a known defect: an overnight period only applies "
                      + "until midnight)") + ", or none of the group's nodes is enabled.",
                    evidence, overnight.isEmpty()
                            ? "Wait for its next period, or widen its periods."
                            : "Until the defect is fixed, split each overnight period in two (start to 23:59:59, "
                              + "then 00:00 to the end on the next day).");
        } else if (!overnight.isEmpty()) {
            checks.add("profile", SLOWING, "Profile '" + name + "' has overnight period(s) (ending at or before they "
                                           + "start). A known defect means they only apply until midnight: from "
                                           + "midnight to their end time, the filter gets no threads.",
                    evidence, "Split each overnight period in two (start to 23:59:59, then 00:00 to the end on the "
                              + "next day) until the defect is fixed.");
        } else {
            checks.add("profile", OK, "Profile '" + name + "' gives threads now on " + profile.nodeLimits().entrySet()
                    .stream()
                    .filter(entry -> hasThreads(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .toList() + ".", evidence, null);
        }
    }

    private void taskCheck(final Report checks, final ProcessorFilter filter, final Map<String, Long> counts) {
        final long created = counts.getOrDefault(TaskStatus.CREATED.getDisplayValue(), 0L);
        final long queued = counts.getOrDefault(TaskStatus.QUEUED.getDisplayValue(), 0L);
        final long processing = counts.getOrDefault(TaskStatus.PROCESSING.getDisplayValue(), 0L);
        if (filter.getMaxProcessingTasks() > 0 && processing >= filter.getMaxProcessingTasks()) {
            checks.add("tasks", SLOWING, "The filter is at its limit of " + filter.getMaxProcessingTasks()
                                         + " task(s) processing at once.", counts, null);
            return;
        }
        if (created > 0 && queued == 0 && processing == 0) {
            final List<ProcessorTask> sample = facts.tasks(filter.getId(), TaskStatus.CREATED, LOCK_SAMPLE);
            final List<Long> locked = facts.locked(sample.stream()
                    .map(ProcessorTask::getMetaId)
                    .filter(Objects::nonNull)
                    .toList());
            if (!locked.isEmpty()) {
                checks.add("tasks", locked.size() == sample.size()
                                ? BLOCKING
                                : SLOWING, locked.size() + " of the " + sample.size() + " oldest waiting tasks are "
                                           + "for streams still Locked (being written): a task isn't queued until its "
                                           + "stream is unlocked.",
                        Map.of("locked_streams", locked),
                        "Find what is still writing those streams; a stream left Locked by a failed write needs "
                        + "unlocking or deleting.");
                return;
            }
            checks.add("tasks", SLOWING, created + " task(s) are made but none is queued or processing: they wait "
                                         + "their turn (see the priority check).", counts, null);
            return;
        }
        checks.add("tasks", OK, created + queued + processing == 0
                ? "No tasks waiting."
                : created + " made, " + queued + " queued, " + processing + " processing.", counts, null);
    }

    // The enabled filters ahead of this one for work in its profile, and whether they are starving it.
    private Map<String, Object> priorityCheck(final Report checks,
                                              final ProcessorFilter filter,
                                              final Map<String, Long> counts) {
        if (!filter.isEnabled() || filter.isDeleted()) {
            return null;
        }
        final List<ProcessorFilter> enabled;
        final Backlogs backlogs;
        try {
            enabled = facts.filters(true, MAX_FILTERS);
            backlogs = facts.backlogs(filter.getPriority(), MAX_BACKLOG_ROWS);
        } catch (final PermissionException e) {
            checks.notChecked("priority", "Manage Processors");
            return null;
        }
        final String profile = NullSafe.string(filter.getProfileName());
        final List<Map<String, Object>> ahead = new ArrayList<>();
        long aheadCreated = 0;
        long aheadWork = 0;
        for (final ProcessorFilter other : enabled) {
            if (Objects.equals(other.getId(), filter.getId()) || WORK_ORDER.compare(other, filter) >= 0
                || !profile.equals(NullSafe.string(other.getProfileName()))) {
                continue;
            }
            final Map<String, Long> otherCounts = counts(backlogs, other);
            final long work = otherCounts.values().stream().mapToLong(Long::longValue).sum();
            if (work == 0) {
                continue;
            }
            aheadCreated += otherCounts.getOrDefault(TaskStatus.CREATED.getDisplayValue(), 0L);
            aheadWork += work;
            if (ahead.size() < MAX_LISTED) {
                final Map<String, Object> entry = new LinkedHashMap<>(describeMap(other));
                entry.put("tasks", otherCounts);
                ahead.add(entry);
            }
        }
        final Map<String, Object> context = new LinkedHashMap<>();
        context.put("priority", filter.getPriority());
        context.put("profile", profile.isEmpty()
                ? null
                : profile);
        context.put("ahead_with_work", ahead);
        context.put("ahead_tasks", aheadWork);
        if (backlogs.capped()) {
            context.put("capped", "more tasks exist than were counted");
        }
        final long created = counts.getOrDefault(TaskStatus.CREATED.getDisplayValue(), 0L);
        final long running = counts.getOrDefault(TaskStatus.QUEUED.getDisplayValue(), 0L)
                             + counts.getOrDefault(TaskStatus.PROCESSING.getDisplayValue(), 0L);
        final String scope = profile.isEmpty()
                ? "filters with no profile"
                : "profile '" + profile + "'";
        if (aheadWork == 0) {
            checks.add("priority", OK, "No filter ahead of it in " + scope + " has work waiting.", context, null);
        } else if (aheadCreated >= settings.tasksToCreate()) {
            checks.add("priority", SLOWING, "Filters ahead of it in " + scope + " already have " + aheadCreated
                                            + " tasks made, at least the creation budget of "
                                            + settings.tasksToCreate() + " per run: task creation spends the budget "
                                            + "on them, so no new tasks are made for this filter until they drain.",
                    context, "Raise this filter's priority, cap the busier filters (their Max Processing Tasks, or "
                             + "a profile's thread limits), or give this filter its own profile.");
        } else if (created > 0 && running == 0) {
            checks.add("priority", SLOWING, "Filters ahead of it in " + scope + " have " + aheadWork + " task(s) "
                                            + "waiting, and work is handed out in priority order: this "
                                            + "filter's " + created + " task(s) wait until they drain.",
                    context, "Raise this filter's priority if it should go first.");
        } else {
            checks.add("priority", OK, aheadWork + " task(s) of filters ahead of it in " + scope + " go first, "
                                       + "but this filter is getting work.", context, null);
        }
        return context;
    }

    // --------------------------------------------------------------------------------
    // The cluster's priorities.

    private Map<String, Object> priorities(final List<ProcessorFilter> filters, final Backlogs backlogs) {
        final List<Map<String, Object>> rows = new ArrayList<>();
        final Map<String, Long> aheadByProfile = new LinkedHashMap<>();
        final List<String> starved = new ArrayList<>();
        int withWork = 0;
        for (final ProcessorFilter filter : filters) {
            final Map<String, Long> counts = counts(backlogs, filter);
            final long work = counts.values().stream().mapToLong(Long::longValue).sum();
            if (work == 0) {
                continue;
            }
            withWork++;
            final String profile = NullSafe.string(filter.getProfileName());
            final long ahead = aheadByProfile.getOrDefault(profile, 0L);
            final boolean waiting = counts.getOrDefault(TaskStatus.CREATED.getDisplayValue(), 0L) > 0
                                    && counts.getOrDefault(TaskStatus.QUEUED.getDisplayValue(), 0L) == 0
                                    && counts.getOrDefault(TaskStatus.PROCESSING.getDisplayValue(), 0L) == 0;
            if (waiting && ahead > 0) {
                starved.add(describe(filter));
            }
            aheadByProfile.merge(profile, work, Long::sum);
            if (rows.size() < MAX_LISTED) {
                final Map<String, Object> row = new LinkedHashMap<>(describeMap(filter));
                row.put("tasks", counts);
                row.put("waiting_behind", ahead);
                rows.add(row);
            }
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("rule", "Work goes to the highest priority first (1-100, default 10), within each processor "
                           + "profile (filters without one share one). Of filters with the same priority, the one "
                           + "whose tracker is furthest behind (the lowest stream id still to reach, then the lowest "
                           + "event id) goes first, so the filter with the oldest work left catches up before newer "
                           + "ones; 'filters' is in this order. Each run, task creation spends a budget of "
                           + settings.tasksToCreate() + " per profile, counting tasks a filter already has made, so "
                           + "a busy high-priority filter can leave lower ones in its profile with no new tasks.");
        result.put("filters_with_work", withWork);
        result.put("filters", rows);
        result.put("starved", starved);
        if (backlogs != null && backlogs.capped()) {
            result.put("capped", "more tasks exist than were counted");
        }
        if (filters.size() >= MAX_FILTERS) {
            result.put("filters_capped", "only the " + MAX_FILTERS + " highest-priority enabled filters were looked "
                                         + "at");
        }
        if (!starved.isEmpty()) {
            report.add("priorities", SLOWING, starved.size() + " filter(s) have tasks waiting with none handed out, "
                                              + "behind the filters ahead of them in their profile.",
                    Map.of("starved", starved), "Raise their priority, or cap the filters ahead of them.");
        } else {
            report.add("priorities", OK, withWork + " enabled filter(s) have work, and none is waiting behind "
                                         + "others with nothing handed out.", null, null);
        }
        return result;
    }

    // --------------------------------------------------------------------------------
    // Helpers.

    private Backlogs backlogs(final List<ProcessorFilter> filters) {
        final int minPriority = filters.stream().mapToInt(ProcessorFilter::getPriority).min().orElse(1);
        try {
            return facts.backlogs(minPriority, MAX_BACKLOG_ROWS);
        } catch (final PermissionException e) {
            report.notChecked("tasks", "Manage Processors");
            return null;
        }
    }

    private static Map<String, Long> counts(final Backlogs backlogs, final ProcessorFilter filter) {
        final Map<String, Long> counts = new LinkedHashMap<>();
        if (backlogs == null) {
            return counts;
        }
        final Map<TaskStatus, Long> byStatus = backlogs.byFilter().getOrDefault(filter.getId(),
                new EnumMap<>(TaskStatus.class));
        for (final TaskStatus status : List.of(TaskStatus.CREATED, TaskStatus.QUEUED, TaskStatus.PROCESSING)) {
            final long count = byStatus.getOrDefault(status, 0L);
            if (count > 0) {
                counts.put(status.getDisplayValue(), count);
            }
        }
        return counts;
    }

    // Reads a fact, noting the check couldn't be made if the user lacks the permission it needs.
    private <T> T read(final String link, final String permission, final Supplier<T> reader) {
        try {
            return reader.get();
        } catch (final PermissionException e) {
            report.notChecked(link, permission);
            return null;
        }
    }

    private static boolean hasThreads(final Limits limits) {
        return limits.maxNodeThreads() > 0 && limits.maxClusterThreads() > 0;
    }

    private static Map<String, Object> subject(final String kind, final String name, final String uuid) {
        final Map<String, Object> subject = new LinkedHashMap<>();
        subject.put("kind", kind);
        if (name != null) {
            subject.put("name", name);
        }
        if (uuid != null) {
            subject.put("uuid", uuid);
        }
        return subject;
    }

    private static String describe(final ProcessorFilter filter) {
        return "filter " + filter.getId() + " (" + pipelineName(filter) + ", priority " + filter.getPriority() + ")";
    }

    // The filter's pipeline by name, else UUID: from the filter, or its processor when the filter doesn't say.
    private static String pipelineName(final ProcessorFilter filter) {
        final DocRef pipeline = filter.getPipeline();
        final String name = NullSafe.isNonBlankString(filter.getPipelineName())
                ? filter.getPipelineName()
                : NullSafe.get(pipeline, DocRef::getName);
        if (NullSafe.isNonBlankString(name)) {
            return name;
        }
        return Objects.requireNonNullElse(NullSafe.get(pipeline, DocRef::getUuid), "an unknown pipeline");
    }

    private static Map<String, Object> describeMap(final ProcessorFilter filter) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", filter.getId());
        map.put("uuid", filter.getUuid());
        map.put("pipeline", pipelineName(filter));
        map.put("pipeline_uuid", NullSafe.get(filter.getPipeline(), DocRef::getUuid));
        map.put("priority", filter.getPriority());
        map.put("enabled", filter.isEnabled());
        if (NullSafe.isNonBlankString(filter.getProfileName())) {
            map.put("profile", filter.getProfileName());
        }
        if (filter.getMaxProcessingTasks() > 0) {
            map.put("max_processing_tasks", filter.getMaxProcessingTasks());
        }
        if (filter.isReprocess()) {
            map.put("reprocess", true);
        }
        return map;
    }

    private static Map<String, Object> trackerMap(final ProcessorFilterTracker tracker) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", NullSafe.getOrElse(tracker.getStatus(), ProcessorFilterTrackerStatus::getDisplayValue,
                "Created"));
        map.put("message", tracker.getMessage());
        map.put("next_stream_id", tracker.getMinMetaId());
        map.put("last_poll", time(tracker.getLastPollMs()));
        map.put("last_poll_tasks", tracker.getLastPollTaskCount());
        map.put("next_poll", time(tracker.getNextPollMs()));
        map.put("streams", tracker.getMetaCount());
        return map;
    }

    private static Map<String, Object> profileMap(final Profile profile) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", profile.name());
        map.put("node_group", profile.nodeGroupName());
        map.put("node_group_enabled", profile.nodeGroupEnabled());
        map.put("time_zone", profile.timeZone());
        map.put("periods", profile.periods().stream().map(period -> {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("days", period.days());
            entry.put("from", period.startTime());
            entry.put("to", period.endTime());
            if (period.overnight()) {
                entry.put("overnight", true);
            }
            entry.put("max_node_threads", period.maxNodeThreads());
            entry.put("max_cluster_threads", period.maxClusterThreads());
            return entry;
        }).toList());
        final Map<String, Object> limits = new LinkedHashMap<>();
        profile.nodeLimits().forEach((node, limit) -> limits.put(node, Map.of(
                "node_threads", threads(limit.maxNodeThreads()),
                "cluster_threads", threads(limit.maxClusterThreads()))));
        map.put("threads_now", limits);
        return map;
    }

    private static Object threads(final int threads) {
        return threads == Integer.MAX_VALUE
                ? "unlimited"
                : threads;
    }

    private static Map<String, Object> jobNode(final JobNodeState node) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("node", node.node());
        map.put("enabled", node.enabled());
        map.put("type", node.type());
        if (node.schedule() != null) {
            map.put("schedule", node.schedule());
        }
        map.put("max_tasks", node.taskLimit());
        map.put("current_tasks", node.currentTaskCount());
        map.put("last_run", time(node.lastExecutedMs()));
        map.put("next_run", time(node.nextScheduledMs()));
        if (node.error() != null) {
            map.put("error", node.error());
        }
        return map;
    }

    private static Map<String, Object> claim(final NodeClaim claim) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("node", claim.node());
        if (claim.error() != null) {
            map.put("error", claim.error());
            return map;
        }
        map.put("eligible_filters", claim.eligibleFilters());
        map.put("filters_with_work", claim.filtersWithWork());
        map.put("backed_off_filters", claim.backedOffFilters());
        map.put("tasks_in_flight", claim.tasksInFlight());
        map.put("locked_stream_releases", claim.lockedMetaReleases());
        map.put("heartbeat_age", NullSafe.get(claim.lastHeartbeatAgeMs(), ms -> duration(Duration.ofMillis(ms))));
        return map;
    }

    private static Map<String, Object> task(final ProcessorTask task) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", task.getId());
        map.put("status", NullSafe.get(task.getStatus(), TaskStatus::getDisplayValue));
        map.put("node", task.getNodeName());
        map.put("filter", NullSafe.get(task.getProcessorFilter(), ProcessorFilter::getId));
        map.put("created", time(task.getCreateTimeMs()));
        map.put("status_time", time(task.getStatusTimeMs()));
        return map;
    }

    private static String names(final List<JobNodeState> nodes) {
        return String.join(", ", nodes.stream().map(JobNodeState::node).toList());
    }

    static String time(final Long ms) {
        return ms == null
                ? null
                : Instant.ofEpochMilli(ms).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    static String duration(final Duration duration) {
        if (duration == null) {
            return null;
        }
        final long seconds = duration.toSeconds();
        if (seconds < 120) {
            return seconds + "s";
        }
        if (seconds < 7200) {
            return (seconds / 60) + "m";
        }
        return (seconds / 3600) + "h";
    }

    // --------------------------------------------------------------------------------

    /// The checks made, in the order of the chain, and what couldn't be checked.
    private static final class Report {

        private final List<Map<String, Object>> checks = new ArrayList<>();
        private final List<String> notChecked = new ArrayList<>();
        private final List<Map<String, Object>> filters = new ArrayList<>();
        // Filters' blocking and slowing findings, raised to the whole answer.
        private final List<Map<String, Object>> raisedFindings = new ArrayList<>();
        private Map<String, Object> stream;
        private Map<String, Object> priorities;
        private String mode;

        private void add(final String link,
                         final String state,
                         final String finding,
                         final Object evidence,
                         final String fix) {
            final Map<String, Object> check = new LinkedHashMap<>();
            check.put("link", link);
            check.put("state", state);
            check.put("finding", finding);
            if (evidence != null) {
                check.put("evidence", evidence);
            }
            if (fix != null) {
                check.put("fix", fix);
            }
            checks.add(check);
        }

        private void notChecked(final String link, final String permission) {
            final String why = link + ": needs the " + permission + " permission";
            if (!notChecked.contains(why)) {
                notChecked.add(why);
                add(link, NOT_CHECKED, "Not checked: you need the " + permission + " permission to see this, and "
                                       + "the answer may lie here.", null, null);
            }
        }

        // A filter's blocking and slowing findings, raised to the whole answer with the filter named.
        private void merge(final Report filterChecks, final String filter) {
            filterChecks.notChecked.forEach(why -> {
                if (!notChecked.contains(why)) {
                    notChecked.add(why);
                }
            });
            for (final Map<String, Object> check : filterChecks.checks) {
                if (BLOCKING.equals(check.get("state")) || SLOWING.equals(check.get("state"))) {
                    final Map<String, Object> raised = new LinkedHashMap<>(check);
                    raised.put("link", filter + ": " + check.get("link"));
                    raised.remove("evidence");
                    raisedFindings.add(raised);
                }
            }
        }

        private Map<String, Object> result(final Map<String, Object> subject) {
            final List<Map<String, Object>> all = new ArrayList<>(checks);
            all.addAll(raisedFindings);
            final Optional<Map<String, Object>> blocker = first(all, BLOCKING);
            final Optional<Map<String, Object>> slower = first(all, SLOWING);
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("subject", subject);
            result.put("answer", blocker.map(check -> "Blocked at " + check.get("link") + ": " + check.get("finding"))
                    .or(() -> slower.map(check -> "Not blocked, but slowed at " + check.get("link") + ": "
                                                  + check.get("finding")))
                    .orElse(notChecked.isEmpty()
                            ? "Nothing found stopping processing."
                            : "Nothing found stopping processing in what could be checked."));
            blocker.ifPresent(check -> result.put("first_blocker", check));
            if (mode != null) {
                result.put("mode", mode);
            }
            if (stream != null) {
                result.put("stream", stream);
            }
            result.put("checks", checks);
            if (!filters.isEmpty()) {
                result.put("filters", filters);
            }
            if (priorities != null) {
                result.put("priorities", priorities);
            }
            if (!notChecked.isEmpty()) {
                result.put("not_checked", notChecked);
            }
            result.put("note", FAILED_NOTE);
            return result;
        }

        private static Optional<Map<String, Object>> first(final List<Map<String, Object>> checks,
                                                           final String state) {
            return checks.stream().filter(check -> state.equals(check.get("state"))).findFirst();
        }
    }
}
