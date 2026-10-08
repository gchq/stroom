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
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.client.event.OpenUsersScreenEvent;
import stroom.security.client.presenter.UserAndGroupsPresenter;
import stroom.security.shared.AppPermission;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;

/// Stories matching `App/Main/UserGroupsScreen` in the React Storybook, showing Stroom's real
/// [UserAndGroupsPresenter] (the 'User Groups' tab) with fake REST replies.
///
/// | React seam | Stroom REST endpoint |
/// |---|---|
/// | `findUsers` (all; `ParentsOf`; `ChildrenOf`) | `POST /users/v1/find`, routed by the term's field |
/// | `findUserRefs` (the user picker) | `POST /userRef/v1/find` |
/// | `updateUser` (its recorder) | `POST /users/v1/updateUser` (the request spy) |
/// | `openScreen` | a spy on Stroom's `OpenUsersScreenEvent` |
///
/// The user holds `MANAGE_USERS_PERMISSION`, which `UsersAndGroupsPlugin` requires to open the
/// screen (React's fixture holds none). The presenter comes from GIN and is opened as
/// `UsersAndGroupsPlugin.open` opens it (refreshed).
public final class UserGroupsScreenStories {

    /// The name of the spy recording the screens opened from the action menus.
    static final String OPEN_SCREEN = "openScreen";

    private static final String ALICE = """
            {"id": 1, "uuid": "u-alice", "subjectId": "alice", "displayName": "Alice Anderson",
              "fullName": "Alice A Anderson", "group": false, "enabled": true}""";
    private static final String ADMINS = """
            {"id": 2, "uuid": "g-admins", "subjectId": "Administrators", "displayName": "Administrators",
              "group": true, "enabled": true}""";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            // Member Of: no groups; Members: Alice; otherwise everyone
            .route(RequestMatcher.post("/users/v1/find").withBodyContaining("ParentsOf"),
                    RestReply.json(page()))
            .route(RequestMatcher.post("/users/v1/find").withBodyContaining("ChildrenOf"),
                    RestReply.json(page(ALICE)))
            .post("/users/v1/find", RestReply.json(page(ALICE, ADMINS)))
            .post("/users/v1/updateUser", request -> RestReply.json(request.getBody()))
            // The user picker (UserRefPopupPresenter)
            .post("/userRef/v1/find", RestReply.json("""
                    {"values": [{"uuid": "u-alice", "subjectId": "alice", "displayName": "Alice Anderson",
                                 "group": false, "enabled": true}],
                     "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}}"""))
            .build();

