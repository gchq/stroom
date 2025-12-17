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

package stroom.security.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.security.client.ApiKeysPlugin;
import stroom.security.client.AppPermissionsPlugin;
import stroom.security.client.CurrentUser;
import stroom.security.client.DocumentPermissionsPlugin;
import stroom.security.client.LoginManager;
import stroom.security.client.LogoutPlugin;
import stroom.security.client.UserPlugin;
import stroom.security.client.UserTabPlugin;
import stroom.security.client.UsersAndGroupsPlugin;
import stroom.security.client.UsersPlugin;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.ApiKeysPresenter;
import stroom.security.client.presenter.AppPermissionsEditPresenter;
import stroom.security.client.presenter.AppPermissionsEditPresenter.AppPermissionsEditView;
import stroom.security.client.presenter.AppPermissionsPresenter;
import stroom.security.client.presenter.AppPermissionsPresenter.AppPermissionsView;
import stroom.security.client.presenter.BatchDocumentPermissionsEditPresenter;
import stroom.security.client.presenter.BatchDocumentPermissionsEditPresenter.BatchDocumentPermissionsEditView;
import stroom.security.client.presenter.BatchDocumentPermissionsPresenter;
import stroom.security.client.presenter.BatchDocumentPermissionsPresenter.BatchDocumentPermissionsView;
import stroom.security.client.presenter.CreateExternalUserPresenter;
import stroom.security.client.presenter.CreateExternalUserPresenter.CreateExternalUserView;
import stroom.security.client.presenter.CreateMultipleUsersPresenter;
import stroom.security.client.presenter.CreateMultipleUsersPresenter.CreateMultipleUsersView;
import stroom.security.client.presenter.CreateUserPresenter;
import stroom.security.client.presenter.CreateUserPresenter.CreateUserView;
import stroom.security.client.presenter.DocumentCreatePermissionsListPresenter;
import stroom.security.client.presenter.DocumentCreatePermissionsListPresenter.DocumentCreatePermissionsListView;
import stroom.security.client.presenter.DocumentUserCreatePermissionsEditPresenter;
import stroom.security.client.presenter.DocumentUserCreatePermissionsEditPresenter.DocumentUserCreatePermissionsEditView;
import stroom.security.client.presenter.DocumentUserPermissionsEditPresenter;
import stroom.security.client.presenter.DocumentUserPermissionsEditPresenter.DocumentUserPermissionsEditView;
import stroom.security.client.presenter.DocumentUserPermissionsPresenter;
import stroom.security.client.presenter.DocumentUserPermissionsPresenter.DocumentUserPermissionsView;
import stroom.security.client.presenter.EditApiKeyPresenter;
import stroom.security.client.presenter.UserAndGroupsPresenter;
import stroom.security.client.presenter.UserAndGroupsPresenter.UserAndGroupsView;
import stroom.security.client.presenter.UserInfoPresenter;
import stroom.security.client.presenter.UserInfoPresenter.UserInfoView;
import stroom.security.client.presenter.UserPermissionReportPresenter;
import stroom.security.client.presenter.UserPermissionReportPresenter.UserPermissionReportView;
import stroom.security.client.presenter.UserTabPresenter;
import stroom.security.client.presenter.UsersPresenter;
import stroom.security.client.presenter.UsersPresenter.UsersView;
import stroom.security.client.view.ApiKeysViewImpl;
import stroom.security.client.view.AppPermissionsEditViewImpl;
import stroom.security.client.view.AppPermissionsViewImpl;
import stroom.security.client.view.BatchDocumentPermissionsEditViewImpl;
import stroom.security.client.view.BatchDocumentPermissionsViewImpl;
import stroom.security.client.view.CreateExternalUserViewImpl;
import stroom.security.client.view.CreateMultipleUsersViewImpl;
import stroom.security.client.view.CreateUserViewImpl;
import stroom.security.client.view.DocumentCreatePermissionsListViewImpl;
import stroom.security.client.view.DocumentUserCreatePermissionsEditViewImpl;
import stroom.security.client.view.DocumentUserPermissionsEditViewImpl;
import stroom.security.client.view.DocumentUserPermissionsViewImpl;
import stroom.security.client.view.EditApiKeyViewImpl;
import stroom.security.client.view.UserAndGroupsViewImpl;
import stroom.security.client.view.UserInfoViewImpl;
import stroom.security.client.view.UserPermissionReportViewImpl;
import stroom.security.client.view.UsersViewImpl;
import stroom.widget.dropdowntree.client.view.QuickFilterDialogView;
import stroom.widget.dropdowntree.client.view.QuickFilterDialogViewImpl;
import stroom.widget.dropdowntree.client.view.QuickFilterPageView;
import stroom.widget.dropdowntree.client.view.QuickFilterPageViewImpl;

