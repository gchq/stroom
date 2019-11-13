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

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.cellview.client.CellTable.Resources;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.gwt.view.client.SingleSelectionModel;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.annotation.client.ChooserPresenter.ChooserView;
import stroom.data.table.client.CellTableView;
import stroom.data.table.client.CellTableViewImpl;
import stroom.data.table.client.CellTableViewImpl.HoverResources;

import java.util.List;
import java.util.function.Consumer;

public class ChooserPresenter extends MyPresenterWidget<ChooserView> implements ChooserUiHandlers {
    private final SingleSelectionModel<String> selectionModel = new SingleSelectionModel<>();
    private final CellTableView<String> table;
    private DataSupplier dataSupplier;

    @Inject
    public ChooserPresenter(final EventBus eventBus, final ChooserView view) {
        super(eventBus, view);

        view.setUiHandlers(this);

        table = new CellTableViewImpl<>(true, (Resources) GWT.create(HoverResources.class));
        view.setBottomView(table);

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
        table.addColumn(textColumn);
        table.setSupportsSelection(true);
        table.setSelectionModel(selectionModel);
    }

    void clearFilter() {
        getView().clearFilter();
    }

    String getSelected() {
        return selectionModel.getSelectedObject();
    }

    void setSelected(final String value) {
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
                    table.setRowData(0, values);
                    table.setRowCount(values.size());
                }
            });
        }
    }

    @Override
    public void onClearSelection() {
        selectionModel.clear();
    }

    public void setDataSupplier(final DataSupplier dataSupplier) {
        this.dataSupplier = dataSupplier;
        onFilterChange(null);
    }

    public interface DataSupplier {
        void onChange(String filter, Consumer<List<String>> consumer);
    }

    public interface ChooserView extends View, HasUiHandlers<ChooserUiHandlers> {
        void setBottomView(View view);

        void clearFilter();

        void setClearSelectionText(String text);
    }
}
