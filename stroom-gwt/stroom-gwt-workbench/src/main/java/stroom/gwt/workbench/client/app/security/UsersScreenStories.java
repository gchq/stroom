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

package stroom.gwt.workbench.client.app.security;

import stroom.gwt.workbench.client.app.gin.AppScreenGinjector;
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
import stroom.security.client.component.UsersController;
import stroom.security.client.event.OpenAppPermissionsScreenEvent;
import stroom.security.shared.AppPermission;
import stroom.util.shared.UserRef;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/UsersScreen`, showing Stroom's real [UsersController] (the 'Users' tab) with
/// fake REST replies.
///
/// The fixtures answer Stroom's `UserResource` (`POST /users/v1/find`, `POST /users/v1/createUser`,
/// `POST /users/v1/createUsers`), and the requests are checked on the request spy. The user holds
/// `MANAGE_USERS_PERMISSION` (and not `ADMINISTRATOR`). The `openScreen` spy records Stroom's
/// `OpenAppPermissionsScreenEvent`. The controller comes from GIN.
public final class UsersScreenStories {

    /// The name of the spy recording the screens opened from the action menu.
    static final String OPEN_SCREEN = "openScreen";

    private static final String ALICE = """
            {"id": 1, "uuid": "u-alice", "subjectId": "alice", "displayName": "Alice Anderson",
              "fullName": "Alice J. Anderson", "group": false, "enabled": true}""";

    // UserResource.find()
    private static final String USERS = """
            {"values": [ALICE], "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}}"""
            .replace("ALICE", ALICE);

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .post("/users/v1/find", RestReply.json(USERS))
            .post("/users/v1/createUser", RestReply.json("""
                    {"id": 2, "uuid": "u-new", "subjectId": "bob", "group": false, "enabled": true}"""))
            .post("/users/v1/createUsers", RestReply.json("""
                    [{"id": 100, "uuid": "u-carol", "subjectId": "carol", "group": false, "enabled": true},
                     {"id": 101, "uuid": "u-dave", "subjectId": "dave", "group": false, "enabled": true}]"""))
            .build();

    // carol was deleted (disabled), so creating her asks whether to restore her, and restoring fails
    private static final RestFixtures RESTORE_FAILS_FIXTURES = RestFixtures.builder()
            .post("/users/v1/find", RestReply.json(USERS))
            .post("/users/v1/createUsers", RestReply.json("""
                    [{"id": 100, "uuid": "u-carol", "subjectId": "carol", "group": false, "enabled": false}]"""))
            .post("/users/v1/updateUser", RestReply.error(500, "Unable to restore the user"))
            .build();

    private static final UserRef ALICE_REF = UserRef.builder()
            .uuid("u-alice")
            .subjectId("alice")
            .displayName("Alice Anderson")
            .fullName("Alice J. Anderson")
            .build();

    private UsersScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/UsersScreen", UsersScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The users list loads; Create User adds a user via a UserDesc
                .story("Users", context -> render(context, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Alice Anderson");
                    play.expect(play.getByText("alice")).toBeInTheDocument();
                    // Create User: the dialog, the unique user id, OK.
                    // Stroom's dialogs have no role="dialog"; the dialog is the caption's closest
                    // '.dialog__container'
                    play.click(play.getByTitle("Create User"));
                    final Play dialog = screen.within(
                            screen.findByText("Add External User(s)").closest(StroomDom.DIALOG));
                    play.type(dialog.getByLabelText("Unique User Identity"), "bob");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/users/v1/createUser")
                                    .withJsonBodyContaining("{\"subjectId\": \"bob\"}")
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                })
                // OK with no unique identity says why under the field and keeps the dialog open, rather
                // than asking the server to create a user with no identity (#5409)
                .story("NoUserIdentity", context -> render(context, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Alice Anderson");
                    play.click(play.getByTitle("Create User"));
                    final Play dialog = screen.within(
                            screen.findByText("Add External User(s)").closest(StroomDom.DIALOG));
                    final Query identity = dialog.getByLabelText("Unique User Identity");
                    play.expect(identity).toHaveAttribute("aria-required", "true");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(identity).toHaveAttribute("aria-invalid", "true"));
                    play.expect(identity).toHaveAccessibleDescription(
                            TextMatch.startingWith("You must provide the user's unique identity"));
                    play.expect(identity).toHaveFocus();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post("/users/v1/createUser").toSpyMatcher());
                    // Still open, and a name can now be given
                    play.type(identity, "bob");
                    play.waitFor(() -> play.expect(identity).not().toHaveAttribute("aria-invalid"));
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/users/v1/createUser")
                                    .withJsonBodyContaining("{\"subjectId\": \"bob\"}")
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                })
                // OK with no users to add says why, rather than closing the dialog as Cancel does (#5409)
                .story("NoUsersToAdd", context -> render(context, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Alice Anderson");
                    play.click(play.getByTitle("Create User"));
                    final Play dialog = screen.within(
                            screen.findByText("Add External User(s)").closest(StroomDom.DIALOG));
                    play.click(dialog.querySelector(StroomDom.SELECTION_BOX));
                    play.click(screen.findByText("Add Multiple Identity Provider Users"));
                    final Query users = dialog.getByLabelText("Users to add");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(users).toHaveAttribute("aria-invalid", "true"));
                    play.expect(users).toHaveAccessibleDescription(
                            TextMatch.startingWith("You must provide at least one user"));
                    play.expect(users).toHaveFocus();
                    play.expect(screen.getByText("Add External User(s)")).toBeInTheDocument();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post("/users/v1/createUsers").toSpyMatcher());
                    expectNoProblems(play);
                })
                // Bulk create: switch the type to 'Multiple', paste a user list: createUsers with the CSV
                .story("BulkCreate", context -> render(context, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Alice Anderson");
                    play.click(play.getByTitle("Create User"));
                    final Play dialog = screen.within(
                            screen.findByText("Add External User(s)").closest(StroomDom.DIALOG));
                    // Switch the type SelectionBox to 'Add Multiple Identity Provider Users'.
                    // GWT's SelectionBox opens when its text box is clicked
                    play.click(dialog.querySelector(StroomDom.SELECTION_BOX));
                    play.click(screen.findByText("Add Multiple Identity Provider Users"));
                    play.type(dialog.getByLabelText("Users to add"), "carol{Enter}dave");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/users/v1/createUsers")
                                    .withBody("the CSV holds carol and dave", body -> body != null
                                            && body.contains("carol") && body.contains("dave"))
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                })
                // Restoring a deleted user that fails says why and leaves the dialog usable: the
                // restore had no failure handler, so OK and Cancel stayed disabled
                .story("FailedRestoreKeepsDialogUsable", context -> render(context, RESTORE_FAILS_FIXTURES, null,
                        true))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Alice Anderson");
                    play.click(play.getByTitle("Create User"));
                    final Play dialog = screen.within(
                            screen.findByText("Add External User(s)").closest(StroomDom.DIALOG));
                    play.click(dialog.querySelector(StroomDom.SELECTION_BOX));
                    play.click(screen.findByText("Add Multiple Identity Provider Users"));
                    play.type(dialog.getByLabelText("Users to add"), "carol");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.click(screen.within(screen.findByText(TextMatch.containing("would you like to restore them"))
                            .closest(StroomDom.DIALOG)).getByRole("button", StroomDom.button("OK")));
                    play.click(screen.within(screen.findByText(TextMatch.containing("Unable to restore the user"))
                            .closest(StroomDom.DIALOG)).getByRole("button", StroomDom.button("Close")));
                    play.click(dialog.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("Add External User(s)")).toBeNull());
                })
                // The row's action menu opens the App Permissions screen focused on the user
                .story("ActionMenu", context -> render(context, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Alice Anderson");
                    final Play row = play.within(play.getByText("Alice Anderson").closest(StroomDom.GRID_ROW));
                    // The cell's open icon: GWT shows it to a MANAGE_USERS holder (or on your own row)
                    play.expect(row.getByTitle(TextMatch.containing("Open user Alice Anderson")))
                            .toBeInTheDocument();
                    // GWT's shared ActionMenuCell: an ellipsis titled 'Actions...'
                    final Query actions = row.getByTitle(StroomDom.ACTIONS_TITLE);
                    play.expect(row.queryByTitle("Actions")).toBeNull();
                    play.click(actions);
                    // The menu is named for the user, rather than having a heading item that does
                    // nothing
                    screen.findByRole("menu", "Actions for user 'Alice Anderson'");
                    play.expect(screen.queryByText(TextMatch.containing("Actions for user"))).toBeNull();
                    // The menu omits a link back to Users; App Permissions opens it focused on Alice
                    play.click(screen.getByText("Show in Application Permissions"));
                    play.waitFor(() -> play.expect(play.spy(OPEN_SCREEN))
                            .toHaveBeenCalledWith("ApplicationPermissions: Alice Anderson"));
                    expectNoProblems(play);
                })
                // A cross-screen open seeds the quick filter with the focused user
                .story("FocusedOpen", context -> render(context, ALICE_REF))
                .withPlay(play -> {
                    play.findByText("Alice Anderson");
                    // OpenUsersScreenEvent carries the user, and UserListController.showUser filters by
                    // its display name with the 'display' field ('display:"Alice Anderson"'). The quick
                    // filter has no label, only a placeholder
                    play.expect(play.getByPlaceholderText(StroomDom.QUICK_FILTER_PLACEHOLDER))
                            .toHaveValue("display:\"Alice Anderson\"");
                    // The value is quoted, so the server finds the users by the whole display name (it
                    // was once split at the space: display name '*Alice*' and '*Anderson*' in any
                    // default field). The filter goes to the server as it is, for it to parse
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/users/v1/find")
                                    .withJsonBodyContaining("{\"quickFilter\": \"display:\\\"Alice Anderson\\\"\"}")
                                    .toSpyMatcher());
                    expectNoProblems(play);
                });
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    private static Widget render(final StoryContext context, final UserRef focus) {
        return render(context, FIXTURES, focus, false);
    }

    // As above, with the given fixtures, and with Stroom's real alerts and confirmations if asked
    private static Widget render(final StoryContext context,
                                 final RestFixtures fixtures,
                                 final UserRef focus,
                                 final boolean realAlerts) {
        final AppScreenGinjector injector = GWT.create(AppScreenGinjector.class);
        final ScreenHarness.Builder builder = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .appPermissions(AppPermission.MANAGE_USERS_PERMISSION);
        if (realAlerts) {
            builder.realAlerts();
        }
        final ScreenHarness harness = builder.build();
        harness.fn(OPEN_SCREEN);
        harness.getEventBus().addHandler(OpenAppPermissionsScreenEvent.getType(), event ->
                harness.spy(OPEN_SCREEN, "ApplicationPermissions: " + event.getUserRef().getDisplayName()));

        if (focus == null) {
            // Opened at once, before the UI config is cached: the list reads it when it refreshes,
            // and UiConfigCache fetches it then (it once also called the list back with null at
            // once, which failed), as UsersPlugin.open opens it, refreshed
            harness.show(injector.getUsersController()).refresh();
        } else {
            // Opened at once, as for an OpenUsersScreenEvent: refreshed, then showing the user.
            // Both refreshes wait for the UI config; only the first sets up the list (the second
            // once set it up again, duplicating its columns and rows, which the play's
            // findByText would now find twice)
            final UsersController controller = harness.show(injector.getUsersController());
            controller.refresh();
            controller.showUser(focus);
        }
        return harness.asWidget();
    }
}
