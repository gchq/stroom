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
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.node.client.presenter.NodePresenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Main/NodesScreen` in the React Storybook, showing Stroom's real
/// [NodePresenter] (the 'Nodes' tab: the node list and the selected node's jobs) with fake REST
/// replies.
///
/// The React story's `NodeApi` and `JobApi` fixtures become routes for Stroom's `NodeResource`
/// (`fetchNodes` → `POST /node/v1/find`, `ping` → `GET /node/v1/ping/{node}`, `setEnabled` →
/// `PUT /node/v1/enabled/{node}`, `listEnabledNodes` → `GET /node/v1/enabled`) and
/// `JobNodeResource` (`findJobNodes` → `POST /jobNode/v1/find`, by the criteria's `nodeName`), and
/// its recorders become checks on the request spy. Its `nodeMonitoring` UI config is the harness's.
/// The presenter comes from GIN.
public final class NodesScreenStories {

    private static final String TEXT_ALL_JOBS_ON_NODE1A = "All jobs on node 'node1a'";

    private static final String NODE_FIND_PATH = "/node/v1/find";
    private static final String JOB_NODE_FIND_PATH = "/jobNode/v1/find";

    // NodeResource.find()
    private static final String NODES = """
            {
              "values": [
                {"master": true, "node": {"name": "node1a", "url": "http://node1a:8080", "buildVersion": "7.5",
                  "priority": 1, "enabled": true, "lastBootMs": 1700000000000}},
                {"master": false, "node": {"name": "node2b", "url": "http://node2b:8080", "buildVersion": "7.5",
                  "priority": 5, "enabled": false, "lastBootMs": 1700000000000}}
              ],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}
            }""";

    // JobNodeResource.find() for node1a, which runs a Data Retention job (node2b runs none)
    private static final String NODE1A_JOBS = """
            {
              "values": [
                {
                  "jobNode": {"id": 21, "job": {"id": 1, "name": "Data Retention", "enabled": true},
                    "jobType": "CRON", "nodeName": "node1a", "enabled": true, "schedule": "0 0 * * ?"},
                  "jobNodeInfo": {"lastExecutedTime": 1700000000000, "nextScheduledTime": 1700003600000,
                    "currentTaskCount": 0}
                }
              ],
              "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}
            }""";

    private static final String NO_JOBS = """
            {"values": [], "pageResponse": {"offset": 0, "length": 0, "total": 0, "exact": true}}""";

    private static final String UI_CONFIG = """
            {"nodeMonitoring": {"pingWarnThreshold": 100, "pingMaxThreshold": 1000}}""";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .post(NODE_FIND_PATH, RestReply.json(NODES))
            .get("/node/v1/ping/node1a", RestReply.json("42"))
            .get("/node/v1/ping/node2b", RestReply.json("850"))
            .put("/node/v1/enabled/node2b", RestReply.json("true"))
            .get("/node/v1/enabled", RestReply.json("[\"node1a\"]"))
            .route(RequestMatcher.post(JOB_NODE_FIND_PATH)
                            .withJsonBodyContaining("{\"nodeName\": {\"string\": \"node1a\"}}"),
                    RestReply.json(NODE1A_JOBS))
            .post(JOB_NODE_FIND_PATH, RestReply.json(NO_JOBS))
            .build();

    private NodesScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/NodesScreen", NodesScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // GWT NodeStatusListPresenter sorts on the server: CriteriaUtil.setSortList puts the
                // grid's column sort into FindNodeStatusCriteria on every load
                .story("ServerSideSorting", context -> render(context, null))
                .withPlay(play -> {
                    play.findByText("node1a");
                    // Sorting a column queries the server again with GWT's field id
                    play.click(play.getByText("Name"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(NODE_FIND_PATH)
                                    .withJsonBodyContaining("{\"sortList\": [{\"id\": \"Name\", \"desc\": false}]}")
                                    .toSpyMatcher()));
                    // The endpoint column maps to GWT's FIELD_ID_URL, not its display text
                    play.click(play.getByText("Cluster Base Endpoint"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(NODE_FIND_PATH)
                                    .withJsonBodyContaining("{\"sortList\": [{\"id\": \"URL\", \"desc\": false}]}")
                                    .toSpyMatcher()));
                    // Ping has no withSorting in GWT, so its header isn't sortable.
                    // Differs from React: the headers are <th> elements (no role attribute), marked
                    // sortable with GWT's 'dataGridSortableHeader' class (React's /sortable/)
                    play.expect(play.getByText("Ping (ms)").closest("th")).not().toHaveClass(StroomDom.SORTABLE_HEADER);
                    play.expect(play.getByText("Name").closest("th")).toHaveClass(StroomDom.SORTABLE_HEADER);
                    expectNoProblems(play);
                })
                // Selecting a node lists its jobs in the bottom pane (GWT NodeJobListPresenter)
                .story("NodeJobs", context -> render(context, null))
                .withPlay(play -> {
                    play.findByText("node1a");
                    // Before selecting, the bottom pane is blank: GWT's job list is a plain grid with
                    // no empty-state text, and no heading until a node is selected
                    play.expect(play.queryByText(TEXT_ALL_JOBS_ON_NODE1A)).toBeNull();
                    play.expect(play.queryByText("Data Retention")).toBeNull();
                    // Select node1a: its job list loads in the bottom pane
                    play.click(play.getByText("node1a"));
                    play.findByText("Data Retention");
                    // Differs from React: React checks there is no 'Jobs on node' heading, which GWT
                    // doesn't show either (that check can't fail); GWT heads the pane 'All jobs on
                    // node ...' (NodeJobListPresenter.updateFormGroupHeading)
                    play.expect(play.getByText(TEXT_ALL_JOBS_ON_NODE1A)).toBeInTheDocument();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(JOB_NODE_FIND_PATH)
                                    .withJsonBodyContaining("{\"nodeName\": {\"string\": \"node1a\"}}")
                                    .toSpyMatcher());
                    expectNoProblems(play);
                })
                // The named node is selected on open (GWT OpenNodeEvent, the Jobs 'Node' link), so its
                // job list loads without a click
                .story("PreselectNode", context -> render(context, "node1a"))
                .withPlay(play -> {
                    play.findByText("node1a");
                    play.findByText("Data Retention");
                    play.waitFor(() -> play.expect(play.getByText("node1a").closest("tr"))
                            .toHaveClass(StroomDom.SELECTED_ROW));
                    expectNoProblems(play);
                })
                // The node grid loads, pings each node, and toggling Enabled sends a per-node PUT
                .story("NodeGrid", context -> render(context, null))
                .withPlay(play -> {
                    play.findByText("node1a");
                    play.findByText("node2b");
                    // Pings resolve per node. GWT prints the number only and puts the unit in the title
                    play.waitFor(() -> play.expect(play.getByText("42")).toBeInTheDocument());
                    play.expect(play.getByText("42").closest("[title]").attribute("title")).toBe("Ping: 42 ms");
                    // Toggle node2b's Enabled tick box: the last tick in the row (Master is read only)
                    final Query ticks = play.within(play.getByText("node2b").closest("tr"))
                            .querySelectorAll(".tickBox");
                    play.click(ticks.nth(1));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put("/node/v1/enabled/node2b").withJsonBody("true").toSpyMatcher()));
                    expectNoProblems(play);
                });
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    private static Widget render(final StoryContext context, final String selectedNode) {
        final AppScreenGinjector injector = GWT.create(AppScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .uiConfig(UI_CONFIG)
                .build();
        final NodePresenter presenter = harness.addContent(injector.getNodePresenter());
        if (selectedNode != null) {
            // As NodeMonitoringPlugin does for an OpenNodeEvent with a node name
            presenter.setSelected(selectedNode);
        }
        return harness.asWidget();
    }
}
