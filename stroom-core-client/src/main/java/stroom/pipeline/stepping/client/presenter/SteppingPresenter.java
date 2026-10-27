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

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.data.client.component.ExpressionDialogController;
import stroom.data.client.component.SteppingMetaListController;
import stroom.data.client.presenter.ExpressionValidator;
import stroom.data.client.presenter.SourcePresenter;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.PipelineModelException;
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
import stroom.pipeline.stepping.client.component.PipelinePathBar;
import stroom.pipeline.stepping.client.component.PipelinePathBarController;
import stroom.pipeline.stepping.client.component.StepControlController;
import stroom.pipeline.stepping.client.component.StepLocationLinkController;
import stroom.pipeline.stepping.client.component.SteppingElementController;
import stroom.pipeline.stepping.client.component.SteppingFilterEditorController;
import stroom.pipeline.structure.client.presenter.DefaultPipelineTreeBuilder;
import stroom.pipeline.structure.client.presenter.PipelineElementTypesFactory;
import stroom.pipeline.structure.client.presenter.PipelineModel;
import stroom.query.api.ExpressionOperator;
import stroom.svg.shared.SvgImage;
import stroom.task.client.SimpleTask;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.util.shared.DataRange;
import stroom.util.shared.ElementId;
import stroom.util.shared.Indicators;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.button.client.ToolbarToggleButton;
import stroom.widget.component.client.ControllerLayer;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.presenter.client.Layer;
import stroom.widget.presenter.client.LayerContainer;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;
import stroom.widget.util.client.SelectionType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

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
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SteppingPresenter
        extends MyPresenterWidget<SteppingPresenter.SteppingView> {

    private static final SteppingResource STEPPING_RESOURCE = GWT.create(SteppingResource.class);
    private static final String LOG_PANE_TITLE = "Log Pane";

    private final PipelineStepRequest.Builder requestBuilder = PipelineStepRequest.builder();
    private final PipelinePathBar pipelinePathBar;
    private final SourcePresenter sourcePresenter;
    private final Provider<SteppingElementController> elementProvider;
    private final SimplePanel stepMessage;
    // Tells screen reader users that a step has begun and where it got to (the progress message
    // above changes too often, while searching, to be announced)
    private final Element stepAnnouncement;
    private final StepLocationLinkController stepLocationLink;
    private final StepControlController stepControl;
    private final SteppingFilterEditorController steppingFilterEditor;
    private final PipelineElementTypesFactory pipelineElementTypesFactory;
    private final RestFactory restFactory;
    private final SteppingMetaListController steppingMetaList;
    private final Provider<ExpressionDialogController> filterDialogProvider;
    private final ExpressionValidator expressionValidator;
    // elementId => SteppingElementController
    private final Map<ElementId, SteppingElementController> elementMap = new HashMap<>();
    // elementId => the layer showing its controller
    private final Map<ElementId, ControllerLayer> elementLayerMap = new HashMap<>();
    private PipelineModel pipelineModel;
    private final ToolbarButton terminateButton;
    private final ToolbarToggleButton toggleLogPaneButton;
    private boolean foundRecord;
    private boolean busyTranslating;
    // Whether the in-flight step is answering an applied step filter. A filtered step is a scan - the
    // server materialises and tests records until one matches - so the progress message says "Searching"
    // rather than "Stepping" to set the expectation of a wait proportional to where the next match is.
    private boolean searching;
    private SteppingResult lastFoundResult;
    private SteppingResult currentResult;
    private final Toolbar logPaneButtons;
    private boolean runStepping = true;

    final ButtonView streamListFilter;

    private Meta meta;
    private String childStreamType;
    private PipelineDoc pipelineDoc;
    private String classification;
    private SteppingElementController currentElement = null;

    private Runnable onChange = () -> {
    };

    @Inject
    public SteppingPresenter(final EventBus eventBus,
                             final SteppingView view,
                             final PipelinePathBar pipelinePathBar,
                             final Provider<PipelinePathBarController> pathBarControllerProvider,
                             final RestFactory restFactory,
                             final SourcePresenter sourcePresenter,
                             final Provider<SteppingElementController> elementProvider,
                             final StepLocationLinkController stepLocationLink,
                             final StepControlController stepControl,
                             final SteppingFilterEditorController steppingFilterEditor,
                             final PipelineElementTypesFactory pipelineElementTypesFactory,
                             final Provider<SteppingMetaListController> metaListPresenterProvider,
                             final Provider<ExpressionDialogController> filterDialogProvider,
                             final ExpressionValidator expressionValidator) {
        super(eventBus, view);
        own(sourcePresenter);
        own(stepLocationLink);
        own(stepControl);
        own(steppingFilterEditor);
        this.restFactory = restFactory;

        this.pipelinePathBar = pipelinePathBar;
        make(pathBarControllerProvider).attach(pipelinePathBar);
        this.sourcePresenter = sourcePresenter;
        this.elementProvider = elementProvider;
        this.stepLocationLink = stepLocationLink;
        this.stepControl = stepControl;
        this.steppingFilterEditor = steppingFilterEditor;
        this.pipelineElementTypesFactory = pipelineElementTypesFactory;
        this.steppingMetaList = make(metaListPresenterProvider);
        this.filterDialogProvider = filterDialogProvider;
        this.expressionValidator = expressionValidator;

        final Toolbar buttonPanel = new Toolbar("Stepping");
        terminateButton = buttonPanel.button(SvgImage.STOP, "Terminate Stepping", this::terminate);
        terminateButton.setEnabled(false);
        terminateButton.addStyleName("query-buttons__button query-buttons__button--stop");

        view.setTreeView(pipelinePathBar);

        sourcePresenter.getWidget().addStyleName("dashboard-panel overflow-hidden");
        sourcePresenter.getWidget().addStyleName("dashboard-panel overflow-hidden");
        sourcePresenter.setSteppingSource(true);

        pipelinePathBar.setPipelineTreeBuilder(new DefaultPipelineTreeBuilder());
        pipelinePathBar.setAllowNullSelection(false);

        // Nothing can be stepped until a stream has been chosen (see beginStepping).
        stepControl.initButtons();
        stepLocationLink.setEnabled(false);

        logPaneButtons = new Toolbar("Log pane");
        toggleLogPaneButton = logPaneButtons.toggle(SvgImage.EXCLAMATION, LOG_PANE_TITLE, this::onToggleLogPane);
        toggleLogPaneButton.setOn();

        sourcePresenter.setOnClassification(this::setClassification);

        this.stepMessage = new SimplePanel();
        stepMessage.addStyleName("stepping-screen__progress-message");

        // The log pane toggle and the step's progress, in a slot of a fixed width before the step
        // location and controls, so that the pipeline's bar beside them doesn't change width as they
        // come and go
        final FlowPanel stepToolbar = new FlowPanel();
        stepToolbar.addStyleName("stepping-screen__progress dock-container-horizontal dock-min");
        stepToolbar.add(logPaneButtons);
        stepToolbar.add(stepMessage);
        stepAnnouncement = Document.get().createDivElement();
        stepAnnouncement.setClassName("sr-only");
        stepAnnouncement.setAttribute("role", "status");
        stepAnnouncement.setAttribute("aria-live", "polite");
        stepToolbar.getElement().appendChild(stepAnnouncement);
        view.addWidgetRight(stepToolbar);
        view.addWidgetRight(stepLocationLink.asWidget());
        view.addWidgetRight(stepControl.asWidget());
        view.addWidgetRight(buttonPanel);

        steppingMetaList.getToolbar().setName("Stream actions");
        streamListFilter = steppingMetaList.getToolbar().button(SvgImage.FILTER, "Filter Streams",
                this::onStreamListFilter);

        sourcePresenter.getView().setMetaListContainerView(steppingMetaList.asWidget());

        own(
                pipelinePathBar.getSelectionModel().addSelectionChangeHandler(event -> {
                    final PipelineElement selectedElement = getSelectedPipeElement();
                    onSelect(selectedElement);
                }));
        stepLocationLink.setOnStepLocation(stepLocation -> {
            step(StepType.REFRESH, stepLocation);
            final Meta meta = Meta.builder().id(stepLocation.getMetaId()).build();
            steppingMetaList.getSelectionModel().setSelected(new MetaRow(meta, null, null));
        });
        // The step controls step from the current location
        stepControl.setOnStep(stepType -> step(stepType, null));
        stepControl.setOnFilter(this::showChangeFiltersDialog);

        own(steppingMetaList.getSelectionModel().addSelectionHandler(event -> {
            if (runStepping) {
                final MetaRow selectedRow = steppingMetaList.getSelected();
                if (selectedRow != null) {

                    final Meta selectedMeta = selectedRow.getMeta();
                    beginStepping(StepType.REFRESH, new StepLocation(selectedMeta.getId(), 0L, 0L),
                            selectedMeta, null);
                }
            } else {
                runStepping = true;
            }
        }));

        steppingMetaList.setOnResult(resultPage -> {
            if (resultPage.size() == 1) {
                final MetaRow metaRow = resultPage.getFirst();
                steppingMetaList.getSelectionModel().setSelected(metaRow, new SelectionType(), false);
                stepLocationLink.setStepLocation(new StepLocation(metaRow.getMeta().getId(), 0L, 0L));
                // Begin stepping if not already stepping on this stream. This handles the case where the user
                // changes the filter expression and a single result is auto-selected
                if (!busyTranslating && (meta == null || meta.getId() != metaRow.getMeta().getId())) {
                    beginStepping(StepType.REFRESH,
                            new StepLocation(metaRow.getMeta().getId(), 0L, 0L),
                            metaRow.getMeta(),
                            null);
                }
            }
            // Update button state in case the meta list loaded after the last step result arrived
            updateStepControlButtons();
        });

        own(pipelinePathBar.addContextMenuHandler(event -> {
            final PipelineElement selectedPipeElement = getSelectedPipeElement();
            if (!PipelineModel.SOURCE_ELEMENT.getId().equals(selectedPipeElement.getId())) {
                final List<Item> menuItems = buildContextMenu();
                if (NullSafe.hasItems(menuItems)) {
                    showMenu("Step filters for element '" + selectedPipeElement.getDisplayName() + "'",
                            menuItems,
                            event.getPopupPosition());
                }
            }
        }));

//        steppingMetaList.setExpression(ExpressionValidator.ALL_UNLOCKED_EXPRESSION, () -> {});
    }

    private void setClassification(final String classification) {
        this.classification = classification;
        for (final SteppingElementController steppingElement : elementMap.values()) {
            steppingElement.setClassification(classification);
        }
    }

    private void onToggleLogPane(final boolean visible) {
        final SteppingElementController steppingElement = getCurrentElement();
        if (steppingElement != null) {
            steppingElement.setDesiredLogPanVisibility(visible);
            steppingElement.setLogPaneVisibility(visible);
        }
    }

    private void onStreamListFilter() {
        filterDialogProvider.get().show(this,
                "Filter Streams",
                PopupSize.resizable(1_000, 600),
                getCriteria().getExpression(),
                MetaFields.STREAM_STORE_DOC_REF,
                MetaFields.getAllFields(),
                (expression, e) -> {
                    expressionValidator.validateExpression(
                            SteppingPresenter.this,
                            MetaFields.getAllFields(),
                            expression, expression2 -> {
                                if (!expression2.equals(getCriteria().getExpression())) {
                                    if (MetaExpressionUtil.hasAdvancedCriteria(expression2)) {
                                        ConfirmEvent.fire(SteppingPresenter.this,
                                                "You are setting advanced filters!  It is recommended you constrain " +
                                                "your filter (e.g. by 'Created') to avoid an expensive query.  "
                                                + "Are you sure you want to apply this advanced filter?",
                                                confirm -> {
                                                    if (confirm) {
                                                        setExpression(expression2);
                                                        e.hide();
                                                    } else {
                                                        // Don't hide
                                                        e.reset();
                                                    }
                                                });
                                    } else {
                                        setExpression(expression2);
                                        e.hide();
                                    }
                                } else {
                                    // Nothing changed!
                                    e.hide();
                                }
                            },
                            // If it isn't valid, let the user change it or cancel
                            e::reset,
                            this);
                });
    }

    public void setExpression(final ExpressionOperator expression) {
        // Copy new filter settings back.
        getCriteria().setExpression(expression);
        // Reset the page offset.
        getCriteria().obtainPageRequest().setOffset(0);

        // Clear the current selection and get a new list of streams.
        steppingMetaList.getSelectionModel().clear();
        refreshMetaList();
    }

    public void setMetaListExpression(final ExpressionOperator expressionOperator, final Runnable afterSet) {
        steppingMetaList.setExpression(expressionOperator, () -> {
            refreshMetaList();
            NullSafe.run(afterSet);
        });
    }

    public ResultPage<MetaRow> getMetaList() {
        return steppingMetaList.getResultPage();
    }

    private FindMetaCriteria getCriteria() {
        return steppingMetaList.getCriteria();
    }

    private SteppingElementController getCurrentElement() {
        return currentElement;
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
            final PipelineElement selectedObject = pipelinePathBar.getSelectionModel()
                    .getSelectedObject();
            steppingFilterEditor.show(
                    elements,
                    getSelectedPipeElement(),
                    pipelineModel.getStepFilterMap(),
                    stepFilterMap -> {
                        pipelineModel.setStepFilterMap(stepFilterMap);
                        // Need to refresh the view in case any elements need to reflect active filters
                        pipelinePathBar.redraw();
                        pipelinePathBar.getSelectionModel().setSelected(selectedObject, true);
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
        pipelinePathBar.redraw();
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
        pipelinePathBar.redraw();
    }

    private void showMenu(final String name,
                          final List<Item> menuItems,
                          final PopupPosition popupPosition) {
        ShowMenuEvent
                .builder()
                .name(name)
                .items(menuItems)
                .popupPosition(popupPosition)
                .fire(this);
    }

    private PipelineElement getSelectedPipeElement() {
        return pipelinePathBar.getSelectionModel().getSelectedObject();
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

    private Layer getContent(final PipelineElement element) {
        if (PipelineModel.SOURCE_ELEMENT.getType().equals(element.getType())) {
            setCurrentElement(null);
            updateToggleConsoleBtn(null);
            return sourcePresenter;
        } else {
            final ElementId elementId = element.getElementId();
            SteppingElementController steppingElement = elementMap.get(elementId);
            if (steppingElement == null) {
                // Editing element code (e.g. XSLT) calls the element's change callback ("something
                // changed"), which we relay up as our own so the enclosing pipeline presenter re-evaluates Save
                // via onChange(). The authoritative dirty verdict is recomputed there by comparison, so a
                // reverted edit correctly returns to clean. It must NOT rebuild the pipeline model/tree -
                // the code content is not part of the pipeline structure, and doing so on every keypress
                // caused the tree to flash and the UI to lag on large documents.
                final Runnable changeEditorHandler = () -> onChange.run();

                final List<PipelineProperty> properties = pipelineModel.getProperties(element);

                final SteppingElementController newElement = make(elementProvider);
                newElement.setPipelineModel(pipelineModel);
                newElement.setElement(element);
                newElement.setProperties(properties);
                newElement.setFeedName(meta == null
                        ? ""
                        : meta.getFeedName());
                newElement.setPipelineName(pipelineDoc.getName());
                newElement.setClassification(classification);
                elementMap.put(elementId, newElement);
                elementLayerMap.put(elementId, new ControllerLayer(newElement));
                newElement.setOnChange(changeEditorHandler);

                // Allow step refresh to be called from the editor
                newElement.setStepRequestHandler(stepType -> {
                    if (stepControl.isEnabled(stepType)) {
                        stepControl.step(stepType);
                    }
                });

                steppingElement = newElement;
            }
            setCurrentElement(steppingElement);

            // Refresh this editor if it needs it.
            refreshEditor(steppingElement, elementId);

            return elementLayerMap.get(elementId);
        }
    }

    // Shows an element's panes in place of the current one's, releasing the current one if it was
    // dropped while it was shown
    private void setCurrentElement(final SteppingElementController steppingElement) {
        if (currentElement != null
            && currentElement != steppingElement
            && !elementMap.containsValue(currentElement)) {
            release(currentElement);
        }
        currentElement = steppingElement;
    }

    private void refreshEditor(final SteppingElementController steppingElement,
                               final ElementId elementId) {
        steppingElement.load(result -> {
            final SharedStepData stepData = getEffectiveStepData();
            if (stepData != null) {
                final SharedElementData elementData = stepData.getElementData(elementId.getId());
                if (elementData != null) {
                    final Indicators indicators = displayIndicators(elementId, elementData);

                    // Update the error indicators for all panes
                    steppingElement.setIndicators(indicators);

                    // Update IO data.
                    refreshEditorIO(steppingElement, elementData);

                    // Update with any errors not specific to an editor pane
//                            refreshGenericErrors(steppingElement, elementId);

                    updateToggleConsoleBtnVisibility(indicators, elementId);
                } else {
                    clearIndicators(steppingElement, elementId);
                }
            } else {
                clearIndicators(steppingElement, elementId);
            }
        });
    }

    /**
     * The indicators to display for an element's panes: the ones its run raised, plus a note when the
     * served record's counts are indicative - the record was produced on its own, so counting elements
     * never saw the records before it (see {@link SharedElementData#isIndicativeCounts()}). The note has no
     * location, so it appears in the element's console rather than as an editor gutter marker.
     */
    private Indicators displayIndicators(final ElementId elementId, final SharedElementData elementData) {
        final Indicators indicators = elementData.getIndicators();
        if (!elementData.isIndicativeCounts()) {
            return indicators;
        }
        final Indicators combined = new Indicators();
        if (indicators != null) {
            combined.addAll(indicators);
        }
        combined.add(new StoredError(Severity.INFO, null, elementId,
                "Counts are indicative: this record was produced on its own, so counting elements "
                + "(e.g. the EventId added by IdEnrichmentFilter) did not see the records before it. "
                + "Stepping from the start of the stream gives exact counts."));
        return combined;
    }

    private void clearIndicators(final SteppingElementController steppingElement,
                                 final ElementId elementId) {
        steppingElement.clearAllIndicators();
        updateToggleConsoleBtnVisibility(null, elementId);
    }

    private void updateToggleConsoleBtn(final ElementId elementId) {
        final SharedStepData stepData = getEffectiveStepData();

        // Only update the code indicators if we have a current result.
        if (stepData != null) {
            final SharedElementData elementData = NullSafe.get(elementId, ElementId::getId, stepData::getElementData);
            if (elementData != null) {
                final Indicators indicators = displayIndicators(elementId, elementData);
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

        final SteppingElementController steppingElement = NullSafe.get(elementId, elementMap::get);
        final boolean isLogPaneVisible = isButtonVisible
                                         && steppingElement != null
                                         && steppingElement.getDesiredLogPanVisibility();

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
            final String msg = LOG_PANE_TITLE + " ("
                               + count + " " + type + plural + ")";
            toggleLogPaneButton.setTitle(msg);
        }

        toggleLogPaneButton.setVisible(isButtonVisible);
        // Now set the button state based on the desired state from the presenter
        if (isButtonVisible) {
            toggleLogPaneButton.setState(isLogPaneVisible);
        }
    }

    private void refreshEditorIO(final SteppingElementController steppingElement, final SharedElementData elementData) {

        final String input = NullSafe.string(elementData.getInput());
        final String output = NullSafe.string(elementData.getOutput());

        steppingElement.setInput(
                input,
                1,
                elementData.isFormatInput());

        steppingElement.setOutput(
                output,
                1,
                elementData.isFormatOutput());
    }

    public void setPipelineDoc(final PipelineDoc pipelineDoc) {
        this.pipelineDoc = pipelineDoc;
    }

    public PipelineDoc getPipelineDoc() {
        return pipelineDoc;
    }

    public void refreshMetaList() {
        steppingMetaList.refresh();
    }

    public void beginStepping() {
        if (this.meta == null) {
            if (steppingMetaList.getSelected() == null) {
                sourcePresenter.clear();

                if (steppingMetaList.getResultPage() == null) {
                    refreshMetaList();
                }

                return;
            }
            this.meta = steppingMetaList.getSelected().getMeta();
        }

        beginStepping(StepType.REFRESH,
                Objects.requireNonNullElse(requestBuilder.build().getStepLocation(),
                        new StepLocation(meta.getId(), 0L, 0L)),
                meta, childStreamType);
    }

    public void beginStepping(final StepType stepType,
                              final StepLocation stepLocation,
                              final Meta meta,
                              final String childStreamType) {
        Objects.requireNonNull(meta);
        Objects.requireNonNull(stepLocation);

        this.meta = meta;
        this.childStreamType = childStreamType;

        // Load the stream.
        final SourceLocation sourceLocation = SourceLocation.builder(this.meta.getId())
                .withChildStreamType(childStreamType)
                .withPartIndex(stepLocation.getPartIndex())
                .withRecordIndex(Math.max(stepLocation.getRecordIndex(), 0))
                .build();
        sourcePresenter.setSourceLocation(sourceLocation);

        // Set the pipeline on the stepping action.
        requestBuilder.pipelineDoc(pipelineDoc);

        // Set the stream id on the stepping action.
        final FindMetaCriteria findMetaCriteria = FindMetaCriteria.createFromMeta(this.meta);
        findMetaCriteria.setSortList(steppingMetaList.getCriteria().getSortList());
        findMetaCriteria.setExpression(steppingMetaList.getCriteria().getExpression());
        requestBuilder.criteria(findMetaCriteria);
        // A step request needs the criteria, so a refresh or new location is only possible from now.
        stepControl.setRefreshEnabled(true);
        stepLocationLink.setEnabled(true);

        requestBuilder.childStreamType(childStreamType);

        // A stepping session is scoped to the stream selection it was created with, so this starts a new
        // one. Dropping the id also lets the server release the old session's captured data rather than
        // holding it until the idle reap.
        terminate();
        requestBuilder.sessionUuid(null);

        if (stepType != null) {
            step(stepType, new StepLocation(
                    meta.getId(),
                    stepLocation.getPartIndex(),
                    stepLocation.getRecordIndex()));
        }
    }

    public void setPipelineModel(final PipelineModel model) {
        try {
            final PipelineElement selectedElement = pipelinePathBar.getSelectionModel().getSelectedObject();

            pipelineModel = model;
            pipelinePathBar.setModel(pipelineModel);

            // Remove elements from the elementMap if they no longer exist or the doc ref has changed
            // stops dirty elements being used in stepping when they are out of date
            final List<ElementId> elementsToRemove = elementMap.entrySet()
                    .stream()
                    .filter(entry -> {
                        final SteppingElementController steppingElement = entry.getValue();
                        if (!pipelineModel.hasElement(steppingElement.getElement())) {
                            return true;
                        }

                        final List<PipelineProperty> newProperties = pipelineModel
                                .getProperties(steppingElement.getElement())
                                .stream()
                                .filter(p -> p.getValue().getEntity() != null)
                                .collect(Collectors.toList());

                        final List<PipelineProperty> currentProperties = steppingElement.getProperties()
                                .stream()
                                .filter(p -> p.getValue().getEntity() != null)
                                .collect(Collectors.toList());

                        return !compareProperties(currentProperties, newProperties);
                    })
                    .map(Entry::getKey)
                    .collect(Collectors.toList());

            elementsToRemove.forEach(elementId -> {
                final SteppingElementController removed = elementMap.remove(elementId);
                elementLayerMap.remove(elementId);
                // The one shown is released once another is shown in its place
                if (removed != currentElement) {
                    release(removed);
                }
            });

            if (selectedElement == null || !pipelineModel.hasElement(selectedElement)) {
                pipelinePathBar.getSelectionModel().setSelected(PipelineModel.SOURCE_ELEMENT, true);
            } else {
                pipelinePathBar.getSelectionModel().setSelected(selectedElement, true);
            }

            final List<PipelineElement> disabledElements = pipelineModel.getPipelineElements(e ->
                    !PipelineModel.SOURCE_ELEMENT.equals(e) &&
                    !pipelineModel.hasRole(e, PipelineElementType.VISABILITY_STEPPING));
            pipelinePathBar.setDisabledElements(disabledElements);

        } catch (final PipelineModelException e) {
            AlertEvent.fireError(SteppingPresenter.this, e.getMessage(), null);
        }
    }

    private boolean compareProperties(final List<PipelineProperty> properties,
                                      final List<PipelineProperty> otherProperties) {
        final ArrayList<PipelineProperty> someProperties = new ArrayList<>(properties);
        for (final PipelineProperty otherProperty : otherProperties) {

            final Optional<PipelineProperty> someProperty = getProperty(someProperties, otherProperty.getName());
            if (someProperty.isPresent()) {

                if (!otherProperty.getValue().equals(someProperty.get().getValue())) {
                    return false;
                }
                someProperties.remove(otherProperty);
            } else {
                return false;
            }
        }
        return someProperties.isEmpty();
    }

    public Optional<PipelineProperty> getProperty(final List<PipelineProperty> properties, final String propertyName) {
        return properties.stream().filter(p -> p.getName().equals(propertyName)).findAny();
    }

    public void save(final List<DocRef> docsToSave, final Runnable onComplete) {
        // Tell editors to save if they are in the list.
        elementMap.values().stream()
                .filter(steppingElement -> docsToSave.contains(steppingElement.getDocRef()))
                .forEach(steppingElement -> steppingElement.save(onComplete));
    }

    public List<DocRef> getDirtyDocs() {
        return elementMap.values().stream()
                .filter(SteppingElementController::isDirty)
                .map(SteppingElementController::getDocRef)
                .collect(Collectors.toList());
    }

    private void step(final StepType stepType,
                      final StepLocation stepLocation) {
        if (!busyTranslating) {
            Objects.requireNonNull(pipelineModel);

            busyTranslating = true;
            setStepMessage("Stepping...");
            announceStep("Stepping");
            stepMessage.setVisible(true);
            terminateButton.setEnabled(true);

            // Keep the session UUID from previous steps. The server holds the stepping session - the data it
            // has already captured for this stream selection - against that id, so preserving it across
            // FIRST/FORWARD/BACKWARD/LAST/REFRESH is what lets each step be served from what was captured
            // instead of running the pipeline again. It is cleared only when the selection changes (see
            // beginStepping/setExpression); the server issues a new id if it no longer knows this one.

            final PipelineData pipelineData = pipelineModel.diff();
            pipelineDoc = pipelineDoc.copy().pipelineData(pipelineData).build();
            requestBuilder.pipelineDoc(pipelineDoc);

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
            for (final SteppingElementController steppingElement : elementMap.values()) {
                if (steppingElement.isDirty()) {
                    final String elementId = steppingElement.getElement().getId();
                    final String code = steppingElement.getCode();
                    codeMap.put(elementId, code);
                }
            }
            requestBuilder.timeout(40L);
            requestBuilder.code(codeMap);
            requestBuilder.stepType(stepType);
            final Map<String, SteppingFilterSettings> stepFilterMap = pipelineModel.getStepFilterMap();
            requestBuilder.stepFilterMap(stepFilterMap);
            searching = stepFilterMap != null && stepFilterMap.values().stream()
                    .anyMatch(settings -> settings != null && settings.isFilterApplied());

            if (StepType.REFRESH.equals(stepType)) {
                elementMap.values().stream()
                        .filter(Predicate.not(SteppingElementController::isDirty))
                        .forEach(e -> e.setLoaded(false));
            }

            poll();
        }
    }

    private void poll() {
        restFactory
                .create(STEPPING_RESOURCE)
                .method(res -> res.step(requestBuilder.build()))
                .onSuccess(response -> {
                    // Adopt the session id whether or not this step finished. A step that resolves on its
                    // first poll would otherwise leave us with no id, so the next press would start a new
                    // server session and re-capture the stream from scratch.
                    requestBuilder.sessionUuid(response.getSessionUuid());

                    if (!response.isComplete()) {
                        if (busyTranslating) {
                            final StepLocation progressLocation = response.getProgressLocation();
                            setStepMessage((searching
                                                    ? "Searching... "
                                                    : "Stepping... ")
                                           + getStepLocationText(progressLocation));
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
        if (busyTranslating) {
            announceStep("Stepping terminated");
        }
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
        setStepMessage("");
        terminateButton.setEnabled(false);
    }

    // Shows the step's progress, in full in its hover tip if its slot cuts it short
    private void setStepMessage(final String message) {
        stepMessage.getElement().setInnerText(message);
        stepMessage.setTitle(message);
    }

    // Tells screen reader users about a step, emptying the announcement first so that the same one
    // (e.g. "Stepping" for each step) is announced again
    private void announceStep(final String message) {
        stepAnnouncement.setInnerText("");
        Scheduler.get().scheduleDeferred(() -> stepAnnouncement.setInnerText(message));
    }

    // A step's location in words, e.g. "Stream 1, part 1, record 3", as "[1:1:3]" would be read as
    // punctuation
    private static String describeStepLocation(final StepLocation stepLocation) {
        return "Stream " + stepLocation.getMetaId()
               + ", part " + (stepLocation.getPartIndex() + 1)
               + ", record " + (stepLocation.getRecordIndex() + 1);
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
            pipelinePathBar.setElementSeverities(elementIdToSeveritiesMap);
        } else {
            // No data so clear them all
            pipelinePathBar.setElementSeverities(null);
        }
    }

    private void readResult(final SteppingResult result) {
        Optional<String> fatalErrors = Optional.empty();
        try {
            currentResult = result;
            foundRecord = result.isFoundRecord();
            if (foundRecord) {
                lastFoundResult = result;
            }

            updateElementSeverities();

            // Tell all editors that a refresh is required.
            for (final Entry<ElementId, SteppingElementController> entry : elementMap.entrySet()) {
                entry.getValue().setRefreshRequired(true);
            }

            // Refresh the currently selected editor.
            final PipelineElement selectedElement = getSelectedPipeElement();
            if (selectedElement != null) {
                final ElementId elementId = selectedElement.getElementId();
                final SteppingElementController steppingElement = elementMap.get(elementId);
                if (steppingElement != null) {
                    refreshEditor(steppingElement, elementId);
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
            }

            announceStep(foundRecord && result.getFoundLocation() != null
                    ? describeStepLocation(result.getFoundLocation())
                    : "No record found");

            if (result.getFoundLocation() != null) {
                // We found a record so update the display to indicate the
                // record that was found and update the request with the new
                // position ready for the next step.
                requestBuilder.stepLocation(result.getFoundLocation());
                stepLocationLink.setStepLocation(result.getFoundLocation());

                final Meta meta = Meta.builder().id(result.getFoundLocation().getMetaId()).build();
                // If the returned meta doesnt match the current selected meta then make sure the stepping doesnt run
                // again when we change the selected meta list row
                final MetaRow selectedMetaRow = steppingMetaList.getSelectionModel().getSelected();
                if (selectedMetaRow != null && !selectedMetaRow.getMeta().equals(meta)) {
                    runStepping = false;
                }
                steppingMetaList.getSelectionModel().setSelected(
                        new MetaRow(meta, null, null));
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
            updateStepControlButtons();
            busyTranslating = false;
        }
    }

    private void updateStepControlButtons() {
        if (currentResult == null || pipelineModel == null) {
            return;
        }
        if (pipelineModel.getCombinedData().getElements().size() > 1) {
            final MetaRow selectedRow = steppingMetaList.getSelected();
            final ResultPage<MetaRow> metaResultPage = steppingMetaList.getResultPage();
            final boolean isFirstStream = metaResultPage != null && metaResultPage.isFirst(selectedRow);
            final boolean isLastStream = metaResultPage != null && metaResultPage.isLast(selectedRow);
            stepControl.setEnabledButtons(
                    requestBuilder.build().getStepType(),
                    foundRecord,
                    currentResult.hasActiveFilter(),
                    currentResult.getFoundLocation(),
                    isFirstStream,
                    isLastStream);
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
                        final Layer content = getContent(element);
                        if (content != null) {
                            // Set the content.
                            getView().getLayerContainer().show(content);

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
            final SteppingElementController steppingElement = elementMap.get(elementId);
            if (steppingElement != null) {
                steppingElement.setLogPaneVisibility(isVisible);
            }
        }
    }

    /// @param onChange What to run when the user changes an element while stepping.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    // --------------------------------------------------------------------------------


    public interface SteppingView extends View {

        void addWidgetRight(Widget widget);

        void setTreeView(IsWidget view);

        LayerContainer getLayerContainer();
    }
}
