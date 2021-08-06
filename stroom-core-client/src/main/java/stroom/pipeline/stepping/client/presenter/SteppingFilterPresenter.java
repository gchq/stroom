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

import stroom.data.table.client.MyCellTable;
import stroom.pipeline.shared.XPathFilter;
import stroom.pipeline.shared.stepping.SteppingFilterSettings;
import stroom.pipeline.stepping.client.presenter.SteppingFilterPresenter.SteppingFilterView;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.OutputState;
import stroom.util.shared.Severity;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.BasicSelectionEventManager;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
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
import java.util.function.Consumer;

public class SteppingFilterPresenter extends
        MyPresenterWidget<SteppingFilterView> {

    private final XPathListPresenter xPathListPresenter;
    private final XPathFilterPresenter xPathFilterPresenter;
    private List<XPathFilter> xPathFilters;

    private final SingleSelectionModel<String> elementSelectionModel = new SingleSelectionModel<>();
    private final CellTable<String> elementChooser;
    private final ButtonView addXPath;
    private final ButtonView editXPath;
    private final ButtonView removeXPath;

    private Map<String, SteppingFilterSettings> settingsMap;
    private String currentElementId;

    @Inject
    public SteppingFilterPresenter(final EventBus eventBus,
                                   final SteppingFilterView view,
                                   final XPathListPresenter xPathListPresenter,
                                   final XPathFilterPresenter xPathFilterProvider) {
        super(eventBus, view);
        this.xPathListPresenter = xPathListPresenter;
        this.xPathFilterPresenter = xPathFilterProvider;

        addXPath = xPathListPresenter.addButton(SvgPresets.ADD);
        addXPath.setTitle("Add XPath");
        editXPath = xPathListPresenter.addButton(SvgPresets.EDIT);
        editXPath.setTitle("Edit XPath");
        removeXPath = xPathListPresenter.addButton(SvgPresets.REMOVE);
        removeXPath.setTitle("Delete XPath");
        editXPath.setEnabled(false);
        removeXPath.setEnabled(false);


        elementChooser = new MyCellTable<>(Integer.MAX_VALUE);

        // Text.
        final Column<String, SafeHtml> textColumn = new Column<String, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final String string) {
                final SafeHtmlBuilder builder = new SafeHtmlBuilder();
                builder.appendHtmlConstant("<div style=\"padding: 5px; min-width: 200px\">");
                builder.appendEscaped(string);
                builder.appendHtmlConstant("</div>");
                return builder.toSafeHtml();
            }
        };
        elementChooser.addColumn(textColumn);
        elementChooser.setSelectionModel(elementSelectionModel, new BasicSelectionEventManager<>(elementChooser));

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
    }

    private void addXPathFilter() {
        final XPathFilter xPathFilter = new XPathFilter();
        final HidePopupRequestEvent.Handler handler = e -> {
            if (e.isOk()) {
                xPathFilterPresenter.write();
                xPathFilters.add(xPathFilter);

                xPathListPresenter.getSelectionModel().setSelected(xPathFilter);
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
        }
    }

    public void show(final List<String> elements,
                     final Map<String, SteppingFilterSettings> map,
                     final Consumer<Map<String, SteppingFilterSettings>> consumer) {
        currentElementId = null;
        if (map != null) {
            settingsMap = new HashMap<>(map);
        } else {
            settingsMap = new HashMap<>();
        }
        elementChooser.setRowData(0, elements);
        elementChooser.setRowCount(elements.size());
        if (elements.size() > 0) {
            final String elementId = elements.get(0);
            elementSelectionModel.setSelected(elementId, true);
            update(elementId);
        }

        final PopupSize popupSize = PopupSize.resizable(850, 550);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Change Filters")
                .onShow(e -> elementChooser.setFocus(true))
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        update(null);
                        consumer.accept(settingsMap);
                    }
                    e.hide();
                })
                .fire();
    }

    private void update(final String elementId) {
        if (currentElementId != null) {
            final SteppingFilterSettings settings = new SteppingFilterSettings();
            settings.setSkipToSeverity(getView().getSkipToErrors());
            settings.setSkipToOutput(getView().getSkipToOutput());
            settings.setFilters(new ArrayList<>(xPathFilters));
            settingsMap.put(currentElementId, settings);
        }

        if (elementId != null && !elementId.equals(currentElementId)) {
            SteppingFilterSettings settings = settingsMap.get(elementId);
            if (settings == null) {
                settings = new SteppingFilterSettings();
            }
            getView().setSkipToErrors(settings.getSkipToSeverity());
            getView().setSkipToOutput(settings.getSkipToOutput());

            xPathFilters = xPathListPresenter.getDataProvider().getList();
            xPathFilters.clear();
            if (settings.getFilters() != null && settings.getFilters().size() > 0) {
                xPathFilters.addAll(settings.getFilters());
            }
        }

        currentElementId = elementId;
    }

    public interface SteppingFilterView extends View {

        void setElementChooser(Widget widget);

        Severity getSkipToErrors();

        void setSkipToErrors(Severity severity);

        OutputState getSkipToOutput();

        void setSkipToOutput(OutputState skipToOutput);

        void setXPathList(View view);
    }
}
