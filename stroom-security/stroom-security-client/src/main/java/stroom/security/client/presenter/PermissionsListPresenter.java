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
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.table.client.MyCellTable;
import stroom.security.shared.Changes;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.DocumentPermissions;
import stroom.security.shared.User;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

public class PermissionsListPresenter
        extends MyPresenterWidget<PermissionsListPresenter.PermissionsListView> {

    private final CellTable<String> cellTable;

    private DocumentPermissions documentPermissions;
    private List<String> permissions;
    private Changes changes;
    private User currentUser;

    @Inject
    public PermissionsListPresenter(final EventBus eventBus, final PermissionsListView view) {
        super(eventBus, view);

        final boolean updateable = true;
        final TickBoxCell.Appearance appearance = updateable
                ? new TickBoxCell.DefaultAppearance()
                : new TickBoxCell.NoBorderAppearance();

        cellTable = new MyCellTable<>(DataGridViewImpl.DEFAULT_LIST_PAGE_SIZE);
        final Column<String, String> column = new Column<String, String>(new TextCell()) {
            @Override
            public String getValue(final String row) {
                return row;
            }
        };
        cellTable.addColumn(column);
        cellTable.setColumnWidth(column, 250, Unit.PX);

        // Selection.
        final Column<String, TickBoxState> selectionColumn = new Column<String, TickBoxState>(
                TickBoxCell.create(appearance, false, false, updateable)) {
            @Override
            public TickBoxState getValue(final String permission) {
                TickBoxState tickBoxState = TickBoxState.UNTICK;

                if (currentUser != null) {
                    final String userUuid = currentUser.getUuid();
                    final Set<String> permissions = documentPermissions.getPermissions().get(userUuid);
                    if (permissions != null) {
                        if (permissions.contains(permission)) {
                            tickBoxState = TickBoxState.TICK;

                        } else {
                            // If the user has a higher level of permission that is inferred by this level
                            // then indicate that with a half tick.
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
            selectionColumn.setFieldUpdater((index, permission, value) -> {
                if (currentUser != null) {
                    final String userUuid = currentUser.getUuid();
                    if (value.toBoolean()) {
                        addPermission(userUuid, permission);
                    } else {
                        removePermission(userUuid, permission);
                    }
                    refresh();
                }
            });
        }
        cellTable.addColumn(selectionColumn);
        cellTable.setColumnWidth(selectionColumn, 50, Unit.PX);
        cellTable.setWidth("auto");

        view.setTable(cellTable);
    }

    public void addPermission(final String userUuid, final String permission) {
        // Record the addition of a permission.
        changes.getAdd().computeIfAbsent(userUuid, k -> new HashSet<>()).add(permission);

        // Add to the model.
        documentPermissions.getPermissions().computeIfAbsent(userUuid, k -> new HashSet<>()).add(permission);
    }

    public void removePermission(final String userUuid, final String permission) {
        // Record the removal of a permission.
        changes.getRemove().computeIfAbsent(userUuid, k -> new HashSet<>()).add(permission);

        // Remove from the model.
        final Set<String> permissions = documentPermissions.getPermissions().get(userUuid);
        if (permissions != null) {
            permissions.remove(permission);
        }
    }

    public void setDocumentPermissions(final DocumentPermissions documentPermissions,
                                       final List<String> permissions,
                                       final Changes changes) {
        this.documentPermissions = documentPermissions;
        this.permissions = permissions;
        this.changes = changes;
    }

    public void setCurrentUser(User currentUser) {
        this.currentUser = currentUser;
        refresh();
    }

    private void refresh() {
        if (currentUser != null) {
            cellTable.setRowData(0, permissions);
            cellTable.setRowCount(permissions.size());
        } else {
            cellTable.setRowCount(0);
        }
    }

    public interface PermissionsListView extends View {

        void setTable(Widget widget);
    }
}
