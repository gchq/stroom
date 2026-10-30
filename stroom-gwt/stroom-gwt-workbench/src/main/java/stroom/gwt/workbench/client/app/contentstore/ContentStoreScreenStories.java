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


package stroom.gwt.workbench.client.app.contentstore;

import stroom.contentstore.client.component.ContentStoreTabController;
import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ContentStorySupport;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/ContentStoreScreen`, showing Stroom's real [ContentStoreTabController] (the
/// 'Content Store' tab: the content pack list and the selected pack's details) with fake REST
/// replies.
///
/// The stories answer Stroom's `ContentStoreResource` (`/contentstore`): `POST /list` (the packs),
/// `POST /create` (an install), `POST /upgradeContentPack` (an upgrade) and
/// `POST /checkContentUpgradeAvailable` (true for the installed 'core' pack), checked on the
/// request spy. The details panel's credential picker loads the credentials
/// (`POST /credentials/findCredentialsWithPermissions`, none).
public final class ContentStoreScreenStories {

    // The packs have owner ids, as Stroom's ContentStoreMetadata requires one
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
            .post("/contentstore/upgradeContentPack",
                    RestReply.json("{\"status\": \"OK\", \"message\": \"Upgraded.\"}"))
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
                // The licence and Git links say where they go, rather than being the bare URL
                // (shown after them), and that they open a new tab. A pack with no URL has no link,
                // rather than an empty one (gchq/stroom#5406)
                .story("Links", ContentStoreScreenStories::render)
                .withPlay(play -> {
                    final Query licence = play.findByRole("link", "View licence (opens in new tab)");
                    play.expect(licence).toHaveAttribute("href", "https://apache.org/licenses/LICENSE-2.0");
                    play.expect(licence).toHaveAttribute("target", "_blank");
                    play.expect(licence).toHaveAttribute("rel", "noopener noreferrer");
                    play.expect(play.getByText("https://apache.org/licenses/LICENSE-2.0")).toBeVisible();
                    // Underlined, so not told apart from the text beside them by colour alone
                    play.expect(licence).toHaveStyle("text-decoration-line", "underline");
                    final Query git = play.getByRole("link", "View Git repository (opens in new tab)");
                    play.expect(git).toHaveAttribute("href", "https://github.com/example/core.git");
                    play.expect(git).toHaveAttribute("target", "_blank");
                    play.expect(play.getByText("Licence:")).toBeInTheDocument();
                    // Selecting the second pack, which has no licence or Git URL, swaps the details
                    play.click(play.getByText("Extra Pack"));
                    play.findAllByText("Community");
                    play.waitFor(() -> play.expect(play.queryByRole("link", "View licence (opens in new tab)"))
                            .toBeNull());
                    play.expect(play.queryByRole("link", "View Git repository (opens in new tab)")).toBeNull();
                    play.expect(play.queryByText("https://github.com/example/core.git")).toBeNull();
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
                // Upgrade upgrades the upgradable pack, which is then installed: the list shows it so
                // and Upgrade is no longer available
                .story("Upgrade", ContentStoreScreenStories::render)
                .withPlay(play -> {
                    final Query upgrade = play.findByRole("button", StroomDom.button("Upgrade"));
                    play.waitFor(() -> play.expect(upgrade).toBeEnabled());
                    play.click(upgrade);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/contentstore/upgradeContentPack")
                                    .withJsonBodyContaining("{\"id\": \"core\"}")
                                    .toSpyMatcher()));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith("INFO: Content pack upgrade success"));
                    play.waitFor(() -> play.expect(upgrade).toBeDisabled());
                    play.expect(play.queryByText("Content upgradable")).toBeNull();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
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
                    // GWT tells the user the pack was installed
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
        harness.afterStartUp(() -> harness.show(injector.getContentStoreTabController()));
        return harness.asWidget();
    }
}
