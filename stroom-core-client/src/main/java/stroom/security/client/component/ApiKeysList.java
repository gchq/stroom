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
import stroom.security.client.presenter.UserAndGroupHelper;
import stroom.security.shared.FindApiKeyCriteria;
import stroom.security.shared.HashedApiKey;
import stroom.security.shared.UserFields;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.menu.client.Item;
import stroom.widget.quickfilter.client.QuickFilterContextHandler;
import stroom.widget.quickfilter.client.QuickFilterPage;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/// API keys, a row each (its owner, name, prefix, state, expiry, hash algorithm, comments and an
/// action menu for its owner), sorted by a column, several of which can be selected; a quick filter
/// above them, and a pager with a toolbar for the screen's own buttons.
public class ApiKeysList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    QuickFilterPage page;
    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<HashedApiKey> dataGrid;

    private final MultiSelectionModelImpl<HashedApiKey> selectionModel;
    private Runnable onSortChange = () -> {
    };

    /// Makes an empty list, with no columns until [#addColumns].
    public ApiKeysList() {
        initWidget(BINDER.createAndBindUi(this));
        // A tick or refresh brings new rows (their equals compares every field), so keep the row by its id
        dataGrid.setKeyProvider(HashedApiKey::getId);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        dataGrid.addColumnSortHandler(event -> onSortChange.run());
    }

    /// Adds the columns, once.
    ///
    /// @param showOwner       Whether to show each key's owner (to those who manage users, when the
    ///                        keys of more than one owner are shown).
    /// @param expiryFormat    Formats when a key expires, with how long until then.
    /// @param actionMenuItems Gives a row's action menu.
    public void addColumns(final boolean showOwner,
                           final Function<HashedApiKey, SafeHtml> expiryFormat,
                           final Function<HashedApiKey, List<Item>> actionMenuItems) {
        // Need manage users perm to CRUD keys for other users
        // If you don't have it no point in showing owner col
        // Also don't show it if this screen has been set with a single owner
        if (showOwner) {
            dataGrid.addColumn(
                    DataGridUtil.cellIconColumnBuilder(false, (HashedApiKey row) ->
                                    UserAndGroupHelper.mapUserRefTypeToIcon(row.getOwner()))
                            .withSorting(UserFields.FIELD_IS_GROUP)
                            .centerAligned()
                            .build(),
                    DataGridUtil.headingBuilder("")
                            .headingText(UserAndGroupHelper.buildUserAndGroupIconHeader())
                            .centerAligned()
                            .withToolTip("Whether this key is for a single user or a named user group.")
                            .build(),
                    (ColumnSizeConstants.ICON_COL * 2) + 20);

            final Column<HashedApiKey, String> ownerColumn = DataGridUtil.textColumnBuilder(
                            (HashedApiKey row) ->
                                    row.getOwner().toDisplayString())
                    .enabledWhen(HashedApiKey::getEnabled)
                    .withSorting(FindApiKeyCriteria.FIELD_OWNER)
                    .build();
            dataGrid.addResizableColumn(
                    ownerColumn,
                    DataGridUtil.headingBuilder("Owner")
                            .withToolTip("The user or group that owns this API key. " +
                                         "The API key has the same permissions as the owner.")
                            .build(),
                    250);
        }

        // Key Name
        final Column<HashedApiKey, String> nameColumn = DataGridUtil.textColumnBuilder(HashedApiKey::getName)
                .enabledWhen(HashedApiKey::getEnabled)
                .withSorting(FindApiKeyCriteria.FIELD_NAME)
                .build();
        dataGrid.addResizableColumn(
                nameColumn,
                DataGridUtil.headingBuilder("Key Name")
                        .withToolTip("The name of the API key")
                        .build(),
                250);

        // Key Prefix
        final Column<HashedApiKey, String> prefixColumn = DataGridUtil.textColumnBuilder(HashedApiKey::getApiKeyPrefix)
                .enabledWhen(HashedApiKey::getEnabled)
                .withSorting(FindApiKeyCriteria.FIELD_PREFIX)
                .build();
        dataGrid.addColumn(
                prefixColumn,
                DataGridUtil.headingBuilder("Key Prefix")
                        .withToolTip("The first few characters of the API key to help identify an API key")
                        .build(),
                130);

        // Enabled state
        final Column<HashedApiKey, String> enabledColumn = DataGridUtil.textColumnBuilder((HashedApiKey apiKey) ->
                        apiKey.getEnabled()
                                ? "Enabled"
                                : "Disabled")
                .enabledWhen(HashedApiKey::getEnabled)
                .withSorting(FindApiKeyCriteria.FIELD_STATE)
                .build();
        dataGrid.addColumn(
                enabledColumn,
                DataGridUtil.headingBuilder("State")
                        .withToolTip("Whether this API key is enabled or disabled")
                        .build(),
                ColumnSizeConstants.SMALL_COL);

        // Expires on
        final Column<HashedApiKey, SafeHtml> expiresOnColumn = DataGridUtil.htmlColumnBuilder(
                        expiryFormat)
                .enabledWhen(HashedApiKey::getEnabled)
                .withSorting(FindApiKeyCriteria.FIELD_EXPIRE_TIME)
                .build();
        dataGrid.addColumn(
                expiresOnColumn,
                DataGridUtil.headingBuilder("Expires On")
                        .withToolTip("When the API key expires")
                        .build(),
                ColumnSizeConstants.DATE_AND_DURATION_COL);
        dataGrid.sort(expiresOnColumn);

        // Hash algorithm
        final Column<HashedApiKey, String> hashAlgorithmColumn = DataGridUtil.textColumnBuilder(
                        (HashedApiKey key) -> key.getHashAlgorithm().getDisplayValue())
                .enabledWhen(HashedApiKey::getEnabled)
                .withSorting(FindApiKeyCriteria.FIELD_HASH_ALGORITHM)
                .build();
        dataGrid.addResizableColumn(hashAlgorithmColumn, "Hash Algorithm", 120);

        // Comments
        final Column<HashedApiKey, String> commentsColumn = DataGridUtil.textColumnBuilder(HashedApiKey::getComments)
                .enabledWhen(HashedApiKey::getEnabled)
                .withSorting(FindApiKeyCriteria.FIELD_COMMENTS)
                .build();
        dataGrid.addAutoResizableColumn(commentsColumn, "Comments", ColumnSizeConstants.BIG_COL);

        // Actions Menu btn
        final Column<HashedApiKey, HashedApiKey> actionMenuCol = DataGridUtil.columnBuilder(
                        Function.identity(),
                        () -> new ActionMenuCell<>(
                                actionMenuItems,
                                null,
                                (HashedApiKey hashedApiKey) -> UserAndGroupHelper.buildActionsForMsg(
                                        NullSafe.get(hashedApiKey, HashedApiKey::getOwner))))
                .build();

        // x2 width so when it is hard right, it doesn't get in the way of the scroll bar
        dataGrid.addColumn(
                actionMenuCol,
                "",
                ColumnSizeConstants.ICON_COL + 10);
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<HashedApiKey> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection.
    public MultiSelectionModelImpl<HashedApiKey> getSelectionModel() {
        return selectionModel;
    }

    /// @return How the rows are sorted, for the request for them.
    public ColumnSortList getSortList() {
        return dataGrid.getColumnSortList();
    }

    /// Empties the list.
    public void clearRows() {
        dataGrid.setRowData(0, Collections.emptyList());
        dataGrid.setRowCount(0, true);
    }

    /// @return The toolbar beside the pager, for the screen's own buttons.
    public Toolbar getToolbar() {
        return pager.getToolbar();
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


    interface Binder extends UiBinder<Widget, ApiKeysList> {

    }
}
