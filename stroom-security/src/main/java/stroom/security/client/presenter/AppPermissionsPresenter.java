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
import stroom.dispatch.client.AsyncCallbackAdaptor;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.security.client.ClientSecurityContext;
import stroom.security.shared.ChangeUserAction;
import stroom.security.shared.FetchUserAppPermissionsAction;
import stroom.security.shared.User;
import stroom.security.shared.UserAppPermissions;
import stroom.security.shared.UserRef;
import stroom.util.shared.VoidResult;
import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class AppPermissionsPresenter extends
        MyPresenterWidget<DataGridViewImpl<String>> {
    private final ClientDispatchAsync dispatcher;
    private final ClientSecurityContext securityContext;
//    private final DataGridView<String> permissionTable;
//    private final MySingleSelectionModel<String> selectedFeature = new MySingleSelectionModel<String>();

    private UserAppPermissions userAppPermissions;

    private UserRef relatedUser;

    @Inject
    public AppPermissionsPresenter(final EventBus eventBus,
                                   final ClientDispatchAsync dispatcher, final ClientSecurityContext securityContext) {
        super(eventBus, new DataGridViewImpl<String>());
        this.dispatcher = dispatcher;
        this.securityContext = securityContext;

        addColumns();
//        permissionTable = getView();
    }

//    @Override
//    protected void onBind() {
//        registerHandler(selectedFeature.addSelectionChangeHandler(new SelectionChangeEvent.Handler() {
//            @Override
//            public void onSelectionChange(final SelectionChangeEvent event) {
//                changeSelectedFeature();
//            }
//        }));
//    }

    public void setUser(final UserRef userRef) {
        this.relatedUser = userRef;
        refresh();
    }

    private void refresh() {
        // Fetch permissions and populate table.
        final FetchUserAppPermissionsAction fetchUserAppPermissionsAction = new FetchUserAppPermissionsAction(
                relatedUser);
        dispatcher.execute(fetchUserAppPermissionsAction, new AsyncCallbackAdaptor<UserAppPermissions>() {
            @Override
            public void onSuccess(final UserAppPermissions userAppPermissions) {
                AppPermissionsPresenter.this.userAppPermissions = userAppPermissions;

                final List<String> features = new ArrayList<String>(
                        userAppPermissions.getAllPermissions());
                Collections.sort(features);
                getView().setRowData(0, features);
                getView().setRowCount(features.size(), true);
            }
        });
    }

//    private DataGridViewImpl<String> createFeatureTable() {
//        final DataGridViewImpl<String> featureTable = new DataGridViewImpl<String>(true, 1000);
//        featureTable.setSelectionModel(selectedFeature);
//
//        // featureTable.addColumn(new InfoHelpLinkColumn<String>() {
//        // @Override
//        // public InfoCell.State getValue(final String feature) {
//        // return InfoCell.State.HELP;
//        // }
//        //
//        // @Override
//        // protected String getHelpLink(final String feature) {
//        // return GWT.getHostPageBaseURL() + "doc/user-guide/roles/roles.html"
//        // + formatAnchor(feature);
//        // }
//        //
//        // }, "<br/>", 20);
//
//        featureTable.addColumn(new Column<String, String>(new TextCell()) {
//            @Override
//            public String getValue(final String row) {
//                return row;
//            }
//        }, "Feature", 150);
//
//        featureTable.addEndColumn(new EndColumn<String>());
//
//        return featureTable;
//    }

    private void addColumns() {
        final boolean updateable = isCurrentUserUpdate();
        final TickBoxCell.Appearance appearance = updateable ? new TickBoxCell.DefaultAppearance() : new TickBoxCell.NoBorderAppearance();

//        final DataGridViewImpl<String> permissionTable = new DataGridViewImpl<String>(false, 1000);

//        permissionTable.addColumn(new InfoHelpLinkColumn<String>() {
//            @Override
//            public InfoCell.State getValue(final String row) {
//                return InfoCell.State.HELP;
//            }
//
//            @Override
//            protected String getHelpLink(final String row) {
//                return GWT.getHostPageBaseURL() + "doc/user-guide/roles/roles.html" + formatAnchor(row);
//            }
//
//        }, "<br/>", 20);

        getView().addColumn(new Column<String, String>(new TextCell()) {
            @Override
            public String getValue(final String row) {
                return row;
            }
        }, "Permission", 200);

        // Selection.
        final Column<String, TickBoxState> selectionColumn = new Column<String, TickBoxState>(
                new TickBoxCell(appearance, true, true, updateable)) {
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
            selectionColumn.setFieldUpdater(new FieldUpdater<String, TickBoxState>() {
                @Override
                public void update(final int index, final String permission, final TickBoxState value) {
                    final ChangeUserAction changeUserAction = new ChangeUserAction();
                    changeUserAction.setUserRef(relatedUser);
                    if (value.toBoolean()) {
                        changeUserAction.getChangedAppPermissions().add(permission);
                    } else {
                        changeUserAction.getChangedAppPermissions().remove(permission);
                    }
                    dispatcher.execute(changeUserAction, new AsyncCallbackAdaptor<VoidResult>() {
                        @Override
                        public void onSuccess(VoidResult result) {
                            refresh();
                        }
                    });
                }
            });
        }
        getView().addColumn(selectionColumn, "<br/>", 50);
    }

//    private void changeSelectedFeature() {
//        final String feature = selectedFeature.getSelectedObject();
//        if (feature != null) {
//            final List<String> permissions = new ArrayList<String>(
//                    userAppPermissions.getAllPermissions().get(feature));
//            Collections.sort(permissions);
//            permissionTable.setRowData(0, permissions);
//            permissionTable.setRowCount(permissions.size(), true);
//        } else {
//            permissionTable.setRowData(0, null);
//            permissionTable.setRowCount(0, true);
//        }
//    }

    protected boolean isCurrentUserUpdate() {
        return securityContext.hasAppPermission(User.MANAGE_USERS_PERMISSION);
    }
}
