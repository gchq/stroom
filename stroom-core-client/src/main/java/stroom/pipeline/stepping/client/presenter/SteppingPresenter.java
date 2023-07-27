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
import stroom.data.client.presenter.ClassificationUiHandlers;
import stroom.data.client.presenter.SourcePresenter;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.editor.client.view.IndicatorLines;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.PipelineModelException;
import stroom.pipeline.shared.PipelineResource;
import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.SourceLocation;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.SharedStepData;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.shared.stepping.SteppingFilterSettings;
import stroom.pipeline.shared.stepping.SteppingResource;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.structure.client.presenter.PipelineModel;
import stroom.pipeline.structure.client.presenter.PipelineTreePresenter;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;
import stroom.util.shared.DataRange;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.Indicators;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
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

public class SteppingPresenter extends MyPresenterWidget<SteppingPresenter.SteppingView> implements
        HasDirtyHandlers,
        ClassificationUiHandlers {

    private static final PipelineResource PIPELINE_RESOURCE = GWT.create(PipelineResource.class);
    private static final SteppingResource STEPPING_RESOURCE = GWT.create(SteppingResource.class);

    private final PipelineStepRequest request;
    private final PipelineTreePresenter pipelineTreePresenter;
    private final SourcePresenter sourcePresenter;
    private final Provider<ElementPresenter> elementPresenterProvider;
    private final StepLocationPresenter stepLocationPresenter;
    private final StepControlPresenter stepControlPresenter;
    private final SteppingFilterPresenter steppingFilterPresenter;
    private final RestFactory restFactory;
    // elementId => ElementPresenter
    private final Map<String, ElementPresenter> elementPresenterMap = new HashMap<>();
    private final PipelineModel pipelineModel;
    private final ButtonView saveButton;
    private final InlineSvgToggleButton toggleLogPaneButton;
    private boolean foundRecord;
    private boolean showingData;
    private boolean busyTranslating;
    private SteppingResult lastFoundResult;
    private SteppingResult currentResult;
    private ButtonPanel leftButtons;

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
                             final StepLocationPresenter stepLocationPresenter,
                             final StepControlPresenter stepControlPresenter,
                             final SteppingFilterPresenter steppingFilterPresenter) {
        super(eventBus, view);
        this.restFactory = restFactory;

        this.pipelineTreePresenter = pipelineTreePresenter;
        this.sourcePresenter = sourcePresenter;
        this.elementPresenterProvider = elementPresenterProvider;
        this.stepLocationPresenter = stepLocationPresenter;
        this.stepControlPresenter = stepControlPresenter;
        this.steppingFilterPresenter = steppingFilterPresenter;

        view.addWidgetRight(stepLocationPresenter.getView().asWidget());
        view.addWidgetRight(stepControlPresenter.getView().asWidget());
        view.setTreeView(pipelineTreePresenter.getView());

        sourcePresenter.getWidget().addStyleName("dashboard-panel overflow-hidden");
        sourcePresenter.getWidget().addStyleName("dashboard-panel overflow-hidden");
        sourcePresenter.setSteppingSource(true);

        pipelineModel = new PipelineModel();
        pipelineTreePresenter.setModel(pipelineModel);
        pipelineTreePresenter.setPipelineTreeBuilder(new SteppingPipelineTreeBuilder());
        pipelineTreePresenter.setAllowNullSelection(false);

        // Create the translation request to use.
        request = new PipelineStepRequest();

        stepControlPresenter.setEnabledButtons(
                false,
                request.getStepType(),
                showingData,
                foundRecord,
                false,
                false,
                null);

        saveButton = addButtonLeft(SvgPresets.SAVE);
        // Create but don't add yet
        toggleLogPaneButton = new InlineSvgToggleButton();
        toggleLogPaneButton.setSvg(SvgImage.EXCLAMATION);
        toggleLogPaneButton.setOn();
        toggleLogPaneButton.setTitle("Toggle Log Pane");
        leftButtons.addButton(toggleLogPaneButton);

        sourcePresenter.setClassificationUiHandlers(this);
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
        registerHandler(stepLocationPresenter.addStepControlHandler(event ->
                step(event.getStepType(), event.getStepLocation())));
        registerHandler(stepControlPresenter.addStepControlHandler(event ->
                step(event.getStepType(), event.getStepLocation())));
        registerHandler(stepControlPresenter.addChangeFilterHandler(event -> {
            showChangeFiltersDialog();
        }));
        registerHandler(saveButton.addClickHandler(event -> save()));
        registerHandler(toggleLogPaneButton.addClickHandler(event -> {
            if (currentElementPresenter != null) {
                currentElementPresenter.setDesiredLogPanVisibility(toggleLogPaneButton.isOn());
                currentElementPresenter.setLogPaneVisibility(toggleLogPaneButton.isOn());
            }
        }));

        registerHandler(pipelineTreePresenter.addContextMenuHandler(event -> {
            final PipelineElement selectedPipeElement = getSelectedPipeElement();
            if (!PipelineModel.SOURCE_ELEMENT.getId().equals(selectedPipeElement.getId())) {
                final List<Item> menuItems = buildContextMenu();
                if (GwtNullSafe.hasItems(menuItems)) {
                    showMenu(menuItems, event.getPopupPosition());
                }
            }
        }));
    }

    private void showChangeFiltersDialog() {
        final List<PipelineElement> elements = new ArrayList<>();
        getDescendantFilters(PipelineModel.SOURCE_ELEMENT, pipelineModel.getChildMap(), elements);
//            GWT.log("elements: \n" + GwtNullSafe.stream(elements)
//                    .map(PipelineElement::toString)
//                    .map(str -> "  " + str)
//                    .collect(Collectors.joining("\n")));

        // Make a note of the selected element as it is lost on refresh
        final PipelineElement selectedObject = pipelineTreePresenter.getSelectionModel()
                .getSelectedObject();
        steppingFilterPresenter.show(
                elements,
                getSelectedPipeElement(),
                request.getStepFilterMap(),
                stepFilterMap -> {
                    pipelineModel.setStepFilters(stepFilterMap);
                    request.setStepFilterMap(stepFilterMap);
                    // Need to refresh the view in case any elements need to reflect active filters
                    pipelineTreePresenter.getView().refresh();
                    pipelineTreePresenter.getSelectionModel().setSelected(selectedObject, true);
                });
    }

    private List<Item> buildContextMenu() {
        final List<Item> menuItems = new ArrayList<>();

        final boolean isClearFiltersOnSelectedEnabled = GwtNullSafe.test(
                getSelectedPipeElement(),
                PipelineElement::hasActiveFilters);

        final List<PipelineElement> elements = new ArrayList<>();
        getDescendantFilters(PipelineModel.SOURCE_ELEMENT, pipelineModel.getChildMap(), elements);
        final boolean isClearAllFiltersEnabled = elements.stream()
                .anyMatch(PipelineElement::hasActiveFilters);

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
        GwtNullSafe.map(request.getStepFilterMap())
                .values()
                .forEach(SteppingFilterSettings::clearAllFilters);
        // Update the model so the filter icon in the pipe elements is updated
        pipelineModel.setStepFilters(request.getStepFilterMap());
        pipelineTreePresenter.getView().refresh();
    }

    private void clearFiltersOnSelected() {
        final PipelineElement selectedPipeElement = getSelectedPipeElement();
        if (selectedPipeElement != null && request.getStepFilterMap() != null) {
            final SteppingFilterSettings steppingFilterSettings = request.getStepFilterMap()
                    .get(selectedPipeElement.getId());
            if (steppingFilterSettings != null) {
                steppingFilterSettings.clearAllFilters();
                // Update the model so the filter icon in the pipe elements is updated
                pipelineModel.setStepFilters(request.getStepFilterMap());
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
        if (GwtNullSafe.hasItems(children)) {
            for (final PipelineElement child : children) {
                final PipelineElementType type = child.getElementType();
                if (type.hasRole(PipelineElementType.VISABILITY_STEPPING)) {
                    descendants.add(child);
                }
                getDescendantFilters(child, childMap, descendants);
            }
        }
    }

    private ButtonView addButtonLeft(final Preset preset) {
        if (leftButtons == null) {
            leftButtons = new ButtonPanel();
            getView().addWidgetLeft(leftButtons);
        }

        return leftButtons.addButton(preset);
    }

    private PresenterWidget<?> getContent(final PipelineElement element) {
        if (PipelineModel.SOURCE_ELEMENT.getElementType().equals(element.getElementType())) {
            updateToggleConsoleBtn(null);
            return sourcePresenter;
        } else {
            final String elementId = element.getId();
            ElementPresenter elementPresenter = elementPresenterMap.get(elementId);
            if (elementPresenter == null) {
                final DirtyHandler dirtyEditorHandler = event -> {
                    DirtyEvent.fire(SteppingPresenter.this, true);
                    saveButton.setEnabled(true);
                };
                updateToggleConsoleBtn(elementId);

                final List<PipelineProperty> properties = pipelineModel.getProperties(element);

                final ElementPresenter presenter = elementPresenterProvider.get();
                presenter.setElement(element);
                presenter.setProperties(properties);
                presenter.setFeedName(meta.getFeedName());
                presenter.setPipelineName(request.getPipeline().getName());
                presenter.setClassification(classification);
                elementPresenterMap.put(elementId, presenter);
                presenter.addDirtyHandler(dirtyEditorHandler);

                elementPresenter = presenter;
            }

            // Refresh this editor if it needs it.
            refreshEditor(elementPresenter, elementId);

            return elementPresenter;
        }
    }

    private void refreshEditor(final ElementPresenter elementPresenter, final String elementId) {
        elementPresenter.load()
                .onSuccess(result -> {
                    // Update code pane.
                    refreshEditorCodeIndicators(elementPresenter, elementId);
                    // Update IO data.
                    refreshEditorIO(elementPresenter, elementId);
                    updateToggleConsoleBtn(elementId);
                })
                .onFailure(throwable ->
                        AlertEvent.fireError(this, throwable.getMessage(), null));
    }

    private void refreshEditorCodeIndicators(final ElementPresenter elementPresenter, final String elementId) {
        final SharedStepData stepData = getEffectiveStepData();
        // Only update the code indicators if we have a current result.
        if (stepData != null) {
            final SharedElementData elementData = stepData.getElementData(elementId);
            if (elementData != null) {
                final Indicators codeIndicators = elementData.getCodeIndicators();
                // Always set the indicators for the code pane as errors in the
                // code pane could be responsible for no record being found.
                final IndicatorLines indicatorLines = new IndicatorLines(codeIndicators);

                elementPresenter.setCodeIndicators(indicatorLines);
            } else {
                elementPresenter.clearAllIndicators();
            }
        } else {
            elementPresenter.clearAllIndicators();
        }
    }

    private void updateToggleConsoleBtn(final String elementId) {
        final SharedStepData stepData = getEffectiveStepData();

        // Only update the code indicators if we have a current result.
        if (stepData != null) {
            final SharedElementData elementData = stepData.getElementData(elementId);
            if (elementData != null) {
                final Indicators codeIndicators = elementData.getCodeIndicators();
                final Indicators outputIndicators = elementData.getOutputIndicators();
                final Indicators combinedIndicators = Indicators.combine(codeIndicators, outputIndicators);

                final IndicatorLines indicatorLines = new IndicatorLines(combinedIndicators);
//                GWT.log(elementId + " - Output indicators: " + outputIndicators);
//                GWT.log(elementId + " - Code indicators: " + codeIndicators);
//                GWT.log(elementId + " - Combined indicatorLines: " + indicatorLines);
                updateToggleConsoleBtnVisibility(indicatorLines, elementId);
            } else {
                updateToggleConsoleBtnVisibility(null, elementId);
            }
        } else {
            updateToggleConsoleBtnVisibility(null, elementId);
        }
    }

    private void updateToggleConsoleBtnVisibility(final IndicatorLines indicatorLines, final String elementId) {
        final Severity maxSeverity = GwtNullSafe.get(indicatorLines, IndicatorLines::getMaxSeverity);
        boolean isButtonVisible = maxSeverity != null;

        if (elementId != null) {
            currentElementPresenter = elementPresenterMap.get(elementId);
        }
        boolean isLogPaneVisible = isButtonVisible
                && currentElementPresenter != null
                && currentElementPresenter.getDesiredLogPanVisibility();

        setLogPaneVisibility(isLogPaneVisible);

        if (maxSeverity != null) {
            final int count = indicatorLines.getCount(maxSeverity);
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

    private void refreshEditorIO(final ElementPresenter elementPresenter, final String elementId) {

        final SharedStepData stepData = getEffectiveStepData();

        if (stepData != null) {
            final SharedElementData elementData = stepData.getElementData(elementId);
            if (elementData != null) {
                final Indicators outputIndicators = elementData.getOutputIndicators();
                final String input = notNull(elementData.getInput());
                final String output = notNull(elementData.getOutput());

                elementPresenter.setInput(input, 1, elementData.isFormatInput(), null);

                elementPresenter.setOutput(
                        output,
                        1,
                        elementData.isFormatOutput(),
                        new IndicatorLines(outputIndicators));
            } else {
                elementPresenter.clearAllIndicators();
            }
        } else {
//            GWT.log("currentResult is null, not updating");
            elementPresenter.clearAllIndicators();
        }
    }

    public void read(final DocRef pipeline,
                     final StepType stepType,
                     final StepLocation stepLocation,
                     final Meta meta,
                     final String childStreamType) {
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
        request.setPipeline(pipeline);

        // Set the stream id on the stepping action.
        final FindMetaCriteria findMetaCriteria = FindMetaCriteria.createFromMeta(meta);
        request.setCriteria(findMetaCriteria);
        request.setChildStreamType(childStreamType);

        // Load the pipeline.
        final Rest<List<PipelineData>> rest = restFactory.create();
        rest
                .onSuccess(result -> {
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
                .call(PIPELINE_RESOURCE)
                .fetchPipelineData(pipeline);
    }

    public void save() {
        // Tell all editors to save.
        for (final Entry<String, ElementPresenter> entry : elementPresenterMap.entrySet()) {
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
                request.reset();
            }

            // Is the event telling us to jump to a specific location?
            if (stepLocation != null) {
                request.setStepLocation(stepLocation);
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
            request.setCode(codeMap);

            request.setStepType(stepType);

            final Rest<SteppingResult> rest = restFactory.create();
            rest
                    .onSuccess(this::readResult)
                    .onFailure(caught -> {
                        AlertEvent.fireErrorFromException(SteppingPresenter.this, caught, null);
                        busyTranslating = false;
                    })
                    .call(STEPPING_RESOURCE)
                    .step(request);
        }
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
                        && Objects.equals(currentResult.getStepLocation(), lastFoundResult.getStepLocation())
                        && GwtNullSafe.isEmptyCollection(currentResult.getGeneralErrors())
                        && !GwtNullSafe.test(currentResult.getStepData(), SharedStepData::hasIndicators)) {
                    // Same location as last time and no errors/indicators in the curr result, so just display
                    // the last one.
                    steppingResult = lastFoundResult;
                } else {
                    steppingResult = currentResult;
                }
            }
        }
        return GwtNullSafe.get(steppingResult, SteppingResult::getStepData);
    }

    private void updateElementSeverities() {
        // If we have not found a record then we have hit the end or run out of filter matches
        // so are still on the last result, thus show the messages based on what it was before.
        final SharedStepData stepData = getEffectiveStepData();

        if (stepData != null && stepData.getElementMap() != null) {
            final Map<String, Severity> elementIdToSeveritiesMap = stepData.getElementMap()
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Entry::getKey,
                            entry -> {
                                final SharedElementData elementData = entry.getValue();
                                return elementData == null
                                        ? null
                                        : elementData.getAllIndicators().getMaxSeverity();
                            }));
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
            for (final Entry<String, ElementPresenter> entry : elementPresenterMap.entrySet()) {
                entry.getValue().setRefreshRequired(true);
            }

            // Refresh the currently selected editor.
            final PipelineElement selectedElement = getSelectedPipeElement();
            if (selectedElement != null) {
                final String elementId = selectedElement.getId();
                final ElementPresenter elementPresenter = elementPresenterMap.get(elementId);
                if (elementPresenter != null) {
                    refreshEditor(elementPresenter, elementId);
                }
                updateToggleConsoleBtn(elementId);
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
                request.setStepLocation(result.getStepLocation());
                stepLocationPresenter.setStepLocation(result.getStepLocation());
            }

            // Sync step filters.
            request.setStepFilterMap(result.getStepFilterMap());

            if (result.getGeneralErrors() != null && result.getGeneralErrors().size() > 0) {
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
                    request.getStepType(),
                    showingData,
                    foundRecord,
                    fatalErrors.isPresent(),
                    result.hasActiveFilter(),
                    result.getStepLocation());
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
                        final Set<StoredError> errors = new HashSet<>();
                        if (sharedElementData.getCodeIndicators() != null
                                && sharedElementData.getCodeIndicators().getUniqueErrorSet() != null) {
                            errors.addAll(sharedElementData.getCodeIndicators().getUniqueErrorSet());
                        }
                        if (sharedElementData.getOutputIndicators() != null
                                && sharedElementData.getOutputIndicators().getUniqueErrorSet() != null) {
                            errors.addAll(sharedElementData.getOutputIndicators().getUniqueErrorSet());
                        }
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

    /**
     * Ensures we don't set a null string into a field by returning an empty
     * string instead of null.
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

                    updateElementSeverities();
                }

                TaskEndEvent.fire(SteppingPresenter.this);
            });
        }
    }

    private void setLogPaneVisibility(final boolean isVisible) {
        final PipelineElement selectedElement = getSelectedPipeElement();
        if (selectedElement != null) {
            final String elementId = selectedElement.getId();
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
