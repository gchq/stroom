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

import stroom.data.client.presenter.MetaPresenter;
import stroom.gwt.workbench.client.app.gin.processing.ProcessingScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// The `App/Data/MetaBrowser` stories, showing Stroom's real [MetaPresenter] (the stream browser of
/// a 'Data' tab: the stream list, its relations and the data preview) with fake REST replies, read
/// with no document (all unlocked streams), as `MetaPresenter.read` does for the data browser.
///
/// | Stroom REST endpoint | Used for |
/// |---|---|
/// | `POST /meta/v1/find` | the streams (the relations when the criteria's `fetchRelationships` is set) |
/// | `POST /meta/v1/getSelectionSummary` | the selection summary |
/// | `POST /meta/v1/getReprocessSelectionSummary` | the reprocess summary |
/// | `POST /data/v1/fetch` | the data preview |
/// | `POST /processorFilter/v1/reprocess` | reprocessing |
/// | `POST /expression/v1/validate` | the selection's validation |
public final class MetaBrowserStories {

    private static final String FIND_PATH = "/meta/v1/find";
    // An ISO instant with millis and an offset (the default date time pattern)
    private static final String ISO_DATE =
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}(?:Z|[+-]\\d{2}:?\\d{2})$";
    private static final String REPROCESS_PATH = "/processorFilter/v1/reprocess";

    private static final String ATTRIBUTES = """
            {"Raw Size": "2048", "File Size": "1536", "Read Count": "1200", "Write Count": "1180",
              "Error Count": "3", "Warning Count": "12"}""";

    private static final String STREAMS = """
            {
              "values": [
                {"meta": {"id": 101, "feedName": "EVENTS", "typeName": "Raw Events", "status": "UNLOCKED",
                  "createMs": 1700000000000, "processorUuid": "proc-1"},
                  "pipeline": {"type": "Pipeline", "uuid": "p1", "name": "Events Pipeline"},
                  "attributes": ATTRIBUTES},
                {"meta": {"id": 102, "feedName": "REFERENCE", "typeName": "Reference", "status": "UNLOCKED",
                  "createMs": 1700000600000}, "attributes": ATTRIBUTES},
                {"meta": {"id": 103, "feedName": "EVENTS", "typeName": "Events", "status": "LOCKED",
                  "createMs": 1700000900000}, "attributes": ATTRIBUTES}
              ],
              "pageResponse": {"offset": 0, "length": 3, "total": 3, "exact": true}
            }""".replace("ATTRIBUTES", ATTRIBUTES);

    // The selected stream's parent/child chain. The child has no attributes (the meta list once
    // read them without a null check)
    private static final String RELATIONS = """
            {
              "values": [
                {"meta": {"id": 101, "feedName": "EVENTS", "typeName": "Raw Events", "status": "UNLOCKED",
                  "createMs": 1700000000000}, "attributes": ATTRIBUTES},
                {"meta": {"id": 201, "feedName": "EVENTS", "typeName": "Events", "status": "UNLOCKED",
                  "createMs": 1700000010000, "parentMetaId": 101}}
              ],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}
            }""".replace("ATTRIBUTES", ATTRIBUTES);

    // The selection summary, with no age range (Stroom's summary dialog once read it without a null
    // check)
    private static final String SUMMARY = """
            {"itemCount": 3, "feedCount": 2, "typeCount": 3, "processorCount": 0, "pipelineCount": 1,
              "statusCount": 2, "distinctFeeds": ["EVENTS", "REFERENCE"],
              "distinctTypes": ["Raw Events", "Reference", "Events"], "distinctStatuses": ["Unlocked", "Locked"]}""";

    // DataResource.fetch(), shaped as the gwt-suite corpus records it
    private static final String DATA = """
            {"type": "data", "feedName": "EVENTS", "streamTypeName": "Raw Events",
              "classification": "UNKNOWN CLASSIFICATION",
              "sourceLocation": {"metaId": 101, "partIndex": 0, "recordIndex": 0,
                "dataRange": {"locationFrom": {"type": "default", "lineNo": 1, "colNo": 1}, "charOffsetFrom": 0,
                  "byteOffsetFrom": 0, "locationTo": {"type": "default", "lineNo": 3, "colNo": 9},
                  "charOffsetTo": 68, "byteOffsetTo": 68, "length": 69},
                "highlights": []},
              "itemRange": {"offset": 0, "length": 1}, "totalItemCount": {"count": 1, "exact": true},
              "totalCharacterCount": {"count": 69, "exact": true}, "totalBytes": 69,
              "availableChildStreamTypes": [null],
              "data": "<Events xmlns=\\"event-logging:3\\">\\n  <Event><Id>1</Id></Event>\\n</Events>",
              "html": false, "dataType": "NON_SEGMENTED", "displayMode": "TEXT"}""";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .route(RequestMatcher.post(FIND_PATH).withJsonBodyContaining("{\"fetchRelationships\": true}"),
                    RestReply.json(RELATIONS))
            .post(FIND_PATH, RestReply.json(STREAMS))
            .post("/meta/v1/getSelectionSummary", RestReply.json(SUMMARY))
            .post("/meta/v1/getReprocessSelectionSummary", RestReply.json(SUMMARY))
            .post("/data/v1/fetch", RestReply.json(DATA))
            .post(REPROCESS_PATH, RestReply.json("[]"))
            .post("/expression/v1/validate", RestReply.json("{\"ok\": true}"))
            // The preview's child stream types (none)
            .get("/data/v1/101/parts/0/child-types", RestReply.json("[null]"))
            .build();

    private MetaBrowserStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Data/MetaBrowser", MetaBrowserStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The stream browser: the meta grid on top, the data preview below
                .story("Default", MetaBrowserStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    // The meta grid loads the streams, with size (IEC) and count columns
                    play.waitFor(() -> play.expect(play.getByText("Events Pipeline")).toBeInTheDocument());
                    play.expect(play.getByText("REFERENCE")).toBeInTheDocument();
                    play.expect(play.getAllByText("2.0K").count()).toBeGreaterThan(0);
                    // Rich cells: Pipeline and Feed are document links, Created and Type copy text.
                    // The feed is a document link (titled 'Open Feed ... in new tab') and the copy
                    // text cells have a 'Copy value' icon
                    play.expect(play.getByText("Events Pipeline").closest(".docRefLinkContainer")).not().toBeNull();
                    play.expect(play.getAllByTitle("Open Feed EVENTS in new tab").count()).toBeGreaterThan(0);
                    play.expect(play.getAllByTitle("Copy value 'Raw Events' to clipboard").count())
                            .toBeGreaterThan(0);
                    // Only the stream produced by a processor names a pipeline
                    play.expect(play.getAllByText("Events Pipeline").count()).toBe(1);
                    // Created is formatted with the user's date time pattern
                    play.expect(play.getAllByText(TextMatch.regex(ISO_DATE, "")).count()).toBeGreaterThan(0);
                    // The preview is empty before a stream is selected, so no data is fetched
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post("/data/v1/fetch").toSpyMatcher());
                    // Before selection the parent stream appears once, in the top grid
                    play.expect(play.getAllByText("Raw Events").count()).toBe(1);
                    // Clicking a row highlights it (its relations and data) but doesn't tick it
                    play.click(play.getByText("Events Pipeline"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/data/v1/fetch").toSpyMatcher()));
                    play.waitFor(() -> play.expect(play.getAllByText("Raw Events").count()).toBeGreaterThan(1));
                    // Actions work on the ticked selection: tick the top list's select-all header
                    play.click(play.getAllByTitle("Not Ticked").nth(0));
                    play.waitFor(() -> play.expect(play.getAllByTitle("Ticked").count()).toBeGreaterThan(0));
                    // Info summarises the ticked selection.
                    // Stroom's icon buttons are found by their title
                    play.click(play.getByTitle("Selection summary"));
                    final Play summary = dialog(screen, "Selection Summary");
                    play.expect(summary.getByText(TextMatch.containing("That are associated with:")))
                            .toBeInTheDocument();
                    play.click(summary.getByRole("button", StroomDom.button("Close")));
                    play.waitFor(() -> play.expect(screen.queryByText("Selection Summary", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    // Delete shows a selection summary that doubles as the confirmation
                    play.click(play.getAllByTitle("Delete").nth(0));
                    confirmSelectAll(play, screen, "delete");
                    final Play confirm = dialog(screen, "Confirm Delete");
                    play.expect(confirm.getByText(TextMatch.containingIgnoreCase("are you sure you want to delete")))
                            .toBeInTheDocument();
                    play.click(confirm.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("Confirm Delete", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    // Filter: the button titled 'Filter' opens 'Filter Streams'
                    play.click(play.getByTitle("Filter"));
                    final Play filter = dialog(screen, "Filter Streams");
                    play.click(filter.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("Filter Streams", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    expectNoProblems(play);
                })
                // Reprocessing a match-all selection strips the filter's Status = Unlocked term
                .story("ReprocessStripsStatusTerms", MetaBrowserStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(play.getByText("Events Pipeline")).toBeInTheDocument());
                    // Select all with the header tick box (match all, so the filter is sent)
                    play.click(play.getAllByTitle("Not Ticked").nth(0));
                    play.waitFor(() -> play.expect(play.getAllByTitle("Ticked").count()).toBeGreaterThan(0));
                    play.click(play.getAllByTitle("Process").nth(0));
                    final Play choice = dialog(screen, "Create Processors");
                    // Tick "Reprocess data" [1] so no pipeline is needed, then OK
                    play.click(choice.getAllByRole("checkbox").nth(1));
                    play.click(choice.getByRole("button", StroomDom.button("OK")));
                    confirmSelectAll(play, screen, "reprocess");
                    // Then the reprocess selection summary confirmation; nothing is sent yet
                    final Play confirm = dialog(screen, "Confirm Reprocess");
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post(REPROCESS_PATH).toSpyMatcher());
                    play.click(confirm.getByRole("button", StroomDom.button("OK")));
                    // The reprocess request has no Status term
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(REPROCESS_PATH)
                                    .withBody("no Status term", body -> body != null && !body.contains("Status"))
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                });
    }

    // Before its selection summary, Stroom's AbstractMetaListPresenter.confirmSelection asks 'Are you
    // sure you want to <action> the selected items?' and, for a select all, warns that every stream
    // matching the filter is included; both are answered OK
    private static void confirmSelectAll(final Play play, final Play screen, final String action) {
        final Play sure = screen.within(screen.findByText(
                "Are you sure you want to " + action + " the selected items?").closest(StroomDom.DIALOG));
        play.click(sure.getByRole("button", StroomDom.button("OK")));
        final Play all = screen.within(screen.findByText(TextMatch.containing(
                "If you continue Stroom will " + action + " all items that match the filter"))
                .closest(StroomDom.DIALOG));
        play.click(all.getByRole("button", StroomDom.button("OK")));
    }

    private static Play dialog(final Play screen, final String caption) {
        return screen.within(screen.findByText(caption, StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    private static Widget render(final StoryContext context) {
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                // The confirmations are Stroom's real dialogs
                .realAlerts()
                .build();
        harness.afterStartUp(() -> {
            final MetaPresenter presenter = injector.getMetaPresenter();
            harness.addContent(presenter);
            // As the data browser reads it, with no document: all unlocked streams
            presenter.read(null, null, false);
        });
        return harness.asWidget();
    }
}
