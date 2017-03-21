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

import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.table.client.CellTableView;
import stroom.data.table.client.CellTableViewImpl;
import stroom.security.shared.ChangeSet;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.DocumentPermissions;
import stroom.security.shared.UserPermission;
import stroom.security.shared.UserRef;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PermissionsListPresenter
        extends MyPresenterWidget<CellTableView<String>> {
    private DocumentPermissions documentPermissions;
    private List<String> permissions;
    private ChangeSet<UserPermission> changeSet;
    private UserRef currentUser;

    @Inject
    public PermissionsListPresenter(final EventBus eventBus) {
        super(eventBus, new CellTableViewImpl<String>(false, (CellTable.Resources) GWT.create(CellTableViewImpl.BasicResources.class)));

        final boolean updateable = true;//isCurrentUserUpdate();
        final TickBoxCell.Appearance appearance = updateable ? new TickBoxCell.DefaultAppearance() : new TickBoxCell.NoBorderAppearance();

        getView().addColumn(new Column<String, String>(new TextCell()) {
            @Override
            public String getValue(final String row) {
                return row;
            }
        }, 250);

        // Selection.
        final Column<String, TickBoxState> selectionColumn = new Column<String, TickBoxState>(
                TickBoxCell.create(appearance, false, false, updateable)) {
            @Override
            public TickBoxState getValue(final String permission) {
                TickBoxState tickBoxState = TickBoxState.UNTICK;

                if (currentUser != null) {
                    final Set<String> permissions = documentPermissions.getUserPermissions().get(currentUser);
                    if (permissions != null) {
                        if (permissions.contains(permission)) {
                            tickBoxState = TickBoxState.TICK;

                        } else {
                            // If the user has a higher level of permission that is inferred by this level then indicate that with a half tick.
                            String higherPermission = DocumentPermissionNames.getHigherPermission(permission);
                            boolean inferred = false;

                            while (higherPermission != null && !inferred) {
                                inferred = permissions.contains(higherPermission);
                                higherPermission = DocumentPermissionNames.getHigherPermission(higherPermission);
                            }

                            if (inferred) {
                                tickBoxState = TickBoxState.HALF_TICK;
                            }
                        }
                    }
                }

                return tickBoxState;
            }
        };
        if (updateable) {
            selectionColumn.setFieldUpdater(new FieldUpdater<String, TickBoxState>() {
                @Override
                public void update(final int index, final String permission, final TickBoxState value) {
                    if (currentUser != null) {
                        if (value.toBoolean()) {
                            addPermission(currentUser, permission);
                        } else {
                            removePermission(currentUser, permission);
                        }
                        refresh();
                    }
                }
            });
        }
        getView().addColumn(selectionColumn, 50);
        getView().asWidget().setWidth("auto");
    }

    public void addPermission(final UserRef userRef, final String permission) {
        final UserPermission userPermission = new UserPermission(userRef, permission);

        // Record the addition of a permission.
        changeSet.add(userPermission);

        // Add to the model.
        documentPermissions.getUserPermissions().computeIfAbsent(userRef, k -> new HashSet<>()).add(permission);
    }

    public void removePermission(final UserRef userRef, final String permission) {
        final UserPermission userPermission = new UserPermission(userRef, permission);

        // Record the removal of a permission.
        changeSet.remove(userPermission);

        // Remove from the model.
        final Set<String> permissions = documentPermissions.getUserPermissions().get(userRef);
        if (permissions != null) {
            permissions.remove(permission);
        }
    }

    public void setDocumentPermissions(final DocumentPermissions documentPermissions, final List<String> permissions, final ChangeSet<UserPermission> changeSet) {
        this.documentPermissions = documentPermissions;
        this.permissions = permissions;
        this.changeSet = changeSet;
    }

    public void setCurrentUser(UserRef currentUser) {
        this.currentUser = currentUser;
        refresh();
    }

    private void refresh() {
        if (currentUser != null) {
            getView().setRowData(0, permissions);
            getView().setRowCount(permissions.size());
        } else {
            getView().setRowCount(0);
        }
    }
}
