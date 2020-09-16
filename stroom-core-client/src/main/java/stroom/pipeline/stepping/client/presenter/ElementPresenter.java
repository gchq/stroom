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

package stroom.pipeline.stepping.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginRegistry;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.editor.client.view.IndicatorLines;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.stepping.FindElementDocRequest;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.SteppingFilterSettings;
import stroom.pipeline.shared.stepping.SteppingResource;
import stroom.pipeline.stepping.client.event.ShowSteppingFilterSettingsEvent;
import stroom.pipeline.stepping.client.presenter.ElementPresenter.ElementView;
import stroom.util.shared.HasData;
import stroom.widget.util.client.Future;
import stroom.widget.util.client.FutureImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;

public class ElementPresenter extends MyPresenterWidget<ElementView> implements HasDirtyHandlers {
    private static final SteppingResource STEPPING_RESOURCE = GWT.create(SteppingResource.class);

    private final Provider<EditorPresenter> editorProvider;
    private final DocumentPluginRegistry documentPluginRegistry;
    private final RestFactory restFactory;

    private PipelineElement element;
    private List<PipelineProperty> properties;
    private String feedName;
    private String pipelineName;
    private PipelineStepRequest pipelineStepRequest;
    private boolean refreshRequired = true;
    private boolean loaded;
    private boolean dirtyCode;
    private DocRef loadedDoc;
    private HasData hasData;
    private IndicatorLines codeIndicators;
    private EditorPresenter codePresenter;
    private EditorPresenter inputPresenter;
    private EditorPresenter outputPresenter;

    @Inject
    public ElementPresenter(final EventBus eventBus, final ElementView view,
                            final Provider<EditorPresenter> editorProvider,
                            final DocumentPluginRegistry documentPluginRegistry,
                            final RestFactory restFactory) {
        super(eventBus, view);
        this.editorProvider = editorProvider;
        this.documentPluginRegistry = documentPluginRegistry;
        this.restFactory = restFactory;
    }

    public Future<Boolean> load() {
        final FutureImpl<Boolean> future = new FutureImpl<>();

        if (!loaded) {
            loaded = true;
            boolean loading = false;

            if (element.getElementType().hasRole(PipelineElementType.ROLE_HAS_CODE)) {
                getView().setCodeView(getCodePresenter().getView());

                try {
                    final FindElementDocRequest findElementDocRequest = new FindElementDocRequest.Builder()
                            .pipelineElement(element)
                            .properties(properties)
                            .feedName(feedName)
                            .pipelineName(pipelineName)
                            .build();

                    final Rest<DocRef> rest = restFactory.create();
                    rest
                            .onSuccess(result -> loadEntityRef(result, future))
                            .onFailure(caught -> {
                                dirtyCode = false;
                                setCode(caught.getMessage(), null);
                                future.setResult(false);
                            })
                            .call(STEPPING_RESOURCE)
                            .findElementDoc(findElementDocRequest);

                    loading = true;
                } catch (final RuntimeException e) {
                    AlertEvent.fireErrorFromException(this, e, null);
                }
            }

            // We only care about seeing input if the element mutates the input
            // some how.
            if (element.getElementType().hasRole(PipelineElementType.ROLE_MUTATOR)) {
                getView().setInputView(getInputPresenter().getView());
            }

            // We always want to see the output of the element.
            getView().setOutputView(getOutputPresenter().getView());

            if (!loading) {
                Scheduler.get().scheduleDeferred(() -> future.setResult(true));
            }
        } else {
            future.setResult(true);
        }

        return future;
    }

    private void loadEntityRef(final DocRef entityRef, final FutureImpl<Boolean> future) {
        if (entityRef != null) {
            final DocumentPlugin<?> documentPlugin = documentPluginRegistry.get(entityRef.getType());
            documentPlugin.load(entityRef,
                    result -> {
                        loadedDoc = entityRef;
                        hasData = (HasData) result;
                        dirtyCode = false;
                        read();

                        future.setResult(true);
                    },
                    caught -> {
                        dirtyCode = false;
                        setCode(caught.getMessage(), null);
                        future.setResult(false);
                    });
        } else {
            Scheduler.get().scheduleDeferred(() -> future.setResult(true));
        }
    }

    public void save() {
        if (loaded && hasData != null && dirtyCode) {
            write();
            final DocumentPlugin documentPlugin = documentPluginRegistry.get(loadedDoc.getType());
            documentPlugin.save(loadedDoc, hasData,
                    result -> {
                        hasData = (HasData) result;
                        dirtyCode = false;
                    },
                    throwable -> {
                        AlertEvent.fireError(
                                this,
                                "Unable to save document " + loadedDoc,
                                ((Throwable) throwable).getMessage(), null);
                    });
        }
    }

    private void read() {
        if (hasData != null) {
            setCode(hasData.getData(), codeIndicators);
        } else {
            setCode("", codeIndicators);
        }
    }

    private void write() {
        hasData.setData(getCode());
    }

    public String getCode() {
        if (codePresenter == null) {
            return null;
        }
        return codePresenter.getText();
    }

    public void setCode(final String code,
                        final IndicatorLines codeIndicators) {
        if (codePresenter != null) {
            this.codeIndicators = codeIndicators;

            if (!codePresenter.getText().equals(code)) {
                codePresenter.setText(code);
            }

            codePresenter.setIndicators(codeIndicators);

            // Done here to ensure the editor is attached
            codePresenter.getCodeCompletionOption().setAvailable(true);
            codePresenter.getCodeCompletionOption().setOn(true, true);
        }
    }

