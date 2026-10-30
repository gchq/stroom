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
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docref.HasDisplayValue;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginRegistry;
import stroom.explorer.shared.ExplorerResource;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyType;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.pipeline.structure.client.PipelineModel;
import stroom.pipeline.structure.client.component.PropertyList.PropertyState;
import stroom.util.client.cell.DocRefCell;
import stroom.util.shared.Document;
import stroom.util.shared.Embeddable;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/// A pipeline element's properties ([PropertyList]) in a pipeline's model: its own, inherited or
/// default values, each document's name fetched afresh. Edit opens the property's editor, unless the
/// pipeline is read only ([Contexts#READ_ONLY]), and writes the change to the model, making an
/// embedded document if asked.
public class PropertyListController extends Controller {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private final Provider<NewPropertyController> newPropertyProvider;
    private final RestFactory restFactory;
    private final DocumentPluginRegistry documentPluginRegistry;
    private final RefCellsController refCells;

    private PropertyList view;
    private PipelineModel pipelineModel;
    private List<PipelineProperty> defaultProperties;
    private PipelineElement currentElement;

    /// Made by GIN; [#attach(PropertyList)] gives it its list.
    ///
    /// @param eventBus               The application's event bus.
    /// @param newPropertyProvider    Makes a property's editor.
    /// @param restFactory            Fetches the documents' names.
    /// @param documentPluginRegistry Makes and saves embedded documents.
    /// @param refCells               Lets the value cells open their documents.
    @Inject
    public PropertyListController(final EventBus eventBus,
                                  final Provider<NewPropertyController> newPropertyProvider,
                                  final RestFactory restFactory,
                                  final DocumentPluginRegistry documentPluginRegistry,
                                  final RefCellsController refCells) {
        super(eventBus);
        this.refCells = own(refCells);
        this.newPropertyProvider = newPropertyProvider;
        this.restFactory = restFactory;
        this.documentPluginRegistry = documentPluginRegistry;
    }

    /// Attaches this to a list, e.g. one a template declares. The list is then this
    /// controller's widget.
    ///
    /// @param view The list.
    /// @return This, for its owner to keep.
    public PropertyListController attach(final PropertyList view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);

