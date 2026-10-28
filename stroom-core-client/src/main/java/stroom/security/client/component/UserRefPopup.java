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

import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.security.client.presenter.UserAndGroupHelper;
import stroom.security.shared.UserFields;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.DataGridSelectionEventManager;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.quickfilter.client.QuickFilterContextHandler;
import stroom.widget.quickfilter.client.QuickFilterDialog;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// The user picker: a quick filter above a page of the users and groups (an icon for which it is,
/// the display name and the full name), one of which is chosen. Down in the filter goes to the
/// chosen row, Up from the first row back to the filter, and typing in the list goes to the filter;
/// moving chooses, and Enter or a double click chooses and closes.
public class UserRefPopup extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    QuickFilterDialog dialog;
    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<UserRef> dataGrid;

    private final MultiSelectionModelImpl<UserRef> selectionModel = new MultiSelectionModelImpl<>();
    private Runnable onChoose = () -> {
    };
    private Runnable onSortChange = () -> {
    };

    /// Makes an empty picker.
    public UserRefPopup() {
        initWidget(BINDER.createAndBindUi(this));

        final DataGridSelectionEventManager<UserRef> selectionEventManager =
                new DataGridSelectionEventManager<>(dataGrid, selectionModel, false);
        selectionEventManager.setMoveUpFromFirst(dialog::focus);
        selectionEventManager.setTypedHandler(dialog::typeInFilter);
        dataGrid.setSelectionModel(selectionModel, selectionEventManager);
        dialog.setFilterMoveDown(this::focusSelected);
        addColumns();

        selectionModel.addSelectionHandler(event -> {
            if (event.getSelectionType().isDoubleSelect()) {
                if (selectionModel.getSelected() != null) {
                    onChoose.run();
                }
            } else {
                NullSafe.consume(selectionModel.getSelected(), userRef ->
                        dialog.announce(userRef.getDisplayName() + " chosen"));
            }
        });
        // Enter on a row chooses that row (the one with the focus, which may not be chosen yet, e.g.
        // after filtering) and closes the dialog, as OK does
        dataGrid.addDomHandler(event -> {
            final int row = dataGrid.getKeyboardSelectedRow();
            final List<UserRef> rows = dataGrid.getVisibleItems();
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER
                && isInRows(event.getNativeEvent())
                && row >= 0 && row < rows.size()) {
                event.preventDefault();
                selectionModel.setSelected(rows.get(row));
                onChoose.run();
            }
        }, KeyDownEvent.getType());
        dataGrid.addColumnSortHandler(event -> onSortChange.run());
    }

    private void addColumns() {
        // Icon
        dataGrid.addColumn(
                DataGridUtil.cellIconColumnBuilder(false, UserAndGroupHelper::mapUserRefTypeToIcon)
                        .withSorting(UserFields.FIELD_IS_GROUP)
                        .centerAligned()
                        .build(),
                DataGridUtil.headingBuilder()
                        .headingText(UserAndGroupHelper.buildUserAndGroupIconHeader())
                        .centerAligned()
                        .withToolTip("Whether this row is a single user or a named user group.")
                        .build(),
                (ColumnSizeConstants.ICON_COL * 2) + 20);

        // Display Name
        final Column<UserRef, String> displayNameCol = DataGridUtil.textColumnBuilder(UserRef::getDisplayName)
                .withSorting(UserFields.FIELD_DISPLAY_NAME, true)
                .build();
        dataGrid.addResizableColumn(
                displayNameCol,
                DataGridUtil.headingBuilder(UserAndGroupHelper.COL_NAME_DISPLAY_NAME)
                        .withToolTip("The name of the user or group.")
                        .build(),
                300);
        dataGrid.sort(displayNameCol);

        // Full name
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(UserRef::getFullName)
                        .withSorting(UserFields.FIELD_FULL_NAME, true)
                        .build(),
                DataGridUtil.headingBuilder(UserAndGroupHelper.COL_NAME_FULL_NAME)
                        .withToolTip("The full name of the user. Groups do not have a full name.")
                        .build(),
                350);
    }

    /// Puts the focus in the filter.
    public void focus() {
        dialog.focus();
    }

    /// @param onFilterChange Given the filter's text when it changes.
    public void setOnFilterChange(final Consumer<String> onFilterChange) {
        dialog.setOnFilterChange(onFilterChange);
    }

    /// @param onChoose Run when a user or group is chosen and the picker should close (Enter, or a
    ///                 double click, on it).
    public void setOnChoose(final Runnable onChoose) {
        this.onChoose = Objects.requireNonNull(onChoose, "onChoose");
    }

    /// @param onSortChange Run when the list is sorted by another column.
    public void setOnSortChange(final Runnable onSortChange) {
        this.onSortChange = Objects.requireNonNull(onSortChange, "onSortChange");
    }

    /// @param popupTextSupplier Gives the filter's help popup its text, the fields it can use.
    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        dialog.registerPopupTextProvider(popupTextSupplier);
    }

    /// @param contextHandler Gives the filter its recent filters and remembers the ones used.
    public void setQuickFilterContextHandler(final QuickFilterContextHandler contextHandler) {
        dialog.setQuickFilterContextHandler(contextHandler);
    }

    /// Shows why the server rejected the filter. See `ResultPage.filterError`.
    ///
    /// @param filterError The reason, or `null` to clear it.
    public void setFilterError(final String filterError) {
        dialog.setFilterError(filterError);
    }

    /// @param dataProvider Gives the list its users and groups, a page at a time.
    public void setDataProvider(final AbstractDataProvider<UserRef> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The selection: the user or group chosen.
    public MultiSelectionModel<UserRef> getSelectionModel() {
        return selectionModel;
    }

    /// @return The columns the list is sorted by.
    public ColumnSortList getSortList() {
        return dataGrid.getColumnSortList();
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// Makes the chosen user's row the list's tab stop, so tabbing into it lands there.
    public void showSelectedAsTabStop() {
        final int index = dataGrid.getVisibleItems().indexOf(selectionModel.getSelected());
        if (index >= 0) {
            dataGrid.setKeyboardSelectedRow(index, false);
        }
    }

    // Moves the focus to the chosen user's row (or the first, if none is), from the filter above
    private void focusSelected() {
        dataGrid.setKeyboardSelectedRow(
                Math.max(0, dataGrid.getVisibleItems().indexOf(selectionModel.getSelected())), true);
    }

    // Whether a key was pressed in the grid's rows (not, e.g., in its column headers)
    private boolean isInRows(final NativeEvent event) {
        final EventTarget target = event.getEventTarget();
        return Element.is(target) && dataGrid.isInRows(Element.as(target));
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, UserRefPopup> {

    }
}
