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

package stroom.gwt.workbench.client.app.processing;

import stroom.data.client.component.ProcessorTaskSummaryListController;
import stroom.data.client.component.ProcessorTasksTabController;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.explorer.shared.ExplorerConstants;
import stroom.feed.client.event.OpenFeedEvent;
import stroom.gwt.workbench.client.app.gin.processing.ProcessingScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.task.client.component.ProcessorFilterTasksController;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// Stories of `App/Main/ProcessorTaskSummary`, showing Stroom's real [ProcessorTaskSummaryListController]
/// in the upper pane of a folder's 'Active Tasks' tab ([ProcessorTasksTabController], as
/// `FolderTabs` creates and reads it), with fake REST replies.
///
/// | Request | Reply |
/// |---|---|
/// | `POST /processorTask/v1/summary` | the summary rows: one per pipeline, feed, priority and status |
/// | `POST /processorTask/v1/find` | the tasks of the selected summary row, in the lower pane |
///
/// The summary is fetched again when a column is sorted or the pager's Refresh is pressed; the
/// controller then selects the row that was selected again, if the reply still has it.
///
/// The `openDocument` spy records the type and name of each [OpenDocumentEvent] fired (the
/// Pipeline column's open button), and `openFeed` the name of each [OpenFeedEvent] (the Feed
/// column's).
public final class ProcessorTaskSummaryStories {

    private static final DocRef FOLDER = new DocRef(ExplorerConstants.FOLDER_TYPE, "folder-uuid-1", "My Folder");

    private static final String SUMMARY_PATH = "/processorTask/v1/summary";
    private static final String FIND_TASKS_PATH = "/processorTask/v1/find";
    private static final String OPEN_DOCUMENT_SPY = "openDocument";
    private static final String OPEN_FEED_SPY = "openFeed";

    private static final String EVENTS_PIPE =
            "{\"type\": \"Pipeline\", \"uuid\": \"pipe-events\", \"name\": \"Events Pipeline\"}";
    private static final String REFERENCE_PIPE =
            "{\"type\": \"Pipeline\", \"uuid\": \"pipe-ref\", \"name\": \"Reference Pipeline\"}";

    // ProcessorTaskResource.findSummary(): a summary row
    private static final String ROW = """
            {"pipeline": PIPE, "feed": "FEED", "priority": PRIORITY, "status": "STATUS", "count": COUNT}""";

    private static final String EVENTS_PROCESSING = row(EVENTS_PIPE, "EVENTS", 10, "PROCESSING", 3);
    // The same group as EVENTS_PROCESSING, with another task processing
    private static final String EVENTS_PROCESSING_MORE = row(EVENTS_PIPE, "EVENTS", 10, "PROCESSING", 4);
    private static final String EVENTS_COMPLETE = row(EVENTS_PIPE, "EVENTS", 10, "COMPLETE", 12345);
    private static final String REFERENCE_QUEUED = row(REFERENCE_PIPE, "REFERENCE", 5, "QUEUED", 7);

    private static final String SUMMARY = page(EVENTS_PROCESSING, EVENTS_COMPLETE, REFERENCE_QUEUED);
    // The summary after one more task of the selected group has started processing
    private static final String SUMMARY_MORE = page(EVENTS_PROCESSING_MORE, EVENTS_COMPLETE, REFERENCE_QUEUED);
    // Sorted by count, as the server sorts it
    private static final String SUMMARY_BY_COUNT = page(EVENTS_PROCESSING, REFERENCE_QUEUED, EVENTS_COMPLETE);
    private static final String SUMMARY_BY_COUNT_DESC = page(EVENTS_COMPLETE, REFERENCE_QUEUED, EVENTS_PROCESSING);

