/*
 * Copyright 2019 Crown Copyright
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

package stroom.annotation.client;

import stroom.annotation.client.ChooserPresenter.ChooserView;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.widget.datagrid.client.DataGridProfile;
import stroom.widget.datagrid.client.DataGridWidget;
import stroom.widget.datagrid.client.DataGridWidgetSelectionEventManager;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ChooserPresenter<T>
        extends MyPresenterWidget<ChooserView>
        implements ChooserUiHandlers {

    private final MultiSelectionModelImpl<T> selectionModel = new MultiSelectionModelImpl<>();
    private final DataGridWidget<T> list;
    private DataSupplier<T> dataSupplier;
    private Function<T, SafeHtml> displayValueFunction = t -> SafeHtmlUtils.fromString(t.toString());
    private Function<T, String> tooltipFunction = null;

    @Inject
    public ChooserPresenter(final EventBus eventBus,
                            final ChooserView view,
                            final UiConfigCache uiConfigCache) {
        super(eventBus, view);

        view.setUiHandlers(this);

        // A list of values to choose from: moving through it chooses nothing; a click, Enter or Space
        // chooses a value, which the caller acts on (and closes the chooser), even the one chosen
        // already. Escape goes on to the popup, which closes
        list = new DataGridWidget<>(this, DataGridWidget.MASSIVE_LIST_PAGE_SIZE);
        list.setProfile(DataGridProfile.LISTBOX);
        // As high as its values, up to the chooser's height (its max-height), then scrolling
        list.setFitContent(true);
        final DataGridWidgetSelectionEventManager<T> selectionEventManager =
                new DataGridWidgetSelectionEventManager<>(list, selectionModel, false);
        selectionEventManager.setChooser(item -> {
            final boolean alreadySelected = Objects.equals(selectionModel.getSelected(), item);
            selectionModel.setSelected(item);
            if (alreadySelected) {
                // setSelected won't fire if selection unchanged, so fire manually
                SelectionChangeEvent.fire(selectionModel);
            }
        });
        list.setSelectionModel(selectionModel, selectionEventManager);
        view.setBottomWidget(list);

        // Text.
        final Column<T, SafeHtml> textColumn = new Column<T, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final T value) {
                final SafeHtmlBuilder builder = new SafeHtmlBuilder();
                if (value != null && tooltipFunction != null) {
                    final String tooltip = tooltipFunction.apply(value);
                    if (tooltip != null && !tooltip.isEmpty()) {
                        builder.appendHtmlConstant("<div style=\"padding: 5px; min-width: 200px\" title=\"");
                        builder.appendEscaped(tooltip);
                        builder.appendHtmlConstant("\">");
                    } else {
                        builder.appendHtmlConstant("<div style=\"padding: 5px; min-width: 200px\">");
                    }
                } else {
                    builder.appendHtmlConstant("<div style=\"padding: 5px; min-width: 200px\">");
                }
                if (value != null) {
                    builder.append(displayValueFunction.apply(value));
                }
                builder.appendHtmlConstant("</div>");
                return builder.toSafeHtml();
            }
        };
        list.addColumn(textColumn, "", 200);

        // Only deals in lists of strings so no field defs required.
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                getView().registerPopupTextProvider(() ->
                        QuickFilterTooltipUtil.createTooltip(
                                "Choose Item Quick Filter",
                                uiConfig.getHelpUrlQuickFilter()));
            }
        }, this);
    }

    /// Names the list of values for assistive technology, e.g. "Statuses" (a list must have one).
    ///
    /// @param name The list's name.
    public void setAccessibleName(final String name) {
        list.setAccessibleName(name);
    }

    void focus() {
        list.setFocus(true);
    }

    void clearFilter() {
        getView().clearFilter();
    }

    void clearSelection() {
        selectionModel.clear();
    }

    /**
     * Sets the function to provide a display value for value T.
     */
    public void setDisplayValueFunction(final Function<T, SafeHtml> displayValueFunction) {
        this.displayValueFunction = Objects.requireNonNull(displayValueFunction);
    }

    public void setTooltipFunction(final Function<T, String> tooltipFunction) {
        this.tooltipFunction = tooltipFunction;
    }

    public T getSelected() {
        return selectionModel.getSelected();
    }

    public SafeHtml getSelectedDisplayValue() {
        final T selected = getSelected();
        return NullSafe.get(selected, displayValueFunction);
    }

    public void setSelected(final T value) {
        // The only value chosen (or none, for null)
        selectionModel.setSelected(value);
    }

    public HandlerRegistration addDataSelectionHandler(final SelectionChangeEvent.Handler handler) {
        return selectionModel.addSelectionChangeHandler(handler);
    }

    @Override
    public void onFilterChange(final String filter) {
        if (dataSupplier != null) {
            dataSupplier.onChange(filter, values -> {
                if (values != null) {
                    list.setRowData(0, values);
                    list.setRowCount(values.size());
                }
            });
        }
    }

    public void setDataSupplier(final DataSupplier<T> dataSupplier) {
        this.dataSupplier = dataSupplier;
        onFilterChange(null);
    }

    // --------------------------------------------------------------------------------


    public interface DataSupplier<T> {

        void onChange(String filter, Consumer<List<T>> consumer);
    }


    // --------------------------------------------------------------------------------


    public interface ChooserView extends View, HasUiHandlers<ChooserUiHandlers> {

        void registerPopupTextProvider(Supplier<SafeHtml> popupTextSupplier);

        void setBottomWidget(Widget widget);

        void clearFilter();
    }
}
