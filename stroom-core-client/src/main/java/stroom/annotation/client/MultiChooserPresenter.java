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

package stroom.annotation.client;

import stroom.annotation.client.ChooserPresenter.ChooserView;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.table.client.MyCellTable;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.util.client.MultiSelectEvent;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.List;
import java.util.Objects;
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
        final Column<T, SafeHtml> textColumn = new Column<T, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final T value) {
                final SafeHtmlBuilder builder = new SafeHtmlBuilder();
//                builder.appendHtmlConstant("<div style=\"padding: 5px; min-width: 200px\">");
                if (value != null) {
                    builder.append(displayValueFunction.apply(value));
                }
//                builder.appendHtmlConstant("</div>");
                return builder.toSafeHtml();
            }
        };
        cellTable.addColumn(textColumn);

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
        column.setFieldUpdater((index, row, value) -> {
            selectionModel.setSelected(row, value.toBoolean());

        });
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
}
