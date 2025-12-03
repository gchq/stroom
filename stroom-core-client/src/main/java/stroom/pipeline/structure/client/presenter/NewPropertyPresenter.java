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

import stroom.alert.client.event.AlertEvent;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.data.store.impl.fs.shared.FsVolumeGroupResource;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.item.client.SelectionBox;
import stroom.meta.shared.MetaResource;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyType;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.pipeline.structure.client.presenter.PropertyListPresenter.Source;
import stroom.pipeline.structure.client.view.NewPropertyUiHandlers;
import stroom.security.shared.DocumentPermission;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.stream.Collectors;

public class NewPropertyPresenter
        extends MyPresenterWidget<NewPropertyPresenter.NewPropertyView>
        implements NewPropertyUiHandlers {

    private static final MetaResource META_RESOURCE = GWT.create(MetaResource.class);
    private static final FsVolumeGroupResource VOLUME_GROUP_RESOURCE = GWT.create(FsVolumeGroupResource.class);

    private final RestFactory restFactory;
    private final DocSelectionBoxPresenter entityDropDownPresenter;
    private boolean dirty;

    private PipelinePropertyType propertyType;
    private PipelineProperty defaultProperty;
    private PipelineProperty inheritedProperty;
    private PipelineProperty localProperty;
    private Source source;

    private boolean booleanListInitialised;
    private SelectionBox<String> listBox;

    private boolean spinnerInitialised;
    private ValueSpinner valueSpinner;

    private boolean textBoxInitialised;
    private TextBox textBox;

    private String currentDataType;
    private String currentVolumeGroup;
    private boolean dataTypePresenterInitialised;
    private SelectionBox<String> dataTypeWidget;
    private boolean entityPresenterInitialised;

    @Inject
    public NewPropertyPresenter(final EventBus eventBus,
                                final NewPropertyView view,
                                final DocSelectionBoxPresenter entityDropDownPresenter,
                                final RestFactory restFactory) {
        super(eventBus, view);
        this.entityDropDownPresenter = entityDropDownPresenter;
        this.restFactory = restFactory;
        view.setUiHandlers(this);
    }

    @Override
    public void onSourceChange(final Source source) {
        if (!this.source.equals(source)) {
            NewPropertyPresenter.this.source = source;
            setDirty(true, false);
            startEdit(source);
        }
    }

    public void edit(final PipelinePropertyType propertyType,
                     final PipelineProperty defaultProperty,
                     final PipelineProperty inheritedProperty,
                     final PipelineProperty localProperty,
                     final Source source,
                     final String defaultValue,
                     final String inheritedValue,
                     final String inheritedFrom) {
        this.propertyType = propertyType;
        this.defaultProperty = defaultProperty;
        this.inheritedProperty = inheritedProperty;
        this.localProperty = localProperty;
        this.source = source;

        getView().setElement(defaultProperty.getElement());
        getView().setName(defaultProperty.getName());
        getView().setDescription(propertyType.getDescription());
        getView().setDefaultValue(defaultValue);
        getView().setInherited(inheritedFrom, inheritedValue);
        getView().setSource(source);

        startEdit(source);
    }

    private void startEdit(final Source source) {
        switch (source) {
            case DEFAULT:
                startEdit(defaultProperty);
                break;
            case INHERIT:
                startEdit(inheritedProperty);
                break;
            case LOCAL:
                startEdit(localProperty);
                break;
        }
    }

    private void startEdit(final PipelineProperty property) {
        if ("streamType".equals(propertyType.getName())) {
            enterDataTypeMode(property);
        } else if ("volumeGroup".equals(propertyType.getName())) {
            enterVolumeGroupMode(property);
        } else if ("boolean".equals(propertyType.getType())) {
            enterBooleanMode(property);
        } else if ("int".equals(propertyType.getType())) {
            enterIntegerMode(property);
        } else if ("long".equals(propertyType.getType())) {
            enterLongMode(property);
        } else if ("String".equals(propertyType.getType())) {
            enterStringMode(property);
//        } else if (StreamType.DOCUMENT_TYPE.equals(propertyType.getType())) {
//            enterStreamTypeMode(property);
        } else {
            enterEntityMode(property);
        }
    }

    public Source getSource() {
        return getView().getSource();
    }

    public PipelinePropertyValue writeValue() {
        if ("streamType".equals(propertyType.getName())) {
            return new PipelinePropertyValue(dataTypeWidget.getValue());
        } else if ("volumeGroup".equals(propertyType.getName())) {
            return new PipelinePropertyValue(dataTypeWidget.getValue());
        } else if ("boolean".equals(propertyType.getType())) {
            final String value = listBox.getValue();
            return new PipelinePropertyValue(Boolean.valueOf(value));
        } else if ("int".equals(propertyType.getType())) {
            final Integer value = valueSpinner.getIntValue();
            return new PipelinePropertyValue(value);
        } else if ("long".equals(propertyType.getType())) {
            final Long value = (long) valueSpinner.getIntValue();
            return new PipelinePropertyValue(value);
        } else if ("String".equals(propertyType.getType())) {
            return new PipelinePropertyValue(textBox.getText());
//        } else if (StreamType.DOCUMENT_TYPE.equals(propertyType.getType())) {
//            property.setValue(new PipelinePropertyValue(streamTypesWidget.getSelectedItem()));
        } else {
            final DocRef namedEntity = entityDropDownPresenter.getSelectedEntityReference();
            PipelinePropertyValue value = null;
            if (namedEntity != null) {
                value = new PipelinePropertyValue(namedEntity);
            }
            return value;
        }
    }

    private void enterBooleanMode(final PipelineProperty property) {
        if (!booleanListInitialised) {
            listBox = new SelectionBox<>();
            listBox.addItem("true");
            listBox.addItem("false");

            listBox.addValueChangeHandler(event -> {
                setDirty(true);
                getView().setSource(Source.LOCAL);
            });

            listBox.getElement().getStyle().setWidth(100, Unit.PCT);
            listBox.getElement().getStyle().setMarginBottom(0, Unit.PX);
            getView().setValueWidget(listBox);

            booleanListInitialised = true;
        }

        Boolean value = Boolean.FALSE;
        if (property.getValue() != null && property.getValue().getBoolean() != null) {
            value = property.getValue().getBoolean();
        }

        listBox.setValue(value.toString().toLowerCase());
    }

    private void enterIntegerMode(final PipelineProperty property) {
        Long number = 0L;
        if (property.getValue() != null && property.getValue().getInteger() != null) {
            number = Long.valueOf(property.getValue().getInteger());
        }

        enterNumberMode(number);
    }

    private void enterLongMode(final PipelineProperty property) {
        Long number = 0L;
        if (property.getValue() != null && property.getValue().getLong() != null) {
            number = property.getValue().getLong();
        }

        enterNumberMode(number);
    }

    private void enterNumberMode(final Long number) {
        if (!spinnerInitialised) {
            valueSpinner = new ValueSpinner();
            valueSpinner.setMin(0);
            valueSpinner.setMax(10000000);

            registerHandler(valueSpinner.addValueChangeHandler(event -> {
                setDirty(true);
                getView().setSource(Source.LOCAL);
            }));

            valueSpinner.getElement().getStyle().setWidth(100, Unit.PCT);
            valueSpinner.getElement().getStyle().setMarginBottom(0, Unit.PX);
            getView().setValueWidget(valueSpinner);

            spinnerInitialised = true;
        }

        valueSpinner.setValue(number);
    }

    private void enterStringMode(final PipelineProperty property) {
        if (!textBoxInitialised) {
            textBox = new TextBox();

            textBox.addValueChangeHandler(event -> {
                setDirty(true);
                getView().setSource(Source.LOCAL);
            });

            textBox.getElement().getStyle().setWidth(100, Unit.PCT);
            textBox.getElement().getStyle().setMarginBottom(0, Unit.PX);
            getView().setValueWidget(textBox);

            textBoxInitialised = true;
        }

        String value = "";
        if (property.getValue() != null && property.getValue().getString() != null) {
            value = property.getValue().getString();
        }

        textBox.setText(value);
    }

    private void enterDataTypeMode(final PipelineProperty property) {
        if (!dataTypePresenterInitialised) {
            dataTypeWidget = new SelectionBox<>();

            // Load data types.
            restFactory
                    .create(META_RESOURCE)
                    .method(MetaResource::getTypes)
                    .onSuccess(result -> {
                        if (result != null) {
                            dataTypeWidget.addItems(result);
                        }

                        if (currentDataType != null) {
                            dataTypeWidget.setValue(currentDataType);
                        }

                        dataTypePresenterInitialised = true;
                    })
                    .taskMonitorFactory(this)
                    .exec();

            dataTypeWidget.addValueChangeHandler(event -> {
                setDirty(true);
                getView().setSource(Source.LOCAL);
            });

            final Widget widget = dataTypeWidget;
            widget.getElement().getStyle().setWidth(100, Unit.PCT);
            widget.getElement().getStyle().setMarginBottom(0, Unit.PX);
            getView().setValueWidget(widget);

            dataTypePresenterInitialised = true;
        }

        currentDataType = null;
        if (property.getValue() != null && property.getValue().getString() != null) {
            currentDataType = property.getValue().toString();
        }

        dataTypeWidget.setValue(currentDataType);
    }

    private void enterVolumeGroupMode(final PipelineProperty property) {
        if (!dataTypePresenterInitialised) {
            dataTypeWidget = new SelectionBox<>();

            // Load data types.
            restFactory
                    .create(VOLUME_GROUP_RESOURCE)
                    .method(res -> res.find(new ExpressionCriteria()))
                    .onSuccess(result -> {
                        dataTypeWidget.clear();
                        dataTypeWidget.setNonSelectString("");
                        if (result != null && result.getValues() != null) {
                            dataTypeWidget.addItems(
                                    result
                                            .getValues()
                                            .stream()
                                            .map(FsVolumeGroup::getName)
                                            .collect(Collectors.toList()));
                        }

                        if (currentVolumeGroup != null) {
                            dataTypeWidget.setValue(currentVolumeGroup);
                        }

                        dataTypePresenterInitialised = true;
                    })
                    .taskMonitorFactory(this)
                    .exec();

            dataTypeWidget.addValueChangeHandler(event -> {
                setDirty(true);
                getView().setSource(Source.LOCAL);
            });

            final Widget widget = dataTypeWidget;
            widget.getElement().getStyle().setWidth(100, Unit.PCT);
            widget.getElement().getStyle().setMarginBottom(0, Unit.PX);
            getView().setValueWidget(widget);

            dataTypePresenterInitialised = true;
        }

        currentVolumeGroup = null;
        if (property.getValue() != null && property.getValue().getString() != null) {
            currentVolumeGroup = property.getValue().toString();
        }

        dataTypeWidget.setValue(currentVolumeGroup);
    }

    private void enterEntityMode(final PipelineProperty property) {
        if (!entityPresenterInitialised) {
            entityDropDownPresenter.addDataSelectionHandler(event -> {
                setDirty(true);
                getView().setSource(Source.LOCAL);
            });

            final Widget widget = entityDropDownPresenter.getView().asWidget();
            widget.getElement().getStyle().setWidth(100, Unit.PCT);
            widget.getElement().getStyle().setMarginBottom(0, Unit.PX);
            getView().setValueWidget(widget);

            entityPresenterInitialised = true;
        }

        DocRef value = null;
        if (property.getValue() != null && property.getValue().getEntity() != null) {
            value = property.getValue().getEntity();
        }

        entityDropDownPresenter.setIncludedTypes(propertyType.getDocRefTypes());
        entityDropDownPresenter.setRequiredPermissions(DocumentPermission.USE);
        try {
            entityDropDownPresenter.setSelectedEntityReference(value, true);
        } catch (final RuntimeException e) {
            AlertEvent.fireError(this, e.getMessage(), null);
        }

    }

    private void setDirty(final boolean dirty, final boolean changeSource) {
        this.dirty = dirty;
        if (changeSource) {
            getView().setSource(Source.LOCAL);
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    private void setDirty(final boolean dirty) {
        setDirty(dirty, true);
    }


    // --------------------------------------------------------------------------------


    public interface NewPropertyView extends View, Focus, HasUiHandlers<NewPropertyUiHandlers> {

        void setElement(String element);

        void setName(String name);

        void setDescription(String description);

        void setDefaultValue(String defaultValue);

        void setInherited(String inheritedFrom, String inheritedValue);

        Source getSource();

        void setSource(Source source);

        void setValueWidget(Widget widget);
    }
}
