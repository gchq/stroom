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

import stroom.security.identity.client.presenter.AuthenticationErrorPresenter;
import stroom.security.identity.client.presenter.AuthenticationErrorPresenter.AuthenticationErrorView;
import stroom.security.identity.client.presenter.EmailResetPasswordPresenter;
import stroom.security.identity.client.presenter.EmailResetPasswordPresenter.EmailResetPasswordView;
import stroom.security.identity.client.presenter.LoginPresenter;
import stroom.security.identity.client.presenter.LoginPresenter.LoginView;
import stroom.security.identity.client.presenter.ResetPasswordPresenter;
import stroom.security.identity.client.presenter.ResetPasswordPresenter.ResetPasswordView;
import stroom.security.identity.client.view.AuthenticationErrorViewImpl;
import stroom.security.identity.client.view.EmailResetPasswordViewImpl;
import stroom.security.identity.client.view.LoginViewImpl;
import stroom.security.identity.client.view.ResetPasswordViewImpl;
import stroom.widget.presenter.client.gin.AbstractPresenterModule;

/// The bindings of the internal identity provider's pages and dialogs from Stroom's
/// `ChangePasswordModule` (`stroom/security/identity/client/gin/ChangePasswordModule.java`):
/// the sign in page (`LoginPresenter`), the authentication error page, the password reset page and
/// the change password and 'Reset Your Password' dialogs, without its plugins and the accounts
/// screens. The pages are bound as presenter widgets: Stroom's `App.onModuleLoad` shows them with
/// `show()`, and a story adds one to itself and calls its `onShown()` (see `client.app.idp.IdpPage`).
public class IdpScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bindPresenterWidget(LoginPresenter.class,
                LoginView.class,
                LoginViewImpl.class);
        bindPresenterWidget(AuthenticationErrorPresenter.class,
                AuthenticationErrorView.class,
                AuthenticationErrorViewImpl.class);
        bindPresenterWidget(ResetPasswordPresenter.class,
                ResetPasswordView.class,
                ResetPasswordViewImpl.class);
        bindPresenterWidget(EmailResetPasswordPresenter.class,
                EmailResetPasswordView.class,
                EmailResetPasswordViewImpl.class);
    }
}
