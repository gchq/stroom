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

import stroom.data.client.presenter.DataUploadPresenter;
import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.gwt.workbench.client.app.gin.processing.ProcessingScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Data/DataUploadDialog` in the React Storybook, showing Stroom's real
/// [DataUploadPresenter] (a feed's 'Upload' dialog, as the feed's data browser shows it) with fake
/// REST replies.
///
/// | React | Stroom |
/// |---|---|
/// | `feedName` | `GET /feed/v1/{uuid}` (the feed, loaded before the dialog shows) |
/// | `streamTypes` | `GET /meta/v1/getTypes` |
///
/// `Upload` (choosing and posting a file) is blocked: Stroom's `FileUploadSubmitter` posts the
/// file with its own `XMLHttpRequest` (see `react-story-status.json`).
public final class DataUploadDialogStories {

    private static final String FEED_UUID = "feed-1";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .get("/feed/v1/" + FEED_UUID, RestReply.json(
                    "{\"type\": \"Feed\", \"uuid\": \"" + FEED_UUID + "\", \"name\": \"TEST_FEED\"}"))
            .get("/meta/v1/getTypes", RestReply.json("[\"Raw Events\", \"Raw Reference\", \"Events\"]"))
            .build();

    private DataUploadDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Data/DataUploadDialog", DataUploadDialogStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // GWT doesn't disable OK: it validates on the click and warns
                .story("OkValidatesOnClick", DataUploadDialogStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Upload", StroomDom.DIALOG_TITLE);
                    // The stream type defaults to Raw Events (the feed has none of its own)
                    play.waitFor(() -> play.expect(screen.getByDisplayValue("Raw Events")).toBeInTheDocument());
                    final Query ok = screen.getByRole("button", StroomDom.button("OK"));
                    play.expect(ok).toBeEnabled();
                    play.click(ok);
                    play.waitFor(() -> play.expect(screen.getByText("File not set!")).toBeInTheDocument());
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).toHaveBeenCalledWith("WARN: File not set!");
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                });
    }

    private static Widget render(final StoryContext context) {
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                // The warning is Stroom's real alert dialog
                .realAlerts()
                .build();
        final DocRef feedRef = DocRef.builder().type(FeedDoc.TYPE).uuid(FEED_UUID).name("TEST_FEED").build();
        final DataUploadPresenter presenter = injector.getDataUploadPresenter();
        // As MetaPresenter's 'Upload' button shows it (the meta presenter is only used after an upload)
        harness.afterStartUp(() -> presenter.show(null, feedRef));
        return harness.asWidget();
    }
}
