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

import stroom.data.table.client.MyCellTable;
import stroom.pipeline.shared.XPathFilter;
import stroom.pipeline.shared.XPathFilter.SearchType;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.stepping.SteppingFilterSettings;
import stroom.pipeline.stepping.client.presenter.SteppingFilterPresenter.SteppingFilterView;
import stroom.pipeline.structure.client.presenter.PipelineElementTypesFactory;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.OutputState;
import stroom.util.shared.Severity;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.BasicSelectionEventManager;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.SingleSelectionModel;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class SteppingFilterPresenter extends
        MyPresenterWidget<SteppingFilterView> {

    protected static final String BASE_CLASS = "pipelineElementChooser";
    private static final int ICON_COL_WIDTH = 22;
    private final XPathListPresenter xPathListPresenter;
    private final XPathFilterPresenter xPathFilterPresenter;
    private List<XPathFilter> xPathFilters;

    private final SingleSelectionModel<PipelineElement> elementSelectionModel = new SingleSelectionModel<>();
    private final CellTable<PipelineElement> elementChooser;
    private final ButtonView addXPath;
    private final ButtonView editXPath;
    private final ButtonView removeXPath;

    // elementId => SteppingFilterSettings
    private Map<String, SteppingFilterSettings> settingsMap;
    private final Map<String, Boolean> elementIdToHasActiveFilterMap = new HashMap<>();
    private String currentElementId;

    @Inject
    public SteppingFilterPresenter(final EventBus eventBus,
                                   final SteppingFilterView view,
                                   final XPathListPresenter xPathListPresenter,
                                   final XPathFilterPresenter xPathFilterProvider,
                                   final PipelineElementTypesFactory pipelineElementTypesFactory) {
        super(eventBus, view);
        this.xPathListPresenter = xPathListPresenter;
        this.xPathFilterPresenter = xPathFilterProvider;

        addXPath = xPathListPresenter.addButton(SvgPresets.ADD);
        addXPath.setTitle("Add XPath Filter");
        editXPath = xPathListPresenter.addButton(SvgPresets.EDIT);
        editXPath.setTitle("Edit XPath Filter");
        removeXPath = xPathListPresenter.addButton(SvgPresets.REMOVE);
        removeXPath.setTitle("Delete XPath Filter");
        editXPath.setEnabled(false);
        removeXPath.setEnabled(false);

        elementChooser = new MyCellTable<>(Integer.MAX_VALUE);
        elementChooser.getElement().addClassName(BASE_CLASS);

        pipelineElementTypesFactory.get(this, elementTypes -> {
            // Pipe element icon column
            final Column<PipelineElement, Preset> iconColumn = DataGridUtil.svgPresetColumnBuilder(false,
                            (final PipelineElement pipelineElement) -> {
                                final PipelineElementType pipelineElementType =
                                        elementTypes.getElementType(pipelineElement);
                                if (pipelineElementType != null && pipelineElementType.getIcon() != null) {
                                    return new Preset(
                                            pipelineElementType.getIcon(),
                                            pipelineElementType.getType(),
                                            true);
                                } else {
                                    return null;
                                }
                            })
                    .withStyleName(BASE_CLASS + "-iconCell svgIcon")
                    .centerAligned()
                    .build();
            elementChooser.addColumn(iconColumn);
            elementChooser.setColumnWidth(iconColumn, ICON_COL_WIDTH, Unit.PX);

            final Function<PipelineElement, String> filterActiveStyleFunc = pipelineElement ->
                    NullSafe.isTrue(elementIdToHasActiveFilterMap.get(pipelineElement.getId()))
                            ? BASE_CLASS + "-filterOn"
                            : BASE_CLASS + "-filterOff";

            // Pipe element name column
            final Column<PipelineElement, String> textColumn = DataGridUtil.textColumnBuilder(
                            PipelineElement::getDisplayName)
                    .withStyleName(BASE_CLASS + "-textCell")
                    .build();
            elementChooser.addColumn(textColumn);

            // Pipe element has active filter icon column (only visible if active filter)
            final Column<PipelineElement, Preset> filterIconColumn = DataGridUtil.svgPresetColumnBuilder(
                            false,
                            (PipelineElement pipelineElement) ->
                                    SvgPresets.FILTER.title("Has active filter(s)"))
                    .withStyleName(BASE_CLASS + "-filterIconCell svgIcon icon-colour__green")
                    .withConditionalStyleName(filterActiveStyleFunc)
                    .centerAligned()
                    .build();
            elementChooser.addColumn(filterIconColumn);
            elementChooser.setColumnWidth(filterIconColumn, ICON_COL_WIDTH, Unit.PX);

            elementChooser.setSelectionModel(elementSelectionModel, new BasicSelectionEventManager<>(elementChooser));
            elementChooser.setWidth("100%", true);
        });

        getView().setElementChooser(elementChooser);
        getView().setXPathList(xPathListPresenter.getView());
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(xPathListPresenter.getSelectionModel().addSelectionHandler(event -> {
            final List<XPathFilter> list = xPathListPresenter.getSelectionModel().getSelectedItems();
            editXPath.setEnabled(list.size() == 1);
            removeXPath.setEnabled(list.size() > 0);

            if (event.getSelectionType().isDoubleSelect()) {
                if (list.size() == 1) {
                    editXPathFilter();
                }
            }
        }));
        registerHandler(addXPath.addClickHandler(e -> addXPathFilter()));
        registerHandler(editXPath.addClickHandler(e -> editXPathFilter()));
        registerHandler(removeXPath.addClickHandler(e -> removeXPathFilter()));
        registerHandler(elementSelectionModel.addSelectionChangeHandler(e ->
                update(elementSelectionModel.getSelectedObject())));
        getView().setSkipToErrorsChangeHandler(severity ->
                updateLocalActiveFilterState());
        getView().setSkipToOutputChangeHandler(outputState ->
                updateLocalActiveFilterState());
    }

    private void addXPathFilter() {
        final XPathFilter xPathFilter = new XPathFilter();
        xPathFilter.setSearchType(SearchType.ALL);
        xPathFilter.setMatchType(XPathFilter.DEFAULT_MATCH_TYPE);

        final HidePopupRequestEvent.Handler handler = e -> {
            if (e.isOk()) {
                xPathFilterPresenter.write();
                xPathFilters.add(xPathFilter);

                xPathListPresenter.getSelectionModel().setSelected(xPathFilter);
                updateLocalActiveFilterState();
            }
            e.hide();
        };
        xPathFilterPresenter.add(xPathFilter, handler);
    }

    private void editXPathFilter() {
        final List<XPathFilter> list = xPathListPresenter.getSelectionModel().getSelectedItems();
        if (list.size() == 1) {
            final HidePopupRequestEvent.Handler handler = e -> {
                if (e.isOk()) {
                    xPathFilterPresenter.write();
                    xPathListPresenter.refresh();
                }
                e.hide();
            };
            xPathFilterPresenter.edit(list.get(0), handler);
        }
    }

    private void removeXPathFilter() {
        // If there is a selected filter remove it from the set of filters and
        // the display.
        final List<XPathFilter> list = xPathListPresenter.getSelectionModel().getSelectedItems();
        if (list.size() > 0) {
            xPathFilters.removeAll(list);
            xPathListPresenter.refresh();
            xPathListPresenter.getSelectionModel().clear();
            updateLocalActiveFilterState();
        }
    }

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
        elementChooser.setRowData(0, elements);
        elementChooser.setRowCount(elements.size());

        if (elements.size() > 0) {
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

            // Distinction between the item that is selected and the one that has focus while
            // moving focus up/down with the keyboard
            elementSelectionModel.setSelected(element, true);
            elementChooser.setKeyboardSelectedRow(selectedRow, true);
            update(element);
        }

        final PopupSize popupSize = PopupSize.resizable(850, 550, 550, 400);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Change Step Filters")
                .onShow(e -> elementChooser.setFocus(true))
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        update(null);
                        consumer.accept(this.settingsMap);
                    }
                    e.hide();
                })
                .fire();
    }

    private void updateLocalActiveFilterState() {
        if (currentElementId != null) {
            final boolean hasActiveFilters = getView().getSkipToErrors() != null
                                             || getView().getSkipToOutput() != null
                                             || xPathListPresenter.getDataProvider().getList()
                                                     .stream()
                                                     .anyMatch(Objects::nonNull);
            elementIdToHasActiveFilterMap.put(currentElementId, hasActiveFilters);
            elementChooser.redraw();
        }
    }

    private void update(final PipelineElement element) {
        if (currentElementId != null) {
            final SteppingFilterSettings settings = new SteppingFilterSettings(
                    getView().getSkipToErrors(),
                    getView().getSkipToOutput(),
                    new ArrayList<>(xPathFilters));
            settingsMap.put(currentElementId, settings);
        }

        final String elementId = NullSafe.get(element, PipelineElement::getId);
        if (elementId != null && !elementId.equals(currentElementId)) {
            getView().setName(element.getDisplayName());
            xPathFilters = xPathListPresenter.getDataProvider().getList();
            xPathFilters.clear();

            final SteppingFilterSettings settings = settingsMap.get(element.getId());
            if (settings != null) {
                getView().setSkipToErrors(NullSafe.get(settings, SteppingFilterSettings::getSkipToSeverity));
                getView().setSkipToOutput(NullSafe.get(settings, SteppingFilterSettings::getSkipToOutput));
                if (NullSafe.hasItems(settings.getFilters())) {
                    xPathFilters.addAll(settings.getFilters());
                }
            }
        }
        currentElementId = elementId;
    }


    // --------------------------------------------------------------------------------


    public interface SteppingFilterView extends View {

        void setElementChooser(Widget widget);

        void setName(final String name);

        Severity getSkipToErrors();

        void setSkipToErrors(Severity severity);

        void setSkipToErrorsChangeHandler(final Consumer<Severity> skipToErrorsValueConsumer);

        OutputState getSkipToOutput();

        void setSkipToOutput(OutputState skipToOutput);

        void setSkipToOutputChangeHandler(final Consumer<OutputState> skipToOutputValueConsumer);

        void setXPathList(View view);
    }
}
