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

import com.google.gwt.inject.client.AsyncProvider;
import com.google.gwt.inject.client.Ginjector;
import com.google.inject.Provider;
import stroom.login.client.LoginManager;
import stroom.login.client.presenter.LoginPresenter;
import stroom.security.client.ClientSecurityContext;
import stroom.security.client.presenter.ChangePasswordPresenter;
import stroom.security.client.presenter.DocumentPermissionsPresenter;
import stroom.security.client.presenter.ResetPasswordPresenter;
import stroom.security.client.presenter.UsersAndGroupsPresenter;

public interface SecurityGinjector extends Ginjector {
    LoginManager getLoginManager();

    AsyncProvider<LoginPresenter> getLoginPresenter();

    AsyncProvider<ChangePasswordPresenter> getChangePasswordPresenter();

    AsyncProvider<ResetPasswordPresenter> getResetPasswordPresenter();

    AsyncProvider<UsersAndGroupsPresenter> getUsersAndGroupsPresenter();

    AsyncProvider<DocumentPermissionsPresenter> getDocumentPermissionsPresenter();

    Provider<ClientSecurityContext> getSecurityContext();
}
