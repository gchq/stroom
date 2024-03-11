/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.index.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneIndexField;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class IndexFieldListPresenter extends DocumentEditPresenter<PagerView, LuceneIndexDoc> {

    private final MyDataGrid<LuceneIndexField> dataGrid;
    private final MultiSelectionModelImpl<LuceneIndexField> selectionModel;
    private final IndexFieldEditPresenter indexFieldEditPresenter;
    private final ButtonView newButton;
    private final ButtonView editButton;
    private final ButtonView removeButton;
    private final ButtonView upButton;
    private final ButtonView downButton;
    private List<LuceneIndexField> indexFields;
    private IndexFieldDataProvider<LuceneIndexField> dataProvider;

    private boolean readOnly = true;

    @Inject
    public IndexFieldListPresenter(final EventBus eventBus,
                                   final PagerView view,
                                   final IndexFieldEditPresenter indexFieldEditPresenter) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        this.indexFieldEditPresenter = indexFieldEditPresenter;

        newButton = getView().addButton(SvgPresets.NEW_ITEM);
        editButton = getView().addButton(SvgPresets.EDIT);
        removeButton = getView().addButton(SvgPresets.DELETE);
        upButton = getView().addButton(SvgPresets.UP);
        downButton = getView().addButton(SvgPresets.DOWN);

        addColumns();
        enableButtons();
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(newButton.addClickHandler(event -> {
            if (!readOnly) {
                if (MouseUtil.isPrimary(event)) {
                    onAdd();
                }
            }
        }));
        registerHandler(editButton.addClickHandler(event -> {
            if (!readOnly) {
                if (MouseUtil.isPrimary(event)) {
                    onEdit();
                }
            }
        }));
        registerHandler(removeButton.addClickHandler(event -> {
            if (!readOnly) {
                if (MouseUtil.isPrimary(event)) {
                    onRemove();
                }
            }
        }));
        registerHandler(upButton.addClickHandler(event -> {
            if (!readOnly) {
                if (MouseUtil.isPrimary(event)) {
                    moveSelectedFieldUp();
                }
            }
        }));
        registerHandler(downButton.addClickHandler(event -> {
            if (!readOnly) {
                if (MouseUtil.isPrimary(event)) {
                    moveSelectedFieldDown();
                }
            }
        }));
        registerHandler(selectionModel.addSelectionHandler(event -> {
            if (!readOnly) {
                enableButtons();
                if (event.getSelectionType().isDoubleSelect()) {
                    onEdit();
                }
            }
        }));
    }

    private void enableButtons() {
        newButton.setEnabled(!readOnly);
        if (!readOnly && indexFields != null) {
            final List<LuceneIndexField> fieldList = indexFields;
            final LuceneIndexField selectedElement = selectionModel.getSelected();
            final boolean enabled = selectedElement != null;
            editButton.setEnabled(enabled);
            removeButton.setEnabled(enabled);
            if (enabled) {
                final int index = fieldList.indexOf(selectedElement);
                upButton.setEnabled(index > 0);
                downButton.setEnabled(index < fieldList.size() - 1);
            } else {
                upButton.setEnabled(false);
                downButton.setEnabled(false);
            }
        } else {
            editButton.setEnabled(false);
            removeButton.setEnabled(false);
            upButton.setEnabled(false);
            downButton.setEnabled(false);
        }

        if (readOnly) {
            newButton.setTitle("New field disabled as index is read only");
            editButton.setTitle("Edit field disabled as index is read only");
            removeButton.setTitle("Remove field disabled as index is read only");
            upButton.setTitle("Move up disabled as index is read only");
            downButton.setTitle("Move down disabled as index is read only");
        } else {
            newButton.setTitle("New Field");
            editButton.setTitle("Edit Field");
            removeButton.setTitle("Remove Field");
            upButton.setTitle("Move Up");
            downButton.setTitle("Move Down");
        }
    }

    private void addColumns() {
        addNameColumn();
        addTypeColumn();
        addStoreColumn();
        addIndexColumn();
        addTermVectorColumn();
        addAnalyzerColumn();
        addCaseSensitiveColumn();
        dataGrid.addEndColumn(new EndColumn<>());
    }

    private void addNameColumn() {
        dataGrid.addResizableColumn(new Column<LuceneIndexField, String>(new TextCell()) {
            @Override
            public String getValue(final LuceneIndexField row) {
                return row.getName();
            }
        }, "Name", 150);
    }

    private void addTypeColumn() {
        dataGrid.addResizableColumn(new Column<LuceneIndexField, String>(new TextCell()) {
            @Override
            public String getValue(final LuceneIndexField row) {
                return row.getType().getDisplayValue();
            }
        }, "Type", 100);
    }

    private void addStoreColumn() {
        dataGrid.addResizableColumn(new Column<LuceneIndexField, String>(new TextCell()) {
            @Override
            public String getValue(final LuceneIndexField row) {
                return getYesNoString(row.isStored());
            }
        }, "Store", 100);
    }

    private void addIndexColumn() {
        dataGrid.addResizableColumn(new Column<LuceneIndexField, String>(new TextCell()) {
            @Override
            public String getValue(final LuceneIndexField row) {
                return getYesNoString(row.isIndexed());
            }
        }, "Index", 100);
    }

    private void addTermVectorColumn() {
        dataGrid.addResizableColumn(new Column<LuceneIndexField, String>(new TextCell()) {
            @Override
            public String getValue(final LuceneIndexField row) {
                return getYesNoString(row.isTermPositions());
            }
        }, "Positions", 100);
    }

    private void addAnalyzerColumn() {
        dataGrid.addResizableColumn(new Column<LuceneIndexField, String>(new TextCell()) {
            @Override
            public String getValue(final LuceneIndexField row) {
                return row.getAnalyzerType().getDisplayValue();
            }
        }, "Analyser", 100);
    }

    private void addCaseSensitiveColumn() {
        dataGrid.addResizableColumn(new Column<LuceneIndexField, String>(new TextCell()) {
            @Override
            public String getValue(final LuceneIndexField row) {
                return String.valueOf(row.isCaseSensitive());
            }
        }, "Case Sensitive", 100);
    }

    private String getYesNoString(final boolean bool) {
        if (bool) {
            return "Yes";
        }
        return "No";
    }

    private Set<String> getFieldNames() {
        return indexFields.stream().map(LuceneIndexField::getName).collect(Collectors.toSet());
    }

    private void onAdd() {
        final Set<String> otherNames = getFieldNames();

        indexFieldEditPresenter.read(LuceneIndexField.builder().build(), otherNames);
        indexFieldEditPresenter.show("New Field", event -> {
            if (event.isOk()) {
                try {
                    final LuceneIndexField indexField = indexFieldEditPresenter.write();
                    indexFields.add(indexField);
                    selectionModel.setSelected(indexField);
                    refresh();

                    event.hide();
                    DirtyEvent.fire(IndexFieldListPresenter.this, true);

                } catch (final RuntimeException e) {
                    AlertEvent.fireError(IndexFieldListPresenter.this, e.getMessage(), null);
                }
            } else {
                event.hide();
            }
        });
    }

    private void onEdit() {
        final LuceneIndexField existingField = selectionModel.getSelected();
        if (existingField != null) {
            final Set<String> otherNames = getFieldNames();
            otherNames.remove(existingField.getName());

            indexFieldEditPresenter.read(existingField, otherNames);
            indexFieldEditPresenter.show("Edit Field", event -> {
                if (event.isOk()) {
                    try {
                        final LuceneIndexField indexField = indexFieldEditPresenter.write();
                        if (!indexField.equals(existingField)) {
                            final List<LuceneIndexField> fieldList = indexFields;
                            final int index = fieldList.indexOf(existingField);
                            fieldList.remove(index);
                            fieldList.add(index, indexField);
                            selectionModel.setSelected(indexField);
                            refresh();

                            event.hide();
                            DirtyEvent.fire(IndexFieldListPresenter.this, true);
                        } else {
                            event.hide();
                        }

                    } catch (final RuntimeException e) {
                        AlertEvent.fireError(IndexFieldListPresenter.this, e.getMessage(), null);
                    }
                } else {
                    event.hide();
                }
            });
        }
    }

    private void onRemove() {
        final List<LuceneIndexField> list = selectionModel.getSelectedItems();
        if (list != null && list.size() > 0) {
            String message = "Are you sure you want to delete the selected field?";
            if (list.size() > 1) {
                message = "Are you sure you want to delete the selected fields?";
            }

            ConfirmEvent.fire(this, message, result -> {
                if (result) {
                    indexFields.removeAll(list);
                    selectionModel.clear();
                    refresh();
                    DirtyEvent.fire(IndexFieldListPresenter.this, true);
                }
            });
        }
    }

    private void moveSelectedFieldUp() {
        final LuceneIndexField selected = selectionModel.getSelected();
        final List<LuceneIndexField> fieldList = indexFields;
        if (selected != null) {
            final int index = fieldList.indexOf(selected);
            if (index > 0) {
                fieldList.remove(index);
                fieldList.add(index - 1, selected);

                refresh();
                enableButtons();
                DirtyEvent.fire(IndexFieldListPresenter.this, true);
            }
        }
    }

    private void moveSelectedFieldDown() {
        final LuceneIndexField selected = selectionModel.getSelected();
        final List<LuceneIndexField> fieldList = indexFields;
        if (selected != null) {
            final int index = fieldList.indexOf(selected);
            if (index >= 0 && index < fieldList.size() - 1) {
                fieldList.remove(index);
                fieldList.add(index + 1, selected);

                refresh();
                enableButtons();
                DirtyEvent.fire(IndexFieldListPresenter.this, true);
            }
        }
    }

    private void refresh() {
        if (indexFields == null) {
            indexFields = new ArrayList<>();
        }
        if (dataProvider == null) {
            this.dataProvider = new IndexFieldDataProvider<>();
            dataProvider.addDataDisplay(dataGrid);
        }
        dataProvider.setList(indexFields);
        dataProvider.refresh();
    }

    @Override
    protected void onRead(final DocRef docRef, final LuceneIndexDoc document, final boolean readOnly) {
        this.readOnly = readOnly;
        enableButtons();

        if (document != null) {
            indexFields = document.getFields();
        }
        refresh();
    }

    @Override
    protected LuceneIndexDoc onWrite(final LuceneIndexDoc document) {
        document.setFields(indexFields);
        return document;
    }
}
