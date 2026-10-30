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

package stroom.pipeline.structure.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.data.client.component.RefCellsController;
import stroom.data.shared.StreamTypeNames;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerResource;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelinePropertyType;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.pipeline.structure.client.PipelineElementTypesFactory;
import stroom.pipeline.structure.client.PipelineModel;
import stroom.pipeline.structure.client.component.PipelineReferenceList.ReferenceState;
import stroom.planb.shared.PlanBDoc;
import stroom.sqlstore.shared.SqlTemporalStoreDoc;
import stroom.util.client.cell.DocRefCell;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/// A pipeline element's reference pipelines ([PipelineReferenceList]) in a pipeline's model: those
/// inherited, added and removed, each document's name fetched afresh. New and Edit open a reference's
/// editor, Remove removes one (or restores a removed one), unless the pipeline is read only
/// ([Contexts#READ_ONLY]); the changes are written to the model.
public class PipelineReferenceListController extends Controller {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private final Map<PipelineReference, ReferenceState> referenceStateMap = new HashMap<>();
    private final List<PipelineReference> references = new ArrayList<>();
    private final Provider<NewPipelineReferenceController> newPipelineReferenceProvider;
    private final RestFactory restFactory;
    private final PipelineElementTypesFactory pipelineElementTypesFactory;
    private final RefCellsController refCells;

    private PipelineReferenceList view;
    private PipelineDoc pipeline;
    private PipelineModel pipelineModel;
    private PipelineElement currentElement;
    private PipelinePropertyType propertyType;

    /// Made by GIN; [#attach(PipelineReferenceList)] gives it its list.
    ///
    /// @param eventBus                     The application's event bus.
    /// @param newPipelineReferenceProvider Makes a reference's editor.
    /// @param restFactory                  Fetches the documents' names.
    /// @param pipelineElementTypesFactory  Gives the elements' property types.
    /// @param refCells                     Lets the cells open their documents.
    @Inject
    public PipelineReferenceListController(
            final EventBus eventBus,
            final Provider<NewPipelineReferenceController> newPipelineReferenceProvider,
            final RestFactory restFactory,
            final PipelineElementTypesFactory pipelineElementTypesFactory,
            final RefCellsController refCells) {
        super(eventBus);
        this.refCells = own(refCells);
        this.newPipelineReferenceProvider = newPipelineReferenceProvider;
        this.restFactory = restFactory;
        this.pipelineElementTypesFactory = pipelineElementTypesFactory;
    }

    /// Attaches this to a list, e.g. one a template declares. The list is then this
    /// controller's widget.
    ///
    /// @param view The list.
    /// @return This, for its owner to keep.
    public PipelineReferenceListController attach(final PipelineReferenceList view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);

