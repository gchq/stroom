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
import stroom.security.client.event.OpenApiKeysScreenEvent;
import stroom.security.client.presenter.AppPermissionsPresenter;
import stroom.security.shared.AppPermission;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// Stories of `App/Main/AppPermissionsScreen`, showing Stroom's real [AppPermissionsPresenter] (the
/// 'Application Permissions' tab) with fake REST replies.
///
/// | Stroom endpoint | Used for |
/// |---|---|
/// | `POST /permission/app/v1/fetchAppUserPermissions` | the users (none for a quick filter of 'zzz') |
/// | `POST /permission/app/v1/getAppUserPermissionsReport` | a user's permissions |
/// | `POST /permission/app/v1/changeAppPermission` (the request spy) | changing a permission |
/// | a spy on Stroom's `OpenApiKeysScreenEvent` | opening a user's API keys |
///
/// The user holds `MANAGE_USERS_PERMISSION` (none for `ReadOnlyWithoutManageUsers`). The presenter
/// comes from GIN and is opened as `AppPermissionsPlugin` opens it (refreshed).
public final class AppPermissionsScreenStories {

    /// The name of the spy recording the screens opened from the action menu.
    static final String OPEN_SCREEN = "openScreen";

    private static final String CHANGE_PATH = "/permission/app/v1/changeAppPermission";

    private static final String ALICE_ROW = """
            {"userRef": {"uuid": "u-alice", "subjectId": "alice", "displayName": "Alice Anderson",
                         "group": false, "enabled": true},
             "permissions": ["MANAGE_JOBS_PERMISSION"], "inherited": []}""";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            // The server applies the quick filter: nothing matches 'zzz'
            .route(RequestMatcher.post("/permission/app/v1/fetchAppUserPermissions").withBodyContaining("zzz"),
                    RestReply.json(page("")))
            .post("/permission/app/v1/fetchAppUserPermissions", RestReply.json(page(ALICE_ROW)))
            // MANAGE_NODES is inherited (not explicit) through the 'Administrators' group
            .post("/permission/app/v1/getAppUserPermissionsReport", RestReply.json("""
                    {"explicitPermissions": ["MANAGE_JOBS_PERMISSION"],
                     "inheritedPermissions": {"MANAGE_NODES_PERMISSION": ["Administrators"]}}"""))
            .post(CHANGE_PATH, RestReply.json("true"))
            .build();

    private AppPermissionsScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/AppPermissionsScreen", AppPermissionsScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Selecting a principal shows its permissions; ticking one changes it
                .story("AppPermissions", context -> render(context, AppPermission.MANAGE_USERS_PERMISSION))
                .withPlay(play -> {
                    play.click(play.findByText("Alice Anderson"));
                    play.findByText("Administrator");
                    // The edit grid: Granted / Permission / Description
                    play.expect(editGrid(play).getAllByRole("columnheader").textContents())
                            .toEqual(List.of("Granted", "Permission", "Description"));
                    play.findByText("Access the Jobs screen to manage Stroom's background jobs.");
                    // The details pane is driven by the selection
                    play.findByText("No application permission selected");
                    // The inherited MANAGE_NODES permission names its source
                    play.click(play.getByText("Manage Nodes"));
                    play.findByText("Inherited From:");
                    play.findByText("Administrators");
                    // Grant 'Manage Cache'
                    play.click(tickBox(play, "Manage Cache"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(CHANGE_PATH)
                                    .withJsonBodyContaining("""
                                            {"type": "AddAppPermission", "permission": "MANAGE_CACHE_PERMISSION"}""")
                                    .toSpyMatcher()));
                    SecurityPlays.expectNoProblems(play);
                })
                // The row's action menu opens the API Keys screen; the quick filter narrows the rows
                .story("ActionMenuAndFilter", context -> render(context, AppPermission.MANAGE_USERS_PERMISSION))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Alice Anderson");
                    final Play row = play.within(play.getByText("Alice Anderson").closest("tr"));
                    play.click(row.getByTitle(StroomDom.ACTIONS_TITLE));
                    // The menu omits a link back to App Permissions; open the user in Manage API Keys
                    play.expect(screen.findByText("Show user on the Manage API Keys screen")).toBeInTheDocument();
                    play.expect(screen.queryByText("Show user on the Application Permissions screen")).toBeNull();
                    play.click(screen.getByText("Show user on the Manage API Keys screen"));
                    play.waitFor(() -> play.expect(play.spy(OPEN_SCREEN))
                            .toHaveBeenCalledWith("ApiKeys: Alice Anderson"));
                    // The quick filter narrows the list (the server applies it)
                    play.type(play.getByPlaceholderText(StroomDom.QUICK_FILTER_PLACEHOLDER), "zzz");
                    play.waitFor(3000, () -> play.expect(play.queryByText("Alice Anderson")).toBeNull());
                    SecurityPlays.expectNoProblems(play);
                })
                // Without MANAGE_USERS the tick boxes are borderless and can't be changed
                .story("ReadOnlyWithoutManageUsers", context -> render(context))
                .withPlay(play -> {
                    play.click(play.findByText("Alice Anderson"));
                    final Query box = tickBox(play, "Manage Cache");
                    play.waitFor(() -> play.expect(box).toHaveClass("tickBox-noBorder"));
                    play.click(box);
                    play.sleep(50);
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post(CHANGE_PATH).toSpyMatcher());
                    SecurityPlays.expectNoProblems(play);
                });
    }

    /// The edit grid has no class of its own; it is the form group holding the 'Granted' column.
    ///
    /// @return The grid of the selected principal's permissions.
    private static Play editGrid(final Play play) {
        return play.within(play.getByText("Granted").closest(".form-group"));
    }

    /// @return The 'Granted' tick box (a `TickBoxCell` div) of a permission's row, once it is shown.
    private static Query tickBox(final Play play, final String permission) {
        return play.within(play.findByText(permission).closest("tr")).querySelector(".tickBox");
    }

    private static String page(final String rows) {
        final int count = rows.isEmpty()
                ? 0
                : 1;
        return "{\"values\": [" + rows + "], \"pageResponse\": {\"offset\": 0, \"length\": " + count
                + ", \"total\": " + count + ", \"exact\": true}}";
    }

    private static Widget render(final StoryContext context, final AppPermission... permissions) {
        final SecurityScreenGinjector injector = GWT.create(SecurityScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .appPermissions(permissions)
                .build();
        harness.fn(OPEN_SCREEN);
        harness.getEventBus().addHandler(OpenApiKeysScreenEvent.getType(), event ->
                harness.spy(OPEN_SCREEN, "ApiKeys: " + event.getUserRef().getDisplayName()));

        // Opened as AppPermissionsPlugin opens it (refreshed), once Stroom has started (the list
        // reads the UI config)
        harness.afterStartUp(() -> {
            final AppPermissionsPresenter presenter = harness.addContent(injector.getAppPermissionsPresenter());
            presenter.refresh();
        });
        return harness.asWidget();
    }
}
