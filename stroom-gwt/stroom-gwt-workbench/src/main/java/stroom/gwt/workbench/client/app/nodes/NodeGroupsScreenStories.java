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

package stroom.gwt.workbench.client.app.nodes;

import stroom.gwt.workbench.client.app.gin.processing.ProcessingScreenGinjector;
import stroom.gwt.workbench.client.app.rest.FakeRows;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.play.ValueMatcher;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.node.client.component.NodeGroupsController;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// Stories of `App/Main/NodeGroupsScreen`, showing Stroom's real [NodeGroupsController] (the 'Node
/// Groups' tab, as `NodeGroupsPlugin` opens it) with fake REST replies, for Stroom's
/// `NodeGroupResource`:
///
/// | Stroom endpoint | Used for |
/// |---|---|
/// | `POST /node/nodeGroup/v2/find` (a sequence: the list as it changes) | the groups |
/// | `PUT /node/nodeGroup/v2/{id}` (echoes the group) | updating a group |
/// | `GET /node/nodeGroup/v2/fetchByName/{name}` (the name check), then `POST /node/nodeGroup/v2` | creating a group |
/// | `GET /node/nodeGroup/v2/getNodeGroupStates/{id}` (the members dialog GWT opens next) | a group's members |
///
/// The requests are checked on the request spy.
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
            // (NodeGroupsController's constructor and the grid's first range change each load it)
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
                // Moving to another page deselects the group, so Edit and Delete can't act on a group
                // that can't be seen (UI-GUIDE.md, "What a list selects"); it once stayed selected,
                // out of sight
                .story("PageChangeDropsSelection", context -> render(context, pagedFixtures(150)))
                .withPlay(play -> {
                    play.click(play.findByText("Group 001"));
                    expectSelected(play, "Group 001", "true");
                    final Query edit = play.getByRole("button", TextMatch.containing("Edit Selected Node Group"));
                    play.waitFor(() -> play.expect(edit).not().toHaveAttribute("aria-disabled"));
                    play.click(play.getByTitle("Forward"));
                    play.findByText("Group 150");
                    play.waitFor(() -> play.expect(edit).toHaveAttribute("aria-disabled", "true"));
                    play.click(play.getByTitle("First"));
                    play.findByText("Group 001");
                    expectSelected(play, "Group 001", "false");
                    play.expect(edit).toHaveAttribute("aria-disabled", "true");
                })
                // Deleting the last group, on the last page: the pager shows a full last page, so the
                // page moves back a row and is fetched again, and the new last group is selected. The
                // fetch of the moved page was once dropped (the fetch the rows came in still counted
                // as running), leaving a placeholder row and the deleted group selected
                .story("DeleteOnTheLastPageSelectsPrevious", context -> render(context, pagedFixtures(150)))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Group 001");
                    play.click(play.getByTitle("Last"));
                    play.click(play.findByText("Group 150"));
                    expectSelected(play, "Group 150", "true");
                    play.click(play.getByRole("button", TextMatch.containing("Delete Selected Node Group")));
                    screen.findByText("Are you sure you want to delete the selected node group?");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.queryByText("Group 150")).toBeNull());
                    expectSelected(play, "Group 149", "true");
                    play.expect(play.querySelectorAll("." + StroomDom.SELECTED_ROW)).toHaveLength(1);
                    play.expect(play.getByText("Group 050")).toBeInTheDocument();
                })
                // Edit edits one group, so it is dead with two selected; Delete deletes both, then the
                // group in place of the first is selected, once both are answered (each delete once
                // fetched the list again, and edited the last selected of several)
                .story("MultiDeleteSelectsNext", context -> render(context, pagedFixtures(6)))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByText("Group 002"));
                    play.fireEvent().mouseDown(play.getByText("Group 004"), EventInit.create().ctrlKey());
                    expectSelected(play, "Group 004", "true");
                    expectSelected(play, "Group 002", "true");
                    final Query edit = play.getByRole("button", TextMatch.containing("Edit Selected Node Group"));
                    final Query delete = play.getByRole("button", TextMatch.containing("Delete Selected Node Group"));
                    play.waitFor(() -> play.expect(edit).toHaveAttribute("aria-disabled", "true"));
                    play.expect(delete).not().toHaveAttribute("aria-disabled");
                    play.click(delete);
                    screen.findByText("Are you sure you want to delete the selected node groups?");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.queryByText("Group 004")).toBeNull());
                    play.expect(play.queryByText("Group 002")).toBeNull();
                    // Group 003 is now where Group 002 was
                    expectSelected(play, "Group 003", "true");
                    play.expect(edit).not().toHaveAttribute("aria-disabled");
                })
                .story("CrudFlow", NodeGroupsScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("All Nodes");
                    play.findByText("Processing");
                    // Toggle "Processing" enabled -> update. GWT's grid tick is a TickBoxCell div.
                    // The grid's rows are <tr> elements with no role attribute
                    play.click(play.within(play.getByText("Processing").closest(StroomDom.GRID_ROW))
                            .querySelector(".tick-box"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put("/node/nodeGroup/v2/2")
                                    .withJsonBodyContaining("{\"id\": 2, \"enabled\": true}")
                                    .toSpyMatcher()));
                    // New group via the create dialog.
                    // The 'New Node Group' button is an icon button titled 'New Node Group'
                    play.click(play.getByTitle("New Node Group"));
                    screen.findByText("New", StroomDom.DIALOG_TITLE);
                    // The name field is Stroom's shared NameEntry ('nameEntryName')
                    play.type(screen.querySelector("#nameEntryName"), "Reporting");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/node/nodeGroup/v2").withBodyContaining("Reporting")
                                    .toSpyMatcher()));
                    play.findByText("Reporting");
                    // GWT then opens the new group's members dialog
                    screen.findByText("Edit Node Group - Reporting");
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // Ticking a selected group's Enabled updates it and fetches the groups again, with
                // the group changed (enabled, a new version): its row stays selected. It once showed
                // unselected, as the fetched group wasn't equal to the one selected
                .story("EnabledKeepsSelection", NodeGroupsScreenStories::render)
                .withPlay(play -> {
                    play.findByText("All Nodes");
                    play.click(play.findByText("Processing"));
                    play.waitFor(() -> play.expect(processingRow(play)).toHaveAttribute("aria-selected", "true"));
                    play.click(play.within(processingRow(play)).querySelector(".tick-box"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put("/node/nodeGroup/v2/2")
                                    .withJsonBodyContaining("{\"id\": 2, \"enabled\": true}")
                                    .toSpyMatcher()));
                    // The groups are fetched after the update (GROUPS_ENABLED, the third reply). The
                    // rows look as they did (the tick box showed the tick when clicked), so the play
                    // gives the reply time to arrive and be drawn
                    final Spy requests = play.spy(ScreenHarness.REQUEST_SPY);
                    play.waitFor(() -> play.expect("find requests", () -> countRequests(requests, "POST " + FIND_PATH))
                            .toBe(3));
                    play.sleep(500);
                    play.expect(play.within(processingRow(play)).getByRole("checkbox"))
                            .toHaveAttribute("aria-checked", "true");
                    play.expect(processingRow(play)).toHaveAttribute("aria-selected", "true");
                    play.expect(play.querySelectorAll("." + StroomDom.SELECTED_ROW)).toHaveLength(1);
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // Cancel creates nothing
                .story("NewGroupCancel", NodeGroupsScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("All Nodes");
                    play.click(play.getByTitle("New Node Group"));
                    screen.findByText("New", StroomDom.DIALOG_TITLE);
                    play.type(screen.querySelector("#nameEntryName"), "Reporting");
                    play.click(screen.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("New", StroomDom.DIALOG_TITLE)).toBeNull());
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post("/node/nodeGroup/v2").toSpyMatcher());
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // A name with a '/' is refused before anything is sent: such a name is looked up in
                // the URL's path (fetchByName/{name}), where the server refuses it (gwt-bugs #42)
                .story("NameWithSlash", NodeGroupsScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("All Nodes");
                    play.click(play.getByTitle("New Node Group"));
                    screen.findByText("New", StroomDom.DIALOG_TITLE);
                    play.type(screen.querySelector("#nameEntryName"), "A/B nodes");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith(ValueMatcher.stringContaining("contain")));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.get("/node/nodeGroup/v2/fetchByName/*").toSpyMatcher());
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post("/node/nodeGroup/v2").toSpyMatcher());
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                });
    }

    // The 'Processing' group's row
    private static Query processingRow(final Play play) {
        return play.getByText("Processing").closest(StroomDom.GRID_ROW);
    }

    // The number of requests the spy recorded with the method and path given
    private static int countRequests(final Spy requests, final String methodAndPath) {
        int count = 0;
        for (final List<Object> call : requests.getCalls()) {
            if (String.valueOf(call.get(0)).startsWith(methodAndPath)) {
                count++;
            }
        }
        return count;
    }

    // A group as the server sends it
    private static String group(final int id, final String name) {
        return "{\"id\": " + id + ", \"version\": 1, \"name\": \"" + name
               + "\", \"enabled\": true, \"invertSelection\": false}";
    }

    // Groups named "Group 001" up to the count, a page of 100 at a time, and a server that deletes
    // groups 2, 4 and 150
    private static RestFixtures pagedFixtures(final int count) {
        final FakeRows groups = new FakeRows();
        for (int i = 1; i <= count; i++) {
            final String name = "Group " + (i < 10 ? "00" : i < 100 ? "0" : "") + i;
            groups.put(String.valueOf(i), name, group(i, name));
        }
        return RestFixtures.builder()
                .post(FIND_PATH, groups::page)
                .delete("/node/nodeGroup/v2/2", groups.removing("2", RestReply.json("true")))
                .delete("/node/nodeGroup/v2/4", groups.removing("4", RestReply.json("true")))
                .delete("/node/nodeGroup/v2/150", groups.removing("150", RestReply.json("true")))
                .build()
                .followedBy(FIXTURES);
    }

    private static void expectSelected(final Play play, final String name, final String selected) {
        play.waitFor(() -> play.expect(play.getByText(name).closest(StroomDom.GRID_ROW))
                .toHaveAttribute("aria-selected", selected));
    }

    private static Widget render(final StoryContext context) {
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .build();
        harness.afterStartUp(() -> harness.show(injector.getNodeGroupsController()));
        return harness.asWidget();
    }

    // With other fixtures, and the confirmations shown, for a play to answer
    private static Widget render(final StoryContext context, final RestFixtures fixtures) {
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .realAlerts()
                .build();
        harness.afterStartUp(() -> harness.show(injector.getNodeGroupsController()));
        return harness.asWidget();
    }
}
