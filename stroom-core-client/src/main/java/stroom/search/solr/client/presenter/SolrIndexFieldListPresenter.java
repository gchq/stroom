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

package stroom.search.solr.client.presenter;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.client.presenter.HasWrite;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.search.solr.shared.FetchSolrTypesAction;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.search.solr.shared.SolrIndexField;
import stroom.search.solr.shared.SolrSynchState;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.popup.client.presenter.PopupUiHandlers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SolrIndexFieldListPresenter extends MyPresenterWidget<SolrIndexFieldListPresenter.SolrIndexFieldListView>
        implements HasDocumentRead<SolrIndexDoc>, HasWrite<SolrIndexDoc>, HasDirtyHandlers, ReadOnlyChangeHandler {
    private final DataGridView<SolrIndexField> dataGridView;
    private final SolrIndexFieldEditPresenter indexFieldEditPresenter;
    private final ClientDispatchAsync dispatcher;
    private final ButtonView newButton;
    private final ButtonView editButton;
    private final ButtonView removeButton;
    private SolrIndexDoc index;
    private List<SolrIndexField> fields;
    private SolrIndexFieldDataProvider<SolrIndexField> dataProvider;

    private boolean readOnly = true;

    @Inject
    public SolrIndexFieldListPresenter(final EventBus eventBus,
                                       final SolrIndexFieldListView view,
                                       final SolrIndexFieldEditPresenter indexFieldEditPresenter,
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
            final SolrIndexField selectedElement = dataGridView.getSelectionModel().getSelected();
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
        addStringColumn("Name", 150, SolrIndexField::getFieldName);
        addStringColumn("Use", row -> row.getFieldUse().getDisplayValue());
        addStringColumn("Type", SolrIndexField::getFieldType);
        addStringColumn("Default Value", SolrIndexField::getDefaultValue);
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
        dataGridView.addEndColumn(new EndColumn<>());
    }

    private void addStringColumn(final String name, final Function<SolrIndexField, String> function) {
        addStringColumn(name, 100, function);
    }

    private void addStringColumn(final String name, final int width, final Function<SolrIndexField, String> function) {
        dataGridView.addResizableColumn(new Column<SolrIndexField, String>(new TextCell()) {
            @Override
            public String getValue(final SolrIndexField row) {
                return function.apply(row);
            }
        }, name, width);
    }

    private void addBooleanColumn(final String name, final Function<SolrIndexField, Boolean> function) {
        dataGridView.addResizableColumn(new Column<SolrIndexField, String>(new TextCell()) {
            @Override
            public String getValue(final SolrIndexField row) {
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
        final Set<String> otherNames = fields.stream().map(SolrIndexField::getFieldName).collect(Collectors.toSet());

        fetchFieldTypes(fieldTypes -> {
            indexFieldEditPresenter.read(new SolrIndexField(), otherNames, fieldTypes);
            indexFieldEditPresenter.show("New Field", new PopupUiHandlers() {
                @Override
                public void onHideRequest(final boolean autoClose, final boolean ok) {
                    if (ok) {
                        final SolrIndexField indexField = new SolrIndexField();
                        if (indexFieldEditPresenter.write(indexField)) {
                            fields.add(indexField);
                            fields.sort(Comparator.comparing(SolrIndexField::getFieldName, String.CASE_INSENSITIVE_ORDER));
                            dataGridView.getSelectionModel().setSelected(indexField);
                            refresh();

                            indexFieldEditPresenter.hide();
                            DirtyEvent.fire(SolrIndexFieldListPresenter.this, true);
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
        final SolrIndexField existingField = dataGridView.getSelectionModel().getSelected();
        if (existingField != null) {
            final Set<String> otherNames = fields.stream().map(SolrIndexField::getFieldName).collect(Collectors.toSet());
            otherNames.remove(existingField.getFieldName());

            fetchFieldTypes(fieldTypes -> {
                indexFieldEditPresenter.read(existingField, otherNames, fieldTypes);
                indexFieldEditPresenter.show("Edit Field", new PopupUiHandlers() {
                    @Override
                    public void onHideRequest(final boolean autoClose, final boolean ok) {
                        if (ok) {
                            final SolrIndexField indexField = new SolrIndexField();
                            if (indexFieldEditPresenter.write(indexField)) {
                                if (!indexField.equals(existingField)) {
                                    fields.remove(existingField);
                                    fields.add(indexField);
                                    fields.sort(Comparator.comparing(SolrIndexField::getFieldName, String.CASE_INSENSITIVE_ORDER));
                                    dataGridView.getSelectionModel().setSelected(indexField);
                                    refresh();

                                    indexFieldEditPresenter.hide();
                                    DirtyEvent.fire(SolrIndexFieldListPresenter.this, true);
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
        dispatcher.exec(new FetchSolrTypesAction(index))
                .onSuccess(result -> {
                    final List<String> fieldTypes = new ArrayList<>();
                    result.forEach(sharedString -> fieldTypes.add(sharedString.toString()));
                    consumer.accept(fieldTypes);
                })
                .onFailure(throwable -> AlertEvent.fireError(SolrIndexFieldListPresenter.this,
                        "Unable to connect to Solr please check connection",
                        throwable.getMessage(),
                        null));
    }

    private void onRemove() {
        final List<SolrIndexField> list = dataGridView.getSelectionModel().getSelectedItems();
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
                    DirtyEvent.fire(SolrIndexFieldListPresenter.this, true);
                }
            });
        }
    }

    private void refresh() {
        if (fields == null) {
            fields = new ArrayList<>();
        }
        if (dataProvider == null) {
            this.dataProvider = new SolrIndexFieldDataProvider<>();
            dataProvider.addDataDisplay(dataGridView.getDataDisplay());
        }
        dataProvider.setList(fields);
        dataProvider.refresh();
    }

    @Override
    public void read(final DocRef docRef,
                     final SolrIndexDoc index) {
        this.index = index;
        if (index != null) {
            fields = index.getFields();

            final SolrSynchState state = index.getSolrSynchState();
            final StringBuilder sb = new StringBuilder();
            if (state != null) {
                if (state.getLastSynchronized() != null) {
                    sb.append("<b>Last synchronised:</b> ");
                    sb.append(ClientDateUtil.toISOString(index.getSolrSynchState().getLastSynchronized()));
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
    public void write(final SolrIndexDoc entity) {
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

    public interface SolrIndexFieldListView extends View {
        void setDataGridView(final View view);

        void setSynchState(final String syncState);
    }
}
