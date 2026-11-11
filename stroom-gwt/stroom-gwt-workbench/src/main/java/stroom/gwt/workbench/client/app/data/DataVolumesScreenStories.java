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


package stroom.gwt.workbench.client.app.data;

import stroom.data.store.impl.fs.client.component.FsVolumeGroupsController;
import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ContentStorySupport;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.ValueMatcher;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/DataVolumesScreen`, showing Stroom's real [FsVolumeGroupsController] (the
/// 'Data Volumes' tab, its 'Edit Data Volume Group' dialog and 'New Data Volume' dialog) with fake REST
/// replies.
///
/// The stories answer Stroom's `FsVolumeGroupResource` (`/fsVolume/volumeGroup/v2`: `findExtended`,
/// `fetch`) and `FsVolumeResource` (`/fsVolume/v1`: `find`, `validate`, `create`, and `fetch` and
/// `update` for an edit), and the requests made are checked on the request spy.
public final class DataVolumesScreenStories {

    private static final String GROUP = "{\"id\": 1, \"name\": \"Default\"}";

    private static final String VOLUME = """
            {"id": 10, "volumeGroup": {"id": 1, "name": "Default"}, "path": "/data1", "volumeType": "STANDARD",
              "status": "ACTIVE", "volumeState": {"bytesUsed": 500, "bytesFree": 500, "bytesTotal": 1000,
              "updateTimeMs": 1700000000000}}""";

    private static final String VOLUMES = "{\"values\": [" + VOLUME + "], "
                                          + "\"pageResponse\": {\"offset\": 0, \"length\": 1, \"total\": 1, "
                                          + "\"exact\": true}}";

    // The path the volume is given by an edit
    private static final String EDITED_PATH = "/archive1";

    private static final String NEW_VOLUME = "New Data Volume";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .post("/fsVolume/volumeGroup/v2/findExtended", RestReply.json(
                    "{\"values\": [{\"group\": " + GROUP + ", \"volumeCount\": 1, \"volumeTypes\": [\"STANDARD\"]}], "
                            + "\"pageResponse\": {\"offset\": 0, \"length\": 1, \"total\": 1, \"exact\": true}}"))
            .get("/fsVolume/volumeGroup/v2/1", RestReply.json(GROUP))
            .put("/fsVolume/volumeGroup/v2/1", RestReply.json(GROUP))
            .get("/fsVolume/volumeGroup/v2/fetchByName/*", RestReply.json(GROUP))
            .post("/fsVolume/v1/find", RestReply.json(VOLUMES))
            .post("/fsVolume/v1/validate", RestReply.json("{}"))
            .post("/fsVolume/v1", request -> RestReply.json(request.getBody()))
            .build();

    // A new group, 'Archive', that no group has the name of yet
    private static final RestFixtures NEW_GROUP_FIXTURES = RestFixtures.builder()
            .get("/fsVolume/volumeGroup/v2/fetchByName/Archive", RestReply.noContent())
            .post("/fsVolume/volumeGroup/v2", RestReply.json("{\"id\": 2, \"name\": \"Archive\"}"))
            .addAll(FIXTURES)
            .build();

    private static final String NEW_GROUP = "New Data Volume Group";

    private DataVolumesScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/DataVolumesScreen", DataVolumesScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Group list → group dialog (Type/Status volume grid) → New Data Volume dialog creates a volume
                .story("Volumes", DataVolumesScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.dblClick(play.findByText("Default"));
                    final Play groupDialog = screen.within(screen.findByText("Edit Data Volume Group - Default")
                            .closest(StroomDom.DIALOG));
                    groupDialog.findByText("/data1");
                    groupDialog.findByText("Standard");
                    groupDialog.findByText("Active");
                    // New volume → "New Data Volume" → set the path → OK (validated, then created)
                    play.click(groupDialog.getByTitle(NEW_VOLUME));
                    final Play volumeDialog = screen.within(screen.findByText(NEW_VOLUME, StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    play.type(volumeDialog.getByLabelText("Path"), "/data2");
                    play.click(volumeDialog.getByRole("button", StroomDom.button("OK")));
                    // GWT's volume holds its group, not a group id
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/fsVolume/v1")
                                    .withJsonBodyContaining("{\"path\": \"/data2\", \"volumeGroup\": {\"id\": 1}}")
                                    .toSpyMatcher()));
                    ContentStorySupport.expectNoProblems(play);
                })
                // Editing the selected volume saves it and fetches the volumes again, with its new
                // path: its row stays selected. It once showed unselected, as the fetched volume
                // wasn't equal to the one selected
                .story("EditKeepsSelection", context -> render(context, editingFixtures()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.dblClick(play.findByText("Default"));
                    final Play groupDialog = screen.within(screen.findByText("Edit Data Volume Group - Default")
                            .closest(StroomDom.DIALOG));
                    play.click(groupDialog.findByText("/data1"));
                    play.waitFor(() -> play.expect(groupDialog.getByText("/data1").closest(StroomDom.GRID_ROW))
                            .toHaveAttribute("aria-selected", "true"));
                    play.click(groupDialog.getByTitle("Edit Selected Data Volume"));
                    final Play volumeDialog = screen.within(
                            screen.findByText("Edit Data Volume", StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
                    final Query path = volumeDialog.getByLabelText("Path");
                    play.waitFor(() -> play.expect(path).toHaveValue("/data1"));
                    play.clear(path);
                    play.type(path, EDITED_PATH);
                    play.click(volumeDialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put("/fsVolume/v1/10")
                                    .withJsonBodyContaining("{\"id\": 10, \"path\": \"" + EDITED_PATH + "\"}")
                                    .toSpyMatcher()));
                    // The volumes as fetched again
                    final Query row = groupDialog.findByText(EDITED_PATH).closest(StroomDom.GRID_ROW);
                    play.expect(row).toHaveAttribute("aria-selected", "true");
                    play.expect(groupDialog.querySelectorAll("." + StroomDom.SELECTED_ROW)).toHaveLength(1);
                    play.expect(groupDialog.queryByText("/data1")).toBeNull();
                    ContentStorySupport.expectNoProblems(play);
                })
                // New group: a name, then Enter, creates it, and its dialog opens
                .story("NewGroup", context -> render(context, NEW_GROUP_FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Default");
                    play.click(play.getByTitle(NEW_GROUP));
                    screen.findByText(NEW_GROUP, StroomDom.DIALOG_TITLE);
                    final Query name = screen.getByLabelText("Name");
                    play.waitFor(() -> play.expect(name).toHaveFocus());
                    play.type(name, "Archive");
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/fsVolume/volumeGroup/v2").withBodyContaining("Archive")
                                    .toSpyMatcher()));
                    screen.findByText("Edit Data Volume Group - Archive");
                    play.expect(screen.queryByText(NEW_GROUP, StroomDom.DIALOG_TITLE)).toBeNull();
                    ContentStorySupport.expectNoProblems(play);
                })
                // Cancel creates nothing
                .story("NewGroupCancel", context -> render(context, NEW_GROUP_FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Default");
                    play.click(play.getByTitle(NEW_GROUP));
                    play.type(screen.findByLabelText("Name"), "Archive");
                    play.click(screen.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText(NEW_GROUP, StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post("/fsVolume/volumeGroup/v2").toSpyMatcher());
                    ContentStorySupport.expectNoProblems(play);
                })
                // A name another group has is refused, and the dialog stays open
                .story("NewGroupNameInUse", DataVolumesScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Default");
                    play.click(play.getByTitle(NEW_GROUP));
                    play.type(screen.findByLabelText("Name"), "Default");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith(ValueMatcher.stringContaining("already in use")));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post("/fsVolume/volumeGroup/v2").toSpyMatcher());
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                });
    }

    // Volumes as a server keeps them: once volume 10 is updated, it is found as updated. Made for
    // each rendering, so a rerun starts with the volume as it was
    private static RestFixtures editingFixtures() {
        final boolean[] saved = {false};
        return RestFixtures.builder()
                .get("/fsVolume/v1/10", RestReply.json(VOLUME))
                .put("/fsVolume/v1/10", request -> {
                    saved[0] = true;
                    return RestReply.json(request.getBody());
                })
                .post("/fsVolume/v1/find", request -> RestReply.json(saved[0]
                        ? VOLUMES.replace("\"/data1\"", "\"" + EDITED_PATH + "\"")
                        : VOLUMES))
                .addAll(FIXTURES)
                .build();
    }

    private static Widget render(final StoryContext context) {
        return render(context, FIXTURES);
    }

    private static Widget render(final StoryContext context, final RestFixtures fixtures) {
        final ContentScreenGinjector injector = GWT.create(ContentScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .build();
        harness.afterStartUp(() -> harness.show(injector.getFsVolumeGroupsController()));
        return harness.asWidget();
    }
}
