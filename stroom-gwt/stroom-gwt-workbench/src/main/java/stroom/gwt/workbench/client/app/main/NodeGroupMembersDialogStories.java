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
import stroom.gwt.workbench.client.app.rest.JsonValues;
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
import stroom.node.client.presenter.NodeGroupEditPresenter;
import stroom.node.shared.NodeGroup;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/// Stories matching `App/Main/NodeGroupMembersDialog` in the React Storybook, showing Stroom's real
/// [NodeGroupEditPresenter] (the 'Edit Node Group - name' dialog, as `NodeGroupPresenter` opens
/// it) with fake REST replies.
///
/// | React | Stroom |
/// |---|---|
/// | `nodeApi.fetchNodes` | `POST /node/v1/find` |
/// | `groupApi.getMembers` | `GET /node/nodeGroup/v2/getNodeGroupStates/{id}` |
/// | `groupApi.setMembers` | `POST /node/nodeGroup/v2/updateNodeGroupState` |
///
/// The recorder's checks become checks on the request spy.
public final class NodeGroupMembersDialogStories {

    private static final String UPDATE_PATH = "/node/nodeGroup/v2/updateNodeGroupState";

    private static final String NODES = """
            {
              "values": [
                {"master": true, "node": {"id": 1, "name": "node1", "url": "http://node1:8080/stroom"}},
                {"master": false, "node": {"id": 2, "name": "node2", "url": "http://node2:8080/stroom"}},
                {"master": false, "node": {"id": 3, "name": "node3"}}
              ],
              "pageResponse": {"offset": 0, "length": 3, "total": 3, "exact": true}
            }""";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .post("/node/v1/find", RestReply.json(NODES))
            .get("/node/nodeGroup/v2/getNodeGroupStates/7", RestReply.json("{\"selected\": [2]}"))
            .post(UPDATE_PATH, RestReply.json("true"))
            .build();

    private NodeGroupMembersDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/NodeGroupMembersDialog", NodeGroupMembersDialogStories.class)
                .layout(StoryLayout.CENTERED)
                // The node grid loads with node2 pre-selected; ticking node1 and OK saves both ids
                .story("Members", NodeGroupMembersDialogStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Edit Node Group - Indexers");
                    screen.findByText("node1");
                    // GWT column order: tickbox, Status, Name, Cluster Base Endpoint
                    // Differs from React: GWT's sortable headers (Name, Cluster Base Endpoint) have
                    // role="button", so the headers are the table's <th> cells
                    play.expect(screen.querySelectorAll("thead th").textContents())
                            .toEqual(List.of("", "Status", "Name", "Cluster Base Endpoint"));
                    // Differs from React: GWT's grid rows are <tr> elements with no role attribute
                    play.expect(screen.within(row(screen, "node1")).getByText("http://node1:8080/stroom"))
                            .toBeInTheDocument();
                    // Differs from React: the row's tick is a TickBoxCell div, not a checkbox
                    play.click(screen.within(row(screen, "node1")).querySelector(".tickBox"));
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(UPDATE_PATH)
                                    .withBody("selectedNodes are 1 and 2", body -> hasSelectedNodes(body, 1, 2))
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                })
                // Invert Selection flips the Status column and persists the invert flag
                .story("InvertSelection", NodeGroupMembersDialogStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("node2");
                    // node2 is selected -> "Included"; node1 is not -> "Excluded"
                    screen.within(row(screen, "node2")).findByText("Included");
                    play.expect(screen.within(row(screen, "node1")).getByText("Excluded")).toBeInTheDocument();
                    // The "Node Inclusion Behaviour" drop-down (NodeInclusionBehaviour)
                    play.click(screen.querySelector("#nodeInclusionBehaviour " + StroomDom.SELECTION_BOX));
                    play.click(screen.findByText("Exclude Selected"));
                    // Differs from React: GWT's onInvertSelectionChange inverts the set of ticked
                    // nodes as it inverts their meaning, so each node keeps its status: node2 stays
                    // 'Included' (now unticked) and node1 'Excluded' (now ticked)
                    play.waitFor(() -> play.expect(screen.within(row(screen, "node1")).getByText("Excluded"))
                            .toBeInTheDocument());
                    play.expect(screen.within(row(screen, "node2")).getByText("Included")).toBeInTheDocument();
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(UPDATE_PATH)
                                    .withJsonBodyContaining("{\"nodeGroup\": {\"invertSelection\": true}}")
                                    .withBody("selectedNodes are 1 and 3", body -> hasSelectedNodes(body, 1, 3))
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                });
    }

    // Differs from React: GWT's grid rows are <tr> elements with no role attribute
    private static Query row(final Play screen, final String text) {
        return screen.getByText(text).closest("tr");
    }

    private static boolean hasSelectedNodes(final String body, final int... ids) {
        if (body == null) {
            return false;
        }
        final Object selected = ((Map<?, ?>) JsonValues.parse(body)).get("selectedNodes");
        if (!(selected instanceof List)) {
            return false;
        }
        final Set<Object> expected = new HashSet<>();
        for (final int id : ids) {
            expected.add(BigDecimal.valueOf(id));
        }
        final List<?> actual = (List<?>) selected;
        return actual.size() == ids.length && expected.equals(new HashSet<>(actual));
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    private static Widget render(final StoryContext context) {
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .build();
        final NodeGroup group = NodeGroup.builder().id(7).version(1).name("Indexers").enabled(true).build();
        final NodeGroupEditPresenter presenter = injector.getNodeGroupEditPresenter();
        // As NodeGroupPresenter.edit(nodeGroup) shows it
        harness.afterStartUp(() -> show(presenter, group, "Edit Node Group - " + group.getName(), result -> {
        }));
        return harness.asWidget();
    }

    // NodeGroupEditPresenter.show is package private (only NodeGroupPresenter calls it), so it is
    // called with JSNI, which ignores Java's access rules
    private static native void show(NodeGroupEditPresenter presenter,
                                    NodeGroup nodeGroup,
                                    String title,
                                    Consumer<Boolean> consumer) /*-{
        presenter.@stroom.node.client.presenter.NodeGroupEditPresenter::show(
            Lstroom/node/shared/NodeGroup;Ljava/lang/String;Ljava/util/function/Consumer;)(
            nodeGroup, title, consumer);
    }-*/;
}
