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
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.user.client.ui.Widget;

import java.util.Map;

/// Stories matching `App/IdP/Login` in the React Storybook, showing Stroom's real sign in page
/// (`LoginPresenter` with `LoginViewImpl`) with fake REST replies.
///
/// | React seam | Stroom REST endpoint |
/// |---|---|
/// | `login` | `POST /authentication/v1/login` (replies after 600ms, as React's fixtures) |
/// | `fetchPolicy` | `GET /authentication/v1/fetchPasswordPolicy` |
/// | `onSuccess` | `Window.Location.replace(redirect_uri)`, recorded by `IdpPage.ON_NAVIGATE` |
/// | `onRequirePasswordChange` | Stroom's 'Change Password' dialog (`POST /authentication/v1/changePassword`) |
/// | `onForgotPassword` | Stroom's 'Reset Your Password' dialog (`POST /authentication/v1/reset`) |
///
/// React's stories have no play functions: their plays here only check what the page shows before
/// anyone signs in, so the page can still be tried by hand.
public final class LoginStories {

    private static final int LOGIN_DELAY_MILLIS = 600;
    // Text in a help popup (the help is also in the page, hidden, as its field's description)
    private static final String HELP_POPUP_TEXT = ".help-button-tooltip *";
    private static final String USER_NAME_HELP = "Enter your Stroom account user name. This is the name you "
                                                 + "were given when your account was created.";
    private static final String PASSWORD_HELP_RESETS = "Enter your Stroom account password. If you have "
                                                       + "forgotten it, use the 'Forgot password?' link.";
    private static final String PASSWORD_HELP_NO_RESETS = "Enter your Stroom account password. If you have "
                                                          + "forgotten it, ask your administrator to reset it.";
    // React's loginPending never resolves
    private static final int NEVER_MILLIS = 24 * 60 * 60 * 1000;