        view.addColumns(
                this::getState,
                property -> NullSafe.get(
                        pipelineModel,
                        pm -> pm.getPropertyType(currentElement, property),
                        PipelinePropertyType::getDescription),
                refCells.configure(new DocRefCell.Builder<>()));
        view.setOnEdit(this::onEdit);
        watch(Contexts.READ_ONLY, view::setReadOnly);
        return this;
    }

    private Source getSource(final PipelineProperty property) {
        Source source = null;
        final PipelineProperty added = getActualProperty(pipelineModel.getPipelineData().getAddedProperties(),
                property);
        if (added != null && added.getValue() != null) {
            if (added.getValue().isEmbedded()) {
                source = Source.EMBEDDED;
            } else {
                source = Source.LOCAL;
            }
        }

        if (source == null) {
            final PipelineProperty removed = getActualProperty(pipelineModel.getPipelineData().getRemovedProperties(),
                    property);
            if (removed != null) {
                source = Source.DEFAULT;
            }
        }

        if (source == null) {
            source = Source.INHERIT;
        }
        return source;
    }

    private PipelineProperty getActualProperty(final List<PipelineProperty> properties,
                                               final PipelineProperty defaultProperty) {
        if (properties != null && !properties.isEmpty()) {
            for (final PipelineProperty property : properties) {
                // Compare by element+name only
                if (property.getElement().equals(defaultProperty.getElement())
                    && property.getName().equals(defaultProperty.getName())) {
                    return property;
                }
            }
        }
        return null;
    }

    // Where a property's value comes from
    private PropertyState getState(final PipelineProperty property) {
        if (getActualProperty(pipelineModel.getPipelineData().getAddedProperties(), property) != null) {
            return PropertyState.ADDED;
        } else if (getActualProperty(pipelineModel.getPipelineData().getRemovedProperties(), property) != null) {
            return PropertyState.REMOVED;
        } else if (getInheritedProperty(property) != null) {
            return PropertyState.INHERITED;
        } else {
            return PropertyState.DEFAULT;
        }
    }

    /// @param tableName The list's name, for assistive technology.
    public void setTableName(final String tableName) {
        view.setTableName(tableName);
    }

    /// @param pipelineModel The pipeline's model, whose properties are shown and changed.
    public void setPipelineModel(final PipelineModel pipelineModel) {
        this.pipelineModel = pipelineModel;
    }

    /// Shows an element's properties, with none selected.
    ///
    /// @param currentElement The element, or `null` for none.
    public void setCurrentElement(final PipelineElement currentElement) {
        this.currentElement = currentElement;
        final List<PipelineProperty> defaultProperties = new ArrayList<>();
        if (currentElement != null) {
            final Map<String, PipelinePropertyType> propertyTypes = pipelineModel.getPropertyTypes(currentElement);
            if (propertyTypes != null) {
                for (final PipelinePropertyType propertyType : propertyTypes.values()) {
                    if (!propertyType.isPipelineReference()) {
                        final PipelineProperty property = createDefaultProperty(currentElement.getId(),
                                propertyType);
                        defaultProperties.add(property);
                    }
                }
            }
        }
        Collections.sort(defaultProperties);
        this.defaultProperties = defaultProperties;

        refresh();
    }

    private PipelineProperty createDefaultProperty(final String elementName, final PipelinePropertyType propertyType) {
        return new PipelineProperty(elementName, propertyType.getName(), getDefaultValue(propertyType));
    }

    private void onEdit(final PipelineProperty property) {
        if (!view.isReadOnly() && property != null) {
            // Get the current value for this property.
            PipelineProperty localProperty = getActualProperty(pipelineModel.getPipelineData().getAddedProperties(),
                    property);
            PipelineProperty inheritedProperty = getInheritedProperty(property);
            final PipelinePropertyType pipelinePropertyType = pipelineModel.getPropertyType(currentElement, property);

            final String defaultValue = pipelinePropertyType.getDefaultValue();
            final String inheritedValue = NullSafe.get(
                    inheritedProperty,
                    PipelineProperty::getValue,
                    PipelinePropertyValue::toString);
            final String inheritedFrom = NullSafe.get(
                    inheritedProperty,
                    this::getInheritedPropertySource,
                    DocRef::getName);

            if (inheritedProperty == null) {
                inheritedProperty = property;
            }
            if (localProperty == null) {
                localProperty = inheritedProperty;
            }

            final PipelineProperty editing = PipelineProperty.builder(localProperty).build();
            final Source source = getSource(editing);

            // The dialog isn't modal, so another element can be chosen, or this one removed, while
            // it is open
            final PipelineElement element = currentElement;
            final NewPropertyController editor = newPropertyProvider.get();
            final Consumer<HidePopupRequestEvent> onOk = e -> {
                if (editor.isDirty() && !pipelineModel.hasElement(element)) {
                    AlertEvent.fireWarn(this, "'" + element.getId() + "' was removed from the pipeline "
                                              + "while its property was being edited, so the change "
                                              + "can't be kept.", null);
                } else if (editor.isDirty()) {

                    final PipelineDataBuilder builder = new PipelineDataBuilder(pipelineModel.getPipelineData());

                    // Remove the property locally (compare by element+name only).
                    builder.getProperties().getAddList().removeIf(p ->
                            p.getElement().equals(editing.getElement())
                            && p.getName().equals(editing.getName()));
                    builder.getProperties().getRemoveList().removeIf(p ->
                            p.getElement().equals(editing.getElement())
                            && p.getName().equals(editing.getName()));

                    // Write new property.
                    final PipelinePropertyValue value = editor.writeValue();
                    final PipelineProperty newProperty = PipelineProperty.builder(editing)
                            .value(value)
                            .build();
                    switch (editor.getSource()) {
                        case LOCAL:
                            builder.getProperties().getAddList().add(newProperty);
                            break;

                        case DEFAULT:
                            builder.getProperties().getRemoveList().add(newProperty);
                            break;

                        case EMBEDDED:
                            final String docType = pipelinePropertyType.getDocRefTypes()[0];
                            final DocRef parentDocRef = pipelineModel.getPipelineLayer().getSourcePipeline();
                            final String embeddedPropertyName = "EMBEDDED " + docType + " (" +
                                                                element.getId() + ")";

                            final DocumentPlugin<Document> documentPlugin = documentPluginRegistry.get(docType,
                                    Document.class);

                            createEmbeddedDocument(documentPlugin, embeddedPropertyName, parentDocRef, e,
                                    document -> {
                                        final PipelineProperty embeddedProperty = PipelineProperty.builder(editing)
                                                .value(new PipelinePropertyValue(document.asDocRef(), true))
                                                .build();
                                        builder.getProperties().getAddList().add(embeddedProperty);

                                        pipelineModel.update(builder.build());

                                        refresh();
                                        e.hide();
                                    });
                            return;

                        case INHERIT:
                            // Do nothing as we have already removed it.
                    }

                    pipelineModel.update(builder.build());

                    refresh();
                }
                e.hide();
            };
            editor.edit(this,
                    currentElement,
                    pipelinePropertyType,
                    property,
                    inheritedProperty,
                    editing,
                    source,
                    defaultValue,
                    inheritedValue,
                    inheritedFrom,
                    onOk);
        }
    }

    private void createEmbeddedDocument(final DocumentPlugin<Document> documentPlugin,
                                        final String documentName,
                                        final DocRef parentDocRef,
                                        final HidePopupRequestEvent event,
                                        final Consumer<Document> callback) {
        // If it can't be created or saved, say why and let the user try again or cancel
        final RestErrorHandler errorHandler = throwable -> AlertEvent.fireError(
                this,
                "Unable to create embedded document",
                throwable.getMessage(), event::reset);

        documentPlugin.create(documentName, document -> {
            if (document instanceof final Embeddable embeddable) {
                embeddable.setEmbeddedIn(parentDocRef);
                documentPlugin.save(document.asDocRef(), document, callback, errorHandler,
                        view.getTaskMonitor());
            }
        }, errorHandler, view.getTaskMonitor());
    }

    private void refresh() {
        final Set<DocRef> docRefs = new HashSet<>();

        final List<PipelineProperty> propertyList = new ArrayList<>(defaultProperties.size());
        for (final PipelineProperty defaultProperty : defaultProperties) {
            PipelineProperty property = defaultProperty;

            final PipelineProperty added = getActualProperty(pipelineModel.getPipelineData().getAddedProperties(),
                    property);
            if (added != null) {
                // Add our version of the property.
                property = added;
            } else {
                final PipelineProperty removed = getActualProperty(
                        pipelineModel.getPipelineData().getRemovedProperties(), property);
                if (removed == null) {
                    // Get the inherited property as we haven't set the property or removed (shadowed) the
                    // parent property.
                    final PipelineProperty inherited = getInheritedProperty(property);
                    if (inherited != null) {
                        property = inherited;
                    }
                }
            }
            // Add the property.
            propertyList.add(property);

            // If the property is a doc ref then we will have to look it up on the server to get the current
            // name for the entity.
            if (property.getValue() != null && property.getValue().getEntity() != null) {
                docRefs.add(property.getValue().getEntity());
            }
        }

        if (!docRefs.isEmpty()) {
            // Load entities.
            restFactory
                    .create(EXPLORER_RESOURCE)
                    .method(res -> res.fetchDocRefs(docRefs))
                    .onSuccess(result -> {
                        final Map<DocRef, DocRef> fetchedDocRefs = result
                                .stream()
                                .collect(Collectors.toMap(Function.identity(), Function.identity()));

                        final List<PipelineProperty> newList = new ArrayList<>(propertyList.size());
                        for (final PipelineProperty property : propertyList) {
                            final PipelineProperty.Builder builder = PipelineProperty.builder(property);
                            final DocRef docRef = property.getValue().getEntity();
                            if (docRef != null) {
                                final DocRef fetchedDocRef = fetchedDocRefs.get(docRef);
                                if (fetchedDocRef != null) {
                                    builder.value(new PipelinePropertyValue(fetchedDocRef));
                                }
                            }
                            newList.add(builder.build());
                        }
                        view.setData(newList);
                    })
                    .owner(this)
                    .taskMonitorFactory(view.getTaskMonitor())
                    .exec();
        } else {
            view.setData(propertyList);
        }
    }

    private PipelinePropertyValue getDefaultValue(final PipelinePropertyType propertyType) {
        if ("boolean".equals(propertyType.getType())) {
            boolean defaultValue = true;
            if (propertyType.getDefaultValue() != null && !propertyType.getDefaultValue().isEmpty()) {
                defaultValue = Boolean.parseBoolean(propertyType.getDefaultValue());
            }
            return new PipelinePropertyValue(defaultValue);
        } else if ("int".equals(propertyType.getType())) {
            int defaultValue = 0;
            if (propertyType.getDefaultValue() != null && !propertyType.getDefaultValue().isEmpty()) {
                defaultValue = Integer.parseInt(propertyType.getDefaultValue());
            }
            return new PipelinePropertyValue(defaultValue);
        } else if ("long".equals(propertyType.getType())) {
            long defaultValue = 0L;
            if (propertyType.getDefaultValue() != null && !propertyType.getDefaultValue().isEmpty()) {
                defaultValue = Long.parseLong(propertyType.getDefaultValue());
            }
            return new PipelinePropertyValue(defaultValue);
        } else if ("String".equals(propertyType.getType())) {
            return new PipelinePropertyValue(propertyType.getDefaultValue());
        }

        return new PipelinePropertyValue();
    }

    /// @param property A property.
    /// @return The value a parent pipeline gives it, or `null` if none does.
    public PipelineProperty getInheritedProperty(final PipelineProperty property) {
        if (property == null) {
            return null;
        }

        final Map<String, PipelineProperty> map = pipelineModel.getBaseData().getProperties()
                .get(property.getElement());
        if (map != null) {
            return map.get(property.getName());
        }

        return null;
    }

    /// @param property A property.
    /// @return The parent pipeline its inherited value comes from, or `null`.
    public DocRef getInheritedPropertySource(final PipelineProperty property) {
        if (property == null) {
            return null;
        }

        return pipelineModel.getBaseData().getPropertySource(property);
    }


    // --------------------------------------------------------------------------------


    /// Where an edited property's value is to come from.
    public enum Source implements HasDisplayValue {
        LOCAL("Local"),
        INHERIT("Inherit"),
        EMBEDDED("Embedded"),
        DEFAULT("Default");

        private final String displayValue;

        Source(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }
}
