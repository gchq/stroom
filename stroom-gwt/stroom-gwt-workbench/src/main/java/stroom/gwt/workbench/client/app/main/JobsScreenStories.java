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

import stroom.gwt.workbench.client.app.gin.AppScreenGinjector;
import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.job.client.presenter.JobPresenter;
import stroom.node.client.event.OpenNodeEvent;
import stroom.task.client.event.OpenTaskManagerEvent;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.Widget;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Stories matching `App/Main/JobsScreen` in the React Storybook, showing Stroom's real
/// [JobPresenter] (the 'Jobs' tab: the jobs list and the selected job's per-node list) with fake
/// REST replies.
///
/// The React story's `JobApi` fixture becomes routes for Stroom's `JobResource`, `JobNodeResource`,
/// `NodeResource` and `ScheduledTimeResource`:
///
/// | React | Stroom |
/// |---|---|
/// | `listJobs` | `GET /job/v1` |
/// | `findJobNodes` | `POST /jobNode/v1/find` (by the criteria's `jobName`) |
/// | `jobNodeInfo` | `GET /jobNode/v1/info?jobName=&nodeName=` |
/// | `listEnabledNodes` | `GET /node/v1/enabled` |
/// | `getScheduledTimes` | `POST /scheduledTime/v1` |
/// | `executeJobNode` | `POST /jobNode/v1/{id}/execute` |
/// | `setJobNodeSchedule` | `PUT /jobNode/v1/{id}/schedule` |
/// | `setJobNodeScheduleBatch` | `PUT /jobNode/v1/schedule` |
///
/// and its recorder becomes checks on the request spy. React's `onOpenNode`/`onShowTasks` are spies
/// on Stroom's `OpenNodeEvent`/`OpenTaskManagerEvent`. The presenter comes from GIN.
public final class JobsScreenStories {

    /// The name of the spy recording the nodes opened (Stroom's `OpenNodeEvent`).
    static final String ON_OPEN_NODE = "onOpenNode";
    /// The name of the spy recording the server tasks shown (Stroom's `OpenTaskManagerEvent`).
    static final String ON_SHOW_TASKS = "onShowTasks";

    // The title of a schedule link's 'open' icon
    private static final String EDIT_SCHEDULE = "Edit schedule";
    // The schedule editor's expression field (its FormGroup's identity is scheduleExpression).
    // Differs from React: the field's id is the identity itself, not React's 'scheduleExpression-input'
    private static final String EXPRESSION_INPUT = "#scheduleExpression";

    private static final String JOB_NODE_FIND_PATH = "/jobNode/v1/find";

    // JobResource.list(). GWT sorts advanced jobs last (the server does); the presenter adds an
    // empty row before the first advanced one
    private static final String JOBS = """
            {
              "values": [
                {"id": 1, "name": "Data Retention", "enabled": true, "description": "Delete old data",
                  "advanced": false},
                {"id": 2, "name": "XX Processor", "enabled": false, "description": "Process streams",
                  "advanced": false},
                {"id": 3, "name": "Property Cache Reload", "enabled": true, "description": "Advanced",
                  "advanced": true}
              ],
              "pageResponse": {"offset": 0, "length": 3, "total": 3, "exact": true}
            }""";

    private static final String DATA_RETENTION = """
            {"id": 1, "name": "Data Retention", "enabled": true, "description": "Delete old data",
              "advanced": false}""";

    // JobNodeResource.find() for 'Data Retention': a CRON node with its info, and a DISTRIBUTED
    // node whose info is fetched lazily
    private static final String JOB_NODES = """
            {
              "values": [
                {
                  "jobNode": {"id": 11, "job": JOB, "jobType": "CRON", "nodeName": "node1", "enabled": true,
                    "schedule": "0 0 * * ?"},
                  "jobNodeInfo": {"lastExecutedTime": 1700000000000, "nextScheduledTime": 1700003600000,
                    "currentTaskCount": 0}
                },
                {
                  "jobNode": {"id": 12, "job": JOB, "jobType": "DISTRIBUTED", "nodeName": "node2",
                    "enabled": true, "taskLimit": 20}
                }
              ],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}
            }""".replace("JOB", DATA_RETENTION);

    // Two CRON nodes of one job, for the multi-select batch path
    private static final String CRON_JOB_NODES = """
            {
              "values": [
                {
                  "jobNode": {"id": 11, "job": JOB, "jobType": "CRON", "nodeName": "node1", "enabled": true,
                    "schedule": "0 0 * * ?"},
                  "jobNodeInfo": {"currentTaskCount": 0}
                },
                {
                  "jobNode": {"id": 13, "job": JOB, "jobType": "CRON", "nodeName": "node3", "enabled": true,
                    "schedule": "0 0 * * ?"},
                  "jobNodeInfo": {"currentTaskCount": 0}
                }
              ],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}
            }""".replace("JOB", DATA_RETENTION);

    private static final String NO_JOB_NODES = """
            {"values": [], "pageResponse": {"offset": 0, "length": 0, "total": 0, "exact": true}}""";

