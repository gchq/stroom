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

package stroom.explorer.client.presenter;

import stroom.data.table.client.MyCellTable;
import stroom.explorer.client.presenter.TabSessionChooserPresenter.TabSessionChooserView;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.view.DialogAction;
import stroom.widget.util.client.BasicSelectionEventManager;
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public class TabSessionChooserPresenter<T> extends MyPresenterWidget<TabSessionChooserView> {

    private final MySingleSelectionModel<T> selectionModel = new MySingleSelectionModel<>();
    private final CellTable<T> cellTable;
    private Function<T, SafeHtml> displayValueFunction = t -> SafeHtmlUtils.fromString(t.toString());

    @Inject
    public TabSessionChooserPresenter(final EventBus eventBus,
                                      final TabSessionChooserView view,
                                      final UiConfigCache uiConfigCache) {
        super(eventBus, view);

        cellTable = new MyCellTable<>(Integer.MAX_VALUE);
        cellTable.setSelectionModel(selectionModel, new BasicSelectionEventManager<T>(cellTable) {
            @Override
            protected void onClose(final CellPreviewEvent<T> e) {
                super.onClose(e);
                HidePopupRequestEvent.builder(TabSessionChooserPresenter.this).autoClose(true)
                        .action(DialogAction.CLOSE)
                        .fire();
            }

            @Override
            protected void onExecute(final CellPreviewEvent<T> e) {
                super.onExecute(e);
                SelectionChangeEvent.fire(selectionModel);
            }
        });
        view.setSelectionList(cellTable);

        // Text.
        final Column<T, SafeHtml> textColumn = new Column<T, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final T value) {
                final SafeHtmlBuilder builder = new SafeHtmlBuilder();
                builder.appendHtmlConstant("<div style=\"padding: 5px; min-width: 200px\">");
                if (value != null) {
                    builder.append(displayValueFunction.apply(value));
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

    /**
     * Sets the function to provide a display value for value T.
     */
    public void setDisplayValueFunction(final Function<T, SafeHtml> displayValueFunction) {
        this.displayValueFunction = Objects.requireNonNull(displayValueFunction);
    }

    public Optional<T> getSelected() {
        return Optional.ofNullable(selectionModel.getSelectedObject());
    }

    public void setSelected(final T value) {
        selectionModel.setSelected(value, true);
    }

    public HandlerRegistration addDataSelectionHandler(final SelectionChangeEvent.Handler handler) {
        return selectionModel.addSelectionChangeHandler(handler);
    }

    public void setSelectionList(final List<T> selectionList) {
        cellTable.setRowData(0, selectionList);
        cellTable.setRowCount(selectionList.size());
    }

    // --------------------------------------------------------------------------------


    public interface TabSessionChooserView extends View {

        void setSelectionList(Widget widget);
    }
}
