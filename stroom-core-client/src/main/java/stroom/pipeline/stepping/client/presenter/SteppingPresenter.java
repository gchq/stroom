/*
 *
 *  * Copyright 2017 Crown Copyright
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package stroom.pipeline.stepping.client.presenter;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.PresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.event.HasDirtyHandlers;
import stroom.entity.shared.DocRef;
import stroom.pipeline.shared.FetchPipelineDataAction;
import stroom.pipeline.shared.PipelineModelException;
import stroom.pipeline.shared.PipelineStepAction;
import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.StepLocation;
import stroom.pipeline.shared.StepType;
import stroom.pipeline.shared.SteppingResult;
import stroom.pipeline.shared.TextConverter;
import stroom.pipeline.shared.XSLT;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyType;
import stroom.pipeline.structure.client.presenter.PipelineModel;
import stroom.pipeline.structure.client.presenter.PipelineTreePresenter;
import stroom.streamstore.client.presenter.ClassificationUiHandlers;
import stroom.streamstore.client.presenter.DataPresenter;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;
import stroom.util.shared.Indicators;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.SvgIcon;
import stroom.widget.button.client.SvgIcons;
import stroom.widget.tab.client.presenter.Layer;
import stroom.widget.tab.client.presenter.LayerContainer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class SteppingPresenter extends MyPresenterWidget<SteppingPresenter.SteppingView> implements HasDirtyHandlers {
    private final PipelineStepAction action;
    private final PipelineTreePresenter pipelineTreePresenter;
    private final DataPresenter sourcePresenter;
    private final Provider<ElementPresenter> editorProvider;
    private final StepLocationPresenter stepLocationPresenter;
    private final StepControlPresenter stepControlPresenter;
    private final ClientDispatchAsync dispatcher;
    private final Map<String, ElementPresenter> editorMap = new HashMap<String, ElementPresenter>();
    private final PipelineModel pipelineModel;
    private final ButtonView saveButton;
    private boolean foundRecord;
    private boolean showingData;
    private boolean busyTranslating;
    private SteppingResult lastFoundResult;
    private SteppingResult currentResult;
    private ButtonPanel leftButtons;

    private Stream stream;

    @Inject
    public SteppingPresenter(final EventBus eventBus, final SteppingView view,
                             final PipelineTreePresenter pipelineTreePresenter,
                             final ClientDispatchAsync dispatcher, final DataPresenter sourcePresenter,
                             final Provider<ElementPresenter> editorProvider, final StepLocationPresenter stepLocationPresenter,
                             final StepControlPresenter stepControlPresenter) {
        super(eventBus, view);
        this.dispatcher = dispatcher;

        this.pipelineTreePresenter = pipelineTreePresenter;
        this.sourcePresenter = sourcePresenter;
        this.editorProvider = editorProvider;
        this.stepLocationPresenter = stepLocationPresenter;
        this.stepControlPresenter = stepControlPresenter;

        view.addWidgetRight(stepLocationPresenter.getView().asWidget());
        view.addWidgetRight(stepControlPresenter.getView().asWidget());
        view.setTreeView(pipelineTreePresenter.getView());

        sourcePresenter.setSteppingSource(true);

        pipelineModel = new PipelineModel();
        pipelineTreePresenter.setModel(pipelineModel);
        pipelineTreePresenter.setPipelineTreeBuilder(new SteppingPipelineTreeBuilder());
        pipelineTreePresenter.setAllowNullSelection(false);

        // Create the translation request to use.
        action = new PipelineStepAction();

        stepControlPresenter.setEnabledButtons(false, action.getStepType(), true, showingData, foundRecord);

        saveButton = addButtonLeft(SvgIcons.SAVE);
    }

    @Override
    protected void onBind() {
        registerHandler(
                pipelineTreePresenter.getSelectionModel().addSelectionChangeHandler(event -> {
                    final PipelineElement selectedElement = pipelineTreePresenter.getSelectionModel()
                            .getSelectedObject();
                    onSelect(selectedElement);
                }));
        registerHandler(stepLocationPresenter.addStepControlHandler(event -> step(event.getStepType(), event.getStepLocation())));
        registerHandler(stepControlPresenter.addStepControlHandler(event -> step(event.getStepType(), event.getStepLocation())));
        registerHandler(saveButton.addClickHandler(event -> save()));
    }

//    private ImageButtonView addButtonLeft(final String title, final ImageResource enabledImage,
//                                          final ImageResource disabledImage) {
//        if (leftButtons == null) {
//            leftButtons = new ButtonPanel();
//            getView().addWidgetLeft(leftButtons);
//        }
//
//        final ImageButtonView button = leftButtons.add(title, enabledImage, disabledImage, true);
//        return button;
//    }

    private ButtonView addButtonLeft(final SvgIcon preset) {
        if (leftButtons == null) {
            leftButtons = new ButtonPanel();
            getView().addWidgetLeft(leftButtons);
        }

        return leftButtons.add(preset);
    }

    private PresenterWidget<?> getContent(final PipelineElement element) {
        if (PipelineModel.SOURCE_ELEMENT.getElementType().equals(element.getElementType())) {
            return sourcePresenter;

        } else {
            DocRef docRef = null;

            final List<PipelineProperty> properties = pipelineModel.getProperties(element);
            if (properties != null && properties.size() > 0) {
                for (final PipelineProperty property : properties) {
                    if (property.getValue() != null) {
                        final PipelinePropertyType propertyType = property.getPropertyType();
                        if (propertyType.isDataEntity() && property.getValue().getEntity() != null) {
                            docRef = property.getValue().getEntity();
                        } else if (property.getName().toLowerCase().contains("pattern") && stream != null) {
                            String value = property.getValue().getString();
                            value = replace(value, "feed", stream.getFeed().getName());
                            value = replace(value, "pipeline", action.getPipeline().getName());

                            if (element.getElementType().getType().equalsIgnoreCase("XSLTFilter")) {
                                docRef = new DocRef(XSLT.ENTITY_TYPE, null, value);
                            } else {
                                docRef = new DocRef(TextConverter.ENTITY_TYPE, null, value);
                            }
                        }
                    }
                }
            }

            final String elementId = element.getId();
            ElementPresenter editorPresenter = editorMap.get(elementId);

            if (editorPresenter == null) {
                final DirtyHandler dirtyEditorHandler = event -> {
                    DirtyEvent.fire(SteppingPresenter.this, true);
                    saveButton.setEnabled(true);
                };

                final ElementPresenter presenter = editorProvider.get();

                presenter.setElementId(element.getId());
                presenter.setElementType(element.getElementType());
                presenter.setEntityRef(docRef);
                presenter.setPipelineStepAction(action);
                editorMap.put(elementId, presenter);
                presenter.addDirtyHandler(dirtyEditorHandler);

                editorPresenter = presenter;
            }

            // Refresh this editor if it needs it.
            refreshEditor(editorPresenter, elementId);

            return editorPresenter;
        }
    }

    private void refreshEditor(final ElementPresenter editorPresenter, final String elementId) {
        editorPresenter.load().onSuccess(result -> {
            if (editorPresenter.isRefreshRequired()) {
                editorPresenter.setRefreshRequired(false);

                // Update code pane.
                refreshEditorCodeIndicators(editorPresenter, elementId);

                // Update IO data.
                refreshEditorIO(editorPresenter, elementId);
            }
        });
    }

    private void refreshEditorCodeIndicators(final ElementPresenter editorPresenter, final String elementId) {
        // Only update the code indicators if we have a current result.
        if (currentResult != null && currentResult.getStepData() != null) {
            final SharedElementData elementData = currentResult.getStepData().getElementData(elementId);
            if (elementData != null) {
                final Indicators codeIndicators = elementData.getCodeIndicators();
                // Always set the indicators for the code pane as errors in the
                // code pane could be responsible for no record being found.
                editorPresenter.setCodeIndicators(codeIndicators);
            }
        }
    }

    private void refreshEditorIO(final ElementPresenter editorPresenter, final String elementId) {
        // Only update the input/output if we found a record.
        if (lastFoundResult != null) {
            final SharedElementData elementData = lastFoundResult.getStepData().getElementData(elementId);
            if (elementData != null) {
                final Indicators outputIndicators = elementData.getOutputIndicators();
                final String input = notNull(elementData.getInput());
                final String output = notNull(elementData.getOutput());

                editorPresenter.setInput(input, 1, elementData.isFormatInput(), null);

                if (output.length() == 0 && outputIndicators != null && outputIndicators.getMaxSeverity() != null) {
                    editorPresenter.setOutput(outputIndicators.toString(), 1, false, null);
                } else {
                    // Don't try and format text output.
                    editorPresenter.setOutput(output, 1, elementData.isFormatOutput(), outputIndicators);
                }
            } else {
                // // if we didn't find a record then it could be the input that
                // is
                // // responsible. Show any error that has been created..
                // if (inputIndicators != null && inputIndicators.hasSummary())
                // {
                // editorPresenter.setInputIndicators(inputIndicators);
                // } else if (outputIndicators != null
                // && outputIndicators.hasSummary()) {
                // editorPresenter.setOutputIndicators(outputIndicators);
                // }
            }
        }
    }

    public void read(final DocRef pipeline, final Stream stream, final long eventId,
                     final StreamType childStreamType) {
        this.stream = stream;

        // Load the stream.
        sourcePresenter.fetchData(true, stream.getId(), childStreamType);

        // Set the pipeline on the stepping action.
        action.setPipeline(pipeline);

        // Set the stream id on the stepping action.
        final FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.obtainStreamIdSet().add(stream.getId());
        action.setCriteria(findStreamCriteria);
        action.setChildStreamType(childStreamType);

        // Load the pipeline.
        final FetchPipelineDataAction fetchPipelineDataAction = new FetchPipelineDataAction(pipeline);
        dispatcher.exec(fetchPipelineDataAction).onSuccess(result -> {
            final PipelineData pipelineData = result.get(result.size() - 1);
            final List<PipelineData> baseStack = new ArrayList<>(result.size() - 1);

            // If there is a stack of pipeline data then we need
            // to make sure changes are reflected appropriately.
            for (int i = 0; i < result.size() - 1; i++) {
                baseStack.add(result.get(i));
            }

            try {
                pipelineModel.setPipelineData(pipelineData);
                pipelineModel.setBaseStack(baseStack);
                pipelineModel.build();
                pipelineTreePresenter.getSelectionModel().setSelected(PipelineModel.SOURCE_ELEMENT, true);

                Scheduler.get().scheduleDeferred(() -> getView().setTreeHeight(pipelineTreePresenter.getTreeHeight() + 3));
            } catch (final PipelineModelException e) {
                AlertEvent.fireError(SteppingPresenter.this, e.getMessage(), null);
            }

            if (eventId > 0) {
                step(StepType.REFRESH, new StepLocation(stream.getId(), 1L, eventId));
            }
        });
    }

    private void save() {
        // Tell all editors to save.
        for (final Entry<String, ElementPresenter> entry : editorMap.entrySet()) {
            entry.getValue().save();
        }
        DirtyEvent.fire(this, false);
        saveButton.setEnabled(false);
    }

    private void step(final StepType stepType, final StepLocation stepLocation) {
        if (!busyTranslating) {
            busyTranslating = true;

            // If we are stepping to the first or last record then clear all
            // current state from the action.
            if (StepType.FIRST.equals(stepType) || StepType.LAST.equals(stepType)) {
                action.reset();
            }

            // Is the event telling us to jump to a specific location?
            if (stepLocation != null) {
                action.setStepLocation(stepLocation);
            }

            // Set dirty code on action.
            final Map<String, String> codeMap = new HashMap<>();
            for (final ElementPresenter editorPresenter : editorMap.values()) {
                if (editorPresenter.isDirtyCode()) {
                    final String elementId = editorPresenter.getElementId();
                    final String code = editorPresenter.getCode();
                    codeMap.put(elementId, code);
                }
            }
            action.setCode(codeMap);

            action.setStepType(stepType);

            dispatcher.exec(action)
                    .onSuccess(this::readResult)
                    .onFailure(caught -> busyTranslating = false);
        }
    }

    private void readResult(final SteppingResult result) {
        try {
            currentResult = result;
            foundRecord = result.isFoundRecord();
            if (foundRecord) {
                showingData = true;
                lastFoundResult = result;
            }

            // Tell all editors that a refresh is required.
            for (final Entry<String, ElementPresenter> entry : editorMap.entrySet()) {
                entry.getValue().setRefreshRequired(true);
            }

            // Refresh the currently selected editor.
            final PipelineElement selectedElement = pipelineTreePresenter.getSelectionModel().getSelectedObject();
            if (selectedElement != null) {
                final String elementId = selectedElement.getId();
                final ElementPresenter editorPresenter = editorMap.get(elementId);
                if (editorPresenter != null) {
                    refreshEditor(editorPresenter, elementId);
                }
            }

            if (foundRecord) {
                // Determine the type of input for input highlighting.
                final StreamType childStreamType = action.getChildStreamType();

                // Set the source selection and highlight.
                sourcePresenter.showStepSource(result.getCurrentStreamOffset(), result.getStepLocation(),
                        childStreamType, result.getStepData().getSourceHighlights());

                // We found a record so update the display to indicate the
                // record that was found and update the request with the new
                // position ready for the next step.
                stepLocationPresenter.setStepLocation(result.getStepLocation());
                action.setStepLocation(result.getStepLocation());
            }

            // Sync step filters.
            action.setStepFilterMap(result.getStepFilterMap());

            if (result.getGeneralErrors() != null && result.getGeneralErrors().size() > 0) {
                final StringBuilder sb = new StringBuilder();
                for (final String err : result.getGeneralErrors()) {
                    sb.append(err);
                    sb.append("\n");
                }

                AlertEvent.fireError(this, "Some errors occured during stepping", sb.toString(), null);
            }

        } finally {
            // final boolean tasksSelected = inputSelected();
            stepControlPresenter.setEnabledButtons(true, action.getStepType(), true, showingData, foundRecord);
            busyTranslating = false;
        }
    }

    /**
     * Ensures we don't set a null string into a field by returning an empty
     * string instead of null.
     *
     * @param str
     * @return
     */
    private String notNull(final String str) {
        if (str == null) {
            return "";
        }

        return str;
    }

    private void onSelect(final PipelineElement element) {
        if (element != null) {
            TaskStartEvent.fire(SteppingPresenter.this);
            Scheduler.get().scheduleDeferred(() -> {
                final PresenterWidget<?> content = getContent(element);
                if (content != null) {
                    // Set the content.
                    getView().getLayerContainer().show((Layer) content);
                }

                TaskEndEvent.fire(SteppingPresenter.this);
            });
        }
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public void setClassificationUiHandlers(final ClassificationUiHandlers classificationUiHandlers) {
        sourcePresenter.setClassificationUiHandlers(classificationUiHandlers);
    }

    public interface SteppingView extends View {
        void setTreeHeight(int height);

        void addWidgetLeft(Widget widget);

        void addWidgetRight(Widget widget);

        void setTreeView(View view);

        LayerContainer getLayerContainer();
    }

    private static String replace(final String path, final String type, final String replacement) {
        String newPath = path;
        final String param = "${" + type + "}";
        int start = newPath.indexOf(param);
        while (start != -1) {
            final int end = start + param.length();
            newPath = newPath.substring(0, start) + replacement + newPath.substring(end);
            start = newPath.indexOf(param, end);
        }

        return newPath;
    }
}
