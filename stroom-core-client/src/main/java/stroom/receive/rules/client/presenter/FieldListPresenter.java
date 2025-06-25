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

package stroom.receive.rules.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.query.api.datasource.QueryField;
import stroom.receive.rules.shared.ReceiveDataRules;
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

public class FieldListPresenter extends DocumentEditPresenter<PagerView, ReceiveDataRules> {

    private final MyDataGrid<QueryField> dataGrid;
    private final MultiSelectionModelImpl<QueryField> selectionModel;

    private final FieldEditPresenter fieldEditPresenter;
    private final ButtonView newButton;
    private final ButtonView editButton;
    private final ButtonView removeButton;
    private final ButtonView upButton;
    private final ButtonView downButton;
    private List<QueryField> fields;

    @Inject
    public FieldListPresenter(final EventBus eventBus,
                              final PagerView view,
                              final FieldEditPresenter fieldEditPresenter) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        this.fieldEditPresenter = fieldEditPresenter;

        newButton = getView().addButton(SvgPresets.NEW_ITEM);
        newButton.setTitle("New Field");
        editButton = getView().addButton(SvgPresets.EDIT);
        editButton.setTitle("Edit Field");
        removeButton = getView().addButton(SvgPresets.DELETE);
        removeButton.setTitle("Remove Field");
        upButton = getView().addButton(SvgPresets.UP);
        upButton.setTitle("Move Up");
        downButton = getView().addButton(SvgPresets.DOWN);
        downButton.setTitle("Move Down");

        addColumns();

        enableButtons();
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(newButton.addClickHandler(event -> {
            if (!isReadOnly()) {
                if (MouseUtil.isPrimary(event)) {
                    onAdd();
                }
            }
        }));
        registerHandler(editButton.addClickHandler(event -> {
            if (!isReadOnly()) {
                if (MouseUtil.isPrimary(event)) {
                    onEdit();
                }
            }
        }));
        registerHandler(removeButton.addClickHandler(event -> {
            if (!isReadOnly()) {
                if (MouseUtil.isPrimary(event)) {
                    onRemove();
                }
            }
        }));
        registerHandler(upButton.addClickHandler(event -> {
            if (!isReadOnly()) {
                if (MouseUtil.isPrimary(event)) {
                    moveSelectedFieldUp();
                }
            }
        }));
        registerHandler(downButton.addClickHandler(event -> {
            if (!isReadOnly()) {
                if (MouseUtil.isPrimary(event)) {
                    moveSelectedFieldDown();
                }
            }
        }));
        registerHandler(selectionModel.addSelectionHandler(event -> {
            if (!isReadOnly()) {
                enableButtons();
                if (event.getSelectionType().isDoubleSelect()) {
                    onEdit();
                }
            }
        }));
    }

    private void enableButtons() {
        newButton.setEnabled(!isReadOnly());

        if (!isReadOnly() && fields != null) {
            final QueryField selectedElement = selectionModel.getSelected();
            final boolean enabled = selectedElement != null;
            editButton.setEnabled(enabled);
            removeButton.setEnabled(enabled);
            if (enabled) {
                final int index = fields.indexOf(selectedElement);
                upButton.setEnabled(index > 0);
                downButton.setEnabled(index < fields.size() - 1);
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
    }

    private void addColumns() {
        addNameColumn();
        addTypeColumn();
        dataGrid.addEndColumn(new EndColumn<>());
    }

    private void addNameColumn() {
        dataGrid.addResizableColumn(new Column<QueryField, String>(new TextCell()) {
            @Override
            public String getValue(final QueryField row) {
                return row.getFldName();
            }
        }, "Name", 150);
    }

    private void addTypeColumn() {
        dataGrid.addResizableColumn(new Column<QueryField, String>(new TextCell()) {
            @Override
            public String getValue(final QueryField row) {
                return row.getFldType().getTypeName();
            }
        }, "Type", 100);
    }

    private void onAdd() {
        final Set<String> otherNames = fields.stream().map(QueryField::getFldName).collect(Collectors.toSet());

        fieldEditPresenter.read(QueryField.createText(""), otherNames);
        fieldEditPresenter.show("New Field", e -> {
            if (e.isOk()) {
                final QueryField newField = fieldEditPresenter.write();
                if (newField != null) {
                    fields.add(newField);
                    refresh();
                    e.hide();
                    DirtyEvent.fire(FieldListPresenter.this, true);
                } else {
                    e.reset();
                }
            } else {
                e.hide();
            }
        });
    }

    private void onEdit() {
        final QueryField field = selectionModel.getSelected();
        if (field != null) {
            final Set<String> otherNames = fields.stream().map(QueryField::getFldName).collect(Collectors.toSet());
            otherNames.remove(field.getFldName());

            fieldEditPresenter.read(field, otherNames);
            fieldEditPresenter.show("Edit Field", e -> {
                if (e.isOk()) {
                    final QueryField newField = fieldEditPresenter.write();
                    if (newField != null) {
                        final int index = fields.indexOf(field);
                        fields.remove(index);
                        fields.add(index, newField);

                        refresh();
                        e.hide();
                        DirtyEvent.fire(FieldListPresenter.this, true);
                    } else {
                        e.reset();
                    }
                } else {
                    e.hide();
                }
            });
        }
    }

    private void onRemove() {
        final List<QueryField> list = selectionModel.getSelectedItems();
        if (list != null && list.size() > 0) {
            String message = "Are you sure you want to delete the selected field?";
            if (list.size() > 1) {
                message = "Are you sure you want to delete the selected fields?";
            }

            ConfirmEvent.fire(this, message, result -> {
                if (result) {
                    fields.removeAll(list);
                    selectionModel.clear();
                    refresh();
                    DirtyEvent.fire(FieldListPresenter.this, true);
                }
            });
        }
    }

    private void moveSelectedFieldUp() {
        final QueryField selected = selectionModel.getSelected();
        if (selected != null) {
            final int index = fields.indexOf(selected);
            if (index > 0) {
                fields.remove(index);
                fields.add(index - 1, selected);

                refresh();
                enableButtons();
                DirtyEvent.fire(FieldListPresenter.this, true);
            }
        }
    }

    private void moveSelectedFieldDown() {
        final QueryField selected = selectionModel.getSelected();
        if (selected != null) {
            final int index = fields.indexOf(selected);
            if (index >= 0 && index < fields.size() - 1) {
                fields.remove(index);
                fields.add(index + 1, selected);

                refresh();
                enableButtons();
                DirtyEvent.fire(FieldListPresenter.this, true);
            }
        }
    }

    public void refresh() {
        if (fields == null) {
            fields = new ArrayList<>();
        }

        dataGrid.setRowData(0, fields);
        dataGrid.setRowCount(fields.size());
    }

    @Override
    protected void onRead(final DocRef docRef, final ReceiveDataRules document, final boolean readOnly) {
        enableButtons();
        if (document != null) {
            fields = document.getFields();
            refresh();
        }
    }

    @Override
    protected ReceiveDataRules onWrite(final ReceiveDataRules document) {
        document.setFields(fields);
        return document;
    }
}