    public void setCodeIndicators(final IndicatorLines codeIndicators) {
        if (codePresenter != null) {
            this.codeIndicators = codeIndicators;
            codePresenter.setIndicators(codeIndicators);
        }
    }

    public void setInput(final String input,
                         final int inputStartLineNo,
                         final boolean formatInput,
                         final IndicatorLines inputIndicators) {
        if (inputPresenter != null) {
            inputPresenter.getStylesOption().setOn(formatInput);

            if (!inputPresenter.getText().equals(input)) {
                inputPresenter.setText(input, formatInput);
            }

            inputPresenter.setFirstLineNumber(inputStartLineNo);
            inputPresenter.setIndicators(inputIndicators);

            inputPresenter.getCodeCompletionOption().setAvailable(false);
            inputPresenter.getCodeCompletionOption().setOn(false);
        }
    }

    public void setOutput(final String output,
                          final int outputStartLineNo,
                          final boolean formatOutput,
                          final IndicatorLines outputIndicators) {
        if (outputPresenter != null) {
            outputPresenter.getStylesOption().setOn(formatOutput);

            if (!outputPresenter.getText().equals(output)) {
                outputPresenter.setText(output, formatOutput);
            }

            outputPresenter.setFirstLineNumber(outputStartLineNo);
            outputPresenter.setIndicators(outputIndicators);

            outputPresenter.getCodeCompletionOption().setAvailable(false);
            outputPresenter.getCodeCompletionOption().setOn(false);
        }
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public PipelineElement getElement() {
        return element;
    }

    public void setElement(final PipelineElement element) {
        this.element = element;
    }

    public void setProperties(final List<PipelineProperty> properties) {
        this.properties = properties;
    }

    public void setFeedName(final String feedName) {
        this.feedName = feedName;
    }

    public void setPipelineName(final String pipelineName) {
        this.pipelineName = pipelineName;
    }

    public void setPipelineStepRequest(final PipelineStepRequest pipelineStepRequest) {
        this.pipelineStepRequest = pipelineStepRequest;
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

    private EditorPresenter getCodePresenter() {
        if (codePresenter == null) {
            codePresenter = editorProvider.get();
            setCommonEditorOptions(codePresenter);

            codePresenter.getFormatAction().setAvailable(true);

            registerHandler(codePresenter.addValueChangeHandler(event -> {
                dirtyCode = true;
                DirtyEvent.fire(ElementPresenter.this, true);
            }));
            registerHandler(codePresenter.addFormatHandler(event -> {
                dirtyCode = true;
                DirtyEvent.fire(ElementPresenter.this, true);
            }));
        }
        return codePresenter;
    }

    private EditorPresenter getInputPresenter() {
        if (inputPresenter == null) {
            inputPresenter = editorProvider.get();
            setCommonEditorOptions(inputPresenter);
            setReadOnlyEditorOptions(inputPresenter);

            inputPresenter.setShowFilterSettings(false);
            inputPresenter.setInput(true);
        }
        return inputPresenter;
    }

    private EditorPresenter getOutputPresenter() {
        if (outputPresenter == null) {
            outputPresenter = editorProvider.get();
            setCommonEditorOptions(outputPresenter);
            setReadOnlyEditorOptions(outputPresenter);

            // Turn on line numbers for the output presenter if this is a validation step as the output needs to show
            // validation errors in the gutter.
            if (element != null && element.getElementType().hasRole(PipelineElementType.ROLE_VALIDATOR)) {
                outputPresenter.getLineNumbersOption().setOn(true);
            }

            outputPresenter.setShowFilterSettings(true);
            outputPresenter.setInput(false);

            registerHandler(outputPresenter.addChangeFilterHandler(event -> {
                final SteppingFilterSettings settings = pipelineStepRequest.getStepFilter(element.getId());
                ShowSteppingFilterSettingsEvent.fire(ElementPresenter.this, outputPresenter, false, element.getId(),
                        settings);
            }));
        }
        return outputPresenter;
    }

    private void setReadOnlyEditorOptions(final EditorPresenter editorPresenter) {
        editorPresenter.setReadOnly(true);
        // Default to wrapped lines as a lot of output is un-formatted xml
        editorPresenter.getLineWrapOption().setOn(true);

        editorPresenter.getFormatAction().setAvailable(false);
    }

    private void setCommonEditorOptions(final EditorPresenter editorPresenter) {
        editorPresenter.getIndicatorsOption().setAvailable(true);
        editorPresenter.getIndicatorsOption().setOn(true);

        editorPresenter.getLineNumbersOption().setAvailable(true);
        editorPresenter.getLineNumbersOption().setOn(true);

        editorPresenter.getLineWrapOption().setAvailable(true);
        editorPresenter.getLineWrapOption().setOn(false);

        editorPresenter.getShowInvisiblesOption().setAvailable(true);
        editorPresenter.getShowInvisiblesOption().setOn(false);

        editorPresenter.getUseVimBindingsOption().setAvailable(true);
        editorPresenter.getUseVimBindingsOption().setOn(false);
    }

    public interface ElementView extends View {
        void setCodeView(View view);

        void setInputView(View view);

        void setOutputView(View view);
    }
}
