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

package stroom.search.solr.client.component;

import stroom.search.solr.shared.SolrIndexField;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.List;
import java.util.function.Function;

/// A Solr index's fields, a row each (its name, types, default value and its schema flags), with New,
/// Edit and Remove; several can be selected, and a double click edits one. Below them, when its
/// schema was last synchronised and the messages from doing so. When read only, the buttons are
/// disabled and say so.
public class SolrIndexFieldList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String READ_ONLY_REASON = " (index is read only)";

    @UiField
    PagerPanel pager;
    @UiField
    ToolbarButton newButton;
    @UiField
    ToolbarButton editButton;
    @UiField
    ToolbarButton removeButton;
    @UiField
    DataGrid<SolrIndexField> dataGrid;
    @UiField
    DivElement lastSynchronised;
    @UiField
    SpanElement lastSynchronisedTime;
    @UiField
    DivElement messages;

    private final String newTitle;
    private final String editTitle;
    private final String removeTitle;
    private final MultiSelectionModelImpl<SolrIndexField> selectionModel;
    private Runnable onNew = () -> {
    };
    private Runnable onEdit = () -> {
    };
    private Runnable onRemove = () -> {
    };

    /// Makes an empty list.
    public SolrIndexFieldList() {
        initWidget(BINDER.createAndBindUi(this));
        newTitle = newButton.getTitle();
        editTitle = editButton.getTitle();
        removeTitle = removeButton.getTitle();
        // Rows keyed by name, so a row whose values change is still the same row
        dataGrid.setKeyProvider(SolrIndexField::getFldName);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        UIObject.setVisible(lastSynchronised, false);

        newButton.setOnClick(() -> {
            if (!isReadOnly()) {
                onNew.run();
            }
        });
        editButton.setOnClick(() -> {
            if (!isReadOnly()) {
                onEdit.run();
            }
        });
        removeButton.setOnClick(() -> {
            if (!isReadOnly()) {
                onRemove.run();
            }
        });

        addColumns();
        enableButtons();

        selectionModel.addSelectionHandler(event -> {
            if (!isReadOnly()) {
                enableButtons();
                if (event.getSelectionType().isDoubleSelect()) {
                    onEdit.run();
                }
            }
        });
    }

    /// @param dataProvider Gives the list its rows.
    public void setDataProvider(final AbstractDataProvider<SolrIndexField> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection.
    public MultiSelectionModelImpl<SolrIndexField> getSelectionModel() {
        return selectionModel;
    }

    /// @param indexName The index's name, which names the table.
    public void setIndexName(final String indexName) {
        dataGrid.setTableName("Solr Index '" + indexName + "' Fields");
    }

    /// Shows the schema's synchronisation state.
    ///
    /// @param lastSynchronisedTime When it was last synchronised, formatted, or `null` if never.
    /// @param messageList          The messages from synchronising it, a line each.
    public void setSyncState(final String lastSynchronisedTime, final List<String> messageList) {
        UIObject.setVisible(lastSynchronised, lastSynchronisedTime != null);
        this.lastSynchronisedTime.setInnerText(lastSynchronisedTime == null
                ? ""
                : lastSynchronisedTime);
        messages.removeAllChildren();
        for (final String message : messageList) {
            final DivElement line = Document.get().createDivElement();
            line.setInnerText(message);
            messages.appendChild(line);
        }
    }

    /// @param onNew Run when New is pressed (not when read only).
    public void setOnNew(final Runnable onNew) {
        this.onNew = onNew;
    }

    /// @param onEdit Run when Edit is pressed, or a field is double clicked (not when read only).
    public void setOnEdit(final Runnable onEdit) {
        this.onEdit = onEdit;
    }

    /// @param onRemove Run when Remove is pressed (not when read only).
    public void setOnRemove(final Runnable onRemove) {
        this.onRemove = onRemove;
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        enableButtons();
    }

    private void enableButtons() {
        final boolean readOnly = isReadOnly();
        newButton.setEnabled(!readOnly);
        final boolean enabled = !readOnly && selectionModel.getSelected() != null;
        editButton.setEnabled(enabled);
        removeButton.setEnabled(enabled);

        final String reason = readOnly
                ? READ_ONLY_REASON
                : "";
        newButton.setTitle(newTitle + reason);
        editButton.setTitle(editTitle + reason);
        removeButton.setTitle(removeTitle + reason);
    }

    private void addColumns() {
        addStringColumn("Name", 150, SolrIndexField::getFldName);
        addStringColumn("Type", 100, row -> row.getFldType().getDisplayValue());
        addStringColumn("Field Type", 100, SolrIndexField::getNativeType);
        addStringColumn("Default Value", 100, SolrIndexField::getDefaultValue);
        addBooleanColumn("Stored", SolrIndexField::isStored);
        addBooleanColumn("Indexed", SolrIndexField::isIndexed);
        addBooleanColumn("Uninvertible", SolrIndexField::isUninvertible);
        addBooleanColumn("Doc Values", SolrIndexField::isDocValues);
        addBooleanColumn("Multi Valued", SolrIndexField::isMultiValued);
        addBooleanColumn("Required", SolrIndexField::isRequired);
        addBooleanColumn("Omit Norms", SolrIndexField::isOmitNorms);
        addBooleanColumn("Omit Term Freq And Positions", SolrIndexField::isOmitTermFreqAndPositions);
        addBooleanColumn("Omit Positions", SolrIndexField::isOmitPositions);
        addBooleanColumn("Term Vectors", SolrIndexField::isTermVectors);
        addBooleanColumn("Term Positions", SolrIndexField::isTermPositions);
        addBooleanColumn("Term Offsets", SolrIndexField::isTermOffsets);
        addBooleanColumn("Term Payloads", SolrIndexField::isTermPayloads);
        addBooleanColumn("Sort Missing First", SolrIndexField::isSortMissingFirst);
        addBooleanColumn("Sort Missing Last", SolrIndexField::isSortMissingLast);
    }

    private void addStringColumn(final String name, final int width, final Function<SolrIndexField, String> function) {
        dataGrid.addResizableColumn(new Column<SolrIndexField, String>(new TextCell()) {
            @Override
            public String getValue(final SolrIndexField row) {
                return function.apply(row);
            }
        }, name, width);
    }

    private void addBooleanColumn(final String name, final Function<SolrIndexField, Boolean> function) {
        dataGrid.addResizableColumn(new Column<SolrIndexField, String>(new TextCell()) {
            @Override
            public String getValue(final SolrIndexField row) {
                return function.apply(row)
                        ? "Yes"
                        : "No";
            }
        }, name, 100);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, SolrIndexFieldList> {

    }
}
