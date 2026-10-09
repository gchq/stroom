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

package stroom.gwt.workbench.client.widgets.editorsandviewers;

import stroom.data.client.presenter.DataPresenter;
import stroom.data.client.presenter.ItemNavigatorPresenter;
import stroom.data.client.presenter.ItemSelectionPresenter;
import stroom.data.client.presenter.MarkerListPresenter;
import stroom.data.client.view.ClassificationLabel;
import stroom.data.client.view.ClassificationWrapperViewImpl;
import stroom.data.client.view.DataViewImpl;
import stroom.data.client.view.ItemNavigatorViewImpl;
import stroom.data.client.view.ItemSelectionViewImpl;
import stroom.data.grid.client.WrapperViewImpl;
import stroom.editor.client.presenter.HtmlPresenter;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestHandler;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.pipeline.shared.SourceLocation;
import stroom.util.shared.DataRange;
import stroom.util.shared.DefaultLocation;
import stroom.widget.progress.client.presenter.ProgressPresenter;
import stroom.widget.progress.client.view.ProgressViewImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/// Stories for Stroom's [DataPresenter] (the data preview of a stream: its Info, Error, Data
/// Preview, Meta and Context tabs, the item navigator and the marker list).
///
/// Fixtures answer the requests [DataPresenter] makes:
/// the stream's meta (`GET /meta/v1/{id}`), its child stream types
/// (`GET /data/v1/{id}/parts/{part}/child-types`) and the data (`POST /data/v1/fetch`).
///
/// The data preview pages by part or record (the 'View Source' link opens the
/// source view, see `SourcePresenterStories`), and shows markers in its Error tab, for streams of
/// the `Error` type.
public final class SourceViewerStories {

    private static final String RAW_EVENTS = "Raw Events";
    private static final String EVENTS = "Events";
    private static final String ERROR = "Error";

    // A non-segmented stream of one part, as the server reports it
    private static final String ONE_PART = "\"totalItemCount\": {\"count\": 1, \"exact\": true}, "
                                           + "\"itemRange\": {\"offset\": 0, \"length\": 1}";

    // A tab's label (a link tab also holds a hidden copy of its label, for its size)
    private static final String TAB_LABEL = ".linkTab-label";

    // The Ace editor's lines
    private static final String ACE_LINES = ".ace_line";

    // Three whole lines beginning at line 5; the middle line's columns 11-16 spell "TARGET"
    private static final String HL_CONTENT = "line five aaaa\nline six  TARGET word\nline seven cccc";

    // The markers as the server returns them: a summary row per severity, followed by its errors
    private static final List<String> SAMPLE_MARKERS = List.of(
            summary("ERROR", true),
            storedError("ERROR", "{\"id\": \"SplitFilter\", \"name\": \"Split records\"}", 4, 12,
                    "Unexpected end of element > caused by a truncated record"),
            summary("WARNING", true),
            storedError("WARNING", "{\"id\": \"XSLTFilter\"}", 9, 3, "Deprecated template match"),
            summary("INFO", true),
            storedError("INFO", "{\"id\": \"Parser\"}", 1, 1, "Stream parsed successfully"));

    private static final RestFixtures LOADED_SOURCE_FIXTURES = streamFixtures(1001, RAW_EVENTS, "[]",
            request -> RestReply.json(window(request.getBody(), buildSampleXml(6), 100_000)).delayed(120));

    private static final RestFixtures MARKERS_FIXTURES = streamFixtures(1001, ERROR, "[]",
            request -> RestReply.json(markers(1001, SAMPLE_MARKERS)));

    private static final RestFixtures MARKER_EXPAND_FIXTURES = streamFixtures(1001, ERROR, "[]",
            request -> RestReply.json(markers(1001, expandingMarkers(request.getBody()))));

    private static final RestFixtures MULTI_PAGE_FIXTURES = streamFixtures(2002, RAW_EVENTS, "[]",
            request -> RestReply.json(window(request.getBody(), buildSampleXml(200), 600)).delayed(120));

    private static final RestFixtures STREAM_NAVIGATION_FIXTURES = streamFixtures(3003, EVENTS,
            "[\"Meta Data\", \"Context\"]",
            request -> RestReply.json(segmented(request.getBody())).delayed(60));

