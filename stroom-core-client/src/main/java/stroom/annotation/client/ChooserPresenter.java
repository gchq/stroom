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
import stroom.data.table.client.MyCellTable;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.util.client.BasicSelectionEventManager;
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.CellPreviewEvent;
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

public class ChooserPresenter<T> extends MyPresenterWidget<ChooserView> implements ChooserUiHandlers {

    private final MySingleSelectionModel<T> selectionModel = new MySingleSelectionModel<>();
    private final CellTable<T> cellTable;
    private DataSupplier<T> dataSupplier;
    private Function<T, String> displayValueFunction = Objects::toString;

    @Inject
    public ChooserPresenter(final EventBus eventBus, final ChooserView view) {
        super(eventBus, view);

        view.setUiHandlers(this);

        cellTable = new MyCellTable<>(Integer.MAX_VALUE);
        cellTable.setSelectionModel(selectionModel, new BasicSelectionEventManager<T>(cellTable) {
            @Override
            protected void onClose(final CellPreviewEvent<T> e) {
                super.onClose(e);
                HidePopupEvent.builder(ChooserPresenter.this).autoClose(true).ok(false).fire();
            }

            @Override
            protected void onExecute(final CellPreviewEvent<T> e) {
                super.onExecute(e);
                SelectionChangeEvent.fire(selectionModel);
            }
        });
        view.setBottomWidget(cellTable);

        // Text.
        final Column<T, SafeHtml> textColumn = new Column<T, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final T value) {
                final SafeHtmlBuilder builder = new SafeHtmlBuilder();
                builder.appendHtmlConstant("<div style=\"padding: 5px; min-width: 200px\">");
                if (value != null) {
                    builder.appendEscaped(displayValueFunction.apply(value));
                }
                builder.appendHtmlConstant("</div>");
                return builder.toSafeHtml();
            }
        };
        cellTable.addColumn(textColumn);
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
    public void setDisplayValueFunction(final Function<T, String> displayValueFunction) {
        this.displayValueFunction = Objects.requireNonNull(displayValueFunction);
    }

    T getSelected() {
        return selectionModel.getSelectedObject();
    }

    String getSelectedDisplayValue() {
        final T selected = getSelected();
        return GwtNullSafe.get(selected, displayValueFunction);
    }

    void setSelected(final T value) {
        selectionModel.setSelected(value, true);
    }

    void setClearSelectionText(final String text) {
        getView().setClearSelectionText(text);
    }

    HandlerRegistration addDataSelectionHandler(final SelectionChangeEvent.Handler handler) {
        return selectionModel.addSelectionChangeHandler(handler);
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
        onFilterChange(null);
    }


    // --------------------------------------------------------------------------------


    public interface DataSupplier<T> {

        void onChange(String filter, Consumer<List<T>> consumer);
    }


    // --------------------------------------------------------------------------------


    public interface ChooserView extends View, HasUiHandlers<ChooserUiHandlers> {

        void setBottomWidget(Widget widget);

        void clearFilter();

        void setClearSelectionText(String text);
    }
}
