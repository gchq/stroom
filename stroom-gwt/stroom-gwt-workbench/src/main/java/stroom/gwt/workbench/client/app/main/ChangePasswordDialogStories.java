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
import stroom.gwt.workbench.client.app.rest.StartupFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
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

/// Stories matching `App/Main/ChangePasswordDialog` in the React Storybook, showing Stroom's real
/// [CurrentPasswordPresenter] ('Enter Your Current Password', then the 'Change Password' dialog
/// and the offer to sign out of the other sessions) with fake REST replies.
///
/// | React seam | Stroom REST endpoint |
/// |---|---|
/// | `confirmPassword` | `POST /authentication/v1/confirmPassword` |
/// | `fetchPasswordPolicy` | `GET /authentication/v1/fetchPasswordPolicy` |
/// | `changePassword` | `POST /authentication/v1/changePassword` |
/// | `terminateOtherSessions` | `POST /session/v1/terminateOther` (its recorder: the request spy) |
///
/// It is shown as the user menu's 'Change Password' item (`ChangePasswordPlugin`) shows it. The
/// presenter is created with `new`, with Stroom's `CurrentUser` holding the `admin` user: it needs
/// that class rather than the harness's `ClientSecurityContext`, and GIN can't create its graph
/// (the splash screen and the current activity) here. The dialogs are Stroom's popups, so React's
/// `OfferHarness` (which closes the dialog for real) needs no GWT equivalent.
public final class ChangePasswordDialogStories {

    private static final String POLICY = """
            {"allowPasswordResets": true, "minimumPasswordLength": 4, "minimumPasswordStrength": 0,
              "passwordPolicyMessage": "Your password must be at least 4 characters."}""";

    private static final RestFixtures VALID = fixtures("{\"valid\": true}");
    private static final RestFixtures INVALID = fixtures("""
            {"valid": false, "message": "The password you entered is incorrect."}""");

    private ChangePasswordDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/ChangePasswordDialog", ChangePasswordDialogStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The full two-step flow ends in the offer to sign out elsewhere
                .story("FullFlow", context -> render(context, VALID))
                .withPlay(play -> {
                    final Play screen = changePassword(play);
                    play.waitFor(() -> play.expect(screen.getByText(TextMatch.regex(
                                    "Your password has been changed\\. Do you also want to sign out")))
                            .toBeInTheDocument());
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/authentication/v1/changePassword")
                                    .withJsonBodyContaining("""
                                            {"userId": "admin", "currentPassword": "oldpass",
                                              "newPassword": "newpass123", "confirmNewPassword": "newpass123"}""")
                                    .toSpyMatcher());
                    expectNoProblems(play);
                })
                // A wrong current password shows the server's reason and stays on step 1
                .story("WrongCurrentPassword", context -> render(context, INVALID))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.type(currentPassword(screen), "wrong");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.getByText("The password you entered is incorrect."))
                            .toBeInTheDocument());
                    // Still on step 1: the new password dialog never opened
                    play.expect(screen.queryByText("Change Password")).toBeNull();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.get("/authentication/v1/fetchPasswordPolicy").toSpyMatcher());
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // Accepting the offer ends the other sessions
                .story("TerminateOtherSessionsAccepted", context -> render(context, VALID))
                .withPlay(play -> {
                    final Play screen = changePassword(play);
                    screen.findByText(TextMatch.containing("Your password has been changed"));
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/session/v1/terminateOther").toSpyMatcher()));
                    play.expect(screen.findByText("Signed out of your other sessions.")).toBeInTheDocument();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // Declining changes nothing
                .story("TerminateOtherSessionsDeclined", context -> render(context, VALID))
                .withPlay(play -> {
                    final Play screen = changePassword(play);
                    screen.findByText(TextMatch.containing("Your password has been changed"));
                    play.click(screen.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText(
                            TextMatch.containing("Your password has been changed"))).toBeNull());
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post("/session/v1/terminateOther").toSpyMatcher());
                    expectNoProblems(play);
                });
    }

    /// Drives both steps through to the offer.
    ///
    /// @return The page's body.
    private static Play changePassword(final Play play) {
        final Play screen = play.screen();
        // Step 1: the current password
        play.type(currentPassword(screen), "oldpass");
        play.click(screen.getByRole("button", StroomDom.button("OK")));
        // Step 2: the new password, once the policy has loaded
        final Play dialog = screen.within(screen.findByText("Change Password").closest(StroomDom.DIALOG));
        dialog.findByText(TextMatch.containingIgnoreCase("at least 4 characters"));
        play.type(dialog.getByLabelText("Password"), "newpass123");
        play.type(dialog.getByLabelText("Confirm Password"), "newpass123");
        play.click(dialog.getByRole("button", StroomDom.button("OK")));
        return screen;
    }

    /// @return The 'Enter Your Current Password' dialog's password field, once it is shown.
    private static Query currentPassword(final Play screen) {
        return screen.within(screen.findByText("Enter Your Current Password").closest(StroomDom.DIALOG))
                .getByLabelText("Current Password");
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    private static RestFixtures fixtures(final String confirmReply) {
        return RestFixtures.builder()
                .post("/authentication/v1/confirmPassword", RestReply.json(confirmReply))
                .get("/authentication/v1/fetchPasswordPolicy", RestReply.json(POLICY))
                .post("/authentication/v1/changePassword", RestReply.json("{\"changeSucceeded\": true}"))
                .post("/session/v1/terminateOther", RestReply.json("true"))
                .build();
    }

    private static Widget render(final StoryContext context, final RestFixtures fixtures) {
        final SecurityScreenGinjector injector = GWT.create(SecurityScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .realAlerts()
                .build();

        // Stroom's CurrentUser, holding the signed in user, as the login sets it (without the
        // splash screen it would show)
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
