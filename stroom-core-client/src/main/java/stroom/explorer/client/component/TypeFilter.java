/*
 * Copyright 2021 Crown Copyright
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

package stroom.explorer.client.component;

import stroom.docstore.shared.DocumentType;
import stroom.explorer.client.DocumentTypeCell;
import stroom.explorer.client.DocumentTypeSelectionModel;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.DataGridSelectionEventManager;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.Consumer;

/// The explorer's type filter: a list of the document types, each ticked or not. The arrows move,
/// and Space, Enter or a press ticks or unticks the type with the focus, Ctrl+A all of them; Escape
/// goes on to the popup it is in, which closes.
public class TypeFilter extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DataGrid<DocumentType> list;

    private Consumer<DocumentType> onToggle = type -> {
    };
    private Runnable onToggleAll = () -> {
    };

    /// Makes an empty list.
    public TypeFilter() {
        initWidget(BINDER.createAndBindUi(this));
        final DataGridSelectionEventManager<DocumentType> selectionEventManager =
                new DataGridSelectionEventManager<>(list, new MultiSelectionModelImpl<>(), true);
        selectionEventManager.setChooser(type -> {
            if (type != null) {
                onToggle.accept(type);
            }
        });
        selectionEventManager.addSelectAllHandler(e -> onToggleAll.run());
        // The ticks are the rows' own state, not a selection
        list.setSelectionModel(null, selectionEventManager);
    }

    /// Says how each type is ticked, and which row is All / None (drawn with a line under it).
    ///
    /// @param ticks   Whether each type is ticked.
    /// @param allType The All / None row's type.
    public void setTicks(final DocumentTypeSelectionModel ticks, final DocumentType allType) {
        list.setRowChecked(ticks::getState);
        list.setRowStyles((type, index) -> allType.equals(type)
                ? "type-filter__all"
                : null);
        list.addColumn(new Column<DocumentType, DocumentType>(new DocumentTypeCell(ticks)) {
            @Override
            public DocumentType getValue(final DocumentType documentType) {
                return documentType;
            }
        }, "", 100);
    }

    /// Shows the types, drawing every row again (the ticks aren't in the types).
    ///
    /// @param types The types, All / None first.
    public void setTypes(final List<DocumentType> types) {
        list.setRowData(0, types);
        list.setRowCount(types.size());
        list.redraw();
    }

    /// Puts the focus on the first type, if there is one.
    public void focus() {
        if (list.getVisibleItemCount() > 0) {
            list.setKeyboardSelectedRow(0, true);
        }
    }

    /// @param onToggle Given a type the user ticks or unticks (All / None among them).
    public void setOnToggle(final Consumer<DocumentType> onToggle) {
        this.onToggle = onToggle;
    }

    /// @param onToggleAll Run when the user ticks or unticks them all (Ctrl+A).
    public void setOnToggleAll(final Runnable onToggleAll) {
        this.onToggleAll = onToggleAll;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, TypeFilter> {

    }
}
