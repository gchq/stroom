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

import stroom.data.client.component.DocRefCell;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.security.shared.FindUserDependenciesCriteria;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.UserDependency;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
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

import java.util.function.Consumer;
import java.util.function.Supplier;

/// The documents that depend on a user, a row each (the document and the details), sorted by a
/// column; a quick filter above them.
public class UserDependenciesList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    QuickFilterPage page;
    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<UserDependency> dataGrid;

    private final MultiSelectionModelImpl<UserDependency> selectionModel;
    private Runnable onSortChange = () -> {
    };

    /// Makes an empty list, with no columns until [#addColumns(DocRefCell.Builder)].
    public UserDependenciesList() {
        initWidget(BINDER.createAndBindUi(this));
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        dataGrid.addColumnSortHandler(event -> onSortChange.run());
    }

    /// Adds the columns.
    ///
    /// @param documentCell The document cell's builder, configured to open its documents.
    public void addColumns(final DocRefCell.Builder<UserDependency> documentCell) {
        final Column<UserDependency, UserDependency> docNameCol = DataGridUtil.docRefColumnBuilder(
                        documentCell
                                .docRefFunction(UserDependency::getDocRef)
                                .showIcon(true))
                .withSorting(FindUserDependenciesCriteria.FIELD_DOC_NAME)
                .build();
        dataGrid.addResizableColumn(
                docNameCol,
                DataGridUtil.headingBuilder("Document Name")
                        .withToolTip("The document that depends on this user.")
                        .build(),
                ColumnSizeConstants.BIG_COL);
        dataGrid.sort(docNameCol);

        dataGrid.addAutoResizableColumn(
                DataGridUtil.textColumnBuilder(UserDependency::getDetails)
                        .withSorting(FindUserDependenciesCriteria.FIELD_DETAILS)
                        .build(),
                DataGridUtil.headingBuilder("Details")
                        .withToolTip("The details of the dependency.")
                        .build(),
                700);
    }

    /// @param dataProvider Gives the list its rows.
    public void setDataProvider(final AbstractDataProvider<UserDependency> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection, one row at most.
    public MultiSelectionModelImpl<UserDependency> getSelectionModel() {
        return selectionModel;
    }

    /// @return How the rows are sorted, for the request for them.
    public ColumnSortList getSortList() {
        return dataGrid.getColumnSortList();
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


    interface Binder extends UiBinder<Widget, UserDependenciesList> {

    }
}
