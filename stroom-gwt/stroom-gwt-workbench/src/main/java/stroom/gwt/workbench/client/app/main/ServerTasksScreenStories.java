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


package stroom.gwt.workbench.client.app.main;

import stroom.data.client.presenter.ShowDataEvent;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.gwt.workbench.client.app.gin.AppScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.play.ValueMatcher;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.processor.task.client.event.OpenProcessorTaskEvent;
import stroom.task.client.presenter.TaskManagerPresenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/// Stories of `App/Main/ServerTasksScreen`, showing Stroom's real [TaskManagerPresenter] (the
/// 'Server Tasks' tab) with fake REST replies.
///
/// The fixtures answer Stroom's `NodeResource` (the nodes, `POST /node/v1/find`) and `TaskResource`
/// (the tasks, `POST /task/v1/find/{node}`, and terminating them,
/// `POST /task/v1/terminate/{node}`). The server works out which tasks match the name filter: a
/// route for a request filtering on 'Processor A' marks only that task `MATCHED`. The requests are
/// checked on the request spy, and the `onOpenDoc`/`onOpenData`/`onShowFilterTasks` spies record
/// Stroom's `OpenDocumentEvent`, `ShowDataEvent` and `OpenProcessorTaskEvent`. The presenter comes
/// from GIN.
///
/// The list refreshes when Stroom's content pane asks it to (`Refreshable`), which the stories
/// don't do, so there is no polling here; see `UserTaskManagerDialogStories` for a screen that
/// polls by itself.
public final class ServerTasksScreenStories {

    /// The name of the spy recording the documents opened.
    static final String ON_OPEN_DOC = "onOpenDoc";
    /// The name of the spy recording the data shown.
    static final String ON_OPEN_DATA = "onOpenData";
    /// The name of the spy recording the processor filters whose tasks are shown.
    static final String ON_SHOW_FILTER_TASKS = "onShowFilterTasks";

    private static final String TASK_FIND_PATH = "/task/v1/find/node1";
    private static final String NODES = """
            {"values": [{"master": true, "node": {"name": "node1", "enabled": true}}],
              "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}}""";

    // A parent task with two child tasks, all on node1 (10s old, so not expanded). TaskInfoParser
    // keys: feed=, filter id=, pipeline uuid=, meta_id=
    private static final String TASKS = """
            {
              "values": [
                {"id": {"id": "p"}, "taskName": "Pipeline Processor", "nodeName": "node1",
                  "userRef": {"uuid": "u-admin", "displayName": "admin"},
                  "submitTimeMs": 1700000000000, "timeNowMs": 1700000010000,
                  "taskInfo": "TASK_INFO",
                  "filterMatchState": "P_STATE"},
                {"id": {"id": "c1", "parentId": {"id": "p"}}, "taskName": "Stream Processor A", "nodeName": "node1",
                  "userRef": {"uuid": "u-admin", "displayName": "admin"},
                  "submitTimeMs": 1700000005000, "timeNowMs": 1700000010000, "taskInfo": "stream 1",
                  "filterMatchState": "A_STATE"},
                {"id": {"id": "c2", "parentId": {"id": "p"}}, "taskName": "Stream Processor B", "nodeName": "node1",
                  "userRef": {"uuid": "u-admin", "displayName": "admin"},
                  "submitTimeMs": 1700000006000, "timeNowMs": 1700000010000, "taskInfo": "stream 2",
                  "filterMatchState": "B_STATE"}
              ],
              "errors": [],
              "pageResponse": {"offset": 0, "length": 3, "total": 3, "exact": true}
            }""";

    private static final String TASK_INFO = "node=node1, feed=TEST_FEED, filter id=7, pipeline uuid=pipe-uuid-1, "
            + "meta_id=5";
    // The task info only holds the feed's name, so 'Open Feed' looks up its doc ref by name
    // (FeedResource.getDocRefForName) when it is chosen
    private static final String FEED_LOOKUP_PATH = "/feed/v1/getDocRefForName/TEST_FEED";
    private static final String FEED_DOC_REF = """
            {"type": "Feed", "uuid": "feed-uuid-1", "name": "TEST_FEED"}""";

