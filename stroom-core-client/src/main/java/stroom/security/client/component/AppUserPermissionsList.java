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

import stroom.cell.info.client.ActionMenuCell;
import stroom.data.client.ColumnSizeConstants;
import stroom.data.client.component.UserRefCell;
import stroom.security.client.DescriptionBuilder;
import stroom.security.client.UserAndGroupHelper;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppUserPermissions;
import stroom.security.shared.UserFields;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.util.shared.UserRef.DisplayType;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.ColumnBuilder;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.DataGridSelectionEventManager;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.menu.client.Item;
import stroom.widget.quickfilter.client.QuickFilterContextHandler;
import stroom.widget.quickfilter.client.QuickFilterPage;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/// Users and groups with their application permissions, a row each (whether it is a user or a
/// group, its name, its permissions, the inherited ones in grey, and an action menu), sorted by a
/// column; one can be selected. A quick filter above them.
public class AppUserPermissionsList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    QuickFilterPage page;
    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<AppUserPermissions> dataGrid;

    private final MultiSelectionModelImpl<AppUserPermissions> selectionModel = new MultiSelectionModelImpl<>();
    private Runnable onSortChange = () -> {
    };

    /// Makes an empty list, with no columns until [#addColumns].
    public AppUserPermissionsList() {
        initWidget(BINDER.createAndBindUi(this));
        dataGrid.setSelectionModel(selectionModel,
                new DataGridSelectionEventManager<>(dataGrid, selectionModel, false));
        dataGrid.addColumnSortHandler(event -> onSortChange.run());
    }

    /// Adds the columns.
    ///
    /// @param userCell        The user cell's builder, configured to open its users.
    /// @param actionMenuItems Gives a row's action menu.
    public void addColumns(final UserRefCell.Builder<AppUserPermissions> userCell,
                           final Function<AppUserPermissions, List<Item>> actionMenuItems) {
        // Icon
        dataGrid.addColumn(
                DataGridUtil.cellIconColumnBuilder(false, (AppUserPermissions row) ->
                                UserAndGroupHelper.mapUserRefTypeToIcon(row.getUserRef()))
                        .withSorting(UserFields.FIELD_IS_GROUP)
                        .centerAligned()
                        .enabledWhen(this::isUserEnabled)
                        .build(),
                DataGridUtil.headingBuilder("")
                        .headingText(UserAndGroupHelper.buildUserAndGroupIconHeader())
                        .centerAligned()
                        .withToolTip("Whether this row is a single user or a named user group.")
                        .build(),
                (ColumnSizeConstants.ICON_COL * 2) + 20);

        final Column<AppUserPermissions, AppUserPermissions> displayNameCol =
                new ColumnBuilder<AppUserPermissions, AppUserPermissions, Cell<AppUserPermissions>>(
                        Function.identity(),
                        () -> userCell
                                .showIcon(false)
                                .displayType(DisplayType.DISPLAY_NAME)
                                .userRefFunction(AppUserPermissions::getUserRef)
                                .build())
                        .withSorting(UserFields.FIELD_DISPLAY_NAME, true)
                        .enabledWhen(this::isUserEnabled)
                        .build();

        dataGrid.addResizableColumn(
                displayNameCol,
                DataGridUtil.headingBuilder(UserAndGroupHelper.COL_NAME_DISPLAY_NAME)
                        .withToolTip("The name of the user or group.")
                        .build(),
                ColumnSizeConstants.USER_DISPLAY_NAME_COL);

        // Show it as the default sort
        dataGrid.sort(displayNameCol);

        // Permissions
        dataGrid.addAutoResizableColumn(
                DataGridUtil.columnBuilder(
                                AppUserPermissionsList::buildPermissionsCellValue,
                                SafeHtmlCell::new)
                        .enabledWhen(this::isUserEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Permissions")
                        .withToolTip("The permissions held by this user/group. Inherited permissions are in grey.")
                        .build(),
                400);

        // Actions Menu btn
        final Column<AppUserPermissions, AppUserPermissions> actionMenuCol = DataGridUtil.columnBuilder(
                        Function.identity(),
                        () -> new ActionMenuCell<>(
                                actionMenuItems,
                                null,
                                (AppUserPermissions appUsrPerms) ->
                                        UserAndGroupHelper.buildActionsForMsg(appUsrPerms.getUserRef())))
                .build();
        // x2 width so when it is hard right, it doesn't get in the way of the scroll bar
        dataGrid.addColumn(
                actionMenuCol,
                "",
                ColumnSizeConstants.ICON_COL + 10);
    }

    private static SafeHtml buildPermissionsCellValue(final AppUserPermissions appUserPermissions) {
        final DescriptionBuilder sb = new DescriptionBuilder();
        boolean notEmpty = false;
        boolean lastIsInherited = false;
        final SafeHtml delimiter = new SafeHtmlBuilder()
                .append(SafeHtmlUtil.ENSP)
                .appendEscaped("|")
                .append(SafeHtmlUtil.ENSP)
                .toSafeHtml();
        for (final AppPermission permission : AppPermission.LIST) {
            if (NullSafe.collectionContains(appUserPermissions.getPermissions(), permission)) {
                if (notEmpty) {
                    sb.addLine(false, lastIsInherited, true, delimiter);
                }
                sb.addLine(permission.getDisplayValue());
                notEmpty = true;
                lastIsInherited = false;
            } else if (NullSafe.collectionContains(appUserPermissions.getInherited(), permission)) {
                if (notEmpty) {
                    sb.addLine(false, lastIsInherited, true, delimiter);
                }
                sb.addLine(false, true, permission.getDisplayValue());
                notEmpty = true;
                lastIsInherited = true;
            }
        }
        return sb.toSafeHtml();
    }

    private boolean isUserEnabled(final AppUserPermissions appUserPermissions) {
        return NullSafe.get(appUserPermissions, AppUserPermissions::getUserRef, UserRef::isEnabled);
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<AppUserPermissions> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection, one row at most.
    public MultiSelectionModelImpl<AppUserPermissions> getSelectionModel() {
        return selectionModel;
    }

    /// @return How the rows are sorted, for the request for them.
    public ColumnSortList getSortList() {
        return dataGrid.getColumnSortList();
    }

    /// Sets the filter's text; if it changes, the filter change callback is told.
    ///
    /// @param quickFilterText The text.
    public void setQuickFilterText(final String quickFilterText) {
        page.setQuickFilterText(quickFilterText);
    }

    /// @param filterError Why the server rejected the filter, or `null` to clear it.
    public void setFilterError(final String filterError) {
        page.setFilterError(filterError);
    }

    /// @param popupTextSupplier Gives the filter's help popup its text.
    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        page.registerPopupTextProvider(popupTextSupplier);
    }

    /// @param contextHandler Gives the filter its recent filters and remembers the ones used.
    public void setQuickFilterContextHandler(final QuickFilterContextHandler contextHandler) {
        page.setQuickFilterContextHandler(contextHandler);
    }

    /// @param onFilterChange Given the filter's text when it changes.
    public void setOnFilterChange(final Consumer<String> onFilterChange) {
        page.setOnFilterChange(onFilterChange);
    }

    /// @param onSortChange Run when the user sorts by another column.
    public void setOnSortChange(final Runnable onSortChange) {
        this.onSortChange = onSortChange;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AppUserPermissionsList> {

    }
}
