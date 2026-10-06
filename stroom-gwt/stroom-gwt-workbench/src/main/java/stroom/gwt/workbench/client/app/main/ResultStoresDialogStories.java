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
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.query.client.presenter.ResultStorePresenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;

/// Stories matching `App/Main/ResultStoresDialog` in the React Storybook, showing Stroom's real
/// [ResultStorePresenter] (the 'Search Result Stores' dialog) with fake REST replies.
///
/// The React story's `ResultStoreApi` becomes routes for Stroom's `NodeResource` and
/// `ResultStoreResource` (`/result-store/v1`): `listNodes` → `GET /node/v1/all`, `find` →
/// `POST /find/{node}`, `terminate` → `POST /terminate/{node}`, `destroy` → `POST /destroy/{node}`,
/// `updateSettings` → `POST /update/{node}`. Its recorder becomes checks on the request spy. The
/// dialog is shown as `ResultStorePlugin` does (`show()`).
public final class ResultStoresDialogStories {

    private static final String FIND_PATH = "/result-store/v1/find/node1";

    private static final String STORE = """
            {"owner": {"uuid": "OWNER", "subjectId": "OWNER", "displayName": "OWNER", "enabled": true},
              "nodeName": "node1", "queryKey": {"uuid": "KEY"}, "creationTime": 1700000000000, "storeSize": SIZE,
              "complete": COMPLETE, "searchRequestSource": {"sourceType": "SOURCE"},
              "storeLifespan": {"timeToLive": "PT1H", "timeToIdle": "PT1H", "destroyOnTabClose": true,
                "destroyOnWindowClose": true},
              "searchProcessLifespan": {"timeToLive": "PT1H", "timeToIdle": "PT1H", "destroyOnTabClose": true,
                "destroyOnWindowClose": true}}""";

    private static final String STORES = page(
            store("admin", "q-1", 5000000, false, "DASHBOARD_UI"),
            store("analyst", "q-2", 2000, true, "TABLE_BUILDER_ANALYTIC"));

    private ResultStoresDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/ResultStoresDialog", ResultStoresDialogStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The store list loads (fanned out per node); a non-analytic incomplete store can be
                // terminated
                .story("Stores", context -> render(context, STORES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("admin");
                    screen.findByText("analyst");
                    // Select the DASHBOARD_UI (non-analytic, incomplete) store: Terminate is enabled.
                    // Differs from React: GWT's toolbar buttons are found by their titles
                    play.click(screen.getByText("admin"));
                    final Query terminate = screen.getByTitle("Terminate Search");
                    play.waitFor(() -> play.expect(terminate).not().toHaveClass("disabled"));
                    play.click(terminate);
                    // GWT confirms before terminating
                    play.waitFor(() -> play.expect(screen.getByText(
                            TextMatch.containingIgnoreCase("terminate this search"))).toBeInTheDocument());
                    play.click(screen.within(screen.getByText(TextMatch.containingIgnoreCase("terminate this search"))
                                    .closest(StroomDom.DIALOG))
                            .getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/result-store/v1/terminate/node1")
                                    .withJsonBodyContaining("{\"uuid\": \"q-1\"}")
                                    .toSpyMatcher()));
                    // Differs from React: GWT then tells the user it terminated the search
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith("INFO: Terminated"));
                    play.click(screen.within(screen.getByText("Terminated").closest(StroomDom.DIALOG))
                            .getByRole("button", StroomDom.button("Close")));
                    // The analytic store can't be terminated or deleted
                    play.click(screen.findByText("analyst"));
                    play.waitFor(() -> play.expect(screen.getByTitle("Delete Store")).toHaveClass("disabled"));
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // Selecting a store and opening Settings edits its lifespans
                .story("Settings", context -> render(context, STORES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(screen.findByText("admin"));
                    // GWT: the toolbar button is titled "Store Settings"; the dialog it opens is
                    // captioned "Change Result Store Settings"
                    final Query settings = screen.getByTitle("Store Settings");
                    play.waitFor(() -> play.expect(settings).not().toHaveClass("disabled"));
                    play.click(settings);
                    final Play dialog = screen.within(screen.findByText("Change Result Store Settings")
                            .closest(StroomDom.DIALOG));
                    final Query ttl = dialog.getByLabelText("Store Time To Live");
                    play.clear(ttl);
                    play.type(ttl, "P30D");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/result-store/v1/update/node1")
                                    .withJsonBodyContaining("{\"queryKey\": {\"uuid\": \"q-1\"}, "
                                            + "\"storeLifespan\": {\"timeToLive\": \"P30D\"}}")
                                    .toSpyMatcher()));
                    ContentStorySupport.expectNoProblems(play);
                })
                // With more than 100 stores the pager pages the merged list client side
                // (ResultPage.createPageLimitedList): page 1 shows the first 100
                .story("Paged", context -> render(context, manyStores()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("user-0");
                    play.expect(screen.queryByText("user-100")).toBeNull();
                    // The pager reports the full total across the fan-out.
                    // Differs from React: the pager is in the dialog, on the page's body
                    screen.findByText("150");
                    // Forward: page 2 shows the rest
                    play.click(screen.getByTitle("Forward"));
                    screen.findByText("user-100");
                    play.expect(screen.queryByText("user-0")).toBeNull();
                    ContentStorySupport.expectNoProblems(play);
                });
    }

    private static String store(final String owner,
                                final String key,
                                final long size,
                                final boolean complete,
                                final String source) {
        return STORE.replace("OWNER", owner)
                .replace("KEY", key)
                .replace("SIZE", String.valueOf(size))
                .replace("COMPLETE", String.valueOf(complete))
                .replace("SOURCE", source);
    }

    private static String page(final String... stores) {
        return "{\"values\": [" + String.join(", ", stores) + "], \"pageResponse\": {\"offset\": 0, \"length\": "
                + stores.length + ", \"total\": " + stores.length + ", \"exact\": true}}";
    }

    private static String manyStores() {
        final List<String> stores = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            stores.add(store("user-" + i, "q-" + i, 1000, false, "DASHBOARD_UI"));
        }
        return page(stores.toArray(new String[0]));
    }

    private static Widget render(final StoryContext context, final String stores) {
        final RestFixtures fixtures = RestFixtures.builder()
                .get("/node/v1/all", RestReply.json("[\"node1\"]"))
                .post(FIND_PATH, RestReply.json(stores))
                .post("/result-store/v1/terminate/node1", RestReply.json("true"))
                .post("/result-store/v1/destroy/node1", RestReply.json("true"))
                .post("/result-store/v1/update/node1", RestReply.json("true"))
                .build();
        final ContentScreenGinjector injector = GWT.create(ContentScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                // The confirmations are answered in Stroom's real dialog
                .realAlerts()
                .build();
        // As ResultStorePlugin does
        harness.afterStartUp(() -> injector.getResultStorePresenter().show());
        return harness.asWidget();
    }
}
