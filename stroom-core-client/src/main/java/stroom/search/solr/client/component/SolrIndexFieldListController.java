/*
 * Copyright 2019 Crown Copyright
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

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.config.global.client.presenter.ListDataProvider;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.preferences.client.DateTimeFormatter;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.search.solr.shared.SolrIndexField;
import stroom.search.solr.shared.SolrIndexResource;
import stroom.search.solr.shared.SolrSynchState;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/// A Solr index's fields ([SolrIndexFieldList]), by name, and its schema's synchronisation state:
/// New and Edit open a field's editor with the types Solr offers, Remove removes those selected once
/// confirmed, unless the index is read only ([Contexts#READ_ONLY]).
public class SolrIndexFieldListController extends Controller {

    private static final SolrIndexResource SOLR_INDEX_RESOURCE = GWT.create(SolrIndexResource.class);

    private final SolrIndexFieldList view;
    private final Provider<SolrIndexFieldEditorController> indexFieldEditorProvider;
    private final RestFactory restFactory;
    private final DateTimeFormatter dateTimeFormatter;
    private SolrIndexDoc index;
    private List<SolrIndexField> fields;
    private ListDataProvider<SolrIndexField> dataProvider;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                 The application's event bus.
    /// @param view                     The content.
    /// @param indexFieldEditorProvider Makes a field's editor.
    /// @param restFactory              Fetches the types Solr offers.
    /// @param dateTimeFormatter        Formats when the schema was last synchronised.
    @Inject
    public SolrIndexFieldListController(final EventBus eventBus,
                                        final SolrIndexFieldList view,
                                        final Provider<SolrIndexFieldEditorController> indexFieldEditorProvider,
                                        final RestFactory restFactory,
                                        final DateTimeFormatter dateTimeFormatter) {
        super(eventBus);
        this.view = view;
        this.indexFieldEditorProvider = indexFieldEditorProvider;
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;
        initWidget(view);
        view.setOnNew(this::onAdd);
        view.setOnEdit(this::onEdit);
        view.setOnRemove(this::onRemove);
        watch(Contexts.READ_ONLY, view::setReadOnly);
    }

    private MultiSelectionModelImpl<SolrIndexField> getSelectionModel() {
        return view.getSelectionModel();
    }

    private void onAdd() {
        final Set<String> otherNames = fields.stream().map(SolrIndexField::getFldName).collect(Collectors.toSet());

        fetchFieldTypes(fieldTypes -> {
            indexFieldEditorProvider.get().edit(this, "New Field", SolrIndexField.builder().build(), otherNames,
                    fieldTypes, indexField -> {
                        fields.add(indexField);
                        fields.sort(Comparator.comparing(SolrIndexField::getFldName,
                                String.CASE_INSENSITIVE_ORDER));
                        getSelectionModel().setSelected(indexField);
                        refresh();
                        markChanged();
                    });
        });
    }

    private void onEdit() {
        final SolrIndexField existingField = getSelectionModel().getSelected();
        if (existingField != null) {
            final Set<String> otherNames = fields.stream()
                    .map(SolrIndexField::getFldName)
                    .collect(Collectors.toSet());
            otherNames.remove(existingField.getFldName());

            fetchFieldTypes(fieldTypes -> {
                indexFieldEditorProvider.get().edit(this, "Edit Field", existingField, otherNames, fieldTypes,
                        indexField -> {
                            if (!indexField.equals(existingField)) {
                                fields.remove(existingField);
                                fields.add(indexField);
                                fields.sort(Comparator.comparing(SolrIndexField::getFldName,
                                        String.CASE_INSENSITIVE_ORDER));
                                getSelectionModel().setSelected(indexField);
                                refresh();
                                markChanged();
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
                .onFailure(throwable -> AlertEvent.fireError(SolrIndexFieldListController.this,
                        "Unable to connect to Solr please check connection",
                        throwable.getMessage(),
                        null))
                .owner(this)
                .taskMonitorFactory(view.getTaskMonitor())
                .exec();
    }

    private void onRemove() {
        final List<SolrIndexField> list = getSelectionModel().getSelectedItems();
        //noinspection SizeReplaceableByIsEmpty
        if (list != null && list.size() > 0) {
            String message = "Are you sure you want to remove the selected field?";
            if (list.size() > 1) {
                message = "Are you sure you want to remove the selected fields?";
            }

            ConfirmEvent.fire(this, message, result -> {
                if (result) {
                    fields.removeAll(list);
                    getSelectionModel().clear();
                    refresh();
                    markChanged();
                }
            });
        }
    }

    private void refresh() {
        if (fields == null) {
            fields = new ArrayList<>();
        }
        if (dataProvider == null) {
            dataProvider = new ListDataProvider<>();
            view.setDataProvider(dataProvider);
        }
        dataProvider.setCompleteList(fields);
    }

    /// Shows an index's fields and its schema's synchronisation state.
    ///
    /// @param docRef   The index.
    /// @param document Its document.
    public void read(final DocRef docRef, final SolrIndexDoc document) {
        view.setIndexName(docRef.getName());
        this.index = document;
        if (document != null) {
            fields = document.getFields().stream()
                    .sorted(Comparator.comparing(SolrIndexField::getFldName, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());

            final SolrSynchState state = document.getSolrSynchState();
            if (state != null) {
                view.setSyncState(
                        state.getLastSynchronized() == null
                                ? null
                                : dateTimeFormatter.format(state.getLastSynchronized()),
                        NullSafe.list(state.getMessages()));
            } else {
                view.setSyncState(null, List.of());
            }
        }
        refresh();
    }

    /// @param document The index's document.
    /// @return A copy of it with the fields shown, and those to delete from Solr.
    public SolrIndexDoc write(final SolrIndexDoc document) {
        // Derive the fields that need deleting from Solr rather than accumulating them as the user
        // edits. A field needs deleting if it existed at load (or was already pending deletion) and is
        // no longer in the current list. Deriving at write time avoids the bugs inherent in
        // incremental tracking - e.g. removing then re-adding a field with the same name previously
        // left it flagged in deletedFields, so a Solr sync would delete the re-added field.
        final Set<String> currentNames = NullSafe.list(fields).stream()
                .map(SolrIndexField::getFldName)
                .collect(Collectors.toSet());
        final Map<String, SolrIndexField> toDelete = new LinkedHashMap<>();
        NullSafe.list(document.getFields()).forEach(field -> {
            if (!currentNames.contains(field.getFldName())) {
                toDelete.put(field.getFldName(), field);
            }
        });
        NullSafe.list(document.getDeletedFields()).forEach(field -> {
            if (!currentNames.contains(field.getFldName())) {
                toDelete.putIfAbsent(field.getFldName(), field);
            }
        });
        return document.copy()
                .fields(fields)
                .deletedFields(new ArrayList<>(toDelete.values()))
                .build();
    }
}
