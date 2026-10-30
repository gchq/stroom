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

package stroom.dictionary.client.component;

import stroom.cell.info.client.CommandLink;
import stroom.docref.DocRef;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.Consumer;

/// A dictionary's imports: documents, a row each, by name (a link that opens it); several can be
/// selected, the first at first. Add and Remove are on its toolbar.
public class ImportList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String READ_ONLY_SUFFIX = " (dictionary is read only)";

    @UiField
    ToolbarButton addButton;
    @UiField
    ToolbarButton removeButton;
    @UiField
    DataGrid<DocRef> dataGrid;

    private final String addTitle;
    private final String removeTitle;

    private final MultiSelectionModelImpl<DocRef> selectionModel;
    private Consumer<DocRef> onOpen = docRef -> {
    };
    private Runnable onAdd = () -> {
    };
    private Runnable onRemove = () -> {
    };

    /// Makes an empty list.
    public ImportList() {
        initWidget(BINDER.createAndBindUi(this));
        addTitle = addButton.getTitle();
        removeTitle = removeButton.getTitle();
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        addButton.setOnClick(() -> onAdd.run());
        removeButton.setOnClick(() -> onRemove.run());
        addColumn();
    }

    /// Shows documents, the first selected.
    ///
    /// @param docRefs The documents.
    public void setData(final List<DocRef> docRefs) {
        dataGrid.setRowData(0, docRefs);
        dataGrid.setRowCount(docRefs.size());
        if (NullSafe.hasItems(docRefs)) {
            selectionModel.setSelected(docRefs.get(0));
        } else {
            selectionModel.clear();
        }
    }

    /// @return The selection.
    public MultiSelectionModel<DocRef> getSelectionModel() {
        return selectionModel;
    }

    /// @param tableName The list's name, for assistive technology.
    public void setTableName(final String tableName) {
        dataGrid.setTableName(tableName);
    }

    /// Brings the buttons up to date: Add is enabled unless read only, and Remove when a document is
    /// also selected; when read only, they say so.
    ///
    /// @param readOnly Whether the dictionary is read only.
    public void updateActions(final boolean readOnly) {
        addButton.setEnabled(!readOnly);
        removeButton.setEnabled(!readOnly && NullSafe.hasItems(selectionModel.getSelectedItems()));
        // The names stay, with why the buttons are disabled when read only
        final String suffix = readOnly
                ? READ_ONLY_SUFFIX
                : "";
        addButton.setTitle(addTitle + suffix);
        removeButton.setTitle(removeTitle + suffix);
    }

    /// @param onOpen Given a document to open, when its link is pressed.
    public void setOnOpen(final Consumer<DocRef> onOpen) {
        this.onOpen = onOpen;
    }


    /// @param onAdd Run when Add is pressed.
    public void setOnAdd(final Runnable onAdd) {
        this.onAdd = onAdd;
    }

    /// @param onRemove Run when Remove is pressed.
    public void setOnRemove(final Runnable onRemove) {
        this.onRemove = onRemove;
    }

    // The one column, of the documents' names: a link that opens the document
    private void addColumn() {
        final Column<DocRef, CommandLink> nodeNameColumn = DataGridUtil.commandLinkColumnBuilder(
                        (DocRef docRef) -> docRef == null
                                ? null
                                : new CommandLink(
                                        docRef.getName(),
                                        "Open " + docRef.getType() + " '" + docRef.getName() + "'.",
                                        () -> onOpen.accept(docRef)))
                .build();
        DataGridUtil.addCommandLinkFieldUpdater(nodeNameColumn);
        dataGrid.addAutoResizableColumn(
                nodeNameColumn,
                DataGridUtil.headingBuilder("Document Name")
                        .build(),
                500);
    }

    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ImportList> {

    }
}
