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
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/IdP/EmailResetPassword` in the React Storybook, showing Stroom's real
/// 'Reset Your Password' dialog (`EmailResetPasswordPresenter` with `EmailResetPasswordViewImpl`),
/// as the sign in page's 'Forgot password?' link shows it, with fake REST replies.
///
/// | React seam | Stroom REST endpoint |
/// |---|---|
/// | `resetEmail` | `POST /authentication/v1/reset` (`true` or `false`) |
/// | `onClose` | the dialog's Cancel (or OK once the email is sent) |
///
/// `InvalidEmail` has no route for the request, so a request would fail the story, as React's
/// fixture throws.
///
/// React's stories have no play functions: their plays here check the empty dialog, so it can still
/// be tried by hand.
public final class EmailResetPasswordStories {

    private static final String CAPTION = "Reset Your Password";

    private EmailResetPasswordStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/IdP/EmailResetPassword", EmailResetPasswordStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // An empty form; the server accepts the request
                .story("Default", context -> render(context, RestReply.json("true")))
                .withPlay(EmailResetPasswordStories::expectEmptyDialog)
                // The address is checked before any request (no route: a request would fail)
                .story("InvalidEmail", context -> render(context, null))
                .withPlay(EmailResetPasswordStories::expectEmptyDialog)
                // The server sends the email
                .story("ResetSent", context -> render(context, RestReply.json("true")))
                .withPlay(EmailResetPasswordStories::expectEmptyDialog)
                // The server can't reset the password
                .story("ResetFailed", context -> render(context, RestReply.json("false")))
                .withPlay(EmailResetPasswordStories::expectEmptyDialog)
                // GWT-only: OK with no email, then with an invalid one, marks the field invalid and
                // focuses it, without asking the server
                .story("InvalidEmailFocus", context -> render(context, null))
                .withPlay(play -> {
                    expectEmptyDialog(play);
                    final Play dialog = IdpPlays.passwordDialog(play, CAPTION);
                    IdpPlays.expectOkFocusesInvalid(play, dialog, "Email Address", "Email is required");
                    play.type(dialog.getByLabelText("Email Address"), "not-an-email");
                    IdpPlays.expectOkFocusesInvalid(play, dialog, "Email Address", "Invalid email address");
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalled();
                });
    }

    // The dialog, with its email field focused and empty
    private static void expectEmptyDialog(final Play play) {
        final Play screen = play.screen();
        final Play dialog = screen.within(screen.findByText(CAPTION, StroomDom.DIALOG_TITLE)
                .closest(StroomDom.DIALOG));
        play.expect(dialog.getByPlaceholderText("Enter Your Email Address")).toHaveValue("");
        play.waitFor(() -> play.expect(dialog.getByPlaceholderText("Enter Your Email Address")).toHaveFocus());
        play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalled();
        IdpPage.expectNoProblems(play);
    }

    private static Widget render(final StoryContext context, final RestReply resetReply) {
        final RestFixtures.Builder fixtures = RestFixtures.builder();
        if (resetReply != null) {
            fixtures.post(IdpPlays.RESET_EMAIL_PATH, resetReply);
        }
        final IdpScreenGinjector injector = GWT.create(IdpScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures.build())
                .injector(injector)
                .realAlerts()
                .build();
        injector.getEmailResetPasswordPresenter().show();
        return harness.asWidget();
    }
}
