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

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermissionResource;
import stroom.security.shared.ChangeSet;
import stroom.security.shared.ChangeUserRequest;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.User;
import stroom.security.shared.UserAndPermissions;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class AppPermissionsPresenter
        extends MyPresenterWidget<PagerView> {

    private static final AppPermissionResource APP_PERMISSION_RESOURCE = GWT.create(AppPermissionResource.class);

    private final RestFactory restFactory;
    private final ClientSecurityContext securityContext;
    private final MyDataGrid<String> dataGrid;

    private UserAndPermissions userAppPermissions;
    private List<String> allPermissions;

    private User relatedUser;

    @Inject
    public AppPermissionsPresenter(final EventBus eventBus,
                                   final PagerView view,
                                   final RestFactory restFactory,
                                   final ClientSecurityContext securityContext) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.securityContext = securityContext;

        dataGrid = new MyDataGrid<>();
        view.setDataWidget(dataGrid);

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
            dataGrid.setRowData(0, features);
            dataGrid.setRowCount(features.size(), true);

        } else {
            // Fetch permissions and populate table.
            restFactory
                    .create(APP_PERMISSION_RESOURCE)
                    .method(res -> res.fetchUserAppPermissions(relatedUser))
                    .onSuccess(userAppPermissions -> {
                        AppPermissionsPresenter.this.userAppPermissions = userAppPermissions;
                        updateAllPermissions();
                    })
                    .taskHandlerFactory(this)
                    .exec();
        }
    }

    private void updateAllPermissions() {
        if (this.allPermissions != null) {
            dataGrid.setRowData(0, allPermissions);
            dataGrid.setRowCount(allPermissions.size(), true);

        } else {
            restFactory
                    .create(APP_PERMISSION_RESOURCE)
                    .method(AppPermissionResource::fetchAllPermissions)
                    .onSuccess(allPermissions -> {
                        Collections.sort(allPermissions);
                        this.allPermissions = allPermissions;

                        dataGrid.setRowData(0, allPermissions);
                        dataGrid.setRowCount(allPermissions.size(), true);
                    })
                    .taskHandlerFactory(this)
                    .exec();
        }
    }

    private void addColumns() {
        final boolean updateable = isCurrentUserUpdate();
        final TickBoxCell.Appearance appearance = updateable
                ? new TickBoxCell.DefaultAppearance()
                : new TickBoxCell.NoBorderAppearance();

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
                final ChangeUserRequest request = new ChangeUserRequest(
                        relatedUser, new ChangeSet<>(), new ChangeSet<>());
                if (value.toBoolean()) {
                    request.getChangedAppPermissions().add(permission);
                } else {
                    request.getChangedAppPermissions().remove(permission);
                }

                restFactory
                        .create(APP_PERMISSION_RESOURCE)
                        .method(res -> res.changeUser(request))
                        .onSuccess(result -> refresh())
                        .taskHandlerFactory(this)
                        .exec();
            });
        }

        dataGrid.addColumn(selectionColumn, "<br/>", ColumnSizeConstants.ICON_COL);

        // Perm name
        dataGrid.addColumn(new Column<String, String>(new TextCell()) {
            @Override
            public String getValue(final String permissionName) {
                return permissionName;
            }
        }, "Permission", 200);

        // Description
        dataGrid.addColumn(new Column<String, String>(new TextCell()) {
            @Override
            public String getValue(final String permissionName) {
                return PermissionNames.getDescription(permissionName);
            }
        }, "Description", 700);
    }

    protected boolean isCurrentUserUpdate() {
        return securityContext.hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION);
    }
}
