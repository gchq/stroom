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

package stroom.gwt.workbench.client.app.core;

import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.client.app.gin.security.SecurityScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.client.CurrentUser;
import stroom.security.client.presenter.UserTabPresenter;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppUserPermissions;
import stroom.util.shared.UserRef;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;

/// The `App/Core/appPermissions` stories, which check two rules as Stroom's GWT code applies them:
///
/// * Holding a permission: Stroom's `CurrentUser.hasAppPermission` (`ADMINISTRATOR` implies every
///   permission), checked against `CurrentUser`s holding each case's permissions;
/// * The user tab's label: `UserTabPresenter.setUserRef`'s tab label, read from real presenters (from
///   GIN) given each case's user. The story shows each case and its label, and the play checks
///   them.
public final class AppPermissionsStories {

    private static final AppPermission SHARDS = AppPermission.MANAGE_INDEX_SHARDS_PERMISSION;

    private AppPermissionsStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Core/appPermissions", AppPermissionsStories.class)
                // ADMINISTRATOR implies every other permission
                .story("AdministratorImpliesEveryPermission",
                        context -> new Label("See the play function."))
                .withPlay(play -> {
                    // An admin holding only ADMINISTRATOR passes every specific check
                    expectHolds(play, "ADMINISTRATOR / SHARDS", () -> user(AppPermission.ADMINISTRATOR), SHARDS, true);
                    expectHolds(play, "ADMINISTRATOR / MANAGE_JOBS", () -> user(AppPermission.ADMINISTRATOR),
                            AppPermission.MANAGE_JOBS_PERMISSION, true);
                    expectHolds(play, "ADMINISTRATOR / ADMINISTRATOR", () -> user(AppPermission.ADMINISTRATOR),
                            AppPermission.ADMINISTRATOR, true);
                    // An explicit permission passes without ADMINISTRATOR
                    expectHolds(play, "SHARDS / SHARDS", () -> user(SHARDS), SHARDS, true);
                    // A non-admin without the permission is refused
                    expectHolds(play, "MANAGE_JOBS / SHARDS", () -> user(AppPermission.MANAGE_JOBS_PERMISSION),
                            SHARDS, false);
                    expectHolds(play, "none / SHARDS", () -> user(), SHARDS, false);
                    // No permissions loaded (signed out, or not yet signed in) refuses rather than
                    // throwing.
                    // The CurrentUser before sign in, and after it is cleared, stand for a missing
                    // permission list
                    expectHolds(play, "not signed in / SHARDS", () -> new CurrentUser(null, null, null, null),
                            SHARDS, false);
                    expectHolds(play, "cleared / SHARDS", AppPermissionsStories::clearedUser, SHARDS, false);
                })
                // The User Profile tab's caption is derived from the user
                .story("UserProfileTabCaption", AppPermissionsStories::renderCaptions)
                .withPlay(play -> {
                    play.findByTestId("caption-displayName");
                    expectCaption(play, "displayName", "User: admin");
                    expectCaption(play, "subjectId", "User: admin");
                    expectCaption(play, "group", "Group: Admins");
                    // displayName wins over subjectId (toDisplayString)
                    expectCaption(play, "both", "User: Alice");
                    // The NullSafe fallback.
                    // A null user ref
                    expectCaption(play, "null", "Unknown User/Group");
                    // A user with nothing to identify it (it was once 'User: {null}', from
                    // toDisplayString's UUID fallback)
                    expectCaption(play, "empty", "Unknown User/Group");
                    // setUserRef(null) once set the caption and then threw, as the Info tab's view
                    // refused a null user
                    play.expect(play.queryByText("(setUserRef threw)")).toBeNull();
                });
    }

    /// Checks a user's permission when the step runs (creating Stroom's `CurrentUser` then, so
    /// each run of the play checks a fresh one).
    private static void expectHolds(final Play play,
                                    final String description,
                                    final Supplier<CurrentUser> user,
                                    final AppPermission permission,
                                    final boolean expected) {
        play.expect(description, () -> user.get().hasAppPermission(permission)).toBe(expected);
    }

    private static void expectCaption(final Play play, final String id, final String expected) {
        play.expect(play.getByTestId("caption-" + id)).toHaveTextContent(expected);
    }

    /// @return Stroom's `CurrentUser`, signed in (without the splash screen) with the permissions.
    private static CurrentUser user(final AppPermission... permissions) {
        final Set<AppPermission> set = EnumSet.noneOf(AppPermission.class);
        for (final AppPermission permission : permissions) {
            set.add(permission);
        }
        final CurrentUser user = new CurrentUser(null, null, null, null);
        user.setUserAndPermissions(new AppUserPermissions(
                new UserRef("u-1", "user", "User", null, false, true), set), false);
        return user;
    }

    /// @return Stroom's `CurrentUser` once signed out (its permissions cleared).
    private static CurrentUser clearedUser() {
        final CurrentUser user = user(AppPermission.ADMINISTRATOR);
        user.setUserAndPermissions(null, false);
        return user;
    }

    private static Widget renderCaptions(final StoryContext context) {
        final SecurityScreenGinjector injector = GWT.create(SecurityScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, RestFixtures.builder().build())
                .injector(injector)
                .build();
        final FlowPanel panel = new FlowPanel();
        addCaption(injector, panel, "displayName", new UserRef(null, null, "admin", null, false, true));
        addCaption(injector, panel, "subjectId", new UserRef(null, "admin", null, null, false, true));
        addCaption(injector, panel, "group", new UserRef(null, null, "Admins", null, true, true));
        addCaption(injector, panel, "both", new UserRef(null, "a-123", "Alice", null, false, true));
        addCaption(injector, panel, "null", null);
        addCaption(injector, panel, "empty", new UserRef(null, null, null, null, null, null));
        harness.add(panel);
        return harness.asWidget();
    }

    /// Shows the caption `UserTabPresenter` gives a user's tab.
    private static void addCaption(final SecurityScreenGinjector injector,
                                   final FlowPanel panel,
                                   final String id,
                                   final UserRef userRef) {
        final UserTabPresenter presenter = injector.getUserTabPresenter();
        String note = "";
        try {
            presenter.setUserRef(userRef);
        } catch (final RuntimeException e) {
            note = "(setUserRef threw)";
        }
        final Label label = new Label(presenter.getLabel());
        label.getElement().setAttribute("data-testid", "caption-" + id);
        panel.add(StoryPanels.row(8, new Label(id + ":"), label, new Label(note)));
    }
}
