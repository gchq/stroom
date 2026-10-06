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

import stroom.docref.DocRef;
import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.pathways.client.presenter.TracesPresenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Main/TracesScreen` in the React Storybook, showing Stroom's real
/// [TracesPresenter] (the 'Traces' tab: the trace list and the selected trace's span waterfall) with
/// fake REST replies.
///
/// The React story's `TracesApi` becomes routes for Stroom's `TracesResource` (`/traces/v2`):
/// `findTraces` → `POST /findTracesWithHistogram`, `findTrace` → `POST /getSpans` (GWT pages a
/// rooted trace's spans in tree order rather than fetching the whole trace). The tab is opened as
/// `TracesPlugin` does for `ShowTracesEvent`, with the React story's `dataSourceRef`.
public final class TracesScreenStories {

    private static final DocRef DATA_SOURCE = new DocRef("PlanB", "ds1", "My PlanB store");

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
                    // Select the trace row: its spans load into the waterfall
                    play.click(play.getByText("GET /api/things"));
                    play.findByText("db query");
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/traces/v2/getSpans")
                                    .withJsonBodyContaining("{\"traceId\": \"t1\"}")
                                    .toSpyMatcher());
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
            final TracesPresenter presenter = injector.getTracesPresenter();
            presenter.setDataSourceRef(DATA_SOURCE);
            presenter.setPathway(null);
            presenter.setFilter(null);
            presenter.forceRefresh();
            harness.addContent(presenter);
        });
        return harness.asWidget();
    }
}
