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

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.gwt.workbench.client.app.gin.idp.IdpScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.identity.client.presenter.ChangePasswordPresenter;
import stroom.security.identity.shared.AuthenticationResource;
import stroom.security.identity.shared.ChangePasswordRequest;
import stroom.task.client.DefaultTaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/IdP/ChangePassword` in the React Storybook, showing Stroom's real
/// 'Change Password' dialog (`ChangePasswordPresenter` with `ChangePasswordViewImpl`) as the sign in
/// page shows it when the server requires a new password (`LoginPresenter.changePassword`), for
/// the user `alice` who signed in with `correct-horse`.
///
/// | React seam | Stroom |
/// |---|---|
/// | `fetchPasswordPolicy` | `GET /authentication/v1/fetchPasswordPolicy` (after 100ms) |
/// | `changePassword` | `POST /authentication/v1/changePassword` (after 250ms) |
/// | `onChanged` | what `LoginPresenter` does on `changeSucceeded` (`afterLogin()`, i.e. navigate) |
/// | `onCancel` | the dialog's Cancel (`LoginPresenter` hides the dialog) |
///
/// The dialog's OK handler is `LoginPresenter.changePassword`'s (a private method), copied here
/// with its `afterLogin()` (a navigation) replaced by React's `onChanged` echo.
public final class ChangePasswordStories {

    /// The spy for React's `onChanged`, given `changed ✓` or `changed ✓ — forceSignIn: …`.
    static final String ON_CHANGED = "onChanged";
    /// The spy for React's `onCancel`.
    static final String ON_CANCEL = "onCancel";

    private static final String CAPTION = "Change Password";
    private static final String STRONG = "Tr0ub4dour&3xtra";

    // Min length 8 and a minimum zxcvbn strength of 3 ("Strong")
    private static final String POLICY = IdpPlays.policy(false, 8, 3,
            "Passwords must be at least 8 characters, contain a letter and a digit, and be hard to guess.");
    private static final String STRICT_POLICY = IdpPlays.policy(false, 12, 4,
            "Passwords must be at least 12 characters, contain a letter and a digit, and be very hard to guess.");

    private static final RestReply SUCCEEDS = RestReply.json("{\"changeSucceeded\": true}");
    private static final RestReply FORCES_SIGN_IN = RestReply.json(
            "{\"changeSucceeded\": true, \"forceSignIn\": true}");
    private static final RestReply SERVER_ERROR = RestReply.json("""
            {"changeSucceeded": false, "message": "New password must differ from the previous one."}""");

    private ChangePasswordStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/IdP/ChangePassword", ChangePasswordStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // A strong password: the change succeeds
                .story("Default", context -> render(context, POLICY, SUCCEEDS))
                .withPlay(play -> {
                    final Play dialog = IdpPlays.fillPasswords(play, CAPTION, STRONG, STRONG);
                    IdpPlays.waitForStrengthScored(play, dialog, "1-5");
                    play.click(dialog.getByRole("button", IdpPlays.OK));
                    play.expect(play.findByText(TextMatch.containing("changed ✓"))).toBeInTheDocument();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(IdpPlays.CHANGE_PASSWORD_PATH).withJsonBodyContaining("""
                                    {"userId": "alice", "currentPassword": "correct-horse",
                                      "newPassword": "Tr0ub4dour&3xtra", "confirmNewPassword": "Tr0ub4dour&3xtra"}""")
                                    .toSpyMatcher());
                    IdpPage.expectNoProblems(play);
                })
                // Long enough but scores below the minimum strength
                .story("WeakPassword", context -> render(context, POLICY, SUCCEEDS))
                .withPlay(play -> {
                    final Play dialog = IdpPlays.fillPasswords(play, CAPTION, "password1", "password1");
                    IdpPlays.waitForStrengthScored(play, dialog, "1-5");
                    play.click(dialog.getByRole("button", IdpPlays.OK));
                    // Differs from React: the dialog is on the page's body, not in the canvas
                    play.expect(dialog.findByText("Password is weak")).toBeInTheDocument();
                    expectNoChange(play);
                })
                // The confirmation doesn't match
                .story("Mismatch", context -> render(context, POLICY, SUCCEEDS))
                .withPlay(play -> {
                    final Play dialog = IdpPlays.fillPasswords(play, CAPTION, STRONG, "Different&3xtra");
                    play.click(dialog.getByRole("button", IdpPlays.OK));
                    play.expect(dialog.findByText("Passwords must match")).toBeInTheDocument();
                    expectNoChange(play);
                })
                // A stricter policy: too short
                .story("PolicyFailure", context -> render(context, STRICT_POLICY, SUCCEEDS))
                .withPlay(play -> {
                    final Play dialog = IdpPlays.fillPasswords(play, CAPTION, "ab1", "ab1");
                    play.click(dialog.getByRole("button", IdpPlays.OK));
                    play.expect(dialog.findByText("Password is short")).toBeInTheDocument();
                    expectNoChange(play);
                })
                // The server refuses the change
                .story("ServerError", context -> render(context, POLICY, SERVER_ERROR))
                .withPlay(play -> {
                    final Play dialog = IdpPlays.fillPasswords(play, CAPTION, STRONG, STRONG);
                    IdpPlays.waitForStrengthScored(play, dialog, "1-5");
                    play.click(dialog.getByRole("button", IdpPlays.OK));
                    // Differs from React: Stroom shows the server's message in an error alert over
                    // the dialog (AlertEvent.fireError), not inline
                    play.expect(play.screen().findByText("New password must differ from the previous one."))
                            .toBeInTheDocument();
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).toHaveBeenCalledWith(
                            "ERROR: New password must differ from the previous one.");
                    play.expect(play.spy(ON_CHANGED)).not().toHaveBeenCalled();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // The change succeeds and the server asks for a new sign in
                .story("ForceSignIn", context -> render(context, POLICY, FORCES_SIGN_IN))
                .withPlay(play -> {
                    final Play dialog = IdpPlays.fillPasswords(play, CAPTION, STRONG, STRONG);
                    IdpPlays.waitForStrengthScored(play, dialog, "1-5");
                    play.click(dialog.getByRole("button", IdpPlays.OK));
                    // Differs from React: Stroom's sign in page ignores forceSignIn (it signs in with
                    // afterLogin() whenever the change succeeds); the story's echo shows the flag
                    play.expect(play.findByText(TextMatch.containing("forceSignIn"))).toBeInTheDocument();
                    IdpPage.expectNoProblems(play);
                });
    }

    private static void expectNoChange(final Play play) {
        play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                RequestMatcher.post(IdpPlays.CHANGE_PASSWORD_PATH).toSpyMatcher());
        play.expect(play.spy(ON_CHANGED)).not().toHaveBeenCalled();
        IdpPage.expectNoProblems(play);
    }

    private static Widget render(final StoryContext context, final String policy, final RestReply changeReply) {
        final RestFixtures fixtures = RestFixtures.builder()
                .get(IdpPlays.FETCH_POLICY_PATH, RestReply.json(policy).delayed(100))
                .post(IdpPlays.CHANGE_PASSWORD_PATH, changeReply.delayed(250))
                .build();
        final IdpScreenGinjector injector = GWT.create(IdpScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .realAlerts()
                .build();
        harness.fn(ON_CHANGED);
        harness.fn(ON_CANCEL);

        // React's StoryHarness: the result of the change
        final Label result = new Label("onChanged: (awaiting change)");
        result.getElement().setAttribute("style", "font-size: 12px; opacity: 0.8");
        final FlowPanel column = new FlowPanel();
        column.getElement().setAttribute("style",
                "width: 380px; display: flex; flex-direction: column; gap: 8px; padding: 48px 16px 0");
        column.add(result);
        harness.add(column);

        final AuthenticationResource resource = GWT.create(AuthenticationResource.class);
        final ChangePasswordPresenter presenter = injector.getChangePasswordPresenter();
        // As LoginPresenter.changePassword, with afterLogin() replaced by React's onChanged
        presenter.show(CAPTION, e -> {
            if (e.isOk()) {
                if (presenter.validate()) {
                    final ChangePasswordRequest request = new ChangePasswordRequest(
                            "alice",
                            "correct-horse",
                            presenter.getPassword(),
                            presenter.getConfirmPassword());
                    harness.getRestFactory()
                            .create(resource)
                            .method(res -> res.changePassword(request))
                            .onSuccess(response -> {
                                if (response.isChangeSucceeded()) {
                                    final String changed = response.isForceSignIn()
                                            ? "changed ✓ — forceSignIn: redirect to login"
                                            : "changed ✓";
                                    result.setText("onChanged: " + changed);
                                    harness.spy(ON_CHANGED, changed);
                                } else {
                                    AlertEvent.fireError(presenter, response.getMessage(), e::reset);
                                }
                            })
                            .onFailure(RestErrorHandler.forPopup(presenter, e))
                            .taskMonitorFactory(new DefaultTaskMonitorFactory(presenter))
                            .exec();
                } else {
                    e.reset();
                }
            } else {
                e.hide();
                result.setText("onChanged: cancelled");
                harness.spy(ON_CANCEL, null);
            }
        });
        return harness.asWidget();
    }
}
