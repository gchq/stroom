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
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.user.client.ui.Widget;

import java.util.Map;

/// The `App/IdP/AuthenticationError` stories, showing Stroom's real authentication error page
/// (`AuthenticationErrorPresenter` with `AuthenticationErrorViewImpl`), which Stroom shows for
/// `/signIn?error=…` with any error but `login_required`. The error is the URL's `error` parameter
/// (see `IdpPage`) and the message the UI config's `authErrorMessage` (trusted HTML). The page has
/// no way back to sign in.
///
/// The plays check what the page shows.
public final class AuthenticationErrorStories {

    // The operator's message, as a JSON string
    private static final String MESSAGE_JSON = "\"<p>Your session could not be established. Please contact "
                                               + "<a href=\\\"mailto:support@example.com\\\">support</a> "
                                               + "if this problem persists.</p>\"";

    private AuthenticationErrorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/IdP/AuthenticationError", AuthenticationErrorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The error only, without a message from the config
                .story("WithError", context -> render(context,
                        "access_denied: the request was rejected by the identity provider", null))
                .withPlay(play -> {
                    play.expect(play.findByText("access_denied: the request was rejected by the identity provider"))
                            .toBeInTheDocument();
                    play.expect(play.getByText("Authentication Error", "label")).toBeInTheDocument();
                    IdpPage.expectNoProblems(play);
                })
                // The error and the operator's (trusted HTML) message
                .story("WithConfigMessage", context -> render(context, "server_error", MESSAGE_JSON))
                .withPlay(play -> {
                    play.expect(play.findByText("server_error")).toBeInTheDocument();
                    play.expect(play.getByRole("link", "support")).toHaveAttribute("href", "mailto:support@example.com");
                    IdpPage.expectNoProblems(play);
                });
    }

    private static Widget render(final StoryContext context, final String error, final String messageJson) {
        final String uiConfig = messageJson == null
                ? null
                : QueryFixtures.uiConfigWith("\"authErrorMessage\": " + messageJson);
        return SignInPages.render(context, RestFixtures.builder().build(), uiConfig, Map.of("error", error),
                IdpPage::redirectedMessage, true, (injector, harness) -> injector.getAuthenticationErrorPresenter());
    }
}
