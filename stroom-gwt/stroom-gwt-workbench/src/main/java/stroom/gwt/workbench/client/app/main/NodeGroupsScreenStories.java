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

import stroom.gwt.workbench.client.app.gin.processing.ProcessingScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.node.client.presenter.NodeGroupPresenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Main/NodeGroupsScreen` in the React Storybook, showing Stroom's real
/// [NodeGroupPresenter] (the 'Node Groups' tab, as `NodeGroupsPlugin` opens it) with fake REST
/// replies.
///
/// The React story's `NodeGroupApi` fixture becomes routes for Stroom's `NodeGroupResource`:
///
/// | React | Stroom |
/// |---|---|
/// | `findGroups` | `POST /node/nodeGroup/v2/find` (a sequence: the list as it changes) |
/// | `update` | `PUT /node/nodeGroup/v2/{id}` (echoes the group) |
/// | `create` | `GET /node/nodeGroup/v2/fetchByName/{name}` (the name check), then `POST /node/nodeGroup/v2` |
/// | `getMembers` | `GET /node/nodeGroup/v2/getNodeGroupStates/{id}` (the members dialog GWT opens next) |
///
/// and its recorder becomes checks on the request spy.
public final class NodeGroupsScreenStories {

    private static final String FIND_PATH = "/node/nodeGroup/v2/find";

    private static final String GROUPS = """
            {
              "values": [
                {"id": 1, "version": 1, "name": "All Nodes", "enabled": true, "invertSelection": false},
                {"id": 2, "version": 1, "name": "Processing", "enabled": false, "invertSelection": false}
              ],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}
            }""";

    private static final String GROUPS_ENABLED = """
            {
              "values": [
                {"id": 1, "version": 1, "name": "All Nodes", "enabled": true, "invertSelection": false},
                {"id": 2, "version": 2, "name": "Processing", "enabled": true, "invertSelection": false}
              ],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}
            }""";

    private static final String GROUPS_CREATED = """
            {
              "values": [
                {"id": 1, "version": 1, "name": "All Nodes", "enabled": true, "invertSelection": false},
                {"id": 2, "version": 2, "name": "Processing", "enabled": true, "invertSelection": false},
                {"id": 12, "version": 1, "name": "Reporting", "enabled": true, "invertSelection": false}
              ],
              "pageResponse": {"offset": 0, "length": 3, "total": 3, "exact": true}
            }""";

    private static final String REPORTING = """
            {"id": 12, "version": 1, "name": "Reporting", "enabled": true, "invertSelection": false}""";

    private static final String NODES = """
            {
              "values": [
                {"master": true, "node": {"id": 1, "name": "node1", "url": "http://node1:8080/stroom",
                  "enabled": true}}
              ],
              "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}
            }""";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            // The list as loaded, after 'Processing' is enabled and after 'Reporting' is created
            // (NodeGroupPresenter's constructor and the grid's first range change each load it)
            .post(FIND_PATH,
                    RestReply.json(GROUPS),
                    RestReply.json(GROUPS),
                    RestReply.json(GROUPS_ENABLED),
                    RestReply.json(GROUPS_CREATED))
            .put("/node/nodeGroup/v2/2", request -> RestReply.json(request.getBody()))
            // No group has the name yet
            .get("/node/nodeGroup/v2/fetchByName/Reporting", RestReply.noContent())
            .post("/node/nodeGroup/v2", RestReply.json(REPORTING))
            // GWT opens the new group's members dialog
            .post("/node/v1/find", RestReply.json(NODES))
            .get("/node/nodeGroup/v2/getNodeGroupStates/12", RestReply.json("{\"selected\": []}"))
            .build();

    private NodeGroupsScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/NodeGroupsScreen", NodeGroupsScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Toggling Enabled updates the group; New creates one via the name dialog
                .story("CrudFlow", NodeGroupsScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("All Nodes");
                    play.findByText("Processing");
                    // Toggle "Processing" enabled -> update. GWT's grid tick is a TickBoxCell div.
                    // Differs from React: GWT's grid rows are <tr> elements with no role attribute
                    play.click(play.within(play.getByText("Processing").closest("tr")).querySelector(".tickBox"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put("/node/nodeGroup/v2/2")
                                    .withJsonBodyContaining("{\"id\": 2, \"enabled\": true}")
                                    .toSpyMatcher()));
                    // New group via the create dialog.
                    // Differs from React: the 'New' button is an icon button titled 'New'
                    play.click(play.getByTitle("New"));
                    screen.findByText("New", StroomDom.DIALOG_TITLE);
                    // Differs from React: the name field is Stroom's shared NameDocumentViewImpl
                    // ('nameDocumentName'), not a 'newNodeGroupName' field
                    play.type(screen.querySelector("#nameDocumentName"), "Reporting");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/node/nodeGroup/v2").withBodyContaining("Reporting")
                                    .toSpyMatcher()));
                    play.findByText("Reporting");
                    // Differs from React: GWT then opens the new group's members dialog
                    screen.findByText("Edit Node Group - Reporting");
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                });
    }

    private static Widget render(final StoryContext context) {
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .build();
        harness.afterStartUp(() -> harness.addContent(injector.getNodeGroupPresenter()));
        return harness.asWidget();
    }
}
