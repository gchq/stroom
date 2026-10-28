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

import stroom.gwt.workbench.client.a11y.ButtonRuns;
import stroom.gwt.workbench.client.a11y.TabStops;
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
import stroom.security.client.component.UsersAndGroupsTabController;
import stroom.security.client.event.OpenUsersScreenEvent;
import stroom.security.shared.AppPermission;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;

/// Stories of `App/Main/UserGroupsScreen`, showing Stroom's real [UsersAndGroupsTabController] (the
/// 'User Groups' tab) with fake REST replies.
///
/// | Stroom endpoint | Used for |
/// |---|---|
/// | `POST /users/v1/find`, routed by the term's field | users and groups (all; `ParentsOf`; `ChildrenOf`) |
/// | `POST /userRef/v1/find` | the user picker |
/// | `POST /users/v1/updateUser` | updating a user (checked on the request spy) |
/// | a spy on Stroom's `OpenUsersScreenEvent` | opening a user |
///
/// The user holds `MANAGE_USERS_PERMISSION`, which `UsersAndGroupsPlugin` requires to open the
/// screen. The controller comes from GIN and is opened as `UsersAndGroupsPlugin.open` opens it
/// (refreshed).
public final class UserGroupsScreenStories {

    /// The name of the spy recording the screens opened from the action menus.
    static final String OPEN_SCREEN = "openScreen";

    // Each list's quick filter, named for what it filters (UserList)
    private static final String FILTER = "Filter users and groups";

    private static final String ALICE = """
            {"id": 1, "uuid": "u-alice", "subjectId": "alice", "displayName": "Alice Anderson",
              "fullName": "Alice A Anderson", "group": false, "enabled": true}""";
    private static final String ADMINS = """
            {"id": 2, "uuid": "g-admins", "subjectId": "Administrators", "displayName": "Administrators",
              "group": true, "enabled": true}""";

