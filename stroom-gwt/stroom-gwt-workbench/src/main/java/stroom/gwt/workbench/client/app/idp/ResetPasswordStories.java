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

import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import java.util.Map;

/// Stories matching `App/IdP/ResetPassword` in the React Storybook, showing Stroom's real password
/// reset page (`ResetPasswordPresenter`, served for `/resetPassword?token=…` from an emailed link)
/// with fake REST replies. It asks for the new password with the 'Change Password' dialog
/// (captioned 'Reset Password').
///
/// | React seam | Stroom |
/// |---|---|
/// | `token` | the URL's `token` parameter (`Window.Location.getParameter`), see `IdpPage` |
/// | `fetchPasswordPolicy` | `GET /authentication/v1/fetchPasswordPolicy` |
/// | `resetPassword` | `POST /authentication/v1/resetPassword` (only for the token `good-token`) |
/// | `onSignIn` | `Window.Location.replace("/signIn?error=login_required")`, recorded by `IdpPage.ON_NAVIGATE` |
public final class ResetPasswordStories {

    // ResetPasswordPresenter.SIGN_IN_URL
    private static final String SIGN_IN_URL = "/signIn?error=login_required";
    private static final String CAPTION = "Reset Password";
    private static final String STRONG = "Tr0ub4dour&3xtra";
    private static final String POLICY = IdpPlays.policy(false, 8, 3,
            "Passwords must be at least 8 characters and be hard to guess.");

    // React's resetOk accepts only 'good-token' (any other request is unhandled, failing the story)
    private static final RequestMatcher RESET_GOOD_TOKEN = RequestMatcher.post(IdpPlays.RESET_PASSWORD_PATH)
            .withJsonBodyContaining("{\"token\": \"good-token\"}");
    private static final RestReply RESET_OK = RestReply.json("{\"changeSucceeded\": true, \"message\": \"\"}");
    private static final RestReply RESET_REJECTED = RestReply.json("""
            {"changeSucceeded": false, "message": "This password reset link has expired."}""");

    private ResetPasswordStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/IdP/ResetPassword", ResetPasswordStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // A valid link: a strong password is accepted, then back to sign in
                .story("Default", context -> render(context, "good-token", RESET_OK))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    // Differs from React: the dialog is on the page's body
                    play.expect(screen.findByText(CAPTION, StroomDom.DIALOG_TITLE)).toBeInTheDocument();
                    final Play dialog = IdpPlays.fillPasswords(play, CAPTION, STRONG, STRONG);
                    IdpPlays.waitForStrengthScored(play, dialog, "1-5");
                    play.click(dialog.getByRole("button", IdpPlays.OK));

                    // Stroom hides the form first, then shows the info alert
                    play.expect(screen.findByText(TextMatch.regex(
                                    "Your password has been reset\\. Please sign in with your new password\\.")))
                            .toBeInTheDocument();
                    play.expect(screen.queryByPlaceholderText("Enter Password")).toBeNull();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(IdpPlays.RESET_PASSWORD_PATH).withJsonBodyContaining("""
                                    {"token": "good-token", "newPassword": "Tr0ub4dour&3xtra",
                                      "confirmNewPassword": "Tr0ub4dour&3xtra"}""").toSpyMatcher());

                    play.click(screen.getByRole("button", StroomDom.button("Close")));
                    expectSignIn(play);
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).toHaveBeenCalledWith(
                            "INFO: Your password has been reset. Please sign in with your new password.");
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // GWT-only: OK with only the confirmation entered marks the new password invalid and
                // focuses it, without asking the server
                .story("MissingPassword", context -> render(context, "good-token", RESET_OK))
                .withPlay(play -> {
                    final Play dialog = IdpPlays.passwordDialog(play, CAPTION);
                    play.type(dialog.getByLabelText("Confirm Password"), STRONG);
                    IdpPlays.expectOkFocusesInvalid(play, dialog, "Password", "Password is required");
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post(IdpPlays.RESET_PASSWORD_PATH).toSpyMatcher());
                })
                // No token: the form is never shown, the invalid link alert is
                .story("InvalidLink", context -> render(context, null, RESET_OK))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.expect(screen.findByText(TextMatch.regex("This password reset link is invalid or has "
                                    + "expired\\. Please request a new one\\.")))
                            .toBeInTheDocument();
                    play.expect(screen.queryByPlaceholderText("Enter Password")).toBeNull();

                    play.click(screen.getByRole("button", StroomDom.button("Close")));
                    expectSignIn(play);
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            IdpPlays.FETCH_POLICY.toSpyMatcher());
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // The server refuses: the message is shown and the form stays open
                .story("ServerRejects", context -> render(context, "good-token", RESET_REJECTED))
                .withPlay(play -> {
                    final Play dialog = IdpPlays.fillPasswords(play, CAPTION, STRONG, STRONG);
                    IdpPlays.waitForStrengthScored(play, dialog, "1-5");
                    play.click(dialog.getByRole("button", IdpPlays.OK));

                    // Differs from React: Stroom shows the server's message in an error alert over
                    // the form (AlertEvent.fireError), not inline
                    play.expect(play.screen().findByText("This password reset link has expired."))
                            .toBeInTheDocument();
                    play.expect(dialog.getByPlaceholderText("Enter Password")).toBeInTheDocument();
                    play.expect(play.spy(IdpPage.ON_NAVIGATE)).not().toHaveBeenCalled();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // Cancel goes back to sign in
                .story("Cancel", context -> render(context, "good-token", RESET_OK))
                .withPlay(play -> {
                    final Play dialog = IdpPlays.passwordDialog(play, CAPTION);
                    dialog.findByPlaceholderText("Confirm Password");
                    play.click(dialog.getByRole("button", StroomDom.button("Cancel")));
                    expectSignIn(play);
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post(IdpPlays.RESET_PASSWORD_PATH).toSpyMatcher());
                    IdpPage.expectNoProblems(play);
                });
    }

    // React's '→ /signIn?error=login_required' marker: Stroom navigated to the sign in page
    private static void expectSignIn(final Play play) {
        play.expect(play.findByText("→ " + SIGN_IN_URL)).toBeInTheDocument();
        play.expect(play.spy(IdpPage.ON_NAVIGATE)).toHaveBeenCalledWith(SIGN_IN_URL);
    }

    // React's marker of the navigation back to sign in, beside the page
    private static Widget signInMarker(final String url) {
        final Label marker = new Label("→ " + url);
        marker.getElement().setAttribute("style", "position: fixed; bottom: 8px; left: 8px");
        return marker;
    }

    private static Widget render(final StoryContext context, final String token, final RestReply resetReply) {
        final RestFixtures fixtures = RestFixtures.builder()
                .get(IdpPlays.FETCH_POLICY_PATH, RestReply.json(POLICY))
                .route(RESET_GOOD_TOKEN, resetReply)
                .build();
        final Map<String, String> parameters = token == null
                ? Map.of()
                : Map.of("token", token);
        return SignInPages.render(context, fixtures, null, parameters, ResetPasswordStories::signInMarker, false,
                (injector, harness) -> injector.getResetPasswordPresenter());
    }
}
