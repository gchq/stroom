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

package stroom.dashboard.client.component;

import stroom.docref.DocRef;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// A filter's dictionaries, a row each (its name), with Add and Remove; several can be selected, and
/// Remove is enabled while any are.
public class IncludeExcludeFilterDictionary extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    ToolbarButton addButton;
    @UiField
    ToolbarButton removeButton;
    @UiField
    DataGrid<DocRef> dataGrid;

    private final MultiSelectionModelImpl<DocRef> selectionModel;
    private Runnable onAdd = () -> {
    };
    private Runnable onRemove = () -> {
    };

    /// Makes an empty list.
    public IncludeExcludeFilterDictionary() {
        initWidget(BINDER.createAndBindUi(this));
        // The dictionaries are the filter's, held here, so there is nothing to refresh
        pager.getRefreshButton().setVisible(false);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        dataGrid.addAutoResizableColumn(DataGridUtil.textColumnBuilder(DocRef::getName).build(),
                DataGridUtil.headingBuilder("Dictionary Name").build(),
                500);

        addButton.setOnClick(() -> onAdd.run());
        removeButton.setOnClick(() -> onRemove.run());
        selectionModel.addSelectionHandler(event ->
                removeButton.setEnabled(NullSafe.hasItems(selectionModel.getSelectedItems())));
    }

    /// Shows dictionaries, the first selected.
    ///
    /// @param dictionaries The dictionaries.
    public void setData(final List<DocRef> dictionaries) {
        dataGrid.setRowData(0, dictionaries);
        dataGrid.setRowCount(dictionaries.size());
        if (NullSafe.hasItems(dictionaries)) {
            selectionModel.setSelected(dictionaries.get(0));
        } else {
            selectionModel.clear();
        }
    }

    /// @return The selection.
    public MultiSelectionModel<DocRef> getSelectionModel() {
        return selectionModel;
    }

    /// @param onAdd Run when Add is pressed.
    public void setOnAdd(final Runnable onAdd) {
        this.onAdd = onAdd;
    }

    /// @param onRemove Run when Remove is pressed.
    public void setOnRemove(final Runnable onRemove) {
        this.onRemove = onRemove;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, IncludeExcludeFilterDictionary> {

    }
}