    // Package-private so the shell stories can open the screen from the main menu
    static final RestFixtures FIXTURES = RestFixtures.builder()
            // Member Of: no groups; Members: Alice; otherwise everyone
            .route(RequestMatcher.post("/users/v1/find").withBodyContaining("ParentsOf"),
                    RestReply.json(page()))
            .route(RequestMatcher.post("/users/v1/find").withBodyContaining("ChildrenOf"),
                    RestReply.json(page(ALICE)))
            .post("/users/v1/find", RestReply.json(page(ALICE, ADMINS)))
            .post("/users/v1/updateUser", request -> RestReply.json(request.getBody()))
            .post("/users/v1/createGroup", RestReply.json("""
                    {"id": 3, "uuid": "g-auditors", "subjectId": "Auditors", "displayName": "Auditors",
                      "group": true, "enabled": true}"""))
            // The user picker (UserRefPopupController)
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
                    // Both membership panes are labelled 'No Selection'
                    play.expect(play.getAllByText("No Selection")).toHaveLength(2);
                    play.expect(play.queryByText(TextMatch.startingWith("Members of "))).toBeNull();
                    // Both panes are shown dimmed and inert, so they can't be reached or used (the
                    // Member Of pane once only looked unavailable, and the Members pane was hidden)
                    play.expect(play.querySelectorAll("[inert]")).toHaveLength(2);
                    play.expect(play.querySelector("[inert]")).toHaveTextContent(TextMatch.containing("No Selection"));
                    // Select the group: both panes name it, and its members (Alice) load
                    play.click(play.getAllByText("Administrators").nth(0));
                    play.waitFor(() -> play.expect(play.querySelector("[inert]")).toBeNull());
                    // The label starts with 'Group' (UserRef.getType(SENTENCE))
                    play.findByText("Group \"Administrators\" is a member of:");
                    play.findByText("Members of group \"Administrators\":");
                    play.waitFor(() -> play.expect(play.getAllByText("Alice Anderson").count())
                            .toBeGreaterThanOrEqual(2));
                    // The group is a member of none: its pager counts none ("0 to 0 of 0", once
                    // "1 to 1 of 0")
                    play.waitFor(() -> play.expect("the pagers' counts", UserGroupsScreenStories::pagerCounts)
                            .toContain("0 to 0 of 0"));
                    play.expect("the pagers' counts", UserGroupsScreenStories::pagerCounts)
                            .not().toContain("1 to 1 of 0");
                    SecurityPlays.expectNoProblems(play);
                })
                // The runs of icon buttons, and the tab stops each costs, once a group is selected
                // (each pane has a list toolbar and a pager)
                .story("ToolbarTabStops", UserGroupsScreenStories::render)
                .withPlay(play -> {
                    play.findAllByText("Administrators");
                    play.click(play.getAllByText("Administrators").nth(0));
                    play.waitFor(() -> play.expect(play.querySelector("[inert]")).toBeNull());
                    play.findByText("Members of group \"Administrators\":");
                    play.expect(ButtonRuns.of(play.body())).toEqual(List.of(
                            "Show Recent Filters, Quick Filter Syntax Help: 1 tab stop",
                            "Create Group, Edit Group 'Administrators', Delete Group 'Administrators': 1 tab stop",
                            "First, Backward, Forward, Last, Refresh: 1 tab stop",
                            "Show Recent Filters, Quick Filter Syntax Help: 1 tab stop",
                            "Show Recent Filters, Quick Filter Syntax Help: 1 tab stop",
                            "Add to Group, Remove from Selected Group: 1 tab stop",
                            "First, Backward, Forward, Last, Refresh: 1 tab stop",
                            "Add Member to Group, Remove Selected Member From Group: 1 tab stop",
                            "First, Backward, Forward, Last, Refresh: 1 tab stop"));
                })
                // #5405: Tab reads the panes as they are laid out, the master list, then the left
                // (Member Of) pane, then the right (Members) pane; the right pane once came first,
                // as its old split panel added the centre (left) pane last. The split panes'
                // splitters are tab stops too, each between the panes it divides
                .story("ReadingOrder", UserGroupsScreenStories::render)
                .withPlay(play -> {
                    play.findAllByText("Administrators");
                    play.click(play.getAllByText("Administrators").nth(0));
                    play.waitFor(() -> play.expect(play.querySelector("[inert]")).toBeNull());
                    play.findByText("Members of group \"Administrators\":");
                    // A screen reader reads the page in this order too
                    play.expect(play.body()).toHaveTextContent(TextMatch.regex(
                            "is a member of:.*Members of group"));
                    // The panes' tab stops: a quick filter, its buttons (one toolbar, named by its
                    // first button), its toolbar and its pager, then its grid, the master's, then
                    // Member Of's, then Members', with the splitter (named by the pane it resizes)
                    // between each. A grid is one tab stop: the row with the keyboard focus (named
                    // by its text; its row actions are reached with Right), or, with no rows, the
                    // grid itself, so its headers can still be reached (named by its table name, Users)
                    play.expect(TabStops.names(play.body())).toEqual(List.of(
                            FILTER, "Show Recent Filters", "Create Group", "First", "Administrators",
                            "Users and groups",
                            FILTER, "Show Recent Filters", "Add to Group", "First", "Users",
                            "Members",
                            FILTER, "Show Recent Filters", "Add Member to Group", "First",
                            "Alice Anderson Alice A Anderson"));
                })
                // Create / Edit / Delete, with titles from the selection; Edit renames a group
                .story("EditGroup", UserGroupsScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findAllByText("Administrators");
                    play.findByText("Alice A Anderson");
                    // The master grid: the user/group icon column ('/'), Display Name (sorted
                    // ascending) and Full Name.
                    // GWT's sortable headers have role="button", not "columnheader", so their names are
                    // read from the header cells' name holders
                    final Play master = masterGrid(play);
                    final Value<List<String>> names = master.querySelectorAll(".data-grid__sort-name")
                            .textContents();
                    play.expect("the first three column headings", () -> trimmed(names.get()).subList(0, 3))
                            .toEqual(List.of("/", "Display Name", "Full Name"));
                    play.expect(master.getByText("Display Name").closest(StroomDom.GRID_HEADER_CELL))
                            .toHaveAttribute("aria-sort", "ascending");
                    // Create, Edit, Delete in the master grid's pager toolbar
                    final Query edit = play.within(play.getByTitle("Create Group").closest(".toolbar"))
                            .querySelectorAll("button").nth(1);
                    play.expect(edit).toHaveAttribute("title", "Edit Selected Group");
                    play.expect(edit).toHaveAttribute("aria-disabled", "true");
                    // A user: disabled, with GWT's reason
                    play.click(play.getByText("Alice Anderson"));
                    play.waitFor(() -> play.expect(edit).toHaveAttribute("title",
                            "Edit Selected Group (users cannot be edited here)"));
                    play.expect(edit).toHaveAttribute("aria-disabled", "true");
                    // A group: enabled
                    play.click(play.getAllByText("Administrators").nth(0));
                    play.waitFor(() -> play.expect(edit).toHaveAttribute("title", "Edit Group 'Administrators'"));
                    play.expect(edit).not().toHaveAttribute("aria-disabled");
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
                // Create Group asks for the group's name; Enter creates it
                .story("CreateGroup", UserGroupsScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Alice A Anderson");
                    play.click(play.getByTitle("Create Group"));
                    final Play dialog = screen.within(screen.findByText("Add User Group", StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    final Query name = dialog.getByLabelText("Name");
                    play.waitFor(() -> play.expect(name).toHaveFocus());
                    play.type(name, "Auditors");
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/users/v1/createGroup").withBodyContaining("Auditors")
                                    .toSpyMatcher()));
                    play.waitFor(() -> play.expect(screen.queryByText("Add User Group", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    SecurityPlays.expectNoProblems(play);
                })
                // Every list has an action menu; the membership panes keep the User Groups link
                .story("MembershipActionMenus", UserGroupsScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findAllByText("Administrators");
                    // Master row: no link to the User Groups screen (we're on it)
                    final Play masterRow = play.within(play.getByText("Alice Anderson").closest(StroomDom.GRID_ROW));
                    play.click(masterRow.getByTitle(StroomDom.ACTIONS_TITLE));
                    play.waitFor(() -> play.expect(screen.getByText(TextMatch.containingIgnoreCase(
                            "Show in Users"))).toBeVisible());
                    play.expect(screen.queryByText(TextMatch.containingIgnoreCase(
                            "Show in User Groups"))).toBeNull();
                    play.keyboard("{Escape}");
                    // Select the group so the membership panes are shown
                    play.click(play.getAllByText("Administrators").nth(0));
                    play.findByText("Members of group \"Administrators\":");
                    // The Members pane's rows have their own menu, which keeps the User Groups link
                    final Play members = play.within(play.getByText("Members of group \"Administrators\":")
                            .closest(".dock-container-vertical"));
                    final Play memberRow = play.within(members.findAllByText("Alice Anderson").nth(0)
                            .closest(StroomDom.GRID_ROW));
                    play.click(memberRow.getByTitle(StroomDom.ACTIONS_TITLE));
                    play.waitFor(() -> play.expect(screen.getByText(TextMatch.containingIgnoreCase(
                            "Show in User Groups"))).toBeVisible());
                    SecurityPlays.expectNoProblems(play);
                })
                // The action menu's 'Copy ... from' opens the user picker for the row's user
                .story("CopyPermissions", UserGroupsScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Alice Anderson");
                    final Play row = play.within(play.getByText("Alice Anderson").closest(StroomDom.GRID_ROW));
                    play.click(row.getByTitle(StroomDom.ACTIONS_TITLE));
                    // The item ends with three dots, not an ellipsis
                    play.click(screen.findByText("Copy User Groups and Permissions from..."));
                    screen.findByText("Select User");
                    // The picker lists the users (not groups) to copy from
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

    // Each pager's count, e.g. "1 to 3 of 3"
    private static List<String> pagerCounts() {
        final List<String> counts = new ArrayList<>();
        final JsArrayString texts = pagerTexts();
        for (int i = 0; i < texts.length(); i++) {
            counts.add(texts.get(i));
        }
        return counts;
    }

    private static native JsArrayString pagerTexts() /*-{
        var texts = [];
        var pagers = $doc.querySelectorAll(".paging-bar");
        for (var i = 0; i < pagers.length; i++) {
            texts.push(pagers[i].textContent.replace(/\u00a0/g, " ").replace(/\s+/g, " ").trim());
        }
        return texts;
    }-*/;

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
            final UsersAndGroupsTabController controller = harness.show(injector.getUsersAndGroupsTabController());
            controller.refresh();
        });
        return harness.asWidget();
    }
}
