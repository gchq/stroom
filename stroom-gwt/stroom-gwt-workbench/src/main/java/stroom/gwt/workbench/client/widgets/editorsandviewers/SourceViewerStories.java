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

import stroom.data.client.component.ClassificationLabelController;
import stroom.data.client.component.DataViewer;
import stroom.data.client.component.DataViewerController;
import stroom.data.client.component.ItemNavigatorController;
import stroom.data.client.component.ItemSelection;
import stroom.data.client.component.ItemSelectionController;
import stroom.data.client.component.MarkerList;
import stroom.data.client.component.MarkerListController;
import stroom.gwt.workbench.client.a11y.ButtonRuns;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestHandler;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.SourceLocation;
import stroom.util.shared.DataRange;
import stroom.util.shared.DefaultLocation;
import stroom.widget.button.client.Button;
import stroom.widget.text.client.HtmlPane;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// Stories for Stroom's [DataViewerController] (the data preview of a stream: its Info, Error, Data
/// Preview, Meta and Context tabs, the item navigator and the marker list).
///
/// Fixtures answer the requests [DataViewerController] makes:
/// the stream's meta (`GET /meta/v1/{id}`), its child stream types
/// (`GET /data/v1/{id}/parts/{part}/child-types`), its Info (`GET /data/v1/{id}/info`) and the data
/// (`POST /data/v1/fetch`).
///
/// The data preview pages by part or record (the 'View Source' link opens the
/// source view, see `SourceStories`), and shows markers in its Error tab, for streams of
/// the `Error` type.
public final class SourceViewerStories {

    private static final String RAW_EVENTS = "Raw Events";
    private static final String EVENTS = "Events";
    private static final String ERROR = "Error";

    // A non-segmented stream of one part, as the server reports it
    private static final String ONE_PART = "\"totalItemCount\": {\"count\": 1, \"exact\": true}, "
                                           + "\"itemRange\": {\"offset\": 0, \"length\": 1}";

    // A tab's label (a link tab also holds a hidden copy of its label, for its size)
    private static final String TAB_LABEL = ".tab-bar--link .tab-bar__label";

    // The classification banner's text
    private static final String BANNER = ".classification-label";

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

    // The data is slow, so that its reply comes after that of a tab chosen since
    private static final int SLOW_DATA_MILLIS = 400;
    private static final long SECOND_STREAM_ID = 3004;
    private static final RestFixtures TAB_FIXTURES = streamFixtures(3003, EVENTS,
            "[\"Meta Data\", \"Context\"]",
            request -> RestReply.json(segmented(request.getBody())).delayed(
                    DataFixtures.get(request.getBody(), "sourceLocation", "childType") == null
                            ? SLOW_DATA_MILLIS
                            : 30));
    private static final RestFixtures STREAMS_FIXTURES = RestFixtures.builder()
            .addAll(streamFixtures(3003, EVENTS, "[\"Meta Data\", \"Context\"]",
                    request -> RestReply.json(segmented(request.getBody())).delayed(60)))
            .get("/meta/v1/" + SECOND_STREAM_ID, RestReply.json("{\"id\": " + SECOND_STREAM_ID
                                                               + ", \"feedName\": \"TEST_FEED\", "
                                                               + "\"typeName\": \"Events\"}"))
            .get(childTypesPath(SECOND_STREAM_ID), RestReply.json("[]").delayed(30))
            .get("/data/v1/" + SECOND_STREAM_ID + "/info", RestReply.json(info(SECOND_STREAM_ID)))
            .build();

