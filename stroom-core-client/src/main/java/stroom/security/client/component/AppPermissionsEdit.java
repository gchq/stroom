/*
 * Copyright 2026 Crown Copyright
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

package stroom.security.client.component;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.security.shared.AppPermission;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.UserRef;
import stroom.util.shared.string.CaseType;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.DataGridSelectionEventManager;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.form.client.FormGroup;
import stroom.widget.text.client.Html;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/// A user's or group's application permissions, a row each with a tick box (ticked if held
/// explicitly, half ticked if inherited or implied by Administrator), and below them how they hold
/// the one selected.
public class AppPermissionsEdit extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String APP_PERM_BASE_LABEL = "Application Permissions";
    private static final String APP_PERM_DETAILS_BASE_LABEL = "Application Permission Details";

    @UiField
    FormGroup appPermissionsFormGroup;
    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<AppPermission> dataGrid;
    @UiField
    FormGroup detailsFormGroup;
    @UiField
    Html details;

    private final MultiSelectionModelImpl<AppPermission> selectionModel = new MultiSelectionModelImpl<>();
    private Function<AppPermission, TickBoxState> permissionState = permission -> TickBoxState.UNTICK;
    private BiConsumer<AppPermission, Boolean> onTick = (permission, ticked) -> {
    };
    private Runnable onSelectionChange = () -> {
    };

    /// Makes an empty list, with no columns until [#addColumns(boolean)].
    public AppPermissionsEdit() {
        initWidget(BINDER.createAndBindUi(this));
        dataGrid.setSelectionModel(selectionModel,
                new DataGridSelectionEventManager<>(dataGrid, selectionModel, false));
        selectionModel.addSelectionHandler(event -> onSelectionChange.run());
    }

    /// Adds the columns: the tick box, the permission's name and its description, faded where it
    /// isn't held.
    ///
    /// @param updateable Whether the tick boxes can be changed.
    public void addColumns(final boolean updateable) {
        final TickBoxCell.Appearance appearance = updateable
                ? new TickBoxCell.DefaultAppearance()
                : new TickBoxCell.NoBorderAppearance();

        final Column<AppPermission, TickBoxState> selectionColumn = DataGridUtil.columnBuilder(
                        (AppPermission permission) -> permissionState.apply(permission),
                        () -> TickBoxCell.create(appearance, true, true, updateable))
                .centerAligned()
                .enabledWhen(this::hasPermission)
                .build();

        if (updateable) {
            selectionColumn.setFieldUpdater((index, permission, value) ->
                    onTick.accept(permission, value.toBoolean()));
        }

        dataGrid.addColumn(selectionColumn,
                DataGridUtil.headingBuilder("Granted")
                        .withToolTip("If ticked the permission is explicitly granted. If half ticked it is " +
                                     "inferred from group membership.")
                        .centerAligned()
                        .build(),
                70);

        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(AppPermission::getDisplayValue)
                        .enabledWhen(this::hasPermission)
                        .build(),
                DataGridUtil.headingBuilder("Permission")
                        .withToolTip("The name of the application permission.")
                        .build(),
                200);

        dataGrid.addAutoResizableColumn(
                DataGridUtil.textColumnBuilder(AppPermission::getDescription)
                        .enabledWhen(this::hasPermission)
                        .build(),
                DataGridUtil.headingBuilder("Description")
                        .withToolTip("Description of what the permission allows the user/group to do.")
                        .build(),
                700);
    }

    /// @param permissions The rows to show.
    public void setRows(final List<AppPermission> permissions) {
        dataGrid.setRowData(0, permissions);
        dataGrid.setRowCount(permissions.size(), true);
    }

    /// @return The permission selected, or `null`.
    public AppPermission getSelected() {
        return selectionModel.getSelected();
    }

    /// @param details How the permission selected is held.
    public void setDetails(final SafeHtml details) {
        this.details.setHTML(SafeHtmlUtil.nullSafe(details));
    }

    /// Names the user or group in the labels.
    ///
    /// @param userRef The user or group, or `null` for none.
    public void setUserRef(final UserRef userRef) {
        if (userRef == null) {
            appPermissionsFormGroup.setLabel(APP_PERM_BASE_LABEL + ":");
            detailsFormGroup.setLabel(APP_PERM_DETAILS_BASE_LABEL + ":");
        } else {
            final String suffix = " for " + userRef.getType(CaseType.LOWER)
                                  + " \"" + userRef.getDisplayName() + "\":";
            appPermissionsFormGroup.setLabel(APP_PERM_BASE_LABEL + suffix);
            detailsFormGroup.setLabel(APP_PERM_DETAILS_BASE_LABEL + suffix);
        }
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @param onRefresh Run when the pager's refresh button is pressed.
    public void setOnRefresh(final Runnable onRefresh) {
        pager.getRefreshButton().setOnClick(onRefresh);
    }

    /// @param permissionState Gives a permission's tick box state.
    public void setPermissionState(final Function<AppPermission, TickBoxState> permissionState) {
        this.permissionState = permissionState;
    }

    /// @param onTick Given a permission and whether its box is now ticked, when it is pressed.
    public void setOnTick(final BiConsumer<AppPermission, Boolean> onTick) {
        this.onTick = onTick;
    }

    /// @param onSelectionChange Run when another permission is selected.
    public void setOnSelectionChange(final Runnable onSelectionChange) {
        this.onSelectionChange = onSelectionChange;
    }

    private boolean hasPermission(final AppPermission permission) {
        final TickBoxState tickBoxState = permissionState.apply(permission);
        return TickBoxState.TICK == tickBoxState
               || TickBoxState.HALF_TICK == tickBoxState;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AppPermissionsEdit> {

    }
}
