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

package stroom.gwt.workbench.client.app.dashboard;

import stroom.dashboard.client.main.IndexLoader;
import stroom.dashboard.client.main.ResultComponent;
import stroom.dashboard.client.main.SearchModel;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.TableResultRequest;
import stroom.docref.DocRef;
import stroom.gwt.workbench.client.app.gin.query.QueryScreenGinjector;
import stroom.gwt.workbench.client.app.query.DocumentEditors;
import stroom.gwt.workbench.client.app.query.QueryFixtures;
import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Result;
import stroom.query.api.ResultRequest.Fetch;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Stories matching `App/Dashboard/PausePolling` in the React Storybook: a dashboard search whose
/// paused component is polled for no data.
///
/// The React story drives the port's search coordinator with two result sinks, one paused; this
/// drives Stroom's real dashboard `SearchModel` with two `ResultComponent`s, one paused (as a
/// paused table or visualisation reports), and checks the `componentResultRequests` of its
/// `POST /dashboard/v1/search/{node}` request, as React's recorder does. Like React's, the
/// story shows only an empty host.
public final class PausePollingStories {

    // DashboardSearchResponse: complete, with no results
    private static final RestFixtures FIXTURES = QueryFixtures.resultStoreRoutes(RestFixtures.builder())
            .route(RequestMatcher.post("/dashboard/v1/search/" + RequestMatcher.PATH_WILDCARD),
                    RestReply.json("{\"complete\": true, \"node\": \"node1\", \"queryKey\": {\"uuid\": \"k1\"}, "
                                   + "\"results\": []}"))
            .build();

    private PausePollingStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Dashboard/PausePolling", PausePollingStories.class)
                .story("PausedComponentFetchesNone", PausePollingStories::render)
                .withPlay(play -> {
                    // The paused component is polled with NONE; the live one is not
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/dashboard/v1/search/" + RequestMatcher.PATH_WILDCARD)
                                    .withBody("frozen fetches NONE, live doesn't", body ->
                                            "NONE".equals(fetch(body, "frozen"))
                                            && fetch(body, "live") != null
                                            && !"NONE".equals(fetch(body, "live")))
                                    .toSpyMatcher()));
                    DocumentEditors.expectNoProblems(play);
                });
    }

    // The fetch mode of a component in a search request's body
    private static String fetch(final String body, final String componentId) {
        final Object requests = ((Map<?, ?>) JsonValues.parse(body)).get("componentResultRequests");
        if (requests instanceof List<?>) {
            for (final Object request : (List<?>) requests) {
                final Map<?, ?> map = (Map<?, ?>) request;
                if (Objects.equals(componentId, map.get("componentId"))) {
                    return String.valueOf(map.get("fetch"));
                }
            }
        }
        return null;
    }

    private static Widget render(final StoryContext context) {
        final QueryScreenGinjector injector = GWT.create(QueryScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES).injector(injector).build();
        final Label host = new Label();
        host.getElement().setAttribute("data-testid", "host");
        harness.add(host);
        harness.afterStartUp(() -> {
            final IndexLoader indexLoader = new IndexLoader(harness.getEventBus());
            final SearchModel searchModel = new SearchModel(harness.getEventBus(),
                    harness.getRestFactory(),
                    indexLoader,
                    injector.getDateTimeSettingsFactory(),
                    injector.getResultStoreModel());
            searchModel.init(new DocRef("Dashboard", "d", "D"), "q1");
            searchModel.addComponent("live", new Sink("live", false));
            searchModel.addComponent("frozen", new Sink("frozen", true));
            indexLoader.loadDataSource(new DocRef("Index", "i", "idx"));
            searchModel.startNewSearch(ExpressionOperator.builder().build(),
                    null, null, true, false, null, null, null);
            harness.addCleanUp(searchModel::stop);
        });
        return harness.asWidget();
    }

    // --------------------------------------------------------------------------------


    // A result component (React's result sink) that may be paused
    private static final class Sink implements ResultComponent {

        private final String id;
        private final boolean paused;

        private Sink(final String id, final boolean paused) {
            this.id = id;
            this.paused = paused;
        }

        @Override
        public ComponentSettings getSettings() {
            return TableComponentSettings.builder().build();
        }

        @Override
        public boolean isPaused() {
            return paused;
        }

        @Override
        public ComponentResultRequest getResultRequest(final Fetch fetch) {
            return TableResultRequest.builder().componentId(id).fetch(fetch).build();
        }

        @Override
        public ComponentResultRequest createDownloadQueryRequest() {
            return getResultRequest(Fetch.ALL);
        }

        @Override
        public void reset() {
            // Nothing to reset
        }

        @Override
        public void startSearch() {
            // Nothing to show
        }

        @Override
        public void endSearch() {
            // Nothing to show
        }

        @Override
        public void setData(final Result componentResult) {
            // Nothing to show
        }
    }
}
