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

package stroom.gwt.workbench.client.app.gin.idp;

import stroom.gwt.workbench.client.app.screen.ScreenGinjector;
import stroom.security.identity.client.component.AuthenticationErrorController;
import stroom.security.identity.client.component.ChangePasswordController;
import stroom.security.identity.client.component.EmailResetPasswordController;
import stroom.security.identity.client.component.LoginController;
import stroom.security.identity.client.component.ResetPasswordController;

import com.google.gwt.inject.client.GinModules;

/// The `idp` batch's [ScreenGinjector]: the internal identity provider's pages (sign in,
/// authentication error, password reset) and dialogs, created as Stroom's GIN modules create them,
/// for the stories in `client.app.idp`:
/// ```
/// final IdpScreenGinjector injector = GWT.create(IdpScreenGinjector.class);
/// final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES).injector(injector).build();
/// final LoginController page = injector.getLoginController();
/// ```
/// Create one each time the story renders: its singletons belong to that rendering's harness.
@GinModules({
})
public interface IdpScreenGinjector extends ScreenGinjector {

    /// @return A new sign in page, which fetches the password policy as it is created. The
    /// page's host element (`#loading`) must exist before it is shown.
    LoginController getLoginController();

    /// @return A new authentication error page. The page's host element must exist before it is
    /// shown.
    AuthenticationErrorController getAuthenticationErrorController();

    /// @return A new password reset page. The page's host element must exist before it is shown.
    ResetPasswordController getResetPasswordController();

    /// @return A new 'Change Password' dialog.
    ChangePasswordController getChangePasswordController();

    /// @return A new 'Reset Your Password' dialog.
    EmailResetPasswordController getEmailResetPasswordController();
}
