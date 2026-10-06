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

import stroom.gwt.workbench.client.app.query.QueryFixtures;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.Presenter;

import java.util.LinkedHashMap;
import java.util.Map;

/// Stories matching `App/IdP/SignInFlow` in the React Storybook: Stroom's sign in host page for
/// `/signIn?error=…&redirect_uri=…`, as Stroom's `App.onModuleLoad` shows it (the sign in page,
/// `LoginPresenter`, for `error=login_required`, otherwise the authentication error page,
/// `AuthenticationErrorPresenter`), through to the navigation after signing in.
///
/// | React | Stroom |
/// |---|---|
/// | `error`, `redirectUri` props | the URL's `error` and `redirect_uri` parameters (see `IdpPage`) |
/// | `authErrorMessage` | the UI config's `authErrorMessage` |
/// | `api.fetchPasswordPolicy` | `GET /authentication/v1/fetchPasswordPolicy` |
/// | `api.login` | `POST /authentication/v1/login` |
/// | `api.changePassword` | `POST /authentication/v1/changePassword` |
/// | `api.resetEmail` | `POST /authentication/v1/reset` |
/// | `onRedirect` | `Window.Location.replace(redirect_uri)`, recorded by `IdpPage.ON_NAVIGATE` |
public final class SignInFlowStories {

    private static final String POLICY = IdpPlays.policy(true, 8, 2,
            "Use at least 8 characters, mixing letters, numbers and symbols.");
    private static final String STRONG = "Str0ng!Passw9x";

    private SignInFlowStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/IdP/SignInFlow", SignInFlowStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Correct credentials: straight to the redirect target
                .story("HappyPath", context -> render(context, "login_required", "/dashboard",
                        RestReply.json("{\"loginSuccessful\": true}"), null))
                .withPlay(play -> {
                    play.type(play.findByPlaceholderText("Enter User Name"), "admin");
                    play.type(play.getByPlaceholderText("Enter Password"), "password123");
                    play.click(play.getByRole("button", IdpPlays.SIGN_IN));
                    // Success: Stroom navigates (Window.Location.replace), shown in place of the page
                    play.expect(play.findByText(TextMatch.containingIgnoreCase("redirected to")))
                            .toBeInTheDocument();
                    play.expect(play.getByText("/dashboard")).toBeInTheDocument();
                    play.expect(play.spy(IdpPage.ON_NAVIGATE)).toHaveBeenCalledWith("/dashboard");
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(IdpPlays.LOGIN_PATH)
                                    .withJsonBodyContaining("{\"userId\": \"admin\", \"password\": \"password123\"}")
                                    .toSpyMatcher());
                    IdpPage.expectNoProblems(play);
                })
                // A required password change: the change dialog, then the redirect
                .story("ForcedPasswordChange", context -> render(context, "login_required", "/dashboard",
                        RestReply.json("{\"requirePasswordChange\": true}"), null))
                .withPlay(play -> {
                    // 1) Sign in: the server requires a password change
                    play.type(play.findByPlaceholderText("Enter User Name"), "admin");
                    play.type(play.getByPlaceholderText("Enter Password"), "oldpassword");
                    play.click(play.getByRole("button", IdpPlays.SIGN_IN));
                    // 2) The change password dialog (on the page's body, over the sign in page)
                    final Play dialog = IdpPlays.fillPasswords(play, "Change Password", STRONG, STRONG);
                    // Differs from React: no meter role; aria-valuenow >= 2 is a bar level >= 3
                    IdpPlays.waitForStrengthScored(play, dialog, "3-5");
                    play.click(dialog.getByRole("button", IdpPlays.OK));
                    // 3) The change succeeds: the redirect. The typed password is the current one.
                    play.expect(play.findByText(TextMatch.containingIgnoreCase("redirected to")))
                            .toBeInTheDocument();
                    play.expect(play.spy(IdpPage.ON_NAVIGATE)).toHaveBeenCalledWith("/dashboard");
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(IdpPlays.CHANGE_PASSWORD_PATH).withJsonBodyContaining("""
                                    {"userId": "admin", "currentPassword": "oldpassword",
                                      "newPassword": "Str0ng!Passw9x", "confirmNewPassword": "Str0ng!Passw9x"}""")
                                    .toSpyMatcher());
                    IdpPage.expectNoProblems(play);
                })
                // Bad credentials: the server's message, and no redirect
                .story("BadCredentials", context -> render(context, "login_required", null,
                        RestReply.json("{\"loginSuccessful\": false, \"message\": \"Invalid credentials.\"}"), null))
                .withPlay(play -> {
                    play.type(play.findByPlaceholderText("Enter User Name"), "admin");
                    play.type(play.getByPlaceholderText("Enter Password"), "wrong");
                    play.click(play.getByRole("button", IdpPlays.SIGN_IN));
                    // Differs from React: Stroom shows the message in an error alert
                    // (AlertEvent.fireError) on the page's body, not inline in the form
                    play.expect(play.screen().findByText("Invalid credentials.")).toBeInTheDocument();
                    play.expect(play.queryByText(TextMatch.containingIgnoreCase("redirected to"))).toBeNull();
                    play.expect(play.spy(IdpPage.ON_NAVIGATE)).not().toHaveBeenCalled();
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).toHaveBeenCalledWith("ERROR: Invalid credentials.");
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // Any other error: the authentication error page
                .story("AuthError", context -> render(context, "access_denied", null,
                        RestReply.json("{\"loginSuccessful\": true}"),
                        "Your account is not permitted to access this application."))
                .withPlay(play -> {
                    // React has no play: this checks the page shows the error and the message
                    play.expect(play.findByText("access_denied")).toBeInTheDocument();
                    play.expect(play.getByText("Your account is not permitted to access this application."))
                            .toBeInTheDocument();
                    play.expect(play.queryByPlaceholderText("Enter User Name")).toBeNull();
                    IdpPage.expectNoProblems(play);
                });
    }

    private static Widget render(final StoryContext context,
                                 final String error,
                                 final String redirectUri,
                                 final RestReply loginReply,
                                 final String authErrorMessage) {
        final RestFixtures fixtures = RestFixtures.builder()
                .get(IdpPlays.FETCH_POLICY_PATH, RestReply.json(POLICY))
                .post(IdpPlays.LOGIN_PATH, loginReply)
                .post(IdpPlays.CHANGE_PASSWORD_PATH, RestReply.json("{\"changeSucceeded\": true}"))
                .post(IdpPlays.RESET_EMAIL_PATH, RestReply.json("true"))
                .build();
        final Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("error", error);
        if (redirectUri != null) {
            parameters.put("redirect_uri", redirectUri);
        }
        final String uiConfig = authErrorMessage == null
                ? null
                : QueryFixtures.uiConfigWith("\"authErrorMessage\": \"" + authErrorMessage + "\"");
        return SignInPages.render(context, fixtures, uiConfig, parameters, IdpPage::redirectedMessage, true,
                (injector, harness) -> {
                    // As App.onModuleLoad does for '/signIn'
                    final Presenter<?, ?> page;
                    if ("login_required".equals(Window.Location.getParameter("error"))) {
                        page = injector.getLoginPresenter();
                    } else {
                        page = injector.getAuthenticationErrorPresenter();
                    }
                    return page;
                });
    }
}
