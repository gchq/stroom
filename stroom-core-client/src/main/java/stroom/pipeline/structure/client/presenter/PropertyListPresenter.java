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

package stroom.pipeline.structure.client.presenter;

import stroom.data.client.presenter.DocRefCell;
import stroom.data.client.presenter.DocRefCell.Builder;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docref.DocRef.DisplayType;
import stroom.docref.HasDisplayValue;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.explorer.shared.ExplorerResource;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineLayer;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyType;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PropertyListPresenter
        extends MyPresenterWidget<PagerView>
        implements HasDirtyHandlers {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private static final String ADDED = "pipelineStructureViewImpl-property-added";
    private static final String REMOVED = "pipelineStructureViewImpl-property-removed";
    private static final String INHERITED = "pipelineStructureViewImpl-property-inherited";
    private static final String DEFAULT = "pipelineStructureViewImpl-property-default";

    private final MyDataGrid<PipelineProperty> dataGrid;
    private final MultiSelectionModelImpl<PipelineProperty> selectionModel;
    private final ButtonView editButton;
    private final Provider<NewPropertyPresenter> newPropertyPresenter;
    private final RestFactory restFactory;

    private PipelineModel pipelineModel;
    private List<PipelineProperty> defaultProperties;
    private PipelineElement currentElement;

    private boolean readOnly = true;

    @Inject
    public PropertyListPresenter(final EventBus eventBus,
                                 final PagerView view,
                                 final Provider<NewPropertyPresenter> newPropertyPresenter,
                                 final RestFactory restFactory) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>(this);
        dataGrid.setMultiLine(true);
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        view.setDataWidget(dataGrid);

        this.newPropertyPresenter = newPropertyPresenter;
        this.restFactory = restFactory;

        editButton = view.addButton(SvgPresets.EDIT);

        addColumns();
        enableButtons();
    }

    @Override
    protected void onBind() {
        registerHandler(selectionModel.addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                onEdit(selectionModel.getSelected(), readOnly);
            }
        }));
        registerHandler(editButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                onEdit(selectionModel.getSelected(), readOnly);
            }
        }));
    }

    private void addColumns() {
        addNameColumn();
        addValueColumn();
        addDescriptionColumn();

        addEndColumn();
    }

    private void addNameColumn() {
        // Name.
        dataGrid.addResizableColumn(new Column<PipelineProperty, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final PipelineProperty property) {
                return getSafeHtmlWithState(property, property.getName(), false);
            }
        }, "Property Name", 300);
    }

    private void addValueColumn() {
        // Value.
        // Value could be a docRef, in which case it gets a hover link to open it) or a simple string
        final DocRefCell.Builder<PipelineProperty> cellBuilder = new Builder<PipelineProperty>()
                .eventBus(getEventBus())
                .showIcon(true)
                .cssClassFunction(property1 -> getStateCssClass(property1, true))
                .cellTextFunction(property -> {
                    if (property == null) {
                        return SafeHtmlUtils.EMPTY_SAFE_HTML;
                    } else {
                        final PipelinePropertyValue value = property.getValue();
                        if (value.getEntity() != null) {
                            return SafeHtmlUtils.fromString(value.getEntity()
                                    .getDisplayValue(NullSafe.requireNonNullElse(
                                            DisplayType.AUTO,
                                            DisplayType.AUTO)));
                        } else {
                            return SafeHtmlUtils.fromString(getVal(property));
                        }
                    }
                })
                .docRefFunction(pipelineProperty -> NullSafe.get(
                        pipelineProperty,
                        PipelineProperty::getValue,
                        PipelinePropertyValue::getEntity));

        final Column<PipelineProperty, PipelineProperty> valueCol = DataGridUtil.docRefColumnBuilder(
                        cellBuilder)
                .build();

        dataGrid.addAutoResizableColumn(valueCol, "Value", 30, 200);
    }

    private Source getSource(final PipelineProperty property) {
        Source source = null;
        final PipelineProperty added = getActualProperty(pipelineModel.getPipelineData().getAddedProperties(),
                property);
        if (added != null) {
            source = Source.LOCAL;
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

    private String getVal(final PipelineProperty property) {
        final PipelinePropertyValue value = property.getValue();
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private PipelineProperty getActualProperty(final List<PipelineProperty> properties,
                                               final PipelineProperty defaultProperty) {
        if (properties != null && !properties.isEmpty()) {
            for (final PipelineProperty property : properties) {
                if (property.equals(defaultProperty)) {
                    return property;
                }
            }
        }
        return null;
    }

    private void addDescriptionColumn() {
        // Default Value.
        dataGrid.addAutoResizableColumn(new Column<PipelineProperty, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final PipelineProperty property) {
                return getSafeHtml(NullSafe.get(
                        pipelineModel,
                        pm -> pm.getPropertyType(currentElement, property),
                        PipelinePropertyType::getDescription));
            }
        }, "Description", 70, 200);
    }

    private SafeHtml getSafeHtmlWithState(final PipelineProperty property,
                                          final String string,
                                          final boolean showRemovedAsDefault) {
        if (string == null) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        }

        final String className;
        if (pipelineModel.getPipelineData().getAddedProperties().contains(property)) {
            className = ADDED;
        } else if (pipelineModel.getPipelineData().getRemovedProperties().contains(property)) {
            if (showRemovedAsDefault) {
                className = DEFAULT;
            } else {
                className = REMOVED;
            }
        } else {
            final PipelineProperty inheritedProperty = getInheritedProperty(property);
            if (inheritedProperty != null) {
                className = INHERITED;
            } else {
                className = DEFAULT;
            }
        }

        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        builder.append(SafeHtmlUtils.fromTrustedString("<div class=\"" + className + "\">"));
        builder.appendEscaped(string);
        builder.append(SafeHtmlUtils.fromTrustedString("</div>"));

        return builder.toSafeHtml();
    }

    private String getStateCssClass(final PipelineProperty property,
                                    final boolean showRemovedAsDefault) {
        final String className;
        if (pipelineModel.getPipelineData().getAddedProperties().contains(property)) {
            className = ADDED;
        } else if (pipelineModel.getPipelineData().getRemovedProperties().contains(property)) {
            if (showRemovedAsDefault) {
                className = DEFAULT;
            } else {
                className = REMOVED;
            }
        } else {
            final PipelineProperty inheritedProperty = getInheritedProperty(property);
            if (inheritedProperty != null) {
                className = INHERITED;
            } else {
                className = DEFAULT;
            }
        }
        return className;
    }

    private SafeHtml getSafeHtml(final String string) {
        if (string == null) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        }

        return SafeHtmlUtils.fromString(string);
    }

    private void addEndColumn() {
        dataGrid.addEndColumn(new EndColumn<>());
    }

    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        enableButtons();
    }

    public void setPipelineModel(final PipelineModel pipelineModel) {
        this.pipelineModel = pipelineModel;
    }

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

        enableButtons();
        refresh();
    }

    private PipelineProperty createDefaultProperty(final String elementName, final PipelinePropertyType propertyType) {
        return new PipelineProperty(elementName, propertyType.getName(), getDefaultValue(propertyType));
    }

    private void onEdit(final PipelineProperty property, final boolean readOnly) {
        if (!readOnly && property != null) {
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

            final PipelineProperty editing = new PipelineProperty.Builder(localProperty).build();
            final Source source = getSource(editing);

            final NewPropertyPresenter editor = newPropertyPresenter.get();
            editor.edit(
                    currentElement,
                    pipelinePropertyType,
                    property,
                    inheritedProperty,
                    editing,
                    source,
                    defaultValue,
                    inheritedValue,
                    inheritedFrom);

            final HidePopupRequestEvent.Handler handler = e -> {
                if (e.isOk()) {
                    if (editor.isDirty()) {

                        final PipelineDataBuilder builder = new PipelineDataBuilder(pipelineModel.getPipelineData());

                        // Remove the property locally.
                        builder.getProperties().getAddList().remove(editing);
                        builder.getProperties().getRemoveList().remove(editing);

                        // Write new property.
                        final PipelinePropertyValue value = editor.writeValue();
                        final PipelineProperty newProperty = new PipelineProperty.Builder(editing)
                                .value(value)
                                .build();
                        switch (editor.getSource()) {
                            case LOCAL:
                                builder.getProperties().getAddList().add(newProperty);
                                break;

                            case DEFAULT:
                                builder.getProperties().getRemoveList().add(newProperty);
                                break;

                            case INHERIT:
                                // Do nothing as we have already removed it.
                        }

                        final PipelineData pipelineData = builder.build();
                        pipelineModel.setPipelineLayer(
                                new PipelineLayer(pipelineModel.getPipelineLayer().getSourcePipeline(), pipelineData));

                        setDirty(true);

                        refresh();
                    }
                }
                e.hide();
            };

            final PopupSize popupSize = PopupSize.builder()
                    .width(Size.builder()
                            .initial(600)
                            .min(600)
                            .resizable(true)
                            .build())
                    .build();

            ShowPopupEvent.builder(editor)
                    .popupType(PopupType.OK_CANCEL_DIALOG)
                    .popupSize(popupSize)
                    .caption("Edit Property")
                    .onShow(e -> editor.getView().focus())
                    .onHideRequest(handler)
                    .modal(true)
                    .fire();
        }
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
                            final PipelineProperty.Builder builder = new PipelineProperty.Builder(property);
                            final DocRef docRef = property.getValue().getEntity();
                            if (docRef != null) {
                                final DocRef fetchedDocRef = fetchedDocRefs.get(docRef);
                                if (fetchedDocRef != null) {
                                    builder.value(new PipelinePropertyValue(fetchedDocRef));
                                }
                            }
                            newList.add(builder.build());
                        }
                        setData(newList);
                    })
                    .taskMonitorFactory(getView())
                    .exec();
        } else {
            setData(propertyList);
        }
    }

    private void setData(final List<PipelineProperty> propertyList) {
        selectionModel.clear();
        dataGrid.setRowData(0, propertyList);
        dataGrid.setRowCount(propertyList.size());
    }

    private void enableButtons() {
        final PipelineProperty selected = selectionModel.getSelected();
        editButton.setEnabled(!readOnly && selected != null);

        if (readOnly) {
            editButton.setTitle("Edit disabled as this pipeline is read only");
        } else {
            editButton.setTitle("Edit Property");
        }
    }

    private void setDirty(final boolean dirty) {
        if (dirty) {
            DirtyEvent.fire(this, dirty);
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

    public DocRef getInheritedPropertySource(final PipelineProperty property) {
        if (property == null) {
            return null;
        }

        return pipelineModel.getBaseData().getPropertySource(property);
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }


    // --------------------------------------------------------------------------------


    public enum Source implements HasDisplayValue {
        LOCAL("Local"),
        INHERIT("Inherit"),
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
