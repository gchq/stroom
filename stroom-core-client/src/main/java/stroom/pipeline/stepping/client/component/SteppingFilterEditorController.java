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

package stroom.pipeline.stepping.client.component;

import stroom.pipeline.shared.XPathFilter;
import stroom.pipeline.shared.XPathFilter.SearchType;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.stepping.SteppingFilterSettings;
import stroom.pipeline.structure.client.presenter.PipelineElementTypesFactory;
import stroom.svg.shared.SvgImage;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/// The step filters of a pipeline's elements ([SteppingFilterEditor]), in a dialog its owner keeps
/// and shows again. Choosing an element keeps the filters of the one chosen before; OK gives all
/// of them to the owner.
public class SteppingFilterEditorController extends Controller {

    private final SteppingFilterEditor view;
    private final XPathList xPathList;
    private final Provider<XPathFilterEditorController> xPathFilterEditorProvider;
    private final ButtonView editXPath;
    private final ButtonView removeXPath;
    private List<XPathFilter> xPathFilters;

    // elementId => SteppingFilterSettings
    private Map<String, SteppingFilterSettings> settingsMap;
    private final Map<String, Boolean> elementIdToHasActiveFilterMap = new HashMap<>();
    private String currentElementId;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                    The application's event bus.
    /// @param view                        The content.
    /// @param xPathFilterEditorProvider   Makes the dialog that adds or edits an XPath filter.
    /// @param pipelineElementTypesFactory Gives the element types, for the elements' icons.
    @Inject
    public SteppingFilterEditorController(final EventBus eventBus,
                                          final SteppingFilterEditor view,
                                          final Provider<XPathFilterEditorController> xPathFilterEditorProvider,
                                          final PipelineElementTypesFactory pipelineElementTypesFactory) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.xPathList = view.getXPathList();
        this.xPathFilterEditorProvider = xPathFilterEditorProvider;

        xPathList.getToolbar().setName("XPath actions");
        xPathList.getToolbar().button(SvgImage.ADD, "New XPath Filter", this::addXPathFilter);
        editXPath = xPathList.getToolbar().button(SvgImage.EDIT, "Edit Selected XPath Filter",
                this::editXPathFilter);
        editXPath.setEnabled(false);
        removeXPath = xPathList.getToolbar().button(SvgImage.REMOVE, "Delete Selected XPath Filters",
                this::removeXPathFilter);
        removeXPath.setEnabled(false);

        pipelineElementTypesFactory.get(taskMonitor(), guard(elementTypes ->
                view.setElementTypes(elementTypes, pipelineElement ->
                        NullSafe.isTrue(elementIdToHasActiveFilterMap.get(pipelineElement.getId())))));

