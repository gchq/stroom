/*
 * Copyright 2016 Crown Copyright
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

package stroom.security.client.gin;

import stroom.login.client.LoginManager;
import stroom.login.client.presenter.LoginPresenter;
import stroom.login.client.presenter.LogoutPlugin;
import stroom.security.client.ManageUserPlugin;
import stroom.security.client.presenter.ChangePasswordPlugin;
import stroom.security.client.presenter.ChangePasswordPresenter;
import stroom.security.client.presenter.DocumentPermissionsPresenter;
import stroom.security.client.presenter.ResetPasswordPresenter;
import stroom.security.client.presenter.UsersAndGroupsPresenter;
import com.google.gwt.inject.client.AsyncProvider;
import com.google.gwt.inject.client.Ginjector;

public interface SecurityGinjector extends Ginjector {
    LoginManager getLoginManager();

    AsyncProvider<LoginPresenter> getLoginPresenter();

    AsyncProvider<LogoutPlugin> getLogoutPlugin();

    AsyncProvider<ChangePasswordPlugin> getChangePasswordPlugin();

    AsyncProvider<ChangePasswordPresenter> getChangePasswordPresenter();

    AsyncProvider<ResetPasswordPresenter> getResetPasswordPresenter();

    AsyncProvider<ManageUserPlugin> getManageUserPlugin();

    AsyncProvider<UsersAndGroupsPresenter> getUsersAndGroupsPresenter();

    AsyncProvider<DocumentPermissionsPresenter> getDocumentPermissionsPresenter();
}
