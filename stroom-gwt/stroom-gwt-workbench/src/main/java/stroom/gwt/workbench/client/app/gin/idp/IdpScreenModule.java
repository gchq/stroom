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
import stroom.security.identity.client.presenter.AuthenticationErrorPresenter.AuthenticationErrorProxy;
import stroom.security.identity.client.presenter.AuthenticationErrorPresenter.AuthenticationErrorView;
import stroom.security.identity.client.presenter.ChangePasswordPresenter;
import stroom.security.identity.client.presenter.ChangePasswordPresenter.ChangePasswordView;
import stroom.security.identity.client.presenter.EmailResetPasswordPresenter;
import stroom.security.identity.client.presenter.EmailResetPasswordPresenter.EmailResetPasswordView;
import stroom.security.identity.client.presenter.LoginPresenter;
import stroom.security.identity.client.presenter.LoginPresenter.LoginProxy;
import stroom.security.identity.client.presenter.LoginPresenter.LoginView;
import stroom.security.identity.client.presenter.ResetPasswordPresenter;
import stroom.security.identity.client.presenter.ResetPasswordPresenter.ResetPasswordProxy;
import stroom.security.identity.client.presenter.ResetPasswordPresenter.ResetPasswordView;
import stroom.security.identity.client.view.AuthenticationErrorViewImpl;
import stroom.security.identity.client.view.ChangePasswordViewImpl;
import stroom.security.identity.client.view.EmailResetPasswordViewImpl;
import stroom.security.identity.client.view.LoginViewImpl;
import stroom.security.identity.client.view.ResetPasswordViewImpl;

import com.google.inject.Provides;
import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The bindings of the internal identity provider's pages and dialogs from Stroom's
/// `ChangePasswordModule` (`stroom/security/identity/client/gin/ChangePasswordModule.java`):
/// the sign in page (`LoginPresenter`), the authentication error page, the password reset page and
/// the change password and 'Reset Your Password' dialogs, without its plugins and the accounts
/// screens. Presenters that Stroom binds with a GWTP proxy are bound as presenter widgets with a
/// null proxy: Stroom's `App.onModuleLoad` reveals them with `forceReveal()`, which a story does by
/// calling their `revealInParent()` (see `client.app.idp.IdpPage`).
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
        // CurrentPasswordPresenter is left out: it needs Stroom's CurrentUser, whose graph (the
        // splash screen, the current activity) the stories don't have, so its story creates it
        // with `new`.
        bindPresenterWidget(ChangePasswordPresenter.class,
                ChangePasswordView.class,
                ChangePasswordViewImpl.class);
        bindPresenterWidget(ResetPasswordPresenter.class,
                ResetPasswordView.class,
                ResetPasswordViewImpl.class);
        bindPresenterWidget(EmailResetPasswordPresenter.class,
                EmailResetPasswordView.class,
                EmailResetPasswordViewImpl.class);
    }

    /// @return No proxy, see the class description.
    @Provides
    LoginProxy provideLoginProxy() {
        return null;
    }

    /// @return No proxy, see the class description.
    @Provides
    AuthenticationErrorProxy provideAuthenticationErrorProxy() {
        return null;
    }

    /// @return No proxy, see the class description.
    @Provides
    ResetPasswordProxy provideResetPasswordProxy() {
        return null;
    }
}