        own(xPathList.getSelectionModel().addSelectionHandler(event -> {
            final List<XPathFilter> list = xPathList.getSelectionModel().getSelectedItems();
            editXPath.setEnabled(list.size() == 1);
            removeXPath.setEnabled(!list.isEmpty());

            if (event.getSelectionType().isDoubleSelect()) {
                if (list.size() == 1) {
                    editXPathFilter();
                }
            }
        }));
        view.setOnElementSelected(this::update);
        view.setOnChange(this::updateLocalActiveFilterState);
    }

    /// Shows the dialog.
    ///
    /// @param elements        The pipeline's elements.
    /// @param selectedElement The element to show the filters of first.
    /// @param settingsMap     Each element's filters, by element ID.
    /// @param consumer        Given each element's filters, by element ID, when OK is pressed.
    public void show(final List<PipelineElement> elements,
                     final PipelineElement selectedElement,
                     final Map<String, SteppingFilterSettings> settingsMap,
                     final Consumer<Map<String, SteppingFilterSettings>> consumer) {
        currentElementId = null;
        if (settingsMap != null) {
            this.settingsMap = new HashMap<>(settingsMap);
            // Initialise our local view of elements with active filters based on persisted state
            settingsMap.forEach((elementId, filterSettings) ->
                    elementIdToHasActiveFilterMap.put(
                            elementId,
                            NullSafe.test(filterSettings, SteppingFilterSettings::hasActiveFilters)));
        } else {
            this.settingsMap = new HashMap<>();
        }
        view.setElements(elements);

        if (!elements.isEmpty()) {
            PipelineElement element = null;
            int selectedRow = 0;
            for (int i = 0; i < elements.size(); i++) {
                final PipelineElement elm = elements.get(i);
                if (Objects.equals(elm, selectedElement)) {
                    element = elm;
                    selectedRow = i;
                }
            }
            if (element == null) {
                element = elements.get(0);
            }
            view.selectElement(element, selectedRow);
            update(element);
        }

        ControllerDialog.show(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(850, 550, 550, 400))
                .caption("Change Step Filters")
                .onShow(e -> view.focusElements())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        update(null);
                        consumer.accept(this.settingsMap);
                    }
                    e.hide();
                })
                .fire();
    }

    private void addXPathFilter() {
        final XPathFilter xPathFilter = new XPathFilter();
        xPathFilter.setSearchType(SearchType.ALL);
        xPathFilter.setMatchType(XPathFilter.DEFAULT_MATCH_TYPE);

        xPathFilterEditorProvider.get().edit(this, "Add XPath Filter", xPathFilter, () -> {
            xPathFilters.add(xPathFilter);
            xPathList.getSelectionModel().setSelected(xPathFilter);
            updateLocalActiveFilterState();
        });
    }

    private void editXPathFilter() {
        final List<XPathFilter> list = xPathList.getSelectionModel().getSelectedItems();
        if (list.size() == 1) {
            xPathFilterEditorProvider.get().edit(this, "Edit XPath Filter", list.get(0),
                    xPathList::refresh);
        }
    }

    private void removeXPathFilter() {
        // If there is a selected filter remove it from the set of filters and
        // the display.
        final List<XPathFilter> list = xPathList.getSelectionModel().getSelectedItems();
        if (!list.isEmpty()) {
            xPathFilters.removeAll(list);
            xPathList.refresh();
            xPathList.getSelectionModel().clear();
            updateLocalActiveFilterState();
        }
    }

    private void updateLocalActiveFilterState() {
        if (currentElementId != null) {
            final boolean hasActiveFilters = view.getSkipToErrors() != null
                                             || view.getSkipToOutput() != null
                                             || xPathList.getDataProvider().getList()
                                                     .stream()
                                                     .anyMatch(Objects::nonNull);
            elementIdToHasActiveFilterMap.put(currentElementId, hasActiveFilters);
            view.redrawElements();
        }
    }

    private void update(final PipelineElement element) {
        if (currentElementId != null) {
            final SteppingFilterSettings settings = new SteppingFilterSettings(
                    view.getSkipToErrors(),
                    view.getSkipToOutput(),
                    new ArrayList<>(xPathFilters));
            settingsMap.put(currentElementId, settings);
        }

        final String elementId = NullSafe.get(element, PipelineElement::getId);
        if (elementId != null && !elementId.equals(currentElementId)) {
            view.setName(element.getDisplayName());
            xPathFilters = xPathList.getDataProvider().getList();
            xPathFilters.clear();

            final SteppingFilterSettings settings = settingsMap.get(element.getId());
            if (settings != null) {
                view.setSkipToErrors(NullSafe.get(settings, SteppingFilterSettings::getSkipToSeverity));
                view.setSkipToOutput(NullSafe.get(settings, SteppingFilterSettings::getSkipToOutput));
                if (NullSafe.hasItems(settings.getFilters())) {
                    xPathFilters.addAll(settings.getFilters());
                }
            }
        }
        currentElementId = elementId;
    }

    private TaskMonitorFactory taskMonitor() {
        final TaskMonitorFactory provided = getProvidedTaskMonitor();
        return provided != null ? provided : new DefaultTaskMonitorFactory(this);
    }
}
