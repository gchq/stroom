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

package stroom.gwt.workbench.client.app.feed;

import stroom.docref.DocRef;
import stroom.feed.client.presenter.FeedPresenter;
import stroom.feed.shared.FeedDoc;
import stroom.feed.shared.FeedResource;
import stroom.gwt.workbench.client.app.editors.DocEditors;
import stroom.gwt.workbench.client.app.editors.DocEditors.DocResource;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// The `App/Feed/FeedEditor` stories, showing Stroom's real [FeedPresenter] (a Feed's tab: Data,
/// Active Tasks, Settings, Documentation and Permissions) with fake REST replies.
///
/// As `FeedPlugin` does, the story fetches the document (`GET /feed/v1/{uuid}`) and reads it into
/// the editor ([DocEditors#open]). The other routes:
///
/// | Stroom endpoint | Used for |
/// |---|---|
/// | `GET /feed/v1/fetchSupportedEncodings` | the encodings |
/// | `GET /meta/v1/getTypes` | the stream types |
/// | `POST /fsVolume/volumeGroup/v2/find` | the volume groups |
/// | `POST /meta/v1/find` | the feed's streams |
/// | `POST /data/v1/fetch` (and the stream's `info` and `child-types`) | the data preview |
/// | `POST /processorTask/v1/summary` | the active tasks' summary |
/// | `POST /processorTask/v1/find` | the active tasks |
/// | the Permissions tab's routes | the Permissions tab |
///
/// The UI config (`receiptCheckMode: FEED_STATUS`, so Feed Status is enabled) is the start-up
/// fixtures' default.
public final class FeedEditorStories {

    private static final DocRef DOC_REF = new DocRef(FeedDoc.TYPE, "feed-events", "EVENTS");

    // FeedResource.fetch(): the feed
    private static final String DOC = """
            {"type": "Feed", "uuid": "feed-events", "name": "EVENTS",
              "description": "# Events feed\\n\\nRaw event data.", "classification": "OFFICIAL",
              "reference": false, "status": "RECEIVE", "streamType": "Raw Events", "encoding": "UTF-8",
              "contextEncoding": "UTF-8", "dataFormat": "JSON", "volumeGroup": "Default"}""";

    // MetaResource.find(): the feed's streams
    private static final String STREAMS = """
            {"values": [
                {"meta": {"id": 101, "feedName": "EVENTS", "typeName": "Raw Events", "status": "UNLOCKED",
                  "createMs": 1700000000000, "processorUuid": "proc-1"},
                  "pipeline": {"type": "Pipeline", "uuid": "p1", "name": "Events Pipeline"}, "attributes": {}},
                {"meta": {"id": 102, "feedName": "EVENTS", "typeName": "Events", "status": "UNLOCKED",
                  "createMs": 1700000600000}, "attributes": {}}],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}}""";

    // DataResource.fetch(): a stream's data
    private static final String DATA = """
            {"type": "data", "feedName": "EVENTS", "streamTypeName": "Raw Events",
              "classification": "OFFICIAL",
              "sourceLocation": {"metaId": 101, "partIndex": 0, "recordIndex": 0},
              "itemRange": {"offset": 0, "length": 1}, "totalItemCount": {"count": 1, "exact": true},
              "totalCharacterCount": {"count": 48, "exact": true}, "totalBytes": 48,
              "availableChildStreamTypes": [null],
              "data": "<Events>\\n  <Event id=\\"1\\">login</Event>\\n</Events>",
              "html": false, "dataType": "NON_SEGMENTED", "displayMode": "TEXT"}""";

    // ProcessorTaskResource.summary() and find(): the active tasks
    private static final String TASK_PIPE =
            "{\"type\": \"Pipeline\", \"uuid\": \"p2\", \"name\": \"Events Task Pipeline\"}";
    private static final String TASK_SUMMARY = """
            {"values": [
                {"pipeline": PIPE, "feed": "EVENTS", "priority": 10, "status": "PROCESSING", "count": 3},
                {"pipeline": PIPE, "feed": "EVENTS", "priority": 10, "status": "COMPLETE", "count": 42}],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}}"""
            .replace("PIPE", TASK_PIPE);
    private static final String TASKS = """
            {"values": [{"id": 1001, "feedName": "EVENTS", "nodeName": "node1", "status": "PROCESSING",
                "createTimeMs": 1700000000000, "startTimeMs": 1700000005000,
                "processorFilter": {"id": 1, "priority": 10, "pipelineName": "Events Task Pipeline",
                  "pipelineUuid": "p2"}}],
              "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}}""";

    private static final String VOLUME_GROUPS = """
            {"values": [{"id": 1, "name": "Default"}, {"id": 2, "name": "Fast"}, {"id": 3, "name": "Archive"}],
              "pageResponse": {"offset": 0, "length": 3, "total": 3, "exact": true}}""";

