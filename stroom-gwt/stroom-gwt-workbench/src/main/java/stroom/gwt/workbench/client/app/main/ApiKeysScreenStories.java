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

import stroom.gwt.workbench.client.app.gin.security.SecurityScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.app.security.SecurityPlays;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.client.presenter.ApiKeysPresenter;
import stroom.security.shared.AppPermission;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Main/ApiKeysScreen` in the React Storybook, showing Stroom's real
/// [ApiKeysPresenter] (the 'Manage API Keys' tab) with fake REST replies.
///
/// The React story's `ApiKeyApi` becomes routes for Stroom's `ApiKeyResource`: `find` →
/// `POST /apikey/v2/find` and `update` → `PUT /apikey/v2/{id}` (an echo; its recorder becomes a
/// check on the request spy). The user holds `MANAGE_USERS_PERMISSION`, so the Owner column is
/// shown, as React's fixture. The presenter comes from GIN and is opened as `ApiKeysPlugin` opens it
/// (refreshed).
public final class ApiKeysScreenStories {

    private static final String KEY = """
            {"id": 1, "version": 1, "name": "CI pipeline key", "apiKeyPrefix": "sk_abc",
              "owner": {"uuid": "u-alice", "subjectId": "alice", "displayName": "Alice Anderson",
                        "group": false, "enabled": true},
              "enabled": true, "expireTimeMs": 2000000000000, "hashAlgorithm": "SHA3_256",
              "comments": "used by CI"}""";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .post("/apikey/v2/find", RestReply.json("""
                    {"values": [KEY], "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}}"""
                    .replace("KEY", KEY)))
            .put("/apikey/v2/1", request -> RestReply.json(request.getBody()))
            .build();

    private ApiKeysScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/ApiKeysScreen", ApiKeysScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The keys list (owner and hash algorithm); editing a key updates it
                .story("ApiKeys", ApiKeysScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("CI pipeline key");
                    play.expect(play.getByText("Alice Anderson")).toBeInTheDocument();
                    play.expect(play.getByText("SHA3-256")).toBeInTheDocument();
                    // Double-click the key: 'Edit API key', rename, OK.
                    // Differs from React: Stroom's dialogs have no role="dialog"
                    play.dblClick(play.getByText("CI pipeline key"));
                    final Play dialog = screen.within(screen.findByText("Edit API key").closest(StroomDom.DIALOG));
                    final Query name = dialog.getByLabelText("API Key Name");
                    play.clear(name);
                    play.type(name, "Renamed key");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put("/apikey/v2/1")
                                    .withJsonBodyContaining("{\"id\": 1, \"name\": \"Renamed key\"}")
                                    .toSpyMatcher()));
                    SecurityPlays.expectNoProblems(play);
                });
    }

    private static Widget render(final StoryContext context) {
        final SecurityScreenGinjector injector = GWT.create(SecurityScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .appPermissions(AppPermission.MANAGE_USERS_PERMISSION)
                .build();
        // Opened as ApiKeysPlugin opens it (refreshed), once Stroom has started (the list reads the
        // UI config and formats dates with the user's preferences)
        harness.afterStartUp(() -> {
            final ApiKeysPresenter presenter = harness.addContent(injector.getApiKeysPresenter());
            presenter.refresh();
        });
        return harness.asWidget();
    }
}
