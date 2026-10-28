/*
 * Copyright 2024 Crown Copyright
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

package stroom.dashboard.client.component;

import stroom.editor.client.component.CodeEditorController;
import stroom.query.api.Column;
import stroom.query.api.ColumnFilter;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.BiConsumer;

/// A table column's filter ([ColumnFilterEditor]): shown as a dialog of its own, or as the Filter
/// tab of a dashboard table's filter dialog.
public class ColumnFilterEditorController extends Controller {

    private final CodeEditorController editor;

    /// Made by GIN, with its filter.
    ///
    /// @param eventBus                     The application's event bus.
    /// @param view                         The filter.
    /// @param codeEditorControllerProvider Makes the code editor's controller.
    @Inject
    public ColumnFilterEditorController(final EventBus eventBus,
                                        final ColumnFilterEditor view,
                                        final Provider<CodeEditorController> codeEditorControllerProvider) {
        super(eventBus);
        initWidget(view);
        editor = make(codeEditorControllerProvider).attach(view.getEditor());
    }

    /// Shows the editor as a dialog for a column's filter, owned by `opener` until it is closed.
    /// OK gives the column, and a copy with the filter entered, to `onChange` if the filter has
    /// changed.
    ///
    /// @param opener   What shows it.
    /// @param column   The column.
    /// @param onChange What to do with the column and its changed copy.
    public void edit(final HasControllerNode opener,
                     final Column column,
                     final BiConsumer<Column, Column> onChange) {
        setColumnFilter(column.getColumnFilter());
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(600, 300))
                .caption("Filter '" + column.getName() + "'")
                .onShow(e -> editor.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final ColumnFilter filter = getColumnFilter(column);
                        if ((filter == null && column.getFilter() != null)
                            || (filter != null && !filter.equals(column.getColumnFilter()))) {
                            onChange.accept(column, column.copy().columnFilter(filter).build());
                        }
                    }
                    e.hide();
                })
                .fire();
    }

    /// Shows a filter's expression.
    ///
    /// @param columnFilter The filter, or null for none.
    public void setColumnFilter(final ColumnFilter columnFilter) {
        String expression = "";
        if (columnFilter != null && columnFilter.getFilter() != null) {
            expression = columnFilter.getFilter();
        }
        editor.setText(expression);
    }

    /// Returns the filter entered for a column.
    ///
    /// @param column The column.
    /// @return The filter, with no expression if none is entered.
    public ColumnFilter getColumnFilter(final Column column) {
        final ColumnFilter.Builder builder = ColumnFilter.fromColumn(column);
        final String expression = editor.getText().trim();
        if (!expression.isEmpty()) {
            builder.filter(expression);
        }
        return builder.build();
    }
}