    private static final RestFixtures FIXTURES = fixtures(TASK_INFO, RestReply.json(FEED_DOC_REF));
    // The feed has gone since the task started: the lookup finds nothing (the server's null)
    private static final RestFixtures FIXTURES_WITHOUT_FEED_DOC = fixtures(TASK_INFO, RestReply.noContent());

    // A task with no node at all
    private static final RestFixtures ORPHAN_FIXTURES = fixtures(TASK_INFO, RestReply.json("""
            {
              "values": [
                {"id": {"id": "solo"}, "taskName": "Orphan Task",
                  "userRef": {"uuid": "u-admin", "displayName": "admin"},
                  "submitTimeMs": 1700000000000, "timeNowMs": 1700000010000, "taskInfo": "no node",
                  "filterMatchState": "MATCHED"}
              ],
              "errors": [],
              "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}
            }"""), RestReply.json(FEED_DOC_REF));

    private ServerTasksScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/ServerTasksScreen", ServerTasksScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The task tree loads collapsed; expanding the parent shows its child tasks, and a
                // selected task can be terminated (sent to its own node)
                .story("TaskTree", context -> render(context, FIXTURES, null))
                .withPlay(play -> {
                    // The root task loads; its children are collapsed (older than 1s, no filter)
                    play.findByText("Pipeline Processor");
                    play.expect(play.queryByText("Stream Processor A")).toBeNull();
                    // Expand the parent: the children appear
                    play.click(play.querySelector(".expanderIcon.active"));
                    play.findByText("Stream Processor A");
                    play.findByText("Stream Processor B");
                    // Tick child A (a TickBoxCell, not an <input>) and terminate it
                    play.click(play.within(play.getByText("Stream Processor A").closest("tr"))
                            .querySelector(".tickBox"));
                    play.click(play.getByRole("button", "Terminate Task"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/task/v1/terminate/node1")
                                    .withJsonBodyContaining("{\"criteria\": {\"idSet\": [{\"id\": \"c1\"}]}}")
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                })
                // Right-clicking the Info cell parses the task info for Open Feed / Open Pipeline
                .story("InfoActions", context -> render(context, FIXTURES, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Pipeline Processor");
                    play.rightClick(infoCell(play, "Pipeline Processor"));
                    // Every item the task info's keys allow is shown; the per-cell items come first,
                    // before the grid's own items
                    play.waitFor(() -> play.expect(firstMenuItems(screen, 4)).toEqual(
                            Arrays.asList("Open Feed", "Show Filter Tasks", "Open Pipeline", "Open Stream")));
                    play.click(screen.getByText("Open Pipeline"));
                    play.waitFor(() -> play.expect(play.spy(ON_OPEN_DOC)).toHaveBeenCalledWith("Pipeline:pipe-uuid-1"));
                    // Open Feed looks the feed up by name and opens the doc ref found (a DocRef
                    // needs a UUID, so the menu once failed for a task with a feed)
                    play.rightClick(infoCell(play, "Pipeline Processor"));
                    play.click(screen.findByText("Open Feed"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.get(FEED_LOOKUP_PATH).toSpyMatcher()));
                    play.waitFor(() -> play.expect(play.spy(ON_OPEN_DOC)).toHaveBeenCalledWith("Feed:feed-uuid-1"));
                    expectNoProblems(play);
                })
                // STREAM_ID → ShowDataEvent(SourceLocation(streamId), INFO, STROOM_TAB); FILTER_ID →
                // OpenProcessorTaskEvent(filter), both gated on their key being in the task info
                // (Here the feed has gone, so Open Feed can't find it)
                .story("InfoActionsStreamAndFilter", context -> render(context, FIXTURES_WITHOUT_FEED_DOC, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Pipeline Processor");
                    play.rightClick(infoCell(play, "Pipeline Processor"));
                    // GWT's buildMenuItems order
                    play.waitFor(() -> play.expect(firstMenuItems(screen, 4)).toEqual(
                            Arrays.asList("Open Feed", "Show Filter Tasks", "Open Pipeline", "Open Stream")));
                    // meta_id=5 → the INFO view in a Stroom tab, exactly GWT's ShowDataEvent arguments
                    play.click(screen.findByText("Open Stream"));
                    play.waitFor(() -> play.expect(play.spy(ON_OPEN_DATA))
                            .toHaveBeenCalledWith("data:5:INFO:STROOM_TAB"));
                    // 'filter id=7' → the filter's own task tab
                    play.rightClick(infoCell(play, "Pipeline Processor"));
                    play.click(screen.findByText("Show Filter Tasks"));
                    play.waitFor(() -> play.expect(play.spy(ON_SHOW_FILTER_TASKS))
                            .toHaveBeenCalledWith("filterTasks:7"));
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                    // A feed that can't be found by name is reported, and nothing opens
                    play.rightClick(infoCell(play, "Pipeline Processor"));
                    play.click(screen.findByText("Open Feed"));
                    // The alert's text is HTML (the quotes are escaped)
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY)).toHaveBeenCalledWith(
                            ValueMatcher.stringContaining("ERROR: Unable to find feed &#39;TEST_FEED&#39;")));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.get(FEED_LOOKUP_PATH).toSpyMatcher());
                    play.expect(play.spy(ON_OPEN_DOC)).not().toHaveBeenCalled();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // A task whose info has no filter/stream keys shows neither item (GWT's per-key gating)
                .story("InfoActionsGatedByKeys", context -> render(context, FIXTURES, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Pipeline Processor");
                    play.click(play.querySelector(".expanderIcon.active"));
                    play.findByText("Stream Processor A");
                    // No row has a '...' button: the items are in the Info cell's context menu, which
                    // has none of the task info items for a row whose info has no keys
                    play.rightClick(infoCell(play, "Stream Processor A"));
                    play.sleep(200);
                    play.expect(screen.queryByText("Open Stream")).toBeNull();
                    play.expect(screen.queryByText("Show Filter Tasks")).toBeNull();
                    play.expect(screen.queryByText("Open Feed")).toBeNull();
                    expectNoProblems(play);
                })
                // The quick filter keeps matched tasks and their ancestors (expanded), hiding the rest
                .story("QuickFilter", context -> render(context, FIXTURES, null))
                .withPlay(play -> {
                    play.findByText("Pipeline Processor");
                    // The quick filter has no label, only a placeholder
                    play.type(play.getByPlaceholderText(StroomDom.QUICK_FILTER_PLACEHOLDER), "Processor A");
                    // Debounced fetch: the matched task's ancestor expands so it appears; its
                    // sibling B stays hidden
                    play.waitFor(3000, () -> play.expect(play.getByText("Stream Processor A")).toBeInTheDocument());
                    play.expect(play.getByText("Pipeline Processor")).toBeInTheDocument();
                    play.expect(play.queryByText("Stream Processor B")).toBeNull();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(filteredFind());
                    expectNoProblems(play);
                })
                // The quick filter is set on open (GWT changeNameFilter, e.g. the Jobs 'Show in Server
                // Tasks' cross navigation), so the results arrive filtered without typing
                .story("SeededFilter", context -> render(context, FIXTURES, "Processor A"))
                .withPlay(play -> {
                    // The quick filter has no label, only a placeholder
                    play.waitFor(() -> play.expect(play.getByPlaceholderText(StroomDom.QUICK_FILTER_PLACEHOLDER))
                            .toHaveValue("Processor A"));
                    play.waitFor(3000, () -> play.expect(play.getByText("Stream Processor A")).toBeInTheDocument());
                    play.expect(play.queryByText("Stream Processor B")).toBeNull();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(filteredFind());
                    expectNoProblems(play);
                })
                // TaskManagerListPresenter details visible on one flat page: '?' for a task with no
                // node, and no expander column when no row is a parent
                .story("FlatListDetails", context -> render(context, ORPHAN_FIXTURES, null))
                .withPlay(play -> {
                    play.findByText("Orphan Task");
                    // An unknown node reads '?', not blank (scoped to the row: the pager also shows '?')
                    play.expect(play.within(play.getByText("Orphan Task").closest("tr")).getByText("?"))
                            .toBeInTheDocument();
                    // Nothing is expandable, so the expander column (the second) collapses.
                    // The grid is a table, so this checks the expander column's <col>, which GWT gives
                    // 1px
                    play.expect(play.querySelector("colgroup col:nth-child(2)").attribute("style")).toBe("width: 1px;");
                    expectNoProblems(play);
                })
                // The first fetch sorts by Age descending (TaskManagerListPresenter's constructor)
                .story("DefaultSortIsAgeDescending", context -> render(context, FIXTURES, null))
                .withPlay(play -> {
                    play.findAllByRole("row");
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(TASK_FIND_PATH)
                                    .withJsonBodyContaining("{\"criteria\": {\"sortList\": "
                                            + "[{\"id\": \"Age\", \"desc\": true, \"ignoreCase\": false}]}}")
                                    .toSpyMatcher()));
                    // A request default, not a grid one: no header has a sort indicator
                    play.expect(play.querySelector(".column-sortIcon")).toBeNull();
                    expectNoProblems(play);
                });
    }

    // The Info cell of a task's row: the last column, the only one with a context menu
    private static Query infoCell(final Play play, final String taskName) {
        return play.within(play.getByText(taskName).closest("tr")).querySelector("td:last-child");
    }

    // The texts of the first items of the menu shown
    private static Supplier<List<String>> firstMenuItems(final Play screen, final int count) {
        final Value<List<String>> texts = screen.querySelectorAll(StroomDom.MENU_ITEM_TEXT).textContents();
        return () -> {
            final List<String> all = texts.get();
            return all.subList(0, Math.min(count, all.size()));
        };
    }

    private static ValueMatcher filteredFind() {
        return RequestMatcher.post(TASK_FIND_PATH)
                .withJsonBodyContaining("{\"criteria\": {\"nameFilter\": \"Processor A\"}}")
                .toSpyMatcher();
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    private static RestFixtures fixtures(final String parentTaskInfo, final RestReply feedLookup) {
        return fixtures(parentTaskInfo,
                RestReply.json(tasks(parentTaskInfo, "MATCHED", "MATCHED", "MATCHED")),
                feedLookup);
    }

    private static String tasks(final String parentTaskInfo,
                                final String parentState,
                                final String aState,
                                final String bState) {
        return TASKS.replace("TASK_INFO", parentTaskInfo)
                .replace("P_STATE", parentState)
                .replace("A_STATE", aState)
                .replace("B_STATE", bState);
    }

    private static RestFixtures fixtures(final String parentTaskInfo,
                                         final RestReply unfilteredTasks,
                                         final RestReply feedLookup) {
        return RestFixtures.builder()
                .post("/node/v1/find", RestReply.json(NODES))
                // The server marks the tasks matching the name filter
                .route(RequestMatcher.post(TASK_FIND_PATH)
                                .withJsonBodyContaining("{\"criteria\": {\"nameFilter\": \"Processor A\"}}"),
                        RestReply.json(tasks(parentTaskInfo, "NOT_MATCHED", "MATCHED", "NOT_MATCHED")))
                .post(TASK_FIND_PATH, unfilteredTasks)
                .post("/task/v1/terminate/node1", RestReply.json("true"))
                .get(FEED_LOOKUP_PATH, feedLookup)
                .build();
    }

    private static Widget render(final StoryContext context, final RestFixtures fixtures, final String filter) {
        final AppScreenGinjector injector = GWT.create(AppScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .build();

        harness.fn(ON_OPEN_DOC);
        harness.fn(ON_OPEN_DATA);
        harness.fn(ON_SHOW_FILTER_TASKS);
        // 'type:uuid', so that a feed opened by name shows the UUID it was resolved to
        harness.getEventBus().addHandler(OpenDocumentEvent.getType(), event ->
                harness.spy(ON_OPEN_DOC, event.getDocRef().getType() + ":" + event.getDocRef().getUuid()));
        harness.getEventBus().addHandler(ShowDataEvent.getType(), event ->
                harness.spy(ON_OPEN_DATA, "data:" + event.getSourceLocation().getMetaId() + ":"
                        + event.getDataViewType() + ":" + event.getDisplayMode()));
        harness.getEventBus().addHandler(OpenProcessorTaskEvent.getType(), event ->
                harness.spy(ON_SHOW_FILTER_TASKS, "filterTasks:" + event.getProcessorFilter().getId()));

        // Opened once Stroom has started (the screen reads the UI config), as TaskManagerPlugin
        // opens it, with the name filter of an OpenTaskManagerEvent if there is one
        harness.afterStartUp(() -> {
            final TaskManagerPresenter presenter = harness.addContent(injector.getTaskManagerPresenter());
            if (filter != null) {
                presenter.changeNameFilter(filter);
            }
        });
        return harness.asWidget();
    }
}
