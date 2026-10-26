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

package stroom.explorer.client.presenter;

import stroom.explorer.client.presenter.TabSessionChooserPresenter.TabSessionChooserView;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.DataGridProfile;
import stroom.widget.datagrid.client.DataGridSelectionEventManager;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
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
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public class TabSessionChooserPresenter<T> extends MyPresenterWidget<TabSessionChooserView> {

    private final MultiSelectionModelImpl<T> selectionModel = new MultiSelectionModelImpl<>();
    private final DataGrid<T> list;
    private Function<T, SafeHtml> displayValueFunction = t -> SafeHtmlUtils.fromString(t.toString());

    @Inject
    public TabSessionChooserPresenter(final EventBus eventBus,
                                      final TabSessionChooserView view,
                                      final UiConfigCache uiConfigCache) {
        super(eventBus, view);

        // A list of sessions, the one with the focus chosen. Escape goes on to the dialog (Cancel)
        list = new DataGrid<>(this, DataGrid.MASSIVE_LIST_PAGE_SIZE);
        list.setProfile(DataGridProfile.LISTBOX);
        list.setAccessibleName("Tab sessions");
        list.setSelectionModel(selectionModel,
                new DataGridSelectionEventManager<>(list, selectionModel, false));
        view.setSelectionList(list);

        // Enter, or a double click, on a session confirms it and closes the dialog
        selectionModel.addSelectionHandler(event -> {
            if (event.getSelectionType().isDoubleSelect()) {
                HidePopupRequestEvent.builder(TabSessionChooserPresenter.this).fire();
            }
        });

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
        list.addColumn(textColumn, "", 200);
    }

    void focus() {
        list.setFocus(true);
    }

    /**
     * Sets the function to provide a display value for value T.
     */
    public void setDisplayValueFunction(final Function<T, SafeHtml> displayValueFunction) {
        this.displayValueFunction = Objects.requireNonNull(displayValueFunction);
    }

    public Optional<T> getSelected() {
        return Optional.ofNullable(selectionModel.getSelected());
    }

    public void setSelected(final T value) {
        selectionModel.setSelected(value);
    }

    public HandlerRegistration addDataSelectionHandler(final SelectionChangeEvent.Handler handler) {
        return selectionModel.addSelectionChangeHandler(handler);
    }

    public void setSelectionList(final List<T> selectionList) {
        list.setRowData(0, selectionList);
        list.setRowCount(selectionList.size());
    }

    // --------------------------------------------------------------------------------


    public interface TabSessionChooserView extends View {

        void setSelectionList(Widget widget);
    }
}