    private static final RestFixtures HIGHLIGHT_FIXTURES = streamFixtures(4004, RAW_EVENTS, "[]",
            request -> RestReply.json(highlightSource()).delayed(60));

    private SourceViewerStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Editors & Viewers/SourceViewer", SourceViewerStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // A fully loaded data preview; the whole stream fits in one part
                .story("LoadedSource", context -> render(context, LOADED_SOURCE_FIXTURES, location(1001)))
                // The marker list (MarkerListPresenter) of an Error stream
                .story("Markers", context -> render(context, MARKERS_FIXTURES, location(1001)))
                .withPlay(play -> {
                    // An Error stream's markers are shown in its Error tab
                    play.click(play.findByText(ERROR, TAB_LABEL));
                    for (final String heading : List.of("Element", "Stream", "Line", "Col", "Message")) {
                        play.expect(play.findByRole("columnheader", heading)).toBeInTheDocument();
                    }
                    // The severity's summary value (plural), then the counts
                    play.expect(play.getByText("Errors (1 item)")).toBeInTheDocument();
                    play.expect(play.getByText("Warnings (1 item)")).toBeInTheDocument();
                    // Line and Col are separate columns
                    play.expect(play.getByText("12")).toBeInTheDocument();
                    // A message's causes are split on the delimiter
                    play.expect(play.getByText(TextMatch.containing("caused by a truncated record")))
                            .toBeInTheDocument();
                    // ElementId.toString(): `name {id}`
                    play.expect(play.getByText("Split records {SplitFilter}")).toBeInTheDocument();
                    // Summary rows span Element+Stream+Line+Col+Message.
                    // The summary cell has a colspan
                    final Query summaryRow = play.getByText("Errors (1 item)").closest("tr");
                    play.expect(play.within(summaryRow).querySelector("td[colspan=\"5\"]")).not().toBeNull();
                    // The expander column is ExpanderCell.getColumnWidth(1) = 45px
                    final Value<Double> expanderWidth = play.getAllByRole("columnheader").nth(0).width();
                    play.expect("the expander column's width", expanderWidth::get).toBeGreaterThan(40);
                })
                // Expanding a marker group fetches again with the expanded severities
                .story("MarkerExpandRefetches", context -> render(context, MARKER_EXPAND_FIXTURES, location(1001)))
                .withPlay(play -> {
                    final Supplier<Object> lastFetch = lastFetch(play);
                    play.click(play.findByText(ERROR, TAB_LABEL));
                    play.waitFor(() -> play.expect(play.getByText("Errors (1 item)")).toBeInTheDocument());
                    // MarkerListPresenter starts with every severity expanded
                    // (resetExpandedSeverities), so the error shows to begin with
                    // and the first click collapses the group
                    play.expect(play.getByText("Split records {SplitFilter}")).toBeInTheDocument();
                    play.click(play.querySelector(".expanderCell .expanderIcon"));
                    play.waitFor(() -> play.expect(play.queryByText("Split records {SplitFilter}")).toBeNull());
                    // Clicking the expander again fetches with ERROR expanded, and the server returns
                    // the group's rows (they aren't filtered by the client)
                    play.click(play.querySelector(".expanderCell .expanderIcon"));
                    play.waitFor(() -> play.expect(play.getByText("Split records {SplitFilter}"))
                            .toBeInTheDocument());
                    play.expect("the last fetch", lastFetch)
                            .toMatch(TextMatch.regex("\"expandedSeverities\":\\[[^\\]]*\"ERROR\"", ""));
                })
                // A large stream; GWT previews the start of it (a window of 600 characters here)
                .story("MultiPageWithRefresh", context -> render(context, MULTI_PAGE_FIXTURES, location(2002)))
                // A segmented stream: the record pager, the child stream tabs and View as Hex
                .story("StreamNavigation", context -> render(context, STREAM_NAVIGATION_FIXTURES, location(3003)))
                .withPlay(play -> {
                    final Supplier<Object> lastFetch = lastFetch(play);
                    waitForEditorText(play, "<event>record 0</event>");

                    // The item pager: the next record fetches with recordIndex 1.
                    play.click(play.getByTitle("Next record"));
                    play.waitFor(() -> play.expect("the last fetch", lastFetch).toMatch("\"recordIndex\":1"));
                    waitForEditorText(play, "<event>record 1</event>");

                    // The child streams are tabs (Data Preview, Meta, Context)
                    play.click(play.getByText("Context", TAB_LABEL));
                    play.waitFor(() -> play.expect("the last fetch", lastFetch).toMatch("\"childType\":\"Context\""));
                    waitForEditorText(play, "<context>ctx</context>");

                    // View as Hex is an option of the editor's context menu
                    play.rightClick(play.querySelector(".ace_content"));
                    play.click(play.screen().findByText(TextMatch.containing("View as Hex")));
                    play.waitFor(() -> play.expect("the last fetch", lastFetch).toMatch("\"displayMode\":\"HEX\""));
                    waitForEditorText(play, "|<event>|");
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                })
                // A range of the source with a highlight, as the dashboard's text pane asks for
                .story("HighlightedRange", context -> render(context, HIGHLIGHT_FIXTURES, highlightLocation()))
                .withPlay(play -> {
                    waitForEditorText(play, "line six  TARGET word");
                    // The range is highlighted (an Ace marker). It once never was for a data stream:
                    // DataPresenter.refreshHighlights compared the stream's type with the location's
                    // child type
                    play.waitFor(() -> play.expect(play.querySelectorAll(".ace_marker-layer .hl").count())
                            .toBeGreaterThan(0));
                    // The data preview hides the line numbers (it formats the
                    // data), so there is no gutter numbered from the returned first line
                    play.expect(play.querySelector(".ace_gutter")).not().toBeVisible();
                    // A range has nothing to navigate, so there is no item navigator (it was once
                    // shown again when the data arrived)
                    play.expect(play.querySelector(".itemNavigator")).toBeNull();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // No location: nothing is fetched
                .story("NoLocation", context -> render(context, RestFixtures.builder().build(), null));
    }

    private static Widget render(final StoryContext context,
                                 final RestFixtures fixtures,
                                 final SourceLocation sourceLocation) {
        // Stroom's Menu (for the data's context menus) is the harness's
        final ScreenHarness harness = ScreenHarness.create(context, fixtures);
        final DataPresenter presenter = dataPresenter(harness);
        // As GWTP does when the presenter is revealed
        presenter.bind();
        harness.unbindOnCleanUp(presenter);
        if (sourceLocation != null) {
            presenter.fetchData(sourceLocation);
        }

        // A 460px high frame
        harness.add(EditorWidgets.frame(presenter.getWidget(), "auto", "460px"));
        return harness.asWidget();
    }

    /// Creates the presenter and its views, as GIN would.
    private static DataPresenter dataPresenter(final ScreenHarness harness) {
        final EventBus eventBus = harness.getEventBus();
        final ItemNavigatorPresenter itemNavigatorPresenter = new ItemNavigatorPresenter(
                eventBus,
                new ItemNavigatorViewImpl(eventBus, GWT.create(ItemNavigatorViewImpl.Binder.class)),
                () -> new ItemSelectionPresenter(
                        eventBus,
                        new ItemSelectionViewImpl(eventBus, GWT.create(ItemSelectionViewImpl.Binder.class))));
        return new DataPresenter(
                eventBus,
                new HtmlPresenter(eventBus),
                itemNavigatorPresenter,
                new DataViewImpl(GWT.create(DataViewImpl.Binder.class)),
                new ClassificationWrapperViewImpl(
                        GWT.create(ClassificationWrapperViewImpl.Binder.class),
                        new ClassificationLabel(harness.getUiConfigCache())),
                EditorWidgets.textPresenter(eventBus),
                new ProgressPresenter(eventBus, new ProgressViewImpl(GWT.create(ProgressViewImpl.Binder.class))),
                new MarkerListPresenter(eventBus, new WrapperViewImpl()),
                // Only used to open the source in a new tab, which the workbench doesn't have
                null,
                harness.getUiConfigCache(),
                harness.getInjector().getClientSecurityContext(),
                harness.getRestFactory());
    }

    private static SourceLocation location(final long metaId) {
        return SourceLocation.builder(metaId).build();
    }

    /// Both the range to fetch and the highlight: columns 11-16 of line 6.
    private static SourceLocation highlightLocation() {
        final DataRange range = DataRange.between(DefaultLocation.of(6, 11), DefaultLocation.of(6, 16));
        return SourceLocation.builder(4004)
                .withDataRange(range)
                .withHighlight(range)
                .build();
    }

    /// Waits until the editor's lines contain the text. Ace splits a line into tokens, so the
    /// lines are read whole.
    private static void waitForEditorText(final Play play, final String text) {
        final Value<List<String>> lines = play.querySelectorAll(ACE_LINES).textContents();
        play.waitFor(() -> play.expect("the editor's text", () -> String.join("\n", lines.get()))
                .toMatch(text));
    }

    /// The last data fetch (`POST /data/v1/fetch` and its body), as recorded by the harness's
    /// request spy.
    private static Supplier<Object> lastFetch(final Play play) {
        final Spy requests = play.spy(ScreenHarness.REQUEST_SPY);
        return () -> {
            String last = "";
            for (final List<Object> call : requests.getCalls()) {
                final String request = String.valueOf(call.get(0));
                if (request.startsWith("POST " + DataFixtures.FETCH_PATH)) {
                    last = request;
                }
            }
            return last;
        };
    }

    // ---------------------------------------------------------------------------------------
    // Fixtures

    private static RestFixtures streamFixtures(final long metaId,
                                               final String typeName,
                                               final String childTypesJson,
                                               final RestHandler fetch) {
        return RestFixtures.builder()
                .get("/meta/v1/" + metaId, RestReply.json("{\"id\": " + metaId
                                                          + ", \"feedName\": \"TEST_FEED\", \"typeName\": "
                                                          + DataFixtures.quote(typeName) + "}"))
                .get("/data/v1/" + metaId + "/parts/0/child-types", RestReply.json(childTypesJson))
                .post(DataFixtures.FETCH_PATH, fetch)
                .build();
    }

    /// Sample XML with the given number of records.
    private static String buildSampleXml(final int records) {
        final StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Events>\n");
        for (int i = 1; i <= records; i++) {
            final int minute = i % 60;
            sb.append("  <Event>\n");
            sb.append("    <Id>").append(i).append("</Id>\n");
            sb.append("    <Time>2026-07-08T09:").append(minute < 10
                    ? "0"
                    : "").append(minute).append(":00.000Z</Time>\n");
            sb.append("    <Message>Sample event number ").append(i).append(" for the source viewer.</Message>\n");
            sb.append("  </Event>\n");
        }
        return sb.append("</Events>").toString();
    }

    /// A window of the text from the requested
    /// character, with the true total.
    private static String window(final String requestBody, final String text, final int windowSize) {
        final int maxIndex = Math.max(text.length() - 1, 0);
        final long requestedFrom = DataFixtures.getLong(requestBody, 0,
                "sourceLocation", "dataRange", "charOffsetFrom");
        final int from = (int) Math.max(0, Math.min(requestedFrom, maxIndex));
        final int to = Math.min(from + windowSize - 1, maxIndex);
        return dataResult(DataFixtures.getLong(requestBody, 0, "sourceLocation", "metaId"),
                RAW_EVENTS,
                text.substring(from, to + 1),
                "NON_SEGMENTED",
                "TEXT",
                "\"recordIndex\": 0, \"dataRange\": {\"charOffsetFrom\": " + from + ", \"charOffsetTo\": " + to + "}",
                "\"totalCharacterCount\": {\"count\": " + text.length() + ", \"exact\": true}, "
                + "\"totalBytes\": " + text.length() + ", " + ONE_PART);
    }

    /// A segmented stream of 3 records with child streams and hex.
    private static String segmented(final String requestBody) {
        final Object childType = DataFixtures.get(requestBody, "sourceLocation", "childType");
        final long record = DataFixtures.getLong(requestBody, 0, "sourceLocation", "recordIndex");
        final boolean hex = "HEX".equals(DataFixtures.get(requestBody, "displayMode"));
        final String data;
        if (hex) {
            data = "00000000  3c 65 76 65 6e 74 3e                              |<event>|";
        } else if ("Context".equals(childType)) {
            data = "<context>ctx</context>";
        } else if (childType != null) {
            data = "<meta>record " + record + "</meta>";
        } else {
            data = "<event>record " + record + "</event>";
        }
        final String childTypeJson = childType != null
                ? ", \"childType\": " + DataFixtures.quote(String.valueOf(childType))
                : "";
        return dataResult(3003, EVENTS, data, "SEGMENTED", hex
                        ? "HEX"
                        : "TEXT",
                "\"recordIndex\": " + record + childTypeJson
                + ", \"dataRange\": {\"charOffsetFrom\": 0, \"charOffsetTo\": " + (data.length() - 1) + "}",
                "\"totalCharacterCount\": {\"count\": " + data.length() + ", \"exact\": true}, "
                + "\"totalBytes\": " + data.length() + ", "
                + "\"availableChildStreamTypes\": [\"Meta Data\", \"Context\"], "
                + "\"totalItemCount\": {\"count\": 3, \"exact\": true}, "
                + "\"itemRange\": {\"offset\": " + record + ", \"length\": 1}");
    }

    /// Whole lines from line 5, reported as the range's first
    /// line.
    private static String highlightSource() {
        return dataResult(4004, RAW_EVENTS, HL_CONTENT, "NON_SEGMENTED", "TEXT",
                "\"recordIndex\": 0, \"dataRange\": {\"locationFrom\": " + DataFixtures.location(5, 1)
                + ", \"charOffsetFrom\": 0, \"charOffsetTo\": " + (HL_CONTENT.length() - 1) + "}",
                "\"totalCharacterCount\": {\"count\": " + HL_CONTENT.length() + ", \"exact\": true}, "
                + "\"totalBytes\": " + HL_CONTENT.length() + ", " + ONE_PART);
    }

    private static String dataResult(final long metaId,
                                     final String streamTypeName,
                                     final String data,
                                     final String dataType,
                                     final String displayMode,
                                     final String sourceLocationMembers,
                                     final String otherMembers) {
        return "{\"type\": \"data\", "
               + "\"feedName\": \"TEST_FEED\", "
               + "\"streamTypeName\": " + DataFixtures.quote(streamTypeName) + ", "
               + "\"data\": " + DataFixtures.quote(data) + ", "
               + "\"dataType\": \"" + dataType + "\", "
               + "\"displayMode\": \"" + displayMode + "\", "
               + "\"sourceLocation\": {\"metaId\": " + metaId + ", \"partIndex\": 0, " + sourceLocationMembers + "}, "
               + otherMembers
               + "}";
    }

    /// The reply for an Error stream's markers.
    private static String markers(final long metaId, final List<String> markers) {
        return "{\"type\": \"marker\", "
               + "\"feedName\": \"TEST_FEED\", "
               + "\"streamTypeName\": \"Error\", "
               + "\"displayMode\": \"MARKER\", "
               + "\"sourceLocation\": {\"metaId\": " + metaId + ", \"partIndex\": 0, \"recordIndex\": 0}, "
               + "\"itemRange\": {\"offset\": 0, \"length\": " + markers.size() + "}, "
               + "\"totalItemCount\": {\"count\": " + markers.size() + ", \"exact\": true}, "
               + "\"availableChildStreamTypes\": [], "
               + "\"markers\": [" + String.join(", ", markers) + "]"
               + "}";
    }

    /// The ERROR group's errors only when ERROR is expanded.
    private static List<String> expandingMarkers(final String requestBody) {
        final Object expanded = DataFixtures.get(requestBody, "expandedSeverities");
        final boolean open = expanded instanceof List && ((List<?>) expanded).contains("ERROR");
        final List<String> markers = new ArrayList<>();
        markers.add(summary("ERROR", open));
        if (open) {
            markers.add(storedError("ERROR", "{\"id\": \"SplitFilter\", \"name\": \"Split records\"}", 4, 12,
                    "Unexpected end of element"));
        }
        return markers;
    }

    private static String summary(final String severity, final boolean expanded) {
        return "{\"type\": \"summary\", \"severity\": \"" + severity + "\", \"count\": 1, \"total\": 1, "
               + "\"expander\": {\"depth\": 0, \"expanded\": " + expanded + ", \"leaf\": false}}";
    }

    private static String storedError(final String severity,
                                      final String elementIdJson,
                                      final int lineNo,
                                      final int colNo,
                                      final String message) {
        return "{\"type\": \"storedError\", \"severity\": \"" + severity + "\", "
               + "\"elementId\": " + elementIdJson + ", "
               + "\"location\": " + DataFixtures.streamLocation(0, lineNo, colNo) + ", "
               + "\"message\": " + DataFixtures.quote(message) + "}";
    }
}
