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

import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.monitoring.client.presenter.DatabaseTablesMonitoringPresenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/DatabaseTablesScreen`, showing Stroom's real
/// [DatabaseTablesMonitoringPresenter] (the 'Database Tables' tab) with fake REST replies.
///
/// The tables come from `POST /dbStatus/v1` (`DbStatusResource.findSystemTableStatus`). Stroom
/// sorts server side, so the reply honours the criteria's sort: a request sorted by `Table` gets
/// the tables in name order.
public final class DatabaseTablesScreenStories {

    private static final String PATH = "/dbStatus/v1";

    private static final String META = """
            {"db": "stroom", "table": "meta", "count": 1000, "dataSize": 5000000, "indexSize": 1000000}""";
    private static final String DOC = """
            {"db": "stroom", "table": "doc", "count": 20, "dataSize": 40000, "indexSize": 8000}""";
    private static final String ANNOTATION = """
            {"db": "stroom", "table": "annotation", "count": 300, "dataSize": 900000, "indexSize": 200000}""";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .route(RequestMatcher.post(PATH)
                            .withJsonBodyContaining("{\"sortList\": [{\"id\": \"Table\", \"desc\": false}]}"),
                    RestReply.json(page(ANNOTATION, DOC, META)))
            .route(RequestMatcher.post(PATH)
                            .withJsonBodyContaining("{\"sortList\": [{\"id\": \"Table\", \"desc\": true}]}"),
                    RestReply.json(page(META, DOC, ANNOTATION)))
            .post(PATH, RestReply.json(page(META, DOC, ANNOTATION)))
            .build();

    private DatabaseTablesScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/DatabaseTablesScreen", DatabaseTablesScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The grid loads the per-table status; sizes are IEC-formatted and columns sort
                .story("Tables", DatabaseTablesScreenStories::render)
                .withPlay(play -> {
                    play.findByText("meta");
                    play.findByText("annotation");
                    // Data size 5,000,000 bytes is "4.8M" (IEC)
                    play.expect(play.getByText("4.8M")).toBeInTheDocument();
                    // Sort by Table ascending: "annotation" is the first data row.
                    // GWT's grid rows are <tr> elements without a role
                    // attribute, and its header is not a row of the body
                    play.click(play.getByText("Table"));
                    play.waitFor(() -> play.expect(play.querySelectorAll(StroomDom.GRID_ROW).nth(0))
                            .toHaveTextContent("annotation"));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(RequestMatcher.post(PATH)
                            .withJsonBodyContaining("{\"sortList\": [{\"id\": \"Table\"}]}")
                            .toSpyMatcher());
                    ContentStorySupport.expectNoProblems(play);
                });
    }

    private static String page(final String... rows) {
        return "{\"values\": [" + String.join(", ", rows) + "], \"pageResponse\": {\"offset\": 0, \"length\": "
                + rows.length + ", \"total\": " + rows.length + ", \"exact\": true}}";
    }

    private static Widget render(final StoryContext context) {
        final ContentScreenGinjector injector = GWT.create(ContentScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .build();
        harness.addContent(injector.getDatabaseTablesMonitoringPresenter());
        return harness.asWidget();
    }
}
