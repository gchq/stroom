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

package stroom.security.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.item.client.SelectionBox;
import stroom.security.client.component.AppPermissionsEditController;
import stroom.security.client.component.AppUserPermissionsListController;
import stroom.security.client.presenter.AppPermissionsPresenter.AppPermissionsView;
import stroom.security.shared.AppUserPermissions;
import stroom.security.shared.PermissionShowLevel;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.widget.presenter.client.View;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Inject;

public class AppPermissionsPresenter
        extends ContentTabPresenter<AppPermissionsView> {

    public static final String TAB_TYPE = "ApplicationPermissions";

    private final AppUserPermissionsListController appUserPermissionsList;
    private final AppPermissionsEditController appPermissionsEdit;

    private final SelectionBox<PermissionShowLevel> permissionVisibility;

    @Inject
    public AppPermissionsPresenter(
            final EventBus eventBus,
            final AppPermissionsView view,
            final AppUserPermissionsListController appUserPermissionsList,
            final AppPermissionsEditController appPermissionsEdit) {

        super(eventBus, view);
        own(appUserPermissionsList);
        own(appPermissionsEdit);
        this.appUserPermissionsList = appUserPermissionsList;
        this.appPermissionsEdit = appPermissionsEdit;
        view.setAppUserPermissionListView(appUserPermissionsList);
        view.setAppPermissionsEditView(appPermissionsEdit);

        permissionVisibility = getView().getPermissionVisibility();
        permissionVisibility.addItems(PermissionShowLevel.ITEMS);
        permissionVisibility.setValue(PermissionShowLevel.SHOW_EXPLICIT);
        appUserPermissionsList.setShowLevel(permissionVisibility.getValue());

        own(appUserPermissionsList.getSelectionModel().addSelectionHandler(e -> {
            editPermissions();
        }));
        own(permissionVisibility.addValueChangeHandler(e -> {
            appUserPermissionsList.setShowLevel(permissionVisibility.getValue());
            appUserPermissionsList.refresh();
        }));

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

    public void setFilterInput(final String filterInput) {
        appUserPermissionsList.setQuickFilter(filterInput);
    }

    public void showUser(final UserRef userRef) {
        appUserPermissionsList.showUser(userRef);
    }

    private void editPermissions() {
        final AppUserPermissions appUserPermissions = appUserPermissionsList.getSelectionModel()
                .getSelected();
        final UserRef userRef = NullSafe.get(appUserPermissions, AppUserPermissions::getUserRef);
        appPermissionsEdit.setUserRef(userRef);
    }

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


    // --------------------------------------------------------------------------------


    public interface AppPermissionsView extends View {

        SelectionBox<PermissionShowLevel> getPermissionVisibility();

        void setAppUserPermissionListView(IsWidget view);

        void setAppPermissionsEditView(IsWidget view);
    }
}
