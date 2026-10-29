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

import stroom.ai.impl.tools.ProcessingFacts.Cluster;
import stroom.ai.impl.tools.ProcessingFacts.Node;
import stroom.docref.DocRef;
import stroom.job.api.JobStates.JobNodeState;
import stroom.job.api.JobStates.JobState;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.api.JobNames;
import stroom.processor.api.ProcessingDiagnostics.Limits;
import stroom.processor.api.ProcessingDiagnostics.Period;
import stroom.processor.api.ProcessingDiagnostics.Profile;
import stroom.processor.api.ProcessingDiagnostics.Settings;
import stroom.processor.shared.FeedDependencies;
import stroom.processor.shared.FeedDependency;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorFilterTrackerStatus;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.QueryData;
import stroom.processor.shared.TaskStatus;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestProcessingExplainer {

    private static final DocRef PIPELINE = new DocRef(PipelineDoc.TYPE, "p1", "ACME-Events");
    private static final long NOW_MS = 1_793_181_600_000L;

    private final FakeProcessingFacts facts = new FakeProcessingFacts();

    @Test
    void aHealthyPipelineHasNothingStoppingIt() {
        facts.pipelineFilters.put("p1", List.of(filter(1, 10).build()));

        final Map<String, Object> result = explainPipeline();

        assertThat(result.get("answer")).isEqualTo("Nothing found stopping processing.");
        assertThat(result).doesNotContainKeys("first_blocker", "not_checked");
        assertThat(states(result)).allMatch(state -> state.equals(ProcessingExplainer.OK));
    }

    @Test
    void aDisabledFilterIsTheBlockerWithTheFix() {
        facts.pipelineFilters.put("p1", List.of(filter(1, 10).enabled(false).build()));

        final Map<String, Object> blocker = firstBlocker(explainPipeline());

        assertThat(blocker.get("link")).isEqualTo("filter 1: filter");
        assertThat(String.valueOf(blocker.get("fix"))).contains("Processors tab")
                .contains("set_processor_filter_enabled filter_uuid=f1 enabled=true");
    }

    @Test
    void aDisabledProcessorBlocksEveryFilter() {
        facts.pipelineFilters.put("p1", List.of(filter(1, 10)
                .processor(Processor.builder().uuid("pr").pipelineUuid("p1").enabled(false).build())
                .build()));

        assertThat(String.valueOf(firstBlocker(explainPipeline()).get("finding")))
                .contains("processor (the parent row of its filters) is disabled");
    }

    @Test
    void aTrackerInErrorIsNeverRetried() {
        facts.pipelineFilters.put("p1", List.of(filter(1, 10)
                .processorFilterTracker(tracker().status(ProcessorFilterTrackerStatus.ERROR)
                        .message("Attempting to create tasks with an unconstrained filter").build())
                .build()));

        final Map<String, Object> blocker = firstBlocker(explainPipeline());

        assertThat(String.valueOf(blocker.get("finding"))).contains("unconstrained filter")
                .contains("Stroom never retries a filter in Error");
        assertThat(String.valueOf(blocker.get("fix"))).contains("replace the filter");
    }

    @Test
    void theTaskCreatorJobComesBeforeTheFilterInTheChain() {
        // Stroom creates the Processor Task Creator job disabled: the commonest reason on a new Stroom.
        facts.jobs.put(ProcessingExplainer.TASK_CREATOR, FakeProcessingFacts.job(ProcessingExplainer.TASK_CREATOR,
                false, true, 20, 0));
        facts.pipelineFilters.put("p1", List.of(filter(1, 10).enabled(false).build()));

        final Map<String, Object> result = explainPipeline();

        assertThat(firstBlocker(result).get("link")).isEqualTo("job: Processor Task Creator");
        assertThat(String.valueOf(firstBlocker(result).get("finding"))).contains("creates this job disabled");
        assertThat(String.valueOf(result.get("answer"))).startsWith("Blocked at job: Processor Task Creator");
    }

    @Test
    void aJobEnabledOnlyOnNodesThatArentAnsweringBlocks() {
        facts.cluster = new Cluster(List.of(new Node("node1", true, true), new Node("node2", true, false)),
                "node1");
        facts.jobs.put(JobNames.DATA_PROCESSOR, new JobState(JobNames.DATA_PROCESSOR,
                true, List.of(new JobNodeState("node1", false, "Distributed", null, 20, 0,
                        null, null, null), new JobNodeState("node2", true,
                        "Distributed", null, 20, null, null, null, "no answer within 5s"))));
        facts.pipelineFilters.put("p1", List.of(filter(1, 10).build()));

        final Map<String, Object> result = explainPipeline();

        assertThat(state(result, "job: Data Processor")).isEqualTo(ProcessingExplainer.BLOCKING);
        assertThat(state(result, "nodes")).as("node2 is enabled but silent").isEqualTo(ProcessingExplainer.SLOWING);
    }

    @Test
    void theDataProcessorWithNoSlotsBlocksAndWithEverySlotBusySlows() {
        facts.pipelineFilters.put("p1", List.of(filter(1, 10).build()));
        facts.jobs.put(JobNames.DATA_PROCESSOR, FakeProcessingFacts.job(JobNames.DATA_PROCESSOR, true, true, 0, 0));
        assertThat(state(explainPipeline(), "job: Data Processor")).isEqualTo(ProcessingExplainer.BLOCKING);

        facts.jobs.put(JobNames.DATA_PROCESSOR, FakeProcessingFacts.job(JobNames.DATA_PROCESSOR, true, true, 4, 4));
        final Map<String, Object> busy = explainPipeline();
        assertThat(state(busy, "job: Data Processor")).isEqualTo(ProcessingExplainer.SLOWING);
        assertThat(check(busy, "job: Data Processor").get("finding")).asString().contains("8 tasks on node1, node2");
    }

    @Test
    void noMasterBlocksTheMasterQueueButNotWorkerClaiming() {
        facts.cluster = new Cluster(facts.cluster.nodes(), null);
        facts.pipelineFilters.put("p1", List.of(filter(1, 10).build()));
        assertThat(state(explainPipeline(), "nodes")).isEqualTo(ProcessingExplainer.BLOCKING);

        facts.settings = new Settings(true, true, true, true, true, 1000, 1000, Duration.ofMinutes(10),
                Duration.ofMinutes(1));
        final Map<String, Object> claiming = explainPipeline();
        assertThat(state(claiming, "nodes")).isEqualTo(ProcessingExplainer.OK);
        assertThat(claiming.get("mode")).asString().startsWith("worker claiming");
    }

    @Test
    void theMasterQueueSettingsCanStopEverything() {
        facts.settings = new Settings(false, false, true, true, true, 1000, 1000, Duration.ofMinutes(10),
                Duration.ofMinutes(1));
        facts.pipelineFilters.put("p1", List.of(filter(1, 10).build()));

        assertThat(String.valueOf(firstBlocker(explainPipeline()).get("finding"))).contains("assignTasks is false");
    }

    @Test
    void whatTheUserMayNotSeeIsSaidNotLeftOut() {
        // Asked for by the user: check what can be seen, mark the rest.
        facts.lacking.add("Manage Jobs");
        facts.lacking.add("Manage Nodes");
        facts.pipelineFilters.put("p1", List.of(filter(1, 10).build()));

        final Map<String, Object> result = explainPipeline();

        assertThat(strings(result.get("not_checked"))).containsExactly(
                "nodes: needs the Manage Nodes permission", "jobs: needs the Manage Jobs permission");
        assertThat(state(result, "jobs")).isEqualTo(ProcessingExplainer.NOT_CHECKED);
        assertThat(result.get("answer")).isEqualTo("Nothing found stopping processing in what could be checked.");
    }

    @Test
    void aProfileThatResolvesToNoThreadsBlocks() {
        facts.pipelineFilters.put("p1", List.of(filter(1, 10).profileName("Nights").build()));
        assertThat(String.valueOf(firstBlocker(explainPipeline()).get("finding")))
                .contains("profile 'Nights' doesn't exist");

        facts.profiles.put("Nights", profile(true, false, List.of(period(false)), threads(5)));
        assertThat(String.valueOf(firstBlocker(explainPipeline()).get("finding")))
                .contains("which is disabled (node groups are created disabled)");

        facts.profiles.put("Nights", profile(true, true, List.of(period(false)), threads(0)));
        assertThat(String.valueOf(firstBlocker(explainPipeline()).get("finding")))
                .contains("gives no node any threads now").doesNotContain("overnight");
    }

    @Test
    void anOvernightPeriodIsWarnedAboutAsTheKnownDefect() {
        facts.pipelineFilters.put("p1", List.of(filter(1, 10).profileName("Nights").build()));
        // After midnight in a 22:00-06:00 period, Stroom gives no threads.
        facts.profiles.put("Nights", profile(true, true, List.of(period(true)), threads(0)));
        final Map<String, Object> blocked = firstBlocker(explainPipeline());
        assertThat(String.valueOf(blocked.get("finding"))).contains("an overnight period only applies until "
                                                                    + "midnight");
        assertThat(String.valueOf(blocked.get("fix"))).contains("split each overnight period in two");

        // Before midnight it works, but the warning stands.
        facts.profiles.put("Nights", profile(true, true, List.of(period(true)), threads(5)));
        assertThat(state(explainPipeline(), "filter 1: profile")).isEqualTo(ProcessingExplainer.SLOWING);
    }

    @Test
    void tasksForLockedStreamsAreNotQueued() {
        facts.pipelineFilters.put("p1", List.of(filter(1, 10).build()));
        facts.backlogs.put(1, counts(3, 0, 0));
        facts.createdTasks.put(1, List.of(task(1, 101L), task(1, 102L), task(1, 103L)));
        facts.locked.addAll(Set.of(101L, 102L, 103L));

        final Map<String, Object> blocker = firstBlocker(explainPipeline());

        assertThat(blocker.get("link")).isEqualTo("filter 1: tasks");
        assertThat(String.valueOf(blocker.get("finding"))).contains("3 of the 3 oldest waiting tasks are for streams "
                                                                    + "still Locked");
    }

    @Test
    void higherPriorityFiltersStarveLowerOnesInTheSameProfile() {
        final ProcessorFilter busy = filter(2, 50).pipelineUuid("p2").pipelineName("Busy").build();
        final ProcessorFilter mine = filter(1, 10).build();
        facts.filters.addAll(List.of(busy, mine));
        facts.pipelineFilters.put("p1", List.of(mine));
        facts.backlogs.put(1, counts(5, 0, 0));

        // Fewer than the creation budget ahead: this filter's tasks wait their turn.
        facts.backlogs.put(2, counts(400, 100, 8));
        Map<String, Object> result = explainPipeline();
        assertThat(state(result, "filter 1: priority")).isEqualTo(ProcessingExplainer.SLOWING);
        assertThat(check(result, "filter 1: priority").get("finding")).asString()
                .contains("508 task(s) waiting").contains("this filter's 5 task(s) wait until they drain");

        // As many made as the budget: no new tasks are made for this one at all.
        facts.backlogs.put(2, counts(1500, 100, 8));
        result = explainPipeline();
        assertThat(check(result, "filter 1: priority").get("finding")).asString()
                .contains("already have 1500 tasks made, at least the creation budget of 1000");

        // A different profile isn't ahead of it.
        facts.filters.set(0, busy.copy().profileName("Bulk").build());
        assertThat(state(explainPipeline(), "filter 1: priority")).isEqualTo(ProcessingExplainer.OK);
    }

    @Test
    void aStreamNoFilterSelectsSaysSo() {
        facts.streams.put(500L, stream(500L, Status.UNLOCKED));
        facts.filters.add(filter(1, 10).build());

        final Map<String, Object> blocker = firstBlocker(explainStream(500L));

        assertThat(blocker.get("link")).isEqualTo("selection");
        assertThat(String.valueOf(blocker.get("fix"))).contains("'ACME', 'Raw Events'");
    }

    @Test
    void aStreamTheFilterHasPassedWontBeSelectedAgain() {
        facts.streams.put(500L, stream(500L, Status.UNLOCKED));
        facts.filters.add(filter(1, 10).processorFilterTracker(tracker().minMetaId(600).build()).build());
        facts.selected.put(1, Set.of(500L));

        final Map<String, Object> blocker = firstBlocker(explainStream(500L));

        assertThat(String.valueOf(blocker.get("finding"))).contains("already moved past stream 500");
        assertThat(String.valueOf(blocker.get("fix"))).contains("reprocess_streams");
    }

    @Test
    void aStreamWithATaskIsExplainedThroughItsFilter() {
        final ProcessorFilter filter = filter(1, 10).enabled(false).build();
        facts.streams.put(500L, stream(500L, Status.UNLOCKED));
        facts.tasksOfStream.put(500L, List.of(ProcessorTask.builder().id(9).metaId(500L).status(TaskStatus.QUEUED)
                .processorFilter(filter).build()));

        final Map<String, Object> result = explainStream(500L);

        assertThat(state(result, "tasks")).isEqualTo(ProcessingExplainer.OK);
        assertThat(firstBlocker(result).get("link")).isEqualTo("filter 1: filter");
    }

    @Test
    void aFailedTaskIsHandedToProcessorTasks() {
        facts.streams.put(500L, stream(500L, Status.UNLOCKED));
        facts.tasksOfStream.put(500L, List.of(ProcessorTask.builder().id(9).metaId(500L).status(TaskStatus.FAILED)
                .processorFilter(filter(1, 10).build()).build()));

        assertThat(String.valueOf(firstBlocker(explainStream(500L)).get("finding")))
                .contains("task failed").contains("Stroom keeps no reason");
    }

    @Test
    void aDeletedStreamIsNeverProcessed() {
        facts.streams.put(500L, stream(500L, Status.DELETED));
        assertThat(String.valueOf(firstBlocker(explainStream(500L)).get("finding"))).contains("is deleted");
    }

    @Test
    void aFeedIsExplainedThroughItsNewestStream() {
        assertThat(String.valueOf(firstBlocker(new ProcessingExplainer(facts).explainFeed("ACME")).get("finding")))
                .contains("holds no streams you can see");

        facts.newest.put("ACME", stream(700L, Status.UNLOCKED));
        final Map<String, Object> result = new ProcessingExplainer(facts).explainFeed("ACME");
        assertThat(map(result.get("subject"))).containsEntry("explained_through", "its newest stream, 700");
        assertThat(firstBlocker(result).get("link")).isEqualTo("selection");
    }

    @Test
    void aMissingFeedDependencyWaitsIndefinitely() {
        facts.dependenciesReady = false;
        facts.pipelineFilters.put("p1", List.of(filter(1, 10).queryData(QueryData.builder()
                .feedDependencies(new FeedDependencies(List.of(new FeedDependency("u1", "USERS", "Reference")), null,
                        null))
                .build()).build()));

        assertThat(String.valueOf(firstBlocker(explainPipeline()).get("finding"))).contains("waits indefinitely");
    }

    @Test
    void aFilterNotYetPolledIsSlowNotBlocked() {
        facts.pipelineFilters.put("p1", List.of(filter(1, 10).processorFilterTracker(ProcessorFilterTracker.builder()
                .build()).build()));

        final Map<String, Object> result = explainPipeline();

        assertThat(result).doesNotContainKey("first_blocker");
        assertThat(check(result, "filter 1: polling").get("finding")).asString()
                .contains("its first poll only records where to start");
    }

    @Test
    void theClusterViewListsWhoIsStarved() {
        facts.filters.addAll(List.of(filter(2, 50).build(), filter(1, 10).build()));
        facts.backlogs.put(2, counts(10, 5, 2));
        facts.backlogs.put(1, counts(5, 0, 0));

        final Map<String, Object> result = new ProcessingExplainer(facts).explainCluster();

        final Map<String, Object> priorities = map(result.get("priorities"));
        assertThat(strings(priorities.get("starved"))).containsExactly("filter 1 (ACME-Events, priority 10)");
        assertThat(String.valueOf(priorities.get("rule"))).contains("highest priority first");
        assertThat(state(result, "priorities")).isEqualTo(ProcessingExplainer.SLOWING);
    }

    @Test
    void theClusterViewOrdersFiltersOfTheSamePriorityByHowFarBehindTheirTrackersAre() {
        facts.filters.addAll(List.of(
                filter(1, 10).processorFilterTracker(tracker().minMetaId(500).build()).build(),
                filter(2, 10).processorFilterTracker(tracker().minMetaId(100).minEventId(7).build()).build(),
                filter(3, 10).processorFilterTracker(tracker().minMetaId(100).minEventId(2).build()).build(),
                filter(4, 50).processorFilterTracker(tracker().minMetaId(900).build()).build()));
        facts.backlogs.put(1, counts(5, 0, 0));
        facts.backlogs.put(2, counts(5, 1, 0));
        facts.backlogs.put(3, counts(5, 1, 0));
        facts.backlogs.put(4, counts(5, 1, 0));

        final Map<String, Object> priorities = map(new ProcessingExplainer(facts).explainCluster().get("priorities"));

        assertThat(((List<?>) priorities.get("filters")).stream().map(row -> map(row).get("id")))
                .containsExactly(4, 3, 2, 1);
        // Filter 1 is behind filters of its own priority, not only filter 4.
        assertThat(((List<?>) priorities.get("filters")).stream()
                .filter(row -> map(row).get("id").equals(1))
                .map(row -> map(row).get("waiting_behind")))
                .containsExactly(18L);
        assertThat(strings(priorities.get("starved"))).containsExactly("filter 1 (ACME-Events, priority 10)");
        assertThat(String.valueOf(priorities.get("rule"))).contains("the same priority")
                .contains("tracker is furthest behind");
    }

    @Test
    void aFilterOfTheSamePriorityWhoseTrackerIsFurtherBehindIsAheadOfIt() {
        final ProcessorFilter mine = filter(1, 10).processorFilterTracker(tracker().minMetaId(500).build()).build();
        final ProcessorFilter behind = filter(2, 10).pipelineUuid("p2").pipelineName("Behind")
                .processorFilterTracker(tracker().minMetaId(100).build()).build();
        facts.filters.addAll(List.of(mine, behind));
        facts.pipelineFilters.put("p1", List.of(mine));
        facts.backlogs.put(1, counts(5, 0, 0));
        facts.backlogs.put(2, counts(40, 10, 2));

        Map<String, Object> result = explainPipeline();
        assertThat(state(result, "filter 1: priority")).isEqualTo(ProcessingExplainer.SLOWING);
        assertThat(check(result, "filter 1: priority").get("finding")).asString()
                .contains("Filters ahead of it").contains("52 task(s) waiting");

        // Once its tracker is ahead of this one's, it waits behind this filter instead.
        facts.filters.set(1, behind.copy().processorFilterTracker(tracker().minMetaId(900).build()).build());
        result = explainPipeline();
        assertThat(state(result, "filter 1: priority")).isEqualTo(ProcessingExplainer.OK);
        assertThat(check(result, "filter 1: priority").get("finding")).asString()
                .contains("No filter ahead of it");
    }

    @Test
    void aFilterNotYetPolledGoesLastOfItsPriority() {
        final ProcessorFilter unpolled = filter(1, 10).processorFilterTracker(null).build();
        final ProcessorFilter polled = filter(2, 10).processorFilterTracker(tracker().minMetaId(9_999).build()).build();
        final ProcessorFilter lower = filter(3, 5).build();

        assertThat(List.of(lower, unpolled, polled).stream().sorted(ProcessingExplainer.WORK_ORDER).toList())
                .containsExactly(polled, unpolled, lower);
    }

    // --------------------------------------------------------------------------------

    private Map<String, Object> explainPipeline() {
        return new ProcessingExplainer(facts).explainPipeline(PIPELINE);
    }

    private Map<String, Object> explainStream(final long id) {
        return new ProcessingExplainer(facts).explainStream(id);
    }

    private static ProcessorFilter.Builder filter(final int id, final int priority) {
        return ProcessorFilter.builder()
                .id(id)
                .uuid("f" + id)
                .pipelineUuid("p1")
                .pipelineName("ACME-Events")
                .priority(priority)
                .enabled(true)
                .processorFilterTracker(tracker().build());
    }

    // A tracker polled a minute ago that found nothing new.
    private static ProcessorFilterTracker.Builder tracker() {
        return ProcessorFilterTracker.builder()
                .status(ProcessorFilterTrackerStatus.CREATED)
                .minMetaId(1)
                .lastPollMs(NOW_MS - 60_000)
                .lastPollTaskCount(0)
                .prevMaxMetaId(1L);
    }

    private static Meta stream(final long id, final Status status) {
        return Meta.builder().id(id).feedName("ACME").typeName("Raw Events").status(status).createMs(NOW_MS).build();
    }

    private static ProcessorTask task(final int filterId, final long metaId) {
        return ProcessorTask.builder().id(metaId).metaId(metaId).status(TaskStatus.CREATED)
                .processorFilter(ProcessorFilter.builder().id(filterId).build()).build();
    }

    private static Map<TaskStatus, Long> counts(final long created, final long queued, final long processing) {
        final Map<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
        counts.put(TaskStatus.CREATED, created);
        counts.put(TaskStatus.QUEUED, queued);
        counts.put(TaskStatus.PROCESSING, processing);
        return counts;
    }

    private static Profile profile(final boolean groupFound,
                                   final boolean groupEnabled,
                                   final List<Period> periods,
                                   final Map<String, Limits> limits) {
        return new Profile("Nights", "Night nodes", groupFound, groupEnabled, "UTC", periods, limits);
    }

    private static Period period(final boolean overnight) {
        return overnight
                ? new Period(List.of("Wednesday"), "22:00:00", "06:00:00", true, 4, 8)
                : new Period(List.of("Wednesday"), "09:00:00", "17:00:00", false, 4, 8);
    }

    private static Map<String, Limits> threads(final int threads) {
        return Map.of("node1", new Limits(threads, threads), "node2", new Limits(threads, threads));
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(final Object value) {
        return (List<String>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(final Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstBlocker(final Map<String, Object> result) {
        assertThat(result).as("a blocker in %s", result.get("checks")).containsKey("first_blocker");
        return (Map<String, Object>) result.get("first_blocker");
    }

    // Every check, the cluster's and each filter's (named "filter N: link").
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> allChecks(final Map<String, Object> result) {
        final List<Map<String, Object>> all = new ArrayList<>((List<Map<String, Object>>) result.get("checks"));
        for (final Map<String, Object> filter : (List<Map<String, Object>>) result.getOrDefault("filters",
                List.of())) {
            for (final Map<String, Object> check : (List<Map<String, Object>>) filter.get("checks")) {
                final Map<String, Object> named = new LinkedHashMap<>(check);
                named.put("link", "filter " + filter.get("id") + ": " + check.get("link"));
                all.add(named);
            }
        }
        return all;
    }

    private static Map<String, Object> check(final Map<String, Object> result, final String link) {
        return allChecks(result).stream()
                .filter(check -> link.equals(check.get("link")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No check " + link + " in " + allChecks(result)));
    }

    private static String state(final Map<String, Object> result, final String link) {
        return String.valueOf(check(result, link).get("state"));
    }

    private static List<String> states(final Map<String, Object> result) {
        return allChecks(result).stream().map(check -> String.valueOf(check.get("state"))).toList();
    }
}
