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

package stroom.receive.rules.client.component;

import stroom.query.api.datasource.QueryField;
import stroom.util.client.DataGridComparatorFactory;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/// A rule set's fields, a row each (its name, type and whether its values are obfuscated), sorted by
/// the column chosen, with New, Edit and Delete; several can be selected, and a double click edits
/// one. When read only, the buttons are disabled.
public class FieldList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String NAME_FIELD = "name";
    private static final String TYPE_FIELD = "type";
    private static final String OBFUSCATED_FIELD = "obfuscated";

    @UiField
    ToolbarButton newButton;
    @UiField
    ToolbarButton editButton;
    @UiField
    ToolbarButton removeButton;
    @UiField
    DataGrid<QueryField> dataGrid;

    private final MultiSelectionModelImpl<QueryField> selectionModel;
    private DataGridComparatorFactory<QueryField> comparatorFactory;
    private Runnable onNew = () -> {
    };
    private Runnable onEdit = () -> {
    };
    private Runnable onRemove = () -> {
    };
    private Runnable onSortChange = () -> {
    };

    /// Makes an empty list, with no columns until [#addColumns(Predicate)].
    public FieldList() {
        initWidget(BINDER.createAndBindUi(this));
        selectionModel = dataGrid.addDefaultSelectionModel(true);

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
        enableButtons();

        selectionModel.addSelectionHandler(event -> {
            if (!isReadOnly()) {
                enableButtons();
                if (event.getSelectionType().isDoubleSelect()) {
                    onEdit.run();
                }
            }
        });
        dataGrid.addColumnSortHandler(event -> onSortChange.run());
    }

    /// Adds the columns, once.
    ///
    /// @param isObfuscated Whether a field's values are obfuscated when the rules are passed to a
    ///                     Stroom-Proxy.
    public void addColumns(final Predicate<QueryField> isObfuscated) {
        final Column<QueryField, String> nameColumn = DataGridUtil.textColumnBuilder(QueryField::getFldName)
                .withSorting(NAME_FIELD, true)
                .build();
        dataGrid.addResizableColumn(nameColumn,
                DataGridUtil.headingBuilder("Name")
                        .withToolTip("The name of the field.")
                        .build(),
                150);

        dataGrid.addResizableColumn(DataGridUtil.textColumnBuilder((QueryField queryField) ->
                                queryField.getFldType().getDisplayValue())
                        .withSorting(TYPE_FIELD, true)
                        .build(),
                DataGridUtil.headingBuilder("Type")
                        .withToolTip("The data type of this field.")
                        .build(),
                150);

        dataGrid.addResizableColumn(DataGridUtil.textColumnBuilder(
                                (final QueryField queryField) ->
                                        DataGridUtil.formatAsYesNo(isObfuscated.test(queryField)))
                        .withSorting(OBFUSCATED_FIELD, true)
                        .build(),
                DataGridUtil.headingBuilder("Obfuscated?")
                        .withToolTip("Whether expression term values for this field are obfuscated when " +
                                     "the rules are passed to a Stroom-Proxy or not. These are controlled by " +
                                     "configuration properties. Speak to your administrator to change them.")
                        .build(),
                150);

        dataGrid.sort(nameColumn);

        comparatorFactory = DataGridUtil.comparatorFactoryBuilder(dataGrid)
                .addField(NAME_FIELD, QueryField::getFldName)
                .addField(TYPE_FIELD, qryField ->
                        qryField.getFldType().getTypeName())
                .addField(OBFUSCATED_FIELD, qryField ->
                        DataGridUtil.formatAsYesNo(isObfuscated.test(qryField)))
                .addDefaultSortField(NAME_FIELD)
                .build();
    }

    /// @return How the fields are to be sorted, by the column chosen, or `null` for no order.
    public Comparator<QueryField> getComparator() {
        return comparatorFactory.create();
    }

    /// @param fields The fields, in the order to show them.
    public void setData(final List<QueryField> fields) {
        dataGrid.setRowData(0, fields);
        dataGrid.setRowCount(fields.size());
    }

    /// @return The selection.
    public MultiSelectionModelImpl<QueryField> getSelectionModel() {
        return selectionModel;
    }

    /// @param onNew Run when New is pressed (not when read only).
    public void setOnNew(final Runnable onNew) {
        this.onNew = onNew;
    }

    /// @param onEdit Run when Edit is pressed, or a field is double clicked (not when read only).
    public void setOnEdit(final Runnable onEdit) {
        this.onEdit = onEdit;
    }

    /// @param onRemove Run when Delete is pressed (not when read only).
    public void setOnRemove(final Runnable onRemove) {
        this.onRemove = onRemove;
    }

    /// @param onSortChange Run when the user sorts by another column.
    public void setOnSortChange(final Runnable onSortChange) {
        this.onSortChange = onSortChange;
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
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FieldList> {

    }
}
