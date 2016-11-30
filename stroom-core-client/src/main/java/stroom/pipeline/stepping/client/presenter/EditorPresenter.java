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

package stroom.pipeline.stepping.client.presenter;

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.core.client.event.DirtyKeyDownHander;
import stroom.dispatch.client.AsyncCallbackAdaptor;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.event.HasDirtyHandlers;
import stroom.entity.shared.Entity;
import stroom.entity.shared.EntityServiceLoadAction;
import stroom.entity.shared.EntityServiceSaveAction;
import stroom.entity.shared.HasData;
import stroom.pipeline.shared.PipelineStepAction;
import stroom.pipeline.shared.SteppingFilterSettings;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.pipeline.stepping.client.event.ShowSteppingFilterSettingsEvent;
import stroom.util.shared.Indicators;
import stroom.xmleditor.client.event.ChangeFilterEvent;
import stroom.xmleditor.client.event.ChangeFilterEvent.ChangeFilterHandler;
import stroom.xmleditor.client.event.FormatEvent;
import stroom.xmleditor.client.event.FormatEvent.FormatHandler;
import stroom.xmleditor.client.presenter.BaseXMLEditorPresenter;
import stroom.xmleditor.client.presenter.ReadOnlyXMLEditorPresenter;
import stroom.xmleditor.client.presenter.XMLEditorPresenter;

public class EditorPresenter extends MyPresenterWidget<EditorPresenter.EditorView>implements HasDirtyHandlers {
    private final ClientDispatchAsync dispatcher;
    private final Provider<XMLEditorPresenter> editorProvider;
    private final Provider<ReadOnlyXMLEditorPresenter> readOnlyEditorProvider;
    private String elementId;
    private PipelineElementType elementType;
    private PipelinePropertyValue propertyValue;
    private PipelineStepAction pipelineStepAction;
    private boolean refreshRequired = true;
    private boolean loaded;
    private boolean dirtyCode;
    private Entity entity;
    private Indicators codeIndicators;
    private XMLEditorPresenter codePresenter;
    private ReadOnlyXMLEditorPresenter inputPresenter;
    private ReadOnlyXMLEditorPresenter outputPresenter;
    @Inject
    public EditorPresenter(final EventBus eventBus, final EditorView view,
            final Provider<XMLEditorPresenter> editorProvider,
            final Provider<ReadOnlyXMLEditorPresenter> readOnlyEditorProvider, final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.editorProvider = editorProvider;
        this.readOnlyEditorProvider = readOnlyEditorProvider;
        this.dispatcher = dispatcher;
    }

    public void load() {
        if (!loaded) {
            loaded = true;

            if (elementType != null && elementType.hasRole(PipelineElementType.ROLE_HAS_CODE) && propertyValue != null
                    && propertyValue.getEntity() != null) {
                getView().setCodeView(getCodePresenter().getView());

                try {
                    dispatcher.execute(new EntityServiceLoadAction<>(propertyValue.getEntity(), null), new AsyncCallbackAdaptor<Entity>() {
                                @Override
                                public void onSuccess(final Entity result) {
                                    entity = result;
                                    dirtyCode = false;
                                    read();
                                }
                            });
                } catch (final Exception e) {
                    AlertEvent.fireErrorFromException(this, e, null);
                }

            }

            // We only care about seeing input if the element mutates the input
            // some how.
            if (elementType != null && elementType.hasRole(PipelineElementType.ROLE_MUTATOR)) {
                getView().setInputView(getInputPresenter().getView());
            }

            // We always want to see the output of the element.
            getView().setOutputView(getOutputPresenter().getView());
        }
    }

    public void save() {
        if (loaded && entity != null && dirtyCode) {
            write();
            dispatcher.execute(new EntityServiceSaveAction<>(entity), new AsyncCallbackAdaptor<Entity>() {
                @Override
                public void onSuccess(final Entity result) {
                    entity = result;
                    dirtyCode = false;
                }
            });
        }
    }

    private void read() {
        if (entity != null && entity instanceof HasData) {
            final HasData hasData = (HasData) entity;
            setCode(hasData.getData(), 1, false, codeIndicators);
        } else {
            setCode("", 1, false, codeIndicators);
        }
    }

    private void write() {
        if (entity instanceof HasData) {
            final HasData hasData = (HasData) entity;
            hasData.setData(getCode());
        }
    }

