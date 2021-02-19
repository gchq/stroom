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

package stroom.search.elastic.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.client.presenter.HasWrite;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.query.api.v2.DocRef;
import stroom.search.elastic.shared.ElasticIndex;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.popup.client.presenter.PopupUiHandlers;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ElasticIndexFieldListPresenter extends MyPresenterWidget<ElasticIndexFieldListPresenter.ElasticIndexFieldListView>
        implements HasDocumentRead<ElasticIndex>, HasWrite<ElasticIndex>, HasDirtyHandlers, ReadOnlyChangeHandler {
    private final DataGridView<ElasticIndexField> dataGridView;
    private final ElasticIndexFieldEditPresenter indexFieldEditPresenter;
    private final ClientDispatchAsync dispatcher;
    private final ButtonView newButton;
    private final ButtonView editButton;
    private final ButtonView removeButton;
    private ElasticIndex index;
    private List<ElasticIndexField> fields;
    private ElasticIndexFieldDataProvider<ElasticIndexField> dataProvider;

    private boolean readOnly = true;

    @Inject
    public ElasticIndexFieldListPresenter(final EventBus eventBus,
                                          final ElasticIndexFieldListView view,
                                          final ElasticIndexFieldEditPresenter indexFieldEditPresenter,
                                          final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.indexFieldEditPresenter = indexFieldEditPresenter;
        this.dispatcher = dispatcher;

        dataGridView = new DataGridViewImpl<>(true, true);
        view.setDataGridView(dataGridView);
        newButton = dataGridView.addButton(SvgPresets.NEW_ITEM);
        editButton = dataGridView.addButton(SvgPresets.EDIT);
        removeButton = dataGridView.addButton(SvgPresets.DELETE);

        addColumns();
        enableButtons();
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(newButton.addClickHandler(event -> {
            if (!readOnly) {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    onAdd();
                }
            }
        }));
        registerHandler(editButton.addClickHandler(event -> {
            if (!readOnly) {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    onEdit();
                }
            }
        }));
        registerHandler(removeButton.addClickHandler(event -> {
            if (!readOnly) {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    onRemove();
                }
            }
        }));
        registerHandler(dataGridView.getSelectionModel().addSelectionHandler(event -> {
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
        if (!readOnly && fields != null) {
            final ElasticIndexField selectedElement = dataGridView.getSelectionModel().getSelected();
            final boolean enabled = selectedElement != null;
            editButton.setEnabled(enabled);
            removeButton.setEnabled(enabled);
        } else {
            editButton.setEnabled(false);
            removeButton.setEnabled(false);
        }

        if (readOnly) {
            newButton.setTitle("New field disabled as index is read only");
            editButton.setTitle("Edit field disabled as index is read only");
            removeButton.setTitle("Remove field disabled as index is read only");
        } else {
            newButton.setTitle("New Field");
            editButton.setTitle("Edit Field");
            removeButton.setTitle("Remove Field");
        }
    }

    private void addColumns() {
        addStringColumn("Name", 150, ElasticIndexField::getFieldName);
        addStringColumn("Use", row -> row.getFieldUse().getDisplayValue());
        addStringColumn("Type", ElasticIndexField::getFieldType);
        addBooleanColumn("Stored", ElasticIndexField::isStored);
        addBooleanColumn("Indexed", ElasticIndexField::isIndexed);
        dataGridView.addEndColumn(new EndColumn<>());
    }

    private void addStringColumn(final String name, final Function<ElasticIndexField, String> function) {
        addStringColumn(name, 100, function);
    }

    private void addStringColumn(final String name, final int width, final Function<ElasticIndexField, String> function) {
        dataGridView.addResizableColumn(new Column<ElasticIndexField, String>(new TextCell()) {
            @Override
            public String getValue(final ElasticIndexField row) {
                return function.apply(row);
            }
        }, name, width);
    }

    private void addBooleanColumn(final String name, final Function<ElasticIndexField, Boolean> function) {
        dataGridView.addResizableColumn(new Column<ElasticIndexField, String>(new TextCell()) {
            @Override
            public String getValue(final ElasticIndexField row) {
                return getYesNoString(function.apply(row));
            }
        }, name, 100);
    }

    private String getYesNoString(final boolean bool) {
        if (bool) {
            return "Yes";
        }
        return "No";
    }

    private void onAdd() {
        final Set<String> otherNames = fields.stream().map(ElasticIndexField::getFieldName).collect(Collectors.toSet());

        fetchFieldTypes(fieldTypes -> {
            indexFieldEditPresenter.read(new ElasticIndexField(), otherNames, fieldTypes);
            indexFieldEditPresenter.show("New Field", new PopupUiHandlers() {
                @Override
                public void onHideRequest(final boolean autoClose, final boolean ok) {
                    if (ok) {
                        final ElasticIndexField indexField = new ElasticIndexField();
                        if (indexFieldEditPresenter.write(indexField)) {
                            fields.add(indexField);
                            fields.sort(Comparator.comparing(ElasticIndexField::getFieldName, String.CASE_INSENSITIVE_ORDER));
                            dataGridView.getSelectionModel().setSelected(indexField);
                            refresh();

                            indexFieldEditPresenter.hide();
                            DirtyEvent.fire(ElasticIndexFieldListPresenter.this, true);
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
        });
    }

    private void onEdit() {
        final ElasticIndexField existingField = dataGridView.getSelectionModel().getSelected();
        if (existingField != null) {
            final Set<String> otherNames = fields.stream().map(ElasticIndexField::getFieldName).collect(Collectors.toSet());
            otherNames.remove(existingField.getFieldName());

            fetchFieldTypes(fieldTypes -> {
                indexFieldEditPresenter.read(existingField, otherNames, fieldTypes);
                indexFieldEditPresenter.show("Edit Field", new PopupUiHandlers() {
                    @Override
                    public void onHideRequest(final boolean autoClose, final boolean ok) {
                        if (ok) {
                            final ElasticIndexField indexField = new ElasticIndexField();
                            if (indexFieldEditPresenter.write(indexField)) {
                                if (!indexField.equals(existingField)) {
                                    fields.remove(existingField);
                                    fields.add(indexField);
                                    fields.sort(Comparator.comparing(ElasticIndexField::getFieldName, String.CASE_INSENSITIVE_ORDER));
                                    dataGridView.getSelectionModel().setSelected(indexField);
                                    refresh();

                                    indexFieldEditPresenter.hide();
                                    DirtyEvent.fire(ElasticIndexFieldListPresenter.this, true);
                                } else {
                                    indexFieldEditPresenter.hide();
                                }
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
            });
        }
    }

    private void fetchFieldTypes(final Consumer<List<String>> consumer) {
        dispatcher.exec(new FetchElasticTypesAction(index))
                .onSuccess(result -> {
                    final List<String> fieldTypes = new ArrayList<>();
                    result.forEach(sharedString -> fieldTypes.add(sharedString.toString()));
                    consumer.accept(fieldTypes);
                })
                .onFailure(throwable -> AlertEvent.fireError(ElasticIndexFieldListPresenter.this,
                        "Unable to connect to Elastic please check connection",
                        throwable.getMessage(),
                        null));
    }

    private void onRemove() {
        final List<ElasticIndexField> list = dataGridView.getSelectionModel().getSelectedItems();
        if (list != null && list.size() > 0) {
            String message = "Are you sure you want to delete the selected field?";
            if (list.size() > 1) {
                message = "Are you sure you want to delete the selected fields?";
            }

            ConfirmEvent.fire(this, message, result -> {
                if (result) {
                    fields.removeAll(list);

                    if (index.getDeletedFields() == null) {
                        index.setDeletedFields(new ArrayList<>());
                    }
                    index.getDeletedFields().addAll(list);

                    dataGridView.getSelectionModel().clear();
                    refresh();
                    DirtyEvent.fire(ElasticIndexFieldListPresenter.this, true);
                }
            });
        }
    }

    private void refresh() {
        if (fields == null) {
            fields = new ArrayList<>();
        }
        if (dataProvider == null) {
            this.dataProvider = new ElasticIndexFieldDataProvider<>();
            dataProvider.addDataDisplay(dataGridView.getDataDisplay());
        }
        dataProvider.setList(fields);
        dataProvider.refresh();
    }

    @Override
    public void read(final DocRef docRef,
                     final ElasticIndex index) {
        this.index = index;
        if (index != null) {
            fields = index.getFields();

            final ElasticSynchState state = index.getElasticSynchState();
            final StringBuilder sb = new StringBuilder();
            if (state != null) {
                if (state.getLastSynchronized() != null) {
                    sb.append("<b>Last synchronised:</b> ");
                    sb.append(ClientDateUtil.toISOString(index.getElasticSynchState().getLastSynchronized()));
                    sb.append("</br>");
                }
                for (final String message : state.getMessages()) {
                    sb.append(message);
                    sb.append("</br>");
                }
            }
            getView().setSynchState(sb.toString());
        }
        refresh();
    }

    @Override
    public void write(final ElasticIndex entity) {
        entity.setFields(fields);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        enableButtons();
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public interface ElasticIndexFieldListView extends View {
        void setDataGridView(final View view);

        void setSynchState(final String syncState);
    }
}
