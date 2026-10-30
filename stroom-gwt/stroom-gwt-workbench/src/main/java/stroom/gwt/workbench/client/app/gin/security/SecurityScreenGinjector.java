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

import stroom.credentials.client.component.CredentialsController;
import stroom.gwt.workbench.client.app.screen.ScreenGinjector;
import stroom.security.client.component.ApiKeysController;
import stroom.security.client.component.AppPermissionsController;
import stroom.security.client.component.BatchDocumentPermissionsController;
import stroom.security.client.component.DocumentCreatePermissionsListController;
import stroom.security.client.component.DocumentUserPermissionsContentTabController;
import stroom.security.client.component.SigningKeysController;
import stroom.security.client.component.UserAccessController;
import stroom.security.client.component.UserInfoTabController;
import stroom.security.client.component.UserTabController;
import stroom.security.client.component.UsersAndGroupsTabController;
import stroom.security.identity.client.component.AccountEditorController;
import stroom.security.identity.client.component.AccountsController;
import stroom.security.identity.client.component.ChangePasswordController;

import com.google.gwt.inject.client.GinModules;

/// The `security` batch's [ScreenGinjector]: Stroom's security, identity (accounts and passwords)
/// and credentials screens, created as Stroom's GIN modules create them, for the stories in
/// `client.app.main` and `client.app.security` that show them:
/// ```
/// final SecurityScreenGinjector injector = GWT.create(SecurityScreenGinjector.class);
/// final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES).injector(injector).build();
/// final SigningKeysController screen = injector.getSigningKeysController();
/// ```
/// Create one each time the story renders: its singletons belong to that rendering's harness.
///
/// It lists the shared base modules only (as `AppScreenGinjector` does): the screens are controllers,
/// which need no bindings.
@GinModules({
})
public interface SecurityScreenGinjector extends ScreenGinjector {

    // Accounts and passwords (the internal identity provider)

    /// @return The 'Manage Accounts' tab.
    AccountsController getAccountsController();

    /// @return A new 'Edit Account'/'Create Account' dialog.
    AccountEditorController getEditAccountController();

    /// @return A new 'Change Password' dialog, as `CurrentPasswordController` shows it once the
    /// current password is confirmed.
    ChangePasswordController getChangePasswordController();

    /// @return The 'Signing Keys' tab.
    SigningKeysController getSigningKeysController();

    // Users, groups and their permissions

    /// @return The 'User Access' tab.
    UserAccessController getUserAccessController();

    /// @return The 'User Groups' tab (also a user tab's 'User Groups' sub-tab).
    UsersAndGroupsTabController getUsersAndGroupsTabController();

    /// @return A user's tab, as `UserTabPlugin` and `UserPlugin` ('User Profile') open it.
    UserTabController getUserTabController();

    /// @return A user tab's 'Info' sub-tab.
    UserInfoTabController getUserInfoTabController();

    /// @return The 'Application Permissions' tab.
    AppPermissionsController getAppPermissionsController();

    /// @return The 'Manage API Keys' tab.
    ApiKeysController getApiKeysController();

    // Document permissions

    /// @return The 'Document Permissions' tab (batch changes for the filtered documents).
    BatchDocumentPermissionsController getBatchDocumentPermissionsController();

    /// @return A document's 'Permissions' tab, as `DocumentPermissionsPlugin` opens it.
    DocumentUserPermissionsContentTabController getDocumentUserPermissionsContentTabController();

    /// @return The grid of document types a user may create in a folder.
    DocumentCreatePermissionsListController getDocumentCreatePermissionsListController();

    // Credentials

    /// @return The 'Credentials' tab.
    CredentialsController getCredentialsController();
}