    private static final RestFixtures FIXTURES = fixtures(JOB_NODES);
    private static final RestFixtures CRON_FIXTURES = fixtures(CRON_JOB_NODES);

    private JobsScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/JobsScreen", JobsScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Selecting a job loads its per-node schedule; 'Run Now' on a schedulable node confirms
                // then executes it
                .story("JobSchedule", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Data Retention");
                    play.findByText("XX Processor");
                    // Select the job: the bottom per-node grid loads
                    play.click(play.getByText("Data Retention"));
                    play.findByText("node1");
                    play.findByText("node2");
                    // Types render; the CRON node has a schedule, the DISTRIBUTED node has no Run Now
                    play.expect(play.getByText("Cron")).toBeInTheDocument();
                    play.expect(play.getByText("Distributed")).toBeInTheDocument();
                    play.expect(play.getByText("0 0 * * ?")).toBeInTheDocument();
                    // Run Now on node1 is an item of the row's action menu
                    play.click(play.within(row(play, "node1")).getByTitle(StroomDom.ACTIONS_TITLE));
                    play.click(screen.findByText("Run Job on 'node1' Now"));
                    play.waitFor(() -> play.expect(screen.getByText(
                            TextMatch.containing("execute job 'Data Retention' on node 'node1'"))).toBeInTheDocument());
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/jobNode/v1/11/execute").toSpyMatcher()));
                    expectNoProblems(play);
                })
                // GWT's JobListPresenter.changeData inserts an empty row before the first advanced job
                .story("AdvancedGap", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    final Query firstAdvanced = play.findByText("Property Cache Reload").closest("tr");
                    // The inserted row is a real row with no job in it
                    play.expect("the text of the row above", () -> previousRowText(firstAdvanced.element().get()))
                            .toBe("");
                    // The non-advanced jobs above it still carry their text
                    final Query above = play.getByText("Data Retention").closest("tr");
                    play.expect("the text of the 'Data Retention' row", () -> rowText(above.element().get()))
                            .not().toBe("");
                    expectNoProblems(play);
                })
                // Batch schedule set (JobNodeListHelper.setSchedule with more than one row selected)
                .story("BatchSchedule", context -> render(context, CRON_FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Data Retention");
                    play.click(play.getByText("Data Retention"));
                    play.findByText("node1");
                    play.findByText("node3");
                    // Multi-select the two CRON nodes: select node1, then Ctrl-mousedown node3.
                    // Differs from React: a selected row has GWT's 'dataGridSelectedRow' class, not
                    // aria-selected="true"
                    play.fireEvent().mouseDown(play.getByText("node1"));
                    play.waitFor(() -> play.expect(row(play, "node1")).toHaveClass(StroomDom.SELECTED_ROW));
                    play.fireEvent().mouseDown(play.getByText("node3"), EventInit.create().ctrlKey());
                    play.waitFor(() -> play.expect(row(play, "node3")).toHaveClass(StroomDom.SELECTED_ROW));
                    play.expect(row(play, "node1")).toHaveClass(StroomDom.SELECTED_ROW);
                    // Open the schedule editor from a selected row's link.
                    // Differs from React: GWT's CommandLinkCell runs its command on a mousedown on
                    // its 'open' icon (not a click on its text) and the grid then leaves the
                    // selection alone, so the multi-selection is kept
                    play.fireEvent().mouseDown(openIcon(play.within(play.getAllByTitle(EDIT_SCHEDULE).nth(0))));
                    // Differs from React: GWT asks to confirm the batch change (naming the nodes) before
                    // it shows the schedule editor; React asks after the editor's OK
                    play.waitFor(() -> play.expect(screen.getByText(TextMatch.containing("for 2 nodes")))
                            .toBeInTheDocument());
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.getByText("Change Schedule")).toBeInTheDocument());
                    final Query expression = screen.querySelector(EXPRESSION_INPUT);
                    play.clear(expression);
                    play.type(expression, "0 /10 * * * ?");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    // One batch request for both ids
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put("/jobNode/v1/schedule")
                                    .withJsonBodyContaining("{\"schedule\": {\"expression\": \"0 /10 * * * ?\"}}")
                                    .withBody("jobNodeIds are 11 and 13", JobsScreenStories::hasIds11And13)
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                })
                // Cross navigation (OpenNodeEvent / OpenTaskManagerEvent)
                .story("CrossNavigation", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Data Retention");
                    play.click(play.getByText("Data Retention"));
                    play.findByText("node1");
                    // The Node cell is a link that opens the Nodes screen.
                    // Differs from React: GWT's CommandLink isn't a button; pressing its 'open' icon,
                    // titled with what it opens, runs its command
                    play.click(openIcon(play.within(
                            play.getByTitle("Open node 'node1' and job 'Data Retention' on the Nodes screen."))));
                    play.waitFor(() -> play.expect(play.spy(ON_OPEN_NODE)).toHaveBeenCalledWith("node1"));
                    // The row's action menu: GWT's buildActionMenu yields four items in this order
                    play.click(play.getAllByTitle(StroomDom.ACTIONS_TITLE).nth(0));
                    play.expect(screen.findByText("Edit Schedule")).toBeInTheDocument();
                    play.expect(screen.getByText("Run Job on 'node1' Now")).toBeInTheDocument();
                    play.click(screen.findByText("Show in Server Tasks (node1)"));
                    // Differs from React: the spy records OpenTaskManagerEvent's node and task names;
                    // the 'node:node1 name:"Data Retention"' filter is built from them by the Server
                    // Tasks screen (TaskManagerPresenter.changeNameFilter)
                    play.waitFor(() -> play.expect(play.spy(ON_SHOW_TASKS))
                            .toHaveBeenCalledWith("node=node1, task=Data Retention"));
                    expectNoProblems(play);
                })
                // Clicking a Schedule link opens the editor; OK saves the edited schedule
                .story("EditSchedule", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Data Retention");
                    play.click(play.getByText("Data Retention"));
                    // node1 (CRON) has a Schedule link showing its expression.
                    // Differs from React: GWT's CommandLinkCell runs its command when its 'open'
                    // icon (titled 'Edit schedule') is pressed, not its text
                    play.findByText("0 0 * * ?");
                    play.click(openIcon(play.within(play.getByTitle(EDIT_SCHEDULE))));
                    play.waitFor(() -> play.expect(screen.getByText("Change Schedule")).toBeInTheDocument());
                    final Query expression = screen.querySelector(EXPRESSION_INPUT);
                    play.clear(expression);
                    play.type(expression, "0 /5 * * * ?");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put("/jobNode/v1/11/schedule")
                                    .withJsonBodyContaining("{\"type\": \"CRON\", \"expression\": \"0 /5 * * * ?\"}")
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                });
    }

    // Differs from React: GWT's grid rows are <tr> elements with no role attribute (their role is
    // implicit), so a cell's row is its closest <tr>, not its closest [role="row"]
    private static Query row(final Play play, final String text) {
        return play.getByText(text).closest("tr");
    }

    // The 'open' icon of a CommandLinkCell, which runs the link's command when pressed
    private static Query openIcon(final Play link) {
        return link.querySelector(StroomDom.COMMAND_LINK_OPEN);
    }

    private static String rowText(final Element row) {
        return row.getInnerText().trim();
    }

    private static String previousRowText(final Element row) {
        final Element previous = row.getPreviousSiblingElement();
        return previous == null
                ? null
                : rowText(previous);
    }

    private static boolean hasIds11And13(final String body) {
        if (body == null) {
            return false;
        }
        final Object ids = ((Map<?, ?>) JsonValues.parse(body)).get("jobNodeIds");
        if (!(ids instanceof List)) {
            return false;
        }
        final Set<Object> expected = new HashSet<>(Arrays.asList(BigDecimal.valueOf(11), BigDecimal.valueOf(13)));
        return ((List<?>) ids).size() == 2 && expected.equals(new HashSet<>((List<?>) ids));
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    private static RestFixtures fixtures(final String dataRetentionNodes) {
        return RestFixtures.builder()
                .get("/job/v1", RestReply.json(JOBS))
                .route(RequestMatcher.post(JOB_NODE_FIND_PATH)
                                .withJsonBodyContaining("{\"jobName\": {\"string\": \"Data Retention\"}}"),
                        RestReply.json(dataRetentionNodes))
                .post(JOB_NODE_FIND_PATH, RestReply.json(NO_JOB_NODES))
                .get("/jobNode/v1/info", RestReply.json(
                        "{\"lastExecutedTime\": 1700000000000, \"nextScheduledTime\": 1700003600000, "
                                + "\"currentTaskCount\": 2}"))
                .get("/node/v1/enabled", RestReply.json("[\"node1\", \"node2\", \"node3\"]"))
                .post("/scheduledTime/v1", RestReply.json("{\"nextScheduledTimeMs\": 1700003600000}"))
                .post("/jobNode/v1/11/execute", RestReply.json("true"))
                .put("/jobNode/v1/11/schedule", RestReply.noContent())
                .put("/jobNode/v1/schedule", RestReply.noContent())
                .build();
    }

    private static Widget render(final StoryContext context, final RestFixtures fixtures) {
        final AppScreenGinjector injector = GWT.create(AppScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                // The confirmations are answered in Stroom's real dialog
                .realAlerts()
                .build();

        harness.fn(ON_OPEN_NODE);
        harness.fn(ON_SHOW_TASKS);
        harness.getEventBus().addHandler(OpenNodeEvent.getType(), event ->
                harness.spy(ON_OPEN_NODE, event.getJobNode() != null
                        ? event.getJobNode().getNodeName()
                        : event.getNodeName()));
        harness.getEventBus().addHandler(OpenTaskManagerEvent.getType(), event ->
                harness.spy(ON_SHOW_TASKS, "node=" + event.getNodeName() + ", task=" + event.getTaskName()));

        harness.addContent(injector.getJobPresenter());
        return harness.asWidget();
    }
}
