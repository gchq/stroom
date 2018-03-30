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
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.security.client.ClientSecurityContext;
import stroom.security.shared.ApplicationPermissionNames;
import stroom.security.shared.ChangeUserAction;
import stroom.security.shared.FetchUserAppPermissionsAction;
import stroom.security.shared.UserAppPermissions;
import stroom.security.shared.UserRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class AppPermissionsPresenter extends
        MyPresenterWidget<DataGridViewImpl<String>> {
    private final ClientDispatchAsync dispatcher;
    private final ClientSecurityContext securityContext;

    private UserAppPermissions userAppPermissions;

    private UserRef relatedUser;

    @Inject
    public AppPermissionsPresenter(final EventBus eventBus,
                                   final ClientDispatchAsync dispatcher, final ClientSecurityContext securityContext) {
        super(eventBus, new DataGridViewImpl<>());
        this.dispatcher = dispatcher;
        this.securityContext = securityContext;

        addColumns();
    }

    public void setUser(final UserRef userRef) {
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
            final FetchUserAppPermissionsAction fetchUserAppPermissionsAction = new FetchUserAppPermissionsAction(
                    relatedUser);
            dispatcher.exec(fetchUserAppPermissionsAction).onSuccess(userAppPermissions -> {
                AppPermissionsPresenter.this.userAppPermissions = userAppPermissions;

                final List<String> features = new ArrayList<>(
                        userAppPermissions.getAllPermissions());
                Collections.sort(features);
                getView().setRowData(0, features);
                getView().setRowCount(features.size(), true);
            });
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
                final Set<String> permissions = userAppPermissions.getUserPermissons();
                if (permissions != null && permissions.contains(permission)) {
                    return TickBoxState.fromBoolean(true);
                }

                return TickBoxState.fromBoolean(false);
            }
        };
        if (updateable) {
            selectionColumn.setFieldUpdater((index, permission, value) -> {
                final ChangeUserAction changeUserAction = new ChangeUserAction();
                changeUserAction.setUserRef(relatedUser);
                if (value.toBoolean()) {
                    changeUserAction.getChangedAppPermissions().add(permission);
                } else {
                    changeUserAction.getChangedAppPermissions().remove(permission);
                }
                dispatcher.exec(changeUserAction).onSuccess(result -> refresh());
            });
        }
        getView().addColumn(selectionColumn, "<br/>", 50);
    }

    private boolean isCurrentUserUpdate() {
        return securityContext.hasAppPermission(ApplicationPermissionNames.MANAGE_USERS_PERMISSION);
    }
}