    // ProcessorTaskResource.find(): the tasks of the 'Events Pipeline', 'EVENTS', 'Processing' group
    private static final String TASKS = """
            {"values": [
                {"id": 1001, "metaId": 2001, "feedName": "EVENTS", "nodeName": "node1a", "status": "PROCESSING",
                  "createTimeMs": 1700000000000, "statusTimeMs": 1700000005000,
                  "processorFilter": {"id": 1, "priority": 10, "pipelineName": "Events Pipeline",
                    "pipelineUuid": "pipe-events"}}],
              "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}}""";

    // The terms ProcessorTasksTabController gives the task list for the 'Events Pipeline', 'EVENTS',
    // 'Processing' row
    private static final String EVENTS_PROCESSING_TASKS_EXPRESSION = """
            {"expression": {"children": [
                {"field": "Pipeline", "condition": "IS_DOC_REF", "docRef": {"uuid": "pipe-events"}},
                {"field": "Feed", "condition": "EQUALS", "value": "EVENTS"},
                {"field": "Status", "condition": "EQUALS", "value": "Processing"}]}}""";

    private static final String SORT_BY_COUNT = """
            {"sortList": [{"id": "Count", "desc": false}]}""";
    private static final String SORT_BY_COUNT_DESC = """
            {"sortList": [{"id": "Count", "desc": true}]}""";

    // The term that keeps a processor filter's tasks to its own
    private static final String FILTER_TERM = """
            {"expression": {"children": [
                {"field": "Processor Filter Id", "condition": "EQUALS", "value": "1"}]}}""";

    // The terms ProcessorFilterTasksController gives the task list for the 'Events Pipeline', 'EVENTS',
    // 'Processing' row of a processor filter's tasks
    private static final String FILTER_PROCESSING_TASKS_EXPRESSION = """
            {"expression": {"children": [
                {"field": "Pipeline", "condition": "IS_DOC_REF", "docRef": {"uuid": "pipe-events"}},
                {"field": "Feed", "condition": "EQUALS", "value": "EVENTS"},
                {"field": "Status", "condition": "EQUALS", "value": "Processing"},
                {"field": "Processor Filter Id", "condition": "EQUALS", "value": "1"}]}}""";

    // The task list's summary row is selected by its status, which no other summary row has
    private static final String PROCESSING = "Processing";

    private ProcessorTaskSummaryStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/ProcessorTaskSummary", ProcessorTaskSummaryStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The summary rows of a folder's tasks: pipeline, feed, priority, status and count
                .story("Rows", context -> render(context, RestReply.json(SUMMARY)))
                .withPlay(play -> {
                    final Play summary = summary(play);
                    play.waitFor(() -> play.expect(summary.querySelectorAll(StroomDom.GRID_ROW)).toHaveLength(3));
                    expectRow(play, 0, "Events Pipeline", "EVENTS", "10", "Processing", "3");
                    expectRow(play, 1, "Events Pipeline", "EVENTS", "10", "Complete", "12,345");
                    expectRow(play, 2, "Reference Pipeline", "REFERENCE", "5", "Queued", "7");
                    // The folder's tasks: those whose pipeline or feed is in the folder
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(SUMMARY_PATH).withJsonBodyContaining("""
                                    {"expression": {"op": "OR", "children": [
                                        {"field": "Pipeline", "condition": "IN_FOLDER",
                                          "docRef": {"uuid": "folder-uuid-1"}},
                                        {"field": "Feed", "condition": "IN_FOLDER",
                                          "docRef": {"uuid": "folder-uuid-1"}}]}}""").toSpyMatcher());
                    // No row is selected, so no tasks are listed
                    play.expect(summary.querySelector("." + StroomDom.SELECTED_ROW)).toBeNull();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post(FIND_TASKS_PATH).toSpyMatcher());
                    expectNoProblems(play);
                })
                // Selecting a summary row lists its tasks in the lower pane
                .story("SelectListsTasks", context -> render(context, RestReply.json(SUMMARY)))
                .withPlay(play -> {
                    final Play summary = summary(play);
                    play.click(summary.findByText(PROCESSING));
                    play.waitFor(() -> play.expect(summary.getByText(PROCESSING).closest(StroomDom.GRID_ROW))
                            .toHaveClass(StroomDom.SELECTED_ROW));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(FIND_TASKS_PATH)
                                    .withJsonBodyContaining(EVENTS_PROCESSING_TASKS_EXPRESSION)
                                    .toSpyMatcher()));
                    play.waitFor(() -> play.expect(tasks(play).getByText("node1a")).toBeInTheDocument());
                    // Only the row clicked is selected
                    play.expect(summary.querySelectorAll("." + StroomDom.SELECTED_ROW)).toHaveLength(1);
                    expectNoProblems(play);
                })
                // The pager's Refresh fetches the summary again; the selected row, unchanged in the
                // reply, stays selected and its tasks stay listed
                .story("RefreshKeepsSelection",
                        context -> render(context, RestReply.json(SUMMARY), RestReply.json(SUMMARY)))
                .withPlay(play -> {
                    selectAndRefresh(play);
                    expectProcessingRowSelected(play, "3");
                    expectNoProblems(play);
                })
                // The pager's Refresh fetches the summary again, and the selected row's group now has
                // one more task: the row shows the new count in its place, and stays selected, as it
                // is the same group (pipeline, feed, priority and status), so its tasks stay listed
                .story("RefreshWithChangedCount",
                        context -> render(context, RestReply.json(SUMMARY), RestReply.json(SUMMARY_MORE)))
                .withPlay(play -> {
                    selectAndRefresh(play);
                    final Play summary = summary(play);
                    play.waitFor(() -> play.expect(summary.querySelectorAll(StroomDom.GRID_ROW).nth(0))
                            .toHaveTextContent("4"));
                    expectRow(play, 0, "Events Pipeline", "EVENTS", "10", "Processing", "4");
                    expectRow(play, 1, "Events Pipeline", "EVENTS", "10", "Complete", "12,345");
                    expectRow(play, 2, "Reference Pipeline", "REFERENCE", "5", "Queued", "7");
                    expectProcessingRowSelected(play, "4");
                    expectNoProblems(play);
                })
                // A row's Info icon shows the row's key data in a tooltip
                .story("InfoShowsKeyData", context -> render(context, RestReply.json(SUMMARY)))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(summary(play).findAllByTitle("Info").nth(2));
                    final Play info = play.within(screen.findByText("Key Data").closest("table"));
                    play.expect(info.getByText("Reference Pipeline {pipe-ref}")).toBeInTheDocument();
                    play.expect(info.getByText("REFERENCE")).toBeInTheDocument();
                    play.expect(info.getByText("5")).toBeInTheDocument();
                    play.expect(info.getByText("QUEUED")).toBeInTheDocument();
                    expectNoProblems(play);
                })
                // The Pipeline and Feed columns' open buttons open the row's pipeline and feed
                .story("OpenPipelineAndFeed", context -> render(context, RestReply.json(SUMMARY)))
                .withPlay(play -> {
                    final Play summary = summary(play);
                    final Query openPipeline = summary.findByRole("button", "Open Pipeline Reference Pipeline");
                    play.hover(openPipeline);
                    play.click(openPipeline);
                    play.waitFor(() -> play.expect(play.spy(OPEN_DOCUMENT_SPY))
                            .toHaveBeenCalledWith("Pipeline Reference Pipeline"));
                    final Query openFeed = summary.getByRole("button", "Open Feed REFERENCE");
                    play.hover(openFeed);
                    play.click(openFeed);
                    play.waitFor(() -> play.expect(play.spy(OPEN_FEED_SPY)).toHaveBeenCalledWith("REFERENCE"));
                    play.expect(play.spy(OPEN_DOCUMENT_SPY)).toHaveBeenCalledTimes(1);
                    play.expect(play.spy(OPEN_FEED_SPY)).toHaveBeenCalledTimes(1);
                    expectNoProblems(play);
                })
                // Sorting by Count asks the server for the rows in count order, ascending and then
                // descending, and shows them in the order of the reply
                .story("SortByCount", context -> render(context, RestReply.json(SUMMARY)))
                .withPlay(play -> {
                    final Play summary = summary(play);
                    play.findByText("12,345");
                    play.click(summary.getByText("Count"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(SUMMARY_PATH).withJsonBodyContaining(SORT_BY_COUNT).toSpyMatcher()));
                    play.waitFor(() -> play.expect(summary.querySelectorAll(StroomDom.GRID_ROW).nth(1))
                            .toHaveTextContent("Reference Pipeline"));
                    expectRow(play, 0, "Events Pipeline", "EVENTS", "10", "Processing", "3");
                    expectRow(play, 2, "Events Pipeline", "EVENTS", "10", "Complete", "12,345");
                    play.click(summary.getByText("Count"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(SUMMARY_PATH)
                                    .withJsonBodyContaining(SORT_BY_COUNT_DESC)
                                    .toSpyMatcher()));
                    play.waitFor(() -> play.expect(summary.querySelectorAll(StroomDom.GRID_ROW).nth(0))
                            .toHaveTextContent("12,345"));
                    expectRow(play, 2, "Events Pipeline", "EVENTS", "10", "Processing", "3");
                    expectNoProblems(play);
                })
                // A processor filter's 'Tasks' tab (ProcessorFilterTasksController, as
                // ProcessorTaskPlugin opens it): the summary and the tasks of that filter only
                .story("ProcessorFilterTasks", ProcessorTaskSummaryStories::renderFilterTasks)
                .withPlay(play -> {
                    final Play summary = summary(play);
                    play.waitFor(() -> play.expect(summary.querySelectorAll(StroomDom.GRID_ROW)).toHaveLength(3));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(SUMMARY_PATH)
                                    .withJsonBodyContaining(FILTER_TERM)
                                    .toSpyMatcher());
                    play.click(summary.getByText(PROCESSING));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(FIND_TASKS_PATH)
                                    .withJsonBodyContaining(FILTER_PROCESSING_TASKS_EXPRESSION)
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                });
    }

    // The summary grid's pane (the upper pane of the tab)
    private static Play summary(final Play play) {
        return play.within(play.querySelectorAll(".split-pane__pane").nth(0));
    }

    // The task list's pane (the lower pane of the tab)
    private static Play tasks(final Play play) {
        return play.within(play.querySelectorAll(".split-pane__pane").nth(1));
    }

    // Checks the summary row at index shows the values given, in its cells after the Info icon's
    private static void expectRow(final Play play,
                                  final int index,
                                  final String pipeline,
                                  final String feed,
                                  final String priority,
                                  final String status,
                                  final String count) {
        final Play row = play.within(summary(play).querySelectorAll(StroomDom.GRID_ROW).nth(index));
        final Query cells = row.querySelectorAll("td, [data-col]");
        play.expect(cells.nth(1)).toHaveTextContent(pipeline);
        play.expect(cells.nth(2)).toHaveTextContent(feed);
        play.expect(cells.nth(3)).toHaveTextContent(priority);
        play.expect(cells.nth(4)).toHaveTextContent(status);
        play.expect(cells.nth(5)).toHaveTextContent(count);
    }

    // Selects the 'Processing' row, waits for its tasks, then presses the summary pager's Refresh
    private static void selectAndRefresh(final Play play) {
        final Play summary = summary(play);
        play.click(summary.findByText(PROCESSING));
        play.waitFor(() -> play.expect(tasks(play).getByText("node1a")).toBeInTheDocument());
        play.click(summary.getByRole("button", "Refresh"));
        final Spy requests = play.spy(ScreenHarness.REQUEST_SPY);
        play.waitFor(() -> play.expect("summary requests", () -> countRequests(requests, SUMMARY_PATH)).toBe(2));
    }

    private static int countRequests(final Spy requests, final String path) {
        int count = 0;
        for (final List<Object> call : requests.getCalls()) {
            if (String.valueOf(call.get(0)).startsWith("POST " + path)) {
                count++;
            }
        }
        return count;
    }

    // Checks the 'Processing' row, with the count given, is the one row selected after the refresh,
    // and its tasks are listed. Selecting the row again (once the refreshed summary has arrived)
    // asks for its tasks a second time
    private static void expectProcessingRowSelected(final Play play, final String count) {
        final Play summary = summary(play);
        final Spy requests = play.spy(ScreenHarness.REQUEST_SPY);
        play.waitFor(() -> play.expect("task requests", () -> countRequests(requests, FIND_TASKS_PATH)).toBe(2));
        play.expect(summary.querySelectorAll(StroomDom.GRID_ROW).nth(0)).toHaveTextContent(count);
        play.expect(summary.getByText(PROCESSING).closest(StroomDom.GRID_ROW)).toHaveClass(StroomDom.SELECTED_ROW);
        play.expect(summary.querySelectorAll("." + StroomDom.SELECTED_ROW)).toHaveLength(1);
        play.waitFor(() -> play.expect(tasks(play).getByText("node1a")).toBeInTheDocument());
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    private static String row(final String pipeline,
                              final String feed,
                              final int priority,
                              final String status,
                              final long count) {
        return ROW.replace("PIPE", pipeline)
                .replace("FEED", feed)
                .replace("PRIORITY", String.valueOf(priority))
                .replace("STATUS", status)
                .replace("COUNT", String.valueOf(count));
    }

    private static String page(final String... rows) {
        return "{\"values\": [" + String.join(", ", rows) + "], \"pageResponse\": {\"offset\": 0, \"length\": "
               + rows.length + ", \"total\": " + rows.length + ", \"exact\": true}}";
    }

    // A processor filter's tasks, as ProcessorTaskPlugin opens them
    private static Widget renderFilterTasks(final StoryContext context) {
        final RestFixtures fixtures = RestFixtures.builder()
                .post(SUMMARY_PATH, RestReply.json(SUMMARY))
                .post(FIND_TASKS_PATH, RestReply.json(TASKS))
                .build();
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .build();
        harness.afterStartUp(() -> {
            final ProcessorFilterTasksController tasks = harness.show(injector.getProcessorFilterTasksController());
            tasks.setProcessorFilter(ProcessorFilter.builder().id(1).build());
        });
        return harness.asWidget();
    }

    // The replies to the summary's requests in turn (the last repeats), unless the
    // request is sorted by count
    private static Widget render(final StoryContext context,
                                 final RestReply summaryReply,
                                 final RestReply... laterSummaryReplies) {
        final RestFixtures fixtures = RestFixtures.builder()
                .route(RequestMatcher.post(SUMMARY_PATH).withJsonBodyContaining(SORT_BY_COUNT),
                        RestReply.json(SUMMARY_BY_COUNT))
                .route(RequestMatcher.post(SUMMARY_PATH).withJsonBodyContaining(SORT_BY_COUNT_DESC),
                        RestReply.json(SUMMARY_BY_COUNT_DESC))
                .post(SUMMARY_PATH, summaryReply, laterSummaryReplies)
                .post(FIND_TASKS_PATH, RestReply.json(TASKS))
                .build();
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .build();
        final Spy openDocument = context.fn(OPEN_DOCUMENT_SPY);
        final Spy openFeed = context.fn(OPEN_FEED_SPY);
        harness.addRegistration(harness.getEventBus().addHandler(OpenDocumentEvent.getType(), event ->
                openDocument.call(event.getDocRef().getType() + " " + event.getDocRef().getName())));
        harness.addRegistration(harness.getEventBus().addHandler(OpenFeedEvent.getType(), event ->
                openFeed.call(event.getName())));
        harness.afterStartUp(() -> {
            final ProcessorTasksTabController controller = injector.getProcessorTasksTabController();
            // As a folder's 'Active Tasks' tab reads it (FolderTabs)
            controller.read(FOLDER, null);
            harness.show(controller);
        });
        return harness.asWidget();
    }
}
