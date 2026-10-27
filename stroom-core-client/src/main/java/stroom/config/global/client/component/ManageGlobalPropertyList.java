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

package stroom.config.global.client.component;

import stroom.config.global.shared.GlobalConfigResource;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;
import com.google.gwt.view.client.Range;

/// The global properties, a row each (its name, value, source and description), sorted by a
/// column; a value or source that differs across the cluster is shown in red. One can be selected.
public class ManageGlobalPropertyList extends Component {

    /// Shown as the value of a property whose value differs across the cluster.
    public static final String MULTIPLE_VALUES_MSG = "[Multiple values]";
    /// Shown as the source of a property whose source differs across the cluster.
    public static final String MULTIPLE_SOURCES_MSG = "[Multiple]";

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<ConfigPropertyRow> dataGrid;

    private final MultiSelectionModelImpl<ConfigPropertyRow> selectionModel;
    private Runnable onSortChange = () -> {
    };

    /// Makes an empty list.
    public ManageGlobalPropertyList() {
        initWidget(BINDER.createAndBindUi(this));
        // Rows keyed by name: each node's reply rebuilds the rows
        dataGrid.setKeyProvider(ConfigPropertyRow::getNameAsString);
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        addColumns();
        dataGrid.addColumnSortHandler(event -> onSortChange.run());
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<ConfigPropertyRow> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The toolbar beside the pager, for the screen's own buttons.
    public Toolbar getToolbar() {
        return pager.getToolbar();
    }

    /// @return The selection, one property at most.
    public MultiSelectionModelImpl<ConfigPropertyRow> getSelectionModel() {
        return selectionModel;
    }

    /// @return How the rows are sorted, for the request for them.
    public ColumnSortList getSortList() {
        return dataGrid.getColumnSortList();
    }

    /// @return The rows shown.
    public Range getVisibleRange() {
        return dataGrid.getVisibleRange();
    }

    /// Goes back to the first page.
    public void resetRange() {
        dataGrid.setVisibleRange(0, dataGrid.getVisibleRange().getLength());
    }

    /// @return How many rows there are in all.
    public int getRowCount() {
        return dataGrid.getRowCount();
    }

    /// @param onSortChange Run when the user sorts by another column.
    public void setOnSortChange(final Runnable onSortChange) {
        this.onSortChange = onSortChange;
    }

    private void addColumns() {
        // Name.
        dataGrid.addResizableColumn(
                DataGridUtil.copyTextColumnBuilder((ConfigPropertyRow row) ->
                                row.getNameAsString())
                        .withSorting(GlobalConfigResource.FIELD_DEF_NAME.getDisplayName())
                        .build(),
                GlobalConfigResource.FIELD_DEF_NAME.getDisplayName(),
                450);

        // Effective Value
        dataGrid.addResizableColumn(
                DataGridUtil.htmlColumnBuilder(
                                (ConfigPropertyRow row) -> multipleOrPlain(
                                        row.getEffectiveValueAsString(), MULTIPLE_VALUES_MSG))
                        .withSorting(GlobalConfigResource.FIELD_DEF_VALUE.getDisplayName())
                        .build(),
                GlobalConfigResource.FIELD_DEF_VALUE.getDisplayName(),
                300);

        // Source
        dataGrid.addResizableColumn(
                DataGridUtil.htmlColumnBuilder(
                                (ConfigPropertyRow row) -> multipleOrPlain(
                                        row.getSourceAsString(), MULTIPLE_SOURCES_MSG))
                        .withSorting(GlobalConfigResource.FIELD_DEF_SOURCE.getDisplayName())
                        .build(),
                GlobalConfigResource.FIELD_DEF_SOURCE.getDisplayName(),
                75);

        // Description
        dataGrid.addAutoResizableColumn(
                DataGridUtil.htmlColumnBuilder(
                                (ConfigPropertyRow row) ->
                                        SafeHtmlUtils.fromString(row.getDescription()))
                        .build(),
                GlobalConfigResource.FIELD_DEF_DESCRIPTION.getDisplayName(),
                750);
    }

    // The text, red if it says the nodes differ
    private static SafeHtml multipleOrPlain(final String text, final String multipleMessage) {
        final SafeHtml safeText = SafeHtmlUtil.getSafeHtml(text);
        return multipleMessage.equals(text)
                ? SafeHtmlUtil.getTemplate().spanWithClass("manage-global-property-list__multiple", safeText)
                : safeText;
    }

    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ManageGlobalPropertyList> {

    }
}
