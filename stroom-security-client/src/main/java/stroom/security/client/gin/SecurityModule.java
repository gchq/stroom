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

import com.google.inject.Singleton;
import stroom.core.client.gin.PluginModule;
import stroom.entity.client.presenter.ManageEntityPresenter.ManageEntityView;
import stroom.entity.client.view.ManageEntityViewImpl;
import stroom.login.client.LoginManager;
import stroom.login.client.presenter.LoginPresenter;
import stroom.login.client.presenter.LogoutPlugin;
import stroom.login.client.view.LoginViewImpl;
import stroom.security.client.ClientSecurityContext;
import stroom.security.client.CurrentUser;
import stroom.security.client.ManageUserPlugin;
import stroom.security.client.presenter.ChangePasswordPlugin;
import stroom.security.client.presenter.ChangePasswordPresenter;
import stroom.security.client.presenter.ChangePasswordPresenter.ChangePasswordProxy;
import stroom.security.client.presenter.ChangePasswordPresenter.ChangePasswordView;
import stroom.security.client.presenter.DocumentPermissionsPresenter;
import stroom.security.client.presenter.DocumentPermissionsTabPresenter;
import stroom.security.client.presenter.FolderPermissionsTabPresenter;
import stroom.security.client.presenter.GroupEditPresenter;
import stroom.security.client.presenter.ResetPasswordPresenter;
import stroom.security.client.presenter.ResetPasswordPresenter.ResetPasswordProxy;
import stroom.security.client.presenter.ResetPasswordPresenter.ResetPasswordView;
import stroom.security.client.presenter.UserEditPresenter;
import stroom.security.client.presenter.UserListView;
import stroom.security.client.view.ChangePasswordViewImpl;
import stroom.security.client.view.DocumentPermissionsTabViewImpl;
import stroom.security.client.view.DocumentPermissionsViewImpl;
import stroom.security.client.view.FolderPermissionsTabViewImpl;
import stroom.security.client.view.ResetPasswordViewImpl;
import stroom.security.client.view.UserEditViewImpl;
import stroom.security.client.view.UserGroupEditViewImpl;
import stroom.security.client.view.UserListViewImpl;

public class SecurityModule extends PluginModule {
    @Override
    protected void configure() {
        bind(ClientSecurityContext.class).to(CurrentUser.class).in(Singleton.class);

        bind(LoginManager.class).in(Singleton.class);

        bindPresenter(LoginPresenter.class, LoginPresenter.LoginView.class, LoginViewImpl.class,
                LoginPresenter.LoginProxy.class);

        bindPlugin(LogoutPlugin.class);
        bindPlugin(ChangePasswordPlugin.class);

        bindPresenter(ChangePasswordPresenter.class, ChangePasswordView.class, ChangePasswordViewImpl.class,
                ChangePasswordProxy.class);
        bindPresenter(ResetPasswordPresenter.class, ResetPasswordView.class, ResetPasswordViewImpl.class,
                ResetPasswordProxy.class);

        bindSharedView(ManageEntityView.class, ManageEntityViewImpl.class);

        // Users
        bindPlugin(ManageUserPlugin.class);
        bindSharedView(UserListView.class, UserListViewImpl.class);
        bindSharedView(UserEditPresenter.UserEditView.class, UserEditViewImpl.class);
        bindSharedView(GroupEditPresenter.UserGroupEditView.class, UserGroupEditViewImpl.class);

        bindPresenterWidget(DocumentPermissionsPresenter.class, DocumentPermissionsPresenter.DocumentPermissionsView.class, DocumentPermissionsViewImpl.class);
        bindPresenterWidget(DocumentPermissionsTabPresenter.class, DocumentPermissionsTabPresenter.DocumentPermissionsTabView.class, DocumentPermissionsTabViewImpl.class);
        bindPresenterWidget(FolderPermissionsTabPresenter.class, FolderPermissionsTabPresenter.FolderPermissionsTabView.class, FolderPermissionsTabViewImpl.class);
    }
}
