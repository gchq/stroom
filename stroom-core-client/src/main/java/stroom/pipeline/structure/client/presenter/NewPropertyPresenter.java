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

package stroom.pipeline.structure.client.presenter;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.*;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.dispatch.client.AsyncCallbackAdaptor;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.EntityReferenceFindAction;
import stroom.entity.shared.ResultList;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
import stroom.explorer.shared.ExplorerData;
import stroom.item.client.ItemListBox;
import stroom.item.client.StringListBox;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyType;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.pipeline.structure.client.presenter.PropertyListPresenter.Source;
import stroom.security.shared.DocumentPermissionNames;
import stroom.streamstore.shared.FindStreamTypeCriteria;
import stroom.streamstore.shared.StreamType.Purpose;
import stroom.util.shared.EqualsUtil;
import stroom.widget.valuespinner.client.SpinnerEvent;
import stroom.widget.valuespinner.client.ValueSpinner;

public class NewPropertyPresenter extends MyPresenterWidget<NewPropertyPresenter.NewPropertyView> {
    private final ClientDispatchAsync dispatcher;
    private final EntityDropDownPresenter entityDropDownPresenter;
    private boolean dirty;

    private PipelineProperty defaultProperty;
    private PipelineProperty inheritedProperty;
    private PipelineProperty localProperty;
    private Source source;

    private Boolean currentBoolean;
    private boolean booleanListInitialised;
    private ListBox listBox;

    private Long currentNumber;
    private boolean spinnerInitialised;
    private ValueSpinner valueSpinner;

    private String currentText;
    private boolean textBoxInitialised;
    private TextBox textBox;

    private String currentStreamType;
    private boolean streamTypePresenterInitialised;
    private StringListBox streamTypesWidget;
    private boolean entityPresenterInitialised;
    private DocRef currentEntity;

    @Inject
    public NewPropertyPresenter(final EventBus eventBus, final NewPropertyView view,
                                final EntityDropDownPresenter entityDropDownPresenter, final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.entityDropDownPresenter = entityDropDownPresenter;
        this.dispatcher = dispatcher;

    }

    @Override
    protected void onBind() {
        registerHandler(getView().getSource().addSelectionHandler(new SelectionHandler<PropertyListPresenter.Source>() {
            @Override
            public void onSelection(final SelectionEvent<Source> event) {
                final Source selected = event.getSelectedItem();
                if (!source.equals(selected)) {
                    NewPropertyPresenter.this.source = selected;
                    setDirty(true, false);
                    startEdit(selected);
                }
            }
        }));

    }

    public void edit(final PipelineProperty defaultProperty, final PipelineProperty inheritedProperty,
                     final PipelineProperty localProperty, final Source source) {
        this.defaultProperty = defaultProperty;
        this.inheritedProperty = inheritedProperty;
        this.localProperty = localProperty;
        this.source = source;

        getView().setElement(defaultProperty.getElement());
        getView().setName(defaultProperty.getName());
        getView().getSource().setSelectedItem(source);

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
        final PipelinePropertyType propertyType = property.getPropertyType();

        if ("streamType".equals(propertyType.getName())) {
            enterStreamTypeMode(property);
        } else if ("boolean".equals(propertyType.getType())) {
            enterBooleanMode(property);
        } else if ("int".equals(propertyType.getType())) {
            enterIntegerMode(property);
        } else if ("long".equals(propertyType.getType())) {
            enterLongMode(property);
        } else if ("String".equals(propertyType.getType())) {
            enterStringMode(property);
//        } else if (StreamType.ENTITY_TYPE.equals(propertyType.getType())) {
//            enterStreamTypeMode(property);
        } else {
            enterEntityMode(property);
        }
    }

    public Source getSource() {
        return getView().getSource().getSelectedItem();
    }