    // The first stream's details are slow, and the second's data; there is no third
    private static final long MISSING_STREAM_ID = 3005;
    private static final RestFixtures LOCATIONS_FIXTURES = RestFixtures.builder()
            .get("/meta/v1/3003", RestReply.json("{\"id\": 3003, \"feedName\": \"TEST_FEED\", "
                                                + "\"typeName\": \"Events\"}").delayed(SLOW_DATA_MILLIS))
            .get(childTypesPath(3003), RestReply.json("[\"Meta Data\", \"Context\"]"))
            .get("/meta/v1/" + MISSING_STREAM_ID, RestReply.json("null"))
            .addAll(streamFixtures(SECOND_STREAM_ID, EVENTS, "[]",
                    request -> RestReply.json(segmented(request.getBody())).delayed(
                            DataFixtures.getLong(request.getBody(), 0, "sourceLocation", "metaId")
                            == SECOND_STREAM_ID
                                    ? SLOW_DATA_MILLIS
                                    : 30)))
            .build();
    // The child types can't be had, but the data's reply has them
    private static final RestFixtures NO_CHILD_TYPES_FIXTURES = RestFixtures.builder()
            .get("/meta/v1/3003", RestReply.json("{\"id\": 3003, \"feedName\": \"TEST_FEED\", "
                                                + "\"typeName\": \"Events\"}"))
            .get(childTypesPath(3003), RestReply.error(500, "No child types"))
            .get("/data/v1/3003/info", RestReply.json(info(3003)))
            .post(DataFixtures.FETCH_PATH, request -> RestReply.json(segmented(request.getBody())))
            .build();

    // The first fetch, the first of another record and the first of another tab fail
    private static final RestFixtures FAILING_FIXTURES = RestFixtures.builder()
            .route(RequestMatcher.post(DataFixtures.FETCH_PATH), RestReply.error(500, "First failure"),
                    RestReply.json(segmented("{\"sourceLocation\": {\"metaId\": 3003, \"recordIndex\": 0}}")),
                    RestReply.error(500, "Second failure"),
                    RestReply.json(segmented("{\"sourceLocation\": {\"metaId\": 3003, \"recordIndex\": 1}}")),
                    RestReply.error(500, "Third failure"))
            // After the fetches, which theirs doesn't reply to
            .addAll(streamFixtures(3003, EVENTS, "[\"Meta Data\", \"Context\"]",
                    request -> RestReply.json(segmented(request.getBody()))))
            .build();

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
                // The navigation's icon buttons, and the tab stops they cost
                .withPlay(play -> play.waitFor(() -> play.expect(ButtonRuns.of(play.body())).toEqual(List.of(
                        "First part, Previous part, Next part, Last part, Refresh: 1 tab stop"))))
                // The marker list (MarkerListController) of an Error stream
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
                    // The summary cell spans five columns
                    final Query summaryRow = play.getByText("Errors (1 item)").closest(StroomDom.GRID_ROW);
                    play.expect(play.within(summaryRow).querySelector("[data-col][aria-colspan=\"5\"]").exists())
                            .toBe(true);
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
                    // MarkerListController starts with every severity expanded
                    // (resetExpandedSeverities), so the error shows to begin with
                    // and the first click collapses the group
                    play.expect(play.getByText("Split records {SplitFilter}")).toBeInTheDocument();
                    play.click(play.querySelector(".expander-cell .expander-cell__icon"));
                    play.waitFor(() -> play.expect(play.queryByText("Split records {SplitFilter}")).toBeNull());
                    // Clicking the expander again fetches with ERROR expanded, and the server returns
                    // the group's rows (they aren't filtered by the client)
                    play.click(play.querySelector(".expander-cell .expander-cell__icon"));
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

                    // View as Hex is an option of the editor's context menu, in its Editor Options
                    play.rightClick(play.querySelector(".ace_content"));
                    play.click(play.screen().findByText(TextMatch.containing("Editor Options")));
                    play.click(play.screen().findByText(TextMatch.containing("View as Hex")));
                    play.waitFor(() -> play.expect("the last fetch", lastFetch).toMatch("\"displayMode\":\"HEX\""));
                    waitForEditorText(play, "|<event>|");

