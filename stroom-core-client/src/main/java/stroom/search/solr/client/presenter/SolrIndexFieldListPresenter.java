/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.search.solr.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.preferences.client.DateTimeFormatter;
import stroom.search.solr.client.presenter.SolrIndexFieldListPresenter.SolrIndexFieldListView;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.search.solr.shared.SolrIndexField;
import stroom.search.solr.shared.SolrIndexResource;
import stroom.search.solr.shared.SolrSynchState;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SolrIndexFieldListPresenter extends DocumentEditPresenter<SolrIndexFieldListView, SolrIndexDoc> {

    private static final SolrIndexResource SOLR_INDEX_RESOURCE = GWT.create(SolrIndexResource.class);

    private final PagerView pagerView;
    private final MyDataGrid<SolrIndexField> dataGrid;
    private final MultiSelectionModelImpl<SolrIndexField> selectionModel;
    private final SolrIndexFieldEditPresenter indexFieldEditPresenter;
    private final RestFactory restFactory;
    private final DateTimeFormatter dateTimeFormatter;
    private final ButtonView newButton;
    private final ButtonView editButton;
    private final ButtonView removeButton;
    private SolrIndexDoc index;
    private List<SolrIndexField> fields;
    private SolrIndexFieldDataProvider<SolrIndexField> dataProvider;

    @Inject
    public SolrIndexFieldListPresenter(final EventBus eventBus,
                                       final SolrIndexFieldListView view,
                                       final PagerView pagerView,
                                       final SolrIndexFieldEditPresenter indexFieldEditPresenter,
                                       final RestFactory restFactory,
                                       final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view);
        this.pagerView = pagerView;
        this.indexFieldEditPresenter = indexFieldEditPresenter;
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        pagerView.setDataWidget(dataGrid);

        view.setDataGridView(pagerView);
        newButton = pagerView.addButton(SvgPresets.NEW_ITEM);
        editButton = pagerView.addButton(SvgPresets.EDIT);
        removeButton = pagerView.addButton(SvgPresets.DELETE);

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
            final SolrIndexField selectedElement = selectionModel.getSelected();
            final boolean enabled = selectedElement != null;
            editButton.setEnabled(enabled);
            removeButton.setEnabled(enabled);
        } else {
            editButton.setEnabled(false);
            removeButton.setEnabled(false);
        }

        if (isReadOnly()) {
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
        addStringColumn("Name", 150, SolrIndexField::getFldName);
        addStringColumn("Type", row -> row.getFldType().getDisplayValue());
        addStringColumn("Field Type", SolrIndexField::getNativeType);
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
        dataGrid.addEndColumn(new EndColumn<>());
    }

    private void addStringColumn(final String name, final Function<SolrIndexField, String> function) {
        addStringColumn(name, 100, function);
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
        final Set<String> otherNames = fields.stream().map(SolrIndexField::getFldName).collect(Collectors.toSet());

        fetchFieldTypes(fieldTypes -> {
            indexFieldEditPresenter.read(SolrIndexField.builder().build(), otherNames, fieldTypes);
            indexFieldEditPresenter.show("New Field", e -> {
                if (e.isOk()) {
                    final SolrIndexField indexField = indexFieldEditPresenter.write();
                    if (indexField != null) {
                        fields.add(indexField);
                        fields.sort(Comparator.comparing(SolrIndexField::getFldName,
                                String.CASE_INSENSITIVE_ORDER));
                        selectionModel.setSelected(indexField);
                        refresh();

                        e.hide();
                        DirtyEvent.fire(SolrIndexFieldListPresenter.this, true);
                    } else {
                        e.reset();
                    }
                } else {
                    e.hide();
                }
            });
        });
    }

    private void onEdit() {
        final SolrIndexField existingField = selectionModel.getSelected();
        if (existingField != null) {
            final Set<String> otherNames = fields.stream()
                    .map(SolrIndexField::getFldName)
                    .collect(Collectors.toSet());
            otherNames.remove(existingField.getFldName());

            fetchFieldTypes(fieldTypes -> {
                indexFieldEditPresenter.read(existingField, otherNames, fieldTypes);
                indexFieldEditPresenter.show("Edit Field", e -> {
                    if (e.isOk()) {
                        final SolrIndexField indexField = indexFieldEditPresenter.write();
                        if (indexField != null) {
                            if (!indexField.equals(existingField)) {
                                fields.remove(existingField);
                                fields.add(indexField);
                                fields.sort(Comparator.comparing(SolrIndexField::getFldName,
                                        String.CASE_INSENSITIVE_ORDER));
                                selectionModel.setSelected(indexField);
                                refresh();

                                e.hide();
                                DirtyEvent.fire(SolrIndexFieldListPresenter.this, true);
                            } else {
                                e.hide();
                            }
                        } else {
                            e.reset();
                        }
                    } else {
                        e.hide();
                    }
                });
            });
        }
    }

    private void fetchFieldTypes(final Consumer<List<String>> consumer) {
        restFactory
                .create(SOLR_INDEX_RESOURCE)
                .method(res -> res.fetchSolrTypes(index))
                .onSuccess(consumer)
                .onFailure(throwable -> AlertEvent.fireError(SolrIndexFieldListPresenter.this,
                        "Unable to connect to Solr please check connection",
                        throwable.getMessage(),
                        null))
                .taskMonitorFactory(pagerView)
                .exec();
    }

    private void onRemove() {
        final List<SolrIndexField> list = selectionModel.getSelectedItems();
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

                    selectionModel.clear();
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
            dataProvider.addDataDisplay(dataGrid);
        }
        dataProvider.setList(fields);
        dataProvider.refresh();
    }

    @Override
    protected void onRead(final DocRef docRef, final SolrIndexDoc document, final boolean readOnly) {
        this.index = document;
        if (document != null) {
            fields = document.getFields().stream()
                    .sorted(Comparator.comparing(SolrIndexField::getFldName, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());

            final SolrSynchState state = document.getSolrSynchState();
            final StringBuilder sb = new StringBuilder();
            if (state != null) {
                if (state.getLastSynchronized() != null) {
                    sb.append("<b>Last synchronised:</b> ");
                    sb.append(dateTimeFormatter.format(document.getSolrSynchState().getLastSynchronized()));
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
        enableButtons();
    }

    @Override
    protected SolrIndexDoc onWrite(final SolrIndexDoc document) {
        document.setFields(fields);
        return document;
    }

    public interface SolrIndexFieldListView extends View {

        void setDataGridView(final View view);

        void setSynchState(final String syncState);
    }
}
