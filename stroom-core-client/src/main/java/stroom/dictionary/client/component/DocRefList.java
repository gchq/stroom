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
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.Consumer;

/// Documents, a row each, by name (a link that opens it, or plain text); several can be selected,
/// the first at first. Its owner adds its buttons to the toolbar.
public class DocRefList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<DocRef> dataGrid;

    private final MultiSelectionModelImpl<DocRef> selectionModel;
    private Consumer<DocRef> onOpen = docRef -> {
    };

    /// Makes an empty list, with no column until [#addColumn(String, boolean)].
    public DocRefList() {
        initWidget(BINDER.createAndBindUi(this));
        selectionModel = dataGrid.addDefaultSelectionModel(true);
    }

    /// Adds the list's one column, of the documents' names.
    ///
    /// @param columnName  The column's heading.
    /// @param hasOpenLink Whether a name is a link that opens its document.
    public void addColumn(final String columnName, final boolean hasOpenLink) {
        if (hasOpenLink) {
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
                    DataGridUtil.headingBuilder(columnName)
                            .build(),
                    500);
        } else {
            dataGrid.addAutoResizableColumn(DataGridUtil.textColumnBuilder(DocRef::getName).build(),
                    DataGridUtil.headingBuilder(columnName).build(),
                    500);
        }
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

    /// @return The toolbar beside the pager, for the owner's buttons.
    public Toolbar getToolbar() {
        return pager.getToolbar();
    }

    /// @param onOpen Given a document to open, when its link is pressed.
    public void setOnOpen(final Consumer<DocRef> onOpen) {
        this.onOpen = onOpen;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DocRefList> {

    }
}
