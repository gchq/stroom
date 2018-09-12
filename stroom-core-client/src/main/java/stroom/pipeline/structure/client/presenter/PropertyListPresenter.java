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

package stroom.pipeline.structure.client.presenter;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.pipeline.shared.FetchDocRefsAction;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyType;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.pipeline.shared.data.SourcePipeline;
import stroom.query.api.v2.DocRef;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.HasDisplayValue;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PropertyListPresenter extends MyPresenterWidget<DataGridView<PipelineProperty>>
        implements HasDirtyHandlers {
    private static final SafeHtml ADDED = SafeHtmlUtils.fromSafeConstant("<div style=\"font-weight:500\">");
    private static final SafeHtml REMOVED = SafeHtmlUtils
            .fromSafeConstant("<div style=\"font-weight:500;text-decoration:line-through\">");
    private static final SafeHtml INHERITED = SafeHtmlUtils.fromSafeConstant("<div style=\"color:black\">");
    private static final SafeHtml DEFAULT = SafeHtmlUtils.fromSafeConstant("<div style=\"color:grey\">");
    private static final SafeHtml END = SafeHtmlUtils.fromSafeConstant("</div>");
    private final ButtonView editButton;
    private final Provider<NewPropertyPresenter> newPropertyPresenter;
    private final ClientDispatchAsync dispatcher;

    private Map<PipelineElementType, Map<String, PipelinePropertyType>> allPropertyTypes;
    private PipelineEntity pipelineEntity;
    private PipelineModel pipelineModel;
    private List<PipelineProperty> defaultProperties;

    @Inject
    public PropertyListPresenter(final EventBus eventBus,
                                 final Provider<NewPropertyPresenter> newPropertyPresenter,
                                 final ClientDispatchAsync dispatcher) {
        super(eventBus, new DataGridViewImpl<>(true));
        this.newPropertyPresenter = newPropertyPresenter;
        this.dispatcher = dispatcher;

        editButton = getView().addButton(SvgPresets.EDIT);
        editButton.setTitle("Edit Property");

        addColumns();
    }

    @Override
    protected void onBind() {
        registerHandler(getView().getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                onEdit(getView().getSelectionModel().getSelected());
            }
        }));
        registerHandler(editButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                onEdit(getView().getSelectionModel().getSelected());
            }
        }));
    }

    private void addColumns() {
        addNameColumn();
        addValueColumn();
        addSourceColumn();
        addInheritedValueColumn();
        addInheritedFromColumn();
        addDefaultValueColumn();
        addDescriptionColumn();

        addEndColumn();
    }

    private void addNameColumn() {
        // Name.
        getView().addResizableColumn(new Column<PipelineProperty, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final PipelineProperty property) {
                return getSafeHtmlWithState(property, property.getName(), false);
            }
        }, "Property Name", 150);
    }

    private void addValueColumn() {
        // Value.
        getView().addResizableColumn(new Column<PipelineProperty, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final PipelineProperty property) {
                final String value = getVal(property);
                return getSafeHtmlWithState(property, value, true);
            }
        }, "Value", 200);
    }

    private void addSourceColumn() {
        getView().addResizableColumn(new Column<PipelineProperty, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final PipelineProperty property) {
                final Source source = getSource(property);
                return getSafeHtml(source.getDisplayValue());
            }
        }, "Source", 100);
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
        if (properties != null && properties.size() > 0) {
            for (final PipelineProperty property : properties) {
                if (property.equals(defaultProperty)) {
                    return property;
                }
            }
        }
        return null;
    }

    private void addInheritedValueColumn() {
        // Default Value.
        getView().addResizableColumn(new Column<PipelineProperty, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final PipelineProperty property) {
                final PipelineProperty inheritedProperty = getInheritedProperty(property);
                if (inheritedProperty != null) {
                    final PipelinePropertyValue value = inheritedProperty.getValue();
                    if (value != null) {
                        return getSafeHtml(value.toString());
                    }
                }
                return getSafeHtml(property.getPropertyType().getDefaultValue());
            }
        }, "Inherited Value", 100);
    }

    private void addInheritedFromColumn() {
        // Default Value.
        getView().addResizableColumn(new Column<PipelineProperty, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final PipelineProperty property) {
                final PipelineProperty inheritedProperty = getInheritedProperty(property);
                if (inheritedProperty != null) {
                    final PipelinePropertyValue value = inheritedProperty.getValue();
                    if (value != null) {
                        return getSafeHtml(inheritedProperty.getSource().getPipeline().getName());
                    }
                }
                return null;
            }
        }, "Inherited From", 100);
    }

    private void addDefaultValueColumn() {
        // Default Value.
        getView().addResizableColumn(new Column<PipelineProperty, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final PipelineProperty property) {
                return getSafeHtml(property.getPropertyType().getDefaultValue());
            }
        }, "Default Value", 100);
    }

    private void addDescriptionColumn() {
        // Default Value.
        getView().addResizableColumn(new Column<PipelineProperty, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final PipelineProperty property) {
                return getSafeHtml(property.getPropertyType().getDescription());
            }
        }, "Description", 600);
    }

    private SafeHtml getSafeHtmlWithState(final PipelineProperty property, final String string,
                                          final boolean showRemovedAsDefault) {
        if (string == null) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        }

        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        if (pipelineModel.getPipelineData().getAddedProperties().contains(property)) {
            builder.append(ADDED);
        } else if (pipelineModel.getPipelineData().getRemovedProperties().contains(property)) {
            if (showRemovedAsDefault) {
                builder.append(DEFAULT);
            } else {
                builder.append(REMOVED);
            }
        } else {
            final PipelineProperty inheritedProperty = getInheritedProperty(property);
            if (inheritedProperty != null) {
                builder.append(INHERITED);
            } else {
                builder.append(DEFAULT);
            }
        }

        builder.appendEscaped(string);
        builder.append(END);

        return builder.toSafeHtml();
    }

    private SafeHtml getSafeHtml(final String string) {
        if (string == null) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        }

        return SafeHtmlUtils.fromString(string);
    }

    private void addEndColumn() {
        getView().addEndColumn(new EndColumn<>());
    }

    public void setPipeline(final PipelineEntity pipelineEntity) {
        this.pipelineEntity = pipelineEntity;
    }

    public void setPipelineModel(final PipelineModel pipelineModel) {
        this.pipelineModel = pipelineModel;
    }

    public void setCurrentElement(final PipelineElement currentElement) {
        final List<PipelineProperty> defaultProperties = new ArrayList<>();
        if (currentElement != null && allPropertyTypes != null) {
            final Map<String, PipelinePropertyType> propertyTypes = allPropertyTypes
                    .get(currentElement.getElementType());
            if (propertyTypes != null) {
                for (final PipelinePropertyType propertyType : propertyTypes.values()) {
                    if (!propertyType.isPipelineReference()) {
                        final PipelineProperty property = createDefaultProperty(currentElement.getId(), propertyType);
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
        final PipelineProperty property = new PipelineProperty(elementName, propertyType.getName());
        property.setPropertyType(propertyType);
        property.setValue(getDefaultValue(propertyType));

        return property;
    }

    private void onEdit(final PipelineProperty property) {
        if (property != null) {
            // Get the current value for this property.
            PipelineProperty localProperty = getActualProperty(pipelineModel.getPipelineData().getAddedProperties(),
                    property);
            PipelineProperty inheritedProperty = getInheritedProperty(property);
            final PipelineProperty defaultProperty = property;

            if (inheritedProperty == null) {
                inheritedProperty = defaultProperty;
            }
            if (localProperty == null) {
                localProperty = inheritedProperty;
            }

            final PipelineProperty editing = new PipelineProperty();
            editing.copyFrom(localProperty);
            editing.setSource(new SourcePipeline(pipelineEntity));
            editing.setValue(localProperty.getValue());

            final Source source = getSource(editing);

            final NewPropertyPresenter editor = newPropertyPresenter.get();
            editor.edit(defaultProperty, inheritedProperty, editing, source);

            final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
                @Override
                public void onHideRequest(final boolean autoClose, final boolean ok) {
                    if (ok) {
                        if (editor.isDirty()) {
                            setDirty(true);

                            editor.write(editing);

                            // Remove the property locally.
                            pipelineModel.getPipelineData().getAddedProperties().remove(editing);
                            pipelineModel.getPipelineData().getRemovedProperties().remove(editing);

                            switch (editor.getSource()) {
                                case LOCAL:
                                    pipelineModel.getPipelineData().getAddedProperties().add(editing);
                                    break;

                                case DEFAULT:
                                    pipelineModel.getPipelineData().getRemovedProperties().add(editing);
                                    break;

                                case INHERIT:
                                    // Do nothing as we have already removed it.
                            }

                            refresh();
                        }
                    }

                    HidePopupEvent.fire(PropertyListPresenter.this, editor);
                }

                @Override
                public void onHide(final boolean autoClose, final boolean ok) {
                    // Do nothing.
                }
            };

            final PopupSize popupSize = new PopupSize(300, 149, 300, 149, 2000, 149, true);
            ShowPopupEvent.fire(this, editor, PopupType.OK_CANCEL_DIALOG, popupSize, "Edit Property", popupUiHandlers);
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
                    // Get the inherited property as we haven't set the property or removed (shadowed) the parent property.
                    final PipelineProperty inherited = getInheritedProperty(property);
                    if (inherited != null) {
                        property = inherited;
                    }
                }
            }
            // Add the property.
            propertyList.add(property);

            // If the property is a doc ref then we will have to look it up on the server to get the current name for the entity.
            if (property.getValue() != null && property.getValue().getEntity() != null) {
                docRefs.add(property.getValue().getEntity());
            }
        }

        if (docRefs.size() > 0) {
            // Load entities.
            dispatcher.exec(new FetchDocRefsAction(docRefs)).onSuccess(result -> {
                final Map<DocRef, DocRef> fetchedDocRefs = result
                        .stream()
                        .collect(Collectors.toMap(Function.identity(), Function.identity()));

                for (final PipelineProperty property : propertyList) {
                    final DocRef docRef = property.getValue().getEntity();
                    if (docRef != null) {
                        final DocRef fetchedDocRef = fetchedDocRefs.get(docRef);
                        if (fetchedDocRef != null) {
                            property.getValue().setEntity(fetchedDocRef);
                        }
                    }
                }

                setData(propertyList);
            });

        } else {
            setData(propertyList);
        }
    }

    private void setData(final List<PipelineProperty> propertyList) {
        getView().getSelectionModel().clear();
        getView().setRowData(0, propertyList);
        getView().setRowCount(propertyList.size());
    }

    private void enableButtons() {
        final PipelineProperty selected = getView().getSelectionModel().getSelected();
        editButton.setEnabled(selected != null);
    }

    private void setDirty(final boolean dirty) {
        if (dirty) {
            DirtyEvent.fire(this, dirty);
        }
    }

    public void setPropertyTypes(final Map<PipelineElementType, Map<String, PipelinePropertyType>> propertyTypes) {
        this.allPropertyTypes = propertyTypes;
    }

    private PipelinePropertyValue getDefaultValue(final PipelinePropertyType propertyType) {
        final PipelinePropertyValue value = new PipelinePropertyValue();
        if ("boolean".equals(propertyType.getType())) {
            Boolean defaultValue = Boolean.TRUE;
            if (propertyType.getDefaultValue() != null && propertyType.getDefaultValue().length() > 0) {
                defaultValue = Boolean.parseBoolean(propertyType.getDefaultValue());
            }
            value.setBoolean(defaultValue);
        } else if ("int".equals(propertyType.getType())) {
            Integer defaultValue = 0;
            if (propertyType.getDefaultValue() != null && propertyType.getDefaultValue().length() > 0) {
                defaultValue = Integer.parseInt(propertyType.getDefaultValue());
            }
            value.setInteger(defaultValue);
        } else if ("long".equals(propertyType.getType())) {
            Long defaultValue = 0L;
            if (propertyType.getDefaultValue() != null && propertyType.getDefaultValue().length() > 0) {
                defaultValue = Long.parseLong(propertyType.getDefaultValue());
            }
            value.setLong(defaultValue);
        } else if ("String".equals(propertyType.getType())) {
            value.setString(propertyType.getDefaultValue());
        }

        return value;
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

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public enum Source implements HasDisplayValue {
        LOCAL("Local"), INHERIT("Inherit"), DEFAULT("Default");

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
