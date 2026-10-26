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

package stroom.gwt.workbench.client.app.idp;

import stroom.gwt.workbench.client.app.gin.idp.IdpScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.rest.StartupFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.client.CurrentUser;
import stroom.security.identity.client.presenter.CurrentPasswordPresenter;
import stroom.security.identity.client.view.CurrentPasswordViewImpl;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppUserPermissions;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.EnumSet;

/// The `App/IdP/CurrentPassword` stories, showing Stroom's real 'Enter Your Current Password'
/// dialog (`CurrentPasswordPresenter` with `CurrentPasswordViewImpl`), the first step of changing a
/// signed in user's password from the user menu, with fake REST replies. The password is checked
/// with `POST /authentication/v1/confirmPassword` (replying after 200ms); once it is confirmed,
/// Stroom goes on to the 'Change Password' dialog itself.
///
/// As in `App/Main/ChangePasswordDialog`, the presenter is created with `new`, with Stroom's
/// `CurrentUser` holding the `admin` user. The plays only check the empty dialog, so it can still
/// be tried by hand.
public final class CurrentPasswordStories {

    private static final String CAPTION = "Enter Your Current Password";
    private static final String CONFIRM_INVALID = """
            {"valid": false, "message": "The password you entered is incorrect."}""";

    private CurrentPasswordStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/IdP/CurrentPassword", CurrentPasswordStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // 'correct-horse' is accepted; anything else is rejected
                .story("Default", context -> render(context, true))
                .withPlay(CurrentPasswordStories::expectEmptyDialog)
                // Every password is rejected
                .story("Invalid", context -> render(context, false))
                .withPlay(CurrentPasswordStories::expectEmptyDialog)
                // GWT-only: OK with no password marks the field invalid and focuses it, without
                // asking the server
                .story("MissingPassword", context -> render(context, true))
                .withPlay(play -> {
                    expectEmptyDialog(play);
                    IdpPlays.expectOkFocusesInvalid(play, IdpPlays.passwordDialog(play, CAPTION),
                            "Current Password", "Password is required");
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalled();
                });
    }

    // The dialog, with its password field focused and empty
    private static void expectEmptyDialog(final Play play) {
        final Play screen = play.screen();
        final Play dialog = screen.within(screen.findByText(CAPTION, StroomDom.DIALOG_TITLE)
                .closest(StroomDom.DIALOG));
        play.expect(dialog.getByPlaceholderText(CAPTION)).toHaveValue("");
        play.waitFor(() -> play.expect(dialog.getByPlaceholderText(CAPTION)).toHaveFocus());
        play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalled();
        IdpPage.expectNoProblems(play);
    }

    private static Widget render(final StoryContext context, final boolean acceptCorrectHorse) {
        final RestFixtures.Builder fixtures = RestFixtures.builder();
        if (acceptCorrectHorse) {
            fixtures.route(RequestMatcher.post(IdpPlays.CONFIRM_PASSWORD_PATH)
                            .withJsonBodyContaining("{\"password\": \"correct-horse\"}"),
                    RestReply.json("{\"valid\": true}").delayed(200));
        }
        fixtures.post(IdpPlays.CONFIRM_PASSWORD_PATH, RestReply.json(CONFIRM_INVALID).delayed(200))
                // Step 2, the 'Change Password' dialog
                .get(IdpPlays.FETCH_POLICY_PATH, RestReply.json(IdpPlays.policy(false, 8, 3,
                        "Passwords must be at least 8 characters and be hard to guess.")))
                .post(IdpPlays.CHANGE_PASSWORD_PATH, RestReply.json("{\"changeSucceeded\": true}"))
                .post("/session/v1/terminateOther", RestReply.json("true"));
        final IdpScreenGinjector injector = GWT.create(IdpScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures.build())
                .injector(injector)
                .realAlerts()
                .build();

        // Stroom's CurrentUser, holding the signed in user, as the login sets it
        final CurrentUser currentUser = new CurrentUser(harness.getEventBus(), harness.getRestFactory(),
                null, null);
        currentUser.setUserAndPermissions(new AppUserPermissions(StartupFixtures.ADMIN,
                EnumSet.of(AppPermission.ADMINISTRATOR)), false);
        final CurrentPasswordPresenter presenter = new CurrentPasswordPresenter(harness.getEventBus(),
                new CurrentPasswordViewImpl(GWT.create(CurrentPasswordViewImpl.Binder.class)),
                harness.getRestFactory(),
                currentUser,
                injector::getChangePasswordPresenter);
        presenter.show();
        return harness.asWidget();
    }
}
