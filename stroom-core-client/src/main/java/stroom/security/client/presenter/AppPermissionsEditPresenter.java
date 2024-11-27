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
import stroom.data.grid.client.DataGridSelectionEventManager;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.AppPermissionsEditPresenter.AppPermissionsEditView;
import stroom.security.client.presenter.AppPermissionsEditPresenter.ValueChange;
import stroom.security.shared.AbstractAppPermissionChange;
import stroom.security.shared.AbstractAppPermissionChange.AddAppPermission;
import stroom.security.shared.AbstractAppPermissionChange.RemoveAppPermission;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppPermissionResource;
import stroom.security.shared.AppUserPermissionsReport;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.UserRef;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.event.shared.LegacyHandlerWrapper;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AppPermissionsEditPresenter
        extends MyPresenterWidget<AppPermissionsEditView>
        implements HasValueChangeHandlers<ValueChange> {

    private static final AppPermissionResource APP_PERMISSION_RESOURCE = GWT.create(AppPermissionResource.class);

    private final RestFactory restFactory;
    private final ClientSecurityContext securityContext;
    private final MyDataGrid<AppPermission> dataGrid;
    private final MultiSelectionModelImpl<AppPermission> selectionModel;

    private AppUserPermissionsReport currentPermissions;
    private List<AppPermission> allPermissions;

    private UserRef relatedUser;

    @Inject
    public AppPermissionsEditPresenter(final EventBus eventBus,
                                       final AppPermissionsEditView view,
                                       final PagerView pagerView,
                                       final RestFactory restFactory,
                                       final ClientSecurityContext securityContext) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.securityContext = securityContext;

        dataGrid = new MyDataGrid<>();
        selectionModel = new MultiSelectionModelImpl<>(dataGrid);
        final DataGridSelectionEventManager<AppPermission> selectionEventManager = new DataGridSelectionEventManager<>(
                dataGrid,
                selectionModel,
                false);
        dataGrid.setSelectionModel(selectionModel, selectionEventManager);
        pagerView.setDataWidget(dataGrid);
        view.setPermissionsView(pagerView);

        addColumns();
    }

    public MultiSelectionModelImpl<AppPermission> getSelectionModel() {
        return selectionModel;
    }

    public void edit(final UserRef userRef) {
        this.relatedUser = userRef;
        refresh();
    }

    private void refresh() {
        if (relatedUser == null) {
            currentPermissions = null;
            // Update details panel.
            ValueChangeEvent.fire(this, new ValueChange(false, true));
            final List<AppPermission> permissions = new ArrayList<>();
            dataGrid.setRowData(0, permissions);
            dataGrid.setRowCount(permissions.size(), true);

        } else {
            // Fetch permissions and populate table.
            restFactory
                    .create(APP_PERMISSION_RESOURCE)
                    .method(res -> res.getAppUserPermissionsReport(relatedUser))
                    .onSuccess(userAppPermissions -> {
                        AppPermissionsEditPresenter.this.currentPermissions = userAppPermissions;
                        updateAllPermissions();
                        // Update details panel.
                        ValueChangeEvent.fire(this, new ValueChange(false, true));
                    })
                    .taskMonitorFactory(this)
                    .exec();
        }
    }

    private void updateAllPermissions() {
        if (this.allPermissions == null) {
            allPermissions = Arrays
                    .stream(AppPermission.values())
                    .sorted(Comparator.comparing(AppPermission::getDisplayValue))
                    .collect(Collectors.toList());
        }

        dataGrid.setRowData(0, allPermissions);
        dataGrid.setRowCount(allPermissions.size(), true);
    }

    private void addColumns() {
        final boolean updateable = isCurrentUserUpdate();
        final TickBoxCell.Appearance appearance = updateable
                ? new TickBoxCell.DefaultAppearance()
                : new TickBoxCell.NoBorderAppearance();

        // Selection.
//        final Column<AppPermission, TickBoxState> selectionColumn = new Column<AppPermission, TickBoxState>(
//                TickBoxCell.create(appearance, true, true, updateable)) {
//            @Override
//            public TickBoxState getValue(final AppPermission permission) {
//                return getTickBoxState(permission);
//            }
//        };

        final Column<AppPermission, TickBoxState> selectionColumn = DataGridUtil.columnBuilder(
                        this::getTickBoxState, () -> TickBoxCell.create(
                                appearance, true, true, updateable))
                .centerAligned()
                .build();

        if (updateable) {
            selectionColumn.setFieldUpdater((index, permission, value) -> {
                final AbstractAppPermissionChange request;
                if (value.toBoolean()) {
                    request = new AddAppPermission(relatedUser, permission);
                } else {
                    request = new RemoveAppPermission(relatedUser, permission);
                }

                restFactory
                        .create(APP_PERMISSION_RESOURCE)
                        .method(res -> res.changeAppPermission(request))
                        .onSuccess(result -> {
                            refresh();
                            // Refresh users pane.
                            ValueChangeEvent.fire(
                                    AppPermissionsEditPresenter.this,
                                    new ValueChange(true, false));
                        })
                        .taskMonitorFactory(this)
                        .exec();
            });
        }

        dataGrid.addColumn(selectionColumn,
                DataGridUtil.headingBuilder("Granted")
                        .withToolTip("If ticked the permission is explicitly granted. If half ticked it is " +
                                     "inferred from group membership.")
                        .centerAligned()
                        .build(),
                70);

        // Perm name
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(AppPermission::getDisplayValue)
                        .build(),
                DataGridUtil.headingBuilder("Permission")
                        .withToolTip("The name of the application permission.")
                        .build(),
                200);

        // Description
        dataGrid.addAutoResizableColumn(
                DataGridUtil.textColumnBuilder(AppPermission::getDescription)
                        .build(),
                DataGridUtil.headingBuilder("Description")
                        .withToolTip("Description of what the permission allows the user/group to do.")
                        .build(),
                700);

        DataGridUtil.addEndColumn(dataGrid);
    }

    private TickBoxState getTickBoxState(final AppPermission permission) {
        if (currentPermissions != null) {
            if (GwtNullSafe.collectionContains(currentPermissions.getExplicitPermissions(), permission)) {
                return TickBoxState.TICK;
            } else if (currentPermissions.getInheritedPermissions().containsKey(permission)) {
                return TickBoxState.HALF_TICK;
            }

            // See if implied by administrator.
            if (!AppPermission.ADMINISTRATOR.equals(permission)) {
                if (GwtNullSafe.collectionContains(currentPermissions.getExplicitPermissions(),
                        AppPermission.ADMINISTRATOR)) {
                    return TickBoxState.HALF_TICK;
                } else if (GwtNullSafe.containsKey(
                        currentPermissions.getInheritedPermissions(),
                        AppPermission.ADMINISTRATOR)) {
                    return TickBoxState.HALF_TICK;
                }
            }
        }
        return TickBoxState.UNTICK;
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<ValueChange> valueChangeHandler) {
        return new LegacyHandlerWrapper(addHandler(ValueChangeEvent.getType(), valueChangeHandler));
    }

    protected boolean isCurrentUserUpdate() {
        return securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION);
    }

    public AppUserPermissionsReport getCurrentPermissions() {
        return currentPermissions;
    }

    public interface AppPermissionsEditView extends View {

        void setPermissionsView(View view);
    }

    public class ValueChange {

        private final boolean refreshUsers;
        private final boolean refreshDetails;

        public ValueChange(final boolean refreshUsers,
                           final boolean refreshDetails) {
            this.refreshUsers = refreshUsers;
            this.refreshDetails = refreshDetails;
        }

        public boolean isRefreshUsers() {
            return refreshUsers;
        }

        public boolean isRefreshDetails() {
            return refreshDetails;
        }
    }
}
