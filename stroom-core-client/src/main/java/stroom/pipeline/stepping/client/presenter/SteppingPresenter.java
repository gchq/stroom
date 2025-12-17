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

package stroom.pipeline.stepping.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.data.client.presenter.ClassificationUiHandlers;
import stroom.data.client.presenter.SourcePresenter;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.PipelineModelException;
import stroom.pipeline.shared.PipelineResource;
import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.SourceLocation;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineLayer;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.SharedStepData;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.shared.stepping.SteppingFilterSettings;
import stroom.pipeline.shared.stepping.SteppingResource;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.structure.client.presenter.PipelineElementTypesFactory;
import stroom.pipeline.structure.client.presenter.PipelineModel;
import stroom.pipeline.structure.client.presenter.PipelineTreePresenter;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.task.client.SimpleTask;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.util.shared.DataRange;
import stroom.util.shared.ElementId;
import stroom.util.shared.Indicators;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.Layer;
import com.gwtplatform.mvp.client.LayerContainer;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.PresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class SteppingPresenter
        extends MyPresenterWidget<SteppingPresenter.SteppingView>
        implements HasDirtyHandlers, ClassificationUiHandlers {

    private static final PipelineResource PIPELINE_RESOURCE = GWT.create(PipelineResource.class);
    private static final SteppingResource STEPPING_RESOURCE = GWT.create(SteppingResource.class);

    private final PipelineStepRequest.Builder requestBuilder = PipelineStepRequest.builder();
    private final PipelineTreePresenter pipelineTreePresenter;
    private final SourcePresenter sourcePresenter;
    private final Provider<ElementPresenter> elementPresenterProvider;
    private final SimplePanel stepMessage;
    private final StepLocationLinkPresenter stepLocationLinkPresenter;
    private final StepControlPresenter stepControlPresenter;
    private final SteppingFilterPresenter steppingFilterPresenter;
    private final PipelineElementTypesFactory pipelineElementTypesFactory;
    private final RestFactory restFactory;
    // elementId => ElementPresenter
    private final Map<ElementId, ElementPresenter> elementPresenterMap = new HashMap<>();
    private PipelineModel pipelineModel;
    private final ButtonView saveButton;
    private final InlineSvgButton terminateButton;
    private final InlineSvgToggleButton toggleLogPaneButton;
    private boolean foundRecord;
    private boolean showingData;
    private boolean busyTranslating;
    private SteppingResult lastFoundResult;
    private SteppingResult currentResult;
    private final ButtonPanel leftButtons;

    private Meta meta;
    private String classification;
    private ElementPresenter currentElementPresenter = null;

    @Inject
    public SteppingPresenter(final EventBus eventBus,
                             final SteppingView view,
                             final PipelineTreePresenter pipelineTreePresenter,
                             final RestFactory restFactory,
                             final SourcePresenter sourcePresenter,
                             final Provider<ElementPresenter> elementPresenterProvider,
                             final StepLocationLinkPresenter stepLocationLinkPresenter,
                             final StepControlPresenter stepControlPresenter,
                             final SteppingFilterPresenter steppingFilterPresenter,
                             final PipelineElementTypesFactory pipelineElementTypesFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;

        this.pipelineTreePresenter = pipelineTreePresenter;
        this.sourcePresenter = sourcePresenter;
        this.elementPresenterProvider = elementPresenterProvider;
        this.stepLocationLinkPresenter = stepLocationLinkPresenter;
        this.stepControlPresenter = stepControlPresenter;
        this.steppingFilterPresenter = steppingFilterPresenter;
        this.pipelineElementTypesFactory = pipelineElementTypesFactory;

        terminateButton = new InlineSvgButton();
        terminateButton.setEnabled(false);
        terminateButton.addStyleName("QueryButtons-button stop");
        terminateButton.setSvg(SvgImage.STOP);
        terminateButton.setTitle("Terminate Stepping");
        final ButtonPanel buttonPanel = new ButtonPanel();
        buttonPanel.addButton(terminateButton);

        view.addWidgetRight(stepLocationLinkPresenter.getView().asWidget());
        view.addWidgetRight(stepControlPresenter.getView().asWidget());
        view.addWidgetRight(buttonPanel);
        view.setTreeView(pipelineTreePresenter.getView());

        sourcePresenter.getWidget().addStyleName("dashboard-panel overflow-hidden");
        sourcePresenter.getWidget().addStyleName("dashboard-panel overflow-hidden");
        sourcePresenter.setSteppingSource(true);

        pipelineTreePresenter.setPipelineTreeBuilder(new SteppingPipelineTreeBuilder());
        pipelineTreePresenter.setAllowNullSelection(false);

        stepControlPresenter.setEnabledButtons(
                false,
                requestBuilder.build().getStepType(),
                showingData,
                foundRecord,
                false,
                false,
                null);

        leftButtons = new ButtonPanel();
        saveButton = leftButtons.addButton(SvgPresets.SAVE);

        // Create but don't add yet
        toggleLogPaneButton = new InlineSvgToggleButton();
        toggleLogPaneButton.setSvg(SvgImage.EXCLAMATION);
        toggleLogPaneButton.setOn();
        toggleLogPaneButton.setTitle("Toggle Log Pane");
        leftButtons.addButton(toggleLogPaneButton);

        sourcePresenter.setClassificationUiHandlers(this);

        this.stepMessage = new SimplePanel();

        final FlowPanel stepToolbar = new FlowPanel();
        stepToolbar.addStyleName("stepToolbar dock-container-horizontal dock-min");
        stepToolbar.add(leftButtons);
        stepToolbar.add(stepMessage);
        getView().addWidgetLeft(stepToolbar);
    }

    @Override
    public void setClassification(final String classification) {
        this.classification = classification;
        for (final ElementPresenter elementPresenter : elementPresenterMap.values()) {
            elementPresenter.setClassification(classification);
        }
    }

    @Override
    protected void onBind() {
        registerHandler(
                pipelineTreePresenter.getSelectionModel().addSelectionChangeHandler(event -> {
                    final PipelineElement selectedElement = getSelectedPipeElement();
                    onSelect(selectedElement);
                }));
        registerHandler(stepLocationLinkPresenter.addStepControlHandler(event ->
                step(event.getStepType(), event.getStepLocation())));
        registerHandler(stepControlPresenter.addStepControlHandler(event ->
                step(event.getStepType(), event.getStepLocation())));
        registerHandler(stepControlPresenter.addChangeFilterHandler(event ->
                showChangeFiltersDialog()));
        registerHandler(saveButton.addClickHandler(event -> save()));
        registerHandler(terminateButton.addClickHandler(event -> terminate()));
        registerHandler(toggleLogPaneButton.addClickHandler(event -> {
            final ElementPresenter elementPresenter = getCurrentElementPresenter();
            if (elementPresenter != null) {
                elementPresenter.setDesiredLogPanVisibility(toggleLogPaneButton.isOn());
                elementPresenter.setLogPaneVisibility(toggleLogPaneButton.isOn());
            }
        }));

        registerHandler(pipelineTreePresenter.addContextMenuHandler(event -> {
            final PipelineElement selectedPipeElement = getSelectedPipeElement();
            if (!PipelineModel.SOURCE_ELEMENT.getId().equals(selectedPipeElement.getId())) {
                final List<Item> menuItems = buildContextMenu();
                if (NullSafe.hasItems(menuItems)) {
                    showMenu(menuItems, event.getPopupPosition());
                }
            }
        }));
    }

    private ElementPresenter getCurrentElementPresenter() {
        return currentElementPresenter;
    }

    private void showChangeFiltersDialog() {
        pipelineElementTypesFactory.get(this, elementTypes -> {
            final List<PipelineElement> elements = new ArrayList<>();
            getDescendantFilters(PipelineModel.SOURCE_ELEMENT, pipelineModel.getChildMap(), elements);
//            GWT.log("elements: \n" + NullSafe.stream(elements)
//                    .map(PipelineElement::toString)
//                    .map(str -> "  " + str)
//                    .collect(Collectors.joining("\n")));

            // Make a note of the selected element as it is lost on refresh
            final PipelineElement selectedObject = pipelineTreePresenter.getSelectionModel()
                    .getSelectedObject();
            steppingFilterPresenter.show(
                    elements,
                    getSelectedPipeElement(),
                    pipelineModel.getStepFilterMap(),
                    stepFilterMap -> {
                        pipelineModel.setStepFilterMap(stepFilterMap);
                        // Need to refresh the view in case any elements need to reflect active filters
                        pipelineTreePresenter.getView().refresh();
                        pipelineTreePresenter.getSelectionModel().setSelected(selectedObject, true);
                    });
        });
    }

    private boolean hasActiveFilters(final PipelineElement element) {
        return NullSafe.getOrElse(pipelineModel, pm -> pm.hasActiveFilters(element), false);
    }

    private List<Item> buildContextMenu() {
        final List<Item> menuItems = new ArrayList<>();

        final boolean isClearFiltersOnSelectedEnabled = hasActiveFilters(getSelectedPipeElement());

        final List<PipelineElement> elements = new ArrayList<>();
        getDescendantFilters(PipelineModel.SOURCE_ELEMENT, pipelineModel.getChildMap(), elements);
        final boolean isClearAllFiltersEnabled = elements.stream()
                .anyMatch(this::hasActiveFilters);

        menuItems.add(new IconMenuItem.Builder()
                .priority(0)
                .icon(SvgImage.EDIT)
                .text("Manage step filters")
                .enabled(true)
                .command(this::showChangeFiltersDialog)
                .build());
        menuItems.add(new IconMenuItem.Builder()
                .priority(0)
                .icon(SvgImage.DELETE)
                .text("Clear step filters on this element")
                .enabled(isClearFiltersOnSelectedEnabled)
                .command(this::clearFiltersOnSelected)
                .build());
        menuItems.add(new IconMenuItem.Builder()
                .priority(0)
                .icon(SvgImage.DELETE)
                .text("Clear step filters on all elements")
                .enabled(isClearAllFiltersEnabled)
                .command(this::clearAllFilters)
                .build());

        return menuItems;
    }

    private void clearAllFilters() {
        pipelineModel.setStepFilterMap(null);
        pipelineTreePresenter.getView().refresh();
    }

    private void clearFiltersOnSelected() {
        final PipelineElement selectedPipeElement = getSelectedPipeElement();
        final Map<String, SteppingFilterSettings> stepFilterMap = pipelineModel.getStepFilterMap();
        if (selectedPipeElement != null && stepFilterMap != null) {
            final SteppingFilterSettings steppingFilterSettings = stepFilterMap.get(selectedPipeElement.getId());
            if (steppingFilterSettings != null) {
                final Map<String, SteppingFilterSettings> newMap = new HashMap<>(stepFilterMap);
                newMap.remove(selectedPipeElement.getId());

                // Update the model so the filter icon in the pipe elements is updated
                pipelineModel.setStepFilterMap(newMap);
            }
        }
        pipelineTreePresenter.getView().refresh();
    }

    private void showMenu(final List<Item> menuItems,
                          final PopupPosition popupPosition) {
        ShowMenuEvent
                .builder()
                .items(menuItems)
                .popupPosition(popupPosition)
                .fire(this);
    }

    private PipelineElement getSelectedPipeElement() {
        return pipelineTreePresenter.getSelectionModel().getSelectedObject();
    }

    private void getDescendantFilters(final PipelineElement parent,
                                      final Map<PipelineElement, List<PipelineElement>> childMap,
                                      final List<PipelineElement> descendants) {
        final List<PipelineElement> children = childMap.get(parent);
        if (NullSafe.hasItems(children)) {
            for (final PipelineElement child : children) {
                if (pipelineModel.hasRole(child, PipelineElementType.VISABILITY_STEPPING)) {
                    descendants.add(child);
                }
                getDescendantFilters(child, childMap, descendants);
            }
        }
    }

    private PresenterWidget<?> getContent(final PipelineElement element) {
        if (PipelineModel.SOURCE_ELEMENT.getType().equals(element.getType())) {
            currentElementPresenter = null;
            updateToggleConsoleBtn(null);
            return sourcePresenter;
        } else {
            final ElementId elementId = element.getElementId();
            ElementPresenter elementPresenter = elementPresenterMap.get(elementId);
            if (elementPresenter == null) {
                final DirtyHandler dirtyEditorHandler = event -> {
                    DirtyEvent.fire(SteppingPresenter.this, true);
                    saveButton.setEnabled(true);
                };

                final List<PipelineProperty> properties = pipelineModel.getProperties(element);

                final ElementPresenter presenter = elementPresenterProvider.get();
                presenter.setPipelineModel(pipelineModel);
                presenter.setTaskMonitorFactory(this);
                presenter.setElement(element);
                presenter.setProperties(properties);
                presenter.setFeedName(meta.getFeedName());
                presenter.setPipelineName(requestBuilder.build().getPipeline().getName());
                presenter.setClassification(classification);
                elementPresenterMap.put(elementId, presenter);
                presenter.addDirtyHandler(dirtyEditorHandler);

                // Allow step refresh to be called from the editor
                presenter.setStepRequestHandler(stepType -> {
                    if (stepControlPresenter.isEnabled(stepType)) {
                        stepControlPresenter.step(stepType);
                    }
                });

                elementPresenter = presenter;
            }
            currentElementPresenter = elementPresenter;

            // Refresh this editor if it needs it.
            refreshEditor(elementPresenter, elementId);

            return elementPresenter;
        }
    }

    private void refreshEditor(final ElementPresenter elementPresenter,
                               final ElementId elementId) {
        elementPresenter.load(result -> {
            final SharedStepData stepData = getEffectiveStepData();
            if (stepData != null) {
                final SharedElementData elementData = stepData.getElementData(elementId.getId());
                if (elementData != null) {
                    final Indicators indicators = elementData.getIndicators();

                    // Update the error indicators for all panes
                    elementPresenter.setIndicators(indicators);

                    // Update IO data.
                    refreshEditorIO(elementPresenter, elementData);

                    // Update with any errors not specific to an editor pane
//                            refreshGenericErrors(elementPresenter, elementId);

                    updateToggleConsoleBtnVisibility(indicators, elementId);
                } else {
                    clearIndicators(elementPresenter, elementId);
                }
            } else {
                clearIndicators(elementPresenter, elementId);
            }
        });
    }

    private void clearIndicators(final ElementPresenter elementPresenter,
                                 final ElementId elementId) {
        elementPresenter.clearAllIndicators();
        updateToggleConsoleBtnVisibility(null, elementId);
    }

    private void updateToggleConsoleBtn(final ElementId elementId) {
        final SharedStepData stepData = getEffectiveStepData();

        // Only update the code indicators if we have a current result.
        if (stepData != null) {
            final SharedElementData elementData = NullSafe.get(elementId, ElementId::getId, stepData::getElementData);
            if (elementData != null) {
                final Indicators indicators = elementData.getIndicators();
                updateToggleConsoleBtnVisibility(indicators, elementId);
            } else {
                updateToggleConsoleBtnVisibility(null, elementId);
            }
        } else {
            updateToggleConsoleBtnVisibility(null, elementId);
        }
    }

    private void updateToggleConsoleBtnVisibility(final Indicators indicators, final ElementId elementId) {
        final Severity maxSeverity = NullSafe.get(indicators, Indicators::getMaxSeverity);
        final boolean isButtonVisible = maxSeverity != null;

        final ElementPresenter elementPresenter = NullSafe.get(elementId, elementPresenterMap::get);
        final boolean isLogPaneVisible = isButtonVisible
                                         && elementPresenter != null
                                         && elementPresenter.getDesiredLogPanVisibility();

        setLogPaneVisibility(isLogPaneVisible);

        if (maxSeverity != null) {
            final int count = NullSafe.getOrElse(
                    indicators,
                    indicators2 -> indicators2.getCount(maxSeverity),
                    0);
            final String plural = count > 1
                    ? "s"
                    : "";
            final String type;
            if (Severity.INFO.equals(maxSeverity)) {
                type = "Information Message";
            } else if (Severity.WARNING.equals(maxSeverity)) {
                type = "Warning";
            } else if (Severity.ERROR.equals(maxSeverity)) {
                type = "Error";
            } else if (Severity.FATAL_ERROR.equals(maxSeverity)) {
                type = "Fatal Error";
            } else {
                type = "Message";
            }
            final String msg = "Toggle Log Pane ("
                               + count + " " + type + plural + ")";
            toggleLogPaneButton.setTitle(msg);
        }

        final boolean hasButton = leftButtons.containsButton(toggleLogPaneButton);
        if (isButtonVisible && !hasButton) {
            leftButtons.addButton(toggleLogPaneButton);
        } else if (!isButtonVisible && hasButton) {
            leftButtons.removeButton(toggleLogPaneButton);
        }
        // Now set the button state based on the desired state from the presenter
        if (isButtonVisible) {
            toggleLogPaneButton.setState(isLogPaneVisible);
        }
    }

    private void refreshEditorIO(final ElementPresenter elementPresenter, final SharedElementData elementData) {

        final String input = NullSafe.string(elementData.getInput());
        final String output = NullSafe.string(elementData.getOutput());

        elementPresenter.setInput(
                input,
                1,
                elementData.isFormatInput());

        elementPresenter.setOutput(
                output,
                1,
                elementData.isFormatOutput());
    }

    public void read(final DocRef pipeline,
                     final StepType stepType,
                     final StepLocation stepLocation,
                     final Meta meta,
                     final String childStreamType) {
        pipelineElementTypesFactory.get(this, elementTypes -> {
            this.meta = meta;

            // Load the stream.
            // When we start stepping we are not on a record so want to see
            // from the start of the stream for non-segmented with no highlight and
            // nothing for segmented. DataFetcher will interpret the -1 rec no to return
            // the right data.
            final SourceLocation sourceLocation = SourceLocation.builder(meta.getId())
                    .withChildStreamType(childStreamType)
                    .withPartIndex(stepLocation.getPartIndex())
                    .withRecordIndex(Math.max(stepLocation.getRecordIndex(), 0))
                    .build();
            sourcePresenter.setSourceLocation(sourceLocation);

            // Set the pipeline on the stepping action.
            requestBuilder.pipeline(pipeline);

            // Set the stream id on the stepping action.
            final FindMetaCriteria findMetaCriteria = FindMetaCriteria.createFromMeta(meta);
            requestBuilder.criteria(findMetaCriteria);
            requestBuilder.childStreamType(childStreamType);

            // Load the pipeline.
            restFactory
                    .create(PIPELINE_RESOURCE)
                    .method(res -> res.fetchPipelineLayers(pipeline))
                    .onSuccess(result -> {
                        final PipelineLayer pipelineLayer = result.get(result.size() - 1);
                        final List<PipelineLayer> baseStack = new ArrayList<>(result.size() - 1);

                        // If there is a stack of pipeline data then we need
                        // to make sure changes are reflected appropriately.
                        for (int i = 0; i < result.size() - 1; i++) {
                            baseStack.add(result.get(i));
                        }

                        try {
                            if (pipelineModel == null) {
                                pipelineModel = new PipelineModel(elementTypes);
                                pipelineTreePresenter.setModel(pipelineModel);
                            }
                            pipelineModel.setPipelineLayer(pipelineLayer);
                            pipelineModel.setBaseStack(baseStack);
                            pipelineModel.build();
                            pipelineTreePresenter.getSelectionModel()
                                    .setSelected(PipelineModel.SOURCE_ELEMENT, true);

                            Scheduler.get().scheduleDeferred(() ->
                                    getView().setTreeHeight(pipelineTreePresenter.getTreeHeight() + 13));
                        } catch (final PipelineModelException e) {
                            AlertEvent.fireError(SteppingPresenter.this, e.getMessage(), null);
                        }

                        if (stepType != null) {
                            step(stepType, new StepLocation(
                                    meta.getId(),
                                    stepLocation.getPartIndex(),
                                    stepLocation.getRecordIndex()));
                        }
                    })
                    .taskMonitorFactory(this)
                    .exec();
        });
    }

    public void save() {
        // Tell all editors to save.
        for (final Entry<ElementId, ElementPresenter> entry : elementPresenterMap.entrySet()) {
            entry.getValue().save();
        }
        DirtyEvent.fire(this, false);
        saveButton.setEnabled(false);
    }

    private void step(final StepType stepType,
                      final StepLocation stepLocation) {
        if (!busyTranslating) {
            busyTranslating = true;
            stepMessage.getElement().setInnerHTML("Stepping...");
            stepMessage.setVisible(true);
            terminateButton.setEnabled(true);

            // Set a null session UUID as this is a new stepping session.
            requestBuilder.sessionUuid(null);

            // If we are stepping to the first or last record then clear all
            // current state from the action.
            if (StepType.FIRST.equals(stepType) || StepType.LAST.equals(stepType)) {
                requestBuilder.stepLocation(null);
                requestBuilder.stepType(null);

                final Map<String, SteppingFilterSettings> stepFilterMap = pipelineModel.getStepFilterMap();
                if (stepFilterMap != null) {
                    stepFilterMap.values().forEach(SteppingFilterSettings::clearUniqueValues);
                }
            }

            // Is the event telling us to jump to a specific location?
            if (stepLocation != null) {
                requestBuilder.stepLocation(stepLocation);
            }

            // Set dirty code on action.
            final Map<String, String> codeMap = new HashMap<>();
            for (final ElementPresenter editorPresenter : elementPresenterMap.values()) {
                if (editorPresenter.isDirtyCode()) {
                    final String elementId = editorPresenter.getElement().getId();
                    final String code = editorPresenter.getCode();
                    codeMap.put(elementId, code);
                }
            }
            requestBuilder.timeout(40L);
            requestBuilder.code(codeMap);
            requestBuilder.stepType(stepType);
            requestBuilder.stepFilterMap(pipelineModel.getStepFilterMap());

            poll();
        }
    }

    private void poll() {
        restFactory
                .create(STEPPING_RESOURCE)
                .method(res -> res.step(requestBuilder.build()))
                .onSuccess(response -> {
                    if (!response.isComplete()) {
                        if (busyTranslating) {
                            final StepLocation progressLocation = response.getProgressLocation();
                            stepMessage.getElement().setInnerHTML("Stepping... " +
                                                                  getStepLocationText(progressLocation));
                            requestBuilder.sessionUuid(response.getSessionUuid());
                            poll();
                        } else {
                            stop();
                        }
                    } else {
                        readResult(response);
                        stop();
                    }
                })
                .onFailure(restError -> {
                    AlertEvent.fireErrorFromException(SteppingPresenter.this, restError.getException(), null);
                    busyTranslating = false;
                })
                .taskMonitorFactory(this)
                .exec();
    }

    private String getStepLocationText(final StepLocation stepLocation) {
        if (stepLocation == null) {
            return "";
        }

        return "[" +
               stepLocation.getMetaId() + ":" +
               (stepLocation.getPartIndex() + 1) + ":" +
               (stepLocation.getRecordIndex() + 1) +
               "]";
    }

    public void terminate() {
        stop();
        final PipelineStepRequest request = requestBuilder.build();
        if (request.getSessionUuid() != null) {
            restFactory
                    .create(STEPPING_RESOURCE)
                    .method(res -> res.terminateStepping(request))
                    .onSuccess(response -> {
                        // Ignore response, we will assume we have stopped.
                    })
                    .taskMonitorFactory(this)
                    .exec();
        }
    }

    private void stop() {
        busyTranslating = false;
        stepMessage.getElement().setInnerHTML("");
        terminateButton.setEnabled(false);
    }

    private SharedStepData getEffectiveStepData() {
        final SteppingResult steppingResult;
        if (currentResult == null) {
            steppingResult = null;
        } else {
            if (currentResult.isFoundRecord()) {
                steppingResult = currentResult;
            } else {
                if (lastFoundResult != null
                    && Objects.equals(currentResult.getFoundLocation(), lastFoundResult.getFoundLocation())
                    && NullSafe.isEmptyCollection(currentResult.getGeneralErrors())
                    && !NullSafe.test(currentResult.getStepData(), SharedStepData::hasIndicators)) {
                    // Same location as last time and no errors/indicators in the curr result, so just display
                    // the last one.
                    steppingResult = lastFoundResult;
                } else {
                    steppingResult = currentResult;
                }
            }
        }
        return NullSafe.get(steppingResult, SteppingResult::getStepData);
    }

    private void updateElementSeverities() {
        // If we have not found a record then we have hit the end or run out of filter matches
        // so are still on the last result, thus show the messages based on what it was before.
        final SharedStepData stepData = getEffectiveStepData();

        if (stepData != null && stepData.getElementMap() != null) {
            final Map<String, Severity> elementIdToSeveritiesMap = stepData.getElementMap()
                    .entrySet()
                    .stream()
                    .map(entry -> {
                        final SharedElementData elementData = entry.getValue();
                        final Severity maxSeverity = elementData == null || elementData.getIndicators() == null
                                ? null
                                : elementData.getIndicators().getMaxSeverity();
                        return new AbstractMap.SimpleEntry<>(
                                entry.getKey(),
                                maxSeverity);
                    })
                    .filter(entry -> entry.getValue() != null)
                    .collect(Collectors.toMap(
                            Entry::getKey,
                            Entry::getValue));
            pipelineTreePresenter.setElementSeverities(elementIdToSeveritiesMap);
        } else {
            // No data so clear them all
            pipelineTreePresenter.setElementSeverities(null);
        }
    }

    private void readResult(final SteppingResult result) {
        Optional<String> fatalErrors = Optional.empty();
        try {
            currentResult = result;
            foundRecord = result.isFoundRecord();
            if (foundRecord) {
                showingData = true;
                lastFoundResult = result;
            }

            updateElementSeverities();

            // Tell all editors that a refresh is required.
            for (final Entry<ElementId, ElementPresenter> entry : elementPresenterMap.entrySet()) {
                entry.getValue().setRefreshRequired(true);
            }

            // Refresh the currently selected editor.
            final PipelineElement selectedElement = getSelectedPipeElement();
            if (selectedElement != null) {
                final ElementId elementId = selectedElement.getElementId();
                final ElementPresenter elementPresenter = elementPresenterMap.get(elementId);
                if (elementPresenter != null) {
                    refreshEditor(elementPresenter, elementId);
                } else {
                    updateToggleConsoleBtn(elementId);
                }
            }

            if (foundRecord) {
                // What we display depends on whether it is segmented (cooked) or not (raw)
                // Segmented shows one event segment on the screen with no highlighting
                // Non-segmented shows the record/event highlighted amongst a load of context.
                if (result.isSegmentedData()) {
                    // Strip any highlighting
                    final SourceLocation newSourceLocation = result.getStepData()
                            .getSourceLocation()
                            .copy()
                            .withHighlight((DataRange) null)
                            .build();
                    sourcePresenter.setSourceLocation(newSourceLocation);
                } else {
                    sourcePresenter.setSourceLocationUsingHighlight(result.getStepData().getSourceLocation());
                }

                // We found a record so update the display to indicate the
                // record that was found and update the request with the new
                // position ready for the next step.
                requestBuilder.stepLocation(result.getFoundLocation());
                stepLocationLinkPresenter.setStepLocation(result.getFoundLocation());
            }

            // Sync step filters.
            pipelineModel.setStepFilterMap(result.getStepFilterMap());

            if (result.getGeneralErrors() != null && !result.getGeneralErrors().isEmpty()) {
                final StringBuilder sb = new StringBuilder();
                for (final String err : result.getGeneralErrors()) {
                    sb.append(err);
                    sb.append("\n");
                }
            } else {
                fatalErrors = getFatalErrors(result);
                fatalErrors.ifPresent(errorText -> {
                });
            }
        } finally {
            stepControlPresenter.setEnabledButtons(
                    true,
                    requestBuilder.build().getStepType(),
                    showingData,
                    foundRecord,
                    fatalErrors.isPresent(),
                    result.hasActiveFilter(),
                    result.getFoundLocation());
            busyTranslating = false;
        }
    }

    private Optional<String> getFatalErrors(final SteppingResult steppingResult) {
        if (steppingResult.getStepData() != null
            && steppingResult.getStepData().getElementMap() != null) {
            final String txt = steppingResult.getStepData().getElementMap()
                    .values()
                    .stream()
                    .flatMap(sharedElementData -> {
                        final Set<StoredError> errors = NullSafe.getOrElseGet(
                                sharedElementData.getIndicators(),
                                Indicators::getUniqueErrorSet,
                                HashSet::new);
                        return errors.stream();
                    })
                    .filter(error -> Severity.FATAL_ERROR.equals(error.getSeverity()))
                    .map(StoredError::toString)
                    .collect(Collectors.joining("\n"));

            return !txt.isEmpty()
                    ? Optional.of(txt)
                    : Optional.empty();
        } else {
            return Optional.empty();
        }
    }

    private void onSelect(final PipelineElement element) {
        if (element != null) {
            final TaskMonitor taskMonitor = createTaskMonitor();
            final Task task = new SimpleTask("Stepping");
            taskMonitor.onStart(task);
            Scheduler.get().scheduleDeferred(() ->
                    pipelineElementTypesFactory.get(this, elementTypes -> {
                        final PresenterWidget<?> content = getContent(element);
                        if (content != null) {
                            // Set the content.
                            getView().getLayerContainer().show((Layer) content);

                            updateElementSeverities();
                        }
                        taskMonitor.onEnd(task);
                    }));
        }
    }

    private void setLogPaneVisibility(final boolean isVisible) {
        final PipelineElement selectedElement = getSelectedPipeElement();
        if (selectedElement != null) {
            final ElementId elementId = selectedElement.getElementId();
            final ElementPresenter elementPresenter = elementPresenterMap.get(elementId);
            if (elementPresenter != null) {
                elementPresenter.setLogPaneVisibility(isVisible);
            }
        }
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    // --------------------------------------------------------------------------------


    public interface SteppingView extends View {

        void setTreeHeight(int height);

        void addWidgetLeft(Widget widget);

        void addWidgetRight(Widget widget);

        void setTreeView(View view);

        LayerContainer getLayerContainer();
    }
}
