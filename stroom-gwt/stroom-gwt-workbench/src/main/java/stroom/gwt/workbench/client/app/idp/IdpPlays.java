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
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.TextMatch;

/// The REST paths, fixtures and play steps shared by the `App/IdP/*` stories.
final class IdpPlays {

    /// `AuthenticationResource.fetchPasswordPolicy`.
    static final String FETCH_POLICY_PATH = "/authentication/v1/fetchPasswordPolicy";
    /// `AuthenticationResource.login`.
    static final String LOGIN_PATH = "/authentication/v1/login";
    /// `AuthenticationResource.changePassword`.
    static final String CHANGE_PASSWORD_PATH = "/authentication/v1/changePassword";
    /// `AuthenticationResource.confirmPassword`.
    static final String CONFIRM_PASSWORD_PATH = "/authentication/v1/confirmPassword";
    /// `AuthenticationResource.resetPassword` (from an emailed link).
    static final String RESET_PASSWORD_PATH = "/authentication/v1/resetPassword";
    /// `AuthenticationResource.resetEmail` (asks for the link).
    static final String RESET_EMAIL_PATH = "/authentication/v1/reset";

    /// The request fetching the password policy.
    static final RequestMatcher FETCH_POLICY = RequestMatcher.get(FETCH_POLICY_PATH);

    /// The sign in page's 'Sign In' button (React's `/^sign in$/i`).
    static final TextMatch SIGN_IN = StroomDom.button("Sign In");
    /// A dialog's OK button (React's `/^ok$/i`).
    static final TextMatch OK = StroomDom.button("OK");

    private IdpPlays() {
        // Static utility
    }

    /// @param allowPasswordResets Whether 'Forgot password?' is shown.
    /// @param minimumLength       The minimum password length.
    /// @param minimumStrength     The minimum zxcvbn score.
    /// @param message             The policy's message.
    /// @return The JSON of an `InternalIdpPasswordPolicyConfig`.
    static String policy(final boolean allowPasswordResets,
                         final int minimumLength,
                         final int minimumStrength,
                         final String message) {
        return "{\"allowPasswordResets\": " + allowPasswordResets
               + ", \"minimumPasswordLength\": " + minimumLength
               + ", \"minimumPasswordStrength\": " + minimumStrength
               + ", \"passwordPolicyMessage\": \"" + message + "\"}";
    }

    /// Waits for the change password dialog (`ChangePasswordPresenter`, shown once the policy has
    /// loaded) and types the new password and its confirmation.
    ///
    /// Differs from React: the dialog is on the page's body, and is found by its caption; its
    /// 'Enter Password' field is looked for in the dialog, as the sign in page beneath it has one
    /// too.
    ///
    /// @param play     The play.
    /// @param caption  The dialog's caption, e.g. `Change Password`.
    /// @param password The new password.
    /// @param confirm  The confirmation.
    /// @return The dialog.
    static Play fillPasswords(final Play play, final String caption, final String password, final String confirm) {
        final Play dialog = passwordDialog(play, caption);
        // React waits for the policy's message (role="note"): the dialog shows it
        play.expect(dialog.querySelector(".passwordPolicyMessage")).not().toHaveTextContent("");
        play.type(dialog.getByPlaceholderText("Enter Password"), password);
        play.type(dialog.getByPlaceholderText("Confirm Password"), confirm);
        return dialog;
    }

    /// @param play    The play.
    /// @param caption The dialog's caption, e.g. `Change Password`.
    /// @return The change password dialog, once shown.
    static Play passwordDialog(final Play play, final String caption) {
        final Play screen = play.screen();
        return screen.within(screen.findByText(caption, StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
    }

    /// Waits for the strength meter to rate the password (React's `waitForStrengthScored`).
    /// Differs from React: Stroom's meter has no `meter` role; its bar's class is
    /// `strength-meter-<score + 1>`, set when the password field changes (on blur), with zxcvbn
    /// loaded by the page, so there is nothing to wait for but the class.
    ///
    /// @param play   The play.
    /// @param dialog The change password dialog.
    /// @param levels The bar's levels to wait for, as a character class, e.g. `1-5`.
    static void waitForStrengthScored(final Play play, final Play dialog, final String levels) {
        play.waitFor(8000, () -> play.expect(dialog.querySelector(".strength-meter-bar").className())
                .toMatch(TextMatch.regex("strength-meter-[" + levels + "]")));
    }
}
