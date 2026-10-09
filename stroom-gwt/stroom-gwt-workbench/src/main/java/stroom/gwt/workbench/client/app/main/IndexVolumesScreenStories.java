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

import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
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
import stroom.index.client.presenter.IndexVolumeGroupPresenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/IndexVolumesScreen`, showing Stroom's real [IndexVolumeGroupPresenter] (the
/// 'Index Volumes' tab, its 'Edit Volume Group' dialog and 'Add Volume' dialog) with fake REST
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

    private static final String EDIT_GROUP = "Edit Volume Group - Default";

    private IndexVolumesScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/IndexVolumesScreen", IndexVolumesScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Group list → group dialog (volume list) → Add Volume dialog creates a volume
                .story("Volumes", IndexVolumesScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.dblClick(play.findByText("Default"));
                    final Play groupDialog = screen.within(screen.findByText(EDIT_GROUP).closest(StroomDom.DIALOG));
                    groupDialog.findByText("/vol1");
                    groupDialog.findByText("Active");
                    // New volume → "Add Volume" → set the path → OK (validated, then created)
                    play.click(groupDialog.getByTitle("New"));
                    final Play volumeDialog = screen.within(screen.findByText("Add Volume").closest(StroomDom.DIALOG));
                    play.type(volumeDialog.getByLabelText("Path"), "/newvol");
                    play.click(volumeDialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/index/volume/v2")
                                    .withJsonBodyContaining("{\"path\": \"/newvol\", \"indexVolumeGroupId\": 1}")
                                    .toSpyMatcher()));
                    ContentStorySupport.expectNoProblems(play);
                })
                // The status columns use GWT's PercentBarCell (Use%) and RedGreenTextCell (Full)
                .story("VolumeStatusCells", IndexVolumesScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.dblClick(play.findByText("Default"));
                    final Play dialog = screen.within(screen.findByText(EDIT_GROUP).closest(StroomDom.DIALOG));
                    dialog.findByText("/vol1");
                    // Use%: the percentage is the bar's title, with the danger class at 90%
                    final Query vol1Row = dialog.getByText("/vol1").closest("tr");
                    final Play vol1 = dialog.within(vol1Row);
                    final Query bar = vol1.querySelector(".percentBarCell-container");
                    play.expect(bar).toBeInTheDocument();
                    play.expect(bar.attribute("title")).toBe("90%");
                    play.expect(dialog.within(bar).querySelector(".percentBarCell-bar__danger")).toBeInTheDocument();
                    play.expect(bar.textContent()).toBe("");
                    // Full: 90% used isn't full by GWT's rule (10 GiB / 1% headroom)
                    final Query full = vol1.getByText("No");
                    play.expect(full).toHaveClass("redGreenCell");
                    play.expect(full).toHaveClass("redGreenCell__green");
                    // Limit: 3 significant figures, trailing zeros stripped
                    play.expect(dialog.getByText("1.23G")).toBeInTheDocument();
                    play.expect(dialog.queryByText("1.2G")).toBeNull();
                    // The INACTIVE volume's cells have GWT's disabled class, the ACTIVE one's don't
                    final Play vol2 = dialog.within(dialog.getByText("/vol2").closest("tr"));
                    play.expect(vol2.querySelectorAll(".dataGridDisabledCell").count()).toBeGreaterThan(0);
                    play.expect(vol1.querySelectorAll(".dataGridDisabledCell").count()).toBe(0);
                    // ...and no cell dims itself with an inline opacity
                    play.expect(dialog.getByText("/vol2").closest("tr").element()).toSatisfy("has no inline opacity",
                            row -> !((Element) row).getInnerHTML().contains("opacity: 0.5"));
                    ContentStorySupport.expectNoProblems(play);
                });
    }

    private static Widget render(final StoryContext context) {
        final ContentScreenGinjector injector = GWT.create(ContentScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .build();
        harness.afterStartUp(() -> harness.addContent(injector.getIndexVolumeGroupPresenter()));
        return harness.asWidget();
    }
}
