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
import stroom.data.grid.client.DataGridSelectionEventManager;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.AppPermissionsEditPresenter.AppPermissionsEditView;
import stroom.security.shared.AbstractAppPermissionChange;
import stroom.security.shared.AbstractAppPermissionChange.AddAppPermission;
import stroom.security.shared.AbstractAppPermissionChange.RemoveAppPermission;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppPermissionResource;
import stroom.security.shared.AppUserPermissionsReport;
import stroom.util.shared.UserRef;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
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
        extends MyPresenterWidget<AppPermissionsEditView> {

    private static final AppPermissionResource APP_PERMISSION_RESOURCE = GWT.create(AppPermissionResource.class);

    private final RestFactory restFactory;
    private final ClientSecurityContext securityContext;
    private final MyDataGrid<AppPermission> dataGrid;
    private final MultiSelectionModelImpl<AppPermission> selectionModel;
    private final DataGridSelectionEventManager<AppPermission> selectionEventManager;

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
        selectionEventManager = new DataGridSelectionEventManager<>(dataGrid, selectionModel, false);
        dataGrid.setSelectionModel(selectionModel, selectionEventManager);
        pagerView.setDataWidget(dataGrid);
        view.setPermissionsView(pagerView);

        addColumns();
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(selectionModel.addSelectionHandler(e -> updateDetails()));
    }

    private void updateDetails() {
        final SafeHtml details = getDetails();
        getView().setDetails(details);
    }

    private SafeHtml getDetails() {
        final DescriptionBuilder sb = new DescriptionBuilder();
        final AppPermission permission = selectionModel.getSelected();
        if (permission != null) {
            addPaths(
                    currentPermissions.getExplicitPermissions().contains(permission),
                    currentPermissions.getInheritedPermissions().get(permission),
                    sb,
                    "Explicit Permission",
                    "Inherited From:");

            // See if implied by administrator.
            if (!AppPermission.ADMINISTRATOR.equals(permission)) {
                addPaths(
                        currentPermissions
                                .getExplicitPermissions()
                                .contains(AppPermission.ADMINISTRATOR),
                        currentPermissions
                                .getInheritedPermissions()
                                .get(AppPermission.ADMINISTRATOR),
                        sb,
                        "Implied By Administrator",
                        "Implied By Administrator Inherited From:");
            }

            if (sb.toSafeHtml().asString().length() == 0) {
                sb.addTitle("No Permission");
            }
        }

        return sb.toSafeHtml();
    }

//    private void addPaths(final List<List<UserRef>> paths,
//                          final SafeHtmlBuilder sb,
//                          final String directTitle,
//                          final String inheritedTitle) {
//        if (paths != null) {
//            final Optional<List<UserRef>> directPath = paths
//                    .stream()
//                    .filter(path -> path.size() == 1 && path.get(0).equals(relatedUser))
//                    .findAny();
//            if (directPath.isPresent()) {
//                appendTitle(sb, directTitle);
//                sb.appendHtmlConstant("<br/>");
//            }
//
//            final List<List<UserRef>> inheritedPaths = paths
//                    .stream()
//                    .filter(path -> path.size() > 1)
//                    .collect(Collectors.toList());
//            if (inheritedPaths.size() > 0) {
//                appendTitle(sb, inheritedTitle);
//
//                for (final List<UserRef> path : inheritedPaths) {
//                    sb.appendEscaped(path
//                            .stream()
//                            .filter(userRef -> !userRef.equals(relatedUser))
//                            .map(UserRef::toDisplayString)
//                            .collect(Collectors.joining(" > ")));
//                    sb.appendHtmlConstant("<br/>");
//                }
//
//                sb.appendHtmlConstant("<br/>");
//            }
//        }
//    }

    private void addPaths(final boolean direct,
                          final List<String> paths,
                          final DescriptionBuilder sb,
                          final String directTitle,
                          final String inheritedTitle) {
        if (direct) {
            sb.addNewLine();
            sb.addNewLine();
            sb.addTitle(directTitle);
        }

        if (paths != null && paths.size() > 0) {
            sb.addNewLine();
            sb.addNewLine();
            sb.addTitle(inheritedTitle);
            for (final String path : paths) {
                sb.addNewLine();
                sb.addLine(path);
            }
        }
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
                        updateDetails();
                    })
                    .taskHandlerFactory(this)
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
                if (currentPermissions != null) {
                    if (currentPermissions.getExplicitPermissions().contains(permission)) {
                        return TickBoxState.TICK;
                    } else if (currentPermissions.getInheritedPermissions().containsKey(permission)) {
                        return TickBoxState.HALF_TICK;
                    }
                }

                // See if implied by administrator.
                if (!AppPermission.ADMINISTRATOR.equals(permission)) {
                    if (currentPermissions.getExplicitPermissions().contains(AppPermission.ADMINISTRATOR)) {
                        return TickBoxState.HALF_TICK;
                    } else if (currentPermissions.getInheritedPermissions().containsKey(AppPermission.ADMINISTRATOR)) {
                        return TickBoxState.HALF_TICK;
                    }
                }

                return TickBoxState.UNTICK;
            }
        };

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
                        .onSuccess(result -> refresh())
                        .taskHandlerFactory(this)
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

    public interface AppPermissionsEditView extends View {

        void setPermissionsView(View view);

        void setDetails(SafeHtml details);
    }
}
