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

package stroom.policy.client.presenter;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.alert.client.event.ConfirmEvent;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.event.HasDirtyHandlers;
import stroom.entity.client.presenter.HasRead;
import stroom.entity.client.presenter.HasWrite;
import stroom.query.shared.IndexField;
import stroom.query.shared.IndexFields;
import stroom.policy.shared.DataReceiptPolicy;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.presenter.PopupUiHandlers;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FieldListPresenter extends MyPresenterWidget<DataGridView<IndexField>>
        implements HasRead<DataReceiptPolicy>, HasWrite<DataReceiptPolicy>, HasDirtyHandlers {
    private final FieldEditPresenter indexFieldEditPresenter;
    private final ButtonView newButton;
    private final ButtonView editButton;
    private final ButtonView removeButton;
    private final ButtonView upButton;
    private final ButtonView downButton;
    private IndexFields indexFields;

    @SuppressWarnings("unchecked")
    @Inject
    public FieldListPresenter(final EventBus eventBus,
                              final FieldEditPresenter indexFieldEditPresenter) {
        super(eventBus, new DataGridViewImpl<IndexField>(true, true));
        this.indexFieldEditPresenter = indexFieldEditPresenter;

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
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(newButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                onAdd();
            }
        }));
        registerHandler(editButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                onEdit();
            }
        }));
        registerHandler(removeButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                onRemove();
            }
        }));
        registerHandler(upButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                moveSelectedFieldUp();
            }
        }));
        registerHandler(downButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                moveSelectedFieldDown();
            }
        }));
        registerHandler(getView().getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                onEdit();
            }
        }));
    }

    private void enableButtons() {
        if (indexFields != null && indexFields.getIndexFields() != null) {
            final List<IndexField> fieldList = indexFields.getIndexFields();
            final IndexField selectedElement = getView().getSelectionModel().getSelected();
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
    }

    private void addColumns() {
        addNameColumn();
        addTypeColumn();
        getView().addEndColumn(new EndColumn<IndexField>());
    }

    private void addNameColumn() {
        getView().addResizableColumn(new Column<IndexField, String>(new TextCell()) {
            @Override
            public String getValue(final IndexField row) {
                return row.getFieldName();
            }
        }, "Name", 150);
    }

    private void addTypeColumn() {
        getView().addResizableColumn(new Column<IndexField, String>(new TextCell()) {
            @Override
            public String getValue(final IndexField row) {
                return row.getFieldType().getDisplayValue();
            }
        }, "Type", 100);
    }

    private void onAdd() {
        final IndexField indexField = new IndexField();
        final Set<String> otherNames = indexFields.getFieldNames();

        indexFieldEditPresenter.read(indexField, otherNames);
        indexFieldEditPresenter.show("New Field", new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    if (indexFieldEditPresenter.write(indexField)) {
                        indexFields.add(indexField);
                        refresh();
                        indexFieldEditPresenter.hide();
                        DirtyEvent.fire(FieldListPresenter.this, true);
                    }
                } else {
                    indexFieldEditPresenter.hide();
                }
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                // Ignore.
            }
        });
    }

    private void onEdit() {
        final IndexField indexField = getView().getSelectionModel().getSelected();
        if (indexField != null) {
            final Set<String> otherNames = indexFields.getFieldNames();
            otherNames.remove(indexField.getFieldName());

            indexFieldEditPresenter.read(indexField, otherNames);
            indexFieldEditPresenter.show("Edit Field", new PopupUiHandlers() {
                @Override
                public void onHideRequest(final boolean autoClose, final boolean ok) {
                    if (ok) {
                        if (indexFieldEditPresenter.write(indexField)) {
                            refresh();
                            indexFieldEditPresenter.hide();
                            DirtyEvent.fire(FieldListPresenter.this, true);
                        }
                    } else {
                        indexFieldEditPresenter.hide();
                    }
                }

                @Override
                public void onHide(final boolean autoClose, final boolean ok) {
                    // Ignore.
                }
            });
        }
    }

    private void onRemove() {
        final List<IndexField> list = getView().getSelectionModel().getSelectedItems();
        if (list != null && list.size() > 0) {
            String message = "Are you sure you want to delete the selected field?";
            if (list.size() > 1) {
                message = "Are you sure you want to delete the selected fields?";
            }

            ConfirmEvent.fire(this, message, result -> {
                if (result) {
                    indexFields.getIndexFields().removeAll(list);
                    getView().getSelectionModel().clear();
                    refresh();
                    DirtyEvent.fire(FieldListPresenter.this, true);
                }
            });
        }
    }

    private void moveSelectedFieldUp() {
        final IndexField selected = getView().getSelectionModel().getSelected();
        final List<IndexField> fieldList = indexFields.getIndexFields();
        if (selected != null) {
            final int index = fieldList.indexOf(selected);
            if (index > 0) {
                fieldList.remove(index);
                fieldList.add(index - 1, selected);

                refresh();
                enableButtons();
                DirtyEvent.fire(FieldListPresenter.this, true);
            }
        }
    }

    private void moveSelectedFieldDown() {
        final IndexField selected = getView().getSelectionModel().getSelected();
        final List<IndexField> fieldList = indexFields.getIndexFields();
        if (selected != null) {
            final int index = fieldList.indexOf(selected);
            if (index >= 0 && index < fieldList.size() - 1) {
                fieldList.remove(index);
                fieldList.add(index + 1, selected);

                refresh();
                enableButtons();
                DirtyEvent.fire(FieldListPresenter.this, true);
            }
        }
    }

    public void refresh() {
        if (indexFields == null) {
            indexFields = new IndexFields(new ArrayList<>());
        }

        getView().setRowData(0, indexFields.getIndexFields());
        getView().setRowCount(indexFields.getIndexFields().size());
    }

    @Override
    public void read(final DataReceiptPolicy policy) {
        if (policy != null) {
            indexFields = policy.getFields();
            refresh();
        }
    }

    @Override
    public void write(final DataReceiptPolicy policy) {
        policy.setFields(indexFields);
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }
}
