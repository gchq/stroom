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

import stroom.gwt.workbench.client.app.gin.ScreenViewsModule;
import stroom.gwt.workbench.client.app.screen.ScreenGinjector;
import stroom.security.identity.client.presenter.AuthenticationErrorPresenter;
import stroom.security.identity.client.presenter.ChangePasswordPresenter;
import stroom.security.identity.client.presenter.EmailResetPasswordPresenter;
import stroom.security.identity.client.presenter.LoginPresenter;
import stroom.security.identity.client.presenter.ResetPasswordPresenter;

import com.google.gwt.inject.client.GinModules;

/// The `idp` batch's [ScreenGinjector]: the internal identity provider's pages (sign in,
/// authentication error, password reset) and dialogs, created as Stroom's GIN modules create them,
/// for the stories in `client.app.idp`:
/// ```
/// final IdpScreenGinjector injector = GWT.create(IdpScreenGinjector.class);
/// final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES).injector(injector).build();
/// final LoginPresenter presenter = injector.getLoginPresenter();
/// ```
/// Create one each time the story renders: its singletons belong to that rendering's harness.
@GinModules({
        ScreenViewsModule.class,
        IdpScreenModule.class,
})
public interface IdpScreenGinjector extends ScreenGinjector {

    /// @return A new sign in page, which fetches the password policy as it is created. The
    /// page's host elements (`#loading`, `#loadingText`) must exist first.
    LoginPresenter getLoginPresenter();

    /// @return A new authentication error page. The page's host elements must exist first.
    AuthenticationErrorPresenter getAuthenticationErrorPresenter();

    /// @return A new password reset page. The page's host elements must exist first.
    ResetPasswordPresenter getResetPasswordPresenter();

    /// @return A new 'Change Password' dialog.
    ChangePasswordPresenter getChangePasswordPresenter();

    /// @return A new 'Reset Your Password' dialog.
    EmailResetPasswordPresenter getEmailResetPasswordPresenter();
}
