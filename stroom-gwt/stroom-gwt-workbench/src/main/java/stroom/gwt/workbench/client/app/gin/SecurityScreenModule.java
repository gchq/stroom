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

package stroom.gwt.workbench.client.app.gin;

import stroom.security.client.presenter.ApiKeysPresenter;
import stroom.security.client.presenter.AppPermissionsPresenter;
import stroom.security.client.presenter.AppPermissionsPresenter.AppPermissionsView;
import stroom.security.client.presenter.BatchDocumentPermissionsPresenter;
import stroom.security.client.presenter.BatchDocumentPermissionsPresenter.BatchDocumentPermissionsView;
import stroom.security.client.presenter.DocumentUserCreatePermissionsEditPresenter;
import stroom.security.client.presenter.DocumentUserCreatePermissionsEditPresenter.DocumentUserCreatePermissionsEditView;
import stroom.security.client.presenter.DocumentUserPermissionsPresenter;
import stroom.security.client.presenter.DocumentUserPermissionsPresenter.DocumentUserPermissionsView;
import stroom.security.client.presenter.EditApiKeyPresenter;
import stroom.security.client.presenter.SigningKeyPresenter;
import stroom.security.client.presenter.UserAccessPresenter;
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
import stroom.security.client.view.AppPermissionsViewImpl;
import stroom.security.client.view.BatchDocumentPermissionsViewImpl;
import stroom.security.client.view.DocumentUserCreatePermissionsEditViewImpl;
import stroom.security.client.view.DocumentUserPermissionsViewImpl;
import stroom.security.client.view.EditApiKeyViewImpl;
import stroom.security.client.view.SigningKeyViewImpl;
import stroom.security.client.view.UserAccessViewImpl;
import stroom.security.client.view.UserAndGroupsViewImpl;
import stroom.security.client.view.UserInfoViewImpl;
import stroom.security.client.view.UserPermissionReportViewImpl;
import stroom.security.client.view.UsersViewImpl;
import stroom.widget.presenter.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `SecurityModule`
/// (`stroom/security/client/gin/SecurityModule.java`), without its plugins and app services.
/// A story shows a screen that Stroom opens on an app-wide event by adding a handler for
/// the event, as the screen's area plugin does in Stroom.
public class SecurityScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bindPresenterWidget(UsersPresenter.class,
                UsersView.class,
                UsersViewImpl.class);
        bindPresenterWidget(UserAndGroupsPresenter.class,
                UserAndGroupsView.class,
                UserAndGroupsViewImpl.class);
        bindPresenterWidget(AppPermissionsPresenter.class,
                AppPermissionsView.class,
                AppPermissionsViewImpl.class);
        bindPresenterWidget(DocumentUserPermissionsPresenter.class,
                DocumentUserPermissionsView.class,
                DocumentUserPermissionsViewImpl.class);
        bindPresenterWidget(DocumentUserCreatePermissionsEditPresenter.class,
                DocumentUserCreatePermissionsEditView.class,
                DocumentUserCreatePermissionsEditViewImpl.class);
        bindPresenterWidget(BatchDocumentPermissionsPresenter.class,
                BatchDocumentPermissionsView.class,
                BatchDocumentPermissionsViewImpl.class);
        bindPresenterWidget(UserPermissionReportPresenter.class,
                UserPermissionReportView.class,
                UserPermissionReportViewImpl.class);
        bind(UserTabPresenter.class);
        bindPresenterWidget(UserInfoPresenter.class,
                UserInfoView.class,
                UserInfoViewImpl.class);
        bindPresenterWidget(ApiKeysPresenter.class,
                ApiKeysPresenter.ApiKeysView.class,
                ApiKeysViewImpl.class);
        bindPresenterWidget(UserAccessPresenter.class,
                UserAccessPresenter.UserAccessView.class,
                UserAccessViewImpl.class);
        bindPresenterWidget(SigningKeyPresenter.class,
                SigningKeyPresenter.SigningKeyView.class,
                SigningKeyViewImpl.class);
        bindPresenterWidget(EditApiKeyPresenter.class,
                EditApiKeyPresenter.EditApiKeyView.class,
                EditApiKeyViewImpl.class);
    }
}