    public void write(final PipelineProperty property) {
        final PipelinePropertyType propertyType = property.getPropertyType();

        if ("streamType".equals(propertyType.getName())) {
            property.setValue(new PipelinePropertyValue(streamTypesWidget.getSelected()));
        } else if ("boolean".equals(propertyType.getType())) {
            final String value = listBox.getItemText(listBox.getSelectedIndex());
            property.setValue(new PipelinePropertyValue(Boolean.valueOf(value)));
        } else if ("int".equals(propertyType.getType())) {
            final Integer value = Integer.valueOf(valueSpinner.getValue());
            property.setValue(new PipelinePropertyValue(value));
        } else if ("long".equals(propertyType.getType())) {
            final Long value = Long.valueOf(valueSpinner.getValue());
            property.setValue(new PipelinePropertyValue(value));
        } else if ("String".equals(propertyType.getType())) {
            property.setValue(new PipelinePropertyValue(textBox.getText()));
//        } else if (StreamType.ENTITY_TYPE.equals(propertyType.getType())) {
//            property.setValue(new PipelinePropertyValue(streamTypesWidget.getSelectedItem()));
        } else {
            final DocRef namedEntity = entityDropDownPresenter.getSelectedEntityReference();
            PipelinePropertyValue value = null;
            if (namedEntity != null) {
                value = new PipelinePropertyValue(namedEntity);
            }
            property.setValue(value);
        }
    }

    private void enterBooleanMode(final PipelineProperty property) {
        if (!booleanListInitialised) {
            listBox = new ListBox();
            listBox.addItem("true");
            listBox.addItem("false");

            listBox.addChangeHandler(new ChangeHandler() {
                @Override
                public void onChange(final ChangeEvent event) {
                    final Boolean selected = getBoolean(listBox.getSelectedIndex());
                    if (!EqualsUtil.isEquals(selected, currentBoolean)) {
                        setDirty(true);
                    }
                }
            });

            listBox.getElement().getStyle().setWidth(100, Unit.PCT);
            listBox.getElement().getStyle().setMarginBottom(0, Unit.PX);
            getView().setValueWidget(listBox);

            booleanListInitialised = true;
        }

        currentBoolean = Boolean.FALSE;
        if (property.getValue() != null && property.getValue().isBoolean() != null) {
            currentBoolean = property.getValue().isBoolean();
        }

        setBoolean(currentBoolean);
    }

    private Boolean getBoolean(final int pos) {
        if (pos == 0) {
            return Boolean.TRUE;
        }

        return Boolean.FALSE;
    }

    private void setBoolean(final Boolean bool) {
        if (bool) {
            listBox.setSelectedIndex(0);
        } else {
            listBox.setSelectedIndex(1);
        }
    }

    private void enterIntegerMode(final PipelineProperty property) {
        Long number = 0L;
        if (property.getValue() != null && property.getValue().getInteger() != null) {
            number = Long.valueOf(property.getValue().getInteger());
        }

        enterNumberMode(property, number);
    }

    private void enterLongMode(final PipelineProperty property) {
        Long number = 0L;
        if (property.getValue() != null && property.getValue().getLong() != null) {
            number = property.getValue().getLong();
        }

        enterNumberMode(property, number);
    }

    private void enterNumberMode(final PipelineProperty property, final Long number) {
        if (!spinnerInitialised) {
            valueSpinner = new ValueSpinner();
            valueSpinner.setMin(0);
            valueSpinner.setMax(10000);

            registerHandler(valueSpinner.getTextBox().addKeyDownHandler(new KeyDownHandler() {
                @Override
                public void onKeyDown(final KeyDownEvent event) {
                    setDirty(true);
                }
            }));
            registerHandler(valueSpinner.getSpinner().addSpinnerHandler(new SpinnerEvent.Handler() {
                @Override
                public void onChange(final SpinnerEvent event) {
                    setDirty(true);
                }
            }));

            valueSpinner.getElement().getStyle().setWidth(100, Unit.PCT);
            valueSpinner.getElement().getStyle().setMarginBottom(0, Unit.PX);
            getView().setValueWidget(valueSpinner);

            spinnerInitialised = true;
        }

        currentNumber = number;
        valueSpinner.setValue(currentNumber);
    }

