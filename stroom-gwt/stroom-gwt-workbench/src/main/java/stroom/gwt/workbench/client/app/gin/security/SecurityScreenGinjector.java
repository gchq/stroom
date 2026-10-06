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

package stroom.gwt.workbench.client.app.gin.security;

import stroom.credentials.client.presenter.CredentialsPresenter;
import stroom.gwt.workbench.client.app.gin.CredentialsScreenModule;
import stroom.gwt.workbench.client.app.gin.EntityScreenModule;
import stroom.gwt.workbench.client.app.gin.ExplorerScreenModule;
import stroom.gwt.workbench.client.app.gin.ScreenViewsModule;
import stroom.gwt.workbench.client.app.gin.SecurityScreenModule;
import stroom.gwt.workbench.client.app.screen.ScreenGinjector;
import stroom.security.client.presenter.ApiKeysPresenter;
import stroom.security.client.presenter.AppPermissionsPresenter;
import stroom.security.client.presenter.BatchDocumentPermissionsPresenter;
import stroom.security.client.presenter.DocumentCreatePermissionsListPresenter;
import stroom.security.client.presenter.DocumentUserPermissionsPresenter;
import stroom.security.client.presenter.SigningKeyPresenter;
import stroom.security.client.presenter.UserAccessPresenter;
import stroom.security.client.presenter.UserAndGroupsPresenter;
import stroom.security.client.presenter.UserInfoPresenter;
import stroom.security.client.presenter.UserTabPresenter;
import stroom.security.identity.client.presenter.AccountsPresenter;
import stroom.security.identity.client.presenter.ChangePasswordPresenter;
import stroom.security.identity.client.presenter.EditAccountPresenter;

import com.google.gwt.inject.client.GinModules;

/// The `security` batch's [ScreenGinjector]: Stroom's security, identity (accounts and passwords)
/// and credentials screens, created as Stroom's GIN modules create them, for the stories in
/// `client.app.main` and `client.app.security` that show them:
/// ```
/// final SecurityScreenGinjector injector = GWT.create(SecurityScreenGinjector.class);
/// final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES).injector(injector).build();
/// final SigningKeyPresenter presenter = injector.getSigningKeyPresenter();
/// ```
/// Create one each time the story renders: its singletons belong to that rendering's harness.
///
/// It lists the shared base modules (as `AppScreenGinjector` does) and [IdentityScreenModule],
/// the mirror of Stroom's `ChangePasswordModule`.
@GinModules({
        ScreenViewsModule.class,
        CredentialsScreenModule.class,
        EntityScreenModule.class,
        ExplorerScreenModule.class,
        IdentityScreenModule.class,
        SecurityScreenModule.class,
})
public interface SecurityScreenGinjector extends ScreenGinjector {

    // Accounts and passwords (the internal identity provider)

    /// @return The 'Manage Accounts' tab.
    AccountsPresenter getAccountsPresenter();

    /// @return A new 'Edit Account'/'Create Account' dialog.
    EditAccountPresenter getEditAccountPresenter();

    /// @return A new 'Change Password' dialog, as `CurrentPasswordPresenter` shows it once the
    /// current password is confirmed.
    ChangePasswordPresenter getChangePasswordPresenter();

    /// @return The 'Signing Keys' tab.
    SigningKeyPresenter getSigningKeyPresenter();

    // Users, groups and their permissions

    /// @return The 'User Access' tab.
    UserAccessPresenter getUserAccessPresenter();

    /// @return The 'User Groups' tab (also a user tab's 'User Groups' sub-tab).
    UserAndGroupsPresenter getUserAndGroupsPresenter();

    /// @return A user's tab, as `UserTabPlugin` and `UserPlugin` ('User Profile') open it.
    UserTabPresenter getUserTabPresenter();

    /// @return A user tab's 'Info' sub-tab.
    UserInfoPresenter getUserInfoPresenter();

    /// @return The 'Application Permissions' tab.
    AppPermissionsPresenter getAppPermissionsPresenter();

    /// @return The 'Manage API Keys' tab.
    ApiKeysPresenter getApiKeysPresenter();

    // Document permissions

    /// @return The 'Document Permissions' tab (batch changes for the filtered documents).
    BatchDocumentPermissionsPresenter getBatchDocumentPermissionsPresenter();

    /// @return A document's 'Permissions' tab, as `DocumentPermissionsPlugin` opens it.
    DocumentUserPermissionsPresenter getDocumentUserPermissionsPresenter();

    /// @return The grid of document types a user may create in a folder.
    DocumentCreatePermissionsListPresenter getDocumentCreatePermissionsListPresenter();

    // Credentials

    /// @return The 'Credentials' tab.
    CredentialsPresenter getCredentialsPresenter();
}