    private static final RestFixtures FIXTURES = DocEditors.permissionRoutes(RestFixtures.builder())
            .get("/feed/v1/feed-events", RestReply.json(DOC))
            .put("/feed/v1/feed-events", request -> RestReply.json(request.getBody()))
            .get("/feed/v1/fetchSupportedEncodings",
                    RestReply.json("[\"UTF-8\", \"UTF-16\", \"ASCII\", \"ISO-8859-1\"]"))
            .get("/meta/v1/getTypes",
                    RestReply.json("[\"Raw Events\", \"Events\", \"Raw Reference\", \"Reference\"]"))
            .post("/fsVolume/volumeGroup/v2/find", RestReply.json(VOLUME_GROUPS))
            .post("/meta/v1/find", RestReply.json(STREAMS))
            .post("/data/v1/fetch", RestReply.json(DATA))
            .get("/data/v1/101/info", RestReply.json("[]"))
            .get("/data/v1/101/parts/0/child-types", RestReply.json("[null]"))
            .post("/processorTask/v1/summary", RestReply.json(TASK_SUMMARY))
            .post("/processorTask/v1/find", RestReply.json(TASKS))
            .build();

    // A FormGroup gives its control the group's identity as its id
    // (FeedSettingsViewImpl.ui.xml)
    private static final String CLASSIFICATION = "#feedSettingsClassification";

    private FeedEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Feed/FeedEditor", FeedEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The full editor: Data, Active Tasks, Settings, Documentation and Permissions
                .story("Default", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    for (final String label : new String[]{
                            "Data", "Active Tasks", "Settings", "Documentation", "Permissions"}) {
                        play.waitFor(() -> play.expect(DocEditors.tab(play, label)).toBeInTheDocument());
                    }
                    // Data is the default tab: the feed's streams, and selecting one previews it
                    play.waitFor(() -> play.expect(play.getByText("Events Pipeline")).toBeInTheDocument());
                    play.click(play.getByText("Events Pipeline"));
                    // The play checks that the selected stream's data is fetched
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/data/v1/fetch")
                                    .withJsonBodyContaining("{\"sourceLocation\": {\"metaId\": 101}}")
                                    .toSpyMatcher()));

                    // Settings: the form, and an edit enables Save
                    play.click(DocEditors.tab(play, "Settings"));
                    play.expect(play.findByText("Classification", "label")).toBeInTheDocument();
                    play.expect(play.getByText("Feed Status", "label")).toBeInTheDocument();
                    play.expect(play.getByText("Volume Group", "label")).toBeInTheDocument();
                    final Query save = play.getByRole("button", "Save");
                    play.expect(save).toHaveClass("disabled");
                    play.type(play.querySelector(CLASSIFICATION), "-X");
                    // The text box reports its change when it loses the focus
                    play.tab();
                    play.waitFor(() -> play.expect(save).not().toHaveClass("disabled"));

                    // Active Tasks: the task summary; its info icon shows the summary's key data;
                    // selecting a summary lists its tasks
                    play.click(DocEditors.tab(play, "Active Tasks"));
                    play.waitFor(() -> play.expect(play.getAllByText("Events Task Pipeline").count())
                            .toSatisfy("more than none", count -> ((Integer) count) > 0));
                    play.click(play.querySelector(".svgCell-icon"));
                    final Play info = play.within(screen.findByText("Key Data").closest("table"));
                    play.expect(info.getByText("Priority")).toBeInTheDocument();
                    play.expect(info.getByText("Feed")).toBeInTheDocument();
                    play.keyboard("{Escape}");
                    play.click(play.getAllByText("Events Task Pipeline").nth(0));
                    play.waitFor(() -> play.expect(play.getByText("node1")).toBeInTheDocument());
                    DocEditors.expectNoProblems(play);
                })
                // The Settings tab, to compare with its read only partner
                .story("SettingsEditable", context -> render(context, false))
                .withPlay(play -> {
                    openTab(play, "Settings");
                    play.expect(play.findByText("Classification", "label")).toBeInTheDocument();
                    play.waitFor(() -> play.expect(play.querySelector(CLASSIFICATION)).toHaveValue("OFFICIAL"));
                    DocEditors.expectNoProblems(play);
                })
                // Read only: the Settings tab, as 'SettingsEditable' shows it
                .story("SettingsReadOnly", context -> render(context, true))
                .withPlay(play -> {
                    openTab(play, "Settings");
                    play.expect(play.findByText("Classification", "label")).toBeInTheDocument();
                    play.waitFor(() -> play.expect(play.querySelector(CLASSIFICATION)).toHaveValue("OFFICIAL"));
                    play.expect(play.getByText("Read only", ".docTab-readOnlyNote")).toBeVisible();
                    DocEditors.expectNoProblems(play);
                });
    }

    // Opens one of the editor's sub-tabs
    private static void openTab(final Play play, final String label) {
        play.waitFor(() -> play.expect(DocEditors.tab(play, label)).toBeInTheDocument());
        play.click(DocEditors.tab(play, label));
    }

    private static Widget render(final StoryContext context, final boolean readOnly) {
        final FeedResource resource = GWT.create(FeedResource.class);
        return DocEditors.render(context, FIXTURES, readOnly,
                (harness, injector) -> DocEditors.open(harness,
                        DOC_REF,
                        injector.getFeedPresenter(),
                        DocResource.of(
                                restFactory -> restFactory.create(resource).method(res -> res.fetch(DOC_REF.getUuid())),
                                (restFactory, doc) -> restFactory.create(resource)
                                        .method(res -> res.update(doc.getUuid(), doc)))));
    }
}
