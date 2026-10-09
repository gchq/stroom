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

import stroom.data.store.impl.fs.client.presenter.FsVolumeGroupPresenter;
import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/DataVolumesScreen`, showing Stroom's real [FsVolumeGroupPresenter] (the
/// 'Data Volumes' tab, its 'Edit Volume Group' dialog and 'Add Volume' dialog) with fake REST
/// replies.
///
/// The stories answer Stroom's `FsVolumeGroupResource` (`/fsVolume/volumeGroup/v2`: `findExtended`,
/// `fetch`) and `FsVolumeResource` (`/fsVolume/v1`: `find`, `validate`, `create`), and the requests
/// made are checked on the request spy.
public final class DataVolumesScreenStories {

    private static final String GROUP = "{\"id\": 1, \"name\": \"Default\"}";

    private static final String VOLUMES = """
            {
              "values": [
                {"id": 10, "volumeGroup": {"id": 1, "name": "Default"}, "path": "/data1", "volumeType": "STANDARD",
                  "status": "ACTIVE", "volumeState": {"bytesUsed": 500, "bytesFree": 500, "bytesTotal": 1000,
                  "updateTimeMs": 1700000000000}}
              ],
              "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}
            }""";

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

    private DataVolumesScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/DataVolumesScreen", DataVolumesScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Group list → group dialog (Type/Status volume grid) → Add Volume creates a volume
                .story("Volumes", DataVolumesScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.dblClick(play.findByText("Default"));
                    final Play groupDialog = screen.within(screen.findByText("Edit Volume Group - Default")
                            .closest(StroomDom.DIALOG));
                    groupDialog.findByText("/data1");
                    groupDialog.findByText("Standard");
                    groupDialog.findByText("Active");
                    // New volume → "Add Volume" → set the path → OK (validated, then created)
                    play.click(groupDialog.getByTitle("New"));
                    final Play volumeDialog = screen.within(screen.findByText("Add Volume").closest(StroomDom.DIALOG));
                    play.type(volumeDialog.getByLabelText("Path"), "/data2");
                    play.click(volumeDialog.getByRole("button", StroomDom.button("OK")));
                    // GWT's volume holds its group, not a group id
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/fsVolume/v1")
                                    .withJsonBodyContaining("{\"path\": \"/data2\", \"volumeGroup\": {\"id\": 1}}")
                                    .toSpyMatcher()));
                    ContentStorySupport.expectNoProblems(play);
                });
    }

    private static Widget render(final StoryContext context) {
        final ContentScreenGinjector injector = GWT.create(ContentScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .build();
        harness.afterStartUp(() -> harness.addContent(injector.getFsVolumeGroupPresenter()));
        return harness.asWidget();
    }
}
