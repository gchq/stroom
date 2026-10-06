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

import stroom.contentstore.client.presenter.ContentStorePresenter;
import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Main/ContentStoreScreen` in the React Storybook, showing Stroom's real
/// [ContentStorePresenter] (the 'Content Store' tab: the content pack list and the selected pack's
/// details) with fake REST replies.
///
/// The React story's `ContentStoreApi` becomes routes for Stroom's `ContentStoreResource`
/// (`/contentstore`): `list` → `POST /list`, `install` → `POST /create`, `upgrade` →
/// `POST /upgradeContentPack`, `checkUpgrade` → `POST /checkContentUpgradeAvailable` (true for the
/// installed 'core' pack). Its recorder becomes checks on the request spy. The details panel's
/// credential picker loads the credentials (`POST /credentials/findCredentialsWithPermissions`, none).
public final class ContentStoreScreenStories {

    // Differs from React: the packs have owner ids, as Stroom's ContentStoreMetadata requires one
    private static final String PACKS = """
            {
              "values": [
                {
                  "contentPack": {"id": "core", "uiName": "Stroom Core",
                    "details": "# Core\\nThe **core** content pack.",
                    "licenseName": "Apache 2.0", "licenseUrl": "https://apache.org/licenses/LICENSE-2.0",
                    "gitUrl": "https://github.com/example/core.git", "gitBranch": "main", "gitPath": "",
                    "gitCommit": "", "gitNeedsAuth": false, "stroomPath": "/System", "gitRepoName": "core",
                    "contentStoreMetadata": {"ownerId": "stroom", "ownerName": "Stroom"}},
                  "installationStatus": "INSTALLED"
                },
                {
                  "contentPack": {"id": "extra", "uiName": "Extra Pack", "gitUrl": "", "gitBranch": "",
                    "gitPath": "", "gitCommit": "", "gitNeedsAuth": false,
                    "contentStoreMetadata": {"ownerId": "community", "ownerName": "Community"}},
                  "installationStatus": "NOT_INSTALLED"
                }
              ],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}
            }""";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .post("/contentstore/list", RestReply.json(PACKS))
            .route(RequestMatcher.post("/contentstore/checkContentUpgradeAvailable")
                            .withJsonBodyContaining("{\"id\": \"core\"}"),
                    RestReply.json("{\"ok\": true, \"value\": true}"))
            .post("/contentstore/checkContentUpgradeAvailable", RestReply.json("{\"ok\": true, \"value\": false}"))
            .post("/contentstore/create", RestReply.json("{\"status\": \"OK\", \"message\": \"Installed.\"}"))
            .post("/contentstore/upgradeContentPack", RestReply.json("{\"status\": \"OK\"}"))
            // The details panel's credential picker (for packs needing authentication) loads the
            // credentials: none
            .post("/credentials/findCredentialsWithPermissions", RestReply.json(
                    "{\"values\": [], \"pageResponse\": {\"offset\": 0, \"length\": 0, \"total\": 0, "
                            + "\"exact\": true}}"))
            .build();

    private ContentStoreScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/ContentStoreScreen", ContentStoreScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The pack list loads and selects the first; the details panel shows its metadata
                .story("ContentStore", ContentStoreScreenStories::render)
                .withPlay(play -> {
                    play.findByText("Extra Pack");
                    // The first row is selected: the details show the licence and git url
                    play.expect(play.findByText("Apache 2.0")).toBeInTheDocument();
                    play.expect(play.getByText("https://github.com/example/core.git")).toBeInTheDocument();
                    // Selecting the second pack swaps the details
                    play.click(play.getByText("Extra Pack"));
                    play.findAllByText("Community");
                    ContentStorySupport.expectNoProblems(play);
                })
                // After the list loads, a per-row upgrade check promotes an installed pack with a
                // newer version to "Content upgradable", which enables Upgrade
                .story("UpgradeCheckPromotesRow", ContentStoreScreenStories::render)
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getAllByText("Content upgradable").count())
                            .toBeGreaterThanOrEqual(1));
                    play.waitFor(() -> play.expect(play.getByRole("button", StroomDom.button("Upgrade")))
                            .toBeEnabled());
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/contentstore/checkContentUpgradeAvailable")
                                    .withJsonBodyContaining("{\"id\": \"core\"}")
                                    .toSpyMatcher());
                    ContentStorySupport.expectNoProblems(play);
                })
                // A NOT_INSTALLED pack enables Install; clicking it installs the pack
                .story("Install", ContentStoreScreenStories::render)
                .withPlay(play -> {
                    play.findByText("Extra Pack");
                    play.click(play.getByText("Extra Pack"));
                    final Query install = play.findByRole("button", StroomDom.button("Install"));
                    play.waitFor(() -> play.expect(install).not().toBeDisabled());
                    play.click(install);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/contentstore/create")
                                    .withJsonBodyContaining("{\"contentPack\": {\"id\": \"extra\"}}")
                                    .toSpyMatcher()));
                    // Differs from React: GWT tells the user the pack was installed
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith("INFO: Creation success"));
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                });
    }

    private static Widget render(final StoryContext context) {
        final ContentScreenGinjector injector = GWT.create(ContentScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .build();
        harness.afterStartUp(() -> harness.addContent(injector.getContentStorePresenter()));
        return harness.asWidget();
    }
}
