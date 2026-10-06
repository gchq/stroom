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
import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.app.security.SecurityPlays;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.client.event.OpenUsersAndGroupsScreenEvent;
import stroom.security.identity.client.presenter.AccountsPresenter;
import stroom.security.shared.AppPermission;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.SortIcon;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Main/AccountsScreen` in the React Storybook, showing Stroom's real
/// [AccountsPresenter] (the 'Manage Accounts' tab) with fake REST replies.
///
/// | React seam | Stroom REST endpoint |
/// |---|---|
/// | `AccountApi.find` (its recorder of the criteria) | `POST /account/v1/search` (the request spy) |
/// | `AccountApi.update` (its recorder of the changes) | `PUT /account/v1/{id}` (the request spy) |
/// | `users.fetchBySubjectId` | `GET /users/v1/fetchBySubjectId/{subjectId}` |
/// | `openScreen` | a spy on Stroom's `OpenUsersAndGroupsScreenEvent` |
///
/// The user holds `MANAGE_USERS_PERMISSION`, which `AccountsPlugin` requires. The presenter comes
/// from GIN and is opened as `AccountsPlugin.open` opens it (refreshed).
public final class AccountsScreenStories {

    /// The name of the spy recording the screens opened from the User Id command link.
    static final String OPEN_SCREEN = "openScreen";

    private static final String ADMIN = """
            {"id": 1, "userId": "admin", "firstName": "Admin", "lastName": "User",
              "email": "admin@example.com", "enabled": true, "inactive": false, "neverExpires": false,
              "lastLoginMs": 1700000000000, "failureCount": 0, "comments": ""}""";
    private static final String LOCKED = """
            {"id": 2, "userId": "locked.user", "firstName": "Locked", "lastName": "User",
              "email": "locked@example.com", "enabled": true, "inactive": false, "neverExpires": false,
              "failureCount": 3, "failureLockedMs": 1700000000000, "failureLockedUntilMs": 4100000000000,
              "comments": ""}""";

    // AccountResource.find()
    private static final String ACCOUNTS = """
            {"values": [ADMIN, LOCKED], "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}}"""
            .replace("ADMIN", ADMIN)
            .replace("LOCKED", LOCKED);

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .post("/account/v1/search", RestReply.json(ACCOUNTS))
            .put("/account/v1/1", RestReply.json("true"))
            .get("/users/v1/fetchBySubjectId/admin", RestReply.json("""
                    {"id": 1, "uuid": "u-1", "subjectId": "admin", "displayName": "Admin User",
                      "group": false, "enabled": true}"""))
            .build();

    private AccountsScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/AccountsScreen", AccountsScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The User Id command link opens the user on the Users and Groups screen
                .story("UserIdCommandLink", AccountsScreenStories::render)
                .withPlay(play -> {
                    play.findByText("admin");
                    // Only the open icon fires the command; clicking the text selects the row
                    final Play row = play.within(play.getByText("admin").closest("tr"));
                    final Query open = row.querySelector(StroomDom.COMMAND_LINK_OPEN);
                    play.expect(open.closest("[title]")).toHaveAttribute("title",
                            "Open account 'admin' on the Users and Groups screen.");
                    play.click(open);
                    play.waitFor(() -> play.expect(play.spy(OPEN_SCREEN))
                            .toHaveBeenCalledWith("UsersAndGroups: Admin User"));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.get("/users/v1/fetchBySubjectId/admin").toSpyMatcher());
                    expectNoProblems(play);
                })
                // React: with no openScreen the cell is plain text
                .story("UserIdPlainWithoutTarget", AccountsScreenStories::render)
                .withPlay(play -> {
                    play.findByText("admin");
                    final Play row = play.within(play.getByText("admin").closest("tr"));
                    // Differs from React: Stroom's grid always has somewhere to open the user (the
                    // Users and Groups screen), so the User Id cell always has its open icon; React's
                    // plain-text cell (no openScreen) has no GWT equivalent
                    play.expect(row.querySelector(StroomDom.COMMAND_LINK_OPEN)).toBeInTheDocument();
                    expectNoProblems(play);
                })
                // The account states have a column each; editing sends only the changed values
                .story("Accounts", AccountsScreenStories::render)
                .withPlay(play -> {
                    play.findByText("admin");
                    final Query rowElement = play.getByText("admin").closest("tr");
                    final Play row = play.within(rowElement);
                    play.expect(row.getByText("Enabled")).toBeInTheDocument();
                    play.expect(row.getByText("Active")).toBeInTheDocument();
                    play.expect(play.getByText("admin@example.com")).toBeInTheDocument();

                    // Double-click the account: 'Edit Account', rename, OK.
                    // Differs from React: Stroom's dialogs have no role="dialog"
                    play.dblClick(row.getByText("admin@example.com"));
                    final Play screen = play.screen();
                    final Play dialog = screen.within(screen.findByText("Edit Account").closest(StroomDom.DIALOG));
                    final Query first = dialog.getByLabelText("First Name");
                    play.clear(first);
                    play.type(first, "Administrator");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    // Only the changed value is sent.
                    // Differs from React: RestyGWT sends the members left alone as null
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            SecurityPlays.withOnlyValues(RequestMatcher.put("/account/v1/1"),
                                    "{\"firstName\": \"Administrator\"}")
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                })
                // A locked account shows 'Locked until <time>'
                .story("LockedAccount", AccountsScreenStories::render)
                .withPlay(play -> {
                    play.findByText("locked.user");
                    final Play row = play.within(play.getByText("locked.user").closest("tr"));
                    play.expect(row.getByText(TextMatch.startingWith("Locked until "))).toBeInTheDocument();
                    // The unlocked account's Locked cell stays blank
                    final Play adminRow = play.within(play.getByText("admin").closest("tr"));
                    play.expect(adminRow.queryByText(TextMatch.startingWith("Locked"))).toBeNull();
                    expectNoProblems(play);
                })
                // The initial sort (User Id, ascending) is sent with the first fetch
                .story("InitialSortIsSent", AccountsScreenStories::render)
                .withPlay(play -> {
                    play.findByText("admin");
                    final Spy requests = play.spy(ScreenHarness.REQUEST_SPY);
                    play.waitFor(() -> play.expect("the first search's sort list",
                                    () -> SecurityPlays.firstBody(requests, RequestMatcher.post("/account/v1/search")))
                            .toSatisfy("sorts by User Id, ascending", body -> body != null
                                    && JsonValues.jsonContains((String) body,
                                    "{\"sortList\": [{\"id\": \"userid\", \"desc\": false}]}")));
                    // Differs from React: GWT's headers have no aria-sort; the sorted header shows
                    // the 'Sort Ascending' icon
                    play.expect(play.within(play.getByText("User Id").closest("th"))
                            .getByTitle(SortIcon.SORT_ASCENDING)).toBeInTheDocument();
                    expectNoProblems(play);
                });
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    private static Widget render(final StoryContext context) {
        final SecurityScreenGinjector injector = GWT.create(SecurityScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .appPermissions(AppPermission.MANAGE_USERS_PERMISSION)
                .build();
        harness.fn(OPEN_SCREEN);
        harness.getEventBus().addHandler(OpenUsersAndGroupsScreenEvent.getType(), event ->
                harness.spy(OPEN_SCREEN, "UsersAndGroups: " + event.getUserRef().getDisplayName()));

        // Opened as AccountsPlugin.open opens it (refreshed), once Stroom has started (the grid
        // reads the UI config and formats dates with the user's preferences)
        harness.afterStartUp(() -> harness.addContent(injector.getAccountsPresenter()).refresh());
        return harness.asWidget();
    }
}