    private UserGroupsScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/UserGroupsScreen", UserGroupsScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The Member Of pane is labelled 'No Selection' until a principal is selected
                .story("UserGroups", UserGroupsScreenStories::render)
                .withPlay(play -> {
                    play.findAllByText("Administrators");
                    // Differs from React: both membership panes are labelled 'No Selection' (the
                    // Members pane's label is hidden with it)
                    play.expect(play.getAllByText("No Selection").nth(0)).toBeInTheDocument();
                    play.expect(play.queryByText(TextMatch.startingWith("Members of "))).toBeNull();
                    // The dimmed Member Of pane is inert, so it can't be reached or used (it once
                    // only looked unavailable)
                    play.expect(play.querySelector("[inert]")).toHaveTextContent(TextMatch.containing("No Selection"));
                    // Select the group: both panes name it, and its members (Alice) load
                    play.click(play.getAllByText("Administrators").nth(0));
                    play.waitFor(() -> play.expect(play.querySelector("[inert]")).toBeNull());
                    // Differs from React: the label starts with 'Group' (UserRef.getType(SENTENCE))
                    play.findByText("Group \"Administrators\" is a member of:");
                    play.findByText("Members of group \"Administrators\":");
                    play.waitFor(() -> play.expect(play.getAllByText("Alice Anderson").count())
                            .toBeGreaterThanOrEqual(2));
                    SecurityPlays.expectNoProblems(play);
                })
                // Create / Edit / Delete, with titles from the selection; Edit renames a group
                .story("EditGroup", UserGroupsScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findAllByText("Administrators");
                    play.findByText("Alice A Anderson");
                    // The master grid: the user/group icon column ('/'), Display Name (sorted
                    // ascending) and Full Name.
                    // Differs from React: GWT's sortable headers have role="button", not
                    // "columnheader", so their names are read from the header cells' name holders
                    final Play master = masterGrid(play);
                    final Value<List<String>> names = master.querySelectorAll(".dataGridSortableHeaderNameHolder")
                            .textContents();
                    play.expect("the first three column headings", () -> trimmed(names.get()).subList(0, 3))
                            .toEqual(List.of("/", "Display Name", "Full Name"));
                    play.expect(master.getByText("Display Name").closest("th"))
                            .toHaveClass("dataGridSortedHeaderAscending");
                    // Create, Edit, Delete in the master grid's pager toolbar
                    final Query edit = play.within(play.getByTitle("Create Group").closest(".button-container"))
                            .querySelectorAll("button").nth(1);
                    play.expect(edit).toHaveAttribute("title", "No Selection");
                    play.expect(edit).toHaveClass("disabled");
                    // A user: disabled, with GWT's reason
                    play.click(play.getByText("Alice Anderson"));
                    play.waitFor(() -> play.expect(edit).toHaveAttribute("title", "User editing is not supported"));
                    play.expect(edit).toHaveClass("disabled");
                    // A group: enabled
                    play.click(play.getAllByText("Administrators").nth(0));
                    play.waitFor(() -> play.expect(edit).toHaveAttribute("title", "Edit group 'Administrators'"));
                    play.expect(edit).not().toHaveClass("disabled");
                    // Edit, rename, OK: the subject id and display name are both changed
                    play.click(edit);
                    final Play dialog = screen.within(screen.findByText("Edit User Group").closest(StroomDom.DIALOG));
                    final Query name = dialog.getByLabelText("Name");
                    play.clear(name);
                    play.type(name, "Admins");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/users/v1/updateUser")
                                    .withJsonBodyContaining("""
                                            {"uuid": "g-admins", "subjectId": "Admins", "displayName": "Admins"}""")
                                    .toSpyMatcher()));
                    SecurityPlays.expectNoProblems(play);
                })
                // Every list has an action menu; the membership panes keep the User Groups link
                .story("MembershipActionMenus", UserGroupsScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findAllByText("Administrators");
                    // Master row: no link to the User Groups screen (we're on it)
                    final Play masterRow = play.within(play.getByText("Alice Anderson").closest("tr"));
                    play.click(masterRow.getByTitle(StroomDom.ACTIONS_TITLE));
                    play.waitFor(() -> play.expect(screen.getByText(TextMatch.containingIgnoreCase(
                            "Show user on the Users screen"))).toBeVisible());
                    play.expect(screen.queryByText(TextMatch.containingIgnoreCase(
                            "Show user on the User Groups screen"))).toBeNull();
                    play.keyboard("{Escape}");
                    // Select the group so the membership panes are shown
                    play.click(play.getAllByText("Administrators").nth(0));
                    play.findByText("Members of group \"Administrators\":");
                    // The Members pane's rows have their own menu, which keeps the User Groups link
                    final Play members = play.within(play.getByText("Members of group \"Administrators\":")
                            .closest(".dock-container-vertical"));
                    final Play memberRow = play.within(members.findAllByText("Alice Anderson").nth(0).closest("tr"));
                    play.click(memberRow.getByTitle(StroomDom.ACTIONS_TITLE));
                    play.waitFor(() -> play.expect(screen.getByText(TextMatch.containingIgnoreCase(
                            "Show user on the User Groups screen"))).toBeVisible());
                    SecurityPlays.expectNoProblems(play);
                })
                // The action menu's 'Copy ... from' opens the user picker for the row's user
                .story("CopyPermissions", UserGroupsScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Alice Anderson");
                    final Play row = play.within(play.getByText("Alice Anderson").closest("tr"));
                    play.click(row.getByTitle(StroomDom.ACTIONS_TITLE));
                    // Differs from React: GWT's item ends with three dots, not an ellipsis
                    play.click(screen.findByText("Copy user groups and permissions from..."));
                    screen.findByText("Select User");
                    // Differs from React: the picker has no 'Copy groups and permissions to ... from:'
                    // line; it lists the users (not groups) to copy from
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/userRef/v1/find")
                                    .withBody("a find of users (isgroup false)", body -> body != null
                                            && body.contains("isgroup") && body.contains("false"))
                                    .toSpyMatcher()));
                    SecurityPlays.expectNoProblems(play);
                });
    }

    /// @return The master grid of all users and groups (with its label, quick filter, toolbar and
    /// pager).
    private static Play masterGrid(final Play play) {
        return play.within(play.getByText("User Groups:", "label").closest(".dock-container-vertical"));
    }

    private static List<String> trimmed(final List<String> texts) {
        final List<String> trimmed = new ArrayList<>();
        for (final String text : texts) {
            trimmed.add(text.trim());
        }
        return trimmed;
    }

    private static String page(final String... users) {
        return "{\"values\": [" + String.join(", ", users) + "], \"pageResponse\": {\"offset\": 0, \"length\": "
                + users.length + ", \"total\": " + users.length + ", \"exact\": true}}";
    }

    private static Widget render(final StoryContext context) {
        final SecurityScreenGinjector injector = GWT.create(SecurityScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .appPermissions(AppPermission.MANAGE_USERS_PERMISSION)
                .build();
        harness.fn(OPEN_SCREEN);
        harness.getEventBus().addHandler(OpenUsersScreenEvent.getType(), event ->
                harness.spy(OPEN_SCREEN, "Users: " + event.getUserRef().getDisplayName()));

        // Opened as UsersAndGroupsPlugin.open opens it (refreshed), once Stroom has started (it
        // reads the UI config)
        harness.afterStartUp(() -> {
            final UserAndGroupsPresenter presenter = harness.addContent(injector.getUserAndGroupsPresenter());
            presenter.refresh();
        });
        return harness.asWidget();
    }
}
