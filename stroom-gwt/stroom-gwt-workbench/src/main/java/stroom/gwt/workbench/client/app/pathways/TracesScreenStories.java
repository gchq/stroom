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


package stroom.gwt.workbench.client.app.pathways;

import stroom.docref.DocRef;
import stroom.document.client.editor.DocumentEditorHost;
import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ContentStorySupport;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.pathways.client.component.TracesEditor;
import stroom.pathways.shared.TracesDoc;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/TracesScreen`, showing Stroom's real [TracesEditor] (the 'Traces' tab:
/// the trace list and the selected trace's span waterfall) with fake REST replies.
///
/// The fixtures answer Stroom's `TracesResource` (`/traces/v2`): the traces
/// (`POST /findTracesWithHistogram`) and a trace's spans (`POST /getSpans`, paged in tree order).
/// The tab is opened as `TracesPlugin` does for `ShowTracesEvent`, with the story's data source.
public final class TracesScreenStories {

    private static final DocRef DATA_SOURCE = new DocRef("PlanB", "ds1", "My PlanB store");

    // The trace overview's operation names, their rows' content (indented by depth) and its span info
    private static final String OPERATION_NAME = ".trace-overview-chart__operation-name";
    private static final String OPERATION_CONTENT = ".trace-overview-chart__operation-content";
    private static final String SPAN_INFO = ".trace-overview-chart__span-info";

    private static final String TRACES = """
            {
              "values": [
                {"traceId": "t1", "name": "GET /api/things", "startTime": {"seconds": 1, "nanos": 0},
                  "endTime": {"seconds": 1, "nanos": 500000}, "services": 2, "depth": 2, "totalSpans": 2,
                  "orphan": false}
              ],
              "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}
            }""";

    // The trace's spans in tree order: a root "GET /api/things" and a child "db query"
    private static final String SPANS = """
            {
              "rows": [
                {"span": {"traceId": "t1", "spanId": "root", "name": "GET /api/things",
                  "startTimeUnixNano": "1000000000", "endTimeUnixNano": "1000500000"},
                  "depth": 0, "hasChildren": true},
                {"span": {"traceId": "t1", "spanId": "c1", "parentSpanId": "root", "name": "db query",
                  "startTimeUnixNano": "1000100000", "endTimeUnixNano": "1000300000"},
                  "depth": 1, "hasChildren": false}
              ],
              "more": false,
              "totalSpans": 2
            }""";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .post("/traces/v2/findTracesWithHistogram", RestReply.json(TRACES))
            .post("/traces/v2/getSpans", RestReply.json(SPANS))
            .build();

    private TracesScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/TracesScreen", TracesScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // With a data source, the traces list; selecting one shows its spans
                .story("Traces", TracesScreenStories::render)
                .withPlay(play -> {
                    play.findByText("GET /api/things");
                    play.expect(play.getByText("Total Spans")).toBeInTheDocument();
                    // Shown without a store's document, so only the Traces tab: the others had
                    // nothing to read
                    play.expect(play.getByText("Traces", StroomDom.LINK_TAB_LABEL)).toBeVisible();
                    for (final String other : new String[]{"Settings", "Documentation", "Permissions"}) {
                        play.expect(play.getByText(other, StroomDom.LINK_TAB_LABEL)).not().toBeVisible();
                    }
                    // Select the trace row: its spans load into the waterfall
                    play.click(play.getByText("GET /api/things"));
                    play.findByText("db query");
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/traces/v2/getSpans")
                                    .withJsonBodyContaining("{\"traceId\": \"t1\"}")
                                    .toSpyMatcher());
                    ContentStorySupport.expectNoProblems(play);
                })
                // A span's row is indented by its depth; a click on it shows its Span Info, at the
                // panel's starting width, and a secondary click on its name shows the Copy menu
                .story("SpanInfo", TracesScreenStories::render)
                .withPlay(play -> {
                    play.click(play.findByText("GET /api/things"));
                    final Query child = play.findByText("db query", OPERATION_NAME);
                    play.expect(play.getByText("GET /api/things", OPERATION_NAME).closest(OPERATION_CONTENT))
                            .toHaveStyle("padding-left", "0px");
                    play.expect(child.closest(OPERATION_CONTENT)).toHaveStyle("padding-left", "30px");

                    play.click(child);
                    play.expect(play.findByText("Span Info")).toBeInTheDocument();
                    play.expect(play.querySelector(SPAN_INFO)).toHaveStyle("width", "340px");
                    // Its details scroll, so the keyboard reaches them (they couldn't be scrolled)
                    play.expect(play.getByRole("region", "Span info")).toHaveAttribute("tabindex", "0");

                    play.rightClick(play.getByText("db query", OPERATION_NAME));
                    final Play screen = play.screen();
                    play.expect(screen.findByText("Copy", StroomDom.MENU_ITEM_TEXT)).toBeInTheDocument();
                    play.keyboard("{Escape}");
                    ContentStorySupport.expectNoProblems(play);
                });
    }

    private static Widget render(final StoryContext context) {
        final ContentScreenGinjector injector = GWT.create(ContentScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .build();
        harness.afterStartUp(() -> {
            // As TracesPlugin does for ShowTracesEvent
            final TracesEditor editor = injector.getTracesEditor();
            editor.showTraces(DATA_SOURCE, null, null);
            harness.show(new DocumentEditorHost<>(harness.getEventBus(), editor, TracesDoc.TYPE));
        });
        return harness.asWidget();
    }
}
