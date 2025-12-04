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

package stroom.annotation.client;

import stroom.annotation.client.ChooserPresenter.ChooserView;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.table.client.MyCellTable;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectEvent;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class MultiChooserPresenter<T>
        extends MyPresenterWidget<ChooserView>
        implements ChooserUiHandlers {

    private final MultiSelectionModel<T> selectionModel = new MultiSelectionModelImpl<>();
    private final CellTable<T> cellTable;
    private DataSupplier<T> dataSupplier;
    private Function<T, SafeHtml> displayValueFunction = t -> SafeHtmlUtils.fromString(t.toString());

    @Inject
    public MultiChooserPresenter(final EventBus eventBus,
                                 final ChooserView view,
                                 final UiConfigCache uiConfigCache) {
        super(eventBus, view);

        view.setUiHandlers(this);

        cellTable = new MyCellTable<>(Integer.MAX_VALUE);
        cellTable.addStyleName("multiChooser");
        view.setBottomWidget(cellTable);

        addSelectedColumn();

        // Text.
        final Column<T, SafeHtml> textColumn = new Column<T, SafeHtml>(ClickableCell.create()) {
            @Override
            public SafeHtml getValue(final T value) {
                final SafeHtmlBuilder builder = new SafeHtmlBuilder();
                if (value != null) {
                    builder.append(displayValueFunction.apply(value));
                }
                return builder.toSafeHtml();
            }
        };
        cellTable.addColumn(textColumn);
        textColumn.setFieldUpdater((index, row, value) -> {
            selectionModel.setSelected(row, !selectionModel.isSelected(row));
            cellTable.redraw();
        });

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

    void addSelectedColumn() {
        // Select Column
        final Column<T, TickBoxState> column = new Column<T, TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue(final T object) {
                return TickBoxState.fromBoolean(selectionModel.isSelected(object));
            }
        };
        cellTable.addColumn(column);

        // Add Handlers
        column.setFieldUpdater((index, row, value) ->
                selectionModel.setSelected(row, value.toBoolean()));
    }

    void focus() {
        cellTable.setFocus(true);
    }

    void clearFilter() {
        getView().clearFilter();
    }

    /**
     * Sets the function to provide a display value for value T.
     */
    public void setDisplayValueFunction(final Function<T, SafeHtml> displayValueFunction) {
        this.displayValueFunction = Objects.requireNonNull(displayValueFunction);
    }

    public List<T> getSelectedItems() {
        return selectionModel.getSelectedItems();
    }

    public void setSelectedItems(final List<T> value) {
        selectionModel.setSelectedItems(value);
    }

    public HandlerRegistration addSelectionHandler(final MultiSelectEvent.Handler handler) {
        return selectionModel.addSelectionHandler(handler);
    }

    @Override
    public void onFilterChange(final String filter) {
        if (dataSupplier != null) {
            dataSupplier.onChange(filter, values -> {
                if (values != null) {
                    cellTable.setRowData(0, values);
                    cellTable.setRowCount(values.size());
                }
            });
        }
    }

    @Override
    public void onClearSelection() {
        selectionModel.clear();
    }

    public void setDataSupplier(final DataSupplier<T> dataSupplier) {
        this.dataSupplier = dataSupplier;
    }

    public void refresh() {
        onFilterChange(null);
    }

    public interface DataSupplier<T> {

        void onChange(String filter, Consumer<List<T>> consumer);
    }

    private static class ClickableCell extends AbstractCell<SafeHtml> {

        private ClickableCell(final Set<String> consumedEvents) {
            super(consumedEvents);
        }

        public static ClickableCell create() {
            final Set<String> consumedEvents = new HashSet<>();
            consumedEvents.add(BrowserEvents.MOUSEDOWN);
            consumedEvents.add(BrowserEvents.KEYDOWN);
            return new ClickableCell(consumedEvents);
        }

        @Override
        public void render(final Context context, final SafeHtml value, final SafeHtmlBuilder sb) {
            if (value != null) {
                sb.append(value);
            }
        }

        @Override
        public void onBrowserEvent(final Context context, final Element parent, final SafeHtml value,
                                   final NativeEvent event, final ValueUpdater<SafeHtml> valueUpdater) {
            if (value != null) {
                super.onBrowserEvent(context, parent, value, event, valueUpdater);
                final String type = event.getType();

                final Action action = KeyBinding.test(event);
                if (((BrowserEvents.MOUSEDOWN.equals(type) && MouseUtil.isPrimary(event)) ||
                     (BrowserEvents.KEYDOWN.equals(type) && action == Action.SELECT))) {
                    event.preventDefault();
                    if (valueUpdater != null) {
                        valueUpdater.update(value);
                    }
                }
            }
        }
    }
}