                    // It stays chosen after visiting Info, where it is off (it was once turned off by it)
                    play.click(play.getByRole("tab", "Info"));
                    play.waitFor(() -> play.expect(play.getByText("Stream Id")).toBeVisible());
                    expectFetchAsPressed(play, play.getByRole("tab", "Data Preview"), "\"displayMode\":\"HEX\"");
                    waitForEditorText(play, "|<event>|");
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                })
                // Changing tab: the tab's data is asked for at once, shown once, and not replaced by a late
                // reply to the tab before
                .story("ChangeTab", context -> render(context, TAB_FIXTURES, location(3003)))
                .withPlay(play -> {
                    final Supplier<Object> lastFetch = lastFetch(play);
                    waitForEditorText(play, "<event>record 0</event>");
                    final Query editor = play.querySelector(".ace_editor");
                    final Query separator = play.querySelector(".tab-bar--link .tab-bar__separator");
                    play.run("count the texts shown", () -> countTexts(editor.element().get()));
                    final Value<Element> separatorBefore = play.capture("separatorBefore", separator.element());

                    // Asked for as the tab is pressed (it once waited, as paging does)
                    expectFetchAsPressed(play, play.getByRole("tab", "Context"), "\"childType\":\"Context\"");
                    waitForEditorText(play, "<context>ctx</context>");
                    // It was once shown in the last tab's mode, then again in its own
                    play.expect("the texts shown", () -> getTextCount(editor.element().get())).toBe(1);
                    // The tab bar isn't laid out again (which replaces its separators) when no tab is
                    // shown or hidden; it once was for each tab, which made the browser lay the page out
                    play.expect("the separator is the same", () ->
                            separator.element().get() == separatorBefore.get()).toBe(true);

                    // The data's reply comes after Meta's, and was once shown in its place
                    play.click(play.getByRole("tab", "Data Preview"));
                    play.waitFor(() -> play.expect("the last fetch", lastFetch)
                            .not().toMatch("\"childType\":\"Context\""));
                    play.click(play.getByRole("tab", "Meta"));
                    waitForEditorText(play, "<meta>record 0</meta>");
                    play.sleep(SLOW_DATA_MILLIS * 2);
                    waitForEditorText(play, "<meta>record 0</meta>");
                    play.expect(play.getByRole("tab", "Meta")).toHaveAttribute("aria-selected", "true");

                    // Nor is a late reply shown on Info, which is asked for once
                    play.click(play.getByRole("tab", "Data Preview"));
                    play.click(play.getByRole("tab", "Info"));
                    play.waitFor(() -> play.expect(play.getByText("Stream Id")).toBeVisible());
                    play.sleep(SLOW_DATA_MILLIS * 2);
                    play.expect(play.getByRole("tab", "Info")).toHaveAttribute("aria-selected", "true");
                    play.expect("the Info requests", requestCount(play, "GET /data/v1/3003/info")).toBe(1);

                    // Paging waits, so that pressing Next then Last asks for one record, not two
                    play.click(play.getByRole("tab", "Data Preview"));
                    waitForEditorText(play, "<event>record 0</event>");
                    final Supplier<Object> fetches = requestCount(play, "POST " + DataFixtures.FETCH_PATH);
                    final Value<Object> fetchesBefore = play.capture("fetchesBefore", fetches);
                    final Query next = play.getByTitle("Next record");
                    final Query last = play.getByTitle("Last record");
                    final Value<Integer> sentAsPressed = play.capture("sentAsPressed", () -> {
                        click(next.element().get());
                        click(last.element().get());
                        return (Integer) fetches.get() - (Integer) fetchesBefore.get();
                    });
                    play.expect("the fetches sent as they were pressed", sentAsPressed).toBe(0);
                    waitForEditorText(play, "<event>record 2</event>");
                    play.expect("the fetches", () ->
                            (Integer) fetches.get() - (Integer) fetchesBefore.get()).toBe(1);

                    // The stream's child types are asked for once
                    play.expect("the child types requests", requestCount(play, "GET " + childTypesPath(3003)))
                            .toBe(1);
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                })
                // Choosing a stream while on Info, as from the data browser's list
                .story("ChooseStreamOnInfo", context -> renderStreams(context, STREAMS_FIXTURES, true))
                .withPlay(play -> {
                    waitForEditorText(play, "<event>record 0</event>");
                    play.expect(play.getByText("CLASS 3003")).toBeVisible();
                    play.click(play.getByRole("tab", "Info"));
                    play.waitFor(() -> play.expect(play.getByText("3003")).toBeVisible());
                    play.click(play.getByRole("button", "Second stream"));
                    // The first stream's Info isn't shown for the second while it loads, nor its
                    // classification; the second's comes with its Info
                    play.expect(play.queryByText("3003")).toBeNull();
                    play.waitFor(() -> play.expect(play.getByText(String.valueOf(SECOND_STREAM_ID))).toBeVisible());
                    play.expect(play.queryByText("CLASS 3003")).toBeNull();
                    play.expect(play.getByText("CLASS " + SECOND_STREAM_ID, BANNER)).toBeVisible();
                    // The second stream has no Meta
                    play.waitFor(() -> play.expect(play.queryByRole("tab", "Meta")).toBeNull());

                    // Its child types are known, so its data is asked for at once (they were once asked for
                    // again first)
                    expectFetchAsPressed(play, play.getByRole("tab", "Data Preview"),
                            "\"metaId\":" + SECOND_STREAM_ID);
                    waitForEditorText(play, "<event>record 0</event>");
                    play.expect("the child types requests",
                            requestCount(play, "GET " + childTypesPath(SECOND_STREAM_ID))).toBe(1);

                    // Moving through the streams asks only for the one stopped at
                    final Query second = play.getByRole("button", "Second stream");
                    final Supplier<Object> firstTypes = requestCount(play, "GET " + childTypesPath(3003));
                    final Value<Object> firstTypesBefore = play.capture("firstTypesBefore", firstTypes);
                    final Query first = play.getByRole("button", "First stream");
                    play.run("move to the first stream and back", () -> {
                        click(first.element().get());
                        click(second.element().get());
                    });
                    play.sleep(400);
                    play.expect("the first stream's child types requests", () ->
                            (Integer) firstTypes.get() - (Integer) firstTypesBefore.get()).toBe(0);
                    waitForEditorText(play, "<event>record 0</event>");
                    play.expect(play.getByText("CLASS " + SECOND_STREAM_ID)).toBeVisible();

                    // Choosing a stream then Info, before the stream's replies, still shows its tabs
                    final Query info = play.getByRole("tab", "Info");
                    play.run("choose the first stream then Info", () -> {
                        click(first.element().get());
                        press(info.element().get());
                    });
                    play.waitFor(() -> play.expect(play.getByText("3003")).toBeVisible());
                    play.waitFor(() -> play.expect(play.getByRole("tab", "Meta")).toBeVisible());
                    play.expect(play.getByRole("tab", "Info")).toHaveAttribute("aria-selected", "true");
                    play.expect(play.queryByText("CLASS " + SECOND_STREAM_ID)).toBeNull();
                    play.expect(play.getByText("CLASS 3003", BANNER)).toBeVisible();
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                })
                // Choosing streams by location, as from an annotation's linked events: a late reply for one
                // chosen before isn't shown
                .story("ChooseLocation", context -> renderStreams(context, LOCATIONS_FIXTURES, false))
                .withPlay(play -> {
                    final Supplier<Object> lastFetch = lastFetch(play);
                    final Query first = play.getByRole("button", "First stream");
                    final Query second = play.getByRole("button", "Second stream");
                    final Query missing = play.getByRole("button", "Missing stream");
                    final TextMatch error = TextMatch.containing("Stream does not exist");

                    // The first stream's details come after the second's
                    play.run("choose the first stream then the second", () -> {
                        click(first.element().get());
                        click(second.element().get());
                    });
                    play.waitFor(() -> play.expect(play.getByText("CLASS " + SECOND_STREAM_ID)).toBeVisible());
                    play.sleep(SLOW_DATA_MILLIS * 2);
                    play.expect("the last fetch", lastFetch).toMatch("\"metaId\":" + SECOND_STREAM_ID);

                    // The second stream's data comes after the missing stream's error
                    play.click(first);
                    play.waitFor(() -> play.expect(play.getByText("CLASS 3003")).toBeVisible());
                    play.click(second);
                    play.waitFor(() -> play.expect("the last fetch", lastFetch)
                            .toMatch("\"metaId\":" + SECOND_STREAM_ID));
                    play.click(missing);
                    play.waitFor(() -> play.expect(play.getByText(error)).toBeVisible());
                    play.sleep(SLOW_DATA_MILLIS * 2);
                    play.expect(play.getByText(error)).toBeVisible();
                    play.expect(play.queryByText("CLASS 3003")).toBeNull();
                    play.expect(play.queryByText("CLASS " + SECOND_STREAM_ID)).toBeNull();

                    // With no stream, pressing a tab does nothing (it once failed)
                    play.click(play.getByRole("tab", "Info"));
                    play.click(play.getByRole("tab", "Data Preview"));
                    // and the next stream is shown
                    play.click(first);
                    waitForEditorText(play, "<event>record 0</event>");
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                })
                // A stream whose child types can't be had: they are asked for once, and the tabs the data's
                // reply says it has stay when Info is pressed
                .story("ChildTypesFail", context -> render(context, NO_CHILD_TYPES_FIXTURES, location(3003)))
                .withPlay(play -> {
                    waitForEditorText(play, "<event>record 0</event>");
                    play.expect(play.getByRole("tab", "Meta")).toBeVisible();
                    play.click(play.getByRole("tab", "Info"));
                    play.waitFor(() -> play.expect(play.getByText("Stream Id")).toBeVisible());
                    play.expect(play.getByRole("tab", "Meta")).toBeVisible();
                    play.click(play.getByRole("tab", "Meta"));
                    waitForEditorText(play, "<meta>record 0</meta>");
                    play.expect("the child types requests", requestCount(play, "GET " + childTypesPath(3003)))
                            .toBe(1);
                })
                // A fetch that fails: its error is shown in place of the data, and it can be tried again
                // (once, no error was shown and the button pressed stayed disabled)
                .story("FetchFails", context -> render(context, FAILING_FIXTURES, location(3003)))
                .withPlay(play -> {
                    final Query refresh = play.querySelector(".item-navigator .refresh-button");
                    final Query next = play.getByTitle("Next record");
                    // The stream's first: how many records there are isn't known
                    play.waitFor(() -> play.expect(play.getByText(TextMatch.containing("First failure")))
                            .toBeVisible());
                    play.expect(play.getByText("Record 1 of ?")).toBeVisible();
                    play.expect(next).toHaveAttribute("aria-disabled", "true");
                    play.expect(play.getByRole("tab", "Data Preview")).toHaveAttribute("aria-selected", "true");
                    play.expect(refresh).not().toHaveAttribute("aria-disabled");
                    play.click(refresh);
                    waitForEditorText(play, "<event>record 0</event>");
                    play.expect(play.queryByText(TextMatch.containing("First failure"))).not().toBeVisible();
                    play.expect(play.getByText("Record 1 of 3")).toBeVisible();

                    // Another record's
                    play.click(next);
                    play.waitFor(() -> play.expect(play.getByText(TextMatch.containing("Second failure")))
                            .toBeVisible());
                    play.expect(play.getByText("Record 2 of 3")).toBeVisible();
                    play.expect(next).not().toHaveAttribute("aria-disabled");
                    play.expect(play.getByText("CLASS 3003")).toBeVisible();
                    play.click(refresh);
                    waitForEditorText(play, "<event>record 1</event>");

                    // Another tab's, which is the one chosen
                    play.click(play.getByRole("tab", "Meta"));
                    play.waitFor(() -> play.expect(play.getByText(TextMatch.containing("Third failure")))
                            .toBeVisible());
                    play.expect(play.getByRole("tab", "Meta")).toHaveAttribute("aria-selected", "true");
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                })
                // A range of the source with a highlight, as the dashboard's text pane asks for
                .story("HighlightedRange", context -> render(context, HIGHLIGHT_FIXTURES, highlightLocation()))
                .withPlay(play -> {
                    waitForEditorText(play, "line six  TARGET word");
                    // The range is highlighted (an Ace marker). It once never was for a data stream:
                    // DataViewerController.refreshHighlights compared the stream's type with the location's
                    // child type
                    play.waitFor(() -> play.expect(
                                    play.querySelectorAll(".ace_marker-layer .code-editor__highlight").count())
                            .toBeGreaterThan(0));
                    // The data preview hides the line numbers (it formats the
                    // data), so there is no gutter numbered from the returned first line
                    play.expect(play.querySelector(".ace_gutter")).not().toBeVisible();
                    // A range has nothing to navigate, so the item navigator is hidden (it was once
                    // shown again when the data arrived)
                    play.expect(play.querySelector(".item-navigator")).not().toBeVisible();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // No location: nothing is fetched
                .story("NoLocation", context -> render(context, RestFixtures.builder().build(), null));
    }

    private static Widget render(final StoryContext context,
                                 final RestFixtures fixtures,
                                 final SourceLocation sourceLocation) {
        // Stroom's MenuManager (for the data's context menus) is the harness's
        final ScreenHarness harness = ScreenHarness.create(context, fixtures);
        final DataViewerController controller = dataController(harness);
        harness.own(controller);
        if (sourceLocation != null) {
            controller.fetchData(sourceLocation);
        }

        // A 460px high frame
        harness.add(EditorWidgets.frame(controller.asWidget(), "auto", "460px"));
        return harness.asWidget();
    }

    // The viewer, with buttons that choose a stream, as from a list of streams (the first chosen to
    // begin with) or by its location
    private static Widget renderStreams(final StoryContext context,
                                        final RestFixtures fixtures,
                                        final boolean byMeta) {
        final ScreenHarness harness = ScreenHarness.create(context, fixtures);
        final DataViewerController controller = dataController(harness);
        harness.own(controller);
        final Consumer<Long> choose = byMeta
                ? metaId -> controller.fetchData(meta(metaId))
                : metaId -> controller.fetchData(location(metaId));
        if (byMeta) {
            choose.accept(3003L);
        }

        harness.add(streamButton(harness, "First stream", () -> choose.accept(3003L)));
        harness.add(streamButton(harness, "Second stream", () -> choose.accept(SECOND_STREAM_ID)));
        if (!byMeta) {
            harness.add(streamButton(harness, "Missing stream", () -> choose.accept(MISSING_STREAM_ID)));
        }
        harness.add(EditorWidgets.frame(controller.asWidget(), "auto", "460px"));
        return harness.asWidget();
    }

    private static Widget streamButton(final ScreenHarness harness, final String text, final Runnable onClick) {
        final Button button = new Button();
        button.setText(text);
        harness.addRegistration(button.addClickHandler(event -> onClick.run()));
        return button;
    }

    private static Meta meta(final long metaId) {
        return Meta.builder()
                .id(metaId)
                .feedName("TEST_FEED")
                .typeName(EVENTS)
                .build();
    }

    /// Creates the controller and its components, as GIN would.
    private static DataViewerController dataController(final ScreenHarness harness) {
        final EventBus eventBus = harness.getEventBus();
        final ItemNavigatorController itemNavigator = new ItemNavigatorController(
                () -> new ItemSelectionController(eventBus, new ItemSelection()));
        return new DataViewerController(
                eventBus,
                new HtmlPane(),
                itemNavigator,
                new ClassificationLabelController(harness.getUiConfigCache()),
                EditorWidgets.dataTextPane(eventBus),
                new MarkerListController(eventBus, new MarkerList()),
                // Only used to open the source in a new tab, which the workbench doesn't have
                null,
                harness.getUiConfigCache(),
                harness.getInjector().getClientSecurityContext(),
                harness.getRestFactory())
                .attach(new DataViewer());
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

    /// How many requests have been made whose method and path are as given.
    private static Supplier<Object> requestCount(final Play play, final String methodAndPath) {
        final Spy requests = play.spy(ScreenHarness.REQUEST_SPY);
        return () -> {
            int count = 0;
            for (final List<Object> call : requests.getCalls()) {
                if (String.valueOf(call.get(0)).startsWith(methodAndPath)) {
                    count++;
                }
            }
            return count;
        };
    }

    // Expects a fetch to have been sent by the time a tab's press has been handled
    private static void expectFetchAsPressed(final Play play, final Query tab, final String fetch) {
        final Supplier<Object> lastFetch = lastFetch(play);
        final Value<Object> sent = play.capture("the last fetch as the tab is pressed", () -> {
            press(tab.element().get());
            return lastFetch.get();
        });
        play.expect("the last fetch as the tab was pressed", sent).toMatch(fetch);
    }

    private static native void press(Element element) /*-{
        ['mousedown', 'mouseup'].forEach(function (type) {
            element.dispatchEvent(new $wnd.MouseEvent(type, {bubbles: true, button: 0}));
        });
    }-*/;

    private static native void click(Element element) /*-{
        element.click();
    }-*/;

    // Counts the texts (that aren't empty) an Ace editor is given from now on
    private static native void countTexts(Element aceEditor) /*-{
        var session = aceEditor.env.editor.session;
        var setValue = session.setValue;
        aceEditor.textCount = 0;
        session.setValue = function (text) {
            if (text) {
                aceEditor.textCount++;
            }
            return setValue.apply(this, arguments);
        };
    }-*/;

    private static native int getTextCount(Element aceEditor) /*-{
        return aceEditor.textCount;
    }-*/;

    // ---------------------------------------------------------------------------------------
    // Fixtures

    private static String childTypesPath(final long metaId) {
        return "/data/v1/" + metaId + "/parts/0/child-types";
    }

    private static String info(final long metaId) {
        return "[{\"title\": \"Stream\", \"entries\": ["
               + "{\"key\": \"Stream Id\", \"value\": \"" + metaId + "\"}, "
               + "{\"key\": \"Classification\", \"value\": \"CLASS " + metaId + "\"}]}]";
    }

    private static RestFixtures streamFixtures(final long metaId,
                                               final String typeName,
                                               final String childTypesJson,
                                               final RestHandler fetch) {
        return RestFixtures.builder()
                .get("/meta/v1/" + metaId, RestReply.json("{\"id\": " + metaId
                                                          + ", \"feedName\": \"TEST_FEED\", \"typeName\": "
                                                          + DataFixtures.quote(typeName) + "}"))
                .get(childTypesPath(metaId), RestReply.json(childTypesJson))
                .get("/data/v1/" + metaId + "/info", RestReply.json(info(metaId)))
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
        final long metaId = DataFixtures.getLong(requestBody, 3003, "sourceLocation", "metaId");
        final String childTypes = metaId == SECOND_STREAM_ID
                ? ""
                : "\"Meta Data\", \"Context\"";
        return dataResult(metaId, EVENTS, data, "SEGMENTED", hex
                        ? "HEX"
                        : "TEXT",
                "\"recordIndex\": " + record + childTypeJson
                + ", \"dataRange\": {\"charOffsetFrom\": 0, \"charOffsetTo\": " + (data.length() - 1) + "}",
                "\"totalCharacterCount\": {\"count\": " + data.length() + ", \"exact\": true}, "
                + "\"totalBytes\": " + data.length() + ", "
                + "\"classification\": \"CLASS " + metaId + "\", "
                + "\"availableChildStreamTypes\": [" + childTypes + "], "
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
