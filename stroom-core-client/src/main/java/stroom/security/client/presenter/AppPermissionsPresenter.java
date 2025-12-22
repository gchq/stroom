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

package stroom.security.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.item.client.SelectionBox;
import stroom.security.client.presenter.AppPermissionsPresenter.AppPermissionsView;
import stroom.security.shared.AppUserPermissions;
import stroom.security.shared.PermissionShowLevel;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import com.google.gwt.core.client.GWT;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import javax.inject.Inject;

public class AppPermissionsPresenter
        extends ContentTabPresenter<AppPermissionsView> {

    private final AppUserPermissionsListPresenter appUserPermissionsListPresenter;
    private final AppPermissionsEditPresenter appPermissionsEditPresenter;

    private final SelectionBox<PermissionShowLevel> permissionVisibility;

    @Inject
    public AppPermissionsPresenter(
            final EventBus eventBus,
            final AppPermissionsView view,
            final AppUserPermissionsListPresenter appUserPermissionsListPresenter,
            final AppPermissionsEditPresenter appPermissionsEditPresenter) {

        super(eventBus, view);
        this.appUserPermissionsListPresenter = appUserPermissionsListPresenter;
        this.appPermissionsEditPresenter = appPermissionsEditPresenter;
        view.setAppUserPermissionListView(appUserPermissionsListPresenter.getView());
        view.setAppPermissionsEditView(appPermissionsEditPresenter.getView());

        permissionVisibility = getView().getPermissionVisibility();
        permissionVisibility.addItems(PermissionShowLevel.ITEMS);
        permissionVisibility.setValue(PermissionShowLevel.SHOW_EXPLICIT);
        appUserPermissionsListPresenter.setShowLevel(permissionVisibility.getValue());
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(appUserPermissionsListPresenter.getSelectionModel().addSelectionHandler(e -> {
            editPermissions();
        }));
        registerHandler(permissionVisibility.addValueChangeHandler(e -> {
            appUserPermissionsListPresenter.setShowLevel(permissionVisibility.getValue());
            appUserPermissionsListPresenter.refresh();
        }));
        registerHandler(appPermissionsEditPresenter.getSelectionModel()
                .addSelectionHandler(e -> appPermissionsEditPresenter.updateDetails()));

        registerHandler(appPermissionsEditPresenter.addValueChangeHandler(e -> {
            GWT.log("valueChange, isRefreshUsers: " + e.getValue().isRefreshUsers()
                    + ", isRefreshDetails: " + e.getValue().isRefreshDetails());
            if (e.getValue().isRefreshUsers()) {
                refresh();
            }
            if (e.getValue().isRefreshDetails()) {
                appPermissionsEditPresenter.updateDetails();
            }
        }));
    }

    public void setFilterInput(final String filterInput) {
        appUserPermissionsListPresenter.setQuickFilter(filterInput);
    }

    public void showUser(final UserRef userRef) {
        appUserPermissionsListPresenter.showUser(userRef);
    }

    private void editPermissions() {
        final AppUserPermissions appUserPermissions = appUserPermissionsListPresenter.getSelectionModel()
                .getSelected();
        final UserRef userRef = NullSafe.get(appUserPermissions, AppUserPermissions::getUserRef);
        appPermissionsEditPresenter.setUserRef(userRef);
    }

    public void refresh() {
        appUserPermissionsListPresenter.refresh();
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
        return "ApplicationPermissions";
    }


    // --------------------------------------------------------------------------------


    public interface AppPermissionsView extends View {

        SelectionBox<PermissionShowLevel> getPermissionVisibility();

        void setAppUserPermissionListView(View view);

        void setAppPermissionsEditView(View view);
    }
}
