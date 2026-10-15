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
import stroom.data.client.presenter.MetaPresenter;
import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.gwt.workbench.client.app.gin.processing.ProcessingScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.rest.UploadReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// The `App/Data/DataUploadDialog` stories, showing Stroom's real [DataUploadPresenter] (a feed's
/// 'Upload' dialog, as the feed's data browser shows it) with fake REST replies.
///
/// | Stroom | Used for |
/// |---|---|
/// | `GET /feed/v1/{uuid}` | the feed, loaded before the dialog shows |
/// | `GET /meta/v1/getTypes` | the stream types |
/// | the file's upload (`importfile.rpc`, checked with the upload spy) | uploading the file |
/// | `POST /data/v1/upload` (checked with the request spy) | adding the uploaded data to the feed |
/// | the 'Uploaded file' message | its Close refreshes the data and closes the dialog |
///
/// The dialog is shown as the feed's data browser (`MetaPresenter`) shows it, with that browser
/// (from GIN, not shown) as the presenter it refreshes after an upload.
public final class DataUploadDialogStories {

    private static final String FEED_UUID = "feed-1";
    private static final String UPLOAD_PATH = "/data/v1/upload";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .get("/feed/v1/" + FEED_UUID, RestReply.json(
                    "{\"type\": \"Feed\", \"uuid\": \"" + FEED_UUID + "\", \"name\": \"TEST_FEED\"}"))
            .get("/meta/v1/getTypes", RestReply.json("[\"Raw Events\", \"Raw Reference\", \"Events\"]"))
            // The file's upload: the resource key is named after the file
            .upload(UploadReply.success("rk-stream.txt", "stream.txt"))
            // DataResource.upload()
            .post(UPLOAD_PATH, RestReply.json("{\"key\": \"stored\", \"name\": \"stored\"}"))
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
                // Choose a file and a stream type; OK uploads the file, then posts an
                // UploadDataRequest (key, feed and type) for it
                .story("Upload", DataUploadDialogStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play dialog = screen.within(screen.findByText("Upload", StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    // No Feed field: the feed is the one whose data is being browsed
                    play.expect(screen.queryByText("TEST_FEED")).toBeNull();
                    // The stream type defaults to Raw Events; choose Events
                    play.waitFor(() -> play.expect(dialog.getByDisplayValue("Raw Events")).toBeInTheDocument());
                    // Stroom's SelectionBox opens when its text box is clicked
                    play.click(dialog.querySelector(StroomDom.SELECTION_BOX));
                    play.click(screen.findByText("Events"));
                    play.waitFor(() -> play.expect(dialog.getByDisplayValue("Events")).toBeInTheDocument());
                    play.upload(dialog.querySelector(StroomDom.FILE_INPUT), "stream.txt", "data", "text/plain");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    // The upload
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.UPLOAD_SPY))
                            .toHaveBeenCalledWith("importfile.rpc", "stream.txt", "data"));
                    // The file's name, not the file input's value, which browsers give as
                    // C:\fakepath\<name> (it was once sent as it is)
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(UPLOAD_PATH)
                                    .withJsonBodyContaining("""
                                            {"key": {"key": "rk-stream.txt"}, "feedName": "TEST_FEED",
                                             "streamTypeName": "Events", "fileName": "stream.txt"}""")
                                    .toSpyMatcher()));
                    // Stroom says the file was uploaded, and closing the message refreshes the
                    // feed's data browser and closes the dialog
                    play.click(screen.findByRole("button", StroomDom.button("Close")));
                    play.waitFor(() -> play.expect(screen.queryByText("Upload", StroomDom.DIALOG_TITLE)).toBeNull());
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).toHaveBeenCalledWith("INFO: Uploaded file");
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
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
        harness.afterStartUp(() -> {
            // The feed's data browser, which the dialog refreshes after an upload (its info
            // message is fired from it). Created after start-up, as it reads the UI config.
            final MetaPresenter metaPresenter = harness.unbindOnCleanUp(injector.getMetaPresenter());
            // As MetaPresenter's 'Upload' button shows it
            presenter.show(metaPresenter, feedRef);
        });
        return harness.asWidget();
    }
}
