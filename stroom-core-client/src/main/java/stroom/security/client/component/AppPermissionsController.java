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

package stroom.security.client.component;

import stroom.content.client.component.ContentTabController;
import stroom.security.shared.AppUserPermissions;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import com.google.gwt.core.client.GWT;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Inject;

/// Shows users and groups and the selected one's application permissions, and changes them.
public class AppPermissionsController extends ContentTabController {

    public static final String TAB_TYPE = "ApplicationPermissions";

    private final AppUserPermissionsListController appUserPermissionsList;
    private final AppPermissionsEditController appPermissionsEdit;

    /// Made by GIN, with its screen and its parts.
    ///
    /// @param eventBus               The application's event bus.
    /// @param view                   Its component.
    /// @param appUserPermissionsList The app user permissions list.
    /// @param appPermissionsEdit     The app permissions edit.
    @Inject
    public AppPermissionsController(
            final EventBus eventBus,
            final AppPermissions view,
            final AppUserPermissionsListController appUserPermissionsList,
            final AppPermissionsEditController appPermissionsEdit) {

        super(eventBus);

        initWidget(view);
        this.appUserPermissionsList = own(appUserPermissionsList).attach(view.getAppUserPermissionsList());
        this.appPermissionsEdit = own(appPermissionsEdit).attach(view.getAppPermissionsEdit());

        appUserPermissionsList.setShowLevel(view.getShowLevel());

        own(appUserPermissionsList.getSelectionModel().addSelectionHandler(e -> {
            editPermissions();
        }));
        view.setOnShowLevelChange(showLevel -> {
            appUserPermissionsList.setShowLevel(showLevel);
            appUserPermissionsList.refresh();
        });

        appPermissionsEdit.setOnChange(change -> {
            GWT.log("valueChange, isRefreshUsers: " + change.isRefreshUsers()
                    + ", isRefreshDetails: " + change.isRefreshDetails());
            if (change.isRefreshUsers()) {
                refresh();
            }
            if (change.isRefreshDetails()) {
                appPermissionsEdit.updateDetails();
            }
        });
    }

    /// Filters the users and groups.
    ///
    /// @param filterInput The filter.
    public void setFilterInput(final String filterInput) {
        appUserPermissionsList.setQuickFilter(filterInput);
    }

    /// Shows a user's or group's permissions.
    ///
    /// @param userRef The user or group.
    public void showUser(final UserRef userRef) {
        appUserPermissionsList.showUser(userRef);
    }

    private void editPermissions() {
        final AppUserPermissions appUserPermissions = appUserPermissionsList.getSelectionModel()
                .getSelected();
        final UserRef userRef = NullSafe.get(appUserPermissions, AppUserPermissions::getUserRef);
        appPermissionsEdit.setUserRef(userRef);
    }

    /// Fetches the users and groups again.
    public void refresh() {
        appUserPermissionsList.refresh();
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.SHIELD;
    }

    @Override
    public String getLabel() {
        return "Application Permissions";
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
