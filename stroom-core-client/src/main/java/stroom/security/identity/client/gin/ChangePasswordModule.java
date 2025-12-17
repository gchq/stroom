/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.security.identity.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.security.identity.client.AccountsPlugin;
import stroom.security.identity.client.ChangePasswordPlugin;
import stroom.security.identity.client.presenter.AccountsPresenter;
import stroom.security.identity.client.presenter.AccountsPresenter.AccountsView;
import stroom.security.identity.client.presenter.AuthenticationErrorPresenter;
import stroom.security.identity.client.presenter.AuthenticationErrorPresenter.AuthenticationErrorProxy;
import stroom.security.identity.client.presenter.AuthenticationErrorPresenter.AuthenticationErrorView;
import stroom.security.identity.client.presenter.ChangePasswordPresenter;
import stroom.security.identity.client.presenter.ChangePasswordPresenter.ChangePasswordView;
import stroom.security.identity.client.presenter.CurrentPasswordPresenter;
import stroom.security.identity.client.presenter.CurrentPasswordPresenter.CurrentPasswordView;
import stroom.security.identity.client.presenter.EditAccountPresenter;
import stroom.security.identity.client.presenter.EditAccountPresenter.EditAccountView;
import stroom.security.identity.client.presenter.EmailResetPasswordPresenter;
import stroom.security.identity.client.presenter.EmailResetPasswordPresenter.EmailResetPasswordView;
import stroom.security.identity.client.presenter.LoginPresenter;
import stroom.security.identity.client.presenter.LoginPresenter.LoginProxy;
import stroom.security.identity.client.presenter.LoginPresenter.LoginView;
import stroom.security.identity.client.view.AccountsViewImpl;
import stroom.security.identity.client.view.AuthenticationErrorViewImpl;
import stroom.security.identity.client.view.ChangePasswordViewImpl;
import stroom.security.identity.client.view.CurrentPasswordViewImpl;
import stroom.security.identity.client.view.EditAccountViewImpl;
import stroom.security.identity.client.view.EmailResetPasswordViewImpl;
import stroom.security.identity.client.view.LoginViewImpl;

public class ChangePasswordModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(AccountsPlugin.class);
        bindPlugin(ChangePasswordPlugin.class);

        bindPresenter(LoginPresenter.class,
                LoginView.class,
                LoginViewImpl.class,
                LoginProxy.class);
        bindPresenter(AuthenticationErrorPresenter.class,
                AuthenticationErrorView.class,
                AuthenticationErrorViewImpl.class,
                AuthenticationErrorProxy.class);
        bindPresenterWidget(CurrentPasswordPresenter.class,
                CurrentPasswordView.class,
                CurrentPasswordViewImpl.class);
        bindPresenterWidget(ChangePasswordPresenter.class,
                ChangePasswordView.class,
                ChangePasswordViewImpl.class);
        bindPresenterWidget(EmailResetPasswordPresenter.class,
                EmailResetPasswordView.class,
                EmailResetPasswordViewImpl.class);

        bindPresenterWidget(AccountsPresenter.class,
                AccountsView.class,
                AccountsViewImpl.class);
        bindPresenterWidget(EditAccountPresenter.class,
                EditAccountView.class,
                EditAccountViewImpl.class);
    }
}
