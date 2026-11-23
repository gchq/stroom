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


package stroom.gwt.workbench.client.app.index;

import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ContentStorySupport;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.index.client.component.IndexVolumeGroupsController;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/IndexVolumesScreen`, showing Stroom's real [IndexVolumeGroupsController] (the
/// 'Index Volumes' tab, its 'Edit Index Volume Group' dialog and 'New Index Volume' dialog) with fake REST
/// replies.
///
/// The stories answer Stroom's `IndexVolumeGroupResource` (`/index/volumeGroup/v2`: `find`,
/// `fetch`, `update`) and `IndexVolumeResource` (`/index/volume/v2`: `find`, `validate`, `create`),
/// plus `NodeResource`'s `GET /node/v1/all` (the nodes), and the requests made are checked on the
/// request spy.
public final class IndexVolumesScreenStories {

    private static final String GROUP = "{\"id\": 1, \"name\": \"Default\"}";

    // 90% used trips the Use% bar's danger threshold; the second volume has a byte limit
    // (1.2345 GiB, formatted to 3 significant figures) and is INACTIVE, so its cells are disabled
    private static final String VOLUMES = """
            {
              "values": [
                {"id": 10, "indexVolumeGroupId": 1, "nodeName": "node1", "path": "/vol1", "state": "ACTIVE",
                  "bytesTotal": 1000000000, "bytesUsed": 900000000, "bytesFree": 100000000},
                {"id": 11, "indexVolumeGroupId": 1, "nodeName": "node2", "path": "/vol2", "state": "INACTIVE",
                  "bytesTotal": 4000000000, "bytesUsed": 1000000000, "bytesFree": 3000000000,
                  "bytesLimit": 1325534282}
              ],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}
            }""";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .post("/index/volumeGroup/v2/find", RestReply.json(
                    "{\"values\": [" + GROUP + "], \"pageResponse\": {\"offset\": 0, \"length\": 1, \"total\": 1, "
                            + "\"exact\": true}}"))
            .get("/index/volumeGroup/v2/1", RestReply.json(GROUP))
            .put("/index/volumeGroup/v2/1", RestReply.json(GROUP))
            .get("/index/volumeGroup/v2/fetchByName/*", RestReply.json(GROUP))
            .post("/index/volume/v2/find", RestReply.json(VOLUMES))
            .post("/index/volume/v2/validate", RestReply.json("{}"))
            .post("/index/volume/v2", request -> RestReply.json(request.getBody()))
            .get("/node/v1/all", RestReply.json("[\"node1\", \"node2\"]"))
            .build();

    // A shared volume (no node) beside a local one, so the group warns that it holds both
    private static final String MIXED_VOLUMES = """
            {
              "values": [
                {"id": 10, "indexVolumeGroupId": 1, "nodeName": "node1", "path": "/vol1", "state": "ACTIVE",
                  "bytesTotal": 1000000000, "bytesUsed": 100000000, "bytesFree": 900000000},
                {"id": 12, "indexVolumeGroupId": 1, "volumeType": "SHARED", "path": "/shared1", "state": "ACTIVE",
                  "bytesTotal": 1000000000, "bytesUsed": 100000000, "bytesFree": 900000000}
              ],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}
            }""";

    private static final RestFixtures MIXED_FIXTURES = RestFixtures.builder()
            .post("/index/volume/v2/find", RestReply.json(MIXED_VOLUMES))
            .addAll(FIXTURES)
            .build();

    private static final String MIXED_WARNING = "This group holds both shared and local volumes.";

    // The new volume validates with a warning, e.g. its path doesn't exist yet
    private static final RestFixtures WARNING_FIXTURES = RestFixtures.builder()
            .post("/index/volume/v2/validate", RestReply.json(
                    "{\"severity\": \"WARNING\", \"message\": \"The path doesn't exist\"}"))
            .addAll(FIXTURES)
            .build();

    // A new group, 'Archive', that no group has the name of yet
    private static final RestFixtures NEW_GROUP_FIXTURES = RestFixtures.builder()
            .get("/index/volumeGroup/v2/fetchByName/Archive", RestReply.noContent())
            .post("/index/volumeGroup/v2", RestReply.json("{\"id\": 2, \"name\": \"Archive\"}"))
            .addAll(FIXTURES)
            .build();

    private static final String NEW_GROUP = "New Index Volume Group";
    private static final String EDIT_GROUP = "Edit Index Volume Group - Default";
    private static final String NEW_VOLUME = "New Index Volume";

    private IndexVolumesScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/IndexVolumesScreen", IndexVolumesScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Group list → group dialog (volume list) → New Index Volume dialog creates a volume
                // New group: a name, then OK, creates it, and its dialog opens
                .story("NewGroup", context -> render(context, NEW_GROUP_FIXTURES, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Default");
                    play.click(play.getByTitle(NEW_GROUP));
                    screen.findByText(NEW_GROUP, StroomDom.DIALOG_TITLE);
                    play.type(screen.getByLabelText("Name"), "Archive");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/index/volumeGroup/v2").withBodyContaining("Archive")
                                    .toSpyMatcher()));
                    screen.findByText("Edit Index Volume Group - Archive");
                    play.expect(screen.queryByText(NEW_GROUP, StroomDom.DIALOG_TITLE)).toBeNull();
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // Cancel creates nothing
                .story("NewGroupCancel", context -> render(context, NEW_GROUP_FIXTURES, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Default");
                    play.click(play.getByTitle(NEW_GROUP));
                    play.type(screen.findByLabelText("Name"), "Archive");
                    play.click(screen.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText(NEW_GROUP, StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post("/index/volumeGroup/v2").toSpyMatcher());
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // New Index Volume Group left open (the screenshot and the dialog measurement show it)
                .story("NewIndexVolumeGroupOpen", context -> render(context, NEW_GROUP_FIXTURES, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Default");
                    play.click(play.getByTitle(NEW_GROUP));
                    final Play dialog = screen.within(screen.findByText(NEW_GROUP, StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    dialog.findByLabelText("Name");
                })
                .story("Volumes", IndexVolumesScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.dblClick(play.findByText("Default"));
                    final Play groupDialog = screen.within(screen.findByText(EDIT_GROUP).closest(StroomDom.DIALOG));
                    groupDialog.findByText("/vol1");
                    groupDialog.findByText("Active");
                    // Both volumes are local, so there is no warning about mixing them
                    play.expect(groupDialog.getByText(TextMatch.containing(MIXED_WARNING))).not().toBeVisible();
                    // New volume → "New Index Volume" → set the path → OK (validated, then created)
                    play.click(groupDialog.getByTitle(NEW_VOLUME));
                    final Play volumeDialog = screen.within(screen.findByText(NEW_VOLUME, StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    play.type(volumeDialog.getByLabelText("Path"), "/newvol");
                    play.click(volumeDialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/index/volume/v2")
                                    .withJsonBodyContaining("{\"path\": \"/newvol\", \"indexVolumeGroupId\": 1}")
                                    .toSpyMatcher()));
                    ContentStorySupport.expectNoProblems(play);
                })
                // A shared volume has no node: choosing Shared hides the node, and the volume is saved
                // without the node chosen before
                .story("SharedVolume", IndexVolumesScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.dblClick(play.findByText("Default"));
                    final Play groupDialog = screen.within(screen.findByText(EDIT_GROUP).closest(StroomDom.DIALOG));
                    groupDialog.findByText("/vol1");
                    play.click(groupDialog.getByTitle(NEW_VOLUME));
                    final Play volumeDialog = screen.within(screen.findByText(NEW_VOLUME, StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    play.click(volumeDialog.findByLabelText("Node"));
                    play.click(screen.findByRole("option", "node1"));
                    play.click(volumeDialog.getByLabelText("Type"));
                    play.click(screen.findByRole("option", "Shared"));
                    play.waitFor(() -> play.expect(volumeDialog.getByLabelText("Node")).not().toBeVisible());
                    play.type(volumeDialog.getByLabelText("Path"), "/shared");
                    play.click(volumeDialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/index/volume/v2")
                                    .withJsonBodyContaining("{\"volumeType\": \"SHARED\", \"path\": \"/shared\"}")
                                    .toSpyMatcher()));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post("/index/volume/v2").withBodyContaining("node1").toSpyMatcher());
                    ContentStorySupport.expectNoProblems(play);
                })
                // A group holding shared and local volumes warns that the local ones' shards are only
                // searched while their nodes are up
                .story("MixedVolumesWarning", context -> render(context, MIXED_FIXTURES, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.dblClick(play.findByText("Default"));
                    final Play groupDialog = screen.within(screen.findByText(EDIT_GROUP).closest(StroomDom.DIALOG));
                    groupDialog.findByText("/shared1");
                    play.expect(groupDialog.findByText(TextMatch.containing(MIXED_WARNING))).toBeVisible();
                    play.expect(groupDialog.getByText("Shared")).toBeInTheDocument();
                    ContentStorySupport.expectNoProblems(play);
                })
                // New Index Volume left open (the screenshot and the dialog measurement show it)
                .story("NewIndexVolumeOpen", IndexVolumesScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.dblClick(play.findByText("Default"));
                    final Play groupDialog = screen.within(screen.findByText(EDIT_GROUP).closest(StroomDom.DIALOG));
                    groupDialog.findByText("/vol1");
                    play.click(groupDialog.getByTitle(NEW_VOLUME));
                    final Play volumeDialog = screen.within(screen.findByText(NEW_VOLUME, StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    volumeDialog.findByLabelText("Path");
                })
                // Declining the warning about a new volume leaves the dialog usable: OK and Cancel once
                // stayed disabled, as only accepting the warning was handled
                .story("DeclinedWarningKeepsDialogUsable", context -> render(context, WARNING_FIXTURES, true))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.dblClick(play.findByText("Default"));
                    final Play groupDialog = screen.within(screen.findByText(EDIT_GROUP).closest(StroomDom.DIALOG));
                    groupDialog.findByText("/vol1");
                    play.click(groupDialog.getByTitle(NEW_VOLUME));
                    final Play volumeDialog = screen.within(screen.findByText(NEW_VOLUME, StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    play.type(volumeDialog.getByLabelText("Path"), "/newvol");
                    play.click(volumeDialog.getByRole("button", StroomDom.button("OK")));
                    play.click(screen.within(screen.findByText(TextMatch.containing("The path doesn't exist"))
                            .closest(StroomDom.DIALOG)).getByRole("button", StroomDom.button("Cancel")));
                    play.click(volumeDialog.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText(NEW_VOLUME, StroomDom.DIALOG_TITLE)).toBeNull());
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post("/index/volume/v2").toSpyMatcher());
                })
                // The status columns use GWT's PercentBarCell (Use%) and RedGreenTextCell (Full)
                .story("VolumeStatusCells", IndexVolumesScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.dblClick(play.findByText("Default"));
                    final Play dialog = screen.within(screen.findByText(EDIT_GROUP).closest(StroomDom.DIALOG));
                    dialog.findByText("/vol1");
                    // Use%: the percentage is the bar's title, with the danger class at 90%
                    final Query vol1Row = dialog.getByText("/vol1").closest(StroomDom.GRID_ROW);
                    final Play vol1 = dialog.within(vol1Row);
                    final Query bar = vol1.querySelector(".percent-bar-cell");
                    play.expect(bar).toBeInTheDocument();
                    play.expect(bar.attribute("title")).toBe("90%");
                    play.expect(dialog.within(bar).querySelector(".percent-bar-cell__bar--danger")).toBeInTheDocument();
                    play.expect(bar.textContent()).toBe("");
                    // Full: 90% used isn't full by GWT's rule (10 GiB / 1% headroom)
                    final Query full = vol1.getByText("No");
                    play.expect(full).toHaveClass("red-green-text-cell");
                    play.expect(full).toHaveClass("red-green-text-cell--green");
                    // Limit: 3 significant figures, trailing zeros stripped
                    play.expect(dialog.getByText("1.23G")).toBeInTheDocument();
                    play.expect(dialog.queryByText("1.2G")).toBeNull();
                    // The INACTIVE volume's cells have GWT's disabled class, the ACTIVE one's don't
                    final Play vol2 = dialog.within(dialog.getByText("/vol2").closest(StroomDom.GRID_ROW));
                    play.expect(vol2.querySelectorAll(".data-grid__cell--disabled").count()).toBeGreaterThan(0);
                    play.expect(vol1.querySelectorAll(".data-grid__cell--disabled").count()).toBe(0);
                    // ...and no cell dims itself with an inline opacity
                    play.expect(dialog.getByText("/vol2").closest(StroomDom.GRID_ROW).element())
                            .toSatisfy("has no inline opacity",
                                    row -> !((Element) row).getInnerHTML().contains("opacity: 0.5"));
                    ContentStorySupport.expectNoProblems(play);
                });
    }

    private static Widget render(final StoryContext context) {
        return render(context, FIXTURES, false);
    }

    // As above, with the given fixtures, and with Stroom's real alerts and confirmations if asked
    private static Widget render(final StoryContext context,
                                 final RestFixtures fixtures,
                                 final boolean realAlerts) {
        final ContentScreenGinjector injector = GWT.create(ContentScreenGinjector.class);
        final ScreenHarness.Builder builder = ScreenHarness.builder(context, fixtures)
                .injector(injector);
        if (realAlerts) {
            builder.realAlerts();
        }
        final ScreenHarness harness = builder.build();
        harness.afterStartUp(() -> harness.show(injector.getIndexVolumeGroupsController()));
        return harness.asWidget();
    }
}