    private void enterStringMode(final PipelineProperty property) {
        if (!textBoxInitialised) {
            textBox = new TextBox();

            textBox.addKeyUpHandler(new KeyUpHandler() {
                @Override
                public void onKeyUp(final KeyUpEvent event) {
                    if (!EqualsUtil.isEquals(textBox.getText(), currentText)) {
                        setDirty(true);
                    }
                }
            });

            textBox.getElement().getStyle().setWidth(100, Unit.PCT);
            textBox.getElement().getStyle().setMarginBottom(0, Unit.PX);
            getView().setValueWidget(textBox);

            textBoxInitialised = true;
        }

        currentText = "";
        if (property.getValue() != null && property.getValue().getString() != null) {
            currentText = property.getValue().getString();
        }

        textBox.setText(currentText);
    }

    private void enterStreamTypeMode(final PipelineProperty property) {
        if (!streamTypePresenterInitialised) {
            streamTypesWidget = new StringListBox();

            // Load stream types.
            final FindStreamTypeCriteria findStreamTypeCriteria = new FindStreamTypeCriteria();
            findStreamTypeCriteria.obtainPurpose().add(Purpose.PROCESSED);
            dispatcher.execute(new EntityReferenceFindAction<>(findStreamTypeCriteria),
                    new AsyncCallbackAdaptor<ResultList<DocRef>>() {
                        @Override
                        public void onSuccess(final ResultList<DocRef> result) {
                            for (final DocRef streamType : result) {
                                streamTypesWidget.addItem(streamType.getName());
                            }
                            streamTypesWidget.setSelected(currentStreamType);
                        }
                    });

            streamTypesWidget.addChangeHandler(new ChangeHandler() {
                @Override
                public void onChange(final ChangeEvent event) {
                    final String streamType = streamTypesWidget.getSelected();
                    if (!EqualsUtil.isEquals(currentStreamType, streamType)) {
                        setDirty(true);
                    }
                }
            });

            final Widget widget = streamTypesWidget;
            widget.getElement().getStyle().setWidth(100, Unit.PCT);
            widget.getElement().getStyle().setMarginBottom(0, Unit.PX);
            getView().setValueWidget(widget);

            streamTypePresenterInitialised = true;
        }

        currentStreamType = null;
        if (property.getValue() != null && property.getValue().getString() != null) {
            currentStreamType = property.getValue().toString();
        }

        streamTypesWidget.setSelected(currentStreamType);
    }

    private void enterEntityMode(final PipelineProperty property) {
        if (!entityPresenterInitialised) {
            entityDropDownPresenter.addDataSelectionHandler(new DataSelectionHandler<ExplorerData>() {
                @Override
                public void onSelection(final DataSelectionEvent<ExplorerData> event) {
                    final DocRef selection = entityDropDownPresenter.getSelectedEntityReference();
                    if (!EqualsUtil.isEquals(currentEntity, selection)) {
                        setDirty(true);
                    }
                }
            });

            final Widget widget = entityDropDownPresenter.getView().asWidget();
            widget.getElement().getStyle().setWidth(100, Unit.PCT);
            widget.getElement().getStyle().setMarginBottom(0, Unit.PX);
            getView().setValueWidget(widget);

            entityPresenterInitialised = true;
        }

        currentEntity = null;
        if (property.getValue() != null && property.getValue().getEntity() != null) {
            currentEntity = property.getValue().getEntity();
        }

        entityDropDownPresenter.setIncludedTypes(property.getPropertyType().getType());
        entityDropDownPresenter.setRequiredPermissions(DocumentPermissionNames.USE);
        try {
            entityDropDownPresenter.setSelectedEntityReference(currentEntity);
        } catch (final Exception e) {
            AlertEvent.fireError(this, e.getMessage(), null);
        }

    }

    private void setDirty(final boolean dirty, final boolean changeSource) {
        this.dirty = dirty;
        if (changeSource) {
            getView().getSource().setSelectedItem(Source.LOCAL);
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    private void setDirty(final boolean dirty) {
        setDirty(dirty, true);
    }

    public interface NewPropertyView extends View {
        void setElement(String element);

        void setName(String name);

        ItemListBox<Source> getSource();

        void setValueWidget(Widget widget);
    }
}
