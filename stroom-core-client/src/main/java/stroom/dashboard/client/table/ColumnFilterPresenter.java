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

package stroom.dashboard.client.table;

import stroom.dashboard.client.table.ColumnFilterPresenter.ColumnFilterView;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.editor.client.presenter.EditorView;
import stroom.query.api.Column;
import stroom.query.api.ColumnFilter;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.BiConsumer;

public class ColumnFilterPresenter extends MyPresenterWidget<ColumnFilterView> {

    private final EditorPresenter editorPresenter;

    @Inject
    public ColumnFilterPresenter(final EventBus eventBus,
                                 final ColumnFilterView view,
                                 final EditorPresenter editorPresenter) {
        super(eventBus, view);
        this.editorPresenter = editorPresenter;
        view.setEditor(editorPresenter.getView());
    }

    public void show(final Column column,
                     final BiConsumer<Column, Column> columnChangeConsumer) {
        final PopupSize popupSize = PopupSize.resizable(600, 300);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Filter '" + column.getName() + "'")
                .modal(true)
                .onShow(e -> editorPresenter.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final ColumnFilter filter = getColumnFilter();
                        if ((filter == null && column.getFilter() != null)
                            || (filter != null && !filter.equals(column.getColumnFilter()))) {
                            columnChangeConsumer.accept(column, column.copy().columnFilter(filter).build());
                        }
                    }
                    e.hide();
                })
                .fire();
    }

    public void setColumnFilter(final ColumnFilter columnFilter) {
        String expression = "";

        if (columnFilter != null) {
            if (columnFilter.getFilter() != null) {
                expression = columnFilter.getFilter();
            }
        }

        editorPresenter.setText(expression);
    }

    public ColumnFilter getColumnFilter() {
        ColumnFilter filter = null;

        final String expression = editorPresenter.getText().trim();
        if (expression.length() > 0) {
            filter = new ColumnFilter(expression);
        }

        return filter;
    }

    public interface ColumnFilterView extends View {

        void setEditor(final EditorView editor);
    }
}
