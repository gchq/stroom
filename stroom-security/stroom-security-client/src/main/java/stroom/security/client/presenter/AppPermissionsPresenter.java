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

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermissionResource;
import stroom.security.shared.ChangeSet;
import stroom.security.shared.ChangeUserRequest;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.User;
import stroom.security.shared.UserAndPermissions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class AppPermissionsPresenter extends
        MyPresenterWidget<DataGridViewImpl<String>> {
    private final AppPermissionResource APP_PERMISSION_RESOURCE = GWT.create(AppPermissionResource.class);

    private final RestFactory restFactory;
    private final ClientSecurityContext securityContext;

    private UserAndPermissions userAppPermissions;
    private List<String> allPermissions;

    private User relatedUser;

    @Inject
    public AppPermissionsPresenter(final EventBus eventBus,
                                   final RestFactory restFactory, final ClientSecurityContext securityContext) {
        super(eventBus, new DataGridViewImpl<>());
        this.restFactory = restFactory;
        this.securityContext = securityContext;

        addColumns();
    }

    public void setUser(final User userRef) {
        this.relatedUser = userRef;
        refresh();
    }

    private void refresh() {
        if (relatedUser == null) {
            userAppPermissions = null;
            final List<String> features = new ArrayList<>();
            getView().setRowData(0, features);
            getView().setRowCount(features.size(), true);

        } else {
            // Fetch permissions and populate table.
            final Rest<UserAndPermissions> rest = restFactory.create();
            rest
                    .onSuccess(userAppPermissions -> {
                        AppPermissionsPresenter.this.userAppPermissions = userAppPermissions;
                        updateAllPermissions();
                    })
                    .call(APP_PERMISSION_RESOURCE)
                    .fetchUserAppPermissions(relatedUser);
        }
    }

    private void updateAllPermissions() {
        if (this.allPermissions != null) {
            getView().setRowData(0, allPermissions);
            getView().setRowCount(allPermissions.size(), true);

        } else {
            final Rest<List<String>> rest = restFactory.create();
            rest
                    .onSuccess(allPermissions -> {
                        Collections.sort(allPermissions);
                        this.allPermissions = allPermissions;

                        getView().setRowData(0, allPermissions);
                        getView().setRowCount(allPermissions.size(), true);
                    })
                    .call(APP_PERMISSION_RESOURCE)
                    .fetchAllPermissions();
        }
    }

    private void addColumns() {
        final boolean updateable = isCurrentUserUpdate();
        final TickBoxCell.Appearance appearance = updateable ? new TickBoxCell.DefaultAppearance() : new TickBoxCell.NoBorderAppearance();

        getView().addColumn(new Column<String, String>(new TextCell()) {
            @Override
            public String getValue(final String row) {
                return row;
            }
        }, "Permission", 200);

        // Selection.
        final Column<String, TickBoxState> selectionColumn = new Column<String, TickBoxState>(
                TickBoxCell.create(appearance, true, true, updateable)) {
            @Override
            public TickBoxState getValue(final String permission) {
                final Set<String> permissions = userAppPermissions.getPermissions();
                if (permissions != null && permissions.contains(permission)) {
                    return TickBoxState.fromBoolean(true);
                }

                return TickBoxState.fromBoolean(false);
            }
        };
        if (updateable) {
            selectionColumn.setFieldUpdater((index, permission, value) -> {
                final ChangeUserRequest request = new ChangeUserRequest(relatedUser, new ChangeSet<>(), new ChangeSet<>());
                if (value.toBoolean()) {
                    request.getChangedAppPermissions().add(permission);
                } else {
                    request.getChangedAppPermissions().remove(permission);
                }

                final Rest<Boolean> rest = restFactory.create();
                rest
                        .onSuccess(result -> refresh())
                        .call(APP_PERMISSION_RESOURCE)
                        .changeUser(request);
            });
        }
        getView().addColumn(selectionColumn, "<br/>", 50);
    }

    protected boolean isCurrentUserUpdate() {
        return securityContext.hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION);
    }
}
