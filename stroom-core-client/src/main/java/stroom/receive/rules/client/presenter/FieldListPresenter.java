/*
 * Copyright 2016 Crown Copyright
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

package stroom.receive.rules.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.query.api.datasource.QueryField;
import stroom.receive.rules.client.component.FieldEditorController;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.client.DataGridComparatorFactory;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.string.CIKey;
import stroom.widget.button.client.ButtonView;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerView;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FieldListPresenter extends DocPresenter<PagerView, ReceiveDataRules> {

    private static final String NAME_FIELD = "name";
    private static final String TYPE_FIELD = "type";
    private static final String OBFUSCATED_FIELD = "obfuscated";

    private final DataGrid<QueryField> dataGrid;
    private final MultiSelectionModelImpl<QueryField> selectionModel;
    private final Provider<FieldEditorController> fieldEditorProvider;
    private final ButtonView newButton;
    private final ButtonView editButton;
    private final ButtonView removeButton;
    //    private final ButtonView upButton;
//    private final ButtonView downButton;
    private final DataGridComparatorFactory<QueryField> comparatorFactory;
    private final List<QueryField> fields = new ArrayList<>();
    private Set<CIKey> obfuscatedFields;

    @Inject
    public FieldListPresenter(final EventBus eventBus,
                              final PagerView view,
                              final Provider<FieldEditorController> fieldEditorProvider,
                              final UiConfigCache uiConfigCache) {
        super(eventBus, view);

        dataGrid = new DataGrid<>(this);
        dataGrid.setTableName("Fields");
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        this.fieldEditorProvider = fieldEditorProvider;

        getView().getToolbar().setName("Field actions");
        newButton = getView().getToolbar().button(SvgImage.ADD, "New Field", this::onAddClicked);
        editButton = getView().getToolbar().button(SvgImage.EDIT, "Edit Selected Field", this::onEditClicked);
        editButton.setEnabled(false);
        removeButton = getView().getToolbar().button(SvgImage.DELETE, "Delete Selected Fields", this::onRemoveClicked);
        removeButton.setEnabled(false);
//        upButton = getView().addButton(SvgPresets.UP);
//        upButton.setTitle("Move Up");
//        downButton = getView().addButton(SvgPresets.DOWN);
//        downButton.setTitle("Move Down");

        this.comparatorFactory = addColumns();
        enableButtons();

        uiConfigCache.get(extendedUiConfig -> {
            obfuscatedFields = CIKey.setOf(extendedUiConfig.getObfuscatedFields());
            refresh();
        });

//        own(upButton.addClickHandler(event -> {
//            if (!isReadOnly()) {
//                if (MouseUtil.isPrimary(event)) {
//                    moveSelectedFieldUp();
//                }
//            }
//        }));
//        own(downButton.addClickHandler(event -> {
//            if (!isReadOnly()) {
//                if (MouseUtil.isPrimary(event)) {
//                    moveSelectedFieldDown();
//                }
//            }
//        }));
        own(selectionModel.addSelectionHandler(event -> {
            if (!isReadOnly()) {
                enableButtons();
                if (event.getSelectionType().isDoubleSelect()) {
                    onEdit();
                }
            }
        }));
        own(dataGrid.addColumnSortHandler(event -> {
            GWT.log("Sort called");
            refresh();
        }));
    }

    private void onAddClicked() {
        if (!isReadOnly()) {
            onAdd();
        }
    }

    private void onEditClicked() {
        if (!isReadOnly()) {
            onEdit();
        }
    }

    private void onRemoveClicked() {
        if (!isReadOnly()) {
            onRemove();
        }
    }

    private void enableButtons() {
        newButton.setEnabled(!isReadOnly());

        if (!isReadOnly()) {
            final QueryField selectedElement = selectionModel.getSelected();
            final boolean enabled = selectedElement != null;
            editButton.setEnabled(enabled);
            removeButton.setEnabled(enabled);
            if (enabled) {
                final int index = fields.indexOf(selectedElement);
//                upButton.setEnabled(index > 0);
//                downButton.setEnabled(index < fields.size() - 1);
            } else {
//                upButton.setEnabled(false);
//                downButton.setEnabled(false);
            }
        } else {
            editButton.setEnabled(false);
            removeButton.setEnabled(false);
//            upButton.setEnabled(false);
//            downButton.setEnabled(false);
        }
    }

    private DataGridComparatorFactory<QueryField> addColumns() {
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
                                (final QueryField queryField) -> {
                                    final CIKey ciFieldName = CIKey.of(queryField.getFldName());
                                    return DataGridUtil.formatAsYesNo(obfuscatedFields.contains(ciFieldName));
                                })
                        .withSorting(OBFUSCATED_FIELD, true)
                        .build(),
                DataGridUtil.headingBuilder("Obfuscated?")
                        .withToolTip("Whether expression term values for this field are obfuscated when " +
                                     "the rules are passed to a Stroom-Proxy or not. These are controlled by " +
                                     "configuration properties. Speak to your administrator to change them.")
                        .build(),
                150);

        dataGrid.sort(nameColumn);

        return DataGridUtil.comparatorFactoryBuilder(dataGrid)
                .addField(NAME_FIELD, QueryField::getFldName)
                .addField(TYPE_FIELD, qryField ->
                        qryField.getFldType().getTypeName())
                .addField(OBFUSCATED_FIELD, qryField ->
                        DataGridUtil.formatAsYesNo(obfuscatedFields.contains(CIKey.of(qryField.getFldName()))))
                .addDefaultSortField(NAME_FIELD)
                .build();
    }

    private void onAdd() {
        final Set<CIKey> otherNames = fields.stream()
                .map(QueryField::getFldName)
                .map(CIKey::of)
                .collect(Collectors.toSet());

        fieldEditorProvider.get().edit(this, "New Field", QueryField.createText(""), otherNames, newField -> {
            fields.add(newField);
            refresh();
            onChange();
        });
    }

    private void onEdit() {
        final QueryField field = selectionModel.getSelected();
        if (field != null) {
            final Set<CIKey> otherNames = fields.stream()
                    .map(QueryField::getFldName)
                    .map(CIKey::of)
                    .collect(Collectors.toSet());

            otherNames.remove(CIKey.of(field.getFldName()));

            fieldEditorProvider.get().edit(this, "Edit Field", field, otherNames, newField -> {
                fields.set(fields.indexOf(field), newField);
                refresh();
                onChange();
            });
        }
    }

    private void onRemove() {
        final List<QueryField> list = selectionModel.getSelectedItems();
        if (NullSafe.hasItems(list)) {
            String message = "Are you sure you want to delete the selected field?";
            if (list.size() > 1) {
                message = "Are you sure you want to delete the selected fields?";
            }

            ConfirmEvent.fire(this, message, result -> {
                if (result) {
                    fields.removeAll(list);
                    selectionModel.clear();
                    refresh();
                    onChange();
                }
            });
        }
    }

//    private void moveSelectedFieldUp() {
//        final QueryField selected = selectionModel.getSelected();
//        if (selected != null) {
//            final int index = fields.indexOf(selected);
//            if (index > 0) {
//                fields.remove(index);
//                fields.add(index - 1, selected);
//
//                refresh();
//                enableButtons();
//                onChange();
//            }
//        }
//    }
//
//    private void moveSelectedFieldDown() {
//        final QueryField selected = selectionModel.getSelected();
//        if (selected != null) {
//            final int index = fields.indexOf(selected);
//            if (index >= 0 && index < fields.size() - 1) {
//                fields.remove(index);
//                fields.add(index + 1, selected);
//
//                refresh();
//                enableButtons();
//                onChange();
//            }
//        }
//    }

    public void refresh() {
        if (!fields.isEmpty()) {
            final Comparator<QueryField> comparator = comparatorFactory.create();
            NullSafe.consume(comparator, fields::sort);

//            final ColumnSortList columnSortList = dataGrid.getColumnSortList();
//            Comparator<QueryField> combinedComparator = null;
//            for (int i = 0; i < columnSortList.size(); i++) {
//                final ColumnSortInfo columnSortInfo = columnSortList.get(i);
//                final Column<?, ?> column = columnSortInfo.getColumn();
//                if (column instanceof OrderByColumn<?, ?> orderByColumn) {
//                    final String sortField = orderByColumn.getField();
//                    final boolean isAscending = columnSortInfo.isAscending();
//                    GWT.log("sortField " + i + ": " + sortField + " isAscending: " + isAscending);
//
//                    Comparator<QueryField> comparator = null;
//                    if (NAME_FIELD.equals(sortField)) {
//                        comparator = Comparator.comparing(QueryField::getFldName);
//                    } else if (TYPE_FIELD.equals(sortField)) {
//                        comparator = Comparator.comparing(qryField ->
//                                qryField.getFldType().getTypeName());
//                    } else if (OBFUSCATED_FIELD.equals(sortField)) {
//                        comparator = Comparator.comparing(qryField ->
//                                DataGridUtil.formatAsYesNo(obfuscatedFields.contains(
//                                CIKey.of(qryField.getFldName()))));
//                    }
//                    comparator = CompareUtil.reverseIf(comparator, !isAscending);
//                    combinedComparator = CompareUtil.combine(combinedComparator, comparator);
//                }
//            }
//            NullSafe.consume(combinedComparator, fields::sort);
        }

        dataGrid.setRowData(0, fields);
        dataGrid.setRowCount(fields.size());
    }

    @Override
    protected void onRead(final DocRef docRef, final ReceiveDataRules document) {
        enableButtons();
        if (document != null) {
            fields.clear();
            fields.addAll(document.getFields());
            refresh();
        }
    }

    @Override
    protected ReceiveDataRules onWrite(final ReceiveDataRules document) {
        return document.copy().fields(new ArrayList<>(fields)).build();
    }

    public List<QueryField> getFields() {
        return fields;
    }
}
