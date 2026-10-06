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
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.client.presenter.UserTabPresenter;
import stroom.security.shared.AppPermission;
import stroom.util.shared.UserRef;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;

/// Stories matching `App/Main/UserTabScreen` in the React Storybook, showing Stroom's real
/// [UserTabPresenter] (a user's tab, and the 'User Profile' tab) with fake REST replies.
///
/// | React seam | Stroom REST endpoint |
/// |---|---|
/// | `users.findUsers` (the User Groups tab's panes) | `POST /users/v1/find` (`ParentsOf`: Administrators) |
/// | `users.findDependencies` | `POST /users/v1/findDependencies` |
/// | `appPerms.getReport` | `POST /permission/app/v1/getAppUserPermissionsReport` |
/// | `appPerms.changeAppPermission` (its recorder) | `POST /permission/app/v1/changeAppPermission` |
/// | `docPerms.findUserDocumentPermissions` | `POST /explorer/v2/advancedFindWithPermissions` |
///
/// The signed in user is `admin` holding `MANAGE_USERS_PERMISSION` (not `MANAGE_API_KEYS`), as
/// React's fixture, so the tab set has Application Permissions and not API Keys. The presenter comes
/// from GIN; a user's tab is opened as `UserTabPlugin.open` opens it (`setUserRef(bob)`), the
/// 'User Profile' tab as `UserPlugin.open` does (`setUserRef` with the signed in user).
public final class UserTabScreenStories {

    private static final UserRef SESSION_USER = new UserRef("admin", "admin", "Admin", null, false, true);
    private static final UserRef BOB = new UserRef("u-bob", "bob", "Bob Barker", null, false, true);

    private static final String BOB_JSON = """
            {"uuid": "u-bob", "subjectId": "bob", "displayName": "Bob Barker", "group": false, "enabled": true}""";
    private static final String ADMINS = """
            {"id": 2, "uuid": "g-admins", "subjectId": "Administrators", "displayName": "Administrators",
              "group": true, "enabled": true}""";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .route(RequestMatcher.post("/users/v1/find").withBodyContaining("ChildrenOf"),
                    RestReply.json(page()))
            .post("/users/v1/find", RestReply.json(page(ADMINS)))
            .post("/users/v1/findDependencies", RestReply.json(page("""
                    {"userRef": BOB, "details": "created by this user",
                     "docRef": {"type": "Dashboard", "uuid": "d-1", "name": "Ops Dashboard"}}"""
                    .replace("BOB", BOB_JSON))))
            .post("/permission/app/v1/getAppUserPermissionsReport", RestReply.json("""
                    {"explicitPermissions": ["ANNOTATIONS"],
                     "inheritedPermissions": {"STEPPING_PERMISSION": ["Administrators"]}}"""))
            .post("/permission/app/v1/changeAppPermission", RestReply.json("true"))
            .post("/explorer/v2/advancedFindWithPermissions", RestReply.json(page("""
                    {"findResult": {"docRef": {"type": "Feed", "uuid": "f-1", "name": "TEST_FEED"},
                                    "path": "/System/Feeds"},
                     "permissions": {"userRef": BOB, "permission": "EDIT"}}"""
                    .replace("BOB", BOB_JSON))))
            .build();

