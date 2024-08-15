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
import stroom.security.client.presenter.AppPermissionsChangePresenter.AppPermissionsChangeView;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppPermissionResource;
import stroom.security.shared.ChangeSet;
import stroom.security.shared.ChangeUserRequest;
import stroom.util.shared.UserRef;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class AppPermissionsChangePresenter
        extends MyPresenterWidget<AppPermissionsChangeView> {

    private static final AppPermissionResource APP_PERMISSION_RESOURCE = GWT.create(AppPermissionResource.class);

    private final RestFactory restFactory;
    private final ClientSecurityContext securityContext;
    private final MyDataGrid<AppPermission> dataGrid;

    private Set<AppPermission> currentPermissions;
    private List<AppPermission> allPermissions;

    private UserRef relatedUser;

    @Inject
    public AppPermissionsChangePresenter(final EventBus eventBus,
                                         final AppPermissionsChangeView view,
                                         final PagerView pagerView,
                                         final RestFactory restFactory,
                                         final ClientSecurityContext securityContext) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.securityContext = securityContext;

        dataGrid = new MyDataGrid<>();
        pagerView.setDataWidget(dataGrid);
        view.setPermissionsView(pagerView);

        addColumns();
    }

    public void show(final UserRef userRef, final Runnable onClose) {
        this.relatedUser = userRef;
        refresh();

        final PopupSize popupSize = PopupSize.builder()
                .width(Size
                        .builder()
                        .initial(800)
                        .min(400)
                        .resizable(true)
                        .build())
                .height(Size
                        .builder()
                        .initial(800)
                        .min(400)
                        .resizable(true)
                        .build())
                .build();
        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(popupSize)
                .onShow(e -> dataGrid.setFocus(true))
                .caption("Change Application Permissions")
                .onHideRequest(e -> {
                    onClose.run();
                    e.hide();
                })
                .fire();
    }

    private void refresh() {
        if (relatedUser == null) {
            currentPermissions = null;
            final List<AppPermission> features = new ArrayList<>();
            dataGrid.setRowData(0, features);
            dataGrid.setRowCount(features.size(), true);

        } else {
            // Fetch permissions and populate table.
            restFactory
                    .create(APP_PERMISSION_RESOURCE)
                    .method(res -> res.fetchUserAppPermissions(relatedUser))
                    .onSuccess(userAppPermissions -> {
                        AppPermissionsChangePresenter.this.currentPermissions = userAppPermissions;
                        updateAllPermissions();
                    })
                    .taskListener(this)
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
        final Column<AppPermission, TickBoxState> selectionColumn = new Column<AppPermission, TickBoxState>(
                TickBoxCell.create(appearance, true, true, updateable)) {
            @Override
            public TickBoxState getValue(final AppPermission permission) {
                if (currentPermissions != null && currentPermissions.contains(permission)) {
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
                        .taskListener(this)
                        .exec();
            });
        }

        dataGrid.addColumn(selectionColumn, "<br/>", ColumnSizeConstants.ICON_COL);

        // Perm name
        dataGrid.addColumn(new Column<AppPermission, String>(new TextCell()) {
            @Override
            public String getValue(final AppPermission permission) {
                return permission.getDisplayValue();
            }
        }, "Permission", 200);

        // Description
        dataGrid.addColumn(new Column<AppPermission, String>(new TextCell()) {
            @Override
            public String getValue(final AppPermission permission) {
                return permission.getDescription();
            }
        }, "Description", 700);
    }

    protected boolean isCurrentUserUpdate() {
        return securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION);
    }

    public interface AppPermissionsChangeView extends View {

        void setPermissionsView(View view);
    }
}
