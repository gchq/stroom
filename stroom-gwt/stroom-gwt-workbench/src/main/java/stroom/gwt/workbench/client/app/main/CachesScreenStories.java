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

import stroom.cache.client.presenter.CachePresenter;
import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RecordedRequest;
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

/// Stories matching `App/Main/CachesScreen` in the React Storybook, showing Stroom's real
/// [CachePresenter] (the 'Caches' tab: the caches list and the selected cache's per-node
/// statistics) with fake REST replies.
///
/// The React story's `CacheApi` fixture becomes routes for Stroom's `NodeResource` and
/// `CacheResource`:
///
/// | React | Stroom |
/// |---|---|
/// | `listNodes` | `GET /node/v1/all` |
/// | `listCaches` | `GET /cache/v1/list?nodeName=` (once per node) |
/// | `cacheInfo` | `GET /cache/v1/info?cacheName=&nodeName=` |
/// | `clear` | `DELETE /cache/v1?cacheName=&nodeName=` |
///
/// and its recorder becomes a check on the request spy. The presenter comes from GIN, and is
/// shown as `CacheMonitoringPlugin` opens it.
public final class CachesScreenStories {

    // CacheResource.list(nodeName): the same caches on every node (the presenter merges them)
    private static final String CACHES = """
            {
              "values": [
                {"cacheName": "Reference Data Store",
                  "basePropertyPath": {"parentParts": ["stroom", "pipeline"], "leafPart": "referenceData"}},
                {"cacheName": "User Cache", "basePropertyPath": {"parentParts": []}}
              ],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}
            }""";

    // CacheResource.info(cacheName, nodeName). 'ExpireAfterWrite' is there for the column width
    // rule: GWT sizes each statistic's column from its header
    private static final String CACHE_INFO = """
            {
              "values": [
                {"name": "NAME", "nodeName": "NODE",
                  "map": {"HitCount": "100", "MissCount": "20", "Size": "5", "ExpireAfterWrite": "10m"}}
              ],
              "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}
            }""";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .get("/node/v1/all", RestReply.json("[\"node1\", \"node2\"]"))
            .get("/cache/v1/list", RestReply.json(CACHES))
            .get("/cache/v1/info", CachesScreenStories::cacheInfo)
            .delete("/cache/v1", RestReply.json("1"))
            .build();

    private CachesScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/CachesScreen", CachesScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Selecting a cache loads its per-node stats (dynamic columns + Hit Ratio); a
                // per-node Clear clears the cache on that node
                .story("CacheStats", CachesScreenStories::render)
                .withPlay(play -> {
                    play.findByText("Reference Data Store");
                    play.findByText("User Cache");
                    // The config property path is shown dotted
                    play.expect(play.getByText("stroom.pipeline.referenceData")).toBeInTheDocument();
                    // Select the cache: the per-node grid loads
                    play.click(play.getByText("Reference Data Store"));
                    play.findByText("node1");
                    play.findByText("node2");
                    // Derived Hit Ratio = 100 / (100 + 20)
                    play.waitFor(() -> play.expect(play.getAllByText("0.833").count()).toBeGreaterThan(0));
                    // GWT sizes each statistic's column from its header text (determineColumnWidth)
                    final Query longHeader = play.getByRole("columnheader", "Expire After Write");
                    final Query shortHeader = play.getByRole("columnheader", "Hit Count");
                    play.waitFor(() -> play.expect("the 'Expire After Write' header's width",
                                    () -> longHeader.width().get() - shortHeader.width().get())
                            .toBeGreaterThan(0));
                    // Clear on node1 (the bottom grid). The icon button column is GWT's SvgCell, a
                    // clickable div
                    play.click(play.within(play.getByText("node1").closest("tr"))
                            .getByTitle("Clear and rebuild cache"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.delete("/cache/v1")
                                    .withQuery("cacheName=Reference+Data+Store&nodeName=node1")
                                    .toSpyMatcher()));
                    ContentStorySupport.expectNoProblems(play);
                });
    }

    private static RestReply cacheInfo(final RecordedRequest request) {
        return RestReply.json(CACHE_INFO
                .replace("NAME", ContentStorySupport.queryParam(request, "cacheName"))
                .replace("NODE", ContentStorySupport.queryParam(request, "nodeName")));
    }

    private static Widget render(final StoryContext context) {
        final ContentScreenGinjector injector = GWT.create(ContentScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .build();
        harness.addContent(injector.getCachePresenter());
        return harness.asWidget();
    }
}