    private UserTabScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/UserTabScreen", UserTabScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // User Profile is the same tab, for the signed in user
                .story("UserProfileTabHost", context -> render(context, null))
                .withPlay(play -> {
                    play.findByText("Info", StroomDom.LINK_TAB_LABEL);
                    play.waitFor(() -> play.expect(play.getByDisplayValue("admin")).toBeInTheDocument());
                    // The full tab set, gated: App Permissions in, API Keys out.
                    // Differs from React: the tabs have no role="tab"; they are found by their labels
                    for (final String tab : new String[]{"Info", "User Groups", "Application Permissions",
                            "Document Permissions", "Dependencies"}) {
                        play.expect(tab(play, tab)).toBeInTheDocument();
                    }
                    play.expect(play.queryByText("API Keys")).toBeNull();
                    SecurityPlays.expectNoProblems(play);
                })
                // The gated tab set; Info is the default and shows Bob
                .story("InfoDefault", context -> render(context, BOB))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(tab(play, "Application Permissions")).toBeInTheDocument());
                    play.expect(play.queryByText("API Keys")).toBeNull();
                    play.waitFor(() -> play.expect(play.getByDisplayValue("bob")).toBeInTheDocument());
                    SecurityPlays.expectNoProblems(play);
                })
                // The Dependencies tab lists the user's dependencies
                .story("DependenciesTab", context -> render(context, BOB))
                .withPlay(play -> {
                    play.click(play.findByText("Dependencies", StroomDom.LINK_TAB_LABEL));
                    play.waitFor(() -> play.expect(play.getByText("created by this user")).toBeInTheDocument());
                    // Differs from React (GWT bug): UserDependenciesListPresenter builds its
                    // DocRefCell without a docRefFunction, so the Document Name cell is always blank
                    // ('Ops Dashboard' isn't shown)
                    play.expect(play.queryByText("Ops Dashboard")).toBeNull();
                    play.expect(play.within(play.getByText("created by this user").closest("tr"))
                            .querySelector("td").textContent()).toBe("");
                    SecurityPlays.expectNoProblems(play);
                })
                // The Document Permissions tab lists the documents and their permissions
                .story("DocumentPermissionsTab", context -> render(context, BOB))
                .withPlay(play -> {
                    play.click(play.findByText("Document Permissions", StroomDom.LINK_TAB_LABEL));
                    play.waitFor(() -> play.expect(play.getByText("TEST_FEED")).toBeInTheDocument());
                    // The permission's display value, with Permission and Inherited Permission apart
                    play.expect(play.getByText("Edit")).toBeInTheDocument();
                    final Play grid = play.within(play.getByText("Edit").closest(".dataGridWidget"));
                    play.expect(grid.getAllByRole("columnheader").textContents())
                            .toEqual(List.of("Document", "Permission", "Inherited Permission", "Path"));
                    SecurityPlays.expectNoProblems(play);
                })
                // The User Groups tab is the User Groups screen without its top pane
                .story("GroupsTab", context -> render(context, BOB))
                .withPlay(play -> {
                    play.click(play.findByText("User Groups", StroomDom.LINK_TAB_LABEL));
                    play.waitFor(() -> play.expect(play.getAllByText("Administrators").count()).toBeGreaterThan(0));
                    // Differs from React: GWT's sortable headers have role="button", not
                    // "columnheader", so the headings are read from the header cells' name holders
                    final Value<List<String>> headings = play.querySelectorAll(".dataGridSortableHeaderNameHolder")
                            .textContents();
                    play.expect("the headings", () -> trimmed(headings.get())).toContain("Display Name");
                    play.expect("the headings", () -> trimmed(headings.get())).not().toContain("Group");
                    play.expect("the headings", () -> trimmed(headings.get())).not().toContain("Unique ID");
                    // The user/group icon column and the action menu
                    final Query rowElement = play.getAllByText("Administrators").nth(0).closest("tr");
                    final Play row = play.within(rowElement);
                    play.expect(row.getByTitle(StroomDom.ACTIONS_TITLE)).toBeInTheDocument();
                    play.expect(row.querySelector(".svgCell-icon")).toBeInTheDocument();
                    // Remove is a pager toolbar button, not a per-row one
                    play.expect(row.queryByTitle("Remove")).toBeNull();
                    play.expect(play.getAllByTitle("Remove").count()).toBeGreaterThan(0);
                    SecurityPlays.expectNoProblems(play);
                })
                // The Application Permissions tab changes a permission
                .story("AppPermissionsTab", context -> render(context, BOB))
                .withPlay(play -> {
                    play.click(play.findByText("Application Permissions", StroomDom.LINK_TAB_LABEL));
                    play.waitFor(() -> play.expect(play.getByText("Annotations")).toBeInTheDocument());
                    // The same edit grid (with Description) and details pane as the Security screen
                    play.expect(play.getAllByRole("columnheader").textContents())
                            .toEqual(List.of("Granted", "Permission", "Description"));
                    play.expect(play.getByText("No application permission selected")).toBeInTheDocument();
                    // The explicit ANNOTATIONS is ticked; unticking it removes it
                    play.click(play.within(play.getByText("Annotations").closest("tr")).querySelector(".tickBox"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/permission/app/v1/changeAppPermission")
                                    .withJsonBodyContaining("""
                                            {"type": "RemoveAppPermission", "permission": "ANNOTATIONS"}""")
                                    .toSpyMatcher()));
                    SecurityPlays.expectNoProblems(play);
                });
    }

    /// Differs from React: the tabs (`LinkTabPanelView`) have no role="tab".
    ///
    /// @return The tab with the label.
    private static Query tab(final Play play, final String label) {
        return play.getByText(label, StroomDom.LINK_TAB_LABEL);
    }

    private static List<String> trimmed(final List<String> texts) {
        final List<String> trimmed = new ArrayList<>();
        for (final String text : texts) {
            trimmed.add(text.trim());
        }
        return trimmed;
    }

    private static String page(final String... rows) {
        return "{\"values\": [" + String.join(", ", rows) + "], \"pageResponse\": {\"offset\": 0, \"length\": "
                + rows.length + ", \"total\": " + rows.length + ", \"exact\": true}}";
    }

    /// @param user The user whose tab to open, or null for the 'User Profile' tab.
    private static Widget render(final StoryContext context, final UserRef user) {
        final SecurityScreenGinjector injector = GWT.create(SecurityScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .user(SESSION_USER)
                .appPermissions(AppPermission.MANAGE_USERS_PERMISSION)
                .build();
        // Opened once Stroom has started (the tabs' lists read the UI config), as UserTabPlugin
        // opens a user's tab, or UserPlugin.open the signed in user's profile
        harness.afterStartUp(() -> {
            final UserTabPresenter presenter = harness.addContent(injector.getUserTabPresenter());
            presenter.setUserRef(user != null
                    ? user
                    : harness.getSecurityContext().getUserRef());
        });
        return harness.asWidget();
    }
}
