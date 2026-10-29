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

package stroom.security.client.component;

import stroom.entity.client.component.LinkTabPanel;
import stroom.entity.client.component.TabbedController;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.shared.UserRef;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// A user's or group's tabs ([UserTabController]): their identity, groups, permissions,
/// dependencies and API keys, a tab each, made when it is first shown.
public class UserTabs extends TabbedController<UserRef> {

    private static final TabData INFO_TAB = TabDataImpl.builder()
            .withLabel("Info")
            .withTooltip("Basic identity information about the user/group.")
            .build();
    // Manage users only
    private static final TabData APP_PERMS_TAB = TabDataImpl.builder()
            .withLabel("Application Permissions")
            .withTooltip("Application level permissions for this user/group.")
            .build();
    private static final TabData DOC_PERMS_TAB = TabDataImpl.builder()
            .withLabel("Document Permissions")
            .withTooltip("Document level permissions for this user/group.")
            .build();
    private static final TabData USER_GROUPS_TAB = TabDataImpl.builder()
            .withLabel("User Groups")
            .withTooltip("Groups this user/group is a member of and members of this group (if applicable).")
            .build();
    private static final TabData DEPENDENCIES_TAB = TabDataImpl.builder()
            .withLabel("Dependencies")
            .withTooltip("Content items that depend on this user/group.")
            .build();
    private static final TabData API_KEYS_TAB = TabDataImpl.builder()
            .withLabel("API Keys")
            .withTooltip("API keys held by this user/group.")
            .build();

    /// Made by GIN, with its tabs, each made when it is first shown.
    ///
    /// @param eventBus                     The application's event bus.
    /// @param view                         The tab bar and the tab showing.
    /// @param userInfo                     The Info tab.
    /// @param securityContext              The user's permissions, which decide some of the tabs.
    /// @param userPermissionReportProvider Makes the Document Permissions tab.
    /// @param userDependenciesListProvider Makes the Dependencies tab.
    /// @param apiKeysProvider              Makes the API Keys tab.
    /// @param appPermissionsEditProvider   Makes the Application Permissions tab.
    /// @param userAndGroupsProvider        Makes the User Groups tab.
    @Inject
    public UserTabs(final EventBus eventBus,
                    final LinkTabPanel view,
                    final UserInfoTabController userInfo,
                    final ClientSecurityContext securityContext,
                    final Provider<UserPermissionReportController> userPermissionReportProvider,
                    final Provider<UserDependenciesListController> userDependenciesListProvider,
                    final Provider<ApiKeysController> apiKeysProvider,
                    final Provider<AppPermissionsEditController> appPermissionsEditProvider,
                    final Provider<UserAndGroupsController> userAndGroupsProvider) {
        super(eventBus, view);
        addOwnedTab(INFO_TAB, own(userInfo), (info, docRef, userRef) -> info.setUserRef(userRef));
        addTab(USER_GROUPS_TAB,
                userAndGroupsProvider::get,
                (userAndGroups, docRef, userRef) -> userAndGroups.setUserRef(userRef));
        // It was decided that a user should not see their own app perms.
        if (securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)) {
            addTab(APP_PERMS_TAB,
                    () -> appPermissionsEditProvider.get().attach(new AppPermissionsEdit()),
                    (appPermissionsEdit, docRef, userRef) -> appPermissionsEdit.setUserRef(userRef));
        }
        addTab(DOC_PERMS_TAB,
                userPermissionReportProvider::get,
                (report, docRef, userRef) -> report.setUserRef(userRef));
        addTab(DEPENDENCIES_TAB,
                userDependenciesListProvider::get,
                (dependencies, docRef, userRef) -> dependencies.setUserRef(userRef));
        if (securityContext.hasAppPermission(AppPermission.MANAGE_API_KEYS)) {
            addTab(API_KEYS_TAB,
                    apiKeysProvider::get,
                    (apiKeys, docRef, userRef) -> apiKeys.setOwner(userRef));
        }
        setDefaultTab(INFO_TAB);
        // The tab bar's menu (the tabs that don't fit) is shown by the app
        own(getTabView().getTabBar().addShowMenuHandler(event -> getEventBus().fireEvent(event)));
    }
}