import com.google.inject.Singleton;

public class SecurityModule extends PluginModule {

    @Override
    protected void configure() {
        bind(ClientSecurityContext.class).to(CurrentUser.class).in(Singleton.class);

        bind(LoginManager.class).in(Singleton.class);

        bindPlugin(LogoutPlugin.class);

        // Users
        bindPlugin(AppPermissionsPlugin.class);
        bindPlugin(DocumentPermissionsPlugin.class);
        bindPlugin(UserPlugin.class);
        bindPlugin(UsersPlugin.class);
        bindPlugin(UserTabPlugin.class);
        bindPlugin(UsersAndGroupsPlugin.class);
//        bindPlugin(UserPermissionsReportPlugin.class);
        bindPlugin(ApiKeysPlugin.class);
        bindSharedView(QuickFilterDialogView.class, QuickFilterDialogViewImpl.class);
        bindSharedView(QuickFilterPageView.class, QuickFilterPageViewImpl.class);
        bindPresenterWidget(
                UsersPresenter.class,
                UsersView.class,
                UsersViewImpl.class);
        bindPresenterWidget(
                UserAndGroupsPresenter.class,
                UserAndGroupsView.class,
                UserAndGroupsViewImpl.class);
        bindPresenterWidget(
                CreateUserPresenter.class,
                CreateUserView.class,
                CreateUserViewImpl.class);
        bindPresenterWidget(
                CreateExternalUserPresenter.class,
                CreateExternalUserView.class,
                CreateExternalUserViewImpl.class);
        bindPresenterWidget(
                CreateMultipleUsersPresenter.class,
                CreateMultipleUsersView.class,
                CreateMultipleUsersViewImpl.class);

        bindPresenterWidget(
                AppPermissionsPresenter.class,
                AppPermissionsView.class,
                AppPermissionsViewImpl.class);
        bindPresenterWidget(
                AppPermissionsEditPresenter.class,
                AppPermissionsEditView.class,
                AppPermissionsEditViewImpl.class);

        bindPresenterWidget(
                DocumentUserPermissionsPresenter.class,
                DocumentUserPermissionsView.class,
                DocumentUserPermissionsViewImpl.class);
        bindPresenterWidget(
                DocumentUserPermissionsEditPresenter.class,
                DocumentUserPermissionsEditView.class,
                DocumentUserPermissionsEditViewImpl.class);
        bindPresenterWidget(
                DocumentUserCreatePermissionsEditPresenter.class,
                DocumentUserCreatePermissionsEditView.class,
                DocumentUserCreatePermissionsEditViewImpl.class);
        bindPresenterWidget(
                DocumentCreatePermissionsListPresenter.class,
                DocumentCreatePermissionsListView.class,
                DocumentCreatePermissionsListViewImpl.class);
        bindPresenterWidget(
                BatchDocumentPermissionsPresenter.class,
                BatchDocumentPermissionsView.class,
                BatchDocumentPermissionsViewImpl.class);
        bindPresenterWidget(
                BatchDocumentPermissionsEditPresenter.class,
                BatchDocumentPermissionsEditView.class,
                BatchDocumentPermissionsEditViewImpl.class);
        bindPresenterWidget(
                UserPermissionReportPresenter.class,
                UserPermissionReportView.class,
                UserPermissionReportViewImpl.class);
        bind(UserTabPresenter.class);
        bindPresenterWidget(
                UserInfoPresenter.class,
                UserInfoView.class,
                UserInfoViewImpl.class);

        bindPresenterWidget(
                ApiKeysPresenter.class,
                ApiKeysPresenter.ApiKeysView.class,
                ApiKeysViewImpl.class);
        bindPresenterWidget(
                EditApiKeyPresenter.class,
                EditApiKeyPresenter.EditApiKeyView.class,
                EditApiKeyViewImpl.class);
    }
}
