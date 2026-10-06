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

import stroom.data.client.presenter.DataPreviewTabPresenter;
import stroom.data.client.presenter.DataViewType;
import stroom.data.client.presenter.SourceTabPresenter;
import stroom.gwt.workbench.client.app.gin.processing.ProcessingScreenGinjector;
import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RecordedRequest;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.pipeline.shared.SourceLocation;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.TextRange;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.Map;

/// Stories matching `App/Main/DataScreen` in the React Storybook, showing Stroom's real data tabs
/// for a query result's data link (`HyperlinkEventHandlerImpl.openData` with display type
/// `STROOM_TAB`): [DataPreviewTabPresenter] for a `PREVIEW` link and [SourceTabPresenter] for a
/// `SOURCE` link, as `DataDisplaySupport` opens them.
///
/// React's `loadSource` is `POST /data/v1/fetch` (with `GET /meta/v1/{id}` for the stream's
/// details); its recorded requests are read back from the request spy.
public final class DataScreenStories {

    private static final String FETCH_PATH = "/data/v1/fetch";

    private static final String DATA = """
            {"type": "data", "feedName": "TEST_FEED", "streamTypeName": "Events",
              "classification": "UNKNOWN CLASSIFICATION",
              "sourceLocation": {"metaId": 1001, "partIndex": 0, "recordIndex": 4,
                "dataRange": {"locationFrom": {"type": "default", "lineNo": 1, "colNo": 1}, "charOffsetFrom": 0,
                  "byteOffsetFrom": 0, "locationTo": {"type": "default", "lineNo": 3, "colNo": 10},
                  "charOffsetTo": 26, "byteOffsetTo": 26, "length": 27},
                "highlights": []},
              "itemRange": {"offset": 4, "length": 1}, "totalItemCount": {"count": 5, "exact": true},
              "totalCharacterCount": {"count": 27, "exact": true}, "totalBytes": 27,
              "availableChildStreamTypes": [null],
              "data": "line one\\nline two\\nline three",
              "html": false, "dataType": "NON_SEGMENTED", "displayMode": "TEXT"}""";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .post(FETCH_PATH, RestReply.json(DATA))
            .get("/data/v1/1001/parts/0/child-types", RestReply.json("[null]"))
            // The preview's stream (MetaResource.fetch)
            .get("/meta/v1/1001", RestReply.json("{\"id\": 1001, \"feedName\": \"TEST_FEED\", "
                    + "\"typeName\": \"Events\", \"status\": \"UNLOCKED\", \"createMs\": 1700000000000}"))
            .build();

    private DataScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/DataScreen", DataScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The preview tab fetches just the requested line/col range
                .story("Preview", context -> render(context, DataViewType.PREVIEW))
                .withPlay(play -> {
                    final Spy requests = play.spy(ScreenHarness.REQUEST_SPY);
                    play.waitFor(() -> play.expect("the data fetched", () -> lastLocation(requests)).not().toBeNull());
                    play.expect("the record index", () -> lastLocation(requests).get("recordIndex"))
                            .toEqual(JsonValues.parse("4"));
                    // PREVIEW seeds the fetch with the data range window
                    play.expect("the data range's first line", () -> firstLine(lastLocation(requests)))
                            .toEqual(JsonValues.parse("2"));
                    expectNoProblems(play);
                })
                // The source tab fetches the whole stream; the range is only a highlight
                .story("Source", context -> render(context, DataViewType.SOURCE))
                .withPlay(play -> {
                    final Spy requests = play.spy(ScreenHarness.REQUEST_SPY);
                    play.waitFor(() -> play.expect("the data fetched", () -> lastLocation(requests)).not().toBeNull());
                    // No data range window from the link: the range is a highlight only.
                    // Differs from React: GWT's SourcePresenter asks for its own page of the stream
                    // (from the first character), not for no range at all
                    play.expect("the data range's first line", () -> firstLine(lastLocation(requests)))
                            .not().toEqual(JsonValues.parse("2"));
                    play.waitFor(() -> play.expect(play.getAllByText(TextMatch.containing("line one")).count())
                            .toBeGreaterThan(0));
                    expectNoProblems(play);
                });
    }

    // The source location of the last data fetch
    private static Map<?, ?> lastLocation(final Spy requests) {
        Map<?, ?> location = null;
        for (final List<Object> call : requests.getCalls()) {
            final RecordedRequest request = RecordedRequest.parse(String.valueOf(call.get(0)));
            if ("POST".equals(request.getMethod()) && FETCH_PATH.equals(request.getPath())) {
                location = (Map<?, ?>) ((Map<?, ?>) JsonValues.parse(request.getBody())).get("sourceLocation");
            }
        }
        return location;
    }

    private static Object firstLine(final Map<?, ?> location) {
        if (location == null || !(location.get("dataRange") instanceof Map)) {
            return null;
        }
        final Object from = ((Map<?, ?>) location.get("dataRange")).get("locationFrom");
        return from instanceof Map
                ? ((Map<?, ?>) from).get("lineNo")
                : null;
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    // The source location HyperlinkEventHandlerImpl.openData builds for the React story's link
    // (id 1001, part 0, record 4, lines 2:1 to 3:9)
    private static SourceLocation sourceLocation(final DataViewType dataViewType) {
        final SourceLocation.Builder builder = SourceLocation.builder(1001)
                .withPartIndex(0L)
                .withRecordIndex(4L);
        if (DataViewType.PREVIEW.equals(dataViewType)) {
            builder.withDataRangeBuilder(dataRange -> dataRange
                    .fromLocation(new DefaultLocation(2, 1))
                    .toLocation(new DefaultLocation(3, 9)));
        }
        builder.withHighlight(new TextRange(new DefaultLocation(2, 1), new DefaultLocation(3, 9)));
        return builder.build();
    }

    private static Widget render(final StoryContext context, final DataViewType dataViewType) {
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .build();
        final SourceLocation sourceLocation = sourceLocation(dataViewType);
        harness.afterStartUp(() -> {
            // As DataDisplaySupport.openStroomTab opens it (DataPreviewTabPlugin/SourceTabPlugin)
            if (DataViewType.PREVIEW.equals(dataViewType)) {
                final DataPreviewTabPresenter presenter = injector.getDataPreviewTabPresenter();
                harness.addContent(presenter);
                presenter.setInitDataViewType(dataViewType);
                presenter.setSourceLocation(sourceLocation);
            } else {
                final SourceTabPresenter presenter = injector.getSourceTabPresenter();
                harness.addContent(presenter);
                presenter.setSourceLocationUsingHighlight(sourceLocation);
            }
        });
        return harness.asWidget();
    }
}