    public String getCode() {
        if (codePresenter == null) {
            return null;
        }
        return codePresenter.getText();
    }

    public void setCode(final String code, final int codeStartLineNo, final boolean formatCode,
            final Indicators codeIndicators) {
        if (codePresenter != null) {
            this.codeIndicators = codeIndicators;
            codePresenter.setText(code, codeStartLineNo, formatCode, null, codeIndicators, false);
        }
    }

    public void setCodeIndicators(final Indicators codeIndicators) {
        if (codePresenter != null) {
            this.codeIndicators = codeIndicators;
            codePresenter.setIndicators(codeIndicators);
        }
    }

    public void setInput(final String input, final int inputStartLineNo, final boolean formatInput,
            final Indicators inputIndicators) {
        if (inputPresenter != null) {
            inputPresenter.getStylesOption().setOn(formatInput);
            inputPresenter.setText(input, inputStartLineNo, formatInput, null, inputIndicators, false);
        }
    }

    public void setOutput(final String output, final int outputStartLineNo, final boolean formatOutput,
            final Indicators outputIndicators) {
        if (outputPresenter != null) {
            outputPresenter.getStylesOption().setOn(formatOutput);
            outputPresenter.setText(output, outputStartLineNo, formatOutput, null, outputIndicators, false);
        }
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public String getElementId() {
        return elementId;
    }

    public void setElementId(final String elementId) {
        this.elementId = elementId;
    }

    public PipelineElementType getElementType() {
        return elementType;
    }

    public void setElementType(final PipelineElementType elementType) {
        this.elementType = elementType;
    }

    public void setPropertyValue(final PipelinePropertyValue propertyValue) {
        this.propertyValue = propertyValue;
    }

    public void setPipelineStepAction(final PipelineStepAction pipelineStepAction) {
        this.pipelineStepAction = pipelineStepAction;
    }

    public boolean isRefreshRequired() {
        return refreshRequired;
    }

    public void setRefreshRequired(final boolean refreshRequired) {
        this.refreshRequired = refreshRequired;
    }

    public boolean isDirtyCode() {
        return dirtyCode;
    }

    private XMLEditorPresenter getCodePresenter() {
        if (codePresenter == null) {
            codePresenter = editorProvider.get();
            setOptions(codePresenter);

            registerHandler(codePresenter.addKeyDownHandler(new DirtyKeyDownHander() {
                @Override
                public void onDirty(final KeyDownEvent event) {
                    dirtyCode = true;
                    DirtyEvent.fire(EditorPresenter.this, true);
                }
            }));
            registerHandler(codePresenter.addFormatHandler(new FormatHandler() {
                @Override
                public void onFormat(final FormatEvent event) {
                    dirtyCode = true;
                    DirtyEvent.fire(EditorPresenter.this, true);
                }
            }));
        }
        return codePresenter;
    }

    private ReadOnlyXMLEditorPresenter getInputPresenter() {
        if (inputPresenter == null) {
            inputPresenter = readOnlyEditorProvider.get();
            setOptions(inputPresenter);

            inputPresenter.setShowFilterSettings(false);
            inputPresenter.setInput(true);
        }
        return inputPresenter;
    }

    private ReadOnlyXMLEditorPresenter getOutputPresenter() {
        if (outputPresenter == null) {
            outputPresenter = readOnlyEditorProvider.get();
            setOptions(outputPresenter);

            outputPresenter.setShowFilterSettings(true);
            outputPresenter.setInput(false);
            registerHandler(outputPresenter.addChangeFilterHandler(new ChangeFilterHandler() {
                @Override
                public void onShowFilter(final ChangeFilterEvent event) {
                    final SteppingFilterSettings settings = pipelineStepAction.getStepFilter(elementId);
                    ShowSteppingFilterSettingsEvent.fire(EditorPresenter.this, outputPresenter, false, elementId,
                            settings);
                }
            }));
        }
        return outputPresenter;
    }

    private void setOptions(final BaseXMLEditorPresenter editorPresenter) {
        editorPresenter.getIndicatorsOption().setAvailable(true);
        editorPresenter.getIndicatorsOption().setOn(true);
        editorPresenter.getLineNumbersOption().setAvailable(true);
        editorPresenter.getLineNumbersOption().setOn(false);
    }

    public interface EditorView extends View {
        void setCodeView(View view);

        void setInputView(View view);

        void setOutputView(View view);
    }
}