        view.addColumns(
                referenceStateMap::get,
                pipelineReference -> pipelineModel.getBaseData().getPipelineReferenceSource(pipelineReference),
                source -> pipeline.getUuid().equals(source.getUuid()),
                () -> refCells.configure(new DocRefCell.Builder<>()));
        view.setOnAdd(this::onAdd);
        view.setOnEdit(this::onEdit);
        view.setOnRemove(this::onRemove);
        watch(Contexts.READ_ONLY, view::setReadOnly);
        return this;
    }

    /// @param tableName The list's name, for assistive technology.
    public void setTableName(final String tableName) {
        view.setTableName(tableName);
    }

    /// @param pipeline The pipeline shown.
    public void setPipeline(final PipelineDoc pipeline) {
        this.pipeline = pipeline;
    }

    /// @param pipelineModel The pipeline's model, whose references are shown and changed.
    public void setPipelineModel(final PipelineModel pipelineModel) {
        this.pipelineModel = pipelineModel;
    }

    /// Shows an element's references, once the elements' property types are known.
    ///
    /// @param currentElement The element, or `null` for none.
    public void setCurrentElement(final PipelineElement currentElement) {
        pipelineElementTypesFactory.get(view.getTaskMonitor(), elementTypes -> {
            this.currentElement = currentElement;

            // Discover the reference property type.
            this.propertyType = null;
            if (currentElement != null) {
                final Map<String, PipelinePropertyType> propertyTypes = elementTypes.getPropertyTypes(currentElement);
                if (propertyTypes != null) {
                    for (final PipelinePropertyType propertyType : propertyTypes.values()) {
                        if (propertyType.isPipelineReference()) {
                            this.propertyType = propertyType;
                        }
                    }
                }
            }
            view.setCanAdd(propertyType != null);

            refresh();
        });
    }

    private void onAdd() {
        if (currentElement != null) {
            final PipelineReference pipelineReference = new PipelineReference(currentElement.getId(),
                    propertyType.getName(), null, null, StreamTypeNames.REFERENCE);
            showEditor(pipelineReference, true);
        }
    }

    private void onEdit(final PipelineReference pipelineReference) {
        if (pipelineReference != null) {
            // Only allow edit of added references.
            final ReferenceState state = referenceStateMap.get(pipelineReference);
            if (ReferenceState.ADDED.equals(state)) {
                showEditor(pipelineReference, false);
            }
        }
    }

    private void showEditor(final PipelineReference pipelineReference,
                            final boolean isNew) {
        if (pipelineReference != null) {
            final PipelineData pipelineData = pipelineModel.getPipelineData();
            final PipelineDataBuilder builder = new PipelineDataBuilder(pipelineData);
            builder.getReferences().getAddList().remove(pipelineReference);

            newPipelineReferenceProvider.get().edit(this,
                    isNew
                            ? "New Pipeline Reference"
                            : "Edit Pipeline Reference",
                    pipelineReference,
                    (updated, e) -> {
                        // A Plan B or SQL temporal store needs no feed or stream type
                        final String type = NullSafe.get(updated.getPipeline(), DocRef::getType);
                        final boolean isStore = PlanBDoc.TYPE.equals(type) || SqlTemporalStoreDoc.TYPE.equals(type);
                        if (updated.getPipeline() == null) {
                            AlertEvent.fireError(this, "You must specify a pipeline to use.", e::reset);
                        } else if (!isStore && updated.getFeed() == null) {
                            AlertEvent.fireError(this, "You must specify a feed to use.", e::reset);
                        } else if (!isStore && updated.getStreamType() == null) {
                            AlertEvent.fireError(this, "You must specify a stream type to use.", e::reset);
                        } else {
                            if (!builder.getReferences().getAddList().contains(updated)) {
                                builder.getReferences().getAddList().add(updated);
                            }
                            setPipelineData(builder.build());
                            refresh();
                            e.hide();
                        }
                    },
                    () -> {
                        // An existing reference cancelled is put back in the list
                        if (!isNew && !builder.getReferences().getAddList().contains(pipelineReference)) {
                            builder.getReferences().getAddList().add(pipelineReference);
                        }
                        setPipelineData(builder.build());
                    });
        }
    }

    private void onRemove(final PipelineReference selected) {
        if (selected != null) {
            final PipelineData pipelineData = pipelineModel.getPipelineData();
            final PipelineDataBuilder builder = new PipelineDataBuilder(pipelineData);
            if (builder.getReferences().getAddList().contains(selected)) {
                builder.getReferences().getAddList().remove(selected);
                builder.getReferences().getRemoveList().remove(selected);

            } else {
                if (builder.getReferences().getRemoveList().contains(selected)) {
                    builder.getReferences().getRemoveList().remove(selected);
                } else {
                    builder.getReferences().getRemoveList().add(selected);
                }
            }

            setPipelineData(builder.build());
            refresh();
        }
    }

    private void setPipelineData(final PipelineData pipelineData) {
        // Update the model rather than just setting the layer on it. The resulting change event is
        // what makes the pipeline re-evaluate whether it is dirty and so enables the Save button.
        pipelineModel.update(pipelineData);
    }

    private void addReference(final PipelineReference reference, final ReferenceState state) {
        // It is important that the pipe references are displayed in the order in which
        // they appear in the pipe doc (with inherited ones first)
        // Order is important as the ref lookup will try each loader in this o
        if (reference != null) {
            referenceStateMap.put(reference, state);
            // The most recent one trumps existing ones
            references.remove(reference);
            references.add(reference);
        }
    }

    private void refresh() {
        referenceStateMap.clear();
        references.clear();
        view.getSelectionModel().clear();

        if (currentElement != null) {
            final String id = currentElement.getId();
            if (id != null) {
                final Map<String, List<PipelineReference>> baseReferences = pipelineModel.getBaseData()
                        .getPipelineReferences().get(id);
                if (baseReferences != null) {
                    for (final List<PipelineReference> list : baseReferences.values()) {
                        for (final PipelineReference reference : list) {
                            addReference(reference, ReferenceState.INHERITED);
                        }
                    }
                }
                for (final PipelineReference reference : pipelineModel.getPipelineData()
                        .getAddedPipelineReferences()) {
                    if (id.equals(reference.getElement())) {
                        addReference(reference, ReferenceState.ADDED);
                    }
                }
                for (final PipelineReference reference : pipelineModel.getPipelineData()
                        .getRemovedPipelineReferences()) {
                    if (id.equals(reference.getElement())) {
                        addReference(reference, ReferenceState.REMOVED);
                    }
                }
            }
        }

        // See if we need to load accurate doc refs (we do this to get correct entity names for display)
        final Set<DocRef> docRefs = new HashSet<>();
        references.forEach(ref -> addPipelineReference(docRefs, ref));
        if (!docRefs.isEmpty()) {
            // Load entities.
            restFactory
                    .create(EXPLORER_RESOURCE)
                    .method(res -> res.fetchDocRefs(docRefs))
                    .onSuccess(result -> {
                        final Map<DocRef, DocRef> fetchedDocRefs = result
                                .stream()
                                .collect(Collectors.toMap(Function.identity(), Function.identity()));

                        final List<PipelineReference> newList = new ArrayList<>(references.size());
                        for (final PipelineReference reference : references) {
                            newList.add(new PipelineReference.Builder(reference)
                                    .feed(resolve(fetchedDocRefs, reference.getFeed()))
                                    .pipeline(resolve(fetchedDocRefs, reference.getPipeline()))
                                    .build());
                        }
                        references.clear();
                        references.addAll(newList);
                        view.setData(references);
                    })
                    .owner(this)
                    .taskMonitorFactory(view.getTaskMonitor())
                    .exec();
        } else {
            view.setData(references);
        }
    }

    private DocRef resolve(final Map<DocRef, DocRef> map, final DocRef docRef) {
        if (docRef == null) {
            return null;
        }

        final DocRef fetchedDocRef = map.get(docRef);
        if (fetchedDocRef != null) {
            return fetchedDocRef;
        }

        return docRef;
    }

    private void addPipelineReference(final Set<DocRef> docRefs, final PipelineReference reference) {
        if (reference.getFeed() != null) {
            docRefs.add(reference.getFeed());
        }
        if (reference.getPipeline() != null) {
            docRefs.add(reference.getPipeline());
        }
    }
}
