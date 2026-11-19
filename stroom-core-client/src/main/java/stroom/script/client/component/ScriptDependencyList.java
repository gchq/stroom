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

package stroom.script.client.component;

import stroom.docref.DocRef;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// A script's dependencies, the scripts it needs, a row each (its name), with Add and Remove;
/// several can be selected. When read only, the buttons are disabled and say why.
public class ScriptDependencyList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String READ_ONLY_REASON = " (script is read only)";

    @UiField
    ToolbarButton addButton;
    @UiField
    ToolbarButton removeButton;
    @UiField
    DataGrid<DocRef> dataGrid;

    private final String addTitle;
    private final String removeTitle;
    private final MultiSelectionModelImpl<DocRef> selectionModel;
    private Runnable onAdd = () -> {
    };
    private Runnable onRemove = () -> {
    };

    /// Makes an empty list.
    public ScriptDependencyList() {
        initWidget(BINDER.createAndBindUi(this));
        addTitle = addButton.getTitle();
        removeTitle = removeButton.getTitle();
        selectionModel = dataGrid.addDefaultSelectionModel(true);

        // Name.
        final Column<DocRef, String> nameColumn = new Column<DocRef, String>(new TextCell()) {
            @Override
            public String getValue(final DocRef docRef) {
                return docRef.getName();
            }
        };
        dataGrid.addResizableColumn(nameColumn, "Name", 250);

        addButton.setOnClick(() -> onAdd.run());
        removeButton.setOnClick(() -> onRemove.run());
        selectionModel.addSelectionHandler(event -> enableButtons());
        enableButtons();
    }

    /// @param scripts The scripts depended on.
    public void setData(final List<DocRef> scripts) {
        dataGrid.setRowData(0, scripts);
        dataGrid.setRowCount(scripts.size());
    }

    /// @return The selection.
    public MultiSelectionModel<DocRef> getSelectionModel() {
        return selectionModel;
    }

    /// @param scriptName The script's name, which names the table.
    public void setScriptName(final String scriptName) {
        dataGrid.setTableName("Script '" + scriptName + "' Dependencies");
    }

    /// @param onAdd Run when Add is pressed.
    public void setOnAdd(final Runnable onAdd) {
        this.onAdd = onAdd;
    }

    /// @param onRemove Run when Remove is pressed.
    public void setOnRemove(final Runnable onRemove) {
        this.onRemove = onRemove;
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        enableButtons();
    }

    private void enableButtons() {
        final boolean readOnly = isReadOnly();
        addButton.setEnabled(!readOnly);
        removeButton.setEnabled(!readOnly && selectionModel.getSelected() != null);

        final String reason = readOnly
                ? READ_ONLY_REASON
                : "";
        addButton.setTitle(addTitle + reason);
        removeButton.setTitle(removeTitle + reason);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ScriptDependencyList> {

    }
}