    private LoginStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/IdP/Login", LoginStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Valid credentials sign in (and navigate)
                .story("Default", context -> render(context, false,
                        RestReply.json("{\"loginSuccessful\": true}")))
                .withPlay(LoginStories::expectSignInForm)
                // Bad credentials: the server's message
                .story("ErrorMessage", context -> render(context, false, RestReply.json("""
                        {"loginSuccessful": false, "message": "Invalid credentials. Please try again."}""")))
                .withPlay(LoginStories::expectSignInForm)
                // The server requires a password change
                .story("RequirePasswordChange", context -> render(context, false, RestReply.json("""
                        {"requirePasswordChange": true, "loginSuccessful": false}""")))
                .withPlay(LoginStories::expectSignInForm)
                // The policy allows resets: 'Forgot password?' is shown
                .story("ForgotLinkVisible", context -> render(context, true,
                        RestReply.json("{\"loginSuccessful\": true}")))
                .withPlay(play -> {
                    expectSignInForm(play);
                    play.waitFor(() -> play.expect(play.getByText("Forgot password?")).toBeVisible());
                    // The password's help points to the link. It is the password's description, and
                    // F1 shows it
                    final Query password = play.getByLabelText("Password");
                    play.waitFor(() -> play.expect(password).toHaveAccessibleDescription(PASSWORD_HELP_RESETS));
                    play.expect(password).toHaveAttribute("aria-keyshortcuts", "F1");
                    // Clicking the help button shows it, leaving the focus in the user name
                    play.click(play.getByTitle("Password - Click for help"));
                    play.expect(play.screen().findByText(PASSWORD_HELP_RESETS, HELP_POPUP_TEXT)).toBeInTheDocument();
                    play.expect(play.getByLabelText("User Name")).toHaveFocus();
                })
                // The policy forbids resets: 'Forgot password?' is hidden
                .story("ForgotLinkHidden", context -> render(context, false,
                        RestReply.json("{\"loginSuccessful\": true}")))
                .withPlay(play -> {
                    expectSignInForm(play);
                    // Differs from React: the link is in the page but hidden, not left out
                    play.expect(play.getByText("Forgot password?")).not().toBeVisible();
                    // The password's help points to the administrator, not the hidden link
                    play.expect(play.getByLabelText("Password")).toHaveAccessibleDescription(
                            PASSWORD_HELP_NO_RESETS);
                    play.click(play.getByTitle("Password - Click for help"));
                    play.expect(play.screen().findByText(PASSWORD_HELP_NO_RESETS, HELP_POPUP_TEXT)).toBeInTheDocument();
                })
                // The sign in request never completes
                .story("Loading", context -> render(context, false,
                        RestReply.json("{\"loginSuccessful\": true}"), NEVER_MILLIS))
                .withPlay(LoginStories::expectSignInForm)
                // GWT-only: signing in with nothing entered marks both fields invalid, describes
                // each with its message and focuses the first, without sending a request
                .story("MissingCredentials", context -> render(context, false,
                        RestReply.json("{\"loginSuccessful\": true}")))
                .withPlay(play -> {
                    expectSignInForm(play);
                    play.click(play.getByRole("button", IdpPlays.SIGN_IN));
                    play.expect(play.findByText("User name is required")).toBeInTheDocument();
                    play.expect(play.getByText("Password is required")).toBeInTheDocument();
                    play.expect(play.getByLabelText("User Name")).toHaveAttribute("aria-invalid", "true");
                    play.expect(play.getByLabelText("Password")).toHaveAttribute("aria-invalid", "true");
                    // Each is described by its message, then its help
                    play.expect(play.getByLabelText("User Name")).toHaveAccessibleDescription(
                            "User name is required " + USER_NAME_HELP);
                    play.expect(play.getByLabelText("Password")).toHaveAccessibleDescription(
                            "Password is required " + PASSWORD_HELP_NO_RESETS);
                    play.expect(play.getByLabelText("User Name")).toHaveFocus();
                    // With a user name, the password is the first field that needs fixing
                    play.type(play.getByLabelText("User Name"), "admin");
                    play.click(play.getByRole("button", IdpPlays.SIGN_IN));
                    play.waitFor(() -> play.expect(play.getByLabelText("Password")).toHaveFocus());
                    play.expect(play.getByLabelText("User Name")).not().toHaveAttribute("aria-invalid");
                    play.expect(play.queryByText("User name is required")).toBeNull();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post(IdpPlays.LOGIN_PATH).toSpyMatcher());
                });
    }

    // The empty form, with the user name focused, once the policy has loaded
    private static void expectSignInForm(final Play play) {
        play.expect(play.findByPlaceholderText("Enter User Name")).toHaveFocus();
        play.expect(play.getByPlaceholderText("Enter Password")).toHaveValue("");
        play.expect(play.getByRole("button", IdpPlays.SIGN_IN)).toBeEnabled();
        play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                IdpPlays.FETCH_POLICY.toSpyMatcher()));
        IdpPage.expectNoProblems(play);
    }

    private static Widget render(final StoryContext context,
                                 final boolean allowPasswordResets,
                                 final RestReply loginReply) {
        return render(context, allowPasswordResets, loginReply, LOGIN_DELAY_MILLIS);
    }

    private static Widget render(final StoryContext context,
                                 final boolean allowPasswordResets,
                                 final RestReply loginReply,
                                 final int loginDelayMillis) {
        final RestFixtures fixtures = RestFixtures.builder()
                .get(IdpPlays.FETCH_POLICY_PATH, RestReply.json(IdpPlays.policy(allowPasswordResets, 8, 3,
                        "Passwords must be at least 8 characters.")))
                .post(IdpPlays.LOGIN_PATH, loginReply.delayed(loginDelayMillis))
                .post(IdpPlays.CHANGE_PASSWORD_PATH, RestReply.json("{\"changeSucceeded\": true}"))
                .post(IdpPlays.RESET_EMAIL_PATH, RestReply.json("true"))
                .build();
        return SignInPages.render(context, fixtures, null, Map.of(), IdpPage::redirectedMessage, true,
                (injector, harness) -> injector.getLoginPresenter());
    }
}
